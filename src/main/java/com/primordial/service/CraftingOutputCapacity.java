/**
 * 【文件职责】在合成结果 Shift 转移前只读模拟目标槽，要求整份产物能放入背包。
 * 【变更记录 #1】2026-10-11 23:25:00（第 1 次备注 · 1.2.0-crafting-jei-system）
 * - 删除：无。
 * - 新增：按物品/NBT、槽位放入权限和堆叠上限计算整份产物的空间。
 * - 修改：TransmutationCraftingMixin 先检查空间，再调用真实转移及原版结果槽消耗。
 * - 用途：避免仅交付部分产物却消耗整份原料；失败不改背包、网格和结果槽。
 */
package com.primordial.service;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public final class CraftingOutputCapacity {
    private CraftingOutputCapacity() {}

    public static boolean fits(ItemStack output, List<Slot> slots) {
        if (output == null || output.isEmpty()) return false;
        int remaining = output.getCount();
        for (Slot slot : slots) {
            if (!slot.mayPlace(output)) continue;
            ItemStack existing = slot.getItem();
            if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, output)) continue;
            int limit = Math.min(output.getMaxStackSize(), slot.getMaxStackSize(output));
            remaining -= Math.max(0, limit - (existing.isEmpty() ? 0 : existing.getCount()));
            if (remaining <= 0) return true;
        }
        return false;
    }
}
