/**
 * 【文件职责】在 NeoForge 物理客户端识别创造模式管理员或本机存档主人，仅服务定价器创造目录展示。
 * 【变更记录 #1】2026-10-23 17:01:51（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。
 * - 新增：当前本地玩家、创造模式及 integrated server 原生主人身份检查。
 * - 修改：NeoForgeCertificates 先核对运行端再反射调用，专用服务器不载入本类。
 * - 用途：关闭作弊的本地主人也可取定价器，LAN 访客不继承；服务端菜单和写价仍独立授权。
 */
package com.primordial.neoforge.client;

import net.minecraft.client.Minecraft;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorAccess;

public final class NeoForgeCreativePriceEditorVisibility {
    private NeoForgeCreativePriceEditorVisibility() {}

    public static boolean canShowPriceEditor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;
        var player = minecraft.player;
        var server = minecraft.getSingleplayerServer();
        boolean localOwner = player != null && server != null && server.isSingleplayer()
                && server.isSingleplayerOwner(player.getGameProfile());
        return NeoForgePriceEditorAccess.canShowInCreative(player, localOwner);
    }
}
