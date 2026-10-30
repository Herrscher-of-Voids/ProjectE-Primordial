package com.primordial.neoforge.transmutation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player learning preferences; existing knowledge is not rewritten. */
public final class NeoForgeTransmutationNbtSettings extends SavedData {
    public static final String DATA_NAME = "primordial_nbt_settings";
    public static final int DEFAULT = 7;
    private static final Factory<NeoForgeTransmutationNbtSettings> FACTORY = new Factory<>(
            NeoForgeTransmutationNbtSettings::new, NeoForgeTransmutationNbtSettings::load);
    private final Map<UUID, Integer> settings = new HashMap<>();

    public static NeoForgeTransmutationNbtSettings get(MinecraftServer server) {
        NeoForgeEmcCredits.requireServerThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public int flags(UUID owner) { return settings.getOrDefault(owner, DEFAULT); }

    public void set(UUID owner, int flags) {
        if (owner == null || flags < 0 || flags > 7) throw new IllegalArgumentException("Invalid NBT settings");
        if (flags(owner) != flags) {
            settings.put(owner, flags);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        settings.forEach((owner, flags) -> players.putInt(owner.toString(), flags));
        tag.put("players", players);
        return tag;
    }

    public static NeoForgeTransmutationNbtSettings load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new NeoForgeTransmutationNbtSettings();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                int flags = players.getInt(key);
                if (players.contains(key, Tag.TAG_INT) && flags >= 0 && flags <= 7)
                    data.settings.put(UUID.fromString(key), flags);
            } catch (IllegalArgumentException invalidOwner) { }
        }
        return data;
    }
}
