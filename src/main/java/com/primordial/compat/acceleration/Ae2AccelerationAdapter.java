/**
 * 【文件职责】用 AE2 15 的公开 IGridTickable 接口对当前方块所属设备执行一次额外请求。
 * 【变更记录 #1】2026-09-18 01:33:29（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：按方向查找有效节点，每次仅执行一个节点，避免六面暴露同一节点时重复加速。
 * - 修改：节点失活、移除或更换时停止调用；异常交由统一预算器计入尝试次数并停用。
 * - 用途：仅在 AE2 安装后延迟加载，本类不会被公共启动路径直接引用。
 */
package com.primordial.compat.acceleration;

import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

public final class Ae2AccelerationAdapter implements Function<BlockEntity, BlockEntityTicker<BlockEntity>> {
    public Ae2AccelerationAdapter() { }
    @Override public BlockEntityTicker<BlockEntity> apply(BlockEntity entity) {
        if (!(entity instanceof IInWorldGridNodeHost host)) return null;
        for (Direction side : Direction.values()) {
            var node = host.getGridNode(side);
            if (node == null || node.getGrid() == null || !node.isActive()) continue;
            var tickable = node.getService(IGridTickable.class);
            if (tickable != null) return new com.primordial.service.AccelerationMachineCompat.CheckedTicker() {
                @Override public boolean valid() { return !entity.isRemoved() && host.getGridNode(side) == node && node.isActive(); }
                @Override public boolean idle() {
                    return node.getGrid().getTickManager() instanceof appeng.me.service.TickManagerService manager
                            && manager.getStatus(node).sleeping();
                }
                @Override public void tick(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
                                           net.minecraft.world.level.block.state.BlockState state, BlockEntity current) {
                    if (current == entity && valid()) tickable.tickingRequest(node, 1);
                }
            };
        }
        return null;
    }
}
