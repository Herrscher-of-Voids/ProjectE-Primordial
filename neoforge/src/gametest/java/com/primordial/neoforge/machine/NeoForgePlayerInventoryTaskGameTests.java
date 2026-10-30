package com.primordial.neoforge.machine;

import java.math.BigInteger;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.primordial.data.SampleMode;
import com.primordial.neoforge.PrimordialNeoForgeMod;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePlayerInventoryTaskGameTests {
    private NeoForgePlayerInventoryTaskGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rulesRoundTripWithComponentsAndRejectInvalidSlots(GameTestHelper helper) {
        var data = new NeoForgePlayerInventorySavedData();
        UUID owner = UUID.randomUUID();
        ItemStack filter = new ItemStack(Items.DIAMOND);
        filter.set(DataComponents.CUSTOM_NAME, Component.literal("Bound sample"));
        data.set(owner, 8, filter, 17, 40, SampleMode.FIXED_RATE, true);
        data.set(owner, 36, filter, 1, 1, SampleMode.KEEP_LEVEL, true);

        var saved = data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
        var loaded = NeoForgePlayerInventorySavedData.load(saved, helper.getLevel().registryAccess());
        var rule = loaded.get(owner, 8);
        check(rule != null && ItemStack.matches(rule.filter(), filter) && rule.targetCount() == 17
                        && rule.intervalTicks() == 40 && rule.mode() == SampleMode.FIXED_RATE && rule.enabled()
                        && loaded.get(owner, 36) == null,
                "Saved rule must preserve its component filter and values while rejecting inventory slot 36");
        loaded.setEnabled(owner, 8, false);
        check(!loaded.get(owner, 8).enabled(), "Pausing a rule must preserve it and its filter");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void knownItemPurchaseOnlyChangesBoundSlotAndCanBePaused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            check(com.primordial.neoforge.config.NeoForgeKnowledgeConfig.required(),
                    "GameTest requires the default knowledge gate to remain enabled");
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null, "ProjectE player knowledge capability must exist");
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.TEN.pow(10));
            ItemStack filter = new ItemStack(Items.DIAMOND);
            check(knowledge.addKnowledge(ItemInfo.fromStack(filter)), "Test player must learn the selected item");
            UUID owner = player.getUUID();
            var data = NeoForgePlayerInventorySavedData.get(server);
            data.set(owner, 8, filter, 5, 1, SampleMode.FIXED_RATE, true);
            player.getInventory().setItem(9, new ItemStack(Items.DIRT, 3));

            BigInteger before = knowledge.getEmc();
            NeoForgePlayerInventoryTask.run(player);
            check(player.getInventory().getItem(8).getCount() == 5
                            && player.getInventory().getItem(9).getCount() == 3
                            && knowledge.getEmc().compareTo(before) < 0,
                    "Automatic supply must buy the configured amount, charge EMC, and leave adjacent slots unchanged");

            data.setEnabled(owner, 8, false);
            player.getInventory().setItem(8, ItemStack.EMPTY);
            BigInteger pausedBalance = knowledge.getEmc();
            NeoForgePlayerInventoryTask.run(player);
            check(player.getInventory().getItem(8).isEmpty() && knowledge.getEmc().equals(pausedBalance),
                    "A paused rule must neither replenish nor charge EMC");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void unknownItemNeverReplenishes(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            check(com.primordial.neoforge.config.NeoForgeKnowledgeConfig.required(),
                    "GameTest requires the default knowledge gate to remain enabled");
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null, "ProjectE player knowledge capability must exist");
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.TEN.pow(10));
            NeoForgePlayerInventorySavedData.get(server).set(player.getUUID(), 4, new ItemStack(Items.DIAMOND),
                    1, 1, SampleMode.FIXED_RATE, true);
            BigInteger before = knowledge.getEmc();
            NeoForgePlayerInventoryTask.run(player);
            check(player.getInventory().getItem(4).isEmpty() && knowledge.getEmc().equals(before),
                    "An unlearned item must not be created or charged to the player");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    private static ServerPlayer login(MinecraftServer server) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "inv-refill-test");
        var info = ClientInformation.createDefault();
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
