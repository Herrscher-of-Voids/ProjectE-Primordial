/**
 * 【文件职责】验证知识配料的费用、材料复用及失败无损行为。
 * 【变更记录 #1】2026-10-07 00:44:19（第 1 次备注 · 1.2.0-knowledge-crafting）
 * - 删除：无；新增：已有材料、预算、余料空间、重复材料、NBT 和批量边界回归。
 * - 修改：使用真实 ItemStack 与 Ingredient，在副本上执行规划。
 * - 用途：防止配方搬运复制材料、遗漏扣款或失败时破坏输入。
 *
 * 【变更记录 #2】2026-10-11 23:18:39（第 2 次备注 · 1.2.0-crafting-jei-system）
 * - 删除：无。
 * - 新增：验证最大可行批量、候选去重、满背包整堆复用和零预算安全失败。
 * - 修改：继续用真实配方 Ingredient、物品堆和大整数价格验证副本规划。
 * - 用途：确保 JEI 最大传输优化不改变批量上限、费用或失败无损行为。
 */
package com.primordial.service;

import com.primordial.machine.AdapterTestBootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.*;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CraftingTransferPlanTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    private static List<ItemStack> empty(int count) { return new ArrayList<>(Collections.nCopies(count, ItemStack.EMPTY)); }
    private static List<Ingredient> recipe(int slots) {
        List<Ingredient> result = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        for (int i = 0; i < slots; i++) result.set(i, Ingredient.of(Items.OAK_PLANKS));
        return result;
    }
    @Test void usesExistingBeforeBuyingOnlyTheMissingCount() {
        var inventory = List.of(new ItemStack(Items.OAK_PLANKS, 3));
        var plan = CraftingTransferPlan.create(recipe(4), inventory, empty(9), List.of(),
                s -> BigInteger.valueOf(8), BigInteger.valueOf(8), 1);
        assertNotNull(plan); assertEquals(BigInteger.valueOf(8), plan.cost());
        assertTrue(plan.inventory().get(0).isEmpty());
        assertEquals(3, inventory.get(0).getCount());
        assertEquals(4, plan.grid().stream().filter(s -> !s.isEmpty()).count());
    }
    @Test void insufficientBudgetOrUnknownMaterialLeavesInputsUntouched() {
        var inventory = List.of(new ItemStack(Items.OAK_PLANKS, 2));
        assertNull(CraftingTransferPlan.create(recipe(4), inventory, empty(9), List.of(),
                s -> BigInteger.valueOf(8), BigInteger.valueOf(15), 1));
        assertNull(CraftingTransferPlan.create(recipe(4), inventory, empty(9), List.of(),
                s -> BigInteger.ZERO, BigInteger.valueOf(100), 1));
        assertEquals(2, inventory.get(0).getCount());
    }
    @Test void gridMaterialsAreReusedWithoutKnowledgeOrPayment() {
        var grid = empty(9); grid.set(0, new ItemStack(Items.OAK_PLANKS, 4));
        var plan = CraftingTransferPlan.create(recipe(4), empty(2), grid, List.of(),
                s -> BigInteger.ZERO, BigInteger.ZERO, 1);
        assertNotNull(plan); assertEquals(BigInteger.ZERO, plan.cost());
        assertEquals(4, grid.get(0).getCount());
    }
    @Test void unrelatedGridContentsRequireReturnSpace() {
        var grid = empty(9); grid.set(0, new ItemStack(Items.DIAMOND));
        assertNull(CraftingTransferPlan.create(recipe(1), List.of(new ItemStack(Items.STONE, 64)), grid,
                List.of(), s -> BigInteger.ONE, BigInteger.TEN, 1));
        var plan = CraftingTransferPlan.create(recipe(1), empty(1), grid,
                List.of(), s -> BigInteger.ONE, BigInteger.TEN, 1);
        assertNotNull(plan); assertTrue(plan.inventory().get(0).is(Items.DIAMOND));
        assertEquals(1, grid.get(0).getCount());
    }
    @Test void batchChargesEverySlotAndNeverReusesTheSamePhysicalItemTwice() {
        var plan = CraftingTransferPlan.create(recipe(4), List.of(new ItemStack(Items.OAK_PLANKS, 64)),
                empty(9), List.of(), s -> BigInteger.valueOf(8), BigInteger.valueOf(1536), 64);
        assertNotNull(plan); assertEquals(BigInteger.valueOf(1536), plan.cost());
        assertEquals(256, plan.grid().stream().mapToInt(ItemStack::getCount).sum());
        assertNull(CraftingTransferPlan.create(recipe(1), empty(1), empty(9), List.of(),
                s -> BigInteger.ONE, BigInteger.valueOf(1000), 65));
    }
    @Test void maximumPlanFindsTheLargestAffordableBatch() {
        var plan = CraftingTransferPlan.createMaximum(recipe(1), empty(9), empty(9), List.of(),
                s -> BigInteger.valueOf(3), BigInteger.valueOf(30));
        assertNotNull(plan);
        assertEquals(BigInteger.valueOf(30), plan.cost());
        assertEquals(10, plan.grid().get(0).getCount());
    }
    @Test void maximumPlanRejectsWhenNoSingleBatchCanBePaidFor() {
        assertNull(CraftingTransferPlan.createMaximum(recipe(1), empty(9), empty(9), List.of(),
                s -> BigInteger.ONE, BigInteger.ZERO));
    }
    @Test void maximumPlanCanReuseAnEntireGridStackWhenSingleBatchCannotReturnLeftovers() {
        var grid = empty(9);
        grid.set(0, new ItemStack(Items.OAK_PLANKS, 64));
        var inventory = List.of(new ItemStack(Items.STONE, 64));
        assertNull(CraftingTransferPlan.create(recipe(1), inventory, grid, List.of(),
                s -> BigInteger.ZERO, BigInteger.ZERO, 1));
        var plan = CraftingTransferPlan.createMaximum(recipe(1), inventory, grid, List.of(),
                s -> BigInteger.ZERO, BigInteger.ZERO);
        assertNotNull(plan);
        assertEquals(64, plan.grid().get(0).getCount());
        assertEquals(BigInteger.ZERO, plan.cost());
        assertEquals(64, grid.get(0).getCount());
    }
    @Test void identicalLearnedAndRecipeCandidatesAreQuotedOnlyOnce() {
        int[] quotes = {0};
        var known = Collections.nCopies(100, new ItemStack(Items.OAK_PLANKS));
        var plan = CraftingTransferPlan.create(recipe(1), empty(9), empty(9), known,
                s -> { quotes[0]++; return BigInteger.ONE; }, BigInteger.ONE, 1);
        assertNotNull(plan);
        assertEquals(1, quotes[0]);
        assertEquals(1, known.get(0).getCount());
    }
    @Test void learnedNbtVariantIsPreservedAndNotReplacedByAnUnknownPlainVariant() {
        ItemStack named = new ItemStack(Items.OAK_PLANKS);
        named.getOrCreateTag().putString("test", "known");
        var plan = CraftingTransferPlan.create(recipe(1), empty(1), empty(9), List.of(named),
                s -> s.hasTag() ? BigInteger.ONE : BigInteger.ZERO, BigInteger.ONE, 1);
        assertNotNull(plan); assertEquals(named.getTag(), plan.grid().get(0).getTag());
        plan.grid().get(0).getOrCreateTag().putString("test", "changed");
        assertEquals("known", named.getTag().getString("test"));
    }
    @Test void alternativesUseAnAffordableKnownVariant() {
        var ingredients = recipe(1);
        ingredients.set(0, Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS));
        var plan = CraftingTransferPlan.create(ingredients, empty(1), empty(9), List.of(),
                s -> s.is(Items.BIRCH_PLANKS) ? BigInteger.ONE : BigInteger.valueOf(100), BigInteger.TEN, 1);
        assertNotNull(plan); assertTrue(plan.grid().get(0).is(Items.BIRCH_PLANKS));
        assertEquals(BigInteger.ONE, plan.cost());
    }
    @Test void nonStackableInputsCannotBeFilledBeyondTheirLimit() {
        var ingredients = recipe(1); ingredients.set(0, Ingredient.of(Items.WOODEN_PICKAXE));
        assertNull(CraftingTransferPlan.create(ingredients, empty(1), empty(9), List.of(),
                s -> BigInteger.ONE, BigInteger.TEN, 2));
        assertNotNull(CraftingTransferPlan.create(ingredients, empty(1), empty(9), List.of(),
                s -> BigInteger.ONE, BigInteger.TEN, 1));
    }
}
