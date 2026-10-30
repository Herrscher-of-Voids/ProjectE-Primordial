package com.primordial.neoforge.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/** Optional Nature's Aura 41.10 public container API, with no linked optional types. */
public final class NeoForgeNaturesAuraResourceAdapter {
    public static final ResourceLocation UNIT = ResourceLocation.parse("naturesaura:aura");

    private NeoForgeNaturesAuraResourceAdapter() {}

    public static void register() {
        if (!ModList.get().isLoaded("naturesaura")) return;
        NeoForgeResourcePortRegistry.register("naturesaura",
                be -> be.getClass().getName().startsWith("de.ellpeck.naturesaura."),
                NeoForgeNaturesAuraResourceAdapter::find);
    }

    private static List<NeoForgeResourcePort> find(BlockEntity be, Direction side) {
        try {
            Object container;
            try { container = be.getClass().getMethod("getAuraContainer").invoke(be); }
            catch (NoSuchMethodException noGetter) { container = be.getClass().getField("container").get(be); }
            if (container == null) return List.of();
            return List.of(new AuraPort(be, container, "naturesaura/" + be.getClass().getName() + "/"
                    + (side == null ? "internal" : side.getName()) + "/aura_container"));
        } catch (ReflectiveOperationException | LinkageError invalid) {
            return List.of();
        }
    }

    public static final class AuraPort implements NeoForgeResourcePort {
        private final BlockEntity machine;
        private final Object container;
        private final String key;
        private final Method amount, capacity, receive, extract;

        public AuraPort(BlockEntity machine, Object container, String key) throws ReflectiveOperationException {
            this.machine = machine;
            this.container = container;
            this.key = key;
            Class<?> type = container.getClass();
            amount = type.getMethod("getStoredAura");
            capacity = type.getMethod("getMaxAura");
            receive = type.getMethod("storeAura", int.class, boolean.class);
            extract = type.getMethod("drainAura", int.class, boolean.class);
        }

        @Override public ResourceLocation unit() { return UNIT; }
        @Override public String key() { return key; }
        @Override public long amount() { return Math.max(0, number(invoke(amount))); }
        @Override public long capacity() { return Math.max(0, number(invoke(capacity))); }
        @Override public boolean canReceive() { return amount() < capacity(); }
        @Override public boolean canExtract() { return amount() > 0; }
        @Override public long receive(long requested, boolean simulate) { return transfer(requested, simulate, true); }
        @Override public long extract(long requested, boolean simulate) { return transfer(requested, simulate, false); }

        private long transfer(long requested, boolean simulate, boolean incoming) {
            if (requested <= 0 || machine.isRemoved()) return 0;
            long before = amount();
            long limit = incoming ? Math.max(0, capacity() - before) : before;
            int ask = (int) Math.min(Integer.MAX_VALUE, Math.min(requested, limit));
            if (ask <= 0) return 0;
            long reported = number(invoke(incoming ? receive : extract, ask, simulate));
            if (reported < 0 || reported > ask) throw new IllegalStateException("Invalid Aura transfer result");
            long after = amount();
            if (simulate) {
                if (before != after) throw new IllegalStateException("Aura simulation mutated storage");
                return reported;
            }
            long actual = incoming ? after - before : before - after;
            if (actual < 0 || actual > ask || actual != reported)
                throw new IllegalStateException("Aura storage change disagrees with transfer result");
            if (actual > 0) machine.setChanged();
            return actual;
        }

        private Object invoke(Method method, Object... args) {
            try { return method.invoke(container, args); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Error error) throw error;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Aura API failed", cause);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Aura API unavailable", failure); }
        }
        private static long number(Object value) {
            if (!(value instanceof Number number)) throw new IllegalStateException("Non-numeric Aura storage");
            return number.longValue();
        }
    }
}
