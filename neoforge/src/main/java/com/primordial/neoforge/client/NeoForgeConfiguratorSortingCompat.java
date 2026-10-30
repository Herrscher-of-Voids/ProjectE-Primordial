/** Keep known sorting controls out of management/ghost-slot screens, matching the Forge UI. */
package com.primordial.neoforge.client;

import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.acceleration.AccelerationScreen;
import com.primordial.neoforge.charm.NeoForgeBlackholeCharmScreen;
import com.primordial.neoforge.machine.NeoForgeMachineConfiguratorScreen;
import com.primordial.neoforge.machine.NeoForgePlayerInventoryScreen;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeConfiguratorSortingCompat {
    private NeoForgeConfiguratorSortingCompat() {}
    private static boolean protectedScreen(Screen screen) {
        return screen instanceof NeoForgeMachineConfiguratorScreen || screen instanceof NeoForgePlayerInventoryScreen
                || screen instanceof NeoForgePriceEditorScreen || screen instanceof NeoForgeBlackholeCharmScreen
                || screen instanceof AccelerationScreen;
    }
    private static boolean sortingWidget(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = current.getName();
            if (name.startsWith("invtweaks.gui.InvTweaksButton")
                    || name.equals("org.violetmoon.quark.content.management.client.screen.widgets.MiniInventoryButton")
                    || name.equals("vazkii.quark.content.management.client.screen.widgets.MiniInventoryButton")
                    || name.equals("net.blay09.mods.craftingtweaks.client.GuiTweakButton")) return true;
        }
        return false;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void initialized(ScreenEvent.Init.Post event) {
        if (!protectedScreen(event.getScreen())) return;
        for (var child : List.copyOf(event.getListenersList()))
            if (sortingWidget(child.getClass())) event.removeListener(child);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!protectedScreen(event.getScreen())) return;
        for (var child : event.getScreen().children())
            if (child instanceof AbstractWidget widget && sortingWidget(child.getClass())) {
                widget.visible = false;
                widget.active = false;
            }
    }
}
