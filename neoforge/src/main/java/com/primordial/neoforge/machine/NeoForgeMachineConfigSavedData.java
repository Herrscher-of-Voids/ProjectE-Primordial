package com.primordial.neoforge.machine;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.TreeMap;

import com.primordial.data.MachineTarget;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.data.ConfigLimits;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;

/** Keeps the Forge-compatible machine-config NBT intact while NeoForge ports its consumers. */
public final class NeoForgeMachineConfigSavedData extends SavedData {
    public static final String DATA_NAME = "primordial_machine_configs";
    private static final Factory<NeoForgeMachineConfigSavedData> FACTORY = new Factory<>(
            NeoForgeMachineConfigSavedData::new, NeoForgeMachineConfigSavedData::load);

    private final Map<String, Tag> configs = new TreeMap<>();
    private final CompoundTag extra = new CompoundTag();
    private CompoundTag blockedSnapshot;

    public static NeoForgeMachineConfigSavedData get(MinecraftServer server) {
        NeoForgeEmcCredits.requireServerThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized boolean available() { return blockedSnapshot == null; }

    /** Owner is fixed at first creation; existing ownerless or malformed entries are never claimed. */
    public synchronized CompoundTag getOrCreate(String machineKey, UUID owner) {
        if (!available() || !validKey(machineKey) || owner == null) return null;
        Tag stored = configs.get(machineKey);
        if (stored != null) return stored instanceof CompoundTag compound ? compound.copy() : null;
        CompoundTag config = new CompoundTag();
        config.putUUID("owner", owner);
        configs.put(machineKey, config.copy());
        setDirty();
        return config;
    }

    public synchronized CompoundTag config(String machineKey) {
        Tag stored = validKey(machineKey) ? configs.get(machineKey) : null;
        return stored instanceof CompoundTag compound ? compound.copy() : null;
    }

    /** Tasks iterate defensive snapshots; malformed entries remain stored and never become executable defaults. */
    public synchronized Map<String, CompoundTag> snapshots() {
        Map<String, CompoundTag> result = new TreeMap<>();
        if (available()) configs.forEach((key, value) -> {
            if (value instanceof CompoundTag compound) result.put(key, compound.copy());
        });
        return java.util.Collections.unmodifiableMap(result);
    }

    public synchronized boolean canEdit(String machineKey, UUID editor) {
        CompoundTag config = config(machineKey);
        return available() && editor != null && config != null
                && config.hasUUID("owner") && editor.equals(config.getUUID("owner"));
    }

    public synchronized List<MachineTarget> targets(String machineKey, int section, int sample) {
        CompoundTag config = config(machineKey);
        if (!validAddress(section, sample) || config == null) return List.of();
        if (section == 4 || section == 5) {
            if (!validCompound(config, "en")) return List.of();
            MachineTarget target = MachineTarget.load(config.getCompound("en")
                    .getCompound(section == 4 ? "supplyPort" : "recoverPort"));
            return target.bound() ? List.of(target) : List.of();
        }
        String listKey = listKey(section);
        if (!validList(config, listKey)) return List.of();
        ListTag samples = config.getList(listKey, Tag.TAG_COMPOUND);
        if (sample >= samples.size()) return List.of();
        CompoundTag sampleTag = samples.getCompound(sample);
        LinkedHashSet<MachineTarget> result = new LinkedHashSet<>();
        if (sampleTag.contains("targets", Tag.TAG_LIST)) {
            ListTag stored = sampleTag.getList("targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < stored.size(); i++) addBound(result, MachineTarget.load(stored.getCompound(i)));
        }
        if (result.isEmpty()) addBound(result, MachineTarget.load(sampleTag.getCompound("target")));
        return List.copyOf(result);
    }

    /** Updates only target NBT; filters, amounts, modes and unknown legacy fields stay untouched. */
    public synchronized boolean updateTarget(String machineKey, UUID editor, int section, int sample,
                                             MachineTarget target, boolean append) {
        if (!canEdit(machineKey, editor) || !validAddress(section, sample) || target == null
                || (!target.bound() && !target.equals(MachineTarget.UNBOUND))) return false;
        List<MachineTarget> updated = new ArrayList<>();
        if (append && target.bound()) updated.addAll(targets(machineKey, section, sample));
        if (target.bound() && !updated.contains(target)) updated.add(target);
        return writeTargets(machineKey, section, sample, updated);
    }

    public synchronized boolean replaceTargets(String machineKey, UUID editor, int section, int sample,
                                               List<MachineTarget> targets) {
        if (!canEdit(machineKey, editor) || !validAddress(section, sample) || targets == null || targets.size() > 512
                || (section == 4 || section == 5) && targets.size() > 1) return false;
        LinkedHashSet<MachineTarget> unique = new LinkedHashSet<>();
        for (MachineTarget target : targets) {
            if (target == null || !target.bound() || section < 4 && target.side() >= 6
                    || (section == 4 || section == 5) && target.index() != 0) return false;
            unique.add(target);
        }
        return writeTargets(machineKey, section, sample, new ArrayList<>(unique));
    }

    /** Updates only a legacy numeric/mode/toggle field, leaving other and unknown NBT intact. */
    public synchronized boolean updateParameter(String machineKey, UUID editor, int section, int sample,
                                                int field, int value) {
        if (!canEdit(machineKey, editor) || !validParameterAddress(section, sample, field)) return false;
        Tag stored = configs.get(machineKey);
        if (!(stored instanceof CompoundTag current)) return false;
        CompoundTag config = current.copy();
        boolean changed;
        if (section <= 3 || section == 6 || section == 7) {
            String key = listKey(section);
            if (!validSampleList(config, key)) return false;
            ListTag samples = config.contains(key) ? ((ListTag) config.get(key)).copy() : new ListTag();
            while (samples.size() <= sample) samples.add(new CompoundTag());
            if (!(samples.get(sample) instanceof CompoundTag sampleTag)) return false;
            CompoundTag updated = sampleTag.copy();
            if (section >= 6) {
                var unit = com.primordial.neoforge.resource.NeoForgeResourceUnits.machineUnit(sample);
                if (unit == null || updated.contains("unit") && (!updated.contains("unit", Tag.TAG_STRING)
                        || !unit.toString().equals(updated.getString("unit")))) return false;
                if (!writeResourceParameter(updated, field, value)) return false;
                updated.putString("unit", unit.toString());
            } else if (!writeSampleParameter(updated, field, value)) return false;
            changed = !updated.equals(sampleTag);
            samples.set(sample, updated);
            config.put(key, samples);
        } else if (section == 4 || section == 5) {
            if (!validCompound(config, "en")) return false;
            CompoundTag energy = config.contains("en") ? config.getCompound("en").copy() : new CompoundTag();
            if (!writeEnergyParameter(energy, section == 4, field, value)) return false;
            changed = !energy.equals(config.getCompound("en"));
            config.put("en", energy);
        } else {
            String key = switch (section) {
                case 8 -> "iie";
                case 9 -> "ioe";
                case 10 -> "fie";
                case 11 -> "foe";
                default -> throw new IllegalStateException("Validated section became unsupported: " + section);
            };
            if (config.contains(key) && !config.contains(key, Tag.TAG_BYTE)) return false;
            changed = config.getBoolean(key) != (value != 0);
            config.putBoolean(key, value != 0);
        }
        if (!changed) return true;
        configs.put(machineKey, config);
        setDirty();
        return true;
    }

    public synchronized boolean updateItemFilter(String machineKey, UUID editor, int section, int sample,
                                                 ItemStack filter, HolderLookup.Provider registries) {
        if (!canEdit(machineKey, editor) || section < 0 || section > 1 || !validSample(sample)
                || filter == null || registries == null) return false;
        CompoundTag encoded = new CompoundTag();
        if (!filter.isEmpty()) {
            Tag saved = filter.copyWithCount(1).save(registries);
            if (!(saved instanceof CompoundTag compound)) return false;
            encoded = compound.copy();
        }
        return updateSampleFilter(machineKey, section, sample, encoded);
    }

    public synchronized boolean updateFluidFilter(String machineKey, UUID editor, int section, int sample,
                                                  FluidStack filter, HolderLookup.Provider registries) {
        if (!canEdit(machineKey, editor) || section < 2 || section > 3 || !validSample(sample)
                || filter == null || registries == null) return false;
        CompoundTag encoded = new CompoundTag();
        if (!filter.isEmpty()) {
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(filter.getFluid());
            if (fluidId == null) return false;
            encoded.putString("FluidName", fluidId.toString());
            encoded.putInt("Amount", filter.getAmount());
            CustomData customData = filter.get(DataComponents.CUSTOM_DATA);
            if (customData != null) encoded.put("Tag", customData.copyTag());
            encoded.put("NeoForge", filter.saveOptional(registries));
        }
        return updateSampleFilter(machineKey, section, sample, encoded);
    }

    public synchronized boolean updateChemicalFilter(String machineKey, UUID editor, int section, int sample,
                                                     ResourceLocation chemical) {
        if (!canEdit(machineKey, editor) || section < 2 || section > 3 || !validSample(sample)
                || chemical == null || !NeoForgePriceEditorChemicalBridge.validChemical(chemical)) return false;
        CompoundTag encoded = new CompoundTag();
        encoded.putString("gasName", chemical.toString());
        encoded.putLong("amount", 1L);
        return updateSampleFilter(machineKey, section, sample, encoded, "g");
    }

    private boolean updateSampleFilter(String machineKey, int section, int sample, CompoundTag filter) {
        return updateSampleFilter(machineKey, section, sample, filter, "f");
    }

    private boolean updateSampleFilter(String machineKey, int section, int sample, CompoundTag filter, String filterKey) {
        Tag stored = configs.get(machineKey);
        if (!(stored instanceof CompoundTag current)) return false;
        CompoundTag config = current.copy();
        String listKey = listKey(section);
        if (!validSampleList(config, listKey)) return false;
        ListTag samples = config.contains(listKey) ? ((ListTag) config.get(listKey)).copy() : new ListTag();
        while (samples.size() <= sample) samples.add(new CompoundTag());
        if (!(samples.get(sample) instanceof CompoundTag oldSample)) return false;
        if (oldSample.contains("f") && !oldSample.contains("f", Tag.TAG_COMPOUND)
                || oldSample.contains("g") && !oldSample.contains("g", Tag.TAG_COMPOUND)
                || oldSample.contains("e") && !oldSample.contains("e", Tag.TAG_BYTE)) return false;
        CompoundTag updated = oldSample.copy();
        if (filter.isEmpty()) updated.remove(filterKey);
        else updated.put(filterKey, filter.copy());
        updated.remove(filterKey.equals("g") ? "f" : "g");
        updated.putBoolean("e", !filter.isEmpty());
        if (updated.equals(oldSample)) return true;
        samples.set(sample, updated);
        config.put(listKey, samples);
        configs.put(machineKey, config);
        setDirty();
        return true;
    }

    private static boolean validSample(int sample) { return sample >= 0 && sample < 9; }

    private static boolean writeSampleParameter(CompoundTag sample, int field, int value) {
        return switch (field) {
            case 0 -> putMode(sample, "m", value);
            case 1 -> putInt(sample, "r", ConfigLimits.amount(value, 1));
            case 2 -> putInt(sample, "t", ConfigLimits.amount(value, 1));
            case 3 -> putInt(sample, "it", ConfigLimits.intervalTicks(value));
            case 4 -> putBoolean(sample, "e", value != 0);
            default -> false;
        };
    }

    private static boolean writeEnergyParameter(CompoundTag energy, boolean supply, int field, int value) {
        String mode = supply ? "sm" : "rm";
        String rate = supply ? "sr" : "rr";
        String amount = supply ? "sa" : "ra";
        String interval = supply ? "sit" : "rit";
        String enabled = supply ? "se" : "re";
        return switch (field) {
            case 0 -> putMode(energy, mode, value);
            case 1 -> putInt(energy, rate, ConfigLimits.amount(value, 1));
            case 2 -> putInt(energy, amount, ConfigLimits.amount(value, 1));
            case 3 -> putInt(energy, interval, ConfigLimits.intervalTicks(value));
            case 4 -> putBoolean(energy, enabled, value != 0);
            default -> false;
        };
    }

    private static boolean writeResourceParameter(CompoundTag sample, int field, int value) {
        return switch (field) {
            case 0 -> putMode(sample, "m", value);
            case 2 -> putInt(sample, "a", ConfigLimits.amount(value, 1));
            case 3 -> putInt(sample, "it", ConfigLimits.intervalTicks(value));
            case 4 -> putBoolean(sample, "e", value != 0);
            default -> false;
        };
    }

    private static boolean putMode(CompoundTag tag, String key, int value) {
        if (value < 0 || value > 1 || tag.contains(key) && !tag.contains(key, Tag.TAG_STRING)) return false;
        tag.putString(key, value == 0 ? "FIXED_RATE" : "KEEP_LEVEL");
        return true;
    }

    private static boolean putInt(CompoundTag tag, String key, int value) {
        if (tag.contains(key) && !tag.contains(key, Tag.TAG_INT)) return false;
        tag.putInt(key, value);
        return true;
    }

    private static boolean putBoolean(CompoundTag tag, String key, boolean value) {
        if (tag.contains(key) && !tag.contains(key, Tag.TAG_BYTE)) return false;
        tag.putBoolean(key, value);
        return true;
    }

    private static boolean validSampleList(CompoundTag config, String key) {
        if (!config.contains(key)) return true;
        if (!(config.get(key) instanceof ListTag list)) return false;
        return list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND;
    }

    private static boolean validParameterAddress(int section, int sample, int field) {
        if (field < 0 || field > 4) return false;
        if (section >= 0 && section <= 3) return sample >= 0 && sample < 9;
        if (section == 4 || section == 5) return sample == 0;
        if (section == 6 || section == 7) return sample >= 0 && sample < 6 && field != 1;
        return section >= 8 && section <= 11 && sample == 0 && field == 0;
    }

    private boolean writeTargets(String machineKey, int section, int sample, List<MachineTarget> targets) {
        Tag stored = configs.get(machineKey);
        if (!(stored instanceof CompoundTag current)) return false;
        CompoundTag config = current.copy();
        if (section == 4 || section == 5) {
            if (targets.size() > 1 || !validCompound(config, "en")) return false;
            CompoundTag energy = config.getCompound("en").copy();
            energy.put(section == 4 ? "supplyPort" : "recoverPort",
                    targets.isEmpty() ? MachineTarget.UNBOUND.save() : targets.get(0).save());
            config.put("en", energy);
        } else {
            String key = listKey(section);
            if (!validSampleList(config, key)) return false;
            ListTag samples = config.getList(key, Tag.TAG_COMPOUND).copy();
            while (samples.size() <= sample) samples.add(new CompoundTag());
            if (!(samples.get(sample) instanceof CompoundTag previous)) return false;
            CompoundTag sampleTag = previous.copy();
            ListTag encoded = new ListTag();
            for (MachineTarget value : targets) encoded.add(value.save());
            sampleTag.put("target", targets.isEmpty() ? MachineTarget.UNBOUND.save() : targets.get(0).save());
            sampleTag.put("targets", encoded);
            samples.set(sample, sampleTag);
            config.put(key, samples);
        }
        if (config.equals(current)) return true;
        configs.put(machineKey, config);
        setDirty();
        return true;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (blockedSnapshot != null) return blockedSnapshot.copy();
        tag.merge(extra.copy());
        CompoundTag root = new CompoundTag();
        configs.forEach((key, value) -> root.put(key, value.copy()));
        tag.put("configs", root);
        return tag;
    }

    public static NeoForgeMachineConfigSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NeoForgeMachineConfigSavedData data = new NeoForgeMachineConfigSavedData();
        if (!tag.contains("configs", Tag.TAG_COMPOUND)) {
            data.blockedSnapshot = tag.copy();
            return data;
        }
        for (String key : tag.getAllKeys()) {
            if (!key.equals("configs")) data.extra.put(key, tag.get(key).copy());
        }
        CompoundTag root = tag.getCompound("configs");
        for (String key : root.getAllKeys()) data.configs.put(key, root.get(key).copy());
        return data;
    }

    private static boolean validKey(String key) { return key != null && !key.isBlank() && key.length() <= 256; }

    private static boolean validAddress(int section, int sample) {
        return section >= 0 && section <= 7 && sample >= 0
                && sample < (section >= 6 ? 6 : section >= 4 ? 1 : 9);
    }

    private static String listKey(int section) {
        return switch (section) {
            case 0 -> "ii";
            case 1 -> "io";
            case 2 -> "fi";
            case 3 -> "fo";
            case 6 -> "ri";
            case 7 -> "ro";
            default -> throw new IllegalArgumentException("Section has no sample list: " + section);
        };
    }

    private static boolean validCompound(CompoundTag tag, String key) {
        return !tag.contains(key) || tag.contains(key, Tag.TAG_COMPOUND);
    }

    private static boolean validList(CompoundTag tag, String key) {
        return !tag.contains(key) || tag.contains(key, Tag.TAG_LIST);
    }

    private static void addBound(LinkedHashSet<MachineTarget> targets, MachineTarget target) {
        if (target.bound()) targets.add(target);
    }
}
