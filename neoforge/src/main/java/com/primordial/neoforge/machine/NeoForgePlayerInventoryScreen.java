package com.primordial.neoforge.machine;

import com.primordial.screen.PrimordialGuiStyle;
import net.neoforged.neoforge.network.PacketDistributor;

import com.primordial.data.SampleMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;

@yalter.mousetweaks.api.MouseTweaksIgnore
public class NeoForgePlayerInventoryScreen extends AbstractContainerScreen<NeoForgePlayerInventoryMenu> {
    private final Set<Integer> selectedSlots = new LinkedHashSet<>();
    private EditBox amountBox;
    private EditBox intervalBox;
    private Button setButton;
    private Button pauseButton;
    private Button resumeButton;
    private Button pauseAllButton;
    private Button unbindButton;
    private SampleMode mode = SampleMode.KEEP_LEVEL;
    private final Button[] resourceButtons = new Button[2];
    private final String[] resourceTooltipState = new String[2];

    public NeoForgePlayerInventoryScreen(NeoForgePlayerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 246; imageHeight = 244;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.charge_held"), b ->
                PacketDistributor.sendToServer(new NeoForgePlayerInventoryPayloads.ChargeHeld(menu.containerId)))
                .bounds(leftPos + 8, topPos + 116, 230, 18).build());
        addRenderableWidget(Button.builder(modeText(), b -> {
            mode = mode == SampleMode.KEEP_LEVEL ? SampleMode.FIXED_RATE : SampleMode.KEEP_LEVEL;
            b.setMessage(modeText());
            updateModeWidgets();
        }).bounds(leftPos + 8, topPos + 151, 86, 18).build());
        amountBox = new EditBox(font, leftPos + 100, topPos + 152, 62, 16, Component.translatable("screen.primordial.amount"));
        amountBox.setMaxLength(10); amountBox.setFilter(s -> s.matches("\\d*")); addRenderableWidget(amountBox);
        amountBox.setValue("1");
        intervalBox = new EditBox(font, leftPos + 170, topPos + 152, 68, 16, Component.translatable("screen.primordial.interval_ticks"));
        intervalBox.setMaxLength(5); intervalBox.setFilter(s -> s.matches("\\d*")); addRenderableWidget(intervalBox);
        intervalBox.setValue("20");
        setButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.set"), b -> setRules())
                .bounds(leftPos + 8, topPos + 174, 74, 18).build());
        pauseButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.pause_selected"), b -> setSelectedEnabled(false))
                .bounds(leftPos + 86, topPos + 174, 74, 18).build());
        resumeButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.resume_selected"), b -> setSelectedEnabled(true))
                .bounds(leftPos + 164, topPos + 174, 74, 18).build());
        pauseAllButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.pause_all_inventory"), b ->
                PacketDistributor.sendToServer(new NeoForgePlayerInventoryPayloads.SetEnabled(menu.containerId,
                        IntStream.range(0, NeoForgePlayerInventoryMenu.SLOT_COUNT).toArray(), false)))
                .bounds(leftPos + 8, topPos + 198, 140, 18).build());
        unbindButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.unbind_selected"), b -> {
            int[] slots = selectedSlots.stream().mapToInt(NeoForgePlayerInventoryMenu::inventoryIndex).toArray();
            if (slots.length > 0) PacketDistributor.sendToServer(
                    new NeoForgePlayerInventoryPayloads.Clear(menu.containerId, slots));
        }).bounds(leftPos + 152, topPos + 198, 86, 18).build());
        unbindButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_unbind_hint")));
        setButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_set_hint")));
        pauseButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_pause_hint")));
        resumeButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_resume_hint")));
        updateModeWidgets();
        for (int index = 0; index < resourceButtons.length; index++) {
            final int resource = index;
            resourceButtons[index] = addRenderableWidget(Button.builder(resourceText(index), b -> {
                if (menu.resourceReady(resource) && menu.resourceState(resource) != NeoForgePlayerInventoryMenu.UNCONFIGURED
                        && minecraft != null && minecraft.gameMode != null)
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 100 + resource);
            }).bounds(leftPos + 8 + index * 116, topPos + 222, 114, 18).build());
            resourceTooltipState[index] = null;
            updateResourceButton(index);
        }
    }

    private void updateModeWidgets() {
        boolean fixed = mode == SampleMode.FIXED_RATE;
        amountBox.visible = fixed; amountBox.active = fixed;
        intervalBox.visible = fixed; intervalBox.active = fixed;
    }

    private Component resourceText(int index) {
        String state = !menu.resourceReady(index) ? "screen.primordial.player_resource_loading" : switch (menu.resourceState(index)) {
            case NeoForgePlayerInventoryMenu.ENABLED -> "screen.primordial.on";
            case NeoForgePlayerInventoryMenu.PAUSED -> "screen.primordial.off";
            default -> "screen.primordial.resource_unavailable";
        };
        return Component.translatable("screen.primordial.player_resource_toggle",
                Component.translatable(index == 0 ? "screen.primordial.iron_mana" : "screen.primordial.soul_energy"),
                Component.translatable(state));
    }

    private void updateResourceButton(int index) {
        Button button = resourceButtons[index];
        if (button == null) return;
        boolean ready = menu.resourceReady(index);
        int state = menu.resourceState(index);
        button.setMessage(resourceText(index));
        button.active = ready && state != NeoForgePlayerInventoryMenu.UNCONFIGURED;
        String key = ready + ":" + state + ":" + menu.resourceStored(index) + ":" + menu.resourceCapacity(index);
        if (key.equals(resourceTooltipState[index])) return;
        resourceTooltipState[index] = key;
        Component details = !ready ? Component.translatable("screen.primordial.player_resource_loading_hint")
                : state == NeoForgePlayerInventoryMenu.UNCONFIGURED
                        ? Component.translatable("screen.primordial.player_resource_unavailable_hint")
                        : Component.translatable("screen.primordial.player_resource_capacity",
                                menu.resourceStored(index), menu.resourceCapacity(index));
        button.setTooltip(Tooltip.create(Component.empty().append(details).append("\n")
                .append(Component.translatable("screen.primordial.player_resource_hint"))));
    }

    private void setRules() {
        if (selectedSlots.isEmpty()) return;
        int amount = 0, interval = 1;
        if (mode == SampleMode.FIXED_RATE) {
            try { amount = Integer.parseInt(amountBox.getValue()); interval = Integer.parseInt(intervalBox.getValue()); }
            catch (NumberFormatException ignored) { return; }
            if (amount <= 0 || interval <= 0) return;
        } else amount = 1;
        for (int menuSlot : selectedSlots) {
            ItemStack stack = menu.getSlot(menuSlot).getItem();
            if (stack.isEmpty()) continue;
            PacketDistributor.sendToServer(new NeoForgePlayerInventoryPayloads.Edit(
                    menu.containerId, NeoForgePlayerInventoryMenu.inventoryIndex(menuSlot), amount, interval, mode,
                    true, stack.copyWithCount(1)));
        }
    }

    /** 向服务端批量设置所选规则的 enabled 状态；空选择不发送网络包。 */
    private void setSelectedEnabled(boolean enabled) {
        if (selectedSlots.isEmpty()) return;
        int[] slots = selectedSlots.stream().mapToInt(NeoForgePlayerInventoryMenu::inventoryIndex).toArray();
        PacketDistributor.sendToServer(new NeoForgePlayerInventoryPayloads.SetEnabled(menu.containerId, slots, enabled));
    }

    private Component modeText() {
        return Component.translatable(mode == SampleMode.KEEP_LEVEL
                ? "screen.primordial.mode_keep" : "screen.primordial.mode_fixed");
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < NeoForgePlayerInventoryMenu.SLOT_COUNT; i++) {
            var slot = menu.slots.get(i);
            if (mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                    && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16) {
                if (button == 0) {
                    if (!selectedSlots.add(i)) selectedSlots.remove(i);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        gg.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PrimordialGuiStyle.SCREEN_BACKGROUND);
        gg.renderOutline(leftPos, topPos, imageWidth, imageHeight, PrimordialGuiStyle.PANEL_BORDER);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i); int x = leftPos + slot.x, y = topPos + slot.y;
            int color = selectedSlots.contains(i) ? PrimordialGuiStyle.SELECTION : PrimordialGuiStyle.SLOT_BORDER;
            gg.fill(x - 1, y - 1, x + 17, y + 17, color);
            gg.fill(x, y, x + 16, y + 16, PrimordialGuiStyle.SLOT_BACKGROUND);
        }
    }

    @Override protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
        gg.drawString(font, Component.translatable("screen.primordial.player_inventory"), 8, 6, PrimordialGuiStyle.PRIMARY_TEXT, false);
        gg.drawString(font, Component.translatable("screen.primordial.selected_slots", selectedSlots.size()), 8, 103, PrimordialGuiStyle.SECONDARY_TEXT, false);
        int enabled = 0, paused = 0;
        boolean canPause = false, canResume = false;
        for (int i = 0; i < NeoForgePlayerInventoryMenu.SLOT_COUNT; i++) {
            int state = menu.ruleState(i);
            if (state == NeoForgePlayerInventoryMenu.ENABLED) enabled++;
            if (state == NeoForgePlayerInventoryMenu.PAUSED) paused++;
            if (selectedSlots.contains(i)) {
                canPause |= state == NeoForgePlayerInventoryMenu.ENABLED;
                canResume |= state == NeoForgePlayerInventoryMenu.PAUSED;
            }
            var slot = menu.slots.get(i);
            int color = state == NeoForgePlayerInventoryMenu.ENABLED ? 0xFF66DD88
                    : state == NeoForgePlayerInventoryMenu.PAUSED ? 0xFFFFBB55 : PrimordialGuiStyle.SLOT_BORDER;
            gg.fill(slot.x, slot.y + 16, slot.x + 16, slot.y + 17, color);
        }
        gg.drawString(font, Component.translatable("screen.primordial.inventory_rule_counts", enabled, paused),
                111, 103, PrimordialGuiStyle.SECONDARY_TEXT, false);
        if (mode == SampleMode.FIXED_RATE) {
            gg.drawString(font, Component.translatable("screen.primordial.amount"), 100, 140, PrimordialGuiStyle.SECONDARY_TEXT, false);
            gg.drawString(font, Component.translatable("screen.primordial.interval_ticks"), 170, 140, PrimordialGuiStyle.SECONDARY_TEXT, false);
        } else {
            gg.drawString(font, Component.translatable("screen.primordial.keep_unlimited"), 8, 140, PrimordialGuiStyle.SECONDARY_TEXT, false);
        }
        setButton.active = !selectedSlots.isEmpty();
        pauseButton.active = canPause;
        resumeButton.active = canResume;
        unbindButton.active = canPause || canResume;
        pauseAllButton.active = enabled > 0;
    }

    /** 槽位标记与提示使用服务端同步状态；格子为空时同样显示已配置规则的状态。 */
    @Override public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        for (int index = 0; index < resourceButtons.length; index++) updateResourceButton(index);
        super.render(gg, mouseX, mouseY, partialTick);
        renderTooltip(gg, mouseX, mouseY);
        if (hoveredSlot != null) {
            String key = switch (menu.ruleState(hoveredSlot.index)) {
                case NeoForgePlayerInventoryMenu.ENABLED -> "screen.primordial.inventory_rule_enabled";
                case NeoForgePlayerInventoryMenu.PAUSED -> "screen.primordial.inventory_rule_paused";
                default -> "screen.primordial.inventory_rule_none";
            };
            if (hoveredSlot.getItem().isEmpty()) {
                gg.renderTooltip(font, Component.translatable(key), mouseX, mouseY);
            } else {
                gg.drawString(font, Component.translatable(key), leftPos + 8, topPos + imageHeight + 3, 0xFFFFFFFF, false);
            }
        }
    }
}
