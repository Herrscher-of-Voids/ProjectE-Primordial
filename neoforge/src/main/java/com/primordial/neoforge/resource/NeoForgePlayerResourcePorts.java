package com.primordial.neoforge.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Native player accounts are resolved per operation, never persisted as opaque attachment data. */
public final class NeoForgePlayerResourcePorts {
    public static final int COUNT = 2;
    private static final String[] MODS = {"irons_spellbooks", "goety"};
    private static final Set<String> WARNED = new HashSet<>();
    private NeoForgePlayerResourcePorts() {}
    public static NeoForgeResourcePort find(ServerPlayer player, int index) {
        if (player == null || !player.server.isSameThread() || !player.isAlive() || player.isRemoved()
                || index < 0 || index >= COUNT || !ModList.get().isLoaded(MODS[index])) return null;
        try { return index == 0 ? NeoForgeIronManaPort.create(player) : NeoForgeGoetySoulPort.create(player); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            warn(MODS[index], unavailable); return null;
        }
    }
    static Object invoke(Method method, Object target, Object... args) {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Native player resource invocation failed", cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native player resource API changed", failure); }
    }
    static void warn(String id, Throwable failure) {
        if (WARNED.add(id)) com.mojang.logging.LogUtils.getLogger().warn("[Primordial] {} player resource API unavailable", id, failure);
    }
}
