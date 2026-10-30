package com.primordial.neoforge.resource;

import java.util.List;
import com.primordial.data.MachineTarget;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Re-resolves the current endpoint and selected relative face; no cached third-party handler survives a call. */
public final class NeoForgeResourcePortAccess {
    private NeoForgeResourcePortAccess() {}
    public static boolean live(BlockEntity machine) {
        if (machine == null || machine.isRemoved() || !(machine.getLevel() instanceof ServerLevel level)) return false;
        return level.getServer().isSameThread() && level.hasChunkAt(machine.getBlockPos())
                && level.getBlockEntity(machine.getBlockPos()) == machine;
    }
    public static List<NeoForgeResourcePort> find(BlockEntity machine, Direction side, ResourceLocation unit) {
        if (!live(machine) || unit == null) return List.of();
        var state = machine.getBlockState();
        var ports = NeoForgeResourcePortRegistry.find(machine, side).stream().filter(port -> unit.equals(port.unit())).toList();
        return live(machine) && state.equals(machine.getBlockState()) ? ports : List.of();
    }
    public static NeoForgeResourcePort resolve(BlockEntity machine, MachineTarget target, ResourceLocation unit) {
        if (target == null || !target.bound() || !live(machine)) return null;
        var state = machine.getBlockState();
        MachineTarget resolved = target.resolve(state);
        if (!resolved.bound()) return null;
        List<NeoForgeResourcePort> ports = find(machine, resolved.directionOrNull(), unit);
        if (resolved.index() >= ports.size()) return null;
        NeoForgeResourcePort port = ports.get(resolved.index());
        return resolved.key().equals(key(machine, resolved.directionOrNull(), port)) && live(machine)
                && state.equals(machine.getBlockState()) ? port : null;
    }
    public static String key(BlockEntity machine, Direction side, NeoForgeResourcePort port) {
        String key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock())
                + "/resource/" + machine.getClass().getName() + "/" + (side == null ? "internal" : side.getName())
                + "/" + port.unit() + "/" + port.key();
        return key.length() <= MachineTarget.MAX_KEY_LENGTH ? key : "";
    }
}
