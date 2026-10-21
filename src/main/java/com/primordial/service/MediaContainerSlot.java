/**
 * 【文件职责】介质页真实 FE 充电槽与非破坏性遗忘槽。
 * 【变更记录 #1】2026-10-12 23:39:35（第 1 次备注 · 1.2.0-media-real-slots）
 * - 删除：无。新增：单容器槽权限、服务端遗忘与原生取回。
 * - 修改：菜单接口提供跨端页签状态；用途：隔离 Mixin 和槽实现，容器内容不被消耗。
 */
package com.primordial.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class MediaContainerSlot extends Slot {
    private final MediaChargingMenu menu;
    private final Player player;
    private final boolean unlearn;

    public MediaContainerSlot(Container inventory, int index, int x, int y,
            MediaChargingMenu menu, Player player, boolean unlearn) {
        super(inventory, index, x, y);
        this.menu = menu;
        this.player = player;
        this.unlearn = unlearn;
    }
    @Override public boolean isActive() { return menu.primordial$mediaPage(); }
    @Override public boolean mayPlace(ItemStack stack) {
        return isActive() && (unlearn ? !hasItem() : stack.getCapability(ForgeCapabilities.ENERGY).isPresent());
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public void set(ItemStack stack) {
        super.set(stack);
        if (unlearn && !stack.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            boolean forgotten = MediaSlotTransactions.forget(serverPlayer, stack, menu.primordial$gasPage());
            player.displayClientMessage(Component.translatable(forgotten
                    ? "message.primordial.media_forgotten" : "message.primordial.media_forget_failed"), true);
        }
    }
}
