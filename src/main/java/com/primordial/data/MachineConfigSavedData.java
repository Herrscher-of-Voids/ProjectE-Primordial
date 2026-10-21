package com.primordial.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** 机器配置表：machineKey -> MachineConfig。存 overworld。 */
public class MachineConfigSavedData extends SavedData {
    public static final String DATA_NAME = "primordial_machine_configs";

    private final Map<String, MachineConfig> configs = new HashMap<>();

    public static MachineConfigSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(MachineConfigSavedData::load, MachineConfigSavedData::new, DATA_NAME);
    }

    /** 获取配置；不存在则以 owner 创建默认配置 */
    public MachineConfig getOrCreate(String machineKey, java.util.UUID owner) {
        MachineConfig cfg = configs.get(machineKey);
        if (cfg == null) {
            cfg = new MachineConfig();
            cfg.owner = owner;
            configs.put(machineKey, cfg);
            setDirty();
        }
        return cfg;
    }

    public MachineConfig get(String machineKey) {
        return configs.get(machineKey);
    }

    public void remove(String machineKey) {
        if (configs.remove(machineKey) != null) setDirty();
    }

    public Collection<MachineConfig> all() {
        return configs.values();
    }

    public Collection<Map.Entry<String, MachineConfig>> allEntries() {
        return configs.entrySet();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<String, MachineConfig> e : configs.entrySet()) {
            root.put(e.getKey(), e.getValue().save());
        }
        tag.put("configs", root);
        return tag;
    }

    public static MachineConfigSavedData load(CompoundTag tag) {
        MachineConfigSavedData data = new MachineConfigSavedData();
        CompoundTag root = tag.getCompound("configs");
        for (String key : root.getAllKeys()) {
            data.configs.put(key, MachineConfig.load(root.getCompound(key)));
        }
        return data;
    }
}
