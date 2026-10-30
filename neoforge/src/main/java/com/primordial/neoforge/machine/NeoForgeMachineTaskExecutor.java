package com.primordial.neoforge.machine;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.primordial.data.ConfigLimits;
import com.primordial.data.MachineTarget;
import com.primordial.neoforge.compat.NeoForgeMachineChemicalTasks;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Forge-compatible fixed-rate and fill/empty tasks, using the common exact EMC transactions. */
public final class NeoForgeMachineTaskExecutor {
    private static final NeoForgeMachineProxy PROXY = NeoForgeCapabilityProxy.INSTANCE;
    private static final String[] LISTS = {"ii", "io", "fi", "fo"};
    private static final String[] MASTERS = {"iie", "ioe", "fie", "foe"};

    private NeoForgeMachineTaskExecutor() {}

    public static void runAll(MinecraftServer server) {
        if (!NeoForgeMachineTaskScheduler.beginTick(server)) return;
        try {
            NeoForgeMachineConfigSavedData data = NeoForgeMachineConfigSavedData.get(server);
            var configs = data.snapshots();
            NeoForgeMachineTaskScheduler.retain(server, configs.keySet());
            for (var entry : configs.entrySet()) {
                String key = entry.getKey();
                try {
                    CompoundTag config = entry.getValue();
                    if (!config.hasUUID("owner")) continue;
                    BlockEntity machine = NeoForgeMachineConfiguratorAccess.findLoaded(server, key);
                    if (machine == null) continue;
                    var state = machine.getBlockState();
                    UUID owner = config.getUUID("owner");
                    BooleanSupplier current = () -> data.canEdit(key, owner) && config.equals(data.config(key))
                            && NeoForgeMachineConfiguratorAccess.findLoaded(server, key) == machine
                            && state.equals(machine.getBlockState());
                    long time = machine.getLevel().getGameTime();
                    for (int section = 0; section < 4 && current.getAsBoolean(); section++)
                        runSamples(server, owner, key, config, section, machine, time, current);
                    if (current.getAsBoolean()) runEnergy(server, owner, key, config, machine, time, current);
                    if (current.getAsBoolean()) runResources(server, owner, key, config, machine, time, current);
                    NeoForgeMachineTaskScheduler.succeeded(server, key);
                } catch (RuntimeException | LinkageError failure) {
                    NeoForgeMachineTaskScheduler.failed(server, key, failure);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            NeoForgeMachineTaskScheduler.failed(server, "configuration store", failure);
        } finally {
            NeoForgeMachineTaskScheduler.endTick(server);
        }
    }

    private static void runSamples(MinecraftServer server, UUID owner, String key, CompoundTag config,
                                   int section, BlockEntity machine, long time, BooleanSupplier current) {
        if (!config.contains(MASTERS[section], Tag.TAG_BYTE) || !config.getBoolean(MASTERS[section])
                || !config.contains(LISTS[section], Tag.TAG_LIST)) return;
        ListTag samples = config.getList(LISTS[section], Tag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(9, samples.size()); index++) {
            CompoundTag sample = samples.getCompound(index);
            if (!validSample(sample) || !sample.getBoolean("e")) continue;
            boolean keep = sample.getString("m").equals("KEEP_LEVEL");
            if (!keep && !NeoForgeMachineTaskScheduler.intervalDue(server, key, section * 9 + index,
                    interval(sample, "it", "i"), time)) continue;
            long requested = keep ? Long.MAX_VALUE : ConfigLimits.amount(sample.getInt("t"), 1);
            for (MachineTarget target : targets(sample)) {
                if (!current.getAsBoolean()) return;
                boolean supply = section % 2 == 0;
                if (section < 2) runItem(server, owner, sample, machine, target, supply, requested, current);
                else if (sample.contains("g", Tag.TAG_COMPOUND) && !sample.contains("f"))
                    NeoForgeMachineChemicalTasks.run(server, owner, sample, PROXY, machine, target, supply, requested);
                else if (!sample.contains("g"))
                    runFluid(server, owner, sample, machine, target, supply, requested, current);
            }
        }
    }

    private static void runItem(MinecraftServer server, UUID owner, CompoundTag sample, BlockEntity machine,
                                MachineTarget target, boolean supply, long requested, BooleanSupplier current) {
        if (!sample.contains("f", Tag.TAG_COMPOUND)) return;
        ItemStack filter = ItemStack.parseOptional(server.registryAccess(), sample.getCompound("f"));
        if (filter.isEmpty()) return;
        IItemHandler handler = NeoForgeMachineTargetAccess.items(PROXY, machine, target);
        if (handler == null) return;
        int wanted = (int) Math.min(Integer.MAX_VALUE, requested);
        if (supply) {
            int offered = itemSpace(handler, filter, wanted);
            int count = offered == 0 ? 0 : accepted(filter, offered, handler.insertItem(0, filter.copyWithCount(offered), true));
            if (count <= 0 || !current.getAsBoolean()) return;
            NeoForgeResourceTrades.purchaseItem(server, owner, filter, count,
                    units -> insertItem(machine, target, filter, (int) units, current));
        } else {
            ItemStack stored = handler.getStackInSlot(0);
            if (!ItemStack.isSameItemSameComponents(stored, filter) || !current.getAsBoolean()) return;
            int amount = Math.min(wanted, stored.getCount());
            if (amount <= 0) return;
            NeoForgeResourceTrades.reclaimItem(server, owner, filter, amount, units -> {
                if (!current.getAsBoolean()) return 0;
                IItemHandler fresh = NeoForgeMachineTargetAccess.items(PROXY, machine, target);
                if (fresh == null || !ItemStack.isSameItemSameComponents(fresh.getStackInSlot(0), filter)
                        || !current.getAsBoolean()) return 0;
                IItemHandler latest = NeoForgeMachineTargetAccess.items(PROXY, machine, target);
                if (latest == null || !current.getAsBoolean()) return 0;
                ItemStack actual = latest.extractItem(0, (int) units, false);
                return ItemStack.isSameItemSameComponents(actual, filter) ? actual.getCount() : 0;
            });
        }
    }

    private static int insertItem(BlockEntity machine, MachineTarget target, ItemStack filter, int requested,
                                  BooleanSupplier current) {
        int remaining = requested;
        // Match Forge's bounded retries for large inventories that accept only one stack per call.
        for (int pass = 0; pass < 64 && remaining > 0; pass++) {
            if (!current.getAsBoolean()) break;
            IItemHandler fresh = NeoForgeMachineTargetAccess.items(PROXY, machine, target);
            if (fresh == null) break;
            int offered = itemSpace(fresh, filter, remaining);
            if (offered <= 0 || !current.getAsBoolean()) break;
            IItemHandler latest = NeoForgeMachineTargetAccess.items(PROXY, machine, target);
            if (latest == null || !current.getAsBoolean()) break;
            int actual = accepted(filter, offered, latest.insertItem(0, filter.copyWithCount(offered), false));
            remaining -= actual;
            if (actual == 0) break;
        }
        return requested - remaining;
    }

    private static int itemSpace(IItemHandler handler, ItemStack filter, int requested) {
        ItemStack stored = handler.getStackInSlot(0);
        if (!stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, filter)) return 0;
        return (int) Math.min(requested, Math.max(0L,
                (long) handler.getSlotLimit(0) - (stored.isEmpty() ? 0 : stored.getCount())));
    }

    private static int accepted(ItemStack filter, int offered, ItemStack remainder) {
        if (remainder == null || remainder.getCount() < 0 || remainder.getCount() > offered
                || !remainder.isEmpty() && !ItemStack.isSameItemSameComponents(filter, remainder))
            throw new IllegalStateException("Machine item handler returned an invalid remainder");
        return offered - remainder.getCount();
    }

    private static void runFluid(MinecraftServer server, UUID owner, CompoundTag sample, BlockEntity machine,
                                 MachineTarget target, boolean supply, long requested, BooleanSupplier current) {
        FluidStack filter = fluid(server, sample.getCompound("f"));
        if (filter.isEmpty()) return;
        IFluidHandler handler = NeoForgeMachineTargetAccess.fluids(PROXY, machine, target);
        if (handler == null) return;
        int wanted = (int) Math.min(Integer.MAX_VALUE, requested);
        if (supply) {
            int offered = fluidSpace(handler, wanted);
            int amount = offered == 0 ? 0 : Math.max(0, Math.min(offered,
                    handler.fill(filter.copyWithAmount(offered), IFluidHandler.FluidAction.SIMULATE)));
            if (amount <= 0 || !current.getAsBoolean()) return;
            NeoForgeResourceTrades.purchaseFluid(server, owner, filter, amount, units -> {
                if (!current.getAsBoolean()) return 0;
                IFluidHandler fresh = NeoForgeMachineTargetAccess.fluids(PROXY, machine, target);
                if (fresh == null) return 0;
                int offeredNow = fluidSpace(fresh, (int) units);
                if (offeredNow <= 0 || !current.getAsBoolean()) return 0;
                IFluidHandler latest = NeoForgeMachineTargetAccess.fluids(PROXY, machine, target);
                return latest == null || !current.getAsBoolean() ? 0
                        : latest.fill(filter.copyWithAmount(offeredNow), IFluidHandler.FluidAction.EXECUTE);
            });
        } else {
            FluidStack stored = handler.getFluidInTank(0);
            if (!FluidStack.isSameFluidSameComponents(stored, filter) || !current.getAsBoolean()) return;
            int amount = Math.min(wanted, stored.getAmount());
            if (amount <= 0) return;
            NeoForgeResourceTrades.reclaimFluid(server, owner, filter, amount, units -> {
                if (!current.getAsBoolean()) return 0;
                IFluidHandler fresh = NeoForgeMachineTargetAccess.fluids(PROXY, machine, target);
                if (fresh == null || !current.getAsBoolean()) return 0;
                FluidStack storedNow = fresh.getFluidInTank(0);
                if (!FluidStack.isSameFluidSameComponents(storedNow, filter) || !current.getAsBoolean()) return 0;
                int bounded = Math.min((int) units, storedNow.getAmount());
                if (bounded <= 0 || !current.getAsBoolean()) return 0;
                IFluidHandler latest = NeoForgeMachineTargetAccess.fluids(PROXY, machine, target);
                if (latest == null || !current.getAsBoolean()) return 0;
                FluidStack actual = latest.drain(filter.copyWithAmount(bounded), IFluidHandler.FluidAction.EXECUTE);
                return FluidStack.isSameFluidSameComponents(actual, filter) ? actual.getAmount() : 0;
            });
        }
    }

    private static int fluidSpace(IFluidHandler handler, int requested) {
        return (int) Math.min(requested, Math.max(0L,
                (long) handler.getTankCapacity(0) - handler.getFluidInTank(0).getAmount()));
    }

    private static FluidStack fluid(MinecraftServer server, CompoundTag tag) {
        if (tag.contains("NeoForge")) return tag.contains("NeoForge", Tag.TAG_COMPOUND)
                ? FluidStack.parseOptional(server.registryAccess(), tag.getCompound("NeoForge")) : FluidStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("FluidName"));
        if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) return FluidStack.EMPTY;
        FluidStack stack = new FluidStack(BuiltInRegistries.FLUID.get(id), 1);
        if (tag.contains("Tag", Tag.TAG_COMPOUND)) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag.getCompound("Tag")));
        return stack;
    }

    private static void runEnergy(MinecraftServer server, UUID owner, String key, CompoundTag config,
                                  BlockEntity machine, long time, BooleanSupplier current) {
        if (!config.contains("en", Tag.TAG_COMPOUND)) return;
        CompoundTag energy = config.getCompound("en");
        for (boolean supply : new boolean[]{true, false}) {
            String enabled = supply ? "se" : "re", mode = supply ? "sm" : "rm", amount = supply ? "sa" : "ra";
            if (!energy.contains(enabled, Tag.TAG_BYTE) || !energy.getBoolean(enabled)
                    || !validMode(energy, mode) || energy.contains(amount) && !energy.contains(amount, Tag.TAG_INT)
                    || !validInterval(energy, supply ? "sit" : "rit", supply ? "si" : "ri")) continue;
            boolean keep = energy.getString(mode).equals("KEEP_LEVEL");
            if (!keep && !NeoForgeMachineTaskScheduler.intervalDue(server, key, supply ? 36 : 37,
                    interval(energy, supply ? "sit" : "rit", supply ? "si" : "ri"), time)) continue;
            MachineTarget target = MachineTarget.load(energy.getCompound(supply ? "supplyPort" : "recoverPort"));
            var handler = NeoForgeMachineTargetAccess.energy(PROXY, machine, target);
            if (handler == null || !current.getAsBoolean()) continue;
            int wanted = keep ? Integer.MAX_VALUE : ConfigLimits.amount(energy.contains(amount)
                    ? energy.getInt(amount) : energy.getInt(supply ? "sr" : "rr"), 1);
            int simulated = simulatedEnergy(handler, wanted, supply);
            if (simulated <= 0 || !current.getAsBoolean()) continue;
            java.util.function.LongUnaryOperator transfer = units -> {
                if (!current.getAsBoolean()) return 0;
                var fresh = NeoForgeMachineTargetAccess.energy(PROXY, machine, target);
                if (fresh == null) return 0;
                int bounded = simulatedEnergy(fresh, (int) units, supply);
                if (bounded <= 0 || !current.getAsBoolean()) return 0;
                var latest = NeoForgeMachineTargetAccess.energy(PROXY, machine, target);
                return latest == null || !current.getAsBoolean() ? 0 : supply
                        ? latest.receiveEnergy(bounded, false) : latest.extractEnergy(bounded, false);
            };
            if (supply) NeoForgeResourceTrades.purchaseEnergy(server, owner, simulated, transfer);
            else NeoForgeResourceTrades.reclaimEnergy(server, owner, simulated, transfer);
        }
    }

    private static int simulatedEnergy(IEnergyStorage handler, int requested, boolean supply) {
        if (requested <= 0 || supply && !handler.canReceive() || !supply && !handler.canExtract()) return 0;
        // FE utility wrappers may clip both displayed balance and capacity to int while retaining long storage.
        return Math.max(0, Math.min(requested, supply ? handler.receiveEnergy(requested, true)
                : handler.extractEnergy(requested, true)));
    }

    private static boolean validSample(CompoundTag sample) {
        return sample.contains("e", Tag.TAG_BYTE) && validMode(sample, "m")
                && (!sample.contains("t") || sample.contains("t", Tag.TAG_INT)) && validInterval(sample, "it", "i");
    }

    private static void runResources(MinecraftServer server, UUID owner, String key, CompoundTag config,
                                     BlockEntity machine, long time, BooleanSupplier current) {
        for (int section = 6; section <= 7; section++) {
            String listKey = section == 6 ? "ri" : "ro";
            if (!config.contains(listKey, Tag.TAG_LIST)) continue;
            ListTag samples = config.getList(listKey, Tag.TAG_COMPOUND);
            for (int index = 0; index < Math.min(6, samples.size()); index++) {
                CompoundTag sample = samples.getCompound(index);
                ResourceLocation unit = com.primordial.neoforge.resource.NeoForgeResourceUnits.machineUnit(index);
                if (!sample.contains("e", Tag.TAG_BYTE) || !sample.getBoolean("e") || !validMode(sample, "m")
                        || sample.contains("a") && !sample.contains("a", Tag.TAG_INT)
                        || !validInterval(sample, "it", "i") || sample.contains("unit")
                        && (!sample.contains("unit", Tag.TAG_STRING) || !unit.toString().equals(sample.getString("unit")))) continue;
                boolean keep = sample.getString("m").equals("KEEP_LEVEL");
                if (!keep && !NeoForgeMachineTaskScheduler.intervalDue(server, key, 38 + (section - 6) * 6 + index,
                        sample.contains("it") ? ConfigLimits.intervalTicks(sample.getInt("it")) : 20, time)) continue;
                long requested = keep ? Long.MAX_VALUE : ConfigLimits.amount(sample.contains("a") ? sample.getInt("a") : 1000, 1);
                boolean supply = section == 6;
                for (MachineTarget target : targets(sample)) {
                    if (!current.getAsBoolean()) return;
                    var port = com.primordial.neoforge.resource.NeoForgeResourcePortAccess.resolve(machine, target, unit);
                    if (port == null || supply && !port.canReceive() || !supply && !port.canExtract()) continue;
                    long simulated = boundedResource(requested, supply ? port.receive(requested, true) : port.extract(requested, true));
                    if (simulated <= 0 || !current.getAsBoolean()) continue;
                    java.util.function.LongUnaryOperator transfer = units -> {
                        if (!current.getAsBoolean()) return 0;
                        var fresh = com.primordial.neoforge.resource.NeoForgeResourcePortAccess.resolve(machine, target, unit);
                        if (fresh == null || supply && !fresh.canReceive() || !supply && !fresh.canExtract()) return 0;
                        long amount = boundedResource(units, supply ? fresh.receive(units, true) : fresh.extract(units, true));
                        if (amount <= 0 || !current.getAsBoolean()) return 0;
                        var latest = com.primordial.neoforge.resource.NeoForgeResourcePortAccess.resolve(machine, target, unit);
                        return latest == null || !current.getAsBoolean() ? 0
                                : supply ? latest.receive(amount, false) : latest.extract(amount, false);
                    };
                    if (supply) NeoForgeResourceTrades.purchaseResource(server, owner, unit, simulated, transfer);
                    else NeoForgeResourceTrades.reclaimResource(server, owner, unit, simulated, transfer);
                }
            }
        }
    }

    private static long boundedResource(long requested, long actual) {
        if (actual < 0 || actual > requested) throw new IllegalStateException("Invalid native resource simulation amount");
        return actual;
    }

    private static boolean validMode(CompoundTag tag, String key) {
        return !tag.contains(key) || tag.contains(key, Tag.TAG_STRING)
                && (tag.getString(key).equals("FIXED_RATE") || tag.getString(key).equals("KEEP_LEVEL"));
    }

    private static boolean validInterval(CompoundTag tag, String ticks, String seconds) {
        return (!tag.contains(ticks) || tag.contains(ticks, Tag.TAG_INT))
                && (!tag.contains(seconds) || tag.contains(seconds, Tag.TAG_INT));
    }

    private static int interval(CompoundTag tag, String ticks, String seconds) {
        return tag.contains(ticks) ? ConfigLimits.intervalTicks(tag.getInt(ticks))
                : ConfigLimits.intervalTicks((int) Math.min(Integer.MAX_VALUE,
                        Math.max(1L, (long) tag.getInt(seconds) * 20L)));
    }

    private static List<MachineTarget> targets(CompoundTag sample) {
        LinkedHashSet<MachineTarget> result = new LinkedHashSet<>();
        if (sample.contains("targets")) {
            if (!(sample.get("targets") instanceof ListTag list) || list.size() > 512
                    || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) return List.of();
            for (int index = 0; index < list.size(); index++) {
                MachineTarget target = MachineTarget.load(list.getCompound(index));
                if (target.bound()) result.add(target);
            }
        }
        if (result.isEmpty()) {
            MachineTarget target = MachineTarget.load(sample.getCompound("target"));
            if (target.bound()) result.add(target);
        }
        return List.copyOf(result);
    }
}
