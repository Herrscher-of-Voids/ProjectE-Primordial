/** Optional integrations expose only common types; all mutation targets a detached one-item copy. */
package com.primordial.neoforge.transmutation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public interface NeoForgeMediaContainer {
    ResourceLocation id();
    long move(long maximum, boolean buy, boolean simulate);
    ItemStack result();
    default FluidStack fluid() { return FluidStack.EMPTY; }
}
