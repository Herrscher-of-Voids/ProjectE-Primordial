package com.primordial.neoforge.events;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.machine.NeoForgePlayerInventoryTask;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgePlayerInventoryEvents {
    private NeoForgePlayerInventoryEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try { NeoForgePlayerInventoryTask.run(player); }
        catch (RuntimeException | LinkageError failure) {
            LogUtils.getLogger().debug("[Primordial] Player inventory replenishment failed for {}",
                    player.getGameProfile().getName(), failure);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        NeoForgePlayerInventoryTask.forget(event.getEntity().getUUID());
    }
}
