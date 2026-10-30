package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
import com.primordial.neoforge.acceleration.C2SAccelerationActionPacket;
import com.primordial.neoforge.acceleration.AccelerationBindingActions;
import com.primordial.neoforge.acceleration.AccelerationSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "primordial", value = Dist.CLIENT)
public final class AccelerationClientInput {
    private AccelerationClientInput() { }
    public static InteractionHand hand() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        for (var hand : InteractionHand.values()) if (player.getItemInHand(hand).getItem() instanceof EmcAcceleratorItem) return hand;
        return null;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        while (AccelerationClientKeys.MENU.consumeClick()) {
            InteractionHand hand = hand();
            if (hand != null && Minecraft.getInstance().screen == null)
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(C2SAccelerationActionPacket.open(hand));
        }
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        InteractionHand hand = hand();
        if (mc.screen != null || mc.player == null || !mc.player.isShiftKeyDown() || hand == null || event.getScrollDeltaY() == 0) return;
        event.setCanceled(true);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new C2SAccelerationActionPacket(-1, C2SAccelerationActionPacket.Action.CYCLE,
                AccelerationBindingActions.SELECTION, AccelerationSettings.read(AccelerationData.get(mc.player.getItemInHand(hand))),
                event.getScrollDeltaY() > 0, hand));
    }
}
