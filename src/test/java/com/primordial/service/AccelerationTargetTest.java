/**
 * 【文件职责】用原版方块和方块实体验证加速目标发现，世界加载状态由测试替身提供。
 * 【变更记录 #1】2026-09-16 13:47:00（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：炉子原生 ticker、作物生长结束、装饰方块和无 ticker 容器拒绝、卸载及实体替换测试。
 * - 修改：直接调用 AccelerationTarget.read，避免执行流程测试替换此入口后遗漏真实类型判断。
 * - 用途：确认发现过程不会加载区块，目标卸载、移除或更换后不会继续执行旧回调。
 */
/**
 * 【变更记录 #2】2026-09-16 13:49:13（第 2 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：测试中初始化小麦各生长状态的原版缓存。
 * - 修改：补齐 Forge 注册完成阶段的前提；仅 Bootstrap 不会设置随机 tick 缓存标记。
 * - 用途：让作物判断与实际游戏加载后的状态一致，不修改生产逻辑规避失败。
 */
package com.primordial.service;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccelerationTargetTest {
    private final BlockPos pos = new BlockPos(2, 64, -3);

    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Blocks.WHEAT.getStateDefinition().getPossibleStates().forEach(state -> state.initCache());
    }

    private ServerLevel loadedWorld() {
        var level = mock(ServerLevel.class);
        when(level.hasChunkAt(pos)).thenReturn(true);
        when(level.shouldTickBlocksAt(pos)).thenReturn(true);
        return level;
    }

    @Test void furnaceUsesNativeTickerAndRejectsEntityReplacement() {
        var level = loadedWorld();
        var state = Blocks.FURNACE.defaultBlockState();
        var furnace = new FurnaceBlockEntity(pos, state);
        when(level.getBlockState(pos)).thenReturn(state);
        when(level.getBlockEntity(pos)).thenReturn(furnace);
        var target = AccelerationTarget.read(level, pos);
        assertNotNull(target);
        assertNotNull(target.ticker());
        assertEquals("minecraft:furnace", target.blockId());
        assertTrue(target.valid());
        when(level.getBlockEntity(pos)).thenReturn(new FurnaceBlockEntity(pos, state));
        assertFalse(target.valid());
        when(level.getBlockEntity(pos)).thenReturn(furnace);
        furnace.setRemoved();
        assertFalse(target.valid());
        assertNull(AccelerationTarget.read(level, pos));
    }

    @Test void cropsStopWhenFullyGrownOrReplaced() {
        var level = loadedWorld();
        when(level.getBlockState(pos)).thenReturn(Blocks.WHEAT.defaultBlockState());
        var target = AccelerationTarget.read(level, pos);
        assertNotNull(target);
        assertNull(target.ticker());
        assertTrue(target.valid());
        when(level.getBlockState(pos)).thenReturn(Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        assertFalse(target.valid());
        assertNull(AccelerationTarget.read(level, pos));
        when(level.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
        assertFalse(target.valid());
    }

    @Test void decorativeBlocksAndContainersWithoutServerTickerAreRejected() {
        var level = loadedWorld();
        when(level.getBlockState(pos)).thenReturn(Blocks.STONE.defaultBlockState());
        assertNull(AccelerationTarget.read(level, pos));
        var chest = Blocks.CHEST.defaultBlockState();
        when(level.getBlockState(pos)).thenReturn(chest);
        when(level.getBlockEntity(pos)).thenReturn(new ChestBlockEntity(pos, chest));
        assertNull(AccelerationTarget.read(level, pos));
    }

    @Test void unloadedOrInactiveChunksAreRejectedBeforeWorldReads() {
        var level = mock(ServerLevel.class);
        assertNull(AccelerationTarget.read(level, pos));
        verify(level, never()).getBlockState(any());
        verify(level, never()).getBlockEntity(any());
        when(level.hasChunkAt(pos)).thenReturn(true);
        assertNull(AccelerationTarget.read(level, pos));
        verify(level, never()).getBlockState(any());
        verify(level, never()).getBlockEntity(any());
    }
}
