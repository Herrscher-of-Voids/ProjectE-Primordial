/** Client-only page rendering for ProjectE's native transmutation screen. */
package com.primordial.neoforge.mixin;

import com.primordial.neoforge.client.NeoForgeTransmutationPages;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GUITransmutation.class, remap = false)
public abstract class NeoForgeTransmutationScreenMixin {
    @Inject(method = "removed", at = @At("TAIL"))
    private void primordial$closed(CallbackInfo callback) {
        NeoForgeTransmutationPages.closed(this);
    }

    @Inject(method = "renderBg", at = @At("HEAD"), cancellable = true)
    private void primordial$background(GuiGraphics graphics, float partial, int x, int y, CallbackInfo callback) {
        if (!NeoForgeTransmutationPages.craftingPage(this)) return;
        NeoForgeTransmutationPages.background(this, graphics);
        callback.cancel();
    }

    @Inject(method = "renderLabels", at = @At("HEAD"), cancellable = true)
    private void primordial$labels(GuiGraphics graphics, int x, int y, CallbackInfo callback) {
        if (NeoForgeTransmutationPages.craftingPage(this)) callback.cancel();
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void primordial$tooltip(GuiGraphics graphics, int x, int y, CallbackInfo callback) {
        if (!NeoForgeTransmutationPages.secondary(this)) return;
        NeoForgeTransmutationPages.tooltip(this, graphics, x, y);
        callback.cancel();
    }
}
