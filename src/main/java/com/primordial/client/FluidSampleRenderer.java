/**
 * 【文件职责】从权威过滤数据绘制流体/气体样本，不依赖桶或其它实物图标。
 * 【变更记录 #1】2026-09-15 11:42:30（第 1 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：Forge 流体与 Mekanism 气体的原生材质、颜色和悬停名称。
 * - 修改：通过独立气体内部类隔离可选 API，绘制后恢复白色避免污染后续 GUI。
 * - 用途：让 JEI 无桶流体和反物质样本在保存、切页、重开后保持可见；不在渲染循环读取价格配置文件。
 */
package com.primordial.client;

import com.primordial.data.FluidSampleConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

public final class FluidSampleRenderer {
    private FluidSampleRenderer() { }
    public static void render(GuiGraphics graphics, FluidSampleConfig sample, int x, int y) {
        if (sample.hasGasFilter()) {
            Gas.render(graphics, sample, x, y);
        } else if (!sample.filter.isEmpty()) {
            var extensions = IClientFluidTypeExtensions.of(sample.filter.getFluid());
            draw(graphics, extensions.getStillTexture(sample.filter), extensions.getTintColor(sample.filter), x, y);
        }
    }

    public static Component name(FluidSampleConfig sample) {
        if (sample.hasGasFilter()) return Gas.name(sample);
        return sample.filter.isEmpty() ? Component.empty() : sample.filter.getDisplayName();
    }

    private static void draw(GuiGraphics graphics, ResourceLocation texture, int tint, int x, int y) {
        if (texture == null) return;
        var sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        graphics.setColor((tint >> 16 & 255) / 255F, (tint >> 8 & 255) / 255F, (tint & 255) / 255F, (tint >>> 24) / 255F);
        try { graphics.blit(x, y, 200, 16, 16, sprite); }
        finally { graphics.setColor(1, 1, 1, 1); }
    }

    private static final class Gas {
        static void render(GuiGraphics graphics, FluidSampleConfig sample, int x, int y) {
            ResourceLocation icon = com.primordial.compat.MekanismGasCompat.gasIcon(sample.gasFilter);
            draw(graphics, icon, com.primordial.compat.MekanismGasCompat.gasColor(sample.gasFilter) | 0xFF000000, x, y);
        }
        static Component name(FluidSampleConfig sample) {
            return com.primordial.compat.MekanismGasCompat.gasName(sample.gasFilter);
        }
    }
}
