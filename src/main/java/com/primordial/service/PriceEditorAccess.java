/**
 * 【文件职责】统一定价器的创造模式与管理员权限门禁；命令使用独立的管理员权限规则。
 * 【变更记录 #1】2026-10-11 04:28:00（第 1 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：同时要求创造模式与 permission level 2 的统一校验。
 * - 修改：物品入口、菜单、选样与保存包、GUI 审计共用此规则。
 * - 用途：服务端重新核验当前游戏模式和权限，防止打开菜单后切换生存模式继续改价。
 */
package com.primordial.service;

import net.minecraft.world.entity.player.Player;

public final class PriceEditorAccess {
    private PriceEditorAccess() {}

    public static boolean canEdit(Player player) {
        return player != null && player.isCreative() && player.hasPermissions(2);
    }
}
