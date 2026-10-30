package com.primordial.neoforge.resource;

import net.neoforged.fml.ModList;

/** Adapters use only the target mod's public API and remain absent when that mod is not installed. */
final class NeoForgeNativeResourceAdapters {
    private NeoForgeNativeResourceAdapters() {}
    static void register() {
        if (ModList.get().isLoaded("botania")) NeoForgeBotaniaResourceAdapter.register();
        if (ModList.get().isLoaded("ars_nouveau")) NeoForgeArsSourceResourceAdapter.register();
        NeoForgePneumaticResourceAdapter.register();
        NeoForgeNaturesAuraResourceAdapter.register();
        if (ModList.get().isLoaded("create")) try {
            Class.forName("com.primordial.neoforge.resource.create.NeoForgeCreateResourceAdapter")
                    .getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            com.mojang.logging.LogUtils.getLogger().warn("[Primordial] Create kinetics adapter is unavailable", unavailable);
        }
    }
}
