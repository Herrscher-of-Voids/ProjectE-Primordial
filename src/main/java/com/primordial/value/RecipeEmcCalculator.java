/**
 * 【文件职责】在服务端启动时读取已注册配方，为没有人工价格或 ProjectE 原生价格的物品建立只读 EMC 快照。
 *
 * 【变更记录 #1】2026-09-15 15:02:00（第 1 次备注 · 1.0.0-stage7-recipe-emc）
 * - 删除：旧 UnknownValueEstimator 使用 double、固定倍率和启动后写回公共价格表的路径。
 * - 新增：基于 BigInteger/EmcAmount 的配方迭代、燃料锚点、可选流体/能源反射读取和稀有度兜底。
 * - 修改：人工覆盖和 ProjectE 原生值永远优先；计算结果只保存在内存快照，不修改管理员文件。
 * - 用途：让标准 Forge 配方及公开的机器配方成本能参与统一 EMC 报价，同时限制迭代次数、选项数量和反射范围，避免启动时内存或 CPU 无界增长。
 */
package com.primordial.value;

import com.primordial.compat.ProjectECompat;
import com.primordial.energy.EnergyConversions;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 服务端启动阶段使用；快照发布后，运行中的管道只做 O(1) 查询。 */
public final class RecipeEmcCalculator {
    private static final int MAX_PASSES = 64;
    private static final int MAX_INGREDIENT_CHOICES = 128;
    private static final int MAX_REFLECTED_VALUES = 32;
    private static volatile Map<String, BigInteger> computed = Map.of();

    private RecipeEmcCalculator() {}

    /** 返回最近一次服务端配方扫描结果；返回的 Map 不可变，可安全并发读取。 */
    public static Map<String, BigInteger> snapshot() {
        return computed;
    }

    /** 按“消耗总 EMC ÷ 产量”计算单个输出价格，向上取整避免非零配方变成免费物品。 */
    public static BigInteger outputValue(EmcAmount totalCost, int outputCount) {
        if (totalCost == null || totalCost.isZero() || outputCount <= 0) return BigInteger.ZERO;
        return totalCost.divide(outputCount).ceil().max(BigInteger.ONE);
    }

    /**
     * 扫描当前服务端的配方管理器并发布计算结果。此方法只应在服务器启动或管理员明确刷新时调用。
     * 失败时清空计算快照，保留人工价格和 ProjectE 原生价格的正常工作路径。
     */
    public static synchronized Map<String, BigInteger> recalculate(MinecraftServer server) {
        if (server == null) return computed;
        long started = System.nanoTime();
        try {
            Map<String, BigInteger> explicit = ValueManager.overrideSnapshot();
            Map<Item, BigInteger> known = new HashMap<>();
            Set<Item> anchored = new HashSet<>();

            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (explicit.containsKey(id)) continue;
                BigInteger nativeValue = safeNativeValue(item);
                if (nativeValue.signum() > 0) {
                    known.put(item, nativeValue);
                    anchored.add(item);
                    continue;
                }
                int burn = ForgeHooks.getBurnTime(new ItemStack(item), net.minecraft.world.item.crafting.RecipeType.SMELTING);
                if (burn > 0) known.put(item, BigInteger.valueOf(Math.max(1, burn / 100)));
            }

            List<Recipe<?>> recipes = List.copyOf(server.getRecipeManager().getRecipes());
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                boolean changed = false;
                for (Recipe<?> recipe : recipes) {
                    ItemStack output;
                    try {
                        output = recipe.getResultItem(server.registryAccess());
                    } catch (RuntimeException failure) {
                        continue;
                    }
                    if (output == null || output.isEmpty() || output.getItem() == Items.AIR) continue;
                    Item outputItem = output.getItem();
                    String outputId = BuiltInRegistries.ITEM.getKey(outputItem).toString();
                    if (explicit.containsKey(outputId) || anchored.contains(outputItem)) continue;

                    EmcAmount cost = ingredientCost(recipe, known);
                    if (cost.isZero()) continue;
                    cost = cost.add(reflectedFluidCost(recipe)).add(reflectedEnergyCost(recipe));
                    BigInteger candidate = outputValue(cost, Math.max(1, output.getCount()));
                    BigInteger previous = known.get(outputItem);
                    if (previous == null || candidate.compareTo(previous) < 0) {
                        known.put(outputItem, candidate);
                        changed = true;
                    }
                }
                if (!changed) break;
            }

            Map<String, BigInteger> result = new HashMap<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (explicit.containsKey(id)) continue;
                BigInteger value = known.get(item);
                if (value == null || value.signum() <= 0) value = rarityFallback(item);
                result.put(id, value.max(BigInteger.ONE));
            }
            computed = Map.copyOf(result);
            com.primordial.PrimordialMod.LOGGER.info("[Primordial] recipe EMC calculated for {} items in {} ms",
                    computed.size(), (System.nanoTime() - started) / 1_000_000L);
            return computed;
        } catch (RuntimeException failure) {
            computed = Map.of();
            com.primordial.PrimordialMod.LOGGER.error("[Primordial] Recipe EMC calculation failed; native prices remain active", failure);
            return computed;
        }
    }

    /** 计算所有已知物品 Ingredient 的最便宜组合；空 Ingredient 代表配方中的空槽。 */
    private static EmcAmount ingredientCost(Recipe<?> recipe, Map<Item, BigInteger> known) {
        List<Ingredient> ingredients;
        try {
            ingredients = recipe.getIngredients();
        } catch (RuntimeException failure) {
            return EmcAmount.ZERO;
        }
        if (ingredients == null || ingredients.isEmpty()) return EmcAmount.ZERO;
        EmcAmount total = EmcAmount.ZERO;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            ItemStack[] choices;
            try {
                choices = ingredient.getItems();
            } catch (RuntimeException failure) {
                return EmcAmount.ZERO;
            }
            EmcAmount best = null;
            int inspected = 0;
            for (ItemStack choice : choices) {
                if (choice == null || choice.isEmpty() || inspected++ >= MAX_INGREDIENT_CHOICES) continue;
                BigInteger value = known.get(choice.getItem());
                if (value == null || value.signum() <= 0) continue;
                EmcAmount candidate = EmcAmount.of(value).multiply(Math.max(1, choice.getCount()));
                if (best == null || less(candidate, best)) best = candidate;
            }
            if (best == null) return EmcAmount.ZERO;
            total = total.add(best);
        }
        return total;
    }

    /**
     * 读取部分公开机器配方的 FluidStack 列表。外部配方没有这些方法时直接返回零，绝不猜测私有单位。
     */
    private static EmcAmount reflectedFluidCost(Recipe<?> recipe) {
        for (String name : new String[]{"getFluidIngredients", "getFluidInputs", "getInputFluids", "getFluids"}) {
            Object value = invokeNoArg(recipe, name);
            if (value == null) continue;
            EmcAmount total = EmcAmount.ZERO;
            int count = 0;
            for (Object entry : values(value)) {
                if (count++ >= MAX_REFLECTED_VALUES || !(entry instanceof FluidStack fluid) || fluid.isEmpty()) continue;
                total = total.add(EmcService.fluidPrice(fluid.getFluid()).multiply(fluid.getAmount()));
            }
            if (!total.isZero()) return total;
        }
        return EmcAmount.ZERO;
    }

    /** 公开配方若明确暴露 FE 能耗，则按统一 forge:fe 价格计入；J/AE 等单位需由适配器先换算。 */
    private static EmcAmount reflectedEnergyCost(Recipe<?> recipe) {
        for (String name : new String[]{"getEnergyCost", "getEnergyRequired", "getEnergy"}) {
            Object value = invokeNoArg(recipe, name);
            if (!(value instanceof Number number)) continue;
            long energy = Math.max(0L, Math.min(Integer.MAX_VALUE, number.longValue()));
            if (energy > 0) return EmcService.energyPrice(EnergyConversions.FE).multiply(energy);
        }
        return EmcAmount.ZERO;
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) return null;
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static List<Object> values(Object value) {
        List<Object> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            int count = 0;
            for (Object entry : iterable) {
                if (count++ >= MAX_REFLECTED_VALUES) break;
                result.add(entry);
            }
        } else if (value.getClass().isArray()) {
            int length = Math.min(Array.getLength(value), MAX_REFLECTED_VALUES);
            for (int i = 0; i < length; i++) result.add(Array.get(value, i));
        } else {
            result.add(value);
        }
        return result;
    }

    private static boolean less(EmcAmount left, EmcAmount right) {
        return left.numerator().multiply(right.denominator())
                .compareTo(right.numerator().multiply(left.denominator())) < 0;
    }

    private static BigInteger safeNativeValue(Item item) {
        try {
            return ProjectECompat.itemEmc(new ItemStack(item, 1)).max(BigInteger.ZERO);
        } catch (RuntimeException | LinkageError failure) {
            return BigInteger.ZERO;
        }
    }

    private static BigInteger rarityFallback(Item item) {
        Rarity rarity = new ItemStack(item).getRarity();
        return switch (rarity) {
            case UNCOMMON -> BigInteger.valueOf(2_000);
            case RARE -> BigInteger.valueOf(8_000);
            case EPIC -> BigInteger.valueOf(32_000);
            default -> BigInteger.valueOf(500);
        };
    }
}
