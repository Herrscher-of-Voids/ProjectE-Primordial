/** Keep rendering, hover and keyboard/mouse actions aligned with the selected client page. */
package com.primordial.neoforge.mixin;

import com.primordial.neoforge.client.NeoForgeTransmutationPages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class, remap = false)
public abstract class NeoForgeTransmutationSlotMixin {
    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void primordial$renderSlot(GuiGraphics graphics, Slot slot, CallbackInfo callback) {
        if (NeoForgeTransmutationPages.hiddenSlot(this, slot)) callback.cancel();
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void primordial$hoverSlot(Slot slot, double x, double y, CallbackInfoReturnable<Boolean> callback) {
        if (NeoForgeTransmutationPages.hiddenSlot(this, slot)) callback.setReturnValue(false);
    }

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void primordial$clickSlot(Slot slot, int index, int button, ClickType type, CallbackInfo callback) {
        if (NeoForgeTransmutationPages.blockClick(this, slot, index, type)) callback.cancel();
    }
}
