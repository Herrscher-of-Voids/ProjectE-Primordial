package com.primordial.neoforge.machine;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.primordial.data.MachineTarget;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.service.EmcAmount;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.machine.NeoForgeMachineTargetTestFixture.TargetBlockEntity;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.util.MachineKey;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineTaskGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);
    private static final Direction SIDE = Direction.NORTH;
    private static final NeoForgeMachineProxy PROXY = NeoForgeCapabilityProxy.INSTANCE;

    private NeoForgeMachineTaskGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void itemTasksUseBoundSlotsAndSettleOnlyActualTransfers(GameTestHelper helper) {
        var machine = place(helper);
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String key = key(machine);
        try {
            var knowledge = knowledge(player);
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.TEN.pow(10));
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            knowledge.addKnowledge(ItemInfo.fromStack(diamond));
            var data = prepare(server, key, player.getUUID());
            var items = new ItemStackHandler(3) {
                private boolean delivered;
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (simulate) return super.insertItem(slot, stack, true);
                    if (delivered) return stack;
                    delivered = true;
                    if (stack.getCount() <= 2) return super.insertItem(slot, stack, false);
                    ItemStack tail = stack.copyWithCount(stack.getCount() - 2);
                    ItemStack refused = super.insertItem(slot, stack.copyWithCount(2), false);
                    tail.grow(refused.getCount());
                    return tail;
                }
            };
            machine.items[SIDE.ordinal()] = items;
            machine.invalidatePorts();
            items.setStackInSlot(0, new ItemStack(Items.DIRT, 3));
            items.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 7));
            sample(data, machine, player, 0, 1, diamond, 10, 1);
            var before = spendable(server, player);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(items.getStackInSlot(1).getCount() == 2,
                    "Machine task must use the bound slot and accept partial actual delivery");
            check(spendable(server, player).equals(
                    before.subtract(NeoForgeRecoveryPrices.itemPurchase(diamond).multiply(2))),
                    "Partial machine delivery must refund every unfilled reserved unit");
            NeoForgeMachineTaskExecutor.runAll(server);
            check(items.getStackInSlot(1).getCount() == 2, "Repeating a task sweep in one tick must not deliver twice");
            data.updateParameter(key, player.getUUID(), 8, 0, 0, 0);
            sample(data, machine, player, 1, 1, diamond, 1, 1);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(items.getStackInSlot(1).getCount() == 1, "Fixed recovery must extract exactly its configured amount");
            check(items.getStackInSlot(0).getCount() == 3 && items.getStackInSlot(2).getCount() == 7,
                    "Automatic purchase and recovery must leave adjacent slots untouched");
        } finally { cleanup(helper, server, key, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void keepLevelFillsMultipleTargetsAndRecoveryEmptiesThem(GameTestHelper helper) {
        var machine = place(helper);
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String key = key(machine);
        try {
            knowledge(player).setEmc(BigInteger.TEN.pow(10));
            ItemStack stone = new ItemStack(Items.STONE);
            knowledge(player).addKnowledge(ItemInfo.fromStack(stone));
            var data = prepare(server, key, player.getUUID());
            sample(data, machine, player, 0, 0, stone, 1, 1);
            data.replaceTargets(key, player.getUUID(), 0, 0, List.of(itemTarget(machine, 0), itemTarget(machine, 1)));
            data.updateParameter(key, player.getUUID(), 0, 0, 0, 1);
            NeoForgeMachineTaskExecutor.runAll(server);
            var items = machine.items[SIDE.ordinal()];
            check(items.getStackInSlot(0).getCount() == 64 && items.getStackInSlot(1).getCount() == 64,
                    "KEEP_LEVEL must fill each bound slot, matching Forge semantics rather than its fixed threshold");
            data.updateParameter(key, player.getUUID(), 8, 0, 0, 0);
            sample(data, machine, player, 1, 0, stone, 1, 1);
            data.replaceTargets(key, player.getUUID(), 1, 0, List.of(itemTarget(machine, 0), itemTarget(machine, 1)));
            data.updateParameter(key, player.getUUID(), 1, 0, 0, 1);
            NeoForgeMachineTaskScheduler.clear(server);
            var before = spendable(server, player);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(items.getStackInSlot(0).isEmpty() && items.getStackInSlot(1).isEmpty()
                            && spendable(server, player).equals(
                            before.add(NeoForgeRecoveryPrices.item(stone).multiply(128))),
                    "KEEP_LEVEL recovery must empty only bound matching slots and credit actual extraction");
        } finally { cleanup(helper, server, key, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void fluidAndEnergyTasksPreserveExactEmcAndRespectBlacklists(GameTestHelper helper) {
        var machine = place(helper);
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String key = key(machine);
        String ratio = NeoForgeServerConfig.FE_PER_EMC.get();
        var fluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        try {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            knowledge(player).setEmc(BigInteger.valueOf(10000));
            knowledge(player).addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample()));
            NeoForgeRecoveryKnowledge.recordFluid(server, player.getUUID(), ResourceLocation.parse("minecraft:water"));
            var data = prepare(server, key, player.getUUID());
            check(data.updateFluidFilter(key, player.getUUID(), 2, 0, new FluidStack(Fluids.WATER, 1), server.registryAccess())
                            && data.updateTarget(key, player.getUUID(), 2, 0, fluidTarget(machine), false),
                    "Fluid task setup must bind a real single-tank endpoint");
            data.updateParameter(key, player.getUUID(), 2, 0, 2, 125);
            data.updateParameter(key, player.getUUID(), 2, 0, 3, 1);
            data.updateParameter(key, player.getUUID(), 10, 0, 0, 1);
            data.updateTarget(key, player.getUUID(), 4, 0, energyTarget(machine), false);
            data.updateParameter(key, player.getUUID(), 4, 0, 2, 125);
            data.updateParameter(key, player.getUUID(), 4, 0, 3, 1);
            data.updateParameter(key, player.getUUID(), 4, 0, 4, 1);
            var before = spendable(server, player);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(machine.fluids[SIDE.ordinal()].getFluidInTank(0).getAmount() == 125
                            && machine.energy[SIDE.ordinal()].getEnergyStored() == 125,
                    "Machine tasks must deliver the configured fluid mB and FE to separate bound endpoints");
            check(spendable(server, player).equals(before
                            .subtract(NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).multiply(125))
                            .subtract(NeoForgeRecoveryPrices.energy().multiply(125))),
                    "Fractional fluid and FE purchases must debit their exact rational cost");
            data.updateParameter(key, player.getUUID(), 10, 0, 0, 0);
            data.updateParameter(key, player.getUUID(), 4, 0, 4, 0);
            check(data.updateFluidFilter(key, player.getUUID(), 3, 0, new FluidStack(Fluids.WATER, 1), server.registryAccess())
                            && data.updateTarget(key, player.getUUID(), 3, 0, fluidTarget(machine), false),
                    "Fluid recovery setup must use its bound endpoint");
            data.updateParameter(key, player.getUUID(), 3, 0, 0, 1);
            data.updateParameter(key, player.getUUID(), 11, 0, 0, 1);
            data.updateTarget(key, player.getUUID(), 5, 0, energyTarget(machine), false);
            data.updateParameter(key, player.getUUID(), 5, 0, 0, 1);
            data.updateParameter(key, player.getUUID(), 5, 0, 4, 1);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(machine.fluids[SIDE.ordinal()].getFluidInTank(0).isEmpty()
                            && machine.energy[SIDE.ordinal()].getEnergyStored() == 0
                            && spendable(server, player).equals(before),
                    "Actual fluid and FE recovery must restore the exact initial balance");
            machine.fluids[SIDE.ordinal()].fill(new FluidStack(Fluids.WATER, 50), IFluidHandler.FluidAction.EXECUTE);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:water"));
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(machine.fluids[SIDE.ordinal()].getFluidInTank(0).getAmount() == 50,
                    "A blacklisted machine resource must never be extracted");
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(ratio);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(fluidBlacklist);
            cleanup(helper, server, key, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void clippedLongEnergyWrappersUseSimulationForKeepAndFixedModes(GameTestHelper helper) {
        var machine = place(helper);
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String key = key(machine);
        String ratio = NeoForgeServerConfig.FE_PER_EMC.get();
        try {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            knowledge(player).setEmc(BigInteger.TEN.pow(10));
            knowledge(player).addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample()));
            var storage = new ClippedEnergyStorage();
            machine.energy[SIDE.ordinal()] = storage;
            machine.invalidatePorts();
            check(storage.getEnergyStored() == Integer.MAX_VALUE && storage.getMaxEnergyStored() == Integer.MAX_VALUE,
                    "The wrapper must expose equal clipped int readings despite having real internal space");
            var data = prepare(server, key, player.getUUID());
            data.updateTarget(key, player.getUUID(), 4, 0, energyTarget(machine), false);
            data.updateParameter(key, player.getUUID(), 4, 0, 0, 1);
            data.updateParameter(key, player.getUUID(), 4, 0, 4, 1);
            var before = spendable(server, player);
            var price = NeoForgeRecoveryPrices.energy();
            NeoForgeMachineTaskExecutor.runAll(server);
            check(storage.balance == ClippedEnergyStorage.INITIAL + Integer.MAX_VALUE
                            && spendable(server, player).equals(before.subtract(price.multiply(Integer.MAX_VALUE))),
                    "KEEP_LEVEL must trust real FE acceptance simulation rather than clipped capacity minus balance");
            data.updateParameter(key, player.getUUID(), 4, 0, 0, 0);
            data.updateParameter(key, player.getUUID(), 4, 0, 2, 125);
            data.updateParameter(key, player.getUUID(), 4, 0, 3, 1);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(storage.balance == ClippedEnergyStorage.INITIAL + Integer.MAX_VALUE + 125
                            && spendable(server, player).equals(before.subtract(price.multiply((long) Integer.MAX_VALUE + 125))),
                    "FIXED_RATE must still deliver only the configured amount through the clipped wrapper");
            data.updateParameter(key, player.getUUID(), 4, 0, 4, 0);
            data.updateTarget(key, player.getUUID(), 5, 0, energyTarget(machine), false);
            data.updateParameter(key, player.getUUID(), 5, 0, 0, 1);
            data.updateParameter(key, player.getUUID(), 5, 0, 4, 1);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(storage.balance == ClippedEnergyStorage.INITIAL + 125
                            && spendable(server, player).equals(before.subtract(price.multiply(125))),
                    "KEEP_LEVEL recovery must credit the real extracted FE without using the clipped stored reading");
            data.updateParameter(key, player.getUUID(), 5, 0, 0, 0);
            data.updateParameter(key, player.getUUID(), 5, 0, 2, 125);
            data.updateParameter(key, player.getUUID(), 5, 0, 3, 1);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(storage.balance == ClippedEnergyStorage.INITIAL && spendable(server, player).equals(before),
                    "FIXED_RATE recovery must preserve the configured amount and exact FE EMC round trip");
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(ratio);
            cleanup(helper, server, key, player);
        }
        helper.succeed();
    }

    private static final class ClippedEnergyStorage extends NeoForgeMachineTargetTestFixture.TestEnergyStorage {
        static final long INITIAL = 3_000_000_000L;
        private static final long CAPACITY = 10_000_000_000L;
        long balance = INITIAL;

        ClippedEnergyStorage() { super(1); }

        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, balance); }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canReceive() { return true; }
        @Override public boolean canExtract() { return true; }

        @Override public int receiveEnergy(int requested, boolean simulate) {
            int accepted = (int) Math.min(Math.max(0, requested), CAPACITY - balance);
            if (!simulate) balance += accepted;
            return accepted;
        }

        @Override public int extractEnergy(int requested, boolean simulate) {
            int extracted = (int) Math.min(Math.max(0, requested), balance);
            if (!simulate) balance -= extracted;
            return extracted;
        }
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void staleOrUnknownTargetsNeverChargeOrUseOldHandlers(GameTestHelper helper) {
        var machine = place(helper);
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String key = key(machine);
        try {
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.TEN.pow(10));
            ItemStack stone = new ItemStack(Items.STONE);
            var data = prepare(server, key, player.getUUID());
            sample(data, machine, player, 0, 1, stone, 4, 1);
            var before = spendable(server, player);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(machine.items[SIDE.ordinal()].getStackInSlot(1).isEmpty()
                            && spendable(server, player).equals(before),
                    "Unknown item tasks must not call transfer or debit EMC");
            knowledge(player).addKnowledge(ItemInfo.fromStack(stone));
            var handler = new ItemStackHandler(3) {
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (simulate) { machine.enabled = false; machine.invalidatePorts(); }
                    return super.insertItem(slot, stack, simulate);
                }
            };
            machine.items[SIDE.ordinal()] = handler;
            machine.invalidatePorts();
            data.updateTarget(key, player.getUUID(), 0, 0, itemTarget(machine, 1), false);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(handler.getStackInSlot(1).isEmpty() && spendable(server, player).equals(before),
                    "A capability invalidated during simulation must be re-resolved before execution and fully refunded");
            machine.enabled = true;
            machine.invalidatePorts();
            data.updateTarget(key, player.getUUID(), 0, 0, new MachineTarget(SIDE.ordinal(), 1, "stale-structure-key"), false);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(handler.getStackInSlot(1).isEmpty() && spendable(server, player).equals(before),
                    "A stale structural key must not silently rebind the task");
            helper.setBlock(POS, Blocks.AIR);
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(data.config(key) != null && spendable(server, player).equals(before),
                    "Absent targets must neither charge nor silently delete the owner's configuration");
        } finally { cleanup(helper, server, key, player); }
        helper.succeed();
    }

    // Manual sweep tests reset server timers; tick assertions need their own isolated batch.
    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_machine_item_ticks", timeoutTicks = 80)
    public static void serverTickRunsFixedIntervalAndPausesWithoutManualSweeps(GameTestHelper helper) {
        var machine = place(helper);
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String key = key(machine);
        var data = prepare(server, key, player.getUUID());
        knowledge(player).setEmc(BigInteger.TEN.pow(10));
        ItemStack stone = new ItemStack(Items.STONE);
        knowledge(player).addKnowledge(ItemInfo.fromStack(stone));
        sample(data, machine, player, 0, 1, stone, 1, 10);
        long started = helper.getLevel().getGameTime();
        helper.runAfterDelay(2, () -> {
            try {
                int first = machine.items[SIDE.ordinal()].getStackInSlot(1).getCount();
                check(first <= 1, "Fixed mode must not repeatedly deliver on every tick before its interval");
                helper.runAfterDelay(14, () -> {
                    try {
                        int after = machine.items[SIDE.ordinal()].getStackInSlot(1).getCount();
                        check(after >= 1 && after <= 2 && helper.getLevel().getGameTime() > started,
                                "The production ServerTick event must execute fixed-interval rules without manual sweeps");
                        data.updateParameter(key, player.getUUID(), 8, 0, 0, 0);
                        helper.runAfterDelay(12, () -> {
                            try {
                                check(machine.items[SIDE.ordinal()].getStackInSlot(1).getCount() == after,
                                        "Pausing the master switch must stop future automatic tick deliveries");
                                helper.succeed();
                            } finally { cleanup(helper, server, key, player); }
                        });
                    } catch (RuntimeException | Error failure) { cleanup(helper, server, key, player); throw failure; }
                });
            } catch (RuntimeException | Error failure) { cleanup(helper, server, key, player); throw failure; }
        });
    }

    private static TargetBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, NeoForgeMachineTargetTestFixture.BLOCK);
        return (TargetBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
    }

    private static String key(TargetBlockEntity machine) {
        return MachineKey.of(machine.getLevel().dimension(), machine.getBlockPos());
    }

    private static NeoForgeMachineConfigSavedData prepare(MinecraftServer server, String key, UUID owner) {
        NeoForgeMachineTaskScheduler.clear(server);
        var data = NeoForgeMachineConfigSavedData.get(server);
        check(data.getOrCreate(key, owner) != null, "Task owner config must be created");
        return data;
    }

    private static void sample(NeoForgeMachineConfigSavedData data, TargetBlockEntity machine, ServerPlayer player,
                               int section, int slot, ItemStack filter, int amount, int interval) {
        String key = key(machine);
        check(data.updateItemFilter(key, player.getUUID(), section, 0, filter, player.server.registryAccess())
                        && data.updateTarget(key, player.getUUID(), section, 0, itemTarget(machine, slot), false),
                "Task setup must bind an item sample to a real port");
        data.updateParameter(key, player.getUUID(), section, 0, 2, amount);
        data.updateParameter(key, player.getUUID(), section, 0, 3, interval);
        data.updateParameter(key, player.getUUID(), section + 8, 0, 0, 1);
    }

    private static MachineTarget itemTarget(TargetBlockEntity machine, int slot) {
        return new MachineTarget(SIDE.ordinal(), slot, PROXY.itemSlotKey(machine, SIDE, machine.items[SIDE.ordinal()], slot));
    }

    private static MachineTarget fluidTarget(TargetBlockEntity machine) {
        return new MachineTarget(SIDE.ordinal(), 0, PROXY.fluidTankKey(machine, SIDE, machine.fluids[SIDE.ordinal()], 0));
    }

    private static MachineTarget energyTarget(TargetBlockEntity machine) {
        return new MachineTarget(SIDE.ordinal(), 0,
                NeoForgeMachineTargetAccess.energyKey(PROXY, machine, SIDE, machine.energy[SIDE.ordinal()]));
    }

    private static EmcAmount spendable(MinecraftServer server, ServerPlayer player) {
        var data = NeoForgeEmcSavedData.get(server);
        return EmcAmount.of(knowledge(player).getEmc()).add(data.pending(player.getUUID()))
                .subtract(EmcAmount.of(data.debit(player.getUUID())));
    }

    private static IKnowledgeProvider knowledge(ServerPlayer player) {
        var result = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (result == null) throw new GameTestAssertException("Missing ProjectE knowledge capability");
        return result;
    }

    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "machine-task");
        var info = ClientInformation.createDefault();
        var player = server.getPlayerList().getPlayerForLogin(profile, info);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player,
                    new net.minecraft.server.network.CommonListenerCookie(profile, 0, info, false, ConnectionType.NEOFORGE));
            return player;
        } catch (RuntimeException | Error failure) { channel.finishAndReleaseAll(); throw failure; }
    }

    private static void cleanup(GameTestHelper helper, MinecraftServer server, String key, ServerPlayer player) {
        var data = NeoForgeMachineConfigSavedData.get(server);
        for (int section : new int[] {8, 9, 10, 11, 4, 5})
            data.updateParameter(key, player.getUUID(), section, 0, section >= 8 ? 0 : 4, 0);
        helper.setBlock(POS, Blocks.AIR);
        NeoForgeMachineTaskScheduler.clear(server);
        try { server.getPlayerList().remove(player); }
        finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
