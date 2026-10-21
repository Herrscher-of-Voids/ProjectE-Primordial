/**
 * 【文件职责】回归背包补充的暂停、恢复、存档和服务端网络入口，执行真实 PlayerInventoryTask。
 * 【变更记录 #1】2026-09-15 11:10:15（第 1 次备注 · 1.0.0-player-inventory-pause）
 * - 删除：无；新增暂停功能的行为测试。
 * - 新增：固定/补充两模式暂停后空槽不再生成、不扣 EMC，重载仍暂停，恢复后按原样本补充。
 * - 修改：仅用替身提供世界和 EMC 服务，SavedData、背包、协议编解码和任务循环运行生产实现。
 * - 用途：防止旧规则持续重建物品，并验证批量启停不改其它玩家、编辑不意外启用和过期包被拒绝。
 */
/**
 * 【变更记录 #2】2026-09-15 15:10:05（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：旧 itemGenCostEmc 价格替身。
 * - 新增：真实统一购买服务和找零存储参与暂停/恢复回归。
 * - 修改：保持原有空槽、规则重载、费用和暂停期间零交互断言。
 * - 用途：确认统一结算不会重新启用暂停规则或创建充能物品。
 */
package com.primordial.machine;

import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.data.SampleMode;
import com.primordial.menu.PlayerInventoryMenu;
import com.primordial.network.C2SPlayerInventoryRulePacket;
import com.primordial.network.C2SSetPlayerInventoryEnabledPacket;
import com.primordial.service.EmcService;
import com.primordial.task.PlayerInventoryTask;
import com.primordial.task.TaskScheduler;
import com.primordial.value.ValueManager;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerInventoryPauseTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    /** 模拟丢弃后空槽的真实任务循环；暂停时连估值和知识查询也不应触发。 */
    @ParameterizedTest @EnumSource(SampleMode.class)
    void pauseStopsGenerationAndSpendingAcrossReloadAndResume(SampleMode mode) {
        var server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        var playerList = mock(PlayerList.class);
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        UUID owner = UUID.randomUUID();
        Inventory inventory = new Inventory(player);
        when(server.getPlayerList()).thenReturn(playerList);
        when(playerList.getPlayers()).thenReturn(List.of(player));
        when(player.getUUID()).thenReturn(owner);
        when(player.getInventory()).thenReturn(inventory);
        when(player.level()).thenReturn(level);
        when(level.getGameTime()).thenReturn(200L);

        var data = new AtomicReference<>(new PlayerInventoryConfigSavedData());
        ItemStack filter = new ItemStack(Items.DIAMOND);
        filter.getOrCreateTag().putString("rule_sample", "retain-on-pause");
        data.get().set(owner, 0, filter, 3, 20, mode, true);
        try (var stores = mockStatic(PlayerInventoryConfigSavedData.class);
             var emc = mockStatic(EmcService.class, CALLS_REAL_METHODS);
             var changes = mockStatic(com.primordial.data.EmcConversionSavedData.class);
             var prices = mockStatic(ValueManager.class)) {
            stores.when(() -> PlayerInventoryConfigSavedData.get(server)).thenAnswer(call -> data.get());
            changes.when(() -> com.primordial.data.EmcConversionSavedData.get(server)).thenReturn(new com.primordial.data.EmcConversionSavedData());
            emc.when(() -> EmcService.balance(server, owner)).thenReturn(BigInteger.TEN.pow(20));
            stores.when(() -> PlayerInventoryConfigSavedData.load(any(CompoundTag.class))).thenCallRealMethod();
            emc.when(() -> EmcService.hasKnowledge(eq(server), eq(owner), any(ItemStack.class))).thenReturn(true);
            emc.when(() -> EmcService.spend(eq(server), eq(owner), any(BigInteger.class))).thenReturn(true);
            prices.when(() -> ValueManager.itemValueEmc(any(ItemStack.class), eq(server))).thenReturn(BigInteger.TEN);

            PlayerInventoryTask.run(server);
            int expected = mode == SampleMode.KEEP_LEVEL ? filter.getMaxStackSize() : 3;
            assertEquals(expected, inventory.getItem(0).getCount());
            emc.verify(() -> EmcService.spend(server, owner, BigInteger.valueOf(expected * 10L)));

            data.get().setEnabled(owner, 0, false);
            inventory.setItem(0, ItemStack.EMPTY);
            data.set(PlayerInventoryConfigSavedData.load(data.get().save(new CompoundTag())));
            assertFalse(data.get().get(owner, 0).enabled());
            assertEquals(mode, data.get().get(owner, 0).mode());
            emc.clearInvocations();
            prices.clearInvocations();
            for (long tick : new long[]{201L, 220L, 1000L}) {
                when(level.getGameTime()).thenReturn(tick);
                PlayerInventoryTask.run(server);
            }
            assertTrue(inventory.getItem(0).isEmpty(), "Paused rule must not recreate a discarded item");
            emc.verifyNoInteractions();
            prices.verifyNoInteractions();

            data.get().setEnabled(owner, 0, true);
            PlayerInventoryTask.run(server);
            assertEquals(expected, inventory.getItem(0).getCount());
            assertEquals(filter.getTag(), inventory.getItem(0).getTag());
            emc.verify(() -> EmcService.spend(server, owner, BigInteger.valueOf(expected * 10L)));
        } finally {
            TaskScheduler.dropTimers("player:" + owner);
        }
    }

    @Test void absentRulesAreNotCreatedAndPauseDoesNotChangeOtherPlayers() {
        var data = new PlayerInventoryConfigSavedData();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var filter = new ItemStack(Items.DIAMOND_SWORD);
        filter.setDamageValue(12);
        data.set(owner, 35, filter, 1, 40, SampleMode.FIXED_RATE, true);
        data.set(other, 35, filter, 1, 40, SampleMode.FIXED_RATE, true);
        data.setDirty(false);
        data.setEnabled(owner, 0, false);
        assertNull(data.get(owner, 0));
        assertFalse(data.isDirty());
        data.setEnabled(owner, 35, false);
        assertTrue(data.isDirty());
        var reloaded = PlayerInventoryConfigSavedData.load(data.save(new CompoundTag()));
        var rule = reloaded.get(owner, 35);
        assertFalse(rule.enabled());
        assertEquals(12, rule.filter().getDamageValue());
        assertEquals(40, rule.intervalTicks());
        assertEquals(1, rule.targetCount());
        assertEquals(SampleMode.FIXED_RATE, rule.mode());
        assertTrue(reloaded.get(other, 35).enabled());
    }

    @Test void batchPauseWorksWithEmptyInventoryAndResumeKeepsSamples() {
        var player = mock(ServerPlayer.class);
        var menu = mock(PlayerInventoryMenu.class);
        player.containerMenu = menu;
        UUID owner = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        var data = new PlayerInventoryConfigSavedData();
        data.set(owner, 0, new ItemStack(Items.IRON_INGOT), 5, true);
        data.set(owner, 35, new ItemStack(Items.DIAMOND), 1, true);
        var context = immediateContext(player);
        try (var stores = mockStatic(PlayerInventoryConfigSavedData.class)) {
            stores.when(() -> PlayerInventoryConfigSavedData.get(player.server)).thenReturn(data);
            C2SSetPlayerInventoryEnabledPacket.handle(new C2SSetPlayerInventoryEnabledPacket(menu.containerId,
                    IntStream.range(0, 36).toArray(), false), () -> context);
            assertFalse(data.get(owner, 0).enabled());
            assertFalse(data.get(owner, 35).enabled());
            assertEquals(2, data.rules(owner).size());
            C2SSetPlayerInventoryEnabledPacket.handle(new C2SSetPlayerInventoryEnabledPacket(menu.containerId,
                    new int[]{35}, true), () -> context);
            assertTrue(data.get(owner, 35).enabled());
            assertFalse(data.get(owner, 0).enabled());
            assertEquals(Items.DIAMOND, data.get(owner, 35).filter().getItem());
            verify(menu, times(2)).broadcastChanges();
            verify(player, never()).getInventory();
        }
    }

    @Test void editingPausedRuleDoesNotResumeIt() {
        var player = mock(ServerPlayer.class);
        var menu = mock(PlayerInventoryMenu.class);
        player.containerMenu = menu;
        UUID owner = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        var data = new PlayerInventoryConfigSavedData();
        var item = new ItemStack(Items.IRON_INGOT);
        data.set(owner, 9, item, 1, false);
        var context = immediateContext(player);
        try (var stores = mockStatic(PlayerInventoryConfigSavedData.class)) {
            stores.when(() -> PlayerInventoryConfigSavedData.get(player.server)).thenReturn(data);
            C2SPlayerInventoryRulePacket.handle(new C2SPlayerInventoryRulePacket(menu.containerId, 9, 12,
                    40, SampleMode.FIXED_RATE, true, item), () -> context);
            assertFalse(data.get(owner, 9).enabled());
            assertEquals(12, data.get(owner, 9).targetCount());
            assertEquals(40, data.get(owner, 9).intervalTicks());
            verify(menu).broadcastChanges();
        }
    }

    @Test void closedAndStaleMenusCannotChangeRules() {
        var player = mock(ServerPlayer.class);
        var context = immediateContext(player);
        try (var stores = mockStatic(PlayerInventoryConfigSavedData.class)) {
            C2SSetPlayerInventoryEnabledPacket.handle(new C2SSetPlayerInventoryEnabledPacket(3, new int[]{0}, false), () -> context);
            var menu = mock(PlayerInventoryMenu.class);
            player.containerMenu = menu;
            C2SSetPlayerInventoryEnabledPacket.handle(new C2SSetPlayerInventoryEnabledPacket(menu.containerId + 1,
                    new int[]{0}, false), () -> context);
            C2SPlayerInventoryRulePacket.handle(new C2SPlayerInventoryRulePacket(menu.containerId + 1, 0, 1,
                    1, SampleMode.KEEP_LEVEL, true, new ItemStack(Items.DIAMOND)), () -> context);
            stores.verifyNoInteractions();
            verify(menu, never()).broadcastChanges();
        }
    }

    @Test void pauseRequestRoundTripRetainsMenuSlotsAndState() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            int[] slots = {0, 9, 35};
            var request = new C2SSetPlayerInventoryEnabledPacket(11, slots, false);
            slots[0] = 27;
            C2SSetPlayerInventoryEnabledPacket.encode(request, buf);
            var decoded = C2SSetPlayerInventoryEnabledPacket.decode(buf);
            assertEquals(11, decoded.containerId());
            assertArrayEquals(new int[]{0, 9, 35}, decoded.slots());
            assertFalse(decoded.enabled());
            assertEquals(0, buf.readableBytes());
        } finally { buf.release(); }
    }

    @Test void oversizedAndOutOfRangeRequestsAreRejected() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(1).writeByte(37);
            assertThrows(DecoderException.class, () -> C2SSetPlayerInventoryEnabledPacket.decode(buf));
            buf.clear();
            buf.writeVarInt(1).writeByte(1).writeByte(36).writeBoolean(false);
            assertThrows(DecoderException.class, () -> C2SSetPlayerInventoryEnabledPacket.decode(buf));
            assertThrows(IllegalArgumentException.class,
                    () -> new C2SSetPlayerInventoryEnabledPacket(1, new int[]{-1}, false));
        } finally { buf.release(); }
    }

    private static NetworkEvent.Context immediateContext(ServerPlayer player) {
        var context = mock(NetworkEvent.Context.class);
        when(context.getSender()).thenReturn(player);
        when(context.enqueueWork(any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        return context;
    }
}
