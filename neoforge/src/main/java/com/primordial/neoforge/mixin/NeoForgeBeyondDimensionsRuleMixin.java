package com.primordial.neoforge.mixin;

import com.primordial.neoforge.compat.beyonddimensions.NeoForgeBeyondDimensionsRuleBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage", remap = false)
public abstract class NeoForgeBeyondDimensionsRuleMixin {
    @Redirect(method = "extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;",
            at = @At(value = "INVOKE", target = "Lcom/wintercogs/beyonddimensions/api/dimensionnet/StorageTransaction;extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;J)J"), remap = false)
    private long primordial$preserveRulesAndFundShortfall(@Coerce Object transaction, @Coerce Object key, long amount,
                                                        @Coerce Object original, long wanted, boolean simulate, boolean fuzzy) {
        return NeoForgeBeyondDimensionsRuleBridge.extract(transaction, key, amount, simulate);
    }

    @Inject(method = "storedAmount", at = @At("RETURN"), cancellable = true, remap = false)
    private void primordial$temporaryRuleBalance(@Coerce Object key, CallbackInfoReturnable<Long> cir) {
        cir.setReturnValue(NeoForgeBeyondDimensionsRuleBridge.storedAmount(this, key, cir.getReturnValue()));
    }
}
