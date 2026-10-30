/**
 * 【文件职责】NeoForge 机器的方向枚举与三类资源访问契约，供目标平台适配器实现。
 * 【变更记录 #1】2026-10-22 11:36:36（第 1 次备注 · 2.0.0-neo-dev 能力平台契约）
 * - 删除：无。
 * - 新增：物品、流体、能量按侧查询，以及首个可用端口的默认查找方法。
 * - 修改：与 Forge MachineProxy 分开编译，不暴露 LazyOptional 或 Forge 类型。
 * - 用途：让后续机器任务复用目标平台能力边界，尚不接入生产黑洞回收或转化接口。
 */
package com.primordial.neoforge.machine;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public interface NeoForgeMachineProxy {
    String id();

    Set<Direction> supportedSides(BlockEntity machine);

    @Nullable IItemHandler itemHandler(BlockEntity machine, @Nullable Direction side);

    @Nullable IFluidHandler fluidHandler(BlockEntity machine, @Nullable Direction side);

    @Nullable IEnergyStorage energyHandler(BlockEntity machine, @Nullable Direction side);

    /** Identity describes the exposed structure, never its current contents. */
    default String endpointKey(BlockEntity machine, @Nullable Direction side) {
        return id() + "/" + BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock())
                + "/side/" + (side == null ? "internal" : side.getName())
                + "/class/" + machine.getClass().getName();
    }

    default String itemSlotKey(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        return endpointKey(machine, side) + "/item/" + handler.getClass().getName()
                + "/" + handler.getSlots() + "/" + slot;
    }

    default String fluidTankKey(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        return endpointKey(machine, side) + "/fluid/" + handler.getClass().getName()
                + "/" + handler.getTanks() + "/" + tank;
    }

    default IItemHandler targetItemHandler(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        return new NeoForgeSingleSlotHandler(handler, slot);
    }

    /** The common fluid API cannot address writes to an individual tank. */
    @Nullable
    default IFluidHandler targetFluidHandler(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        return tank == 0 && handler.getTanks() == 1 ? handler : null;
    }

    @Nullable
    default IItemHandler firstItemHandler(BlockEntity machine) {
        for (Direction side : supportedSides(machine)) {
            IItemHandler handler = itemHandler(machine, side);
            if (handler != null) return handler;
        }
        return null;
    }

    @Nullable
    default IFluidHandler firstFluidHandler(BlockEntity machine) {
        for (Direction side : supportedSides(machine)) {
            IFluidHandler handler = fluidHandler(machine, side);
            if (handler != null) return handler;
        }
        return null;
    }

    @Nullable
    default IEnergyStorage firstEnergyHandler(BlockEntity machine) {
        for (Direction side : supportedSides(machine)) {
            IEnergyStorage handler = energyHandler(machine, side);
            if (handler != null) return handler;
        }
        return null;
    }
}
