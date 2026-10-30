/** Optional JEI 19 count hook; excluded from dedicated-server mixin loading. */
package com.primordial.neoforge.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class NeoForgeJeiCountOverlayMixin {
    @Shadow public abstract boolean isListDisplayed();
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void primordial$button(Minecraft mc, GuiGraphics graphics, int x, int y, float partial, CallbackInfo ci) {
        if (isListDisplayed()) com.primordial.neoforge.client.NeoForgeJeiEmcCounts.renderButton(graphics, x, y, partial);
    }
}
