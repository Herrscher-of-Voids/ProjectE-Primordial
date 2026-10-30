package com.primordial.neoforge.machine;

import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.primordial.data.MachineTarget;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.machine.NeoForgeMachineTestFixture.FixtureBlockEntity;
import com.primordial.neoforge.machine.NeoForgeMachineTargetTestFixture.TargetBlockEntity;
import com.primordial.service.EmcAmount;
import com.primordial.util.MachineKey;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineConfiguratorMenuGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeMachineConfiguratorMenuGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void onlyTheNearbyOwnerMayUseTheCapturedLiveMachine(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        ServerPlayer other = null;
        try {
            other = login(server);
            FixtureBlockEntity machine = placeFixture(helper);
            String key = key(helper.getLevel(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            data.getOrCreate(key, owner.getUUID());
            CompoundTag before = snapshot(data, helper);
            near(owner, machine.getBlockPos());
            var menu = open(owner, key, 121);

            check(menu.machineKey().equals(key) && menu.targetMachine(owner) == machine
                            && menu.canEdit(owner) && menu.stillValid(owner),
                    "The pinned owner must access the originally captured machine while nearby");
            other.containerMenu = menu;
            near(other, machine.getBlockPos());
            check(menu.targetMachine(other) == null && !menu.canEdit(other) && !menu.stillValid(other),
                    "A different logged-in player must not use the owner's menu");

            ServerPlayer sameProfile = new ServerPlayer(server, helper.getLevel(), owner.getGameProfile(),
                    ClientInformation.createDefault());
            sameProfile.containerMenu = menu;
            near(sameProfile, machine.getBlockPos());
            check(menu.targetMachine(sameProfile) == null && !menu.canEdit(sameProfile)
                            && !menu.stillValid(sameProfile),
                    "A second Player object with the owner's UUID must not inherit the menu");

            owner.setPos(machine.getBlockPos().getX() + 8.5, machine.getBlockPos().getY() + 0.5,
                    machine.getBlockPos().getZ() + 0.5);
            check(!menu.stillValid(owner) && menu.targetMachine(owner) == null,
                    "A player exactly eight blocks from the machine must be rejected");
            near(owner, machine.getBlockPos());

            ServerLevel nether = server.getLevel(Level.NETHER);
            check(nether != null && owner.teleportTo(nether, 0.5, 80, 0.5, Set.of(), 0, 0),
                    "The test owner must be movable to the other dimension");
            check(!menu.stillValid(owner) && menu.targetMachine(owner) == null,
                    "The menu must reject its owner in another dimension");
            check(owner.teleportTo((ServerLevel) helper.getLevel(), machine.getBlockPos().getX() + 0.5,
                    machine.getBlockPos().getY() + 0.5, machine.getBlockPos().getZ() + 0.5, Set.of(), 0, 0),
                    "The owner must return to the fixture dimension");
            owner.setHealth(0.0F);
            check(!owner.isAlive(), "The test owner must be in a dead state");
            check(!menu.stillValid(owner) && menu.targetMachine(owner) == null,
                    "A dead owner must not retain access");
            check(snapshot(data, helper).equals(before), "Authorization checks must not write machine configuration");
        } finally {
            logout(server, other);
            logout(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void missingInitialTargetInvalidKeysAndStoredOwnerFailuresStayInactive(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        var storage = server.overworld().getDataStorage();
        var previous = NeoForgeMachineConfigSavedData.get(server);
        CompoundTag previousTag = previous.save(new CompoundTag(), helper.getLevel().registryAccess());
        try {
            BlockPos pos = helper.absolutePos(POSITION);
            String key = key(helper.getLevel(), pos);
            var data = new NeoForgeMachineConfigSavedData();
            storage.set(NeoForgeMachineConfigSavedData.DATA_NAME, data);
            data.getOrCreate(key, owner.getUUID());
            near(owner, pos);
            var absent = open(owner, key, 122);
            check(absent.targetMachine(owner) == null && !absent.canEdit(owner) && !absent.stillValid(owner),
                    "A menu opened without an initial target must remain inactive");
            FixtureBlockEntity later = placeFixture(helper);
            check(absent.targetMachine(owner) == null && !absent.canEdit(owner) && !absent.stillValid(owner),
                    "A machine appearing later must not activate a menu that captured no target");

            CompoundTag ownerlessRoot = new CompoundTag();
            CompoundTag configs = new CompoundTag();
            CompoundTag ownerless = new CompoundTag();
            ownerless.putString("opaque", "preserve");
            configs.put(key, ownerless);
            ownerlessRoot.put("configs", configs);
            var ownerlessData = NeoForgeMachineConfigSavedData.load(ownerlessRoot, helper.getLevel().registryAccess());
            storage.set(NeoForgeMachineConfigSavedData.DATA_NAME, ownerlessData);
            CompoundTag ownerlessBefore = snapshot(ownerlessData, helper);
            var rejectedOwnerless = open(owner, key, 123);
            check(rejectedOwnerless.targetMachine(owner) == null && !rejectedOwnerless.canEdit(owner)
                            && !rejectedOwnerless.stillValid(owner)
                            && snapshot(ownerlessData, helper).equals(ownerlessBefore),
                    "Ownerless saved configs must remain unchanged and cannot be claimed by opening a menu");

            var repairedData = new NeoForgeMachineConfigSavedData();
            repairedData.getOrCreate(key, owner.getUUID());
            storage.set(NeoForgeMachineConfigSavedData.DATA_NAME, repairedData);
            check(absent.targetMachine(owner) == null && !absent.canEdit(owner) && !absent.stillValid(owner)
                            && rejectedOwnerless.targetMachine(owner) == null && !rejectedOwnerless.canEdit(owner),
                    "Repairing authorization later must not activate a menu that was initially unauthorized");

            CompoundTag corrupt = new CompoundTag();
            corrupt.putString("configs", "wrong-type");
            corrupt.putString("unknown", "keep-exactly");
            var corruptData = NeoForgeMachineConfigSavedData.load(corrupt, helper.getLevel().registryAccess());
            storage.set(NeoForgeMachineConfigSavedData.DATA_NAME, corruptData);
            CompoundTag corruptBefore = snapshot(corruptData, helper);
            var rejectedCorrupt = open(owner, key, 124);
            check(rejectedCorrupt.targetMachine(owner) == null && !rejectedCorrupt.canEdit(owner)
                            && !rejectedCorrupt.stillValid(owner)
                            && snapshot(corruptData, helper).equals(corruptBefore),
                    "Corrupt SavedData must fail closed without rewriting its opaque snapshot");

            var restored = NeoForgeMachineConfigSavedData.load(previousTag, helper.getLevel().registryAccess());
            storage.set(NeoForgeMachineConfigSavedData.DATA_NAME, restored);
            String invalid = "not-a-machine-key";
            CompoundTag beforeInvalid = snapshot(restored, helper);
            var rejectedKey = open(owner, invalid, 125);
            check(rejectedKey.machineKey().isEmpty() && rejectedKey.targetMachine(owner) == null
                            && !rejectedKey.canEdit(owner) && !rejectedKey.stillValid(owner)
                            && snapshot(restored, helper).equals(beforeInvalid),
                    "Malformed machine keys must be rejected without throwing, creating or changing SavedData");

            BlockPos remote = pos.offset(1024, 0, 1024);
            String remoteKey = key(helper.getLevel(), remote);
            restored.getOrCreate(remoteKey, owner.getUUID());
            CompoundTag beforeRemote = snapshot(restored, helper);
            var unloaded = open(owner, remoteKey, 126);
            check(!helper.getLevel().hasChunkAt(remote) && unloaded.targetMachine(owner) == null
                            && !unloaded.canEdit(owner) && !unloaded.stillValid(owner)
                            && snapshot(restored, helper).equals(beforeRemote),
                    "A saved target in an unloaded remote chunk must stay unloaded and untouched");
            check(later != null, "The later fixture must have been placed for the inactive-menu assertion");
        } finally {
            // Other delayed tests retain this instance; restoring a copy detaches their later pause writes.
            storage.set(NeoForgeMachineConfigSavedData.DATA_NAME, previous);
            logout(server, owner);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void removedOrReplacedMachinePermanentlyInvalidatesItsMenu(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        try {
            TargetBlockEntity machine = placeTarget(helper);
            String key = key(helper.getLevel(), machine.getBlockPos());
            NeoForgeMachineConfigSavedData.get(server).getOrCreate(key, owner.getUUID());
            near(owner, machine.getBlockPos());
            var menu = open(owner, key, 127);
            check(menu.targetMachine(owner) == machine && menu.canEdit(owner) && menu.stillValid(owner),
                    "The owner must initially access the selected test machine");

            helper.setBlock(POSITION, Blocks.AIR);
            check(machine.isRemoved() && menu.targetMachine(owner) == null
                            && !menu.canEdit(owner) && !menu.stillValid(owner),
                    "Removing the captured machine must invalidate the menu");
            TargetBlockEntity replacement = placeTarget(helper);
            check(replacement != machine && replacement.getClass() == machine.getClass()
                            && helper.getLevel().getBlockEntity(replacement.getBlockPos()) == replacement
                            && menu.targetMachine(owner) == null && !menu.canEdit(owner) && !menu.stillValid(owner),
                    "Replacing the block entity with the same type must not reactivate an old menu");
            var fresh = open(owner, key, 128);
            check(fresh.targetMachine(owner) == replacement && fresh.canEdit(owner) && fresh.stillValid(owner),
                    "A newly opened menu may capture the replacement machine");
        } finally { logout(server, owner); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void configuratorOpensOnlySupportedOwnedMachines(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        ServerPlayer other = null;
        try {
            other = login(server);
            FixtureBlockEntity machine = placeFixture(helper);
            near(owner, machine.getBlockPos());
            near(other, machine.getBlockPos());
            ItemStack configurator = new ItemStack(NeoForgeModRegistries.CONFIGURATOR_ITEM.get());
            owner.setItemInHand(InteractionHand.MAIN_HAND, configurator.copy());
            var hit = hit(owner, machine.getBlockPos());

            InteractionResult opened = NeoForgeModRegistries.CONFIGURATOR_ITEM.get()
                    .useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit));
            String key = key(helper.getLevel(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            check(opened.consumesAction() && owner.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                            && menu.machineKey().equals(key) && menu.canEdit(owner)
                            && data.canEdit(key, owner.getUUID()),
                    "The configurator must open the clicked supported machine and pin its first owner");

            other.setItemInHand(InteractionHand.MAIN_HAND, configurator.copy());
            InteractionResult rejected = NeoForgeModRegistries.CONFIGURATOR_ITEM.get()
                    .useOn(new UseOnContext(other, InteractionHand.MAIN_HAND, hit(other, machine.getBlockPos())));
            check(rejected == InteractionResult.FAIL && data.canEdit(key, owner.getUUID())
                            && !data.canEdit(key, other.getUUID()),
                    "A different player must not claim or open the pinned machine");

            BlockPos blackholePos = helper.absolutePos(POSITION.east());
            helper.setBlock(POSITION.east(), NeoForgeModRegistries.BLACKHOLE.get().defaultBlockState());
            var blackholeKey = key(helper.getLevel(), blackholePos);
            CompoundTag blackholeBefore = data.config(blackholeKey);
            InteractionResult blackholeResult = NeoForgeModRegistries.CONFIGURATOR_ITEM.get()
                    .useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit(owner, blackholePos)));
            check(blackholeResult.consumesAction()
                            && java.util.Objects.equals(data.config(blackholeKey), blackholeBefore),
                    "Black holes must not create or alter machine configurator entries");

            BlockPos plainPos = helper.absolutePos(POSITION.west());
            helper.setBlock(POSITION.west(), Blocks.STONE);
            CompoundTag plainBefore = data.config(key(helper.getLevel(), plainPos));
            InteractionResult plainResult = NeoForgeModRegistries.CONFIGURATOR_ITEM.get()
                    .useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit(owner, plainPos)));
            check(plainResult == InteractionResult.PASS && java.util.Objects.equals(
                            data.config(key(helper.getLevel(), plainPos)), plainBefore),
                    "Unsupported blocks must not create or alter configuration entries");
        } finally {
            logout(server, other);
            logout(server, owner);
        }
        helper.succeed();
    }

    private static BlockHitResult hit(ServerPlayer player, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void unavailableOrThrowingCapabilitiesDoNotMutateInventoryOrEmc(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer owner = login(server);
        try {
            TargetBlockEntity machine = placeTarget(helper);
            String key = key(helper.getLevel(), machine.getBlockPos());
            var data = NeoForgeMachineConfigSavedData.get(server);
            data.getOrCreate(key, owner.getUUID());
            IItemHandler inventory = machine.items[Direction.NORTH.ordinal()];
            inventory.insertItem(0, new ItemStack(Items.DIAMOND, 7), false);
            var knowledge = owner.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null, "ProjectE knowledge capability must exist for the logged-in test owner");
            EmcAmount balance = exactBalance(owner);
            CompoundTag config = data.config(key);
            near(owner, machine.getBlockPos());
            var menu = open(owner, key, 129);

            MachineTarget target = MachineTarget.relative(Direction.NORTH.get3DDataValue(), 0,
                    NeoForgeCapabilityProxy.INSTANCE.itemSlotKey(machine, Direction.NORTH, inventory, 0),
                    machine.getBlockState());
            for (int failure : new int[]{0, 1, 2}) {
                machine.enabled = failure != 0;
                machine.failure = failure;
                machine.invalidatePorts();
                IItemHandler resolved = NeoForgeMachineTargetAccess.items(NeoForgeCapabilityProxy.INSTANCE, machine, target);
                check(resolved == null && menu.targetMachine(owner) == machine && menu.canEdit(owner)
                                && menu.stillValid(owner),
                        "Unavailable or throwing capabilities must fail closed without invalidating machine identity");
                check(inventory.getStackInSlot(0).is(Items.DIAMOND)
                                && inventory.getStackInSlot(0).getCount() == 7
                                && exactBalance(owner).equals(balance)
                                && data.config(key).equals(config),
                        "Capability failures must not change inventory, EMC or saved machine configuration");
            }
        } finally { logout(server, owner); }
        helper.succeed();
    }

    private static FixtureBlockEntity placeFixture(GameTestHelper helper) {
        helper.setBlock(POSITION, NeoForgeMachineTestFixture.BLOCK);
        return (FixtureBlockEntity) helper.getBlockEntity(POSITION);
    }

    static TargetBlockEntity placeTarget(GameTestHelper helper) {
        helper.setBlock(POSITION, NeoForgeMachineTargetTestFixture.BLOCK.defaultBlockState());
        return (TargetBlockEntity) helper.getBlockEntity(POSITION);
    }

    private static String key(Level level, BlockPos pos) {
        return MachineKey.of(level.dimension(), pos);
    }

    private static void near(ServerPlayer player, BlockPos pos) {
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static NeoForgeMachineConfiguratorMenu open(ServerPlayer player, String key, int id) {
        var menu = new NeoForgeMachineConfiguratorMenu(id, player.getInventory(), key);
        player.containerMenu = menu;
        return menu;
    }

    private static CompoundTag snapshot(NeoForgeMachineConfigSavedData data, GameTestHelper helper) {
        return data.save(new CompoundTag(), helper.getLevel().registryAccess());
    }

    private static EmcAmount exactBalance(ServerPlayer player) {
        var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        return EmcAmount.of(knowledge.getEmc()).add(NeoForgeEmcSavedData.get(player.server).pending(player.getUUID()));
    }

    private static ServerPlayer login(MinecraftServer server) {
        UUID id = UUID.randomUUID();
        GameProfile profile = new GameProfile(id, "machine-" + id.toString().substring(0, 8));
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
