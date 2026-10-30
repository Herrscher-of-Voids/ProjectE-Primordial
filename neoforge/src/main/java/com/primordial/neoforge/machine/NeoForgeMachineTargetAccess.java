package com.primordial.neoforge.machine;

import javax.annotation.Nullable;

import com.primordial.data.MachineTarget;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Re-resolves the bound face and structure on every query; callers must not cache the returned view. */
public final class NeoForgeMachineTargetAccess {
    private NeoForgeMachineTargetAccess() {}

    @Nullable
    public static IItemHandler items(@Nullable NeoForgeMachineProxy proxy, @Nullable BlockEntity machine,
                                    @Nullable MachineTarget target) {
        if (proxy == null || target == null) return null;
        try {
            if (!live(machine) || !target.bound() || target.side() == MachineTarget.INTERNAL_SIDE) return null;
            BlockState state = machine.getBlockState();
            target = target.resolve(state);
            if (!target.bound()) return null;
            Direction side = target.direction();
            IItemHandler handler = proxy.itemHandler(machine, side);
            if (handler == null || target.index() >= handler.getSlots()
                    || !target.key().equals(proxy.itemSlotKey(machine, side, handler, target.index()))) return null;
            IItemHandler view = proxy.targetItemHandler(machine, side, handler, target.index());
            return live(machine) && state.equals(machine.getBlockState()) ? view : null;
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    @Nullable
    public static IFluidHandler fluids(@Nullable NeoForgeMachineProxy proxy, @Nullable BlockEntity machine,
                                      @Nullable MachineTarget target) {
        if (proxy == null || target == null) return null;
        try {
            if (!live(machine) || !target.bound() || target.side() == MachineTarget.INTERNAL_SIDE) return null;
            BlockState state = machine.getBlockState();
            target = target.resolve(state);
            if (!target.bound()) return null;
            Direction side = target.direction();
            IFluidHandler handler = proxy.fluidHandler(machine, side);
            if (handler == null || target.index() >= handler.getTanks()
                    || !target.key().equals(proxy.fluidTankKey(machine, side, handler, target.index()))) return null;
            IFluidHandler view = proxy.targetFluidHandler(machine, side, handler, target.index());
            return live(machine) && state.equals(machine.getBlockState()) ? view : null;
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    @Nullable
    public static IEnergyStorage energy(@Nullable NeoForgeMachineProxy proxy, @Nullable BlockEntity machine,
                                       @Nullable MachineTarget target) {
        if (proxy == null || target == null) return null;
        try {
            if (!live(machine) || !target.bound() || target.index() != 0) return null;
            BlockState state = machine.getBlockState();
            target = target.resolve(state);
            if (!target.bound()) return null;
            Direction side = target.directionOrNull();
            IEnergyStorage handler = proxy.energyHandler(machine, side);
            if (handler == null || !target.key().equals(energyKey(proxy, machine, side, handler))) return null;
            return live(machine) && state.equals(machine.getBlockState()) ? handler : null;
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    public static String energyKey(NeoForgeMachineProxy proxy, BlockEntity machine, @Nullable Direction side,
                                   IEnergyStorage handler) {
        return proxy.endpointKey(machine, side) + "/energy/" + handler.getClass().getName();
    }

    static boolean live(@Nullable BlockEntity machine) {
        if (machine == null || machine.isRemoved() || !(machine.getLevel() instanceof ServerLevel level)) return false;
        return level.getServer().isSameThread() && level.hasChunkAt(machine.getBlockPos())
                && level.getBlockEntity(machine.getBlockPos()) == machine;
    }
}
