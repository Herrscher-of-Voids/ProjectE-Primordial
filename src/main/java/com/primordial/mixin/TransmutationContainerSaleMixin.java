package com.primordial.mixin;

import com.primordial.service.TransmutationContainerReturn;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransmutationContainer.class, remap = false)
public abstract class TransmutationContainerSaleMixin {
    @Shadow @Final public TransmutationInventory transmutationInventory;
    @Inject(method = {"quickMoveStack", "m_7648_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$returnContents(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        var menu = (TransmutationContainer)(Object)this;
        if (index <= 26 || index >= 63 || index >= menu.slots.size()) return;
        var slot = menu.getSlot(index);
        ItemStack stack = slot.getItem();
        if (!TransmutationContainerReturn.enabled(transmutationInventory) || !TransmutationContainerReturn.handles(stack)) return;
        if (transmutationInventory.isServer() && !TransmutationContainerReturn.sell(transmutationInventory, stack,
                () -> slot.set(ItemStack.EMPTY)))
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.primordial.container_return_unsupported"), true);
        cir.setReturnValue(ItemStack.EMPTY);
    }
}
