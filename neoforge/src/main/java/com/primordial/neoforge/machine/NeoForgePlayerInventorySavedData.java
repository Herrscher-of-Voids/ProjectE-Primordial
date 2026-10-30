package com.primordial.neoforge.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.primordial.data.ConfigLimits;
import com.primordial.data.SampleMode;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent, per-player rules for replenishing fixed inventory slots. */
public final class NeoForgePlayerInventorySavedData extends SavedData {
    private static final String DATA_NAME = "primordial_player_inventory_configs";
    private static final Factory<NeoForgePlayerInventorySavedData> FACTORY = new Factory<>(
            NeoForgePlayerInventorySavedData::new, NeoForgePlayerInventorySavedData::load);

    public record Rule(ItemStack filter, int targetCount, int intervalTicks, SampleMode mode, boolean enabled) {
        public Rule {
            filter = filter.copyWithCount(1);
        }

        @Override public ItemStack filter() { return filter.copy(); }
    }

    private final Map<UUID, Map<Integer, Rule>> rules = new HashMap<>();
    private final Map<UUID, Integer> resourceFlags = new HashMap<>();

    public boolean resourceEnabled(UUID owner, int index) {
        return owner != null && index >= 0 && index < 2 && (resourceFlags.getOrDefault(owner, 0) & 1 << index) != 0;
    }

    public void toggleResource(UUID owner, int index) {
        if (owner == null || index < 0 || index >= 2) return;
        int flags = resourceFlags.getOrDefault(owner, 0) ^ 1 << index;
        if (flags == 0) resourceFlags.remove(owner); else resourceFlags.put(owner, flags);
        setDirty();
    }

    public static NeoForgePlayerInventorySavedData get(MinecraftServer server) {
        NeoForgeEmcCredits.requireServerThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Rule get(UUID owner, int slot) {
        return rules.getOrDefault(owner, Map.of()).get(slot);
    }

    public Map<Integer, Rule> rules(UUID owner) {
        return Map.copyOf(rules.getOrDefault(owner, Map.of()));
    }

    public void set(UUID owner, int slot, ItemStack filter, int targetCount, int intervalTicks,
                    SampleMode mode, boolean enabled) {
        if (owner == null || slot < 0 || slot >= 36 || filter == null || filter.isEmpty()) return;
        rules.computeIfAbsent(owner, ignored -> new HashMap<>()).put(slot,
                new Rule(filter, ConfigLimits.amount(targetCount, 0), ConfigLimits.intervalTicks(intervalTicks),
                        mode == null ? SampleMode.KEEP_LEVEL : mode, enabled));
        setDirty();
    }

    public void setEnabled(UUID owner, int slot, boolean enabled) {
        Rule current = get(owner, slot);
        if (current == null || current.enabled() == enabled) return;
        rules.get(owner).put(slot, new Rule(current.filter(), current.targetCount(), current.intervalTicks(),
                current.mode(), enabled));
        setDirty();
    }

    public void clear(UUID owner, int slot) {
        Map<Integer, Rule> playerRules = rules.get(owner);
        if (playerRules == null || playerRules.remove(slot) == null) return;
        if (playerRules.isEmpty()) rules.remove(owner);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag root = new CompoundTag();
        rules.forEach((owner, playerRules) -> {
            ListTag entries = new ListTag();
            playerRules.forEach((slot, rule) -> {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", slot);
                entry.putInt("count", rule.targetCount());
                entry.putInt("intervalTicks", rule.intervalTicks());
                entry.putString("mode", rule.mode().name());
                entry.putBoolean("enabled", rule.enabled());
                entry.put("item", rule.filter().save(registries));
                entries.add(entry);
            });
            if (!entries.isEmpty()) root.put(owner.toString(), entries);
        });
        tag.put("rules", root);
        CompoundTag resources = new CompoundTag();
        resourceFlags.forEach((owner, flags) -> resources.putInt(owner.toString(), flags));
        tag.put("playerResources", resources);
        return tag;
    }

    public static NeoForgePlayerInventorySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NeoForgePlayerInventorySavedData data = new NeoForgePlayerInventorySavedData();
        CompoundTag resources = tag.getCompound("playerResources");
        for (String id : resources.getAllKeys()) try {
            if (resources.contains(id, Tag.TAG_INT) && resources.getInt(id) >= 0 && resources.getInt(id) <= 3
                    && resources.getInt(id) != 0) data.resourceFlags.put(UUID.fromString(id), resources.getInt(id));
        } catch (IllegalArgumentException invalidOwner) { }
        CompoundTag root = tag.getCompound("rules");
        for (String id : root.getAllKeys()) {
            UUID owner;
            try { owner = UUID.fromString(id); }
            catch (IllegalArgumentException invalidOwner) { continue; }
            ListTag entries = root.getList(id, Tag.TAG_COMPOUND);
            for (int index = 0; index < entries.size(); index++) {
                CompoundTag entry = entries.getCompound(index);
                int slot = entry.getInt("slot");
                if (slot < 0 || slot >= 36) continue;
                ItemStack filter = ItemStack.parseOptional(registries, entry.getCompound("item"));
                if (filter.isEmpty()) continue;
                SampleMode mode;
                try { mode = SampleMode.valueOf(entry.getString("mode")); }
                catch (IllegalArgumentException invalidMode) { mode = SampleMode.KEEP_LEVEL; }
                data.set(owner, slot, filter, entry.getInt("count"),
                        entry.contains("intervalTicks") ? entry.getInt("intervalTicks") : 1,
                        mode, entry.getBoolean("enabled"));
            }
        }
        data.setDirty(false);
        return data;
    }
}
