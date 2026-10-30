package com.primordial.neoforge.mixin;

import com.primordial.neoforge.transmutation.NeoForgeTransmutationNbtPolicy;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = TransmutationInventory.class, remap = false)
public abstract class NeoForgeTransmutationNbtLearningMixin {
    @Shadow @Final public Player player;

    @ModifyVariable(method = "handleKnowledge(Lmoze_intel/projecte/api/ItemInfo;)V", at = @At("HEAD"), argsOnly = true)
    private ItemInfo primordial$learn(ItemInfo info) {
        return player instanceof ServerPlayer serverPlayer ? NeoForgeTransmutationNbtPolicy.learned(serverPlayer, info) : info;
    }

    @ModifyVariable(method = "writeIntoOutputSlot", at = @At("HEAD"), argsOnly = true)
    private ItemStack primordial$safeOutput(ItemStack stack) {
        return NeoForgeTransmutationNbtPolicy.safeOutput(stack);
    }
}
