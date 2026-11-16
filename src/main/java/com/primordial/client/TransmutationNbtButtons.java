package com.primordial.client;

import com.primordial.network.C2SNbtSettingsPacket;
import com.primordial.network.S2CNbtSettingsPacket;
import com.primordial.network.PrimordialNetwork;
import java.lang.ref.WeakReference;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;

public final class TransmutationNbtButtons {
    private static WeakReference<View> current = new WeakReference<>(null);
    private TransmutationNbtButtons() { }
    public static void install(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof GUITransmutation screen)) return;
        var view = new View(screen);
        current = new WeakReference<>(view);
        event.addListener(view.settings);
        for (var button : view.options) event.addListener(button);
        PrimordialNetwork.CHANNEL.sendToServer(new C2SNbtSettingsPacket(screen.getMenu().containerId, -1));
    }
    public static boolean returnContents() {
        var view = current.get();
        return view == null || (view.flags & 8) == 0;
    }
    public static void accept(S2CNbtSettingsPacket packet) {
        var view = current.get();
        if (view == null || Minecraft.getInstance().screen != view.screen
                || view.screen.getMenu().containerId != packet.containerId()) return;
        view.flags = packet.flags();
        view.ready = true;
        view.refresh();
    }
    private static final class View {
        final GUITransmutation screen;
        final Button settings;
        final Button[] options = new Button[4];
        int flags = 7;
        boolean expanded, ready;
        View(GUITransmutation screen) {
            this.screen = screen;
            int x = screen.getGuiLeft() - 28, y = screen.getGuiTop() + 93;
            settings = Button.builder(Component.literal("设置"), button -> {
                expanded = !expanded; refresh();
            }).pos(x, y).size(26, 20).build();
            String[] tooltipKeys = {
                    "screen.primordial.nbt.retain",
                    "screen.primordial.nbt.damage",
                    "screen.primordial.nbt.enchant",
                    "screen.primordial.nbt.return_contents"
            };
            for (int i = 0; i < options.length; i++) {
                int bit = 1 << i;
                options[i] = Button.builder(Component.empty(), button -> {
                    ready = false;
                    refresh();
                    PrimordialNetwork.CHANNEL.sendToServer(new C2SNbtSettingsPacket(screen.getMenu().containerId, flags ^ bit));
                }).pos(x, y + 22 * (i + 1)).size(26, 20)
                        .tooltip(Tooltip.create(Component.translatable(tooltipKeys[i]))).build();
            }
            refresh();
        }
        void refresh() {
            String[] symbols = {"保", "损", "附", "返"};
            for (int i = 0; i < options.length; i++) {
                options[i].visible = expanded;
                options[i].active = ready;
                boolean enabled = (flags & (1 << i)) != 0;
                if (i == 3) enabled = !enabled;
                options[i].setMessage(Component.literal(symbols[i] + (enabled ? "+" : "-")));
            }
        }
    }
}
