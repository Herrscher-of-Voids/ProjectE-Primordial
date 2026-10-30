package com.primordial.neoforge.transmutation;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationNbtGameTests {
    private NeoForgeTransmutationNbtGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void threeIndependentFlagsPreserveSafeComponentsWithoutAliasing(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack sword = sword(helper.getLevel().getServer());
        var before = sword.save(registries);
        var sharpness = registries.registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS);
        for (int flags = 0; flags <= 7; flags++) {
            ItemInfo info = NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(sword), flags, registries);
            ItemStack output = info.createStack();
            check(output.has(DataComponents.CUSTOM_NAME) == ((flags & 1) != 0), "Ordinary safe data must use only bit 1");
            check(output.getDamageValue() == (((flags & 2) != 0) ? 37 : 0), "Damage must independently use bit 2");
            check(EnchantmentHelper.getItemEnchantmentLevel(sharpness, output) == (((flags & 4) != 0) ? 3 : 0),
                    "The complete enchantment combination must independently use bit 4");
            check(info.equals(NeoForgeTransmutationNbtPolicy.normalize(info, flags, registries)), "Normalization must be idempotent");
            check(before.equals(sword.save(registries)), "No setting may mutate the real source item");
        }
        ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(sword));
        check(persistent.equals(NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(sword), 7, registries)),
                "The production ProjectE persistence Mixin must use the safe component policy");
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        check(persistent.createStack().getHoverName().getString().equals("Safe sword"), "Knowledge must not alias mutable source components");
        sword.setDamageValue(Integer.MAX_VALUE);
        check(NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(sword), 2, registries).createStack().getDamageValue()
                == new ItemStack(Items.DIAMOND_SWORD).getMaxDamage(), "Damage must be bounded by the item's real base durability");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void unsafeStorageIdentityAndOversizedStateCannotBecomeRepeatableKnowledge(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack chest = new ItemStack(Items.CHEST);
        CompoundTag unsafe = new CompoundTag();
        unsafe.putString("id", "minecraft:chest");
        unsafe.putUUID("Owner", UUID.randomUUID());
        unsafe.putInt("Energy", 10000);
        unsafe.putInt("x", 100);
        chest.set(DataComponents.CUSTOM_DATA, CustomData.of(unsafe));
        chest.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(unsafe));
        chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
        chest.set(DataComponents.CUSTOM_NAME, Component.literal("Empty shell"));
        var original = chest.save(registries);
        ItemStack output = NeoForgeTransmutationNbtPolicy.safeOutput(chest);
        check(!output.has(DataComponents.CUSTOM_DATA) && !output.has(DataComponents.BLOCK_ENTITY_DATA)
                && output.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().findAny().isEmpty()
                && output.getHoverName().getString().equals("Empty shell"),
                "Ownership, coordinates, arbitrary mod data and contents must be stripped while safe display remains: " + output.save(registries));
        check(original.equals(chest.save(registries)), "Stripping knowledge must not consume or change the stored contents");
        chest.set(DataComponents.CUSTOM_NAME, Component.literal("x".repeat(40000)));
        check(!NeoForgeTransmutationNbtPolicy.persistent(ItemInfo.fromStack(chest)).hasModifiedComponents(),
                "Oversized safe-looking components must fall back to the base item");
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        check(ItemStack.isSameItemSameComponents(bucket, NeoForgeTransmutationNbtPolicy.safeOutput(bucket)),
                "A water bucket's inherent item identity must remain intact");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preferencesRoundTripByOwnerAndRejectInvalidPacketFlags(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var data = new NeoForgeTransmutationNbtSettings();
        UUID owner = UUID.randomUUID();
        check(data.flags(owner) == 7, "Forge's default is all three options enabled");
        for (int flags = 0; flags <= 7; flags++) {
            data.set(owner, flags);
            check(NeoForgeTransmutationNbtSettings.load(data.save(new CompoundTag(), registries), registries).flags(owner) == flags,
                    "All independent combinations must survive SavedData serialization");
        }
        data.set(owner, 2);
        data.setDirty(false);
        data.set(owner, 2);
        check(!data.isDirty() && data.flags(UUID.randomUUID()) == 7, "No-op writes must remain clean and other owners keep defaults");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        try {
            var request = new NeoForgeTransmutationNbtPayloads.Settings(91, -1);
            NeoForgeTransmutationNbtPayloads.Settings.STREAM_CODEC.encode(buffer, request);
            check(request.equals(NeoForgeTransmutationNbtPayloads.Settings.STREAM_CODEC.decode(buffer)), "Queries must preserve their menu and sentinel");
            buffer.clear(); buffer.writeVarInt(91); buffer.writeByte(8);
            try {
                NeoForgeTransmutationNbtPayloads.Settings.STREAM_CODEC.decode(buffer);
                throw new GameTestAssertException("Unknown preference bits must reject at decode");
            } catch (DecoderException expected) { }
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativeLearningAndOutputsUseAuthoritativeMenuSettings(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        TransmutationContainer menu = null;
        try {
            var settings = NeoForgeTransmutationNbtSettings.get(server);
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.TEN.pow(15));
            var request = new NeoForgeTransmutationNbtPayloads.Settings(92, 1);
            check(NeoForgeTransmutationNbtPayloads.apply(player, request) == null, "An inventory menu must not mutate transmutation preferences");
            menu = new TransmutationContainer(92, player.getInventory());
            player.containerMenu = menu;
            check(NeoForgeTransmutationNbtPayloads.apply(player, new NeoForgeTransmutationNbtPayloads.Settings(92, -1)).flags() == 7,
                    "Opening the actual menu must return the authoritative default");
            check(NeoForgeTransmutationNbtPayloads.apply(player, new NeoForgeTransmutationNbtPayloads.Settings(93, 0)) == null
                    && NeoForgeTransmutationNbtPayloads.apply(player, new NeoForgeTransmutationNbtPayloads.Settings(92, 8)) == null,
                    "Stale menus and invalid direct requests must not change settings");
            check(NeoForgeTransmutationNbtPayloads.apply(player, request).flags() == 1, "The sender must be able to select ordinary data alone");
            ItemStack sword = sword(server);
            ItemInfo namedOnly = NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(sword), 1, player.registryAccess());
            menu.transmutationInventory.handleKnowledge(sword);
            check(knowledge.hasKnowledge(namedOnly) && !knowledge.hasKnowledge(ItemInfo.fromStack(sword)),
                    "Native learning must retain only the chosen components, without learning the original full state");
            NeoForgeTransmutationNbtPayloads.apply(player, new NeoForgeTransmutationNbtPayloads.Settings(92, 4));
            check(knowledge.hasKnowledge(namedOnly), "Changing settings must not rewrite previously learned variants");
            menu.transmutationInventory.handleKnowledge(sword);
            check(knowledge.hasKnowledge(NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(sword), 4, player.registryAccess())),
                    "Subsequent native learning must use the new independent enchantment preference");
            menu.transmutationInventory.handleUnlearn(namedOnly);
            check(!knowledge.hasKnowledge(namedOnly)
                    && knowledge.hasKnowledge(NeoForgeTransmutationNbtPolicy.normalize(ItemInfo.fromStack(sword), 4, player.registryAccess())),
                    "Native unlearning must remove the selected historical variant without removing a different component combination");
            ItemStack filled = new ItemStack(Items.CHEST);
            filled.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
            knowledge.addKnowledge(ItemInfo.fromItem(Items.CHEST));
            menu.transmutationInventory.writeIntoOutputSlot(0, filled);
            check(menu.transmutationInventory.outputs.getStackInSlot(0).is(Items.CHEST)
                    && menu.transmutationInventory.outputs.getStackInSlot(0)
                            .getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().findAny().isEmpty(),
                    "The production native output Mixin must not reproduce historical or forged container contents: " + menu.transmutationInventory.outputs.getStackInSlot(0).save(player.registryAccess()));
            player.containerMenu = player.inventoryMenu;
            check(NeoForgeTransmutationNbtPayloads.apply(player, request) == null && settings.flags(player.getUUID()) == 4,
                    "A delayed request after close must leave the saved choice intact");
        } finally {
            if (menu != null) menu.removed(player);
            logout(server, player);
        }
        helper.succeed();
    }

    private static ItemStack sword(MinecraftServer server) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Safe sword"));
        sword.setDamageValue(37);
        sword.enchant(server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.SHARPNESS), 3);
        return sword;
    }

    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "nbt-" + UUID.randomUUID().toString().substring(0, 6));
        var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
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
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel) channel.finishAndReleaseAll();
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
