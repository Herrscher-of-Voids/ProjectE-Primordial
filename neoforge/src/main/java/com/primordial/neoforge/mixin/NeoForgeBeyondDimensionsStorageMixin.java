package com.primordial.neoforge.mixin;

import java.util.List;
import java.util.Optional;

import com.primordial.neoforge.compat.beyonddimensions.NeoForgeBeyondDimensionsStorageAdapter;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler", remap = false)
public abstract class NeoForgeBeyondDimensionsStorageMixin {
    @Inject(method = "getBucket", at = @At("RETURN"), cancellable = true, remap = false)
    private void primordial$bucket(ResourceLocation type, CallbackInfoReturnable<Optional<?>> cir) {
        cir.setReturnValue(NeoForgeBeyondDimensionsStorageAdapter.bucket(this, type, cir.getReturnValue()));
    }

    @Inject(method = "getOutStackByKey", at = @At("RETURN"), cancellable = true, remap = false)
    private void primordial$output(@Coerce Object key, CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(NeoForgeBeyondDimensionsStorageAdapter.outputStack(this, key, cir.getReturnValue()));
    }

    @Inject(method = "getStorage", at = @At("RETURN"), cancellable = true, remap = false)
    private void primordial$catalogue(CallbackInfoReturnable<List<?>> cir) {
        cir.setReturnValue(NeoForgeBeyondDimensionsStorageAdapter.overlay(this, cir.getReturnValue()));
    }

    @Inject(method = "getStackByKey", at = @At("RETURN"), cancellable = true, remap = false)
    private void primordial$stack(@Coerce Object key, CallbackInfoReturnable<Object> cir) {
        if (key != null) cir.setReturnValue(NeoForgeBeyondDimensionsStorageAdapter.stack(this, key, cir.getReturnValue()));
    }

    @Inject(method = "extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void primordial$extract(@Coerce Object key, long amount, boolean simulate, boolean fuzzy,
                                   CallbackInfoReturnable<Object> cir) {
        if (key != null) cir.setReturnValue(
                NeoForgeBeyondDimensionsStorageAdapter.extract(this, key, amount, simulate, cir.getReturnValue()));
    }
}
