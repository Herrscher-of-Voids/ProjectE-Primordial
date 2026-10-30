package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.S2CAccelerationMarkersPacket;
import com.primordial.neoforge.acceleration.S2CAccelerationMarkersPacket.Marker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.lang.ref.WeakReference;
import java.util.*;


final class AccelerationMarkers {
    private static final int MAX_TRACKED = 2048;
    private static final Map<Object, Active> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, List<Marker>> SENT = new HashMap<>();
    private record Active(Marker marker, int tick, WeakReference<ItemStack> tool, UUID binding) { }
    private AccelerationMarkers() { }
    private record EntityKey(net.minecraft.resources.ResourceLocation dimension, int id) { }
    static void record(MinecraftServer server, ItemStack tool, CompoundTag entry, ServerLevel level, BlockPos pos, int rate, int entityId) {
        Object key = entityId >= 0 ? new EntityKey(level.dimension().location(), entityId) : GlobalPos.of(level.dimension(), pos);
        if (ACTIVE.size() < MAX_TRACKED || ACTIVE.containsKey(key)) ACTIVE.put(key,
                new Active(new Marker(level.dimension().location(), pos.immutable(), rate, entityId), server.getTickCount(),
                        new WeakReference<>(tool), AccelerationBindings.id(entry)));
    }
    static void synchronize(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % 5 != 0) return;
        ACTIVE.values().removeIf(active -> {
            ItemStack tool = active.tool().get();
            CompoundTag current = tool == null ? null : AccelerationBindings.find(tool, active.binding());
            return tick - active.tick() > 10 || current == null || !AccelerationBindings.enabled(current)
                    || AccelerationRun.expired(tool, server);
        });
        var players = server.getPlayerList().getPlayers();
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (var player : players) {
            var nearby = ACTIVE.values().stream().map(Active::marker)
                    .filter(marker -> marker.dimension().equals(player.level().dimension().location())
                            && marker.position().distToCenterSqr(player.position()) <= 64D * 64D)
                    .sorted(Comparator.comparingDouble(marker -> marker.position().distToCenterSqr(player.position())))
                    .limit(S2CAccelerationMarkersPacket.MAX_MARKERS).toList();
            if (!nearby.equals(SENT.getOrDefault(player.getUUID(), List.of())) || !nearby.isEmpty() && tick % 20 == 0) {
                S2CAccelerationMarkersPacket.send(player, nearby);
                SENT.put(player.getUUID(), nearby);
            }
        }
    }
    static void clear() { ACTIVE.clear(); SENT.clear(); }
}
