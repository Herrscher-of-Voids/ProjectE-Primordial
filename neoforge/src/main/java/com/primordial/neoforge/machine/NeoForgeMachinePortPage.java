package com.primordial.neoforge.machine;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.primordial.data.MachineTarget;
import net.minecraft.resources.ResourceLocation;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;

/** Read-only port snapshots; huge inventories are inspected one 27-entry page at a time. */
public record NeoForgeMachinePortPage(String proxyId, int page, int total, List<Port> ports) {
    public static final int PAGE_SIZE = 27;
    private static final NeoForgeMachinePortPage EMPTY = new NeoForgeMachinePortPage("", 0, 0, List.of());

    public NeoForgeMachinePortPage {
        ports = List.copyOf(ports);
    }

    public record Port(int index, String key, ItemStack item, FluidStack fluid, ResourceLocation chemical,
                       int amount, int capacity, ResourceLocation resource, long resourceAmount, long resourceCapacity) {
        public Port(int index, String key, ItemStack item, FluidStack fluid, ResourceLocation chemical,
                    int amount, int capacity) {
            this(index, key, item, fluid, chemical, amount, capacity, null, 0, 0);
        }
        public Port(int index, String key, ItemStack item, FluidStack fluid, int amount, int capacity) {
            this(index, key, item, fluid, null, amount, capacity);
        }
        public Port {
            item = item.copy();
            fluid = fluid.copy();
        }

        @Override public ItemStack item() { return item.copy(); }
        @Override public FluidStack fluid() { return fluid.copy(); }
        public boolean bindable() { return !key.isEmpty(); }
    }

    public static NeoForgeMachinePortPage resources(BlockEntity machine, int sample, Direction side, int page) {
        try {
            var unit = com.primordial.neoforge.resource.NeoForgeResourceUnits.machineUnit(sample);
            var state = machine.getBlockState();
            var found = com.primordial.neoforge.resource.NeoForgeResourcePortAccess.find(machine, side, unit);
            int total = found.size();
            int selected = Math.max(0, Math.min(page, Math.max(0, total - 1) / PAGE_SIZE));
            List<Port> ports = new ArrayList<>();
            for (int index = selected * PAGE_SIZE; index < Math.min(total, (selected + 1) * PAGE_SIZE); index++) {
                var port = found.get(index);
                long amount = Math.max(0, port.amount()), capacity = Math.max(0, port.capacity());
                String key = port.canReceive() || port.canExtract()
                        ? com.primordial.neoforge.resource.NeoForgeResourcePortAccess.key(machine, side, port) : "";
                ports.add(new Port(index, key, ItemStack.EMPTY, FluidStack.EMPTY, null, 0, 0,
                        unit, amount, capacity));
            }
            return com.primordial.neoforge.resource.NeoForgeResourcePortAccess.live(machine)
                    && state.equals(machine.getBlockState()) ? new NeoForgeMachinePortPage("native_resources", selected, total, ports) : EMPTY;
        } catch (RuntimeException | LinkageError unavailable) { return EMPTY; }
    }

    /** Sections match the legacy menu: item in/out, fluid in/out, then FE supply/recovery. */
    public static NeoForgeMachinePortPage read(@Nullable NeoForgeMachineProxy proxy, @Nullable BlockEntity machine,
                                               int section, @Nullable Direction side, int page) {
        if (proxy == null || section < 0 || section > 5 || (section < 4 && side == null)) return EMPTY;
        try {
            if (!NeoForgeMachineTargetAccess.live(machine)) return EMPTY;
            var state = machine.getBlockState();
            var items = section < 2 ? proxy.itemHandler(machine, side) : null;
            var fluids = section >= 2 && section < 4 ? proxy.fluidHandler(machine, side) : null;
            var energy = section >= 4 ? proxy.energyHandler(machine, side) : null;
            int total = Math.max(0, items != null ? items.getSlots() : fluids != null ? fluids.getTanks()
                    : energy != null ? 1 : 0);
            int selected = Math.max(0, Math.min(page, Math.max(0, total - 1) / PAGE_SIZE));
            int start = selected * PAGE_SIZE;
            int end = (int) Math.min((long) total, (long) start + PAGE_SIZE);
            List<Port> ports = new ArrayList<>(end - start);
            for (int index = start; index < end; index++) {
                if (items != null) {
                    ItemStack stack = items.getStackInSlot(index);
                    ItemStack icon = stack.copyWithCount(1);
                    ports.add(new Port(index, limited(proxy.itemSlotKey(machine, side, items, index)),
                            icon, FluidStack.EMPTY, stack.getCount(), Math.max(0, items.getSlotLimit(index))));
                } else if (fluids != null) {
                    // Generic multi-tank handlers cannot target writes, so show their contents without a binding key.
                    String key = proxy.targetFluidHandler(machine, side, fluids, index) == null ? ""
                            : limited(proxy.fluidTankKey(machine, side, fluids, index));
                    FluidStack fluid = fluids.getFluidInTank(index);
                    ports.add(new Port(index, key, ItemStack.EMPTY, fluid,
                            fluid.getAmount(), Math.max(0, fluids.getTankCapacity(index))));
                } else {
                    ports.add(new Port(0, limited(NeoForgeMachineTargetAccess.energyKey(proxy, machine, side, energy)),
                            ItemStack.EMPTY, FluidStack.EMPTY, Math.max(0, energy.getEnergyStored()),
                            Math.max(0, energy.getMaxEnergyStored())));
                }
            }
            if (!NeoForgeMachineTargetAccess.live(machine) || !state.equals(machine.getBlockState())
                    || (items != null && items.getSlots() != total)
                    || (fluids != null && fluids.getTanks() != total)) return EMPTY;
            return new NeoForgeMachinePortPage(proxy.id(), selected, total, ports);
        } catch (RuntimeException | LinkageError failure) {
            return EMPTY;
        }
    }

    private static String limited(String key) {
        return key != null && key.length() <= MachineTarget.MAX_KEY_LENGTH ? key : "";
    }
}
