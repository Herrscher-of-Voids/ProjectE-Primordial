package com.primordial.neoforge.machine;

import java.util.UUID;
import java.util.List;

import com.mojang.authlib.GameProfile;
import com.primordial.data.MachineTarget;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.util.MachineKey;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineConfiguratorPayloadGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeMachineConfiguratorPayloadGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void relativeSelectorRejectsRotatedPagesAndKeepsGhostTabsSeparate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), owner.registryAccess());
        try {
            var machine = NeoForgeMachineConfiguratorMenuGameTests.placeTarget(helper);
            String key = MachineKey.of(helper.getLevel().dimension(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            data.getOrCreate(key, owner.getUUID());
            owner.setPos(machine.getBlockPos().getX() + 0.5, machine.getBlockPos().getY() + 0.5,
                    machine.getBlockPos().getZ() + 0.5);
            var menu = new NeoForgeMachineConfiguratorMenu(303, owner.getInventory(), key);
            owner.containerMenu = menu;
            check(menu.clientConfig() != null && !menu.clientConfig().contains("en"),
                    "A fresh owner-only snapshot must be available before any energy setting is stored");
            var saved = data.save(new net.minecraft.nbt.CompoundTag(), owner.registryAccess());
            var view = menu.clientConfig();
            for (int section = 0; section < 8; section++) {
                var sparse = new net.minecraft.nbt.CompoundTag();
                check(NeoForgeMachineConfiguratorMenu.parameterValue(sparse, section, 2) == (section >= 6 ? 1000 : 1)
                                && NeoForgeMachineConfiguratorMenu.parameterValue(sparse, section, 3) == (section >= 6 ? 20 : 1),
                        "Sparse numeric fields must retain their executable defaults when displayed or synchronized");
            }
            var legacy = new net.minecraft.nbt.CompoundTag();
            legacy.putInt("sr", 700);
            legacy.putInt("si", 3);
            view.put("en", legacy);
            menu.setClientConfig(view);
            check(NeoForgeMachineConfiguratorMenu.parameterValue(menu.clientConfig().getCompound("en"), 4, 2) == 700
                            && NeoForgeMachineConfiguratorMenu.parameterValue(menu.clientConfig().getCompound("en"), 4, 3) == 60,
                    "Legacy FE amount and seconds must remain visible until explicit modern values replace them");
            legacy.putInt("sa", 91);
            legacy.putInt("sit", 9);
            menu.setClientConfig(view);
            check(NeoForgeMachineConfiguratorMenu.parameterValue(menu.clientConfig().getCompound("en"), 4, 2) == 91
                            && NeoForgeMachineConfiguratorMenu.parameterValue(menu.clientConfig().getCompound("en"), 4, 3) == 9
                            && data.save(new net.minecraft.nbt.CompoundTag(), owner.registryAccess()).equals(saved),
                    "Explicit numeric values must win, and displaying snapshots must never write defaults to saved data");
            check(menu.slots.get(0).isActive() && !menu.slots.get(18).isActive(),
                    "Only item samples may be active on the item tab");
            menu.activeTab = 1;
            check(!menu.slots.get(0).isActive() && menu.slots.get(18).isActive(),
                    "Hidden item samples must not cover the fluid samples at the same coordinates");
            menu.pickingTarget = true;
            check(!menu.slots.get(0).isActive() && !menu.slots.get(18).isActive(),
                    "The selector must keep every sample slot read-only and hidden");
            menu.pickingTarget = false;
            check(NeoForgeMachineConfiguratorPayloads.updateParameters(owner,
                    new NeoForgeMachineConfiguratorPayloads.UpdateParameters(303, 0, 4, 0, 2, 80))
                            && data.config(key).getCompound("en").getInt("sa") == 80,
                    "The first energy edit must work without a pre-existing energy compound");

            for (Direction front : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                helper.getLevel().setBlock(machine.getBlockPos(), machine.getBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, front), 3);
                Direction side = front.getClockWise();
                var query = new NeoForgeMachineConfiguratorPayloads.Query(303, 1, 0, 0,
                        side.get3DDataValue(), 0, false, front.get3DDataValue());
                buffer.clear();
                NeoForgeMachineConfiguratorPayloads.Query.STREAM_CODEC.encode(buffer, query);
                check(query.equals(NeoForgeMachineConfiguratorPayloads.Query.STREAM_CODEC.decode(buffer)),
                        "Queries must retain the selector's orientation on the wire");
                var ports = NeoForgeMachineConfiguratorPayloads.query(owner, query);
                check(ports != null && ports.valid() && !ports.ports().isEmpty(),
                        "Every horizontal orientation must expose the selected machine face");
                var port = ports.ports().getFirst();
                var batch = new NeoForgeMachineConfiguratorPayloads.BindBatch(303, 2, 0, 0,
                        List.of(new NeoForgeMachineConfiguratorPayloads.PortTarget(side.get3DDataValue(),
                                port.index(), port.key())), false, front.get3DDataValue());
                buffer.clear();
                NeoForgeMachineConfiguratorPayloads.BindBatch.STREAM_CODEC.encode(buffer, batch);
                check(batch.equals(NeoForgeMachineConfiguratorPayloads.BindBatch.STREAM_CODEC.decode(buffer)),
                        "Bindings must retain the selector's orientation on the wire");
                check(NeoForgeMachineConfiguratorPayloads.bindBatch(owner, batch)
                                && data.targets(key, 0, 0).getFirst().relativeFace()
                                == com.primordial.util.RelativeMachineSides.Face.LEFT,
                        "The selected left face must be saved relative to the current machine front");
                var before = data.save(new net.minecraft.nbt.CompoundTag(), owner.registryAccess());
                helper.getLevel().setBlock(machine.getBlockPos(), machine.getBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, front.getClockWise()), 3);
                var stale = NeoForgeMachineConfiguratorPayloads.query(owner, query);
                check(stale != null && !stale.valid() && stale.ports().isEmpty()
                                && !NeoForgeMachineConfiguratorPayloads.bindBatch(owner, batch)
                                && data.save(new net.minecraft.nbt.CompoundTag(), owner.registryAccess()).equals(before),
                        "Rotating the machine must reject stale pages and bindings without changing saved targets");
            }
        } finally {
            buffer.release();
            logout(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void queryReadsLivePortsAndBindRevalidatesExactPortIdentity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        ServerPlayer other = null;
        try {
            other = login(server);
            var machine = NeoForgeMachineConfiguratorMenuGameTests.placeTarget(helper);
            String key = MachineKey.of(helper.getLevel().dimension(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            data.getOrCreate(key, owner.getUUID());
            owner.setPos(machine.getBlockPos().getX() + 0.5, machine.getBlockPos().getY() + 0.5,
                    machine.getBlockPos().getZ() + 0.5);
            var menu = new NeoForgeMachineConfiguratorMenu(301, owner.getInventory(), key);
            owner.containerMenu = menu;

            var query = new NeoForgeMachineConfiguratorPayloads.Query(menu.containerId, 7, 0, 0,
                    Direction.NORTH.get3DDataValue(), 0);
            var ports = NeoForgeMachineConfiguratorPayloads.query(owner, query);
            check(ports != null && ports.valid() && ports.total() == 3 && ports.ports().size() == 3
                            && !ports.ports().getFirst().key().isEmpty(),
                    "The authorized owner must receive a bounded page from the current machine");
            var selected = ports.ports().getFirst();
            var before = data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());

            var forged = new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 8, 0, 0,
                    Direction.NORTH.get3DDataValue(), 0, selected.index(), selected.key() + "/forged", false, false);
            check(!NeoForgeMachineConfiguratorPayloads.bind(owner, forged)
                            && data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()).equals(before),
                    "A forged port key must not change saved configuration");

            other.containerMenu = menu;
            check(NeoForgeMachineConfiguratorPayloads.query(other, query) == null
                            && !NeoForgeMachineConfiguratorPayloads.bind(other,
                            new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 9, 0, 0,
                                    Direction.NORTH.get3DDataValue(), 0, selected.index(), selected.key(), false, false)),
                    "A second player must not query or edit the owner's machine menu");
            owner.containerMenu = menu;

            var bind = new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 10, 0, 0,
                    Direction.NORTH.get3DDataValue(), 0, selected.index(), selected.key(), false, false);
            check(NeoForgeMachineConfiguratorPayloads.bind(owner, bind)
                            && data.targets(key, 0, 0).size() == 1
                            && data.targets(key, 0, 0).getFirst().key().equals(selected.key()),
                    "The owner must bind a port only after its live key and index are revalidated");

            var clear = new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 11, 0, 0,
                    Direction.NORTH.get3DDataValue(), 0, -1, "", false, true);
            check(NeoForgeMachineConfiguratorPayloads.bind(owner, clear) && data.targets(key, 0, 0).isEmpty(),
                    "The owner must be able to clear the selected machine target");

            var wrongMenu = new NeoForgeMachineConfiguratorPayloads.Query(menu.containerId + 1, 12, 0, 0,
                    Direction.NORTH.get3DDataValue(), 0);
            check(NeoForgeMachineConfiguratorPayloads.query(owner, wrongMenu) == null,
                    "Stale container IDs must not receive machine port data");
        } finally {
            logout(server, other);
            logout(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void energyReadoutUsesLiveTargetsAndFallsBackSafely(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        ServerPlayer other = null;
        try {
            other = login(server);
            var machine = NeoForgeMachineConfiguratorMenuGameTests.placeTarget(helper);
            String key = MachineKey.of(helper.getLevel().dimension(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            data.getOrCreate(key, owner.getUUID());
            owner.setPos(machine.getBlockPos().getX() + 0.5, machine.getBlockPos().getY() + 0.5,
                    machine.getBlockPos().getZ() + 0.5);
            var menu = new NeoForgeMachineConfiguratorMenu(302, owner.getInventory(), key);
            owner.containerMenu = menu;

            var north = machine.energy[Direction.NORTH.ordinal()];
            var south = machine.energy[Direction.SOUTH.ordinal()];
            check(north.receiveEnergy(321, false) == 321 && south.receiveEnergy(567, false) == 567,
                    "Test energy ports must accept the configured values");
            var northTarget = new MachineTarget(Direction.NORTH.get3DDataValue(), 0,
                    NeoForgeMachineTargetAccess.energyKey(NeoForgeCapabilityProxy.INSTANCE, machine,
                            Direction.NORTH, north));
            var southTarget = new MachineTarget(Direction.SOUTH.get3DDataValue(), 0,
                    NeoForgeMachineTargetAccess.energyKey(NeoForgeCapabilityProxy.INSTANCE, machine,
                            Direction.SOUTH, south));
            check(data.updateTarget(key, owner.getUUID(), 4, 0, northTarget, false)
                            && data.updateTarget(key, owner.getUUID(), 5, 0, southTarget, false),
                    "The owner must bind both energy targets");

            var live = NeoForgeMachineConfiguratorPayloads.energyState(owner, menu);
            check(live != null && live.stored() == 321 && live.capacity() == 1000,
                    "Energy state must read the live supply port first");

            north.failReads = true;
            var throwingSupply = NeoForgeMachineConfiguratorPayloads.energyState(owner, menu);
            check(throwingSupply != null && throwingSupply.stored() == 567 && throwingSupply.capacity() == 1000,
                    "A supply handler that throws during reads must fall back to recovery");
            south.failReads = true;
            var throwingTargets = NeoForgeMachineConfiguratorPayloads.energyState(owner, menu);
            check(throwingTargets != null && throwingTargets.stored() == 0 && throwingTargets.capacity() == 0,
                    "Throwing energy handlers must fail closed without escaping the server tick");
            north.failReads = false;
            south.failReads = false;

            var staleSupply = new MachineTarget(northTarget.side(), northTarget.index(), northTarget.key() + "/stale");
            check(data.updateTarget(key, owner.getUUID(), 4, 0, staleSupply, false),
                    "A persisted target may become stale after the block's port structure changes");
            var fallback = NeoForgeMachineConfiguratorPayloads.energyState(owner, menu);
            check(fallback != null && fallback.stored() == 567 && fallback.capacity() == 1000,
                    "A stale supply target must fall back to the live recovery target");

            var staleRecovery = new MachineTarget(southTarget.side(), southTarget.index(), southTarget.key() + "/stale");
            check(data.updateTarget(key, owner.getUUID(), 5, 0, staleRecovery, false),
                    "The persisted recovery target may also become stale");
            var unavailable = NeoForgeMachineConfiguratorPayloads.energyState(owner, menu);
            check(unavailable != null && unavailable.stored() == 0 && unavailable.capacity() == 0,
                    "Unavailable energy targets must report zero without reusing stale capabilities");
            check(NeoForgeMachineConfiguratorPayloads.energyState(other, menu) == null,
                    "A non-owner must not read the owner's live machine energy");
        } finally {
            logout(server, other);
            logout(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void invalidAddressesAndStaleMachineTargetsFailClosed(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        try {
            var machine = NeoForgeMachineConfiguratorMenuGameTests.placeTarget(helper);
            String key = MachineKey.of(helper.getLevel().dimension(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            data.getOrCreate(key, owner.getUUID());
            owner.setPos(machine.getBlockPos().getX() + 0.5, machine.getBlockPos().getY() + 0.5,
                    machine.getBlockPos().getZ() + 0.5);
            var menu = new NeoForgeMachineConfiguratorMenu(302, owner.getInventory(), key);
            owner.containerMenu = menu;
            var before = data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());

            var invalidPage = NeoForgeMachineConfiguratorPayloads.query(owner,
                    new NeoForgeMachineConfiguratorPayloads.Query(menu.containerId, 2, 6, 6,
                            Direction.NORTH.get3DDataValue(), 0));
            check(!NeoForgeMachineConfiguratorPayloads.bind(owner,
                            new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 1, 0, 9,
                                    Direction.NORTH.get3DDataValue(), 0, 0, "fake", false, false))
                            && invalidPage != null && !invalidPage.valid() && invalidPage.ports().isEmpty(),
                    "Invalid samples and reserved independent-resource options must be rejected");

            helper.setBlock(POSITION, net.minecraft.world.level.block.Blocks.AIR);
            check(!NeoForgeMachineConfiguratorPayloads.bind(owner,
                            new NeoForgeMachineConfiguratorPayloads.Bind(menu.containerId, 3, 0, 0,
                                    Direction.NORTH.get3DDataValue(), 0, 0, "machine:stale", false, false))
                            && data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()).equals(before),
                    "A removed target must reject binding without changing SavedData");
        } finally { logout(server, owner); }
        helper.succeed();
    }

    private static ServerPlayer login(MinecraftServer server) {
        UUID id = UUID.randomUUID();
        GameProfile profile = new GameProfile(id, "mp-" + id.toString().substring(0, 8));
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
}
