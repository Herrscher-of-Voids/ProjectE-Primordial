package com.primordial.neoforge.mixin;

import com.primordial.neoforge.equipment.NeoForgeBlackholeCollectorConversion;
import moze_intel.projecte.gameObjs.EnumCollectorTier;
import moze_intel.projecte.gameObjs.block_entities.CollectorMK1BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CollectorMK1BlockEntity.class, remap = false)
public abstract class NeoForgeCollectorCapacityMixin {
    @ModifyArg(method = "<init>(Lmoze_intel/projecte/gameObjs/registration/impl/BlockEntityTypeRegistryObject;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lmoze_intel/projecte/gameObjs/EnumCollectorTier;)V",
            at = @At(value = "INVOKE", target = "Lmoze_intel/projecte/gameObjs/block_entities/EmcBlockEntity;<init>(Lmoze_intel/projecte/gameObjs/registration/impl/BlockEntityTypeRegistryObject;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;J)V", remap = false),
            index = 3, remap = false)
    private static long primordial$capacity(long original) { return Math.multiplyExact(original, 100_000L); }

    @Redirect(method = "<init>(Lmoze_intel/projecte/gameObjs/registration/impl/BlockEntityTypeRegistryObject;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lmoze_intel/projecte/gameObjs/EnumCollectorTier;)V",
            at = @At(value = "INVOKE", target = "Lmoze_intel/projecte/gameObjs/EnumCollectorTier;getGenRate()J", remap = false), remap = false)
    private long primordial$generation(EnumCollectorTier tier) { return Math.multiplyExact(tier.getGenRate(), 10L); }

    @Inject(method = "checkFuelOrKlein", at = @At("HEAD"), remap = false)
    private void primordial$adjacentTarget(CallbackInfo callback) {
        var aux = (IItemHandlerModifiable) ((CollectorMK1BlockEntity) (Object) this).getAux();
        ItemStack input = aux.getStackInSlot(CollectorMK1BlockEntity.UPGRADING_SLOT);
        ItemStack lock = aux.getStackInSlot(CollectorMK1BlockEntity.LOCK_SLOT);
        if (NeoForgeBlackholeCollectorConversion.isMaterial(input)) {
            ItemStack next = NeoForgeBlackholeCollectorConversion.next(input);
            if (!ItemStack.isSameItemSameComponents(next, lock)) aux.setStackInSlot(CollectorMK1BlockEntity.LOCK_SLOT, next);
        } else if (!input.isEmpty() && NeoForgeBlackholeCollectorConversion.isMaterial(lock)) {
            aux.setStackInSlot(CollectorMK1BlockEntity.LOCK_SLOT, ItemStack.EMPTY);
        }
    }
    @Inject(method = "rotateUpgraded", at = @At("HEAD"), cancellable = true, remap = false)
    private void primordial$leaveMaterialOutput(CallbackInfo callback) {
        if (NeoForgeBlackholeCollectorConversion.isMaterial(((CollectorMK1BlockEntity) (Object) this).getAux()
                .getStackInSlot(CollectorMK1BlockEntity.UPGRADE_SLOT))) callback.cancel();
    }
}
