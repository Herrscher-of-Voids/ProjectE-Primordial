/** Detached item capability fixtures: partial execution, a throwing handler, and an over-reporting handler. */
package com.primordial.neoforge.trade;

import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class CarriedMediaTestFixture {
    static final ResourceLocation PARTIAL = id("carried_test_partial"), THROWING = id("carried_test_throw"), EXCESS = id("carried_test_excess");
    static final ResourceLocation BATTERY = id("carried_test_battery");
    static Runnable onExecute;
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }
    @SubscribeEvent public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            for (var id : List.of(PARTIAL, THROWING, EXCESS, BATTERY))
                event.register(Registries.ITEM, id, () -> new Item(new Item.Properties()));
        }
    }
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        for (var id : List.of(PARTIAL, THROWING, EXCESS)) event.registerItem(Capabilities.FluidHandler.ITEM,
                (stack, context) -> new Tank(stack, id), BuiltInRegistries.ITEM.get(id));
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> new Battery(stack), BuiltInRegistries.ITEM.get(BATTERY));
    }
    static ItemStack stack(ResourceLocation id) { return new ItemStack(BuiltInRegistries.ITEM.get(id)); }
    static int amount(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("water"); }
    static ItemStack battery(int energy) {
        ItemStack stack = stack(BATTERY);
        setEnergy(stack, energy);
        return stack;
    }
    static int energy(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("energy"); }
    private static void setEnergy(ItemStack stack, int energy) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        data.putInt("energy", energy);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
    private record Tank(ItemStack getContainer, ResourceLocation mode) implements IFluidHandlerItem {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return new FluidStack(Fluids.WATER, amount(getContainer)); }
        @Override public int getTankCapacity(int tank) { return 1000; }
        @Override public boolean isFluidValid(int tank, FluidStack fluid) { return fluid.is(Fluids.WATER); }
        @Override public int fill(FluidStack fluid, FluidAction action) {
            if (!fluid.is(Fluids.WATER)) return 0;
            return move(Math.min(fluid.getAmount(), 1000 - amount(getContainer)), true, action);
        }
        @Override public FluidStack drain(FluidStack fluid, FluidAction action) {
            return fluid.is(Fluids.WATER) ? drain(fluid.getAmount(), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int maximum, FluidAction action) {
            return new FluidStack(Fluids.WATER, move(Math.min(maximum, amount(getContainer)), false, action));
        }
        private int move(int maximum, boolean buy, FluidAction action) {
            if (action.simulate() || maximum <= 0) return maximum;
            int moved = Math.min(250, maximum);
            CompoundTag data = getContainer.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            data.putInt("water", amount(getContainer) + (buy ? moved : -moved));
            getContainer.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            if (onExecute != null) onExecute.run();
            if (mode.equals(THROWING)) throw new IllegalStateException("Intentional detached capability failure");
            return mode.equals(EXCESS) ? maximum + 1 : moved;
        }
    }

    private record Battery(ItemStack container) implements IEnergyStorage {
        private static final int CAPACITY = 2000;
        private static final int TRANSFER_LIMIT = 250;
        @Override public int receiveEnergy(int maximum, boolean simulate) {
            int moved = Math.min(Math.min(maximum, TRANSFER_LIMIT), CAPACITY - energy(container));
            CompoundTag flags = container.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (moved > 0 && (!simulate || flags.getBoolean("mutate_energy_simulation")))
                setEnergy(container, energy(container) + moved);
            if (!simulate && flags.getBoolean("throw_energy_execution"))
                throw new IllegalStateException("Intentional detached energy capability failure");
            if (!simulate && moved > 0 && flags.getBoolean("overreport_energy_execution")) return moved + 1;
            if (!simulate && moved > 0 && flags.getBoolean("mutate_energy_execution_count")) container.setCount(2);
            return moved;
        }
        @Override public int extractEnergy(int maximum, boolean simulate) {
            int moved = Math.min(Math.min(maximum, TRANSFER_LIMIT), energy(container));
            if (!simulate && moved > 0) setEnergy(container, energy(container) - moved);
            return moved;
        }
        @Override public int getEnergyStored() { return energy(container); }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return energy(container) > 0; }
        @Override public boolean canReceive() { return energy(container) < CAPACITY; }
    }
}
