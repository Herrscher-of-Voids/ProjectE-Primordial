/**
 * 【文件职责】将既有 IItemHandler 的一个明确槽映射成视图槽 0。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增通用精确槽包装。
 * - 新增：构造时索引校验及非 0 视图索引拒绝。
 * - 修改：模拟、实际插入、提取、过滤和容量全部转交同一原槽。
 * - 用途：旧任务算法即便遍历视图，也只会看到目标槽；无旁路 setStackInSlot。
 */
package com.primordial.machine;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** 将外部能力的指定槽映射为唯一的槽 0；容量、过滤、模拟和执行仍交给原能力处理。 */
public final class SingleSlotHandler implements IItemHandler {
    private final IItemHandler source;
    private final int target;

    public SingleSlotHandler(IItemHandler source, int target) {
        if (target < 0 || target >= source.getSlots()) throw new IllegalArgumentException("Invalid target slot");
        this.source = source;
        this.target = target;
    }

    private void check(int slot) {
        if (slot != 0) throw new IndexOutOfBoundsException("Single-slot view: " + slot);
    }

    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { check(slot); return source.getStackInSlot(target); }
    @Override public int getSlotLimit(int slot) { check(slot); return source.getSlotLimit(target); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { check(slot); return source.isItemValid(target, stack); }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        check(slot);
        return source.insertItem(target, stack, simulate);
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        check(slot);
        return source.extractItem(target, amount, simulate);
    }
}
