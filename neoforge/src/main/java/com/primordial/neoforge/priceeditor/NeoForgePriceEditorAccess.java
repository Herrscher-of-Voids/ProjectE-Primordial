/**
 * 【文件职责】服务端统一确认玩家实际持有定价器，供物品入口、菜单和每次网络修改复用。
 * 【变更记录 #1】2026-10-23 16:56:25（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：任意游戏模式二级管理员，或单人存档主人创造模式的门禁。
 * - 修改：权限只读取真实 ServerPlayer 与服务器主人身份，LAN 访客不能凭创造模式绕过。
 * - 用途：客户端仅显示服务器同步的 editable；失权后立即关闭菜单并拒绝继续写价。
 * 2026-11-11：按用户要求恢复生存获取与使用，取消 OP/创造限制；保留主线程与实际手持工具校验。
 */
package com.primordial.neoforge.priceeditor;

import com.primordial.neoforge.NeoForgeModRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class NeoForgePriceEditorAccess {
    private NeoForgePriceEditorAccess() {}

    /** 生存玩家也可使用；实际工具和服务端会话校验保留，管理员命令继续独立鉴权。 */
    public static boolean canEdit(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.server.isSameThread()) return false;
        return serverPlayer.getMainHandItem().is(NeoForgeModRegistries.PRICE_EDITOR_ITEM.get())
                || serverPlayer.getOffhandItem().is(NeoForgeModRegistries.PRICE_EDITOR_ITEM.get());
    }
}
