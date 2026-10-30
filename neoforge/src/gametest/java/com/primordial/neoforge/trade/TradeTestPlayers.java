/**
 * 【文件职责】为受控交易 GameTest 创建真实 ProjectE 玩家、协商内存连接并正常保存退出。
 * 【变更记录 #1】2026-10-23 11:40:58（第 1 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无。
 * - 新增：真实登录/退出、知识能力读取和断言工具。
 * - 修改：测试使用 PlayerList 与已协商的 NeoForge 连接，拒绝用假余额或假知识替代原生附件。
 * - 用途：正常退出保存后可测离线知识与再次登录，连接资源由 finally 释放，不打包进生产 JAR。
 */
package com.primordial.neoforge.trade;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class TradeTestPlayers {
    private TradeTestPlayers() {}

    static ServerPlayer login(MinecraftServer server) {
        return login(server, new GameProfile(UUID.randomUUID(), "resource-trader"));
    }

    static ServerPlayer login(MinecraftServer server, GameProfile profile) {
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(),
                false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            try {
                logout(server, player);
            } finally {
                channel.finishAndReleaseAll();
            }
            throw failure;
        }
    }

    static void logout(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        try {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }

    static IKnowledgeProvider knowledge(ServerPlayer player) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(knowledge != null, "The actual ProjectE player attachment must supply knowledge and EMC");
        return knowledge;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
