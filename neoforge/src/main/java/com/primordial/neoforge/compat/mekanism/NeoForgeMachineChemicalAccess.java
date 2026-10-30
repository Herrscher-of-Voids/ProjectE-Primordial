package com.primordial.neoforge.compat.mekanism;

import java.util.ArrayList;
import java.util.List;

import com.primordial.data.MachineTarget;
import com.primordial.neoforge.machine.NeoForgeCapabilityProxy;
import com.primordial.neoforge.machine.NeoForgeMachinePortPage;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/** This class is reflectively loaded only when Mekanism is installed. */
public final class NeoForgeMachineChemicalAccess {
    public static final BlockCapability<IChemicalHandler, @Nullable Direction> CHEMICAL =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    private NeoForgeMachineChemicalAccess() {}

    public static boolean hasCapability(BlockEntity machine, @Nullable Direction side) {
        IChemicalHandler handler = handler(machine, side);
        return handler != null && handler.getChemicalTanks() > 0;
    }

    public static NeoForgeMachinePortPage read(BlockEntity machine, @Nullable Direction side, int page) {
        if (!live(machine)) return empty();
        var state = machine.getBlockState();
        IChemicalHandler handler = handler(machine, side);
        if (handler == null) return empty();
        int total = Math.max(0, handler.getChemicalTanks());
        int selected = Math.max(0, Math.min(page, Math.max(0, total - 1) / NeoForgeMachinePortPage.PAGE_SIZE));
        int start = selected * NeoForgeMachinePortPage.PAGE_SIZE;
        int end = (int) Math.min((long) total, (long) start + NeoForgeMachinePortPage.PAGE_SIZE);
        List<NeoForgeMachinePortPage.Port> ports = new ArrayList<>(end - start);
        for (int index = start; index < end; index++) {
            ChemicalStack stack = handler.getChemicalInTank(index);
            ResourceLocation id = chemicalId(stack);
            long capacity = Math.max(0L, handler.getChemicalTankCapacity(index));
            String key = NeoForgeCapabilityProxy.INSTANCE.endpointKey(machine, side) + "/gas/"
                    + handler.getClass().getName() + "/" + total + "/" + index;
            if (key.length() > MachineTarget.MAX_KEY_LENGTH) key = "";
            ports.add(new NeoForgeMachinePortPage.Port(index, key, net.minecraft.world.item.ItemStack.EMPTY,
                    net.neoforged.neoforge.fluids.FluidStack.EMPTY, id,
                    stack == null || stack.isEmpty() ? 0 : bounded(stack.getAmount()), bounded(capacity)));
        }
        if (!live(machine) || !state.equals(machine.getBlockState()) || handler.getChemicalTanks() != total) return empty();
        return new NeoForgeMachinePortPage("mekanism_chemical_handler", selected, total, ports);
    }

    private static ResourceLocation chemicalId(@Nullable ChemicalStack stack) {
        if (stack == null || stack.isEmpty() || stack.getAmount() <= 0) return null;
        ResourceLocation id = stack.getChemicalHolder().unwrapKey().map(key -> key.location()).orElse(null);
        return NeoForgePriceEditorChemicalSamples.validChemical(id) ? id : null;
    }

    @Nullable
    private static IChemicalHandler handler(@Nullable BlockEntity machine, @Nullable Direction side) {
        if (!live(machine)) return null;
        Level level = machine.getLevel();
        return level.getCapability(CHEMICAL, machine.getBlockPos(), machine.getBlockState(), machine, side);
    }

    private static boolean live(@Nullable BlockEntity machine) {
        return machine != null && !machine.isRemoved() && machine.getLevel() != null;
    }

    private static int bounded(long value) { return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, value)); }

    private static NeoForgeMachinePortPage empty() {
        return new NeoForgeMachinePortPage("", 0, 0, List.of());
    }
}
