package com.primordial.neoforge.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.machine.NeoForgeMachinePortPage;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

/** Optional Mekanism machine-port access without third-party API types in common code. */
public final class NeoForgeMachineChemicalBridge {
    private static final String IMPLEMENTATION = "com.primordial.neoforge.compat.mekanism.NeoForgeMachineChemicalAccess";
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private NeoForgeMachineChemicalBridge() {}

    public static boolean available() { return ModList.get().isLoaded("mekanism"); }

    public static boolean hasCapability(BlockEntity machine, Direction side) {
        if (!available() || machine == null) return false;
        Object value = call("hasCapability", new Class<?>[]{BlockEntity.class, Direction.class}, machine, side);
        return Boolean.TRUE.equals(value);
    }

    public static NeoForgeMachinePortPage read(BlockEntity machine, Direction side, int page) {
        if (!available() || machine == null) return empty();
        Object value = call("read", new Class<?>[]{BlockEntity.class, Direction.class, int.class}, machine, side, page);
        return value instanceof NeoForgeMachinePortPage result ? result : empty();
    }

    private static Object call(String name, Class<?>[] parameters, Object... arguments) {
        try {
            Method method = METHODS.get(name);
            if (method == null) {
                method = Class.forName(IMPLEMENTATION).getMethod(name, parameters);
                Method existing = METHODS.putIfAbsent(name, method);
                if (existing != null) method = existing;
            }
            return method.invoke(null, arguments);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (WARNED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                    "[Primordial] Optional Mekanism machine chemical ports unavailable", failure);
            return null;
        }
    }

    private static NeoForgeMachinePortPage empty() {
        return new NeoForgeMachinePortPage("", 0, 0, java.util.List.of());
    }
}
