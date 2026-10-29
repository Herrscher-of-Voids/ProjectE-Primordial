/**
 * 【文件职责】统一定价器的创造模式与管理员权限门禁；命令使用独立的管理员权限规则。
 * 【变更记录 #1】2026-10-11 04:28:00（第 1 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：同时要求创造模式与 permission level 2 的统一校验。
 * - 修改：物品入口、菜单、选样与保存包、GUI 审计共用此规则。
 * - 用途：服务端重新核验当前游戏模式和权限，防止打开菜单后切换生存模式继续改价。
 * 【变更记录 #2】2026-10-23 16:57:02（第 2 次备注 · 2.0.0-neoforge-pricing 双版本权限对齐）
 * - 删除：管理员必须同时处于创造模式的限制。
 * - 新增：OP 2 级可在任意游戏模式使用；单人存档主人创造模式也可使用，LAN 访客不继承主人特权。
 * - 修改：物品、菜单、选样、保存和审计继续共用本方法，服务端每次按当前身份复核。
 * - 用途：与用户确认的 1.21.1 规则同步；专用服务器非 OP 即使创造也不能改公共价格。
 */
package com.primordial.service;

import net.minecraft.world.entity.player.Player;

public final class PriceEditorAccess {
    private PriceEditorAccess() {}

    public static boolean canEdit(Player player) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        if (!player.isCreative()) return false;
        var server = player.getServer();
        return server != null && !server.isDedicatedServer()
                && server.isSingleplayerOwner(player.getGameProfile());
    }
}
