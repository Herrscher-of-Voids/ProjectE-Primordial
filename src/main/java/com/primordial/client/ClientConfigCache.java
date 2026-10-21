package com.primordial.client;

import net.minecraft.nbt.CompoundTag;

import com.primordial.data.MachineConfig;

/** 客户端最近收到的配置同步（供 Screen/Menu 读取渲染） */
public final class ClientConfigCache {
    private static String lastKey = "";
    private static MachineConfig config = new MachineConfig();

    private ClientConfigCache() {}

    public static void accept(String machineKey, CompoundTag tag) {
        lastKey = machineKey;
        config = MachineConfig.load(tag);
    }

    public static boolean matches(String machineKey) {
        return lastKey.equals(machineKey);
    }

    public static boolean matchesLastKey() {
        return !lastKey.isEmpty();
    }

    public static MachineConfig get() {
        return config;
    }

    public static void clear() {
        lastKey = "";
        config = new MachineConfig();
    }
}
