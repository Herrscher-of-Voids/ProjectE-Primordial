/**
 * 【文件职责】在物理客户端只读识别本机创造模式存档主人，供定价器创造目录展示使用。
 * 【变更记录 #1】2026-10-23 17:01:51（第 1 次备注 · 2.0.0-neoforge-pricing 双版本权限对齐）
 * - 删除：无。
 * - 新增：当前本地玩家、创造模式及 integrated server 原生主人身份三重检查。
 * - 修改：仅由 ModCreativeTabs 的客户端分支反射调用，专用服务器不载入本类。
 * - 用途：未开作弊的主人仍能从创造栏取工具；联网访客没有本机集成服务器，展示结果不构成服务端授权。
 */
package com.primordial.client;

import net.minecraft.client.Minecraft;

public final class CreativePriceEditorVisibility {
    private CreativePriceEditorVisibility() {}

    public static boolean isLocalCreativeOwner() {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var server = minecraft.getSingleplayerServer();
        return player != null && player.isCreative() && server != null
                && server.isSingleplayerOwner(player.getGameProfile());
    }
}
