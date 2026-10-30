/** Optional hand-container API boundary: no Mekanism types in the public transmutation path. */
package com.primordial.neoforge.compat;

import com.primordial.neoforge.transmutation.NeoForgeMediaContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class NeoForgeCarriedMekanismBridge {
    private static final String IMPLEMENTATION = "com.primordial.neoforge.compat.mekanism.NeoForgeCarriedMekanismContainers";
    private NeoForgeCarriedMekanismBridge() {}
    public static NeoForgeMediaContainer open(ItemStack copy, ResourceLocation selected) {
        if (!ModList.get().isLoaded("mekanism")) return null;
        try {
            return (NeoForgeMediaContainer) Class.forName(IMPLEMENTATION).getMethod("open", ItemStack.class, ResourceLocation.class)
                    .invoke(null, copy, selected);
        } catch (ReflectiveOperationException | LinkageError failure) { throw new IllegalStateException("Chemical container unavailable", failure); }
    }
    public static int manualFluid(IFluidHandler handler, FluidStack sample, boolean buy, IFluidHandler.FluidAction action) {
        if (!ModList.get().isLoaded("mekanism")) return -1;
        try {
            return (int) Class.forName(IMPLEMENTATION).getMethod("manualFluid", IFluidHandler.class, FluidStack.class,
                    boolean.class, IFluidHandler.FluidAction.class).invoke(null, handler, sample, buy, action);
        } catch (ReflectiveOperationException | LinkageError failure) { throw new IllegalStateException("Manual fluid container unavailable", failure); }
    }
}
