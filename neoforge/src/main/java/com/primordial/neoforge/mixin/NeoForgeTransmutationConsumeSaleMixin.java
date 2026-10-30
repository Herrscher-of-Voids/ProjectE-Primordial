/** Route direct ProjectE consume-slot insertion through safe local-container settlement. */
package com.primordial.neoforge.mixin;

import com.primordial.neoforge.transmutation.NeoForgeTransmutationContainerReturn;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SlotConsume.class, remap = false)
public abstract class NeoForgeTransmutationConsumeSaleMixin {
    @Shadow @Final private TransmutationInventory inv;

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void primordial$returnContainerContents(ItemStack stack, CallbackInfo callback) {
        if (!NeoForgeTransmutationContainerReturn.handles(stack)) return;
        if (inv.isServer() && !NeoForgeTransmutationContainerReturn.sell(inv, stack, () -> {
        })) {
            inv.player.getInventory().placeItemBackInInventory(stack.copy());
            inv.player.displayClientMessage(Component.translatable("message.primordial.container_return_unsupported"), true);
        }
        callback.cancel();
    }
}
