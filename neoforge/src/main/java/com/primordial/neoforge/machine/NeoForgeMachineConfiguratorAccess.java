/**
 * 【文件职责】NeoForge 机器配置器的服务端目标解析与菜单有效性校验。
 * 【用途】不加载区块、不缓存方块实体；每次打开或校验都按机器键重新解析目标。
 */
package com.primordial.neoforge.machine;

import javax.annotation.Nullable;

import com.primordial.neoforge.compat.NeoForgeMachineChemicalBridge;
import com.primordial.util.MachineKey;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Shared server checks for the machine configurator entry point and menu. */
public final class NeoForgeMachineConfiguratorAccess {
    public static final double MAX_DISTANCE_SQR = 64.0D;

    private NeoForgeMachineConfiguratorAccess() {}

    @Nullable
    public static BlockEntity findLoaded(MinecraftServer server, String machineKey) {
        if (server == null || !server.isSameThread() || !validKey(machineKey)) return null;
        try {
            ResourceKey<Level> dimension = MachineKey.parseDim(machineKey);
            BlockPos pos = MachineKey.parsePos(machineKey);
            ServerLevel level = server.getLevel(dimension);
            if (level == null || !level.hasChunkAt(pos)) return null;
            BlockEntity machine = level.getBlockEntity(pos);
            return machine == null || machine.isRemoved() ? null : machine;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    public static boolean hasAnyCapability(@Nullable BlockEntity machine) {
        if (!NeoForgeMachineTargetAccess.live(machine)) return false;
        try {
            for (var side : NeoForgeCapabilityProxy.INSTANCE.supportedSides(machine)) {
                if (NeoForgeCapabilityProxy.INSTANCE.itemHandler(machine, side) != null
                        || NeoForgeCapabilityProxy.INSTANCE.fluidHandler(machine, side) != null
                        || NeoForgeCapabilityProxy.INSTANCE.energyHandler(machine, side) != null
                        || NeoForgeMachineChemicalBridge.hasCapability(machine, side)) return true;
            }
            if (NeoForgeCapabilityProxy.INSTANCE.energyHandler(machine, null) != null) return true;
            for (var side : net.minecraft.core.Direction.values())
                if (!com.primordial.neoforge.resource.NeoForgeResourcePortRegistry.find(machine, side).isEmpty()) return true;
            return !com.primordial.neoforge.resource.NeoForgeResourcePortRegistry.find(machine, null).isEmpty();
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    public static boolean canUse(ServerPlayer player, String machineKey,
                                 NeoForgeMachineConfigSavedData data) {
        if (player == null || !player.server.isSameThread() || !player.isAlive() || player.isRemoved()
                || data == null || !validKey(machineKey)) return false;
        if (!data.canEdit(machineKey, player.getUUID())) return false;
        BlockEntity machine = findLoaded(player.server, machineKey);
        if (machine == null) return false;
        try {
            if (machine.getLevel() != player.level()
                    || !MachineKey.of(player.level().dimension(), machine.getBlockPos()).equals(machineKey)) return false;
            if (player.distanceToSqr(machine.getBlockPos().getX() + 0.5D,
                    machine.getBlockPos().getY() + 0.5D,
                    machine.getBlockPos().getZ() + 0.5D) >= MAX_DISTANCE_SQR) return false;
            return findLoaded(player.server, machineKey) == machine;
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    public static boolean canOpen(ServerPlayer player, String machineKey,
                                  NeoForgeMachineConfigSavedData data) {
        if (!canUse(player, machineKey, data)) return false;
        BlockEntity machine = findLoaded(player.server, machineKey);
        return hasAnyCapability(machine) && findLoaded(player.server, machineKey) == machine;
    }

    public static boolean validKey(String machineKey) {
        if (machineKey == null || machineKey.length() > 256 || machineKey.isBlank()) return false;
        try {
            return MachineKey.of(MachineKey.parseDim(machineKey), MachineKey.parsePos(machineKey)).equals(machineKey);
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
