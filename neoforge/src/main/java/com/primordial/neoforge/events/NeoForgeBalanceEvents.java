/**
 * 【文件职责】玩家登录及每 10 tick 先提交待结算整数，再同步原初 HUD；退出/停服清理会话缓存。
 * 【变更记录 #1】2026-10-22 21:10:40（第 1 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：UUID 离线代理读取、缺少能力时伪装零余额和 HashMap。
 * - 新增：登录与轮询结算、真实在线能力读取、退出/停服清理。
 * - 修改：余额同步发生在账本尝试提交之后；网络异常不回滚已写入的 EMC。
 * - 用途：离线积累可在登录后恢复，HUD 与真实 ProjectE 账户保持一致。
 * 【变更记录 #2】2026-10-22 21:44:20（第 2 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：登录、十 tick 轮询的回收知识补学及停服会话清理。
 * - 修改：EMC 与知识分别确认，通知失败分别重试。
 * - 用途：离线卖入物品可以在真实玩家登录后学习。
 */
package com.primordial.neoforge.events;

import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.network.SyncBalancePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeBalanceEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, BigInteger> LAST_SENT = new ConcurrentHashMap<>();
    private static final Set<UUID> SYNC_WARNED = ConcurrentHashMap.newKeySet();

    private NeoForgeBalanceEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        NeoForgeEmcCredits.flush(player);
        NeoForgeRecoveryKnowledge.flush(player);
        syncBalance(player, true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // ProjectE 的 setEmc 不发余额事件；沿用每 10 tick 的同步周期并重试离线待入账。
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            NeoForgeEmcCredits.flush(player);
            NeoForgeRecoveryKnowledge.flush(player);
            syncBalance(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
        NeoForgeEmcCredits.forget(event.getEntity().getUUID());
        SYNC_WARNED.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
        SYNC_WARNED.clear();
        NeoForgeEmcCredits.clearSession();
        NeoForgeRecoveryKnowledge.clearSession();
    }

    private static void syncBalance(ServerPlayer player, boolean force) {
        try {
            BigInteger emc = NeoForgeEmcCredits.onlineBalance(player);
            if (force || !emc.equals(LAST_SENT.get(player.getUUID()))) {
                PacketDistributor.sendToPlayer(player, new SyncBalancePayload(emc));
                LAST_SENT.put(player.getUUID(), emc);
            }
            SYNC_WARNED.remove(player.getUUID());
        } catch (RuntimeException | LinkageError failure) {
            if (SYNC_WARNED.add(player.getUUID())) LOGGER.error(
                    "Could not sync ProjectE EMC for player {}; account credit is unaffected",
                    player.getGameProfile().getName(), failure);
        }
    }
}
