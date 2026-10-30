/**
 * 【文件职责】仅在 GameTest 批次开始前通过真实登录完成 ProjectE 原生 EMC 映射初始化。
 * 【变更记录 #1】2026-10-22 21:52:16（第 1 次备注 · 2.0.0-neo-dev 回收测试初始化）
 * - 删除：无。
 * - 新增：普通、配置、价格与独立重启批次的 BeforeBatch 登录夹具及完整退出/连接释放。
 * - 修改：测试登录使用 NeoForge configureMockConnection 协商；已能读取原生钻石售价时不重复登录。
 * - 用途：独立运行任何回收批次均获得真实 ProjectE 映射，不写入假 EMC 价格或绕过生产生命周期。
 */
package com.primordial.neoforge;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeRecoveryTestSetup {
    private NeoForgeRecoveryTestSetup() {}

    @BeforeBatch(batch = "defaultBatch")
    public static void beforeDefaultBatch(ServerLevel level) {
        initializeNativeMapping(level.getServer());
    }

    @BeforeBatch(batch = "blackhole_resource_budget")
    public static void beforeResourceBudgetBatch(ServerLevel level) {
        initializeNativeMapping(level.getServer());
    }

    @BeforeBatch(batch = "primordial_prices")
    public static void beforePriceBatch(ServerLevel level) {
        initializeNativeMapping(level.getServer());
    }

    @BeforeBatch(batch = "blackhole_entity_config")
    public static void beforeEntityConfigBatch(ServerLevel level) {
        initializeNativeMapping(level.getServer());
    }

    @BeforeBatch(batch = "blackhole_knowledge_persistence")
    public static void beforeKnowledgeRestartBatch(ServerLevel level) {
        initializeNativeMapping(level.getServer());
    }

    @BeforeBatch(batch = "emc_credit_persistence")
    public static void beforeCreditRestartBatch(ServerLevel level) {
        initializeNativeMapping(level.getServer());
    }

    /** 和正式玩家一致地触发登录事件与原生映射发送；退出后不留下在线测试玩家或内存连接。 */
    private static void initializeNativeMapping(MinecraftServer server) {
        if (IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromItem(Items.DIAMOND)) > 0) return;
        GameProfile profile = new GameProfile(UUID.randomUUID(), "recovery-setup");
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(),
                false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            if (IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromItem(Items.DIAMOND)) <= 0) {
                throw new GameTestAssertException("Native ProjectE diamond sell mapping must be available after a real login");
            }
        } finally {
            try {
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
            } finally {
                channel.finishAndReleaseAll();
            }
        }
    }
}
