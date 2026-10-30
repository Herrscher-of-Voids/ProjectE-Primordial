package com.primordial.neoforge.machine;

import java.util.UUID;
import java.util.Arrays;
import java.math.BigInteger;
import com.mojang.authlib.GameProfile;
import com.primordial.data.SampleMode;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePlayerInventoryMenuGameTests {
    private NeoForgePlayerInventoryMenuGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void readOnlyMenuNeverMovesOrDropsItems(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            var menu = open(player, 91);
            ItemStack real = new ItemStack(Items.DIAMOND, 7);
            player.getInventory().setItem(9, real.copy());
            menu.setCarried(new ItemStack(Items.EMERALD, 3));
            for (ClickType type : ClickType.values()) {
                menu.clicked(0, 0, type, player);
                menu.clicked(0, 1, type, player);
                menu.clicked(-999, 0, type, player);
            }
            check(ItemStack.matches(real, player.getInventory().getItem(9))
                            && menu.getCarried().is(Items.EMERALD) && menu.getCarried().getCount() == 3,
                    "Every container click must preserve real inventory and carried items, including outside drops");
            var slot = menu.getSlot(0);
            check(slot.x == 43 && slot.y == 24 && menu.getSlot(27).y == 82
                            && menu.slots.size() == 36 && !slot.mayPickup(player)
                            && !slot.mayPlace(new ItemStack(Items.DIRT)) && slot.remove(1).isEmpty()
                            && !menu.canDragTo(slot) && !menu.canTakeItemForPickAll(real, slot)
                            && menu.quickMoveStack(player, 0).isEmpty(),
                    "Legacy layout must be preserved and every direct slot mutation path must be blocked");
            check(menu.resourceState(0) == NeoForgePlayerInventoryMenu.UNCONFIGURED
                            && !menu.resourceReady(0) && !menu.clickMenuButton(player, 102),
                    "Unsynchronized player magic controls start unavailable and invalid resource buttons are rejected");
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void authoritativeEditsPreservePauseAndOnlyAffectOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            var menu = open(player, 92);
            var data = NeoForgePlayerInventorySavedData.get(server);
            UUID otherOwner = UUID.randomUUID();
            ItemStack sample = new ItemStack(Items.DIAMOND, 7);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Authoritative sample"));
            player.getInventory().setItem(9, sample.copy());
            data.set(otherOwner, 9, new ItemStack(Items.EMERALD), 1, 20, SampleMode.KEEP_LEVEL, true);
            NeoForgePlayerInventoryPayloads.handleEdit(player, new NeoForgePlayerInventoryPayloads.Edit(
                    menu.containerId, 9, 5, 20, SampleMode.FIXED_RATE, true, sample.copyWithCount(1)));
            var rule = data.get(player.getUUID(), 9);
            check(rule != null && rule.enabled() && rule.targetCount() == 5
                            && ItemStack.matches(rule.filter(), sample.copyWithCount(1))
                            && menu.ruleState(0) == NeoForgePlayerInventoryMenu.ENABLED,
                    "A valid edit must bind the actual component-bearing stack and expose authoritative state");
            NeoForgePlayerInventoryPayloads.handleSetEnabled(player,
                    new NeoForgePlayerInventoryPayloads.SetEnabled(menu.containerId, new int[]{9}, false));
            NeoForgePlayerInventoryPayloads.handleEdit(player, new NeoForgePlayerInventoryPayloads.Edit(
                    menu.containerId, 9, 11, 40, SampleMode.FIXED_RATE, true, sample.copyWithCount(1)));
            check(!data.get(player.getUUID(), 9).enabled() && data.get(player.getUUID(), 9).targetCount() == 11
                            && menu.ruleState(0) == NeoForgePlayerInventoryMenu.PAUSED,
                    "Editing a paused rule must keep it paused until an explicit resume");
            NeoForgePlayerInventoryPayloads.handleClear(player,
                    new NeoForgePlayerInventoryPayloads.Clear(menu.containerId, new int[]{9}));
            check(data.get(player.getUUID(), 9) == null && data.get(otherOwner, 9).filter().is(Items.EMERALD)
                            && menu.ruleState(0) == NeoForgePlayerInventoryMenu.UNCONFIGURED
                            && ItemStack.matches(player.getInventory().getItem(9), sample),
                    "Unbinding must clear only this player's rule and never consume or create the sample");
            data.clear(otherOwner, 9);
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void staleAndMalformedRequestsCannotMutateRules(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        ServerPlayer other = login(server);
        try {
            var menu = open(player, 93);
            var data = NeoForgePlayerInventorySavedData.get(server);
            ItemStack sample = new ItemStack(Items.DIAMOND, 2);
            player.getInventory().setItem(9, sample);
            data.set(player.getUUID(), 9, sample, 5, 20, SampleMode.FIXED_RATE, true);
            NeoForgePlayerInventoryPayloads.handleEdit(player, new NeoForgePlayerInventoryPayloads.Edit(
                    menu.containerId + 1, 9, 99, 20, SampleMode.FIXED_RATE, true, sample));
            NeoForgePlayerInventoryPayloads.handleEdit(player, new NeoForgePlayerInventoryPayloads.Edit(
                    menu.containerId, 9, 99, 20, SampleMode.FIXED_RATE, true, new ItemStack(Items.EMERALD)));
            NeoForgePlayerInventoryPayloads.handleEdit(player, new NeoForgePlayerInventoryPayloads.Edit(
                    menu.containerId, 36, 99, 20, SampleMode.FIXED_RATE, true, sample));
            NeoForgePlayerInventoryPayloads.handleSetEnabled(player,
                    new NeoForgePlayerInventoryPayloads.SetEnabled(menu.containerId, new int[]{9, 36}, false));
            NeoForgePlayerInventoryPayloads.handleClear(player,
                    new NeoForgePlayerInventoryPayloads.Clear(menu.containerId, new int[]{9, -1}));
            check(data.get(player.getUUID(), 9).enabled() && data.get(player.getUUID(), 9).targetCount() == 5
                            && data.get(player.getUUID(), 36) == null,
                    "Stale windows, forged samples and invalid batches must not partially update any rule");
            other.containerMenu = menu;
            NeoForgePlayerInventoryPayloads.handleEdit(other, new NeoForgePlayerInventoryPayloads.Edit(
                    menu.containerId, 9, 99, 20, SampleMode.FIXED_RATE, true, sample));
            check(!menu.stillValid(other) && data.get(other.getUUID(), 9) == null,
                    "Even an exact menu ID must not authorize another player against the menu owner");
            other.containerMenu = other.inventoryMenu;
            player.containerMenu = player.inventoryMenu;
            NeoForgePlayerInventoryPayloads.handleClear(player,
                    new NeoForgePlayerInventoryPayloads.Clear(menu.containerId, new int[]{9}));
            check(data.get(player.getUUID(), 9) != null, "Requests from a closed menu must be ignored");
        } finally { logout(server, other); logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void heldChargingRequiresKnowledgeFundsAndCurrentMenu(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String previousRatio = NeoForgeServerConfig.FE_PER_EMC.get();
        try {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            var menu = open(player, 94);
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null, "ProjectE player knowledge capability must exist");
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.TEN);
            var batteryId = ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "carried_test_battery");
            check(BuiltInRegistries.ITEM.containsKey(batteryId), "The shared battery fixture must be registered");
            ItemStack battery = new ItemStack(BuiltInRegistries.ITEM.get(batteryId));
            battery.set(DataComponents.CUSTOM_NAME, Component.literal("Held battery"));
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, battery);
            var energy = battery.getCapability(Capabilities.EnergyStorage.ITEM);
            check(energy != null, "The real held fixture must expose item FE capability");
            var request = new NeoForgePlayerInventoryPayloads.ChargeHeld(menu.containerId);
            NeoForgePlayerInventoryPayloads.handleChargeHeld(player, request);
            check(energy.getEnergyStored() == 0 && exactBalance(player).equals(EmcAmount.of(BigInteger.TEN)),
                    "An unknown energy certificate must prevent both charging and EMC withdrawal");
            check(knowledge.addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample())),
                    "The test player must learn the energy certificate");
            knowledge.setEmc(BigInteger.ZERO);
            NeoForgePlayerInventoryPayloads.handleChargeHeld(player, request);
            check(energy.getEnergyStored() == 0 && exactBalance(player).isZero(),
                    "Insufficient EMC must not transfer FE or leave a reservation");
            knowledge.setEmc(BigInteger.TEN);
            NeoForgePlayerInventoryPayloads.handleChargeHeld(player,
                    new NeoForgePlayerInventoryPayloads.ChargeHeld(menu.containerId + 1));
            check(energy.getEnergyStored() == 0 && exactBalance(player).equals(EmcAmount.of(BigInteger.TEN)),
                    "A stale menu ID must not charge the currently held battery");
            player.containerMenu = player.inventoryMenu;
            NeoForgePlayerInventoryPayloads.handleChargeHeld(player, request);
            check(energy.getEnergyStored() == 0 && exactBalance(player).equals(EmcAmount.of(BigInteger.TEN)),
                    "A request from a closed menu must not charge the currently held battery");
            player.containerMenu = menu;
            NeoForgePlayerInventoryPayloads.handleChargeHeld(player, request);
            ItemStack charged = player.getMainHandItem();
            var chargedEnergy = charged.getCapability(Capabilities.EnergyStorage.ITEM);
            check(chargedEnergy != null && chargedEnergy.getEnergyStored() == 250
                            && exactBalance(player).equals(EmcAmount.of(BigInteger.TEN).subtract(NeoForgeRecoveryPrices.energy().multiply(250)))
                            && charged.is(battery.getItem()) && charged.getCount() == 1
                            && Component.literal("Held battery").equals(charged.get(DataComponents.CUSTOM_NAME))
                            && NeoForgePlayerInventorySavedData.get(server).rules(player.getUUID()).isEmpty(),
                    "Held charging must honor the 250 FE transfer limit, charge exactly 1/4 EMC, preserve components and create no refill rule");
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(previousRatio);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void brokenHeldEnergyCapabilitiesOnlyMutateDetachedCopies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String previousRatio = NeoForgeServerConfig.FE_PER_EMC.get();
        try {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            var menu = open(player, 96);
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null, "ProjectE player knowledge capability must exist");
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.TEN);
            check(knowledge.addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample()))
                            && NeoForgeResourceKnowledge.allows(server, player.getUUID(), Kind.ENERGY),
                    "Energy generation must be authorized so the broken capability reaches its execution path");
            player.getInventory().selected = 0;
            var batteryId = ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "carried_test_battery");
            for (boolean throwsOnExecute : new boolean[]{true, false}) {
                ItemStack battery = new ItemStack(BuiltInRegistries.ITEM.get(batteryId));
                battery.set(DataComponents.CUSTOM_NAME, Component.literal("Faulty held battery"));
                CompoundTag flags = new CompoundTag();
                flags.putBoolean("mutate_energy_simulation", true);
                flags.putBoolean(throwsOnExecute ? "throw_energy_execution" : "overreport_energy_execution", true);
                battery.set(DataComponents.CUSTOM_DATA, CustomData.of(flags));
                ItemStack original = battery.copy();
                player.getInventory().setItem(0, battery);
                NeoForgePlayerInventoryPayloads.handleChargeHeld(player,
                        new NeoForgePlayerInventoryPayloads.ChargeHeld(menu.containerId));
                var energy = player.getMainHandItem().getCapability(Capabilities.EnergyStorage.ITEM);
                check(player.getMainHandItem() == battery && ItemStack.matches(player.getMainHandItem(), original)
                                && energy != null && energy.getEnergyStored() == 0
                                && exactBalance(player).equals(EmcAmount.of(BigInteger.TEN))
                                && NeoForgeEmcSavedData.get(server).debit(player.getUUID()).signum() == 0
                                && NeoForgePlayerInventorySavedData.get(server).rules(player.getUUID()).isEmpty(),
                        "A mutating simulation followed by throwing or over-reporting execution must preserve the complete live stack and refund all EMC");
            }
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(previousRatio);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void packetCodecsBoundSelectionsAndPreserveSamples(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            ItemStack sample = new ItemStack(Items.DIAMOND);
            sample.set(DataComponents.CUSTOM_NAME, Component.literal("Packet sample"));
            var edit = new NeoForgePlayerInventoryPayloads.Edit(95, 35, 17, 40, SampleMode.FIXED_RATE, true, sample);
            NeoForgePlayerInventoryPayloads.Edit.STREAM_CODEC.encode(buffer, edit);
            var decoded = NeoForgePlayerInventoryPayloads.Edit.STREAM_CODEC.decode(buffer);
            check(decoded.containerId() == 95 && decoded.slot() == 35 && decoded.amount() == 17
                            && decoded.intervalTicks() == 40 && decoded.mode() == SampleMode.FIXED_RATE
                            && decoded.enabled() && ItemStack.matches(decoded.filter(), sample),
                    "Rule packets must round-trip exact values and item components");
            buffer.clear();
            int[] slots = {0, 9, 35};
            var enabled = new NeoForgePlayerInventoryPayloads.SetEnabled(95, slots, false);
            var clear = new NeoForgePlayerInventoryPayloads.Clear(95, slots);
            slots[0] = 36;
            enabled.slots()[1] = 36;
            clear.slots()[1] = 36;
            NeoForgePlayerInventoryPayloads.SetEnabled.STREAM_CODEC.encode(buffer, enabled);
            var decodedEnabled = NeoForgePlayerInventoryPayloads.SetEnabled.STREAM_CODEC.decode(buffer);
            check(decodedEnabled.containerId() == 95 && !decodedEnabled.enabled()
                            && Arrays.equals(decodedEnabled.slots(), new int[]{0, 9, 35}),
                    "Enabled selections must defend both constructor and accessor arrays and round-trip every slot");
            buffer.clear();
            NeoForgePlayerInventoryPayloads.Clear.STREAM_CODEC.encode(buffer, clear);
            var decodedClear = NeoForgePlayerInventoryPayloads.Clear.STREAM_CODEC.decode(buffer);
            check(decodedClear.containerId() == 95 && Arrays.equals(decodedClear.slots(), new int[]{0, 9, 35}),
                    "Clear selections must defend both constructor and accessor arrays and round-trip every slot");
            buffer.clear();
            var charge = new NeoForgePlayerInventoryPayloads.ChargeHeld(95);
            NeoForgePlayerInventoryPayloads.ChargeHeld.STREAM_CODEC.encode(buffer, charge);
            check(charge.equals(NeoForgePlayerInventoryPayloads.ChargeHeld.STREAM_CODEC.decode(buffer)),
                    "Charging packets must retain their exact menu ID");
            for (boolean enabledPacket : new boolean[]{true, false}) {
                buffer.clear(); buffer.writeVarInt(95); buffer.writeByte(37);
                expectDecoder(() -> {
                    if (enabledPacket) NeoForgePlayerInventoryPayloads.SetEnabled.STREAM_CODEC.decode(buffer);
                    else NeoForgePlayerInventoryPayloads.Clear.STREAM_CODEC.decode(buffer);
                }, "A 37-slot selection must reject before allocating its advertised array");
                buffer.clear(); buffer.writeVarInt(95); buffer.writeByte(1); buffer.writeByte(36);
                expectDecoder(() -> {
                    if (enabledPacket) NeoForgePlayerInventoryPayloads.SetEnabled.STREAM_CODEC.decode(buffer);
                    else NeoForgePlayerInventoryPayloads.Clear.STREAM_CODEC.decode(buffer);
                }, "Inventory slot 36 must be rejected by both selection codecs");
            }
        } finally { buffer.release(); }
        helper.succeed();
    }

    private static EmcAmount exactBalance(ServerPlayer player) {
        var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        return EmcAmount.of(knowledge.getEmc()).add(NeoForgeEmcSavedData.get(player.server).pending(player.getUUID()));
    }

    private static void expectDecoder(Runnable action, String message) {
        try { action.run(); } catch (DecoderException expected) { return; }
        throw new GameTestAssertException(message);
    }

    private static NeoForgePlayerInventoryMenu open(ServerPlayer player, int id) {
        var menu = new NeoForgePlayerInventoryMenu(id, player.getInventory());
        player.containerMenu = menu;
        return menu;
    }

    private static ServerPlayer login(MinecraftServer server) {
        UUID id = UUID.randomUUID();
        GameProfile profile = new GameProfile(id, "inv-" + id.toString().substring(0, 8));
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
        player.containerMenu = player.inventoryMenu;
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
