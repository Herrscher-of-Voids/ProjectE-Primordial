package com.primordial.neoforge.machine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mojang.logging.LogUtils;
import com.primordial.data.ConfigLimits;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Transient timing is scoped to one server; configurations remain in SavedData. */
@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeMachineTaskScheduler {
    public static final int SLOTS_PER_CONFIG = 50;
    private static final Map<MinecraftServer, State> SERVERS = new HashMap<>();

    private static final class State {
        final Map<String, long[]> timers = new HashMap<>();
        final Set<String> warned = new HashSet<>();
        int lastTick;
        boolean tickSeen;
        boolean running;
    }

    private NeoForgeMachineTaskScheduler() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        NeoForgeMachineTaskExecutor.runAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { clear(event.getServer()); }

    static boolean beginTick(MinecraftServer server) {
        if (server == null || !server.isSameThread()) return false;
        State state = SERVERS.computeIfAbsent(server, ignored -> new State());
        int tick = server.getTickCount();
        if (state.running || state.tickSeen && state.lastTick == tick) return false;
        state.running = state.tickSeen = true;
        state.lastTick = tick;
        return true;
    }

    static void endTick(MinecraftServer server) {
        State state = SERVERS.get(server);
        if (state != null) state.running = false;
    }

    public static boolean intervalDue(MinecraftServer server, String key, int slot, int interval, long gameTime) {
        if (server == null || !server.isSameThread() || slot < 0 || slot >= SLOTS_PER_CONFIG || gameTime < 0) return false;
        long[] timers = SERVERS.computeIfAbsent(server, ignored -> new State()).timers
                .computeIfAbsent(key, ignored -> new long[SLOTS_PER_CONFIG]);
        if (gameTime < timers[slot]) { timers[slot] = gameTime; return false; }
        if (gameTime - timers[slot] < ConfigLimits.intervalTicks(interval)) return false;
        timers[slot] = gameTime;
        return true;
    }

    static void retain(MinecraftServer server, Set<String> keys) {
        State state = SERVERS.get(server);
        if (state == null) return;
        state.timers.keySet().retainAll(keys);
        state.warned.retainAll(keys);
    }

    static void failed(MinecraftServer server, String key, Throwable failure) {
        State state = SERVERS.get(server);
        if (state != null && state.warned.add(key)) LogUtils.getLogger().error(
                "[Primordial] Machine task failed at {}; configuration retained", key, failure);
    }

    static void succeeded(MinecraftServer server, String key) {
        State state = SERVERS.get(server);
        if (state != null) state.warned.remove(key);
    }

    public static void clear(MinecraftServer server) { SERVERS.remove(server); }
}
