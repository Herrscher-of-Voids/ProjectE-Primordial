/**
 * 【文件职责】验证有界范围扫描、重叠去重、位置名单和空闲收费。
 * 【变更记录 #1】2026-09-18 02:07:31（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：范围成员真实扣款、空名单、同类标记、重启持久化、预算限制、几何编辑及降频断言。
 * - 修改：运行生产范围调度与统一结算，世界查询使用只返回已知机器的替身。
 * - 用途：大范围不会在一个 tick 无界扫描，重复覆盖不多收 EMC，改名单后必须再次启动。
 */
package com.primordial.service;

import com.primordial.item.EmcAcceleratorItem;
import com.primordial.machine.AdapterTestBootstrap;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccelerationAreaTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void areaBillsEachMachineAndOverlappingAreasDoNotStack() throws Exception {
        try (var f = fixture(2)) {
            BlockPos second = f.pos.east(); f.addTarget(second);
            f.bind();
            EmcAccelerationService.bind(f.stack, f.player, f.level, second, InteractionHand.MAIN_HAND);
            f.run(4096);
            assertEquals(2, f.calls.get());
            assertEquals(BigInteger.valueOf(9_999_800), f.knowledge.getEmc());
            assertEquals(2, AccelerationBindings.view(f.stack).size());
        }
    }

    @Test void emptyWhitelistCostsNothingAndMarkedPositionsPersist() throws Exception {
        try (var f = fixture(2)) {
            f.bind();
            CompoundTag entry = AccelerationBindings.view(f.stack).get(0);
            var id = AccelerationBindings.id(entry);
            assertTrue(AccelerationBindingActions.policy(f.stack, f.player, id, false, true, false));
            assertFalse(f.enabled());
            assertTrue(AccelerationBindingActions.start(f.stack, f.player, InteractionHand.MAIN_HAND, id));
            f.run(4096);
            assertEquals(0, f.calls.get());
            assertEquals(BigInteger.valueOf(10_000_000), f.knowledge.getEmc());
            assertTrue(AccelerationBindingActions.marking(f.stack, id, false));
            assertTrue(AccelerationArea.mark(f.stack, f.player, f.level, f.pos, false));
            assertFalse(f.enabled());
            assertEquals(1, entry.getLongArray("AreaMarks").length);
            assertTrue(AccelerationArea.allows(entry.copy(), f.pos, false));
            assertFalse(AccelerationArea.allows(entry.copy(), f.pos.east(), false));
            assertTrue(AccelerationBindingActions.start(f.stack, f.player, InteractionHand.MAIN_HAND, id));
            f.run(4096);
            assertEquals(1, f.calls.get());
            assertEquals(BigInteger.valueOf(9_999_900), f.knowledge.getEmc());
        }
    }

    @Test void sameTypeMarkingTogglesMatchingBlocksAndRejectsOutsidePositions() throws Exception {
        try (var f = fixture(2)) {
            f.addTarget(f.pos.east());
            f.bind();
            CompoundTag entry = AccelerationBindings.view(f.stack).get(0);
            assertTrue(AccelerationBindingActions.marking(f.stack, AccelerationBindings.id(entry), false));
            assertTrue(AccelerationArea.mark(f.stack, f.player, f.level, f.pos, true));
            assertEquals(2, entry.getLongArray("AreaMarks").length);
            assertFalse(AccelerationArea.allows(entry, f.pos, false));
            assertFalse(AccelerationArea.mark(f.stack, f.player, f.level, f.pos.offset(99, 0, 0), false));
            assertEquals(2, entry.getLongArray("AreaMarks").length);
            assertTrue(AccelerationArea.mark(f.stack, f.player, f.level, f.pos, true));
            assertEquals(0, entry.getLongArray("AreaMarks").length);
            assertTrue(AccelerationArea.allows(entry, f.pos, false));
        }
    }

    @Test void idleThrottleBillsItsActualRateAndIdleListCanExemptTheMachine() throws Exception {
        try (var f = fixture(16)) {
            f.bind(); f.run(4096);
            assertEquals(15, f.calls.get());
            when(f.server.getTickCount()).thenReturn(100);
            f.run(4096);
            assertEquals(18, f.calls.get());
            assertEquals(BigInteger.valueOf(9_999_000), f.knowledge.getEmc());
            CompoundTag entry = AccelerationBindings.view(f.stack).get(0);
            var id = AccelerationBindings.id(entry);
            assertTrue(AccelerationBindingActions.marking(f.stack, id, true));
            assertTrue(AccelerationArea.mark(f.stack, f.player, f.level, f.pos, false));
            assertTrue(AccelerationBindingActions.start(f.stack, f.player, InteractionHand.MAIN_HAND, id));
            when(f.server.getTickCount()).thenReturn(200);
            f.run(4096);
            assertEquals(33, f.calls.get());
            assertEquals(BigInteger.valueOf(9_998_200), f.knowledge.getEmc());
        }
    }

    @Test void highRateAreaNeedsConfirmationAndQuotesItsUpperBound() throws Exception {
        try (var f = fixture(256)) {
            f.bind(); f.run(4096);
            assertEquals(0, f.calls.get());
            assertEquals("6912000", f.warning.get().cost());
            EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            f.run(4096);
            assertEquals(255, f.calls.get());
            assertEquals(BigInteger.valueOf(9_987_200), f.knowledge.getEmc());
        }
    }

    @Test void largeAreaScanningIsIncrementalAndDoesNotLoadChunks() throws Exception {
        try (var f = fixture(2)) {
            new AccelerationSettings(AccelerationSettings.Kind.AREA, 2, 15, 15, 15, 0, 0, 0, true, false).write(f.tag);
            f.bind();
            f.targets.clearInvocations();
            f.run(4096);
            f.targets.verify(() -> AccelerationTarget.read(eq(f.level), any(BlockPos.class)), times(64));
            assertEquals(0, f.calls.get());
            verify(f.level, never()).getChunk(anyInt(), anyInt());
            assertEquals(BigInteger.valueOf(10_000_000), f.knowledge.getEmc());
        }
    }

    @Test void resizingPausesAndClearsOldMarksButPreservesOtherTargets() throws Exception {
        try (var f = fixture(2)) {
            f.bind();
            CompoundTag entry = AccelerationBindings.view(f.stack).get(0);
            var id = AccelerationBindings.id(entry);
            entry.putLongArray("AreaMarks", new long[]{f.pos.asLong()});
            entry.putLongArray("IdleMarks", new long[]{f.pos.asLong()});
            var changed = new AccelerationSettings(AccelerationSettings.Kind.AREA, 4, 5, 3, 3, 1, 0, 0, true, false);
            assertTrue(AccelerationBindingActions.edit(f.stack, f.player, id, changed));
            assertFalse(f.enabled());
            assertEquals(0, entry.getLongArray("AreaMarks").length);
            assertEquals(0, entry.getLongArray("IdleMarks").length);
            assertEquals(changed, AccelerationSettings.read(entry));
            assertFalse(AccelerationBindingActions.edit(f.stack, f.player, id,
                    new AccelerationSettings(AccelerationSettings.Kind.AREA, 4, 16, 3, 3, 0, 0, 0, true, false)));
            assertEquals(changed, AccelerationSettings.read(entry));
            assertTrue(AccelerationBindingActions.pause(f.stack, f.player, id, true));
            assertTrue(AccelerationBindings.view(f.stack).isEmpty());
        }
    }

    @Test void geometryAndIdleListsHaveIndependentExactBoundaries() {
        var settings = new AccelerationSettings(AccelerationSettings.Kind.AREA, 2, 2, 3, 4, -15, 15, 0, true, false);
        assertTrue(settings.valid());
        assertEquals(24, settings.volume());
        var positions = new java.util.HashSet<BlockPos>();
        for (int i = 0; i < settings.volume(); i++) {
            var pos = settings.position(BlockPos.ZERO, i); positions.add(pos);
            assertTrue(settings.bounds(BlockPos.ZERO).contains(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5));
        }
        assertEquals(24, positions.size());
        var tag = new CompoundTag(); tag.putBoolean("IdleWhitelist", true);
        assertTrue(AccelerationArea.allows(tag, BlockPos.ZERO, false));
        assertFalse(AccelerationArea.allows(tag, BlockPos.ZERO, true));
        tag.putLongArray("IdleMarks", new long[]{BlockPos.ZERO.asLong()});
        assertTrue(AccelerationArea.allows(tag, BlockPos.ZERO, true));
    }

    private static AccelerationFlowTest.Fixture fixture(int rate) throws Exception {
        var f = new AccelerationFlowTest.Fixture(rate);
        when(f.level.getMinBuildHeight()).thenReturn(-64); when(f.level.getMaxBuildHeight()).thenReturn(320);
        var border = mock(WorldBorder.class); when(border.isWithinBounds(any(BlockPos.class))).thenReturn(true);
        when(f.level.getWorldBorder()).thenReturn(border);
        when(f.level.hasChunkAt(any(BlockPos.class))).thenReturn(true);
        when(f.level.getBlockState(any(BlockPos.class))).thenReturn(Blocks.AIR.defaultBlockState());
        f.addTarget(f.pos);
        AccelerationSettings.read(f.tag).withKind(AccelerationSettings.Kind.AREA).write(f.tag);
        return f;
    }
}
