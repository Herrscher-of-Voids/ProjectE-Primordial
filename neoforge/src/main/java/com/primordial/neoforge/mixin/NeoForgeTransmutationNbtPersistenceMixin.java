package com.primordial.neoforge.mixin;

import com.primordial.neoforge.transmutation.NeoForgeTransmutationNbtPolicy;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.emc.components.DataComponentManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataComponentManager.class, remap = false)
public abstract class NeoForgeTransmutationNbtPersistenceMixin {
    @Inject(method = "getPersistentInfo", at = @At("HEAD"), cancellable = true)
    private static void primordial$persistent(ItemInfo info, CallbackInfoReturnable<ItemInfo> callback) {
        callback.setReturnValue(NeoForgeTransmutationNbtPolicy.persistent(info));
    }
}
