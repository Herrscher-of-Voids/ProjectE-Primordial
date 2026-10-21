package com.primordial.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeBalanceClientModEvents {
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.primordial.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.primordial");
    private static final ResourceLocation HUD_LAYER = ResourceLocation.fromNamespaceAndPath(
            PrimordialNeoForgeMod.MOD_ID, "balance_hud");

    private NeoForgeBalanceClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HUD);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, HUD_LAYER, NeoForgeBalanceClientModEvents::renderHud);
    }

    private static void renderHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientBalanceState.hudVisible() || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Component text = Component.translatable("hud.primordial.balance",
                NeoForgeEmcFormatter.format(ClientBalanceState.emc()));
        graphics.drawString(minecraft.font, text, 4, 18, 0xFF55FFFF, true);
    }
}
