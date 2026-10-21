/**
 * 【文件职责】运行生产绑定、确认、执行和退款流程；世界、网络传输和账户用替身提供边界。
 * 【变更记录 #1】2026-09-16 13:35:51（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：2/4 倍实际扣款、预算不足退款、余额不足停用、三档高倍率取消/重放/换物品拒绝。
 * - 修改：测试保留真实 NBT 和服务逻辑，只用可计数 ticker 代替第三方机器内部工作。
 * - 用途：防止只测试倍率数学而遗漏确认门禁、停用和重复目标的实际行为。
 */
/**
 * 【变更记录 #2】2026-09-16 14:46:36（第 2 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：以模拟 EmcService 的内存数代替真实账户扣款的测试前提。
 * - 新增：实际 ProjectE KnowledgeImpl 账户、多目标独立倍率/费用、批量确认、轮转和旧绑定迁入验证。
 * - 修改：费用改按倍率乘 1000；网络传输和世界继续使用替身，生产消费及退款链完整执行。
 * - 用途：直接证明大整数 EMC 会扣除，区分紧凑 HUD 不变与账户没有变化。
 */
/**
 * 【文件职责】验证加速激活、真实ProjectE扣款和多目标执行。
 * 【变更记录 #3】2026-09-16 19:46:19（第 3 次备注 · 1.0.0-review-ready）
 * - 删除：旧绑定自动继续运行的预期。
 * - 新增：599/600 tick边界、重新激活、确认等待不计时、共享额度及到期追加目标回归。
 * - 修改：使用真实KnowledgeImpl，剩余额度裁剪和限流退款共同验证。
 * - 用途：确保每轮不超过21亿EMC且到期不继续调用机器。
 */
/**
 * 【变更记录 #4】2026-09-18 01:09:18（第 4 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：倍率乘 1000 EMC/tick 及限流重复向上取整的费用预期。
 * - 新增：真实 EmcConversionSavedData 找零夹具，供专门的秒计费回归复用。
 * - 修改：所有真实 ProjectE 扣款断言按新价格更新；21 亿边界使用接近上限的对应额度。
 * - 用途：继续覆盖多目标、确认、限流退款和时限，验证生产结算链实际减少原生余额。
 */
package com.primordial.service;

import com.primordial.item.EmcAcceleratorItem;
import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.network.S2CAccelerationWarningPacket;
import com.primordial.data.PrimordialEmcSavedData;
import com.primordial.data.EmcConversionSavedData;
import com.primordial.util.BalanceSyncer;
import com.primordial.util.EmcFormatter;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccelerationFlowTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(ints = {2, 4})
    void actualCallsAndEmcMatchConfiguredMultiplier(int rate) throws Exception {
        try (var f = new Fixture(rate)) {
            f.bind();
            f.run(4096);
            assertEquals(rate - 1, f.calls.get());
            assertEquals(BigInteger.valueOf(10_000_000 - rate * 50L), f.knowledge.getEmc());
            f.bind(); // 同一个目标再次右键暂停。
            f.run(4096);
            assertEquals(rate - 1, f.calls.get());
            assertFalse(f.enabled());
        }
    }

    @Test void throttledWorkRefundsAndEmptyBalancePauses() throws Exception {
        try (var f = new Fixture(4)) {
            f.bind(); f.run(1);
            assertEquals(1, f.calls.get());
            assertEquals(BigInteger.valueOf(9_999_933), f.knowledge.getEmc());
            f.knowledge.setEmc(BigInteger.ZERO);
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(1, f.calls.get());
        }
    }

    @Test void missingOrChangedTargetDoesNotSpendOrRun() throws Exception {
        try (var f = new Fixture(4)) {
            f.bind();
            f.targets.when(() -> AccelerationTarget.read(f.level, f.pos)).thenReturn(null);
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(0, f.calls.get());
            assertEquals(BigInteger.valueOf(10_000_000), f.knowledge.getEmc());
        }
    }

    @Test void sameTargetOnlyRunsOnceWithinSharedBudget() throws Exception {
        try (var f = new Fixture(4)) {
            f.bind();
            var budget = new AccelerationBudget(64, 4096, 2000, () -> 0L);
            EmcAccelerationService.process(f.server, f.player, f.stack, budget);
            EmcAccelerationService.process(f.server, f.player, f.stack, budget);
            assertEquals(3, f.calls.get());
            assertEquals(BigInteger.valueOf(9_999_800), f.knowledge.getEmc());
        }
    }

    @ParameterizedTest @ValueSource(ints = {256, 512, 1024})
    void eachHighRateRequiresFreshExplicitConfirmation(int rate) throws Exception {
        try (var f = new Fixture(rate)) {
            f.bind();
            UUID first = f.warning.get().token();
            f.run(4096);
            assertEquals(0, f.calls.get());
            assertFalse(f.enabled());
            EmcAccelerationService.confirm(f.player, UUID.randomUUID(), true);
            assertFalse(f.enabled());
            EmcAccelerationService.confirm(f.player, first, false);
            assertFalse(f.enabled());
            EmcAccelerationService.confirm(f.player, first, true);
            assertFalse(f.enabled());
            f.bind();
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            assertTrue(f.enabled());
            f.run(4096);
            assertEquals(rate - 1, f.calls.get());
            assertEquals(BigInteger.valueOf(10_000_000 - rate * 50L), f.knowledge.getEmc());
            f.bind(); // 停用后的下次启动重新弹窗。
            f.bind();
            assertFalse(f.enabled());
            EmcAccelerationService.confirm(f.player, first, true);
            assertFalse(f.enabled());
        }
    }

    @Test void changingHeldItemOrMultiplierInvalidatesConsent() throws Exception {
        try (var f = new Fixture(256)) {
            f.bind();
            when(f.player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(ItemStack.EMPTY);
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            assertFalse(f.enabled());
            when(f.player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(f.stack);
            f.bind();
            UUID token = f.warning.get().token();
            EmcAccelerationService.cycleMultiplier(f.stack, f.player);
            EmcAccelerationService.confirm(f.player, token, true);
            assertFalse(f.enabled());
        }
    }

    @Test void tickerFailurePausesAndDoesNotRefundAlreadyAttemptedWork() throws Exception {
        try (var f = new Fixture(4)) {
            f.failOn = 2;
            f.bind(); f.run(4096);
            assertEquals(2, f.calls.get());
            assertFalse(f.enabled());
            assertEquals(BigInteger.valueOf(9_999_866), f.knowledge.getEmc());
            f.run(4096);
            assertEquals(2, f.calls.get());
        }
    }

    @Test void hugeNativeBalanceReallyDecreasesEvenWhenCompactDisplayDoesNotChange() throws Exception {
        try (var f = new Fixture(2)) {
            BigInteger before = new BigInteger("2379612356852347327802");
            f.knowledge.setEmc(before);
            f.bind();
            for (int tick = 0; tick < 10; tick++) f.run(4096);
            assertEquals(10, f.calls.get());
            assertEquals(before.subtract(BigInteger.valueOf(1000)), f.knowledge.getEmc());
            assertEquals(EmcFormatter.formatCompact(before), EmcFormatter.formatCompact(f.knowledge.getEmc()));
            verify(f.knowledge, times(10)).syncEmc(f.player);
        }
    }

    @Test void oneToolRunsDifferentTargetsAtIndependentRatesAndChargesEach() throws Exception {
        try (var f = new Fixture(2)) {
            f.bind();
            var second = new BlockPos(2, 64, 1);
            f.addTarget(second);
            EmcAccelerationService.cycleMultiplier(f.stack, f.player);
            EmcAccelerationService.bind(f.stack, f.player, f.level, second, InteractionHand.MAIN_HAND);
            assertEquals(2, AccelerationBindings.view(f.stack).size());
            f.run(4096);
            assertEquals(1, f.perTarget.get(f.pos).get());
            assertEquals(3, f.perTarget.get(second).get());
            assertEquals(BigInteger.valueOf(9_999_700), f.knowledge.getEmc());
            f.bind(); // 只暂停第一台，第二台继续以 4 倍运行。
            f.run(4096);
            assertEquals(1, f.perTarget.get(f.pos).get());
            assertEquals(6, f.perTarget.get(second).get());
            assertEquals(BigInteger.valueOf(9_999_500), f.knowledge.getEmc());
            EmcAccelerationService.toggle(f.stack, f.player, InteractionHand.MAIN_HAND);
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(7, f.calls.get());
            EmcAccelerationService.toggle(f.stack, f.player, InteractionHand.MAIN_HAND);
            f.run(4096);
            assertEquals(2, f.perTarget.get(f.pos).get());
            assertEquals(9, f.perTarget.get(second).get());
            EmcAccelerationService.clear(f.stack, f.player);
            f.run(4096);
            assertTrue(AccelerationBindings.view(f.stack).isEmpty());
            assertEquals(11, f.calls.get());
        }
    }

    @Test void limitedBudgetRotatesTargetsInsteadOfStarvingSecondMachine() throws Exception {
        try (var f = new Fixture(4)) {
            f.bind();
            var second = new BlockPos(2, 64, 1);
            f.addTarget(second);
            EmcAccelerationService.bind(f.stack, f.player, f.level, second, InteractionHand.MAIN_HAND);
            f.run(1); f.run(1);
            assertEquals(1, f.perTarget.get(f.pos).get());
            assertEquals(1, f.perTarget.get(second).get());
            assertEquals(BigInteger.valueOf(9_999_866), f.knowledge.getEmc());
        }
    }

    @Test void highRateAdditionKeepsExistingTargetAndBatchRestartQuotesBoth() throws Exception {
        try (var f = new Fixture(2)) {
            f.bind();
            var second = new BlockPos(2, 64, 1);
            f.addTarget(second);
            f.tag.putInt(EmcAcceleratorItem.MULTIPLIER_KEY, 256);
            EmcAccelerationService.bind(f.stack, f.player, f.level, second, InteractionHand.MAIN_HAND);
            f.run(4096);
            assertEquals(1, f.perTarget.get(f.pos).get());
            assertEquals(0, f.perTarget.get(second).get());
            assertEquals(1, f.warning.get().targets());
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            EmcAccelerationService.toggle(f.stack, f.player, InteractionHand.MAIN_HAND);
            assertFalse(f.enabled());
            EmcAccelerationService.toggle(f.stack, f.player, InteractionHand.MAIN_HAND);
            assertFalse(f.enabled());
            assertEquals(2, f.warning.get().targets());
            assertEquals("258000", f.warning.get().cost());
            assertEquals(256, f.warning.get().multiplier());
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            f.run(4096);
            assertEquals(2, f.perTarget.get(f.pos).get());
            assertEquals(255, f.perTarget.get(second).get());
            assertEquals(BigInteger.valueOf(9_987_000), f.knowledge.getEmc());
        }
    }

    @Test void removingOneTargetDoesNotPauseOthersOrChargeMissingMachine() throws Exception {
        try (var f = new Fixture(2)) {
            f.bind();
            var second = new BlockPos(2, 64, 1);
            f.addTarget(second);
            EmcAccelerationService.bind(f.stack, f.player, f.level, second, InteractionHand.MAIN_HAND);
            f.targets.when(() -> AccelerationTarget.read(f.level, f.pos)).thenReturn(null);
            f.run(4096);
            assertEquals(0, f.perTarget.get(f.pos).get());
            assertEquals(1, f.perTarget.get(second).get());
            assertEquals(BigInteger.valueOf(9_999_900), f.knowledge.getEmc());
            assertEquals(1, AccelerationBindings.view(f.stack).stream().filter(AccelerationBindings::enabled).count());
        }
    }

    @Test void legacySingleTargetMigratesPausedAndRequiresActivation() throws Exception {
        try (var f = new Fixture(4)) {
            f.tag.putString(EmcAcceleratorItem.DIMENSION_KEY, "minecraft:overworld");
            f.tag.putString(EmcAcceleratorItem.BLOCK_KEY, "minecraft:furnace");
            f.tag.putInt(EmcAcceleratorItem.X_KEY, f.pos.getX());
            f.tag.putInt(EmcAcceleratorItem.Y_KEY, f.pos.getY());
            f.tag.putInt(EmcAcceleratorItem.Z_KEY, f.pos.getZ());
            f.tag.putBoolean(EmcAcceleratorItem.ENABLED_KEY, true);
            f.run(4096); f.run(4096);
            assertEquals(1, AccelerationBindings.view(f.stack).size());
            assertEquals(0, f.calls.get());
            assertFalse(f.enabled());
            EmcAccelerationService.toggle(f.stack, f.player, InteractionHand.MAIN_HAND);
            f.run(4096);
            assertEquals(3, f.calls.get());
            assertEquals(BigInteger.valueOf(9_999_800), f.knowledge.getEmc());
            assertFalse(f.tag.contains(EmcAcceleratorItem.DIMENSION_KEY));
        }
    }

    @Test void expiredBatchOrChangedEntryCannotReuseOldConfirmation() throws Exception {
        try (var f = new Fixture(256)) {
            f.bind();
            when(f.server.getTickCount()).thenReturn(601);
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            assertFalse(f.enabled());
            f.bind();
            AccelerationBindings.view(f.stack).get(0).putInt(EmcAcceleratorItem.MULTIPLIER_KEY, 512);
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(0, f.calls.get());
            assertEquals(BigInteger.valueOf(10_000_000), f.knowledge.getEmc());
        }
    }

    @Test void bindingLimitRejectsExtraTargetWithoutReplacingExistingOnes() throws Exception {
        try (var f = new Fixture(2)) {
            for (int i = 0; i < 64; i++) {
                BlockPos at = new BlockPos(i, 64, 3);
                f.addTarget(at);
                EmcAccelerationService.bind(f.stack, f.player, f.level, at, InteractionHand.MAIN_HAND);
            }
            f.bind(); // 第 65 个有效位置超过默认上限，不得覆盖列表或停止已有目标。
            assertEquals(64, AccelerationBindings.view(f.stack).size());
            assertEquals(64, AccelerationBindings.view(f.stack).stream().filter(AccelerationBindings::enabled).count());
            f.run(4096);
            assertEquals(64, f.calls.get());
            assertEquals(0, f.perTarget.get(f.pos).get());
            assertEquals(BigInteger.valueOf(9_993_600), f.knowledge.getEmc());
        }
    }

    @Test void deadlineStopsAt600TicksAndNewActivationRestartsIt() throws Exception {
        try (var f = new Fixture(2)) {
            f.bind();
            when(f.server.getTickCount()).thenReturn(599);
            f.run(4096); assertEquals(1, f.calls.get());
            when(f.server.getTickCount()).thenReturn(600);
            f.run(4096); assertFalse(f.enabled()); assertEquals(1, f.calls.get());
            EmcAccelerationService.toggle(f.stack, f.player, InteractionHand.MAIN_HAND);
            assertEquals(1200, f.tag.getLong("RunDeadline"));
            f.run(4096); assertEquals(2, f.calls.get());
        }
    }

    @Test void addingTargetsSharesDeadlineAndDoesNotRestartExpiredOldTargets() throws Exception {
        try (var f = new Fixture(2)) {
            f.bind(); f.run(4096);
            var second = new BlockPos(2, 64, 2); f.addTarget(second);
            when(f.server.getTickCount()).thenReturn(100);
            EmcAccelerationService.bind(f.stack, f.player, f.level, second, InteractionHand.MAIN_HAND);
            assertEquals(600, f.tag.getLong("RunDeadline"));
            assertEquals("100", f.tag.getString("RunSpent"));
            when(f.server.getTickCount()).thenReturn(600);
            var third = new BlockPos(3, 64, 3); f.addTarget(third);
            EmcAccelerationService.bind(f.stack, f.player, f.level, third, InteractionHand.MAIN_HAND);
            assertEquals(1, AccelerationBindings.view(f.stack).stream().filter(AccelerationBindings::enabled).count());
            assertEquals(1200, f.tag.getLong("RunDeadline"));
        }
    }

    @Test void remainingRunAllowanceIsSharedAndRefundedByActualWork() throws Exception {
        try (var f = new Fixture(4)) {
            f.bind();
            var second = new BlockPos(2, 64, 2); f.addTarget(second);
            EmcAccelerationService.bind(f.stack, f.player, f.level, second, InteractionHand.MAIN_HAND);
            f.tag.putString("RunSpent", "2099999850");
            f.run(1);
            assertEquals("2099999917", f.tag.getString("RunSpent"));
            assertTrue(f.enabled());
            f.run(4096);
            assertEquals(2, f.calls.get());
            assertEquals("2099999984", f.tag.getString("RunSpent"));
            assertFalse(f.enabled());
            assertEquals(BigInteger.valueOf(9_999_866), f.knowledge.getEmc());
        }
    }

    @Test void exactRunCapStopsEveryTargetWithoutOverspending() throws Exception {
        try (var f = new Fixture(2)) {
            f.bind();
            f.tag.putString("RunSpent", "2099999900");
            f.run(4096); f.run(4096);
            assertEquals(1, f.calls.get());
            assertEquals("2100000000", f.tag.getString("RunSpent"));
            assertFalse(f.enabled());
        }
    }

    @Test void confirmationTimeDoesNotUseRunDuration() throws Exception {
        try (var f = new Fixture(256)) {
            f.bind();
            assertFalse(f.tag.contains("RunDeadline"));
            when(f.server.getTickCount()).thenReturn(300);
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            assertEquals(900, f.tag.getLong("RunDeadline"));
        }
    }

    static final class Fixture implements AutoCloseable {
        final MinecraftServer server = mock(MinecraftServer.class);
        final UUID owner = UUID.randomUUID();
        final ServerPlayer player = mock(ServerPlayer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final BlockPos pos = new BlockPos(1, 64, 1);
        final ItemStack stack = mock(ItemStack.class);
        final CompoundTag tag = new CompoundTag();
        final AtomicInteger calls = new AtomicInteger();
        final IKnowledgeProvider knowledge = spy(KnowledgeImpl.getDefault());
        final PrimordialEmcSavedData pending = new PrimordialEmcSavedData();
        final EmcConversionSavedData change = new EmcConversionSavedData();
        final java.util.Map<BlockPos, AtomicInteger> perTarget = new java.util.HashMap<>();
        final AtomicReference<S2CAccelerationWarningPacket> warning = new AtomicReference<>();
        final MockedStatic<AccelerationTarget> targets = mockStatic(AccelerationTarget.class);
        final MockedStatic<PrimordialEmcSavedData> stores = mockStatic(PrimordialEmcSavedData.class, CALLS_REAL_METHODS);
        final MockedStatic<EmcConversionSavedData> conversions = mockStatic(EmcConversionSavedData.class, CALLS_REAL_METHODS);
        final MockedStatic<S2CAccelerationWarningPacket> packets = mockStatic(S2CAccelerationWarningPacket.class);
        int failOn;

        Fixture(int rate) throws Exception {
            var field = ServerPlayer.class.getDeclaredField("server"); field.setAccessible(true); field.set(player, server);
            when(player.getUUID()).thenReturn(owner);
            var players = mock(PlayerList.class);
            when(server.getPlayerList()).thenReturn(players);
            when(players.getPlayer(owner)).thenReturn(player);
            when(player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)).thenReturn(LazyOptional.of(() -> knowledge));
            knowledge.setEmc(BigInteger.valueOf(10_000_000));
            doNothing().when(knowledge).syncEmc(player);
            stores.when(() -> PrimordialEmcSavedData.get(server)).thenReturn(pending);
            conversions.when(() -> EmcConversionSavedData.get(server)).thenReturn(change);
            when(server.isSameThread()).thenReturn(true);
            when(player.isAlive()).thenReturn(true);
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
            when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
            when(stack.getItem()).thenReturn(mock(EmcAcceleratorItem.class));
            when(stack.hasTag()).thenReturn(true);
            when(stack.getTag()).thenReturn(tag);
            when(stack.getOrCreateTag()).thenReturn(tag);
            tag.putInt(EmcAcceleratorItem.MULTIPLIER_KEY, rate);
            addTarget(pos);
            packets.when(() -> S2CAccelerationWarningPacket.send(eq(player), any(UUID.class), anyInt(), any(BlockPos.class), anyInt(), anyString()))
                    .thenAnswer(call -> {
                        warning.set(new S2CAccelerationWarningPacket(call.getArgument(1), call.getArgument(2),
                                call.getArgument(3), call.getArgument(4), call.getArgument(5))); return null;
                    });
        }

        void addTarget(BlockPos at) {
            when(level.hasChunkAt(at)).thenReturn(true);
            when(level.shouldTickBlocksAt(at)).thenReturn(true);
            when(level.getBlockState(at)).thenReturn(Blocks.FURNACE.defaultBlockState());
            var entity = mock(BlockEntity.class);
            when(level.getBlockEntity(at)).thenReturn(entity);
            perTarget.put(at, new AtomicInteger());
            var target = new AccelerationTarget(level, at, Blocks.FURNACE.defaultBlockState(), entity,
                    (world, position, state, blockEntity) -> {
                        perTarget.get(position).incrementAndGet();
                        if (calls.incrementAndGet() == failOn) throw new IllegalStateException("test machine failure");
                    });
            targets.when(() -> AccelerationTarget.read(level, at)).thenReturn(target);
        }

        void bind() { EmcAccelerationService.bind(stack, player, level, pos, InteractionHand.MAIN_HAND); }
        void run(int calls) { EmcAccelerationService.process(server, player, stack, new AccelerationBudget(64, calls, 2000, () -> 0L)); }
        boolean enabled() { return AccelerationBindings.view(stack).stream().anyMatch(AccelerationBindings::enabled); }
        @Override public void close() {
            EmcAccelerationService.clear(stack, player);
            BalanceSyncer.clear(owner);
            packets.close(); conversions.close(); stores.close(); targets.close();
        }
    }
}
