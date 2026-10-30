package com.primordial.neoforge.machine;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.primordial.data.MachineTarget;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.api.MediaKnowledgeApi;
import com.primordial.neoforge.compat.NeoForgeMachineChemicalTasks;
import com.primordial.neoforge.compat.mekanism.NeoForgeMachineChemicalAccess;
import com.primordial.neoforge.machine.NeoForgeMachineTargetTestFixture.TargetBlockEntity;
import com.primordial.util.MachineKey;
import io.netty.channel.embedded.EmbeddedChannel;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Registered by the optional Mekanism test generator; absent from the base test discovery path. */
public final class NeoForgeMachineChemicalTaskGameTests {
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");
    private static final ResourceLocation OXYGEN = ResourceLocation.parse("mekanism:oxygen");
    private static final NeoForgeCapabilityProxy PROXY = NeoForgeCapabilityProxy.INSTANCE;

    private NeoForgeMachineChemicalTaskGameTests() {}

    public static List<TestFunction> tests() {
        return List.of(test("chemical_tasks_charge_only_actual_selected_tank_transfer", NeoForgeMachineChemicalTaskGameTests::selectedTankTransfer),
                test("chemical_tasks_require_knowledge_owner_and_current_structure", NeoForgeMachineChemicalTaskGameTests::authorizationAndStructure),
                test("chemical_tasks_recheck_configuration_and_fresh_handler", NeoForgeMachineChemicalTaskGameTests::configurationAndFreshHandler),
                test("chemical_tasks_execute_saved_rules_and_isolate_fluid_mode", NeoForgeMachineChemicalTaskGameTests::savedRulesDispatch));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> action) {
        return new TestFunction("machine_chemical_tasks", "machine_chemical_tasks." + name,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true, action);
    }

    private static void selectedTankTransfer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        try {
            TargetBlockEntity machine = setup(helper, owner);
            MutableChemicalHandler handler = new MutableChemicalHandler(4);
            handler.maxInsert = 3;
            handler.maxExtract = 2;
            machine.chemicalHandler = handler;
            MachineTarget target = target(machine);
            check(MediaKnowledgeApi.learn(server, owner.getUUID(), MediaKnowledgeApi.Media.GAS, HYDROGEN), "Hydrogen must become learned");
            IKnowledgeProvider knowledge = knowledge(owner);
            BigInteger before = knowledge.getEmc();

            check(run(owner, machine, target, true, Long.MAX_VALUE) == 3
                            && handler.stacks[1].getAmount() == 7 && handler.stacks[0].getAmount() == 40
                            && knowledge.getEmc().equals(before.subtract(BigInteger.valueOf(3))),
                    "A long-capacity tank must charge only the partial real insertion and leave tank zero intact");
            check(run(owner, machine, target, false, Long.MAX_VALUE) == 2
                            && handler.stacks[1].getAmount() == 5 && handler.stacks[0].getAmount() == 40
                            && knowledge.getEmc().equals(before.subtract(BigInteger.ONE)),
                    "Reclamation must credit only the real selected-tank extraction");
        } finally { logout(server, owner); }
        helper.succeed();
    }

    private static void authorizationAndStructure(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        try {
            TargetBlockEntity machine = setup(helper, owner);
            MutableChemicalHandler handler = new MutableChemicalHandler(4);
            machine.chemicalHandler = handler;
            MachineTarget target = target(machine);
            BigInteger before = knowledge(owner).getEmc();
            for (CompoundTag invalid : List.of(
                    sampleWithoutAmount(), sampleWithAmount(0), sampleWithAmount(-1), sampleWithStringAmount())) {
                check(NeoForgeMachineChemicalTasks.run(server, owner.getUUID(), invalid, PROXY, machine, target, true, 6) == 0,
                        "Missing, nonnumeric, zero, and negative chemical sample amounts must be rejected");
            }
            check(handler.simulatedInserts == 0 && handler.stacks[1].getAmount() == 4 && knowledge(owner).getEmc().equals(before),
                    "Invalid sample amounts must neither transfer chemicals nor change EMC");
            check(run(owner, machine, target, true, 6) == 0 && handler.stacks[1].getAmount() == 4
                            && knowledge(owner).getEmc().equals(before),
                    "Unknown chemicals cannot be supplied or charged");
            MediaKnowledgeApi.learn(server, owner.getUUID(), MediaKnowledgeApi.Media.GAS, HYDROGEN);
            check(NeoForgeMachineChemicalTasks.run(server, UUID.randomUUID(), filter(), PROXY, machine, target, true, 6) == 0,
                    "A non-owner cannot automate an owned configuration");
            MachineTarget wrong = new MachineTarget(target.side(), 0, target.key());
            check(run(owner, machine, wrong, false, 6) == 0, "A different selected tank must invalidate its key");
            handler.tanks = 3;
            check(run(owner, machine, target, true, 6) == 0, "Changed tank structure must invalidate a bound port");
            handler.tanks = 2;
            machine.closedSide = Direction.NORTH;
            machine.invalidatePorts();
            check(run(owner, machine, target, false, 6) == 0 && handler.stacks[1].getAmount() == 4
                            && handler.stacks[0].getAmount() == 40 && knowledge(owner).getEmc().equals(before),
                    "Missing faces must not fall back to another side or modify either tank");
        } finally { logout(server, owner); }
        helper.succeed();
    }

    private static void configurationAndFreshHandler(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        try {
            TargetBlockEntity machine = setup(helper, owner);
            MediaKnowledgeApi.learn(server, owner.getUUID(), MediaKnowledgeApi.Media.GAS, HYDROGEN);
            MutableChemicalHandler original = new MutableChemicalHandler(4);
            machine.chemicalHandler = original;
            MachineTarget target = target(machine);
            BigInteger before = knowledge(owner).getEmc();
            String key = MachineKey.of(machine.getLevel().dimension(), machine.getBlockPos());
            original.simulationChange = () -> NeoForgeMachineConfigSavedData.get(server)
                    .updateParameter(key, owner.getUUID(), 2, 0, 4, 1);
            check(run(owner, machine, target, true, 6) == 0 && original.stacks[1].getAmount() == 4
                            && knowledge(owner).getEmc().equals(before),
                    "Configuration mutation during simulation must reject the stale task without charging");

            MutableChemicalHandler replacement = new MutableChemicalHandler(9);
            replacement.maxInsert = 2;
            original.simulationChange = () -> {
                machine.chemicalHandler = replacement;
                machine.invalidatePorts();
            };
            check(run(owner, machine, target, true, 6) == 2 && original.stacks[1].getAmount() == 4
                            && replacement.stacks[1].getAmount() == 11
                            && knowledge(owner).getEmc().equals(before.subtract(BigInteger.valueOf(2))),
                    "Real delivery must re-resolve the live handler and refund its unaccepted amount");

            replacement.simulationChange = () -> { machine.closedSide = Direction.NORTH; machine.invalidatePorts(); };
            BigInteger closedBalance = knowledge(owner).getEmc();
            check(run(owner, machine, target, true, 6) == 0 && replacement.stacks[1].getAmount() == 11
                            && knowledge(owner).getEmc().equals(closedBalance),
                    "A disappearing capability after simulation must fully refund the reserved purchase");

            machine.closedSide = null;
            replacement.simulationChange = null;
            check(dataEdit(server, owner, key, 1), "Restore a valid sample after the earlier configuration change");
            int simulatedBefore = replacement.simulatedInserts;
            replacement.readChange = () -> machine.closedSide = Direction.NORTH;
            BigInteger staleReadBalance = knowledge(owner).getEmc();
            check(run(owner, machine, target, true, 6) == 0
                            && replacement.simulatedInserts == simulatedBefore
                            && replacement.stacks[1].getAmount() == 11
                            && knowledge(owner).getEmc().equals(staleReadBalance),
                    "A capability getter that invalidates the bound face must be detected before simulation");

            machine.closedSide = null;
            replacement.readChange = null;
            int simulatedExtractsBefore = replacement.simulatedExtracts;
            replacement.readChange = () -> machine.closedSide = Direction.NORTH;
            BigInteger staleExtractBalance = knowledge(owner).getEmc();
            check(run(owner, machine, target, false, 6) == 0
                            && replacement.simulatedExtracts == simulatedExtractsBefore
                            && replacement.stacks[1].getAmount() == 11
                            && knowledge(owner).getEmc().equals(staleExtractBalance),
                    "A capability getter that invalidates recovery must be detected before extraction simulation");
        } finally { logout(server, owner); }
        helper.succeed();
    }

    private static void savedRulesDispatch(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        String key = null;
        try {
            TargetBlockEntity machine = setup(helper, owner);
            MutableChemicalHandler handler = new MutableChemicalHandler(4);
            machine.chemicalHandler = handler;
            MachineTarget target = target(machine);
            key = MachineKey.of(machine.getLevel().dimension(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            MediaKnowledgeApi.learn(server, owner.getUUID(), MediaKnowledgeApi.Media.GAS, HYDROGEN);
            check(data.updateChemicalFilter(key, owner.getUUID(), 2, 0, HYDROGEN)
                            && data.replaceTargets(key, owner.getUUID(), 2, 0, List.of(target))
                            && data.updateParameter(key, owner.getUUID(), 2, 0, 0, 0)
                            && data.updateParameter(key, owner.getUUID(), 2, 0, 2, 2)
                            && data.updateParameter(key, owner.getUUID(), 2, 0, 3, 1)
                            && data.updateParameter(key, owner.getUUID(), 10, 0, 0, 1),
                    "Fixed chemical input and its master must be persisted through production configuration APIs");
            check(data.updateFluidFilter(key, owner.getUUID(), 2, 1,
                            new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1), server.registryAccess())
                            && data.replaceTargets(key, owner.getUUID(), 2, 1, List.of(target))
                            && data.updateParameter(key, owner.getUUID(), 2, 1, 0, 1),
                    "A fluid rule may retain a stale chemical target but must not execute in chemical mode");
            BigInteger before = knowledge(owner).getEmc();
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(handler.stacks[1].getAmount() == 6 && handler.stacks[0].getAmount() == 40
                            && knowledge(owner).getEmc().equals(before.subtract(BigInteger.valueOf(2))),
                    "The production executor must dispatch fixed chemical supply while ignoring the fluid rule's incompatible port");
            NeoForgeMachineTaskExecutor.runAll(server);
            check(handler.stacks[1].getAmount() == 6, "Repeating the executor in one server tick must not transfer twice");
            check(data.updateParameter(key, owner.getUUID(), 10, 0, 0, 0)
                            && data.updateChemicalFilter(key, owner.getUUID(), 3, 0, HYDROGEN)
                            && data.replaceTargets(key, owner.getUUID(), 3, 0, List.of(target))
                            && data.updateParameter(key, owner.getUUID(), 3, 0, 0, 1)
                            && data.updateParameter(key, owner.getUUID(), 11, 0, 0, 1),
                    "The output master and KEEP_LEVEL chemical rule must be editable through production APIs");
            NeoForgeMachineTaskScheduler.clear(server);
            NeoForgeMachineTaskExecutor.runAll(server);
            check(handler.stacks[1].isEmpty() && handler.stacks[0].getAmount() == 40
                            && knowledge(owner).getEmc().equals(before.add(BigInteger.valueOf(4))),
                    "The executor must empty only the selected hydrogen tank and settle exactly its six reclaimed units");
        } finally {
            if (key != null) {
                var data = NeoForgeMachineConfigSavedData.get(server);
                data.updateParameter(key, owner.getUUID(), 10, 0, 0, 0);
                data.updateParameter(key, owner.getUUID(), 11, 0, 0, 0);
            }
            NeoForgeMachineTaskScheduler.clear(server);
            logout(server, owner);
        }
        helper.succeed();
    }

    private static TargetBlockEntity setup(GameTestHelper helper, ServerPlayer owner) {
        TargetBlockEntity machine = NeoForgeMachineConfiguratorMenuGameTests.placeTarget(helper);
        machine.chemicalOnly = true;
        NeoForgeMachineConfigSavedData.get(helper.getLevel().getServer())
                .getOrCreate(MachineKey.of(machine.getLevel().dimension(), machine.getBlockPos()), owner.getUUID());
        IKnowledgeProvider knowledge = knowledge(owner);
        knowledge.clearKnowledge();
        knowledge.setEmc(BigInteger.TEN.pow(10));
        return machine;
    }

    private static MachineTarget target(TargetBlockEntity machine) {
        String key = NeoForgeMachineChemicalAccess.read(machine, Direction.NORTH, 0).ports().get(1).key();
        return MachineTarget.relative(Direction.NORTH.get3DDataValue(), 1, key, machine.getBlockState());
    }

    private static long run(ServerPlayer owner, TargetBlockEntity machine, MachineTarget target, boolean supply, long requested) {
        return NeoForgeMachineChemicalTasks.run(owner.getServer(), owner.getUUID(), filter(), PROXY, machine, target, supply, requested);
    }

    private static CompoundTag filter() {
        CompoundTag filter = new CompoundTag();
        filter.putString("gasName", HYDROGEN.toString());
        filter.putLong("amount", 1);
        CompoundTag sample = new CompoundTag();
        sample.put("g", filter);
        return sample;
    }

    private static CompoundTag sampleWithAmount(long amount) {
        CompoundTag filter = filter().getCompound("g");
        filter.putLong("amount", amount);
        CompoundTag sample = new CompoundTag();
        sample.put("g", filter);
        return sample;
    }

    private static CompoundTag sampleWithoutAmount() {
        CompoundTag filter = filter().getCompound("g");
        filter.remove("amount");
        CompoundTag sample = new CompoundTag();
        sample.put("g", filter);
        return sample;
    }

    private static CompoundTag sampleWithStringAmount() {
        CompoundTag filter = filter().getCompound("g");
        filter.putString("amount", "1");
        CompoundTag sample = new CompoundTag();
        sample.put("g", filter);
        return sample;
    }

    private static boolean dataEdit(MinecraftServer server, ServerPlayer owner, String key, int enabled) {
        return NeoForgeMachineConfigSavedData.get(server).updateParameter(key, owner.getUUID(), 2, 0, 4, enabled);
    }

    private static ChemicalStack stack(ResourceLocation id, long amount) {
        return new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id)).orElseThrow(), amount);
    }

    private static IKnowledgeProvider knowledge(ServerPlayer player) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(knowledge != null, "ProjectE knowledge capability must be present");
        return knowledge;
    }

    private static ServerPlayer login(MinecraftServer server) {
        UUID id = UUID.randomUUID();
        GameProfile profile = new GameProfile(id, "ct-" + id.toString().substring(0, 8));
        ClientInformation info = ClientInformation.createDefault();
        var cookie = new net.minecraft.server.network.CommonListenerCookie(profile, 0, info, false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, info);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            try { logout(server, player); } finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        try {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class MutableChemicalHandler implements IChemicalHandler {
        final ChemicalStack[] stacks;
        int tanks = 2;
        long maxInsert = Long.MAX_VALUE;
        long maxExtract = Long.MAX_VALUE;
        Runnable simulationChange;
        Runnable readChange;
        int simulatedInserts;
        int simulatedExtracts;

        MutableChemicalHandler(long hydrogen) { stacks = new ChemicalStack[]{stack(OXYGEN, 40), stack(HYDROGEN, hydrogen)}; }
        @Override public int getChemicalTanks() { return tanks; }
        @Override public ChemicalStack getChemicalInTank(int tank) {
            changedRead();
            return tank >= 0 && tank < stacks.length ? stacks[tank].copy() : ChemicalStack.EMPTY;
        }
        @Override public long getChemicalTankCapacity(int tank) { changedRead(); return tank >= 0 && tank < stacks.length ? 3_000_000_000L : 0; }
        @Override public void setChemicalInTank(int tank, ChemicalStack stack) { throw new UnsupportedOperationException(); }
        @Override public boolean isValid(int tank, ChemicalStack sample) { return tank == 1 && sample.is(MekanismAPI.CHEMICAL_REGISTRY.get(HYDROGEN)); }
        @Override public ChemicalStack insertChemical(int tank, ChemicalStack sample, Action action) {
            if (!isValid(tank, sample)) return sample;
            if (!action.execute()) simulatedInserts++;
            long accepted = Math.min(sample.getAmount(), Math.max(0, getChemicalTankCapacity(tank) - stacks[tank].getAmount()));
            if (action.execute()) {
                accepted = Math.min(accepted, maxInsert);
                stacks[tank] = sample.copyWithAmount(stacks[tank].getAmount() + accepted);
            } else changed();
            return sample.copyWithAmount(sample.getAmount() - accepted);
        }
        @Override public ChemicalStack extractChemical(int tank, long requested, Action action) {
            if (tank != 1) return ChemicalStack.EMPTY;
            if (!action.execute()) simulatedExtracts++;
            long extracted = Math.min(requested, stacks[tank].getAmount());
            if (action.execute()) {
                extracted = Math.min(extracted, maxExtract);
                stacks[tank] = stacks[tank].copyWithAmount(stacks[tank].getAmount() - extracted);
            } else changed();
            return stack(HYDROGEN, extracted);
        }
        private void changed() {
            Runnable change = simulationChange;
            simulationChange = null;
            if (change != null) change.run();
        }

        private void changedRead() {
            Runnable change = readChange;
            readChange = null;
            if (change != null) change.run();
        }
    }
}
