package com.primordial.neoforge.charm;

import java.util.List;
import java.util.Map;

import com.primordial.item.CharmNbtFields;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Forge's six rule lists, stored as custom data; NBT rules compare serialized 1.21.1 components. */
public final class NeoForgeCharmFilters {
    public static final int MAX_BYTES = 32768;
    public static final int MAX_RULES = 256;
    private NeoForgeCharmFilters() {}

    public static String key(int type, boolean blacklist) {
        return (blacklist ? "Blacklist" : "Whitelist") + switch (type) {
            case 1 -> "Nbt"; case 2 -> "Tags"; default -> "Items";
        };
    }

    public static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static CompoundTag components(ItemStack sample, HolderLookup.Provider registries) {
        return sample.isEmpty() ? new CompoundTag() : ((CompoundTag) sample.save(registries)).getCompound("components").copy();
    }

    public static CompoundTag snapshot(ItemStack charm) {
        CompoundTag result = new CompoundTag(), data = data(charm);
        for (int type = 0; type < 3; type++) for (boolean black : new boolean[]{false, true}) {
            String key = key(type, black);
            if (data.contains(key)) result.put(key, data.get(key).copy());
        }
        return result;
    }

    public static ListTag rules(CompoundTag root, int type, boolean blacklist) {
        return root.getList(key(type, blacklist), type == 1 ? Tag.TAG_COMPOUND : Tag.TAG_STRING);
    }

    public static boolean accepts(ItemStack charm, ItemStack sample, HolderLookup.Provider registries) {
        if (sample == null || sample.isEmpty()) return false;
        CompoundTag root = snapshot(charm);
        if (!bounded(root)) return false;
        String id = BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
        CompoundTag actual = null;
        for (int type = 0; type < 3; type++) {
            if (type == 1 && (!rules(root, type, false).isEmpty() || !rules(root, type, true).isEmpty()))
                actual = components(sample, registries);
            if (matches(rules(root, type, true), type, id, sample, actual)) return false;
            ListTag allow = rules(root, type, false);
            if (!allow.isEmpty() && !matches(allow, type, id, sample, actual)) return false;
        }
        return true;
    }

    private static boolean matches(ListTag list, int type, String id, ItemStack sample, CompoundTag actual) {
        for (int i = 0; i < Math.min(MAX_RULES, list.size()); i++) {
            if (type == 0 && id.equals(list.getString(i))) return true;
            if (type == 2) {
                ResourceLocation tag = ResourceLocation.tryParse(list.getString(i));
                if (tag != null && sample.is(TagKey.create(Registries.ITEM, tag))) return true;
            }
            if (type != 1) continue;
            CompoundTag rule = list.getCompound(i);
            if (!rule.getString("id").isEmpty() && !id.equals(rule.getString("id"))) continue;
            if (rule.contains("fields", Tag.TAG_COMPOUND)) {
                CompoundTag fields = rule.getCompound("fields");
                if (!fields.isEmpty() && fields.size() <= CharmNbtFields.MAX_FIELDS
                        && fields.getAllKeys().stream().allMatch(path -> fields.get(path).equals(CharmNbtFields.value(actual, path)))) return true;
            } else {
                CompoundTag expected = rule.contains("tag", Tag.TAG_COMPOUND) ? rule.getCompound("tag")
                        : rule.contains("nbt", Tag.TAG_COMPOUND) ? rule.getCompound("nbt") : null;
                if (expected != null && expected.equals(actual)) return true;
            }
        }
        return false;
    }

    public static boolean add(ItemStack charm, ItemStack sample, int type, boolean blacklist,
                              List<String> selected, HolderLookup.Provider registries) {
        if (sample.isEmpty() || type < 0 || type > 2 || selected.size() > 128) return false;
        CompoundTag root = data(charm);
        if (!bounded(snapshot(charm))) return false;
        ListTag list = rules(root, type, blacklist);
        if (list.size() >= MAX_RULES) return false;
        if (type == 0) append(list, StringTag.valueOf(BuiltInRegistries.ITEM.getKey(sample.getItem()).toString()));
        else if (type == 1) {
            CompoundTag rule = new CompoundTag(), actual = components(sample, registries);
            rule.putString("id", BuiltInRegistries.ITEM.getKey(sample.getItem()).toString());
            if (selected.isEmpty()) rule.put("tag", actual);
            else {
                CompoundTag fields = new CompoundTag();
                Map<String, Tag> allowed = CharmNbtFields.fields(actual);
                for (String path : selected) {
                    if (!allowed.containsKey(path)) return false;
                    fields.put(path, allowed.get(path).copy());
                }
                rule.put("fields", fields);
            }
            append(list, rule);
        } else {
            var tags = sample.getTags().map(tag -> tag.location().toString()).toList();
            if (selected.isEmpty() || selected.stream().anyMatch(value -> !tags.contains(value))) return false;
            for (String tag : selected) {
                if (!list.contains(StringTag.valueOf(tag)) && list.size() >= MAX_RULES) return false;
                append(list, StringTag.valueOf(tag));
            }
        }
        root.put(key(type, blacklist), list);
        if (root.sizeInBytes() > MAX_BYTES) return false;
        charm.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return true;
    }

    public static boolean remove(ItemStack charm, int type, boolean blacklist, int index) {
        if (type < 0 || type > 2) return false;
        CompoundTag root = data(charm);
        ListTag list = rules(root, type, blacklist);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        charm.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return true;
    }

    public static boolean bounded(CompoundTag root) {
        if (root.sizeInBytes() > MAX_BYTES) return false;
        for (int type = 0; type < 3; type++) for (boolean black : new boolean[]{false, true}) {
            String key = key(type, black);
            if (!root.contains(key)) continue;
            if (!(root.get(key) instanceof ListTag list) || list.size() > MAX_RULES
                    || !list.isEmpty() && list.getElementType() != (type == 1 ? Tag.TAG_COMPOUND : Tag.TAG_STRING)) return false;
        }
        return true;
    }

    private static void append(ListTag rules, Tag value) { if (!rules.contains(value)) rules.add(value); }
}
