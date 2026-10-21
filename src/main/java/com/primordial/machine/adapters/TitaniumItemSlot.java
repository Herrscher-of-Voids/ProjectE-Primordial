/**
 * 【文件职责】把公开 Titanium 组件的单个槽转换为有自动化过滤的 IItemHandler。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增专用单槽视图。
 * - 新增：以组件内相对索引校验 getInsertPredicate/getExtractPredicate。
 * - 修改：避免 MultiInv 聚合槽偏移导致锁定/配方过滤判断错位，传输仍调用组件本身以触发内容回调。
 * - 用途：只由 IndustrialForegoingProxy 在选定面的公开组件中创建，不搜索其它槽或绕过禁用面。
 */
package com.primordial.machine.adapters;

import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import com.primordial.machine.SingleSlotHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Titanium 输入过滤器接收组件内索引；避免 MultiInv 聚合索引偏移后把锁定槽判断应用到其它槽。 */
final class TitaniumItemSlot implements IItemHandler {
    private final InventoryComponent<?> component;
    private final int relativeSlot;
    private final SingleSlotHandler view;

    TitaniumItemSlot(InventoryComponent<?> component, int relativeSlot) {
        this.component = component;
        this.relativeSlot = relativeSlot;
        this.view = new SingleSlotHandler(component, relativeSlot);
    }

    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return view.getStackInSlot(slot); }
    @Override public int getSlotLimit(int slot) { return view.getSlotLimit(slot); }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        view.getSlotLimit(slot); // 同时检查视图索引必须为 0。
        return component.getInsertPredicate().test(stack, relativeSlot);
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) return stack;
        return view.insertItem(slot, stack, simulate);
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack existing = view.getStackInSlot(slot);
        if (!component.getExtractPredicate().test(existing, relativeSlot)) return ItemStack.EMPTY;
        return view.extractItem(slot, amount, simulate);
    }
}
