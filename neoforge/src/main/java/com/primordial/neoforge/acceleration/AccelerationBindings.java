package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class AccelerationBindings {
    public static final String TARGETS_KEY = "AccelerationTargets";
    public static final int HARD_LIMIT = 256;

    private AccelerationBindings() { }


    public static List<CompoundTag> view(ItemStack stack) {
        if (!AccelerationData.has(stack)) return List.of();
        CompoundTag root = AccelerationData.get(stack);
        if (!root.contains(TARGETS_KEY, Tag.TAG_LIST)) {
            return root.contains(EmcAcceleratorItem.DIMENSION_KEY) ? List.of(root) : List.of();
        }
        ListTag tags = root.getList(TARGETS_KEY, Tag.TAG_COMPOUND);
        var result = new ArrayList<CompoundTag>(Math.min(tags.size(), HARD_LIMIT));
        for (int i = 0; i < tags.size() && i < HARD_LIMIT; i++) result.add(tags.getCompound(i));
        return result;
    }

    static ListTag editable(ItemStack stack) {
        CompoundTag root = AccelerationData.get(stack);
        if (!root.contains(TARGETS_KEY, Tag.TAG_LIST)) {
            var entries = new ListTag();
            if (root.contains(EmcAcceleratorItem.DIMENSION_KEY)) {
                var previous = new CompoundTag();
                for (String key : new String[]{EmcAcceleratorItem.DIMENSION_KEY, EmcAcceleratorItem.X_KEY,
                        EmcAcceleratorItem.Y_KEY, EmcAcceleratorItem.Z_KEY, EmcAcceleratorItem.BLOCK_KEY,
                        EmcAcceleratorItem.MULTIPLIER_KEY, EmcAcceleratorItem.ENABLED_KEY}) {
                    if (root.contains(key)) previous.put(key, root.get(key).copy());
                    if (!key.equals(EmcAcceleratorItem.MULTIPLIER_KEY)) root.remove(key);
                }
                if (rate(previous) >= EmcAccelerationService.warningMultiplier()) pause(previous);
                entries.add(previous);
            }
            root.remove(EmcAcceleratorItem.WARNING_PENDING_KEY);
            root.remove("ConfirmedSession");
            root.remove("ConfirmedOwner");
            root.put(TARGETS_KEY, entries);
        }
        ListTag entries = root.getList(TARGETS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size() && i < HARD_LIMIT; i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("BindingId")) entry.putUUID("BindingId", java.util.UUID.randomUUID());
        }
        return entries;
    }

    public static int rate(CompoundTag entry) {
        int rate = entry.getInt(EmcAcceleratorItem.MULTIPLIER_KEY);
        return EmcAccelerationService.isMultiplier(rate) ? rate : EmcAccelerationService.minMultiplier();
    }

    public static boolean enabled(CompoundTag entry) { return entry.getBoolean(EmcAcceleratorItem.ENABLED_KEY); }
    public static boolean pending(CompoundTag entry) { return entry.getBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY); }

    public static BlockPos pos(CompoundTag entry) {
        return new BlockPos(entry.getInt(EmcAcceleratorItem.X_KEY), entry.getInt(EmcAcceleratorItem.Y_KEY), entry.getInt(EmcAcceleratorItem.Z_KEY));
    }

    static boolean matches(CompoundTag entry, ServerLevel level, BlockPos pos) {
        return AccelerationSettings.kind(entry) == AccelerationSettings.Kind.BLOCK
                && entry.getString(EmcAcceleratorItem.DIMENSION_KEY).equals(level.dimension().location().toString()) && pos(entry).equals(pos);
    }

    public static java.util.UUID id(CompoundTag entry) {
        return entry.hasUUID("BindingId") ? entry.getUUID("BindingId") : new java.util.UUID(0, 0);
    }

    public static CompoundTag find(ItemStack tool, java.util.UUID id) {
        return view(tool).stream().filter(entry -> id(entry).equals(id)).findFirst().orElse(null);
    }

    static void pause(CompoundTag entry) {
        entry.putBoolean(EmcAcceleratorItem.ENABLED_KEY, false);
        entry.putBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY, false);
        entry.remove("ConfirmedSession");
        entry.remove("ConfirmedOwner");
    }

    static void pauseAll(ItemStack stack) { view(stack).forEach(AccelerationBindings::pause); }
}
