/**
 * 【变更记录 #1】2026-10-08 02:06:10（第 1 次备注 · 1.2.0-jei-counts）
 * - 删除：无；新增：JEI 右侧列表绘制后显示开关；修改：无 JEI 布局变更。
 * - 用途：按钮保持在列表底部左侧，隐藏列表时不显示。
 */
package com.primordial.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class JeiCountOverlayMixin {
    @Shadow public abstract boolean isListDisplayed();
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void primordial$button(Minecraft mc, GuiGraphics graphics, int x, int y, float partial, CallbackInfo ci) {
        if (isListDisplayed()) com.primordial.client.JeiEmcCounts.renderButton(graphics, x, y, partial);
    }
}
