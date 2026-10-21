/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
package com.primordial.value;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.primordial.PrimordialMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

/**
 * ProjectE EMC 式自动定价（服务端启动时一次性注册所有已注册物品）：
 *
 * 1. 燃料价值：burnTime/100
 * 2. 配方双向定点迭代（不限深度，全 RecipeType 一次扫全：合成/熔炼/锻造/mod自定义）：
 *    - 正向：产物 = Σ原料 × 1.1 / 产量（最小路径，最便宜的获取方式）
 *    - 逆向：单原料配方（熔炼等）原料 = 产物 × 产量 / 1.1
 *    - 单调收敛（只从未知→已知），环路安全
 * 3. 兜底：稀有度 COMMON 0.5 / UNCOMMON 2 / RARE 8 / EPIC 32
 *
 * 这样 mod 物品（如无尽贪婪的深配方）只要配方链能到达已定价的基础物品，
 * 就会自动得到合理价格；到不了基础物品的走稀有度兜底。
 */
public final class UnknownValueEstimator {
    private static final double CRAFT_BONUS = 1.1;
    private static final int MAX_PASSES = 200;

    private UnknownValueEstimator() {}

    /** 启动批量注册：返回所有"表中还没有"的物品 → 价值(EMC) */
    public static Map<String, Double> estimateAll(MinecraftServer server) {
        long t0 = System.currentTimeMillis();
        Map<String, Double> committed = ValueManager.snapshotItemValues();

        // 工作表：已定价物品（表内锚点 + 燃料 + 迭代结果）
        Map<Item, Double> value = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            Double known = committed.get(BuiltInRegistries.ITEM.getKey(item).toString());
            if (known != null) value.put(item, known);
        }

        List<Recipe<?>> recipes = List.copyOf(server.getRecipeManager().getRecipes());

        // 1) 燃料先行（mod 燃料也入表，之后可向配方链传播）
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || value.containsKey(item)) continue;
            int burn = ForgeHooks.getBurnTime(new ItemStack(item), RecipeType.SMELTING);
            if (burn > 0) value.put(item, burn / 100.0);
        }

        // 2) 双向定点迭代
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;

            // 正向：产物 = Σ原料 × 1.1 / 产量（原料取已知选项中最便宜的）
            for (Recipe<?> r : recipes) {
                ItemStack out = r.getResultItem(server.registryAccess());
                if (out.isEmpty() || value.containsKey(out.getItem())) continue;
                double sum = 0;
                boolean ok = true;
                for (Ingredient ing : r.getIngredients()) {
                    Double best = null;
                    for (ItemStack c : ing.getItems()) {
                        if (c.isEmpty()) continue;
                        Double v = value.get(c.getItem());
                        if (v != null && (best == null || v < best)) best = v;
                    }
                    if (best == null) { ok = false; break; }
                    sum += best;
                }
                if (ok && sum > 0) {
                    value.put(out.getItem(), sum * CRAFT_BONUS / Math.max(1, out.getCount()));
                    changed = true;
                }
            }

            // 逆向：单原料配方（熔炼/烟熏/高炉等）：原料 = 产物 × 产量 / 1.1
            for (Recipe<?> r : recipes) {
                List<Ingredient> ings = r.getIngredients();
                if (ings.size() != 1) continue;
                ItemStack[] choices = ings.get(0).getItems();
                if (choices.length != 1) continue;
                ItemStack out = r.getResultItem(server.registryAccess());
                if (out.isEmpty()) continue;
                Item in = choices[0].getItem();
                if (value.containsKey(in)) continue;
                Double rv = value.get(out.getItem());
                if (rv == null) continue;
                value.put(in, rv * Math.max(1, out.getCount()) / CRAFT_BONUS);
                changed = true;
            }

            if (!changed) break;
        }

        // 3) 兜底：仍未定价的按稀有度
        Map<String, Double> result = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (committed.containsKey(id)) continue;
            Double v = value.get(item);
            if (v == null) v = rarityFallback(item);
            result.put(id, Math.max(v, 0.0001));
        }
        PrimordialMod.LOGGER.info("[Primordial] auto-estimated {} items in {} ms",
                result.size(), System.currentTimeMillis() - t0);
        return result;
    }

    private static double rarityFallback(Item item) {
        return switch (new ItemStack(item).getRarity()) {
            case UNCOMMON -> 2000.0;
            case RARE -> 8000.0;
            case EPIC -> 32000.0;
            default -> 500.0;
        };
    }

    // ---------------- 单件惰性估算（漏网之鱼，首次回收时用） ----------------

    public static double estimate(ItemStack stack, MinecraftServer server) {
        // 与批量一致的燃料/配方正向推理（浅层），失败走稀有度
        double best = 0;
        int burnTime = ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
        if (burnTime > 0) best = burnTime / 100.0;

        if (server != null) {
            for (Recipe<?> recipe : List.copyOf(server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING))) {
                ItemStack result = recipe.getResultItem(server.registryAccess());
                if (result.isEmpty() || result.getItem() != stack.getItem()) continue;
                double cost = 0;
                boolean allKnown = true;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    Double choiceMin = null;
                    for (ItemStack choice : ingredient.getItems()) {
                        if (choice.isEmpty()) continue;
                        Double v = ValueManager.peekItemValue(
                                BuiltInRegistries.ITEM.getKey(choice.getItem()).toString());
                        if (v != null && v >= 0 && (choiceMin == null || v < choiceMin)) choiceMin = v;
                    }
                    if (choiceMin == null) { allKnown = false; break; }
                    cost += choiceMin;
                }
                if (allKnown && cost > 0) {
                    best = Math.max(best, (cost * CRAFT_BONUS) / Math.max(1, result.getCount()));
                }
            }
        }

        if (best <= 0) best = rarityFallback(stack.getItem());
        return Math.max(best, 0.0001);
    }
}
