/**
 * 【文件职责】仅提交一件独立介质容器副本；拆堆时先保证背包能完整接收结果。
 * 【变更记录 #1】2026-10-23（2.0.0-neoforge-carried-media）
 * - 新增：菜单身份、完整组件/数量快照、单次提交和原子拆堆；模拟与执行使用不同副本。
 * - 用途：未成功交付容器时返回零实际量，由公共 EMC 事务退款，不触碰原容器。
 */
package com.primordial.neoforge.transmutation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class NeoForgeCarriedContainerTransaction {
    private final ServerPlayer player;
    private final AbstractContainerMenu menu;
    private final ItemStack original, copy;
    private boolean committed;
    private NeoForgeMediaTradeResult.Status failure = NeoForgeMediaTradeResult.Status.NO_TRANSFER;

    public NeoForgeCarriedContainerTransaction(ServerPlayer player) {
        this.player = player;
        menu = player.containerMenu;
        original = menu.getCarried().copy();
        copy = menu.getCarried().copyWithCount(1);
    }

    public ItemStack copy() { return copy; }
    public ItemStack simulationCopy() { return original.copyWithCount(1); }
    public NeoForgeMediaTradeResult.Status failure() { return failure; }
    public boolean unchanged() {
        return !committed && player.isAlive() && player.containerMenu == menu && menu.stillValid(player)
                && ItemStack.matches(original, menu.getCarried());
    }

    public boolean commit(ItemStack result) {
        if (result == null || result.isEmpty() || result.getCount() != 1 || !unchanged()) return false;
        // Materialize every copy before publishing either destination.
        ItemStack delivered = result.copy();
        if (original.getCount() == 1) menu.setCarried(delivered);
        else {
            var inventory = player.getInventory();
            int destination = -1;
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack stored = inventory.getItem(i);
                if (stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, delivered)
                        && stored.getCount() < Math.min(stored.getMaxStackSize(), inventory.getMaxStackSize())) {
                    destination = i;
                    break;
                }
            }
            if (destination < 0) {
                failure = NeoForgeMediaTradeResult.Status.INVENTORY_FULL;
                return false;
            }
            ItemStack stored = inventory.getItem(destination);
            ItemStack inserted = stored.isEmpty() ? delivered : stored.copyWithCount(stored.getCount() + 1);
            ItemStack remaining = original.copyWithCount(original.getCount() - 1);
            inventory.setItem(destination, inserted);
            menu.setCarried(remaining);
            inventory.setChanged();
        }
        committed = true;
        return true;
    }
}
