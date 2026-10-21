package com.primordial.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** 机器配置键："dim|x|y|z" */
public final class MachineKey {
    private MachineKey() {}

    public static String of(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    public static ResourceKey<Level> parseDim(String key) {
        String dim = key.split("\\|")[0];
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
    }

    public static BlockPos parsePos(String key) {
        String[] parts = key.split("\\|");
        return new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}
