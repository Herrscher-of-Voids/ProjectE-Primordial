/**
 * 【文件职责】将转化桌光标中的一个容器副本提交给光标或玩家背包。
 * 【变更记录 #1】2026-10-10 03:53:52（第 1 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：堆叠拆分、背包容量预留和光标身份复核，只有交付容器成功才报告实际资源数量。
 * - 修改：复用统一 EMC 结算及服务端主线程，不接受客户端价格或实际数量。
 * - 用途：完整提供资源系统的真实交易与失败保护；可选 API 保持在兼容层。
 */
/**
 * 【变更记录 #2】2026-10-10 04:03:51（第 2 次备注 · 1.2.0-resource-system-one）
 * - 删除：从身份快照再次复制能力容器的冗余路径。
 * - 新增：从当前光标直接构造独立的一件交易副本。
 * - 修改：身份快照继续单独保存；能力副本不从可能丢失附加状态的二次快照复制。
 * - 用途：避免重复复制开销，便于验证真实容器能力与原容器身份分别受到保护。
 */
package com.primordial.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 调用者只修改 copy；commit 失败时原容器、背包均保持原状。 */
public final class CarriedContainerTransaction {
    private final ServerPlayer player;
    private final ItemStack original;
    private final ItemStack copy;
    private ResourceTradeResult.Status failure = ResourceTradeResult.Status.NO_TRANSFER;
    private boolean committed;

    public CarriedContainerTransaction(ServerPlayer player) {
        this.player = player;
        ItemStack carried = player.containerMenu.getCarried();
        original = carried.copy();
        copy = carried.copyWithCount(1);
    }
    public ItemStack copy() { return copy; }
    public ResourceTradeResult.Status failure() { return failure; }

    /** 结果必须仍是一个容器；先查找可完整容纳的背包槽，再同时提交背包和光标。 */
    public boolean commit(ItemStack result) {
        if (committed || result == null || result.isEmpty() || result.getCount() != 1) return false;
        ItemStack carried = player.containerMenu.getCarried();
        if (!ItemStack.matches(original, carried)) return false;
        if (original.getCount() == 1) {
            player.containerMenu.setCarried(result);
        } else {
            var inventory = player.getInventory();
            int destination = -1;
            for (int i = 0; i < inventory.items.size(); i++) {
                ItemStack stored = inventory.getItem(i);
                if (stored.isEmpty() || ItemStack.isSameItemSameTags(stored, result)
                        && stored.getCount() < Math.min(stored.getMaxStackSize(), inventory.getMaxStackSize())) {
                    destination = i;
                    break;
                }
            }
            if (destination < 0) {
                failure = ResourceTradeResult.Status.INVENTORY_FULL;
                return false;
            }
            ItemStack stored = inventory.getItem(destination);
            ItemStack inserted = stored.isEmpty() ? result.copy() : stored.copy();
            if (!stored.isEmpty()) inserted.grow(1);
            ItemStack remaining = original.copy();
            remaining.shrink(1);
            inventory.setItem(destination, inserted);
            player.containerMenu.setCarried(remaining);
            inventory.setChanged();
        }
        committed = true;
        return true;
    }
}

