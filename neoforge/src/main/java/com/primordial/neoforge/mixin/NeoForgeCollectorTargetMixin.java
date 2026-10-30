package com.primordial.neoforge.mixin;

import com.primordial.neoforge.equipment.NeoForgeBlackholeCollectorConversion;
import moze_intel.projecte.gameObjs.container.CollectorMK1Container;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CollectorMK1Container.class, remap = false)
public abstract class NeoForgeCollectorTargetMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void primordial$selectNext(int slot, int button, ClickType click, Player player, CallbackInfo callback) {
        if (slot == 0 && click == ClickType.QUICK_MOVE && player instanceof ServerPlayer serverPlayer
                && NeoForgeBlackholeCollectorConversion.selectNext((CollectorMK1Container) (Object) this, serverPlayer)) callback.cancel();
    }
}
