/**
 * 【文件职责】验证合成整份产物放入真实槽位时的容量、NBT 和权限边界。
 * 【变更记录 #1】2026-10-11 23:25:00（第 1 次备注 · 1.2.0-crafting-jei-system）
 * - 删除：无。
 * - 新增：部分空间拒绝、合并空槽、满背包、NBT 隔离、锁定槽及物品上限的回归。
 * - 修改：使用真实 SimpleContainer、Slot 和 ItemStack，仅锁定槽覆盖放入条件。
 * - 用途：检查容量预判无副作用，保护结果槽取出前的原料与产物完整性。
 */
package com.primordial.service;

import com.primordial.machine.AdapterTestBootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CraftingOutputCapacityTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void partialSpaceRejectsTheWholeOutputAndChangesNothing() {
        var inventory = new SimpleContainer(new ItemStack(Items.STICK, 63));
        var output = new ItemStack(Items.STICK, 4);
        assertFalse(CraftingOutputCapacity.fits(output, List.of(new Slot(inventory, 0, 0, 0))));
        assertEquals(63, inventory.getItem(0).getCount());
        assertEquals(4, output.getCount());
    }

    @Test void mergeAndEmptySlotsTogetherCanFitTheEntireOutput() {
        var inventory = new SimpleContainer(2);
        inventory.setItem(0, new ItemStack(Items.STICK, 63));
        assertTrue(CraftingOutputCapacity.fits(new ItemStack(Items.STICK, 64),
                List.of(new Slot(inventory, 0, 0, 0), new Slot(inventory, 1, 0, 0))));
        assertEquals(63, inventory.getItem(0).getCount());
        assertTrue(inventory.getItem(1).isEmpty());
    }

    @Test void differentNbtCannotProvideMergeSpace() {
        var existing = new ItemStack(Items.STICK, 60);
        existing.getOrCreateTag().putString("variant", "named");
        var inventory = new SimpleContainer(existing);
        assertFalse(CraftingOutputCapacity.fits(new ItemStack(Items.STICK, 4),
                List.of(new Slot(inventory, 0, 0, 0))));
        assertTrue(CraftingOutputCapacity.fits(existing.copyWithCount(4),
                List.of(new Slot(inventory, 0, 0, 0))));
    }

    @Test void lockedSlotsAndContainerLimitsAreRespected() {
        var inventory = new SimpleContainer(2);
        var locked = new Slot(inventory, 0, 0, 0) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        };
        var small = new Slot(inventory, 1, 0, 0) {
            @Override public int getMaxStackSize() { return 3; }
        };
        assertFalse(CraftingOutputCapacity.fits(new ItemStack(Items.STICK, 4), List.of(locked, small)));
        assertTrue(CraftingOutputCapacity.fits(new ItemStack(Items.STICK, 3), List.of(locked, small)));
    }

    @Test void nonStackableAndFullInventoriesCannotFitExtraItems() {
        var inventory = new SimpleContainer(1);
        var slots = List.of(new Slot(inventory, 0, 0, 0));
        assertFalse(CraftingOutputCapacity.fits(new ItemStack(Items.WOODEN_PICKAXE, 2), slots));
        inventory.setItem(0, new ItemStack(Items.STICK, 64));
        assertFalse(CraftingOutputCapacity.fits(new ItemStack(Items.STICK), slots));
        assertFalse(CraftingOutputCapacity.fits(ItemStack.EMPTY, slots));
    }
}
