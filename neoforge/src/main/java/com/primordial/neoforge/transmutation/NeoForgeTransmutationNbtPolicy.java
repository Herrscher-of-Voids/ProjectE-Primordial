package com.primordial.neoforge.transmutation;

import java.util.Set;

import com.mojang.serialization.DynamicOps;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** The three Forge learning flags applied to safe 1.21.1 component patches. */
public final class NeoForgeTransmutationNbtPolicy {
    private static final Set<DataComponentType<?>> SAFE = Set.of(
            DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME, DataComponents.LORE, DataComponents.DYED_COLOR,
            DataComponents.HIDE_ADDITIONAL_TOOLTIP, DataComponents.HIDE_TOOLTIP, DataComponents.CUSTOM_MODEL_DATA,
            DataComponents.REPAIR_COST, DataComponents.UNBREAKABLE, DataComponents.CAN_BREAK, DataComponents.CAN_PLACE_ON,
            DataComponents.POTION_CONTENTS, DataComponents.TRIM, DataComponents.FIREWORKS,
            DataComponents.FIREWORK_EXPLOSION, DataComponents.ATTRIBUTE_MODIFIERS);

    private NeoForgeTransmutationNbtPolicy() {}

    public static ItemInfo learned(ServerPlayer player, ItemInfo info) {
        return normalize(info, NeoForgeTransmutationNbtSettings.get(player.server).flags(player.getUUID()), player.registryAccess());
    }

    public static ItemInfo persistent(ItemInfo info) {
        HolderLookup.Provider registries = null;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) registries = server.registryAccess();
        else if (FMLEnvironment.dist.isClient()) registries = ClientRegistries.get();
        return normalize(info, 7, registries);
    }

    public static ItemStack safeOutput(ItemStack sample) {
        if (sample == null || sample.isEmpty()) return ItemStack.EMPTY;
        return persistent(ItemInfo.fromStack(sample)).createStack().copyWithCount(sample.getCount());
    }

    public static ItemInfo normalize(ItemInfo info, int flags, HolderLookup.Provider registries) {
        if (info == null) throw new IllegalArgumentException("Missing item information");
        DataComponentPatch.Builder result = DataComponentPatch.builder();
        if ((flags & 1) != 0) for (DataComponentType<?> type : SAFE) copy(info.getComponentsPatch(), result, type);
        if ((flags & 2) != 0) {
            ItemStack sample = info.createStack();
            ItemStack base = new ItemStack(sample.getItem());
            int damage = sample.getOrDefault(DataComponents.DAMAGE, 0);
            if (base.isDamageableItem() && damage > 0)
                result.set(DataComponents.DAMAGE, Math.min(base.getMaxDamage(), damage));
        }
        if ((flags & 4) != 0) {
            copy(info.getComponentsPatch(), result, DataComponents.ENCHANTMENTS);
            copy(info.getComponentsPatch(), result, DataComponents.STORED_ENCHANTMENTS);
        }
        DataComponentPatch patch = result.build();
        if (patch.isEmpty()) return info.itemOnly();
        try {
            DynamicOps<Tag> ops = registries == null ? NbtOps.INSTANCE : registries.createSerializationContext(NbtOps.INSTANCE);
            Tag encoded = DataComponentPatch.CODEC.encodeStart(ops, patch).result().orElse(null);
            if (encoded == null || !bounded(encoded, 0, new int[]{4096})) return info.itemOnly();
            // Codec round-trip also prevents mutable text/components from sharing the source sample.
            DataComponentPatch copied = DataComponentPatch.CODEC.parse(ops, encoded).result().orElse(DataComponentPatch.EMPTY);
            return ItemInfo.fromItem(info.getItem(), copied);
        } catch (RuntimeException | LinkageError invalid) {
            return info.itemOnly();
        }
    }

    private static <T> void copy(DataComponentPatch source, DataComponentPatch.Builder target, DataComponentType<T> type) {
        var value = source.get(type);
        if (value != null) {
            if (value.isEmpty()) target.remove(type);
            else target.set(type, value.get());
        }
    }

    private static boolean bounded(Tag tag, int depth, int[] remaining) {
        if (depth > 16 || --remaining[0] < 0 || tag.sizeInBytes() > 32768) return false;
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) if (!bounded(compound.get(key), depth + 1, remaining)) return false;
        } else if (tag instanceof ListTag list) {
            for (Tag entry : list) if (!bounded(entry, depth + 1, remaining)) return false;
        }
        return true;
    }

    private static final class ClientRegistries {
        static HolderLookup.Provider get() {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            return level == null ? null : level.registryAccess();
        }
    }
}
