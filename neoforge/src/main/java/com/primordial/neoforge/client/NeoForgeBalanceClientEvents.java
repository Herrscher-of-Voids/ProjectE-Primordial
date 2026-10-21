package com.primordial.neoforge.client;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeBalanceClientEvents {
    private NeoForgeBalanceClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (NeoForgeBalanceClientModEvents.TOGGLE_HUD.consumeClick()) {
            ClientBalanceState.toggleHud();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBalanceState.clear();
    }
}
