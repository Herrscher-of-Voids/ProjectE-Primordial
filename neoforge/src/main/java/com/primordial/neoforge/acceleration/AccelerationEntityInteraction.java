package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "primordial")
public final class AccelerationEntityInteraction {
    private static final Map<ServerPlayer, Integer> LAST_INTERACTION = new WeakHashMap<>();
    private AccelerationEntityInteraction() { }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void interact(PlayerInteractEvent.EntityInteract event) {
        route(event, event.getTarget());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void specific(PlayerInteractEvent.EntityInteractSpecific event) {
        route(event, event.getTarget());
    }
    private static void route(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
        var tool = event.getItemStack();
        if (!(tool.getItem() instanceof EmcAcceleratorItem) || AccelerationData.get(tool) == null
                || AccelerationSettings.kind(AccelerationData.get(tool)) != AccelerationSettings.Kind.ENTITY) return;
        if (event instanceof PlayerInteractEvent.EntityInteract interact) {
            interact.setCanceled(true); interact.setCancellationResult(InteractionResult.SUCCESS);
        } else if (event instanceof PlayerInteractEvent.EntityInteractSpecific interact) {
            interact.setCanceled(true); interact.setCancellationResult(InteractionResult.SUCCESS);
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int now = player.server.getTickCount();
        if (LAST_INTERACTION.getOrDefault(player, Integer.MIN_VALUE) == now) return;
        LAST_INTERACTION.put(player, now);

            try (var edit = AccelerationData.edit(tool)) {
            AccelerationBindingActions.entity(tool, player, target, event.getHand());
        }

    }
}
