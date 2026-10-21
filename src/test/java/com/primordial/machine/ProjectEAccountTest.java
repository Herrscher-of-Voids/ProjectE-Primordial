/**
 * 【文件职责】用同版 ProjectE 默认知识提供器验证统一服务的整数账户提交和离线增量。
 * 【变更记录 #1】2026-09-15 15:49:31（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：大整数充扣、同步异常、写入失败保留队列与 capability 暂缺回归。
 * - 修改：世界和网络由测试替身提供；ProjectE getEmc/setEmc、兼容层及待同步 SavedData 使用真实实现。
 * - 用途：避免网络错误误判为入账失败，确保离线增量在实际写入成功后仅合并一次。
 */
package com.primordial.machine;

import com.primordial.compat.ProjectECompat;
import com.primordial.data.PrimordialEmcSavedData;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.math.BigInteger;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectEAccountTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void nativeBigIntegerBalanceCommitsEvenWhenClientSyncFails() throws Exception {
        try (var account = new Account()) {
            var huge = BigInteger.TEN.pow(100);
            account.knowledge.setEmc(huge);
            account.pending.add(account.owner, BigInteger.valueOf(7));
            doThrow(new IllegalStateException("disconnected")).when(account.knowledge).syncEmc(account.player);
            assertEquals(huge.add(BigInteger.valueOf(7)), ProjectECompat.get(account.server, account.owner));
            assertTrue(ProjectECompat.spend(account.server, account.owner, BigInteger.valueOf(3)));
            assertEquals(huge.add(BigInteger.valueOf(4)), account.knowledge.getEmc());
            assertEquals(BigInteger.ZERO, account.pending.get(account.owner));
            assertDoesNotThrow(() -> ProjectECompat.add(account.server, account.owner, BigInteger.valueOf(11)));
            assertEquals(huge.add(BigInteger.valueOf(15)), ProjectECompat.get(account.server, account.owner));
            assertFalse(ProjectECompat.spend(account.server, account.owner, huge.multiply(BigInteger.TWO)));
            assertEquals(huge.add(BigInteger.valueOf(15)), account.knowledge.getEmc());
        }
    }

    @Test void offlineDeltaSurvivesReloadAndFailedCapabilityWriteThenMergesOnce() throws Exception {
        try (var account = new Account()) {
            account.knowledge.setEmc(BigInteger.valueOf(50));
            when(account.players.getPlayer(account.owner)).thenReturn(null);
            ProjectECompat.add(account.server, account.owner, BigInteger.valueOf(17));
            account.pending = PrimordialEmcSavedData.load(account.pending.save(new CompoundTag()));
            when(account.players.getPlayer(account.owner)).thenReturn(account.player);
            doThrow(new IllegalStateException("capability unavailable")).when(account.knowledge).setEmc(any());
            assertThrows(IllegalStateException.class, () -> ProjectECompat.mergePending(account.player));
            assertEquals(BigInteger.valueOf(17), account.pending.get(account.owner));
            assertEquals(BigInteger.valueOf(50), account.knowledge.getEmc());
            doCallRealMethod().when(account.knowledge).setEmc(any());
            ProjectECompat.mergePending(account.player);
            ProjectECompat.mergePending(account.player);
            assertEquals(BigInteger.valueOf(67), account.knowledge.getEmc());
            assertEquals(BigInteger.ZERO, account.pending.get(account.owner));
        }
    }

    @Test void missingOnlineCapabilityQueuesCreditAndRejectsSpendingUntilRestored() throws Exception {
        try (var account = new Account()) {
            when(account.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)).thenReturn(LazyOptional.empty());
            ProjectECompat.add(account.server, account.owner, BigInteger.valueOf(9));
            assertEquals(BigInteger.valueOf(9), account.pending.get(account.owner));
            assertFalse(ProjectECompat.spend(account.server, account.owner, BigInteger.ONE));
            when(account.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)).thenReturn(LazyOptional.of(() -> account.knowledge));
            assertEquals(BigInteger.valueOf(9), ProjectECompat.get(account.server, account.owner));
            ProjectECompat.add(account.server, account.owner, BigInteger.ONE);
            assertEquals(BigInteger.TEN, account.knowledge.getEmc());
            assertEquals(BigInteger.ZERO, account.pending.get(account.owner));
        }
    }

    private static final class Account implements AutoCloseable {
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final PlayerList players = mock(PlayerList.class);
        final UUID owner = UUID.randomUUID();
        final IKnowledgeProvider knowledge = spy(KnowledgeImpl.getDefault());
        PrimordialEmcSavedData pending = new PrimordialEmcSavedData();
        final MockedStatic<PrimordialEmcSavedData> stores = mockStatic(PrimordialEmcSavedData.class, CALLS_REAL_METHODS);
        Account() throws Exception {
            stores.when(() -> PrimordialEmcSavedData.get(server)).thenAnswer(call -> pending);
            when(server.getPlayerList()).thenReturn(players);
            when(players.getPlayer(owner)).thenReturn(player);
            when(player.getUUID()).thenReturn(owner);
            when(player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)).thenReturn(LazyOptional.of(() -> knowledge));
            var field = ServerPlayer.class.getDeclaredField("server");
            field.setAccessible(true); field.set(player, server);
            doNothing().when(knowledge).syncEmc(player);
        }
        @Override public void close() { stores.close(); }
    }
}
