package com.primordial.neoforge.acceleration;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
@EventBusSubscriber(modid="primordial", bus=EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class AccelerationClientKeys {
    public static final KeyMapping MENU = new KeyMapping("key.primordial.accelerator_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.primordial");
    private AccelerationClientKeys() {}
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(MENU); }
}
