package com.primordial.neoforge.compat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import com.primordial.data.MachineTarget;
import com.primordial.neoforge.machine.NeoForgeMachineProxy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Optional chemical automation; common classes never reference Mekanism API types. */
public final class NeoForgeMachineChemicalTasks {
    private static Method run;

    private NeoForgeMachineChemicalTasks() {}

    public static long run(MinecraftServer server, UUID owner, CompoundTag sample,
                           NeoForgeMachineProxy proxy, BlockEntity machine, MachineTarget target,
                           boolean supply, long requested) {
        if (server == null || !server.isSameThread() || owner == null || sample == null || proxy == null
                || machine == null || target == null || requested <= 0 || !validSample(sample)
                || !NeoForgeMachineChemicalBridge.available()) return 0;
        try {
            if (run == null) run = Class.forName("com.primordial.neoforge.compat.mekanism.NeoForgeMachineChemicalTransfer")
                    .getMethod("run", MinecraftServer.class, UUID.class, CompoundTag.class,
                            NeoForgeMachineProxy.class, BlockEntity.class, MachineTarget.class, boolean.class, long.class);
            return (long) run.invoke(null, server, owner, sample, proxy, machine, target, supply, requested);
        } catch (InvocationTargetException failure) {
            // Delivery failures may follow a real write; preserve the transaction's uncertainty semantics.
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("Mekanism machine transfer failed", failure.getCause());
        } catch (ReflectiveOperationException | LinkageError failure) {
            throw new IllegalStateException("Cannot access installed Mekanism machine transfer API", failure);
        }
    }

    private static boolean validSample(CompoundTag sample) {
        CompoundTag filter = sample.contains("g", Tag.TAG_COMPOUND) ? sample.getCompound("g") : sample;
        if (!filter.contains("gasName", Tag.TAG_STRING)) return false;
        if (filter.contains("amount", Tag.TAG_LONG)) return filter.getLong("amount") > 0;
        return filter.contains("amount", Tag.TAG_INT) && filter.getInt("amount") > 0;
    }
}
