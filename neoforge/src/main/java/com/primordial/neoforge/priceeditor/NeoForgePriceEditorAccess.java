/**
 * 【文件职责】提供创造目录显示策略，服务端统一判断定价器入口、菜单与每次网络修改的权限。
 * 【变更记录 #1】2026-10-23 16:56:25（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：任意游戏模式二级管理员，或单人存档主人创造模式的门禁。
 * - 修改：权限只读取真实 ServerPlayer 与服务器主人身份，LAN 访客不能凭创造模式绕过。
 * - 用途：客户端仅显示服务器同步的 editable；失权后立即关闭菜单并拒绝继续写价。
 */
package com.primordial.neoforge.priceeditor;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class NeoForgePriceEditorAccess {
    private NeoForgePriceEditorAccess() {}

    /** Client catalog policy only; this does not authorize opening or saving prices. */
    public static boolean canShowInCreative(Player player, boolean localOwner) {
        return player != null && player.isCreative() && (player.hasPermissions(2) || localOwner);
    }

    public static boolean canEdit(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.server.isSameThread()) return false;
        return serverPlayer.hasPermissions(2) || serverPlayer.isCreative()
                && serverPlayer.server.isSingleplayer()
                && serverPlayer.server.isSingleplayerOwner(serverPlayer.getGameProfile());
    }
}
