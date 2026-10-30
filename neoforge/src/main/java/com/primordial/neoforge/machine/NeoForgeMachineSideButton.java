package com.primordial.neoforge.machine;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** The Forge selector's live neighboring-block icon; clicking still selects the machine's own face. */
final class NeoForgeMachineSideButton extends Button {
    private final Supplier<BlockState> neighborState;
    private final Direction side;
    private final boolean selected;
    private TextureAtlasSprite sprite;
    private ItemStack icon = ItemStack.EMPTY;
    private BlockState lastState;
    private boolean initialized;

    NeoForgeMachineSideButton(int x, int y, Component name, Supplier<BlockState> neighborState,
                             Direction side, boolean selected, OnPress action) {
        super(x, y, 30, 34, name, action, DEFAULT_NARRATION);
        this.neighborState = neighborState;
        this.side = side;
        this.selected = selected;
        updateNeighbor();
    }

    private void updateNeighbor() {
        BlockState state = neighborState.get();
        if (initialized && state == lastState) return;
        initialized = true;
        lastState = state;
        icon = ItemStack.EMPTY;
        sprite = null;
        Component name = Component.translatable(state == null
                ? "screen.primordial.side.neighbor_unavailable" : "screen.primordial.side.neighbor_air");
        if (state != null && !state.isAir()) {
            icon = new ItemStack(state.getBlock().asItem());
            name = icon.isEmpty() ? state.getBlock().getName() : icon.getHoverName();
            if (icon.isEmpty()) {
                try {
                    sprite = Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon();
                } catch (RuntimeException | LinkageError ignored) { }
            }
        }
        setTooltip(Tooltip.create(Component.translatable("screen.primordial.side.neighbor_hint", getMessage(),
                Component.translatable("screen.primordial.side." + side.getName()), name)
                .append("\n").append(Component.translatable("screen.primordial.side.face_hint", getMessage()))));
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateNeighbor();
        int x = getX(), y = getY();
        graphics.fill(x, y, x + width, y + height, isHoveredOrFocused() ? 0xFF405363 : 0xFF202B35);
        graphics.renderOutline(x, y, width, height,
                selected ? 0xFF66E5BB : isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF657583);
        if (!icon.isEmpty()) graphics.renderItem(icon, x + 7, y + 3);
        else if (sprite != null) graphics.blit(x + 7, y + 3, 0, 16, 16, sprite);
        else graphics.drawCenteredString(Minecraft.getInstance().font,
                lastState != null && lastState.isAir() ? "—" : "?", x + 15, y + 6, 0xFFFFFF);
        graphics.drawCenteredString(Minecraft.getInstance().font, getMessage(), x + 15, y + 22,
                selected ? 0xFF66E5BB : 0xFFFFFFFF);
    }
}
