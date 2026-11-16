package com.primordial.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class TransmutationNbtSettings extends SavedData {
    public static final int DEFAULT = 7;
    private final Map<UUID, Integer> settings = new ConcurrentHashMap<>();
    public static TransmutationNbtSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TransmutationNbtSettings::load, TransmutationNbtSettings::new, "primordial_nbt_settings");
    }
    public int flags(UUID player) { return settings.getOrDefault(player, DEFAULT); }
    public void set(UUID player, int flags) {
        if (player == null || flags < 0 || flags > 15) throw new IllegalArgumentException("Invalid NBT settings");
        if (flags(player) != flags) { settings.put(player, flags); setDirty(); }
    }
    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        settings.forEach((id, flags) -> players.putInt(id.toString(), flags));
        tag.put("players", players);
        return tag;
    }
    public static TransmutationNbtSettings load(CompoundTag tag) {
        var data = new TransmutationNbtSettings();
        var players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                int flags = players.getInt(key);
                if (players.contains(key, 3) && flags >= 0 && flags <= 15) data.settings.put(UUID.fromString(key), flags);
            } catch (IllegalArgumentException ignored) { }
        }
        return data;
    }
}
