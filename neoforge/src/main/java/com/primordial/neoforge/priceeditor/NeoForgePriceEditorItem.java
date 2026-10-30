/**
 * 【文件职责】独立定价器的空气右键入口；只在服务端授权后打开管理菜单。
 * 【变更记录 #1】2026-10-23 16:56:25（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：NeoForge 原生 openMenu 与失败交互反馈，不移动或消耗工具。
 * - 修改：统一调用服务端权限门禁，不用客户端单人模式推测编辑能力。
 * - 用途：将管理员价格编辑与尚未迁移的配置器分开，菜单和保存包仍分别复核权限。
 */
package com.primordial.neoforge.priceeditor;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class NeoForgePriceEditorItem extends Item {
    public NeoForgePriceEditorItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!NeoForgePriceEditorAccess.canEdit(player)) {
                player.displayClientMessage(Component.translatable("message.primordial.price_permission"), true);
                return InteractionResultHolder.fail(player.getItemInHand(hand));
            }
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, ignored) ->
                    new NeoForgePriceEditorMenu(id, inventory), Component.translatable("screen.primordial.custom_price")));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
