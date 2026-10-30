package com.primordial.neoforge.charm;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeBlackholeCharmGameTests {
    private NeoForgeBlackholeCharmGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void sixListsPreserveTypedComponentFieldsAndDenyWins(GameTestHelper helper) {
        ItemStack charm = charm(), apple = new ItemStack(Items.APPLE), stone = new ItemStack(Items.STONE);
        var registries = helper.getLevel().registryAccess();
        check(NeoForgeCharmFilters.accepts(charm, stone, registries), "No filters must allow ordinary drops");
        check(NeoForgeCharmFilters.add(charm, apple, 0, false, List.of(), registries)
                        && NeoForgeCharmFilters.accepts(charm, apple, registries) && !NeoForgeCharmFilters.accepts(charm, stone, registries),
                "Item allow rules must tighten matching without changing the sample");
        NeoForgeCharmFilters.add(charm, apple, 0, true, List.of(), registries);
        check(!NeoForgeCharmFilters.accepts(charm, apple, registries), "Deny must override allow");
        charm = charm();
        ItemStack sample = new ItemStack(Items.DIAMOND_SWORD);
        sample.setDamageValue(17);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("first"));
        check(NeoForgeCharmFilters.add(charm, sample, 1, false, List.of("/minecraft:damage"), registries), "A real component field must be selectable");
        ItemStack renamed = sample.copy(); renamed.set(DataComponents.CUSTOM_NAME, Component.literal("second"));
        check(NeoForgeCharmFilters.accepts(charm, renamed, registries), "Unselected component changes must not affect field matching");
        renamed.setDamageValue(18);
        check(!NeoForgeCharmFilters.accepts(charm, renamed, registries), "Selected damage must match exactly");
        check(!NeoForgeCharmFilters.add(charm, sample, 1, false, List.of("/forged"), registries), "Client-selected nonexistent fields must be rejected");
        ItemStack restored = ItemStack.parse(registries, charm.save(registries)).orElseThrow();
        check(NeoForgeCharmFilters.snapshot(restored).equals(NeoForgeCharmFilters.snapshot(charm)), "Six lists must survive normal item serialization");
        ItemStack full = charm();
        NeoForgeCharmFilters.add(full, sample, 1, false, List.of(), registries);
        check(NeoForgeCharmFilters.accepts(full, sample.copy(), registries) && !NeoForgeCharmFilters.accepts(full, renamed, registries),
                "An unselected NBT rule must compare the full serialized component state");
        ItemStack tags = charm(), log = new ItemStack(Items.OAK_LOG);
        check(NeoForgeCharmFilters.add(tags, log, 2, false, List.of("minecraft:logs"), registries)
                        && NeoForgeCharmFilters.accepts(tags, new ItemStack(Items.BIRCH_LOG), registries)
                        && !NeoForgeCharmFilters.accepts(tags, stone, registries), "Tag rules must use the actual item registry tags");
        NeoForgeCharmFilters.add(tags, log, 2, true, List.of("minecraft:logs"), registries);
        check(!NeoForgeCharmFilters.accepts(tags, log, registries), "Tag deny rules must override a matching allow rule");
        CompoundTag corrupt = NeoForgeCharmFilters.data(charm); corrupt.putString("WhitelistItems", "broken");
        charm.set(DataComponents.CUSTOM_DATA, CustomData.of(corrupt));
        check(!NeoForgeCharmFilters.accepts(charm, sample, registries), "Malformed filter lists must pause absorption");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID, timeoutTicks = 60)
    public static void nativeCuriosTickerAbsorbsOnlyAfterEquipping(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        position(helper, player);
        ItemEntity entity = drop(player, new ItemStack(Items.STONE, 3));
        EmcAmount before = balance(player), expected = NeoForgeRecoveryPrices.item(entity.getItem()).multiply(3);
        try { equip(player, charm(), 0); }
        catch (RuntimeException | Error failure) { entity.discard(); logout(server, player); throw failure; }
        helper.runAfterDelay(4, () -> {
            try {
                check(entity.isRemoved() && balance(player).equals(before.add(expected)),
                        "Curios native worn-item ticker must invoke registered recovery without a manual curioTick call");
                helper.succeed();
            } finally { entity.discard(); logout(server, player); }
        });
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void wornCharmRespectsBudgetsCreditsActualDropsAndIgnoresCosmetics(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        int limit = NeoForgeServerConfig.BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK.get();
        try {
            position(helper, player);
            var charm = charm(); equip(player, charm, 0);
            NeoForgeServerConfig.BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK.set(2);
            ItemStack stone = new ItemStack(Items.STONE, 5);
            ItemEntity drop = drop(player, stone);
            var before = balance(player);
            var item = NeoForgeModRegistries.BLACKHOLE_CHARM_ITEM.get();
            item.curioTick(new SlotContext("charm", player, 0, true, true), charm);
            check(drop.getItem().getCount() == 5 && balance(player).equals(before), "Cosmetic callbacks must consume and credit nothing");
            item.curioTick(new SlotContext("charm", player, 0, false, true), charm);
            check(drop.getItem().getCount() == 3 && balance(player).equals(before.add(NeoForgeRecoveryPrices.item(stone).multiply(2))),
                    "A worn charm must credit exactly the consumed partial drop");
            check(player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).hasKnowledge(ItemInfo.fromStack(new ItemStack(Items.STONE))),
                    "Actual absorption must teach ProjectE knowledge");
            equip(player, charm(), 1);
            item.curioTick(new SlotContext("charm", player, 1, false, true), CuriosApi.getCuriosInventory(player).orElseThrow()
                    .getStacksHandler("charm").orElseThrow().getStacks().getStackInSlot(1));
            item.curioTick(new SlotContext("charm", player, 0, false, true), charm);
            check(drop.getItem().getCount() == 3, "Multiple worn charms and repeated callbacks must share the same per-player tick budget");
            drop.discard();
        } finally { NeoForgeServerConfig.BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK.set(limit); logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void detachedInactiveAndUnsafeContainerDropsRemainUntouched(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            position(helper, player);
            var charm = charm(); player.setItemInHand(InteractionHand.MAIN_HAND, charm);
            ItemEntity stone = drop(player, new ItemStack(Items.STONE, 2));
            var item = NeoForgeModRegistries.BLACKHOLE_CHARM_ITEM.get();
            item.curioTick(new SlotContext("charm", player, 0, false, true), charm);
            check(!stone.isRemoved(), "A held or forged detached charm callback must not scan");
            equip(player, charm, 0);
            CuriosApi.getCuriosInventory(player).orElseThrow().setSlotActive("charm", 0, false);
            item.curioTick(new SlotContext("charm", player, 0, false, true), charm);
            check(!stone.isRemoved(), "An inactive Curios slot must not scan");
            stone.discard();
            CuriosApi.getCuriosInventory(player).orElseThrow().setSlotActive("charm", 0, true);
            ItemStack chest = new ItemStack(Items.CHEST);
            chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3))));
            ItemEntity stored = drop(player, chest);
            var before = balance(player);
            item.curioTick(new SlotContext("charm", player, 0, false, true), charm);
            check(!stored.isRemoved() && ItemStack.matches(stored.getItem(), chest) && balance(player).equals(before),
                    "Automatic recycling must leave container contents intact without paying or consuming the shell");
            stored.discard();
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void editorUsesServerSamplesAndRejectsStaleOrSwitchedHandActions(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            ItemStack charm = charm(); player.setItemInHand(InteractionHand.MAIN_HAND, charm);
            player.getInventory().setItem(9, new ItemStack(Items.APPLE, 7));
            var menu = new NeoForgeBlackholeCharmMenu(67, player.getInventory(), InteractionHand.MAIN_HAND);
            player.containerMenu = menu;
            menu.clicked(1, 0, ClickType.PICKUP, player);
            check(menu.sample().is(Items.APPLE) && player.getInventory().getItem(9).getCount() == 7
                            && menu.getCarried().isEmpty(), "Inventory clicks must only create a ghost sample");
            int revision = menu.revision();
            NeoForgeBlackholeCharmPayloads.handle(player, new NeoForgeBlackholeCharmPayloads.Action(menu.containerId, revision - 1, 4));
            check(NeoForgeCharmFilters.snapshot(charm).isEmpty(), "Stale revisions must not add rules");
            NeoForgeBlackholeCharmPayloads.handle(player, new NeoForgeBlackholeCharmPayloads.Action(menu.containerId, revision, 4));
            check(NeoForgeCharmFilters.rules(NeoForgeCharmFilters.snapshot(charm), 0, false).size() == 1,
                    "Current menu action must save its authoritative sample");
            player.setItemInHand(InteractionHand.MAIN_HAND, charm());
            NeoForgeBlackholeCharmPayloads.handle(player, new NeoForgeBlackholeCharmPayloads.Action(menu.containerId, menu.revision(), 1000));
            check(!menu.stillValid(player) && NeoForgeCharmFilters.rules(NeoForgeCharmFilters.snapshot(charm), 0, false).size() == 1,
                    "Replacing the held item must invalidate the editor without editing either charm");
        } finally { logout(server, player); }
        helper.succeed();
    }

    private static ItemStack charm() { return new ItemStack(NeoForgeModRegistries.BLACKHOLE_CHARM_ITEM.get()); }
    private static void equip(ServerPlayer player, ItemStack charm, int index) {
        var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
        var handler = curios.getStacksHandler("charm").orElseThrow(() -> new GameTestAssertException("A real charm slot must be registered"));
        if (handler.getStacks().getSlots() <= index) handler.grow(index + 1 - handler.getStacks().getSlots());
        handler.getStacks().setStackInSlot(index, charm);
    }
    private static void position(GameTestHelper helper, ServerPlayer player) {
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        player.teleportTo(helper.getLevel(), pos.getX() + .5, pos.getY() + .1, pos.getZ() + .5, 0, 0);
    }
    private static ItemEntity drop(ServerPlayer player, ItemStack stack) {
        ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        entity.setNoGravity(true); entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); entity.setPickUpDelay(1000);
        player.serverLevel().addFreshEntity(entity); return entity;
    }
    private static EmcAmount balance(ServerPlayer player) {
        var ledger = NeoForgeEmcSavedData.get(player.server);
        return EmcAmount.of(player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).getEmc())
                .add(ledger.pending(player.getUUID())).subtract(EmcAmount.of(ledger.debit(player.getUUID())));
    }
    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "ch-" + UUID.randomUUID().toString().substring(0, 6));
        var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.ZERO);
            return player;
        } catch (RuntimeException | Error failure) { try { logout(server, player); } finally { channel.finishAndReleaseAll(); } throw failure; }
    }
    private static void logout(MinecraftServer server, ServerPlayer player) {
        try { if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player); }
        finally { if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel) channel.finishAndReleaseAll(); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
