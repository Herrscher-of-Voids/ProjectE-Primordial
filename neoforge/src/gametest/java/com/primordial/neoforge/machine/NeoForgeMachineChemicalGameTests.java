package com.primordial.neoforge.machine;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.machine.NeoForgeMachineTargetTestFixture.TargetBlockEntity;
import com.primordial.util.MachineKey;
import io.netty.channel.embedded.EmbeddedChannel;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Loaded reflectively only when Mekanism is installed. */
public final class NeoForgeMachineChemicalGameTests {
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");

    private NeoForgeMachineChemicalGameTests() {}

    public static List<TestFunction> tests() {
        var tests = new java.util.ArrayList<>(List.of(
                test("chemical_ports_query_bind_and_require_gas_mode", NeoForgeMachineChemicalGameTests::chemicalPortsQueryBindAndRequireGasMode),
                test("chemical_filter_uses_legacy_g_format_and_clears_conflicts", NeoForgeMachineChemicalGameTests::chemicalFilterUsesLegacyFormatAndClearsConflicts)));
        tests.addAll(NeoForgeMachineChemicalTaskGameTests.tests());
        return List.copyOf(tests);
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> action) {
        return new TestFunction("machine_chemical", "machine_chemical." + name,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true, action);
    }

    private static void chemicalPortsQueryBindAndRequireGasMode(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        try {
            TargetBlockEntity machine = NeoForgeMachineConfiguratorMenuGameTests.placeTarget(helper);
            machine.chemicalOnly = true;
            machine.chemicalHandler = new FixedChemicalHandler(sample(413), 900);
            String machineKey = MachineKey.of(helper.getLevel().dimension(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            data.getOrCreate(machineKey, owner.getUUID());
            owner.setPos(machine.getBlockPos().getX() + 0.5, machine.getBlockPos().getY() + 0.5,
                    machine.getBlockPos().getZ() + 0.5);
            var menu = new NeoForgeMachineConfiguratorMenu(311, owner.getInventory(), machineKey);
            owner.containerMenu = menu;
            check(menu.canEdit(owner) && NeoForgeMachineConfiguratorAccess.hasAnyCapability(machine),
                    "A Mekanism-only machine must open the configurator");

            var query = new NeoForgeMachineConfiguratorPayloads.Query(menu.containerId, 17, 2, 0,
                    Direction.NORTH.get3DDataValue(), 0, true);
            var ports = NeoForgeMachineConfiguratorPayloads.query(owner, query);
            check(ports != null && ports.valid() && ports.gasMode() && ports.total() == 1
                            && ports.ports().getFirst().chemical().equals(HYDROGEN)
                            && ports.ports().getFirst().amount() == 413
                            && ports.ports().getFirst().capacity() == 900
                            && ports.ports().getFirst().key().contains("/gas/"),
                    "Gas-mode queries must return the live chemical ID, amount, capacity, and stable gas key");

            var selected = ports.ports().getFirst();
            var wrongMode = new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 18, 2, 0,
                    Direction.NORTH.get3DDataValue(), 0, selected.index(), selected.key(), false, false, false);
            check(!NeoForgeMachineConfiguratorPayloads.bind(owner, wrongMode),
                    "A chemical key must not bind through the fluid mode");
            var batch = new NeoForgeMachineConfiguratorPayloads.BindBatch(menu.containerId, 19, 2, 0,
                    List.of(new NeoForgeMachineConfiguratorPayloads.PortTarget(Direction.NORTH.get3DDataValue(),
                            selected.index(), selected.key())), true);
            check(NeoForgeMachineConfiguratorPayloads.bindBatch(owner, batch)
                            && data.targets(machineKey, 2, 0).size() == 1
                            && data.targets(machineKey, 2, 0).getFirst().key().equals(selected.key()),
                    "The gas-mode batch bind must revalidate and save the exact live chemical port");
        } finally {
            logout(server, owner);
        }
        helper.succeed();
    }

    private static void chemicalFilterUsesLegacyFormatAndClearsConflicts(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        CompoundTag config = new CompoundTag();
        config.putUUID("owner", owner);
        root.put("test:chemical", config);
        CompoundTag saved = new CompoundTag();
        saved.put("configs", root);
        var data = NeoForgeMachineConfigSavedData.load(saved, helper.getLevel().registryAccess());
        check(data.updateChemicalFilter("test:chemical", owner, 2, 0, HYDROGEN),
                "The owner must save a registered Mekanism chemical filter");
        CompoundTag chemical = data.config("test:chemical").getList("fi", Tag.TAG_COMPOUND).getCompound(0);
        check(chemical.getCompound("g").getString("gasName").equals(HYDROGEN.toString())
                        && chemical.getCompound("g").getLong("amount") == 1L
                        && !chemical.contains("f") && chemical.getBoolean("e"),
                "Chemical samples must use the legacy g.gasName/amount shape and remain exclusive with fluid filters");

        FluidStack water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000);
        check(data.updateFluidFilter("test:chemical", owner, 2, 0, water, helper.getLevel().registryAccess()),
                "A fluid filter must replace a prior chemical sample");
        CompoundTag fluid = data.config("test:chemical").getList("fi", Tag.TAG_COMPOUND).getCompound(0);
        check(fluid.contains("f", Tag.TAG_COMPOUND) && !fluid.contains("g"),
                "Fluid and chemical filters must not coexist on one sample");

        check(data.updateChemicalFilter("test:chemical", owner, 2, 0, HYDROGEN),
                "A chemical filter must replace a prior fluid sample");
        var reloaded = NeoForgeMachineConfigSavedData.load(
                data.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        check(reloaded.config("test:chemical").getList("fi", Tag.TAG_COMPOUND).getCompound(0)
                        .getCompound("g").getString("gasName").equals(HYDROGEN.toString())
                        && reloaded.updateFluidFilter("test:chemical", owner, 2, 0, FluidStack.EMPTY,
                        helper.getLevel().registryAccess()),
                "Chemical filters must survive persistence and clear through the existing fluid-clear action");
        CompoundTag cleared = reloaded.config("test:chemical").getList("fi", Tag.TAG_COMPOUND).getCompound(0);
        check(!cleared.contains("f") && !cleared.contains("g") && !cleared.getBoolean("e"),
                "Clearing a chemical sample must remove its filter and disable the sample");
        check(!NeoForgePriceEditorChemicalBridge.validChemical(ResourceLocation.fromNamespaceAndPath("mekanism", "missing")),
                "Unregistered chemical IDs must not enter SavedData");
        helper.succeed();
    }

    private static ChemicalStack sample(long amount) {
        var key = ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, HYDROGEN);
        return new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolder(key).orElseThrow(), amount);
    }

    private static ServerPlayer login(MinecraftServer server) {
        UUID id = UUID.randomUUID();
        GameProfile profile = new GameProfile(id, "mc-" + id.toString().substring(0, 8));
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
            try { logout(server, player); }
            finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
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

    private static final class FixedChemicalHandler implements IChemicalHandler {
        private final ChemicalStack stack;
        private final long capacity;

        private FixedChemicalHandler(ChemicalStack stack, long capacity) {
            this.stack = stack.copy();
            this.capacity = capacity;
        }

        @Override public int getChemicalTanks() { return 1; }
        @Override public ChemicalStack getChemicalInTank(int tank) { return tank == 0 ? stack.copy() : ChemicalStack.EMPTY; }
        @Override public long getChemicalTankCapacity(int tank) { return tank == 0 ? capacity : 0; }
        @Override public void setChemicalInTank(int tank, ChemicalStack stack) { throw new UnsupportedOperationException(); }
        @Override public ChemicalStack extractChemical(int tank, long amount, Action action) { return ChemicalStack.EMPTY; }
        @Override public boolean isValid(int tank, ChemicalStack stack) { return tank == 0; }
        @Override public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) { return stack; }
    }
}
