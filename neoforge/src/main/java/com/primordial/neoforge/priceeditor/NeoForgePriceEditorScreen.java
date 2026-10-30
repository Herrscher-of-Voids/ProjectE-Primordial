/**
 * 【文件职责】绘制定价器幽灵槽、精确输入、五种模式和服务器确认的保存反馈。
 * 【变更记录 #1】2026-10-23 16:56:25（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：物品/流体/统一化学品预览、JEI 通用入口及有界保存请求。
 * - 修改：仅同步 editable 和 available 控制编辑；请求前后不改客户端价表，失败保留未保存输入。
 * - 用途：与服务端菜单和价格服务形成闭环；恢复、取消价格与方向模式各自显示实际权威状态。
 * 【变更记录 #2】2026-10-23 17:11:28（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：空样本的首次权威回包被误显示为价格服务不可用的提示。
 * - 新增：空目标优先显示选样引导，已有目标仍按实际 available 状态显示失败。
 * - 修改：只调整反馈选择顺序，不更改权限、输入或包协议。
 * - 用途：初次打开定价器时明确提示放入幽灵样本，避免把未选样误报为后端故障。
 */
package com.primordial.neoforge.priceeditor;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorPayloads.State;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorPayloads.Status;
import com.primordial.service.EmcAmount;
import com.primordial.value.PriceMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;

@yalter.mousetweaks.api.MouseTweaksIgnore
public final class NeoForgePriceEditorScreen extends AbstractContainerScreen<NeoForgePriceEditorMenu> {
    private static final int BACKGROUND = 0xF0080C12, BORDER = 0xFF3A5A7A, SLOT_BORDER = 0xFF5A6A7A;
    private static final int PRIMARY = 0xFF7FDFFF, SECONDARY = 0xFFAAAAAA;
    private EditBox value;
    private Button save;
    private final Map<PriceMode, Button> modeButtons = new EnumMap<>(PriceMode.class);
    private long lastVersion = -1;
    private State lastState;
    private Component localStatus;
    private boolean inputEdited, pending;

    public NeoForgePriceEditorScreen(NeoForgePriceEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = NeoForgePriceEditorMenu.GUI_HEIGHT;
    }

    @Override protected void init() {
        super.init();
        value = new EditBox(font, leftPos + 8, topPos + 73, 100, 16, Component.translatable("screen.primordial.custom_price"));
        value.setMaxLength(NeoForgePriceEditorPayloads.MAX_PRICE_TEXT);
        value.setFilter(text -> text.matches("[a-zA-Z0-9_./-]*"));
        value.setResponder(text -> inputEdited = true);
        addRenderableWidget(value);
        save = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.set"), ignored -> submit(value.getValue()))
                .bounds(leftPos + 112, topPos + 72, 56, 18).build());
        modeButtons.clear();
        PriceMode[] modes = {PriceMode.INHERIT_NATIVE, PriceMode.CUSTOM_VALUE, PriceMode.DISABLED, PriceMode.BUY_ONLY, PriceMode.SELL_ONLY};
        for (int i = 0; i < modes.length; i++) {
            PriceMode mode = modes[i];
            modeButtons.put(mode, addRenderableWidget(Button.builder(Component.translatable("screen.primordial.price_action_" + lower(mode)),
                    ignored -> submit(mode.name().toLowerCase(Locale.ROOT))).bounds(leftPos + 8 + i * 32, topPos + 94, 30, 18).build()));
        }
        lastVersion = -1;
        lastState = null;
        pending = false;
        syncSelectionUi();
    }

    public Rect2i jeiPriceArea() { return new Rect2i(leftPos + 80, topPos + 25, 16, 16); }

    public void acceptJeiItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        menu.setItemSelection(stack);
        sendTarget();
    }
    public void acceptJeiFluid(FluidStack fluid) {
        if (fluid == null || fluid.isEmpty()) return;
        menu.setFluidSelection(fluid);
        sendTarget();
    }
    public void acceptJeiChemical(ResourceLocation id, long amount) {
        if (amount <= 0 || !NeoForgePriceEditorChemicalBridge.validChemical(id)) return;
        menu.setGasSelection(id);
        sendTarget();
    }
    private void sendTarget() {
        PacketDistributor.sendToServer(new NeoForgePriceEditorPayloads.Target(menu.containerId, menu.selectedKind(),
                menu.selectedId().toString(), menu.selectionVersion()));
        syncSelectionUi();
    }

    private void syncSelectionUi() {
        if (value == null) return;
        byte kind = menu.selectedKind();
        if (lastVersion != menu.selectionVersion()) {
            lastVersion = menu.selectionVersion();
            value.setMaxLength(kind == NeoForgePriceEditorMenu.KIND_FLUID ? NeoForgePriceEditorPayloads.MAX_PRICE_TEXT : 4096);
            value.setValue("");
            inputEdited = false;
            pending = false;
            localStatus = null;
            lastState = null;
            value.setTooltip(Tooltip.create(Component.translatable("screen.primordial." + (kind == NeoForgePriceEditorMenu.KIND_FLUID
                    ? "price_input_help_fluid" : kind == NeoForgePriceEditorMenu.KIND_GAS ? "price_input_help_gas" : "price_input_help"))));
        }
        State state = menu.priceState();
        if (state != null && state != lastState) {
            lastState = state;
            if (!inputEdited || state.status() == Status.SAVED) {
                value.setValue(state.price());
                inputEdited = false;
            }
            if (state.status() != Status.CURRENT || !pending) {
                pending = false;
                localStatus = null;
            }
        }
        boolean editable = menu.selectedId() != null && state != null && state.editable() && state.available() && !pending;
        save.active = editable;
        value.setEditable(editable);
        boolean media = kind != NeoForgePriceEditorMenu.KIND_ITEM;
        for (var entry : modeButtons.entrySet()) {
            PriceMode mode = entry.getKey();
            Button button = entry.getValue();
            button.visible = media || mode == PriceMode.INHERIT_NATIVE || mode == PriceMode.DISABLED;
            button.active = button.visible && editable && !Objects.equals(mode.name(), state == null ? "" : state.mode());
            if (mode == PriceMode.INHERIT_NATIVE || mode == PriceMode.DISABLED) {
                button.setWidth(media ? 30 : 78);
                button.setX(leftPos + (mode == PriceMode.INHERIT_NATIVE ? 8 : media ? 72 : 90));
            }
            if (button.visible) button.setTooltip(Tooltip.create(Component.translatable("screen.primordial.price_action_" + lower(mode)
                    + (media ? "_media" : "_item") + "_help")));
        }
    }

    /** 预校验只改善输入反馈；服务端仍完整重新解析并确认目标、权限和修订。 */
    private void submit(String text) {
        if (pending || menu.selectedId() == null) return;
        State state = menu.priceState();
        if (state == null || !state.editable() || !state.available()) return;
        try {
            PriceMode mode = modeOrNull(text);
            if (mode == null) {
                if (menu.selectedKind() == NeoForgePriceEditorMenu.KIND_FLUID) EmcAmount.parse(text);
                else {
                    if (text.isEmpty() || text.length() > 4096 || !text.matches("[0-9]+")) throw new IllegalArgumentException("Invalid integer");
                    BigInteger number = new BigInteger(text);
                    if (menu.selectedKind() == NeoForgePriceEditorMenu.KIND_ITEM && number.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
                        throw new IllegalArgumentException("ProjectE item limit");
                }
            } else if (menu.selectedKind() == NeoForgePriceEditorMenu.KIND_ITEM && mode != PriceMode.INHERIT_NATIVE
                    && mode != PriceMode.CUSTOM_VALUE && mode != PriceMode.DISABLED) throw new IllegalArgumentException("Item direction");
            PacketDistributor.sendToServer(new NeoForgePriceEditorPayloads.Edit(menu.containerId, menu.selectedKind(), menu.selectedId().toString(),
                    menu.selectionVersion(), state.priceRevision(), text));
            pending = true;
            localStatus = Component.translatable("screen.primordial.price_saving");
        } catch (IllegalArgumentException invalid) {
            localStatus = Component.translatable("screen.primordial." + (menu.selectedKind() == NeoForgePriceEditorMenu.KIND_FLUID
                    ? "price_invalid_fluid" : menu.selectedKind() == NeoForgePriceEditorMenu.KIND_ITEM ? "price_invalid_item" : "price_invalid_integer"));
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncSelectionUi();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.selectedKind() != NeoForgePriceEditorMenu.KIND_ITEM && menu.selectedId() != null && isHovering(80, 25, 16, 16, mouseX, mouseY)) {
            Component name = menu.selectedKind() == NeoForgePriceEditorMenu.KIND_FLUID ? menu.selectedFluid().getHoverName()
                    : NeoForgePriceEditorChemicalBridge.chemicalName(menu.selectedId());
            graphics.renderTooltip(font, name.copy().append(" [" + menu.selectedId() + "]"), mouseX, mouseY);
        }
        if (menu.priceState() != null && isHovering(8, 47, imageWidth - 16, 9, mouseX, mouseY))
            graphics.renderTooltip(font, Component.translatable("screen.primordial.price_current", menu.priceState().price()), mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BACKGROUND);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER);
        for (var slot : menu.slots) graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, SLOT_BORDER);
        if (menu.selectedKind() == NeoForgePriceEditorMenu.KIND_ITEM || menu.selectedId() == null) return;
        ResourceLocation texture;
        int tint;
        if (menu.selectedKind() == NeoForgePriceEditorMenu.KIND_FLUID) {
            FluidStack fluid = menu.selectedFluid();
            var extension = IClientFluidTypeExtensions.of(fluid.getFluid());
            texture = extension.getStillTexture(fluid);
            tint = extension.getTintColor(fluid);
        } else {
            texture = NeoForgePriceEditorChemicalBridge.chemicalTexture(menu.selectedId());
            tint = 0xFF000000 | NeoForgePriceEditorChemicalBridge.chemicalTint(menu.selectedId());
        }
        if (texture != null) {
            var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
            graphics.blit(leftPos + 80, topPos + 25, 0, 16, 16, sprite, ((tint >> 16) & 255) / 255F,
                    ((tint >> 8) & 255) / 255F, (tint & 255) / 255F, ((tint >>> 24) & 255) / 255F);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.primordial.custom_price"), 8, 7, PRIMARY, false);
        String kindKey = menu.selectedKind() == NeoForgePriceEditorMenu.KIND_FLUID ? "price_fluid"
                : menu.selectedKind() == NeoForgePriceEditorMenu.KIND_GAS ? "price_gas" : "price_item";
        graphics.drawString(font, Component.translatable("screen.primordial." + kindKey), 8, 23, SECONDARY, false);
        State state = menu.priceState();
        String current = state == null || state.price().isEmpty() ? "—" : state.price();
        String label = Component.translatable("screen.primordial.price_current", current).getString();
        if (font.width(label) > imageWidth - 16) label = font.plainSubstrByWidth(label, imageWidth - 25) + "...";
        graphics.drawString(font, label, 8, 47, SECONDARY, false);
        PriceMode mode = state == null ? null : modeOrNull(state.mode());
        graphics.drawString(font, Component.translatable("screen.primordial.price_mode", Component.translatable("screen.primordial.price_mode_"
                + (mode == null ? "unknown" : lower(mode)))), 8, 59, SECONDARY, false);
        Component feedback = localStatus;
        if (feedback == null) {
            String key;
            if (state == null) key = menu.selectedId() == null ? "price_select" : "price_loading";
            else if (!state.editable()) key = "price_denied";
            else if (!state.messageKey().isEmpty()) key = state.messageKey();
            else if (menu.selectedId() == null) key = "price_select";
            else if (!state.available()) key = "price_unavailable";
            else key = menu.selectedId() == null ? "price_select" : menu.selectedKind() == NeoForgePriceEditorMenu.KIND_ITEM ? "price_scope" : "price_scope_media";
            feedback = Component.translatable(key.indexOf('.') >= 0 ? key : "screen.primordial." + key);
        }
        graphics.drawWordWrap(font, feedback, 8, 118, imageWidth - 16, SECONDARY);
    }

    private static String lower(PriceMode mode) { return mode.name().toLowerCase(Locale.ROOT); }
    private static PriceMode modeOrNull(String text) {
        try { return PriceMode.parse(text); }
        catch (IllegalArgumentException invalid) { return null; }
    }
}
