/**
 * 【文件职责】验证 AE2 专用加速按设备节点调用而非整个网络重复调度。
 * 【变更记录 #1】2026-09-18 02:07:31（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：多面同一节点只执行一次、失活/更换/移除拒绝与原生休眠状态检查。
 * - 修改：只模拟 AE2 接口边界，生产适配器完整执行。
 * - 用途：保证额外调用与统一预算的一次工作对应，普通非 AE2 方块不会被误识别。
 */
package com.primordial.service;

import appeng.api.networking.*;
import appeng.api.networking.ticking.*;
import appeng.me.service.TickManagerService;
import com.primordial.compat.acceleration.Ae2AccelerationAdapter;
import com.primordial.machine.AdapterTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccelerationAe2Test {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @Test void sharedNodeRunsOnceAndSleepingStateIsReported() {
        var entity = mock(BlockEntity.class, withSettings().extraInterfaces(IInWorldGridNodeHost.class));
        var host = (IInWorldGridNodeHost) entity;
        var node = mock(IGridNode.class); var grid = mock(IGrid.class); var tickable = mock(IGridTickable.class);
        var manager = mock(TickManagerService.class);
        when(host.getGridNode(any(Direction.class))).thenReturn(node);
        when(node.getGrid()).thenReturn(grid); when(node.isActive()).thenReturn(true);
        when(node.getService(IGridTickable.class)).thenReturn(tickable);
        when(grid.getTickManager()).thenReturn(manager);
        when(manager.getStatus(node)).thenReturn(new TickManagerService.NodeStatus(false, true, false, false, 20, 1));
        var callback = (AccelerationMachineCompat.CheckedTicker) new Ae2AccelerationAdapter().apply(entity);
        assertNotNull(callback); assertTrue(callback.valid()); assertTrue(callback.idle());
        callback.tick(null, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), entity);
        verify(tickable).tickingRequest(node, 1);
        when(node.isActive()).thenReturn(false);
        assertFalse(callback.valid());
        callback.tick(null, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), entity);
        verifyNoMoreInteractions(tickable);
        assertNull(new Ae2AccelerationAdapter().apply(entity));
        assertNull(new Ae2AccelerationAdapter().apply(mock(BlockEntity.class)));
    }
}
