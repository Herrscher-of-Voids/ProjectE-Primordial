package com.primordial.neoforge.client;

import java.lang.ref.WeakReference;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationNbtPayloads;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Same position and independent three-button settings panel as Forge 1.20.1. */
@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeTransmutationNbtButtons {
    private static WeakReference<View> current = new WeakReference<>(null);

    private NeoForgeTransmutationNbtButtons() {}

    // Install after media pages have collected ProjectE's native search/page widgets.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void initialized(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof GUITransmutation screen)) return;
        View view = new View(screen);
        current = new WeakReference<>(view);
        event.addListener(view.settings);
        for (Button button : view.options) event.addListener(button);
        PacketDistributor.sendToServer(new NeoForgeTransmutationNbtPayloads.Settings(screen.getMenu().containerId, -1));
    }

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        View view = current.get();
        if (view != null && view.screen == event.getScreen()) view.refresh();
    }

    public static void accept(NeoForgeTransmutationNbtPayloads.State state) {
        View view = current.get();
        if (view == null || Minecraft.getInstance().screen != view.screen || view.screen.getMenu().containerId != state.menuId()) return;
        view.flags = state.flags();
        view.ready = true;
        view.refresh();
    }

    private static final class View {
        final GUITransmutation screen;
        final Button settings;
        final Button[] options = new Button[3];
        int flags = 7;
        boolean expanded, ready;

        View(GUITransmutation screen) {
            this.screen = screen;
            int x = screen.getGuiLeft() - 28, y = screen.getGuiTop() + 93;
            settings = Button.builder(Component.translatable("screen.primordial.nbt.settings"), ignored -> {
                expanded = !expanded;
                refresh();
            }).bounds(x, y, 26, 20).build();
            String[] keys = {"retain", "damage", "enchant"};
            for (int index = 0; index < options.length; index++) {
                int bit = 1 << index;
                options[index] = Button.builder(Component.empty(), ignored -> {
                    ready = false;
                    refresh();
                    PacketDistributor.sendToServer(new NeoForgeTransmutationNbtPayloads.Settings(screen.getMenu().containerId, flags ^ bit));
                }).bounds(x, y + 22 * (index + 1), 26, 20)
                        .tooltip(Tooltip.create(Component.translatable("screen.primordial.nbt." + keys[index]))).build();
            }
            refresh();
        }

        void refresh() {
            settings.visible = true;
            String[] keys = {"retain_short", "damage_short", "enchant_short"};
            for (int index = 0; index < options.length; index++) {
                options[index].visible = expanded;
                options[index].active = ready;
                options[index].setMessage(Component.translatable("screen.primordial.nbt." + keys[index])
                        .append((flags & (1 << index)) != 0 ? "+" : "-"));
            }
        }
    }
}
