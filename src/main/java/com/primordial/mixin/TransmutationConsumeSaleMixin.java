package com.primordial.mixin;

import com.primordial.service.TransmutationContainerReturn;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SlotConsume.class, remap = false)
public abstract class TransmutationConsumeSaleMixin {
    @Shadow @Final private TransmutationInventory inv;
    @Inject(method = {"set", "m_5852_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$returnContents(ItemStack stack, CallbackInfo ci) {
        if (!TransmutationContainerReturn.enabled(inv) || !TransmutationContainerReturn.handles(stack)) return;
        if (inv.isServer() && !TransmutationContainerReturn.sell(inv, stack, () -> { })) {
            inv.player.getInventory().placeItemBackInInventory(stack.copy());
            inv.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.primordial.container_return_unsupported"), true);
        }
        ci.cancel();
    }
}
