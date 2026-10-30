/** Client page switching for ProjectE's native transmutation screen. */
package com.primordial.neoforge.client;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationMediaCatalog;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationTable;
import com.primordial.neoforge.transmutation.NeoForgeMediaChargingMenu;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationPayloads;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeTransmutationPages {
    private static WeakReference<View> current = new WeakReference<>(null);
    private static final int ITEMS = 0, FLUIDS = 1, CHEMICALS = 2, CRAFTING = 3;
    private static final int CATALOG_SIZE = 16;
    private static final int PAGE_STRIDE = 12;

    private NeoForgeTransmutationPages() {}

    @SubscribeEvent
    public static void initialized(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof GUITransmutation screen)) return;
        View old = current.get();
        int restored = old != null && old.screen == screen ? old.tab : ITEMS;
        List<AbstractWidget> nativeWidgets = new ArrayList<>();
        for (var listener : event.getListenersList())
            if (listener instanceof AbstractWidget widget) nativeWidgets.add(widget);
        NeoForgeTransmutationMediaClient.clear();
        View view = new View(screen, nativeWidgets, event);
        current = new WeakReference<>(view);
        view.show(restored);
    }

    @SubscribeEvent
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        View view = current.get();
        if (view != null && view.screen == event.getScreen()) view.update();
    }

    @SubscribeEvent
    public static void tabPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        View view = current.get();
        if (view == null || view.screen != event.getScreen()) return;
        for (Button tab : view.tabs) {
            if (!over(event.getMouseX(), event.getMouseY(), tab.getX(), tab.getY(), tab.getWidth(), tab.getHeight())) continue;
            view.tabReleaseButton = event.getButton();
            tab.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton());
            event.setCanceled(true);
            return;
        }
        if (!view.isMediaPage()) return;
        if (view.findSlot(event.getMouseX(), event.getMouseY(), NeoForgeTransmutationTable.MEDIA_FIRST_SLOT,
                NeoForgeTransmutationTable.MEDIA_SLOT_COUNT) >= 0) return;
        if (view.search.isMouseOver(event.getMouseX(), event.getMouseY())) {
            if (event.getButton() == 1) {
                view.search.setValue("");
                event.setCanceled(true);
            }
            return;
        }
        if (overWidget(event.getMouseX(), event.getMouseY(), view.previous)) {
            if (event.getButton() == 0 && view.previous.active) view.request(view.page - 1);
            event.setCanceled(true);
            return;
        }
        if (overWidget(event.getMouseX(), event.getMouseY(), view.next)) {
            if (event.getButton() == 0 && view.next.active) view.request(view.page + 1);
            event.setCanceled(true);
            return;
        }
        int slot = view.findSlot(event.getMouseX(), event.getMouseY(), 0, 27);
        if (slot < 0) return;
        if (slot >= 11) {
            view.selectOrBuy(slot - 11, event.getButton());
        } else if (event.getButton() == 0 && slot == 9) {
            NeoForgeTransmutationMediaClient.trade(view.screen.getMenu().containerId, false,
                    view.tab == CHEMICALS, "");
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void tabReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        View view = current.get();
        if (view != null && view.screen == event.getScreen() && view.tabReleaseButton == event.getButton()) {
            view.tabReleaseButton = -1;
            event.setCanceled(true);
        }
    }

    public static boolean secondary(Object screen) {
        View view = current.get();
        return view != null && view.screen == screen && view.tab != ITEMS;
    }

    public static boolean craftingPage(Object screen) {
        View view = current.get();
        return view != null && view.screen == screen && view.tab == CRAFTING;
    }

    public static void showCraftingPage(Object menu) {
        View view = current.get();
        if (view != null && view.screen.getMenu() == menu) view.show(CRAFTING);
    }

    public static void mediaReclaimed() {
        View view = current.get();
        if (view != null && view.isMediaPage()) {
            view.search.setValue("");
            view.request(0);
        }
    }

    public static void closed(Object screen) {
        View view = current.get();
        if (view != null && view.screen == screen) {
            NeoForgeTransmutationMediaClient.clear();
            // JEI retains this screen as its parent; preserve its page until the same screen is reinitialized.
        }
    }

    public static boolean hiddenSlot(Object screen, Slot slot) {
        if (!(screen instanceof GUITransmutation) || slot == null) return false;
        View view = current.get();
        int tab = view != null && view.screen == screen ? view.tab : ITEMS;
        int menuIndex = ((GUITransmutation) screen).getMenu().slots.indexOf(slot);
        if (menuIndex < 0) return false;
        if (tab == CRAFTING) return menuIndex < 27 || menuIndex >= NeoForgeTransmutationTable.MEDIA_FIRST_SLOT;
        if (tab == ITEMS) return menuIndex >= NeoForgeTransmutationTable.NATIVE_SLOT_COUNT;
        return menuIndex < 27 || NeoForgeTransmutationTable.isCraftingSlot(menuIndex)
                || NeoForgeTransmutationTable.isResultSlot(menuIndex);
    }

    public static boolean blockClick(Object screen, Slot slot, int index, ClickType type) {
        if (hiddenSlot(screen, slot)) return true;
        if (!secondary(screen)) return false;
        return type == ClickType.PICKUP_ALL || type == ClickType.QUICK_MOVE && index >= 27
                && index < NeoForgeTransmutationTable.NATIVE_SLOT_COUNT;
    }

    public static void background(Object screen, GuiGraphics graphics) {
        View view = current.get();
        if (view != null && view.screen == screen && view.tab == CRAFTING) view.craftingBackground(graphics);
    }

    public static void tooltip(Object screen, GuiGraphics graphics, int mouseX, int mouseY) {
        View view = current.get();
        if (view == null || view.screen != screen || !view.screen.getMenu().getCarried().isEmpty()) return;
        var font = Minecraft.getInstance().font;
        for (Slot slot : view.screen.getMenu().slots) {
            if (!hiddenSlot(screen, slot) && slot.hasItem()
                    && over(mouseX, mouseY, view.left + slot.x, view.top + slot.y, 16, 16)) {
                graphics.renderTooltip(font, slot.getItem(), mouseX, mouseY);
                return;
            }
        }
        if (!view.isMediaPage()) return;
        for (int i = 0; i < CATALOG_SIZE; i++) {
            String idText = view.resultIds[i];
            ResourceLocation id = ResourceLocation.tryParse(idText);
            if (id != null && view.isOverSlot(mouseX, mouseY, 11 + i)) {
                graphics.renderTooltip(font, view.name(id), mouseX, mouseY);
                return;
            }
        }
    }

    private static boolean over(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private static boolean overWidget(double x, double y, AbstractWidget widget) {
        return widget.visible && over(x, y, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    private static final class View {
        final GUITransmutation screen;
        final List<AbstractWidget> nativeWidgets;
        final List<Button> tabs = new ArrayList<>();
        final EditBox search;
        final Button previous, next;
        final int left, top;
        final String[] resultIds = new String[CATALOG_SIZE];
        int tab, page, tabReleaseButton = -1;
        String requestedQuery = "";
        String selectedId = "";
        boolean requestedChemical, confirmingForget;
        ItemStack forgetSample = ItemStack.EMPTY;

        View(GUITransmutation screen, List<AbstractWidget> nativeWidgets, ScreenEvent.Init.Post event) {
            this.screen = screen;
            this.nativeWidgets = nativeWidgets;
            left = screen.getGuiLeft();
            top = screen.getGuiTop();
            search = nativeWidgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                    .filter(widget -> widget.getX() == left + 83 && widget.getY() == top + 8)
                    .findFirst().orElseThrow(() -> new IllegalStateException("ProjectE search box not found"));
            previous = findNativeButton(left + 125, top + 100);
            next = findNativeButton(left + 193, top + 100);
            Arrays.fill(resultIds, "");
            String[] keys = {"tab_items", "tab_fluids", "tab_chemicals", "tab_crafting"};
            for (int i = 0; i < keys.length; i++) {
                final int target = i;
                Button button = Button.builder(text(keys[i]), ignored -> show(target))
                        .bounds(left - 28, top + 5 + i * 22, 26, 20)
                        .tooltip(Tooltip.create(text(keys[i]))).build();
                tabs.add(button);
                event.addListener(button);
            }
            search.setMaxLength(NeoForgeTransmutationMediaCatalog.MAX_QUERY);
            for (AbstractWidget widget : nativeWidgets) widget.setTooltip(null);
        }

        private Button findNativeButton(int x, int y) {
            return nativeWidgets.stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(widget -> widget.getX() == x && widget.getY() == y).findFirst()
                    .orElseThrow(() -> new IllegalStateException("ProjectE page button not found at " + x + "," + y));
        }

        boolean isMediaPage() { return tab == FLUIDS || tab == CHEMICALS; }

        void show(int target) {
            tab = target;
            ((NeoForgeMediaChargingMenu) screen.getMenu()).primordial$setMediaPage(isMediaPage(), tab == CHEMICALS);
            PacketDistributor.sendToServer(new NeoForgeTransmutationPayloads.PageState(
                    screen.getMenu().containerId, isMediaPage(), tab == CHEMICALS));
            screen.setFocused(null);
            selectedId = "";
            confirmingForget = false;
            if (isMediaPage()) {
                configureMediaSearch();
                Arrays.fill(resultIds, "");
                request(0);
            } else {
                configureItemSearch();
                previous.active = true;
                next.active = true;
            }
            update();
        }

        private void configureMediaSearch() {
            search.setResponder(ignored -> request(0));
        }

        private void configureItemSearch() {
            search.setResponder(value -> screen.getMenu().transmutationInventory.updateFilter(value));
            screen.getMenu().transmutationInventory.updateFilter(search.getValue());
        }

        void request(int targetPage) {
            if (targetPage < 0 || targetPage > NeoForgeTransmutationMediaCatalog.MAX_PAGE) return;
            page = targetPage;
            requestedQuery = search.getValue().trim();
            if (requestedQuery.length() > NeoForgeTransmutationMediaCatalog.MAX_QUERY)
                requestedQuery = requestedQuery.substring(0, NeoForgeTransmutationMediaCatalog.MAX_QUERY);
            requestedChemical = tab == CHEMICALS;
            selectedId = "";
            confirmingForget = false;
            Arrays.fill(resultIds, "");
            previous.active = false;
            next.active = false;
            NeoForgeTransmutationMediaClient.requestCatalog(screen.getMenu().containerId,
                    requestedChemical, requestedQuery, page);
        }

        com.primordial.neoforge.transmutation.NeoForgeTransmutationPayloads.Catalog catalog() {
            return NeoForgeTransmutationMediaClient.currentCatalog(screen.getMenu().containerId,
                    tab == CHEMICALS, search.getValue().trim(), page);
        }

        void update() {
            boolean media = isMediaPage();
            if (media) {
                ItemStack sample = screen.getMenu().getSlot(NeoForgeTransmutationTable.FORGET_SLOT).getItem();
                if (!ItemStack.matches(sample, forgetSample)) {
                    forgetSample = sample.copy();
                    if (!sample.isEmpty()) request(0);
                }
            }
            for (AbstractWidget widget : nativeWidgets) {
                widget.visible = tab == ITEMS || media && (widget == search || widget == previous || widget == next);
                if (!widget.visible) widget.setFocused(false);
            }
            for (int i = 0; i < tabs.size(); i++) tabs.get(i).active = i != tab;
            var catalog = media ? catalog() : null;
            if (media) {
                previous.active = catalog != null && page > 0;
                next.active = catalog != null && (long) (page + 1) * PAGE_STRIDE < catalog.total();
            }
            if (catalog == null) return;
            for (int i = 0; i < CATALOG_SIZE; i++)
                resultIds[i] = i < catalog.ids().size() ? catalog.ids().get(i) : "";
            if (!selectedId.isEmpty() && !catalog.ids().contains(selectedId)) {
                selectedId = "";
                confirmingForget = false;
            }
        }

        void selectOrBuy(int index, int button) {
            if (button != 0 && button != 1) return;
            ResourceLocation id = ResourceLocation.tryParse(resultIds[index]);
            if (id == null) return;
            if (button == 1) {
                selectedId = id.toString();
                confirmingForget = false;
            } else {
                selectedId = id.toString();
                NeoForgeTransmutationMediaClient.trade(screen.getMenu().containerId,
                        true, tab == CHEMICALS, selectedId);
            }
        }

        void forgetSelected() {
            if (selectedId.isEmpty()) return;
            if (!confirmingForget) {
                confirmingForget = true;
                return;
            }
            NeoForgeTransmutationMediaClient.forget(screen.getMenu().containerId, tab == CHEMICALS, selectedId);
            selectedId = "";
            confirmingForget = false;
        }

        int findSlot(double x, double y, int start, int count) {
            for (int i = 0; i < count; i++) if (isOverSlot(x, y, start + i)) return start + i;
            return -1;
        }

        boolean isOverSlot(double x, double y, int index) {
            if (index < 0 || index >= screen.getMenu().slots.size()) return false;
            Slot slot = screen.getMenu().slots.get(index);
            return over(x, y, left + slot.x, top + slot.y, 16, 16);
        }

        Component name(ResourceLocation id) {
            if (tab == CHEMICALS) return NeoForgePriceEditorChemicalBridge.chemicalName(id);
            return BuiltInRegistries.FLUID.containsKey(id)
                    ? new FluidStack(BuiltInRegistries.FLUID.get(id), 1000).getHoverName() : Component.empty();
        }

        void craftingBackground(GuiGraphics graphics) {
            int width = screen.getXSize(), height = screen.getYSize();
            graphics.fill(left, top, left + width, top + height, 0xFF282B2E);
            graphics.fill(left + 2, top + 2, left + width - 2, top + height - 2, 0xFFBFC1C1);
            for (Slot slot : screen.getMenu().slots) if (!hiddenSlot(screen, slot)) {
                int x = left + slot.x, y = top + slot.y;
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFEEEEEE);
                graphics.fill(x, y, x + 16, y + 16, 0xFF888888);
            }
            graphics.drawString(Minecraft.getInstance().font, text("crafting"), left + 32, top + 14, 0x303030, false);
            graphics.fill(left + 107, top + 50, left + 119, top + 60, 0xFF777777);
            graphics.drawString(Minecraft.getInstance().font, Component.literal("→"), left + 108, top + 51, 0xFFFFFF, false);
            graphics.drawWordWrap(Minecraft.getInstance().font, text("crafting_help"), left + 10, top + 91,
                    width - 20, 0xFF333333);
        }

        void renderMedia(GuiGraphics graphics, int mouseX, int mouseY) {
            if (!isMediaPage()) return;
            ResourceLocation background = ResourceLocation.fromNamespaceAndPath("projecte", "textures/gui/transmute.png");
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 199);
            for (int i = 0; i < 8; i++) drawNativeSlot(graphics, background, i);
            for (int i = 8; i < 27; i++) {
                if (i == 9 || i == 10) continue;
                Slot slot = screen.getMenu().slots.get(i);
                graphics.blit(background, left + slot.x - 1, top + slot.y - 1,
                        slot.x - 1, slot.y - 1, 18, 18);
            }
            graphics.pose().popPose();
            for (int i = 0; i < CATALOG_SIZE; i++) {
                ResourceLocation id = ResourceLocation.tryParse(resultIds[i]);
                if (id == null) continue;
                Slot slot = screen.getMenu().slots.get(11 + i);
                int x = left + slot.x, y = top + slot.y;
                if (tab == CHEMICALS) {
                    NeoForgeFluidSampleRenderer.renderChemical(graphics,
                            NeoForgePriceEditorChemicalBridge.chemicalTexture(id),
                            0xFF000000 | NeoForgePriceEditorChemicalBridge.chemicalTint(id), x, y);
                } else if (BuiltInRegistries.FLUID.containsKey(id)) {
                    NeoForgeFluidSampleRenderer.renderFluid(graphics,
                            new FluidStack(BuiltInRegistries.FLUID.get(id), 1000), x, y);
                } else continue;
            }
        }

        private void drawNativeSlot(GuiGraphics graphics, ResourceLocation texture, int index) {
            Slot slot = screen.getMenu().slots.get(index);
            int x = slot.x - 1, y = slot.y - 1;
            graphics.blit(texture, left + x, top + y, x, y, 18, 1);
            graphics.blit(texture, left + x, top + y + 17, x, y + 17, 18, 1);
            graphics.blit(texture, left + x, top + y + 1, x, y + 1, 1, 16);
            graphics.blit(texture, left + x + 17, top + y + 1, x + 17, y + 1, 1, 16);
        }
    }

    @SubscribeEvent
    public static void rendered(ScreenEvent.Render.Post event) {
        View view = current.get();
        if (view != null && view.screen == event.getScreen())
            view.renderMedia(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    private static Component text(String key) {
        return Component.translatable("screen.primordial.transmutation." + key);
    }
}
