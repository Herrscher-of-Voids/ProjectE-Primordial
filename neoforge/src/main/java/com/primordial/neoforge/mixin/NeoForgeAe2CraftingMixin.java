package com.primordial.neoforge.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.primordial.neoforge.compat.ae2.NeoForgeAe2CraftingTransaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "appeng.crafting.execution.CraftingCpuHelper", remap = false)
public abstract class NeoForgeAe2CraftingMixin {
    @WrapMethod(method = "tryExtractInitialItems", remap = false)
    private static GenericStack primordial$initialItems(ICraftingPlan plan, IGrid grid, ListCraftingInventory inventory,
                                                       IActionSource source, Operation<GenericStack> original) {
        try (var transaction = NeoForgeAe2CraftingTransaction.begin(source)) {
            try {
                return original.call(plan, grid, inventory, source);
            } catch (RuntimeException | Error failure) {
                transaction.consumeReturned(inventory);
                throw failure;
            }
        }
    }

    @Redirect(method = "tryExtractInitialItems", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"), remap = false)
    private static long primordial$returnInitialItems(MEStorage inventory, AEKey key, long amount,
                                                     Actionable mode, IActionSource source) {
        return NeoForgeAe2CraftingTransaction.returnInitialItems(inventory, key, amount, mode, source);
    }

    @Redirect(method = "tryExtractInitialItems", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/inv/ListCraftingInventory;clear()V"), remap = false)
    private static void primordial$keepRejectedReturns(ListCraftingInventory inventory) {
        NeoForgeAe2CraftingTransaction.clearReturnedItems(inventory);
    }
}
