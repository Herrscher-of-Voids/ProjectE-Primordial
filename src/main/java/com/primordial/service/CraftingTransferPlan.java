/**
 * 【文件职责】在库存副本上规划合成材料与 EMC 购买，失败不改动真实库存。
 * 【变更记录 #1】2026-10-07 00:44:19（第 1 次备注 · 1.2.0-knowledge-crafting）
 * - 删除：无。
 * - 新增：九格配料、现有材料优先、缺料报价、余料回填与预算检查。
 * - 修改：无既有行为；服务端提交前使用完整规划结果。
 * - 用途：防止材料不足、背包满或 EMC 不足时发生部分消耗。
 *
 * 【变更记录 #2】2026-10-11 23:18:39（第 2 次备注 · 1.2.0-crafting-jei-system）
 * - 删除：重复候选堆叠造成的相同价格与库存统计。
 * - 新增：候选按完整 ItemInfo 去重，最大批量规划入口和满背包余料边界验证。
 * - 修改：最大批量保留 64 到 1 的有界检查；余料空间会破坏单调性，不能使用二分。
 * - 用途：减少重复工作，保持有空间才提交、失败无损的语义。
 */
package com.primordial.service;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public final class CraftingTransferPlan {
    private CraftingTransferPlan() { }
    public record Plan(List<ItemStack> inventory, List<ItemStack> grid, BigInteger cost) { }

    /** inventory 仅包含可搬运玩家槽；所有返回堆叠均为独立副本。零报价表示禁止兑换。 */
    public static Plan create(List<Ingredient> ingredients, List<ItemStack> inventory,
            List<ItemStack> oldGrid, List<ItemStack> known, Function<ItemStack, BigInteger> quote,
            BigInteger balance, int count) {
        if (ingredients.size() != 9 || count < 1 || count > 64) return null;
        List<ItemStack> pool = new ArrayList<>();
        oldGrid.forEach(s -> pool.add(s.copy()));
        inventory.forEach(s -> pool.add(s.copy()));
        List<ItemStack> grid = new ArrayList<>();
        BigInteger cost = BigInteger.ZERO;
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) { grid.add(ItemStack.EMPTY); continue; }
            Map<moze_intel.projecte.api.ItemInfo, ItemStack> candidates = new LinkedHashMap<>();
            for (ItemStack stack : pool) if (!stack.isEmpty() && ingredient.test(stack))
                candidates.putIfAbsent(moze_intel.projecte.api.ItemInfo.fromStack(stack), stack);
            for (ItemStack stack : known) if (!stack.isEmpty() && ingredient.test(stack))
                candidates.putIfAbsent(moze_intel.projecte.api.ItemInfo.fromStack(stack), stack);
            for (ItemStack stack : ingredient.getItems()) if (!stack.isEmpty())
                candidates.putIfAbsent(moze_intel.projecte.api.ItemInfo.fromStack(stack), stack);
            ItemStack chosen = ItemStack.EMPTY;
            BigInteger chosenCost = null;
            int chosenExisting = -1;
            for (ItemStack candidate : candidates.values()) {
                if (candidate.isEmpty() || candidate.getMaxStackSize() < count) continue;
                int existing = 0;
                for (ItemStack stack : pool) if (ItemStack.isSameItemSameTags(stack, candidate))
                    existing += Math.min(count - existing, stack.getCount());
                int missing = count - existing;
                BigInteger price = missing == 0 ? BigInteger.ZERO : quote.apply(candidate);
                if (missing > 0 && (price == null || price.signum() <= 0)) continue;
                BigInteger payment = price.multiply(BigInteger.valueOf(missing));
                if (cost.add(payment).compareTo(balance) > 0) continue;
                if (existing > chosenExisting || (existing == chosenExisting && payment.compareTo(chosenCost) < 0)) {
                    chosen = candidate.copy(); chosenExisting = existing; chosenCost = payment;
                }
            }
            if (chosen.isEmpty()) return null;
            int remaining = count;
            for (ItemStack stack : pool) {
                if (ItemStack.isSameItemSameTags(stack, chosen)) {
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken); remaining -= taken;
                }
            }
            chosen.setCount(count); grid.add(chosen); cost = cost.add(chosenCost);
        }
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) result.add(pool.get(oldGrid.size() + i));
        // 只将合成格余料归还可用背包；不丢弃、不掉落、不卖入。
        for (int i = 0; i < oldGrid.size(); i++) {
            ItemStack rest = pool.get(i);
            for (ItemStack target : result) if (!rest.isEmpty() && ItemStack.isSameItemSameTags(rest, target)) {
                int move = Math.min(rest.getCount(), Math.max(0, target.getMaxStackSize() - target.getCount()));
                target.grow(move); rest.shrink(move);
            }
            for (int j = 0; j < result.size() && !rest.isEmpty(); j++) if (result.get(j).isEmpty()) {
                int move = Math.min(rest.getCount(), rest.getMaxStackSize());
                result.set(j, rest.copyWithCount(move)); rest.shrink(move);
            }
            if (!rest.isEmpty()) return null;
        }
        return new Plan(result, grid, cost);
    }

    /** 余料归还会破坏单调性；从大到小检查才能保留满背包时直接复用整堆材料的方案。 */
    public static Plan createMaximum(List<Ingredient> ingredients, List<ItemStack> inventory,
            List<ItemStack> oldGrid, List<ItemStack> known, Function<ItemStack, BigInteger> quote,
            BigInteger balance) {
        for (int count = 64; count >= 1; count--) {
            Plan candidate = create(ingredients, inventory, oldGrid, known, quote, balance, count);
            if (candidate != null) return candidate;
        }
        return null;
    }
}
