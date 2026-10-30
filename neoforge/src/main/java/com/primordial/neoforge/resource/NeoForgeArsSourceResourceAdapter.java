package com.primordial.neoforge.resource;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Ars 5.13: one-argument add/remove return the final balance, not transferred Source. */
public final class NeoForgeArsSourceResourceAdapter {
    private NeoForgeArsSourceResourceAdapter() {}
    public static void register() {
        try {
            Class<?> api = Class.forName("com.hollingsworth.arsnouveau.api.source.AbstractSourceMachine");
            var storage = api.getField("sourceStorage");
            var amount = api.getMethod("getSource");
            var capacity = api.getMethod("getMaxSource");
            var receive = api.getMethod("addSource", int.class);
            var extract = api.getMethod("removeSource", int.class);
            NeoForgeResourcePortRegistry.register("ars_nouveau", api::isInstance, (machine, side) -> {
                try {
                    // Reading an uninitialized machine must not create SourceStorage or invalidate capabilities.
                    if (storage.get(machine) == null) return List.of();
                } catch (IllegalAccessException unavailable) { return List.of(); }
                return List.of(new NeoForgeResourcePort() {
                    public ResourceLocation unit() { return NeoForgeResourceUnits.machineUnit(3); }
                    public String key() { return "ars_nouveau/source/" + machine.getClass().getName(); }
                    public long amount() { return Math.max(0, ((Number) invoke(amount, machine)).longValue()); }
                    public long capacity() { return Math.max(0, ((Number) invoke(capacity, machine)).longValue()); }
                    public boolean canReceive() { return !machine.isRemoved(); }
                    public boolean canExtract() { return !machine.isRemoved(); }
                    public long receive(long requested, boolean simulate) { return move(requested, simulate, true); }
                    public long extract(long requested, boolean simulate) { return move(requested, simulate, false); }
                    private long move(long requested, boolean simulate, boolean supply) {
                        if (machine.isRemoved() || requested <= 0) return 0;
                        long before = amount();
                        int bounded = (int) Math.min(Integer.MAX_VALUE, Math.min(requested,
                                supply ? Math.max(0, capacity() - before) : before));
                        if (simulate || bounded <= 0) return bounded;
                        invoke(supply ? receive : extract, machine, bounded);
                        long delta = supply ? amount() - before : before - amount();
                        if (delta < 0 || delta > bounded) throw new IllegalStateException("Ars Source changed outside authorized transfer");
                        return delta;
                    }
                });
            });
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            com.mojang.logging.LogUtils.getLogger().warn("[Primordial] Ars Source public API is unavailable", unavailable);
        }
    }
    private static Object invoke(java.lang.reflect.Method method, BlockEntity machine, Object... args) {
        try { return method.invoke(machine, args); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Ars Source invocation failed", cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Ars Source API changed", failure); }
    }
}
