package com.primordial.neoforge.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** BD 0.8.2's Botania integration requires the old API namespace, which Revived does not expose. */
@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.integration.ModPresence", remap = false)
public abstract class NeoForgeBeyondDimensionsBotaniaPresenceMixin {
    @Inject(method = "isLoaded", at = @At("RETURN"), cancellable = true, remap = false)
    private static void primordial$requireCompatibleBotaniaApi(String modId, CallbackInfoReturnable<Boolean> cir) {
        if (!"botania".equals(modId) || !Boolean.TRUE.equals(cir.getReturnValue())) return;
        try {
            Class.forName("vazkii.botania.api.mana.ManaCollector", false,
                    NeoForgeBeyondDimensionsBotaniaPresenceMixin.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError unavailable) {
            cir.setReturnValue(false);
        }
    }
}
