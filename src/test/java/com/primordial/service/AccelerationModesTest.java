/**
 * 【文件职责】验证实体和全局时钟的生产绑定、预算、真实 ProjectE 扣款与停止边界。
 * 【变更记录 #1】2026-09-18 02:04:29（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：实体完成/失败/AI 恢复/玩家排除、昼夜规则、天气转换及世界时钟不叠加回归。
 * - 修改：复用真实 ProjectE 账户夹具，只有世界与目标对象使用可计数替身。
 * - 用途：确认新模式进入同一付费链，未执行、规则关闭或目标失效不会持续扣费。
 */
/**
 * 【变更记录 #2】2026-09-18 02:16:58（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：不存在的 ServerLevel.setGameTime 调用与嵌套 Mockito 取值。
 * - 新增：对权威 ServerLevelData 的游戏总时间写入禁用断言。
 * - 修改：实体类型使用显式 doReturn，工具类型在配置替身前读取。
 * - 用途：使昼夜、实体及多工具计费回归能够按 1.20.1 API 正常执行。
 */
package com.primordial.service;

import com.primordial.machine.AdapterTestBootstrap;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.ServerLevelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccelerationModesTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void entityUsesTheSameTwentyTickPriceAndStopsWhenRemoved() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            Entity entity = entity(f, false);
            AccelerationBindingActions.entity(f.stack, f.player, entity, InteractionHand.MAIN_HAND);
            for (int i = 0; i < 20; i++) f.run(4096);
            verify(entity, times(60)).tick();
            assertEquals(60, entity.tickCount);
            assertEquals(BigInteger.valueOf(9_996_000), f.knowledge.getEmc());
            when(entity.isRemoved()).thenReturn(true);
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(BigInteger.valueOf(9_996_000), f.knowledge.getEmc());
        }
    }

    @Test void entityFailureRestoresAiAndChargesOnlyAttemptedWork() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            Mob entity = (Mob) entity(f, true);
            var noAi = new AtomicBoolean();
            when(entity.isNoAi()).thenAnswer(call -> noAi.get());
            doAnswer(call -> { noAi.set(call.getArgument(0)); return null; }).when(entity).setNoAi(anyBoolean());
            doAnswer(call -> { assertTrue(noAi.get()); throw new IllegalStateException("entity changed before failure"); }).when(entity).tick();
            f.tag.putBoolean("DisableEntityAi", true);
            AccelerationBindingActions.entity(f.stack, f.player, entity, InteractionHand.MAIN_HAND);
            f.run(4096);
            assertFalse(noAi.get());
            assertFalse(f.enabled());
            assertEquals(1, entity.tickCount);
            assertEquals(BigInteger.valueOf(9_999_933), f.knowledge.getEmc());
            assertEquals(EmcAmount.parse("1/3"), f.change.get(f.owner));
        }
    }

    @Test void playerAndRidingEntitiesAreRejectedBeforeBilling() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            var player = mock(Player.class);
            when(player.level()).thenReturn(f.level);
            AccelerationBindingActions.entity(f.stack, f.player, player, InteractionHand.MAIN_HAND);
            Entity entity = entity(f, false);
            when(entity.isVehicle()).thenReturn(true);
            AccelerationBindingActions.entity(f.stack, f.player, entity, InteractionHand.MAIN_HAND);
            when(entity.isVehicle()).thenReturn(false); when(entity.isMultipartEntity()).thenReturn(true);
            AccelerationBindingActions.entity(f.stack, f.player, entity, InteractionHand.MAIN_HAND);
            assertTrue(AccelerationBindings.view(f.stack).isEmpty());
            assertEquals(BigInteger.valueOf(10_000_000), f.knowledge.getEmc());
        }
    }

    @Test void entityStopsWithinTheSameBatchAfterItLeavesTheLoadedArea() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            Entity entity = entity(f, false);
            doAnswer(call -> { when(entity.blockPosition()).thenReturn(new BlockPos(10000, 64, 0)); return null; }).when(entity).tick();
            AccelerationBindingActions.entity(f.stack, f.player, entity, InteractionHand.MAIN_HAND);
            f.run(4096);
            verify(entity).tick();
            assertFalse(f.enabled());
            assertEquals(BigInteger.valueOf(9_999_933), f.knowledge.getEmc());
        }
    }

    @Test void daylightAdvancesWithoutChangingGameTimeAndObeysTheRule() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            var clock = world(f);
            select(f, AccelerationSettings.Kind.DAYTIME);
            AccelerationBindingActions.world(f.stack, f.player, InteractionHand.MAIN_HAND);
            for (int i = 0; i < 20; i++) f.run(4096);
            assertEquals(1060, clock.day.get());
            assertEquals(BigInteger.valueOf(9_996_000), f.knowledge.getEmc());
            verify(clock.data, never()).setGameTime(anyLong());
            clock.rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(1060, clock.day.get());
            assertEquals(BigInteger.valueOf(9_996_000), f.knowledge.getEmc());
        }
    }

    @Test void weatherMovesBothTimersAndRespectsWeatherCycle() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            var clock = world(f);
            select(f, AccelerationSettings.Kind.WEATHER);
            AccelerationBindingActions.world(f.stack, f.player, InteractionHand.MAIN_HAND);
            f.run(4096);
            assertEquals(997, clock.rain.get()); assertEquals(997, clock.thunder.get());
            assertEquals(BigInteger.valueOf(9_999_800), f.knowledge.getEmc());
            assertEquals(1000, clock.day.get());
            clock.rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            f.run(4096); assertFalse(f.enabled());
            assertEquals(997, clock.rain.get());
        }
    }

    @Test void sameGlobalClockCannotStackAcrossToolsAndStillUsesTheCommonBudget() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            var clock = world(f);
            select(f, AccelerationSettings.Kind.DAYTIME);
            AccelerationBindingActions.world(f.stack, f.player, InteractionHand.MAIN_HAND);
            var second = mock(ItemStack.class);
            var tag = new net.minecraft.nbt.CompoundTag();
            AccelerationSettings.read(f.tag).write(tag);
            var item = f.stack.getItem();
            when(second.getItem()).thenReturn(item); when(second.getTag()).thenReturn(tag);
            when(second.getOrCreateTag()).thenReturn(tag); when(second.hasTag()).thenReturn(true);
            AccelerationBindingActions.world(second, f.player, InteractionHand.MAIN_HAND);
            var budget = new AccelerationBudget(64, 2, 2000, () -> 0L);
            EmcAccelerationService.process(f.server, f.player, f.stack, budget);
            EmcAccelerationService.process(f.server, f.player, second, budget);
            assertEquals(1002, clock.day.get());
            assertEquals(BigInteger.valueOf(9_999_866), f.knowledge.getEmc());
            assertEquals("0", tag.getString("RunSpent"));
        }
    }

    @Test void weatherTransitionsAndClearWeatherProtectionFollowVanillaTimers() {
        var data = mock(ServerLevelData.class);
        var random = net.minecraft.util.RandomSource.create(12);
        when(data.getRainTime()).thenReturn(1); when(data.getThunderTime()).thenReturn(1);
        AccelerationWeather.tick(data, random);
        verify(data).setRaining(true); verify(data).setThundering(true);
        reset(data);
        when(data.getClearWeatherTime()).thenReturn(5); when(data.isRaining()).thenReturn(true);
        AccelerationWeather.tick(data, random);
        verify(data).setClearWeatherTime(4); verify(data).setRaining(false); verify(data).setThundering(false);
        verify(data).setRainTime(0); verify(data).setThunderTime(1);
    }

    private static Entity entity(AccelerationFlowTest.Fixture f, boolean mob) {
        Entity entity = mob ? mock(Mob.class) : mock(Entity.class);
        UUID id = UUID.randomUUID();
        when(entity.getUUID()).thenReturn(id); when(entity.getId()).thenReturn(123);
        doReturn(EntityType.PIG).when(entity).getType(); when(entity.level()).thenReturn(f.level);
        when(entity.blockPosition()).thenReturn(f.pos); when(entity.isAlive()).thenReturn(true); when(entity.canUpdate()).thenReturn(true);
        when(entity.getDisplayName()).thenReturn(Component.literal("Target"));
        when(f.level.getEntity(id)).thenReturn(entity);
        return entity;
    }
    private static void select(AccelerationFlowTest.Fixture f, AccelerationSettings.Kind kind) { AccelerationSettings.read(f.tag).withKind(kind).write(f.tag); }
    private static WorldClock world(AccelerationFlowTest.Fixture f) {
        var clock = new WorldClock();
        when(f.server.overworld()).thenReturn(f.level); when(f.level.getGameRules()).thenReturn(clock.rules);
        when(f.level.getDayTime()).thenAnswer(call -> clock.day.get());
        doAnswer(call -> { clock.day.set(call.getArgument(0)); return null; }).when(f.level).setDayTime(anyLong());
        var dimension = mock(DimensionType.class); when(dimension.hasSkyLight()).thenReturn(true);
        when(f.level.dimensionType()).thenReturn(dimension); when(f.level.getLevelData()).thenReturn(clock.data);
        when(f.level.getRandom()).thenReturn(net.minecraft.util.RandomSource.create(42));
        when(clock.data.getRainTime()).thenAnswer(call -> clock.rain.get());
        when(clock.data.getThunderTime()).thenAnswer(call -> clock.thunder.get());
        doAnswer(call -> { clock.rain.set(call.getArgument(0)); return null; }).when(clock.data).setRainTime(anyInt());
        doAnswer(call -> { clock.thunder.set(call.getArgument(0)); return null; }).when(clock.data).setThunderTime(anyInt());
        return clock;
    }
    private static final class WorldClock {
        final GameRules rules = new GameRules();
        final AtomicLong day = new AtomicLong(1000);
        final AtomicInteger rain = new AtomicInteger(1000), thunder = new AtomicInteger(1000);
        final ServerLevelData data = mock(ServerLevelData.class);
    }
}
