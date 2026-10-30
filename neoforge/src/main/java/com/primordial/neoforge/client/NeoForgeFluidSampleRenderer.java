/**
 * Draws NeoForge fluid and chemical samples using the same atlas/color/depth
 * sequence as the Forge 1.20.1 media renderer.
 */
package com.primordial.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeFluidSampleRenderer {
    private NeoForgeFluidSampleRenderer() {}

    public static void renderFluid(GuiGraphics graphics, FluidStack sample, int x, int y) {
        if (sample == null || sample.isEmpty()) return;
        var extension = IClientFluidTypeExtensions.of(sample.getFluid());
        draw(graphics, extension.getStillTexture(sample), extension.getTintColor(sample), x, y);
    }

    public static void renderChemical(GuiGraphics graphics, ResourceLocation texture, int tint, int x, int y) {
        draw(graphics, texture, tint, x, y);
    }

    private static void draw(GuiGraphics graphics, ResourceLocation texture, int tint, int x, int y) {
        if (texture == null) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        graphics.setColor((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F,
                (tint & 255) / 255F, (tint >>> 24 & 255) / 255F);
        try {
            graphics.blit(x, y, 200, 16, 16, sprite);
        } finally {
            graphics.setColor(1, 1, 1, 1);
        }
    }
}
