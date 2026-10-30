package com.primordial.neoforge.acceleration;

import java.util.function.Function;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

/** Uses AE2 19's public host and tick-service API; loaded only with the optional mod. */
public final class Ae2AccelerationAdapter implements Function<BlockEntity, BlockEntityTicker<BlockEntity>> {
    public Ae2AccelerationAdapter() {}

    @Override public BlockEntityTicker<BlockEntity> apply(BlockEntity entity) {
        if (!(entity.getLevel() instanceof ServerLevel level) || entity.isRemoved()
                || !level.getServer().isSameThread() || !level.hasChunkAt(entity.getBlockPos())
                || level.getBlockEntity(entity.getBlockPos()) != entity) return null;
        var host = host(entity);
        if (host == null) return null;
        for (Direction side : Direction.values()) {
            var node = host.getGridNode(side);
            if (node == null || node.getLevel() != level || node.getGrid() == null || !node.isActive()) continue;
            var tickable = node.getService(IGridTickable.class);
            if (tickable == null) continue;
            var grid = node.getGrid();
            return new AccelerationMachineCompat.CheckedTicker() {
                private boolean requestedSleep;
                @Override public boolean valid() {
                    return !entity.isRemoved() && entity.getLevel() == level && level.getServer().isSameThread()
                            && level.hasChunkAt(entity.getBlockPos()) && level.getBlockEntity(entity.getBlockPos()) == entity
                            && host(entity) == host && host.getGridNode(side) == node
                            && node.getGrid() == grid && node.isActive() && node.getService(IGridTickable.class) == tickable;
                }
                @Override public boolean idle() {
                    if (!valid() || requestedSleep) return true;
                    // AE2's public tick manager has no live scheduler-status query.
                    var request = tickable.getTickingRequest(node);
                    return request == null || request.isSleeping();
                }
                @Override public void tick(net.minecraft.world.level.Level currentLevel, net.minecraft.core.BlockPos pos,
                                           net.minecraft.world.level.block.state.BlockState state, BlockEntity current) {
                    if (current == entity && currentLevel == level && pos.equals(entity.getBlockPos()) && valid() && !idle())
                        requestedSleep = tickable.tickingRequest(node, 1) == TickRateModulation.SLEEP;
                }
            };
        }
        return null;
    }

    private static IInWorldGridNodeHost host(BlockEntity entity) {
        return entity instanceof IInWorldGridNodeHost direct ? direct
                : GridHelper.getNodeHost(entity.getLevel(), entity.getBlockPos());
    }
}
