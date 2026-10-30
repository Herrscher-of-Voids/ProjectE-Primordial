/** Unified chemical and fluid tanks use MANUAL interactions, preserving capacity and validity rules. */
package com.primordial.neoforge.compat.mekanism;

import java.util.List;
import com.primordial.neoforge.transmutation.NeoForgeMediaContainer;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.chemical.IMekanismChemicalHandler;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class NeoForgeCarriedMekanismContainers {
    private static final ItemCapability<IChemicalHandler, Void> CHEMICAL = ItemCapability.createVoid(
            ResourceLocation.parse("mekanism:chemical_handler"), IChemicalHandler.class);
    private NeoForgeCarriedMekanismContainers() {}

    public static NeoForgeMediaContainer open(ItemStack copy, ResourceLocation selected) {
        IChemicalHandler handler = copy.getCapability(CHEMICAL);
        if (handler == null) return null;
        ChemicalStack sample = selected == null ? ChemicalStack.EMPTY : new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolder(selected).orElseThrow(), 1);
        if (selected == null) for (int tank = 0; tank < handler.getChemicalTanks(); tank++) {
            ChemicalStack stored = handler.getChemicalInTank(tank);
            if (!stored.isEmpty()) { sample = stored.copyWithAmount(1); break; }
        }
        return new ChemicalContainer(copy, handler, sample);
    }

    private record ChemicalContainer(ItemStack result, IChemicalHandler handler, ChemicalStack sample) implements NeoForgeMediaContainer {
        @Override public ResourceLocation id() { return sample.isEmpty() ? null : sample.getChemicalHolder().getKey().location(); }
        @Override public long move(long maximum, boolean buy, boolean simulate) {
            Action action = simulate ? Action.SIMULATE : Action.EXECUTE;
            long remaining = maximum;
            for (int index = 0; index < handler.getChemicalTanks() && remaining > 0; index++) {
                IChemicalTank tank = handler instanceof IMekanismChemicalHandler typed ? typed.getChemicalTank(index, null) : null;
                long moved;
                if (buy) {
                    ChemicalStack proposal = sample.copyWithAmount(remaining);
                    ChemicalStack rest = tank == null ? handler.insertChemical(index, proposal, action)
                            : tank.insert(proposal, action, AutomationType.MANUAL);
                    if (!rest.isEmpty() && !ChemicalStack.isSameChemical(rest, sample)) throw new IllegalStateException("Wrong chemical remainder");
                    moved = remaining - rest.getAmount();
                } else {
                    if (!ChemicalStack.isSameChemical(handler.getChemicalInTank(index), sample)) continue;
                    ChemicalStack removed = tank == null ? handler.extractChemical(index, remaining, action)
                            : tank.extract(remaining, action, AutomationType.MANUAL);
                    if (!removed.isEmpty() && !ChemicalStack.isSameChemical(removed, sample)) throw new IllegalStateException("Wrong extracted chemical");
                    moved = removed.getAmount();
                }
                if (moved < 0 || moved > remaining) throw new IllegalStateException("Chemical capability exceeded authorization");
                remaining -= moved;
            }
            return maximum - remaining;
        }
    }

    /** -1 means an ordinary NeoForge fluid handler; its own fill/drain contract must be used. */
    public static int manualFluid(IFluidHandler handler, FluidStack sample, boolean buy, IFluidHandler.FluidAction action) {
        List<IExtendedFluidTank> tanks;
        if (handler instanceof IMekanismFluidHandler typed) tanks = typed.getFluidTanks(null);
        else if (handler instanceof IExtendedFluidTank direct) tanks = List.of(direct);
        else return -1;
        int remaining = sample.getAmount();
        Action interaction = action.simulate() ? Action.SIMULATE : Action.EXECUTE;
        for (IExtendedFluidTank tank : tanks) {
            if (remaining == 0) break;
            int moved;
            if (buy) {
                FluidStack rest = tank.insert(sample.copyWithAmount(remaining), interaction, AutomationType.MANUAL);
                if (!rest.isEmpty() && !FluidStack.isSameFluidSameComponents(rest, sample)) throw new IllegalStateException("Wrong fluid remainder");
                moved = remaining - rest.getAmount();
            } else {
                if (!FluidStack.isSameFluidSameComponents(tank.getFluid(), sample)) continue;
                FluidStack removed = tank.extract(remaining, interaction, AutomationType.MANUAL);
                if (!removed.isEmpty() && !FluidStack.isSameFluidSameComponents(removed, sample)) throw new IllegalStateException("Wrong extracted fluid");
                moved = removed.getAmount();
            }
            if (moved < 0 || moved > remaining) throw new IllegalStateException("Fluid capability exceeded authorization");
            remaining -= moved;
        }
        return sample.getAmount() - remaining;
    }
}
