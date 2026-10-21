package com.primordial.neoforge;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.config.NeoForgeClientConfig;
import com.primordial.neoforge.network.NeoForgePayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(PrimordialNeoForgeMod.MOD_ID)
public final class PrimordialNeoForgeMod {
    public static final String MOD_ID = "primordial";
    private static final Logger LOGGER = LogUtils.getLogger();

    public PrimordialNeoForgeMod(IEventBus modEventBus, ModContainer modContainer) {
        NeoForgeModRegistries.register(modEventBus);
        NeoForgeClientConfig.register(modContainer);
        NeoForgePayloads.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);
        LOGGER.info("[Primordial] NeoForge mod loading");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("[Primordial] NeoForge common setup complete"));
    }
}
