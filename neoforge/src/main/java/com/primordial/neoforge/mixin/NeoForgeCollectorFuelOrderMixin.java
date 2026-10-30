package com.primordial.neoforge.mixin;

import com.primordial.neoforge.equipment.NeoForgeBlackholeCollectorConversion;
import moze_intel.projecte.emc.FuelMapper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FuelMapper.class, remap = false)
public abstract class NeoForgeCollectorFuelOrderMixin {
    @Inject(method = "loadMap", at = @At("TAIL"), remap = false)
    private static void primordial$orderLoadedMap(CallbackInfo callback) {
        FuelMapper.setFuelMap(FuelMapper.getFuelMap());
    }
    @ModifyVariable(method = "setFuelMap", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private static HolderSet<Item> primordial$orderSyncedMap(HolderSet<Item> input) {
        return NeoForgeBlackholeCollectorConversion.ordered(input);
    }
    // This overload also drives ProjectE's native recipe viewer in 1.21.1.
    @Inject(method = "getFuelUpgrade(Lnet/minecraft/core/Holder;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void primordial$separateUpgrade(Holder<Item> input, CallbackInfoReturnable<Holder<Item>> callback) {
        ItemStack stack = new ItemStack(input);
        if (NeoForgeBlackholeCollectorConversion.isMaterial(stack)) {
            ItemStack next = NeoForgeBlackholeCollectorConversion.next(stack);
            callback.setReturnValue(next.isEmpty() ? null : next.getItemHolder());
        } else if (NeoForgeBlackholeCollectorConversion.lastOrdinary(input.value())) callback.setReturnValue(null);
    }
    @Inject(method = "isStackMaxFuel", at = @At("HEAD"), cancellable = true, remap = false)
    private static void primordial$separateTerminal(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (NeoForgeBlackholeCollectorConversion.isMaterial(stack))
            callback.setReturnValue(NeoForgeBlackholeCollectorConversion.next(stack).isEmpty());
        else if (NeoForgeBlackholeCollectorConversion.lastOrdinary(stack.getItem())) callback.setReturnValue(true);
    }
}
