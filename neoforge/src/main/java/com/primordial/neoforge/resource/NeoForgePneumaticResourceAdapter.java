package com.primordial.neoforge.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/** Uses PNC 8.2.23's sided public capability helper instead of private handler fields. */
public final class NeoForgePneumaticResourceAdapter {
    public static final ResourceLocation UNIT = ResourceLocation.parse("pneumaticcraft:pressure");

    private NeoForgePneumaticResourceAdapter() {}

    public static void register() {
        if (!ModList.get().isLoaded("pneumaticcraft")) return;
        try {
            Method get = Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities")
                    .getMethod("getAirHandler", BlockEntity.class, Direction.class);
            NeoForgeResourcePortRegistry.register("pneumaticcraft", be -> true, (be, side) -> find(be, side, get));
        } catch (ReflectiveOperationException | LinkageError unavailable) { }
    }

    private static List<NeoForgeResourcePort> find(BlockEntity be, Direction side, Method get) {
        try {
            Object result = get.invoke(null, be, side);
            Object handler = result instanceof Optional<?> optional ? optional.orElse(null) : null;
            if (handler == null) return List.of();
            return List.of(new AirPort(be, handler, "pneumaticcraft/" + be.getClass().getName() + "/"
                    + (side == null ? "internal" : side.getName()) + "/air_handler"));
        } catch (ReflectiveOperationException | LinkageError unavailable) { return List.of(); }
    }

    public static final class AirPort implements NeoForgeResourcePort {
        private final BlockEntity machine;
        private final Object handler;
        private final String key;
        private final Method amount, volume, danger, add;

        public AirPort(BlockEntity machine, Object handler, String key) throws ReflectiveOperationException {
            this.machine = machine;
            this.handler = handler;
            this.key = key;
            Class<?> type = handler.getClass();
            amount = type.getMethod("getAir");
            volume = type.getMethod("getVolume");
            danger = type.getMethod("getDangerPressure");
            add = type.getMethod("addAir", int.class);
        }

        @Override public ResourceLocation unit() { return UNIT; }
        @Override public String key() { return key; }
        @Override public long amount() { return Math.max(0, number(invoke(amount)).longValue()); }
        @Override public long capacity() {
            double max = number(invoke(volume)).doubleValue() * number(invoke(danger)).doubleValue();
            return !Double.isFinite(max) || max <= 0 ? 0 : (long) Math.floor(Math.min(Integer.MAX_VALUE, max));
        }
        @Override public boolean canReceive() { return amount() < capacity(); }
        @Override public boolean canExtract() { return amount() > 0; }
        @Override public long receive(long requested, boolean simulate) { return transfer(requested, simulate, true); }
        @Override public long extract(long requested, boolean simulate) { return transfer(requested, simulate, false); }

        private long transfer(long requested, boolean simulate, boolean incoming) {
            if (requested <= 0 || machine.isRemoved()) return 0;
            long before = amount();
            int ask = (int) Math.min(Integer.MAX_VALUE, Math.min(requested,
                    incoming ? Math.max(0, capacity() - before) : before));
            if (ask <= 0 || simulate) return ask;
            invoke(add, incoming ? ask : -ask);
            long after = amount();
            long actual = incoming ? after - before : before - after;
            if (actual < 0 || actual > ask) throw new IllegalStateException("Invalid pneumatic air storage change");
            if (actual > 0) machine.setChanged();
            return actual;
        }

        private Object invoke(Method method, Object... args) {
            try { return method.invoke(handler, args); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Error error) throw error;
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("PNC API failed", cause);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("PNC API unavailable", failure); }
        }
        private static Number number(Object value) {
            if (!(value instanceof Number number)) throw new IllegalStateException("Non-numeric pneumatic storage");
            return number;
        }
    }
}
