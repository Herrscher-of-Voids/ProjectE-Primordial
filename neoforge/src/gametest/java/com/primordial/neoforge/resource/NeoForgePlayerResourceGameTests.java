package com.primordial.neoforge.resource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.machine.NeoForgePlayerInventoryMenu;
import com.primordial.neoforge.machine.NeoForgePlayerInventoryPayloads;
import com.primordial.neoforge.machine.NeoForgePlayerInventorySavedData;
import com.primordial.neoforge.machine.NeoForgePlayerInventoryTask;
import com.primordial.service.EmcAmount;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePlayerResourceGameTests {
    private NeoForgePlayerResourceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void magicFlagsAndAuthoritativeSnapshotsKeepLegacyBounds(GameTestHelper helper) {
        ServerPlayer p = login(helper.getLevel().getServer());
        try {
            var data = new NeoForgePlayerInventorySavedData();
            helper.assertTrue(!data.resourceEnabled(p.getUUID(), 0) && !data.resourceEnabled(p.getUUID(), 1),
                    "Magic supply defaults disabled without deleting inventory rules");
            data.toggleResource(p.getUUID(), 0); data.toggleResource(p.getUUID(), 1);
            data.toggleResource(p.getUUID(), 2);
            var loaded = NeoForgePlayerInventorySavedData.load(data.save(new net.minecraft.nbt.CompoundTag(), p.registryAccess()), p.registryAccess());
            helper.assertTrue(loaded.resourceEnabled(p.getUUID(), 0) && loaded.resourceEnabled(p.getUUID(), 1)
                    && !loaded.resourceEnabled(UUID.randomUUID(), 0) && !loaded.resourceEnabled(p.getUUID(), 2),
                    "Two native account flags persist per owner and reject invalid index");
            var menu = new NeoForgePlayerInventoryMenu(810, p.getInventory());
            p.containerMenu = menu;
            helper.assertTrue(!menu.resourceReady(0), "Client magic controls wait for an authoritative current-menu snapshot");
            var wrong = new NeoForgePlayerInventoryPayloads.ResourceState(811, 1, 20, 100, 2, 30, 200);
            menu.acceptResourceState(wrong);
            helper.assertTrue(!menu.resourceReady(0), "Other menu cannot publish player resource state");
            var state = new NeoForgePlayerInventoryPayloads.ResourceState(810, 1, 20, 100, 2, 30, 200);
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), p.registryAccess());
            try {
                NeoForgePlayerInventoryPayloads.ResourceState.STREAM_CODEC.encode(buf, state);
                menu.acceptResourceState(NeoForgePlayerInventoryPayloads.ResourceState.STREAM_CODEC.decode(buf));
            } finally { buf.release(); }
            helper.assertTrue(menu.resourceReady(0) && menu.resourceState(0) == 1 && menu.resourceStored(1) == 30
                    && menu.resourceCapacity(1) == 200, "Snapshot codec retains both independent resources");
            if (!ModList.get().isLoaded("irons_spellbooks")) helper.assertTrue(NeoForgePlayerResourcePorts.find(p, 0) == null
                    && !menu.clickMenuButton(p, 100), "Absent Iron API cannot toggle or expose a fake account");
            if (!ModList.get().isLoaded("goety")) helper.assertTrue(NeoForgePlayerResourcePorts.find(p, 1) == null
                    && !menu.clickMenuButton(p, 101), "Absent Goety API cannot toggle or expose a fake account");
        } finally { logout(p); }
        helper.succeed();
    }

    @GameTestGenerator public static List<TestFunction> nativePlayers() {
        List<TestFunction> result = new ArrayList<>();
        if (ModList.get().isLoaded("irons_spellbooks")) result.add(test("iron_mana", NeoForgePlayerResourceGameTests::ironMana));
        if (ModList.get().isLoaded("goety")) result.add(test("goety_accounts", NeoForgePlayerResourceGameTests::goetyAccounts));
        return result;
    }
    private static TestFunction test(String id, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction("player_resource", "player_resource." + id, "primordial:capability_proxy_empty", 100, 0L, true, body);
    }
    @SuppressWarnings("unchecked")
    private static void ironMana(GameTestHelper helper) {
        ServerPlayer p = login(helper.getLevel().getServer());
        try {
            Class<?> magic = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Object data = magic.getMethod("getPlayerMagicData", LivingEntity.class).invoke(null, p);
            magic.getMethod("setMana", float.class).invoke(data, 0f);
            var port = NeoForgePlayerResourcePorts.find(p, 0);
            helper.assertTrue(port != null && port.capacity() > 0 && !port.canExtract(), "Native mana account must expose real maximum and purchase only");
            var before = balance(p);
            long wanted = Math.min(25, port.capacity());
            helper.assertTrue(port.receive(wanted, true) == wanted && port.amount() == 0,
                    "Mana simulation cannot set MagicData or fire a mutation");
            long actual = com.primordial.neoforge.trade.NeoForgeResourceTrades.purchaseResource(p.server, p.getUUID(), port.unit(), wanted,
                    units -> port.receive(units, false));
            helper.assertTrue(actual == 0 && port.amount() == 0 && balance(p).equals(before), "Unknown mana must never transfer or debit");
            p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).addKnowledge(ItemInfo.fromStack(Kind.IRON_MANA.sample()));
            actual = com.primordial.neoforge.trade.NeoForgeResourceTrades.purchaseResource(p.server, p.getUUID(), port.unit(), wanted,
                    units -> port.receive(units, false));
            helper.assertTrue(actual == wanted && port.amount() == wanted
                    && balance(p).equals(before.subtract(NeoForgeResourceUnits.price(port.unit()).multiply(wanted))),
                    "Native MagicData increment must charge the exact delivered integer units");
            magic.getMethod("setMana", float.class).invoke(data, 0f);
            Class<?> changeMana = Class.forName("io.redspace.ironsspellbooks.api.events.ChangeManaEvent");
            var mode = new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.PlayerEvent> listener = event -> {
                if (event.getEntity() != p) return;
                if (mode.get() == 0) ((net.neoforged.bus.api.ICancellableEvent) event).setCanceled(true);
                else try { changeMana.getMethod("setNewMana", float.class).invoke(event, .5f); }
                catch (ReflectiveOperationException failed) { throw new IllegalStateException(failed); }
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    (Class<net.neoforged.neoforge.event.entity.player.PlayerEvent>) changeMana, listener);
            try {
                var beforeEvent = balance(p);
                actual = com.primordial.neoforge.trade.NeoForgeResourceTrades.purchaseResource(p.server, p.getUUID(), port.unit(), 20,
                        units -> port.receive(units, false));
                helper.assertTrue(actual == 0 && port.amount() == 0 && balance(p).equals(beforeEvent),
                        "Cancelled native mana event must leave resource and EMC unchanged");
                mode.set(1);
                actual = com.primordial.neoforge.trade.NeoForgeResourceTrades.purchaseResource(p.server, p.getUUID(), port.unit(), 20,
                        units -> port.receive(units, false));
                helper.assertTrue(actual == 1 && ((Number) magic.getMethod("getMana").invoke(data)).floatValue() == .5f
                        && balance(p).equals(beforeEvent.subtract(NeoForgeResourceUnits.price(port.unit()))),
                        "A fractional native event grant is billed as the smallest whole resource unit rather than refunded for free");
            } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); }
            magic.getMethod("setMana", float.class).invoke(data, 0f);
            var menu = new NeoForgePlayerInventoryMenu(812, p.getInventory()); p.containerMenu = menu;
            helper.assertTrue(menu.clickMenuButton(p, 100) && NeoForgePlayerInventorySavedData.get(p.server).resourceEnabled(p.getUUID(), 0),
                    "Owner menu button enables native mana replenishment");
            NeoForgePlayerInventoryTask.forget(p.getUUID());
            NeoForgePlayerInventoryTask.run(p);
            helper.assertTrue(port.amount() == port.capacity(), "Player task must fill real native mana to equipment-defined maximum");
            var filled = balance(p);
            NeoForgePlayerInventoryTask.run(p);
            helper.assertTrue(balance(p).equals(filled), "Repeated same tick/full mana tasks must not debit again");
            p.containerMenu = p.inventoryMenu;
            helper.assertTrue(!menu.clickMenuButton(p, 100), "Closed player menu cannot toggle a native account");
        } catch (ReflectiveOperationException failed) { throw new IllegalStateException("Native Iron test API", failed); }
        finally { logout(p); }
        helper.succeed();
    }
    private static void goetyAccounts(GameTestHelper helper) {
        ServerPlayer p = login(helper.getLevel().getServer());
        try {
            Class<?> helperApi = Class.forName("com.Polarice3.Goety.utils.SEHelper");
            Class<?> totemApi = Class.forName("com.Polarice3.Goety.api.items.magic.ITotem");
            helperApi.getMethod("setSEActive", Player.class, boolean.class).invoke(null, p, false);
            helper.assertTrue(NeoForgePlayerResourcePorts.find(p, 1) == null, "A player with no active Arca or valid Totem must expose no Soul account");
            ItemStack totem = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("goety:totem_of_souls")));
            helper.assertTrue(!totem.isEmpty() && totemApi.isInstance(totem.getItem()), "Native soul totem item must exist");
            totemApi.getMethod("setMaxSoulAmount", ItemStack.class, int.class).invoke(null, totem, 100);
            totemApi.getMethod("setSoulsamount", ItemStack.class, int.class).invoke(null, totem, 10);
            p.getInventory().setItem(0, totem);
            var port = NeoForgePlayerResourcePorts.find(p, 1);
            helper.assertTrue(port != null && port.capacity() == 100 && port.amount() == 10 && port.receive(20, true) == 20
                    && port.amount() == 10, "Goety native TotemFinder and read-only simulation must preserve the actual totem");
            p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).addKnowledge(ItemInfo.fromStack(Kind.SOUL_ENERGY.sample()));
            var before = balance(p);
            long actual = com.primordial.neoforge.trade.NeoForgeResourceTrades.purchaseResource(p.server, p.getUUID(), port.unit(), 20,
                    units -> port.receive(units, false));
            helper.assertTrue(actual == 20 && port.amount() == 30 && balance(p).equals(before.subtract(EmcAmount.parse("1/50"))),
                    "Totem increase uses actual integer delta and exact fractional EMC");
            p.getInventory().setItem(0, ItemStack.EMPTY);
            helper.assertTrue(port.receive(10, false) == 0, "A removed selected totem cannot accept through its old port");
            helperApi.getMethod("setSEActive", Player.class, boolean.class).invoke(null, p, true);
            helperApi.getMethod("setSESouls", Player.class, int.class).invoke(null, p, 50);
            p.getInventory().setItem(0, totem);
            var arca = NeoForgePlayerResourcePorts.find(p, 1);
            helper.assertTrue(arca != null && arca.key().endsWith("arca") && arca.amount() == 50,
                    "Active Arca takes precedence over a carried soul totem");
            long totemBefore = ((Number) totemApi.getMethod("currentSouls", ItemStack.class).invoke(null, totem)).longValue();
            actual = com.primordial.neoforge.trade.NeoForgeResourceTrades.purchaseResource(p.server, p.getUUID(), arca.unit(), 20,
                    units -> arca.receive(units, false));
            helper.assertTrue(actual == 20 && arca.amount() == 70
                    && ((Number) totemApi.getMethod("currentSouls", ItemStack.class).invoke(null, totem)).longValue() == totemBefore,
                    "Arca purchase updates only native player souls without kill bonuses or carried totem writes");
            helperApi.getMethod("setSEActive", Player.class, boolean.class).invoke(null, p, false);
            helper.assertTrue(arca.receive(10, false) == 0, "Account mode changes invalidate the original Arca port");
        } catch (ReflectiveOperationException failed) { throw new IllegalStateException("Native Goety test API", failed); }
        finally { logout(p); }
        helper.succeed();
    }
    private static EmcAmount balance(ServerPlayer p) {
        var ledger = NeoForgeEmcSavedData.get(p.server);
        return EmcAmount.of(p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).getEmc()).add(ledger.pending(p.getUUID()))
                .subtract(EmcAmount.of(ledger.debit(p.getUUID())));
    }
    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "pr-" + UUID.randomUUID().toString().substring(0, 7));
        var info = ClientInformation.createDefault();
        var p = server.getPlayerList().getPlayerForLogin(profile, info);
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, new net.minecraft.server.network.CommonListenerCookie(profile, 0, info, false, ConnectionType.NEOFORGE));
            p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).clearKnowledge();
            p.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.valueOf(1000));
            return p;
        } catch (RuntimeException | Error failed) { channel.finishAndReleaseAll(); throw failed; }
    }
    private static void logout(ServerPlayer p) {
        NeoForgePlayerInventoryTask.forget(p.getUUID());
        var data = NeoForgePlayerInventorySavedData.get(p.server);
        for (int index = 0; index < 2; index++) if (data.resourceEnabled(p.getUUID(), index)) data.toggleResource(p.getUUID(), index);
        try { p.server.getPlayerList().remove(p); }
        finally { if (p.connection != null && p.connection.getConnection().channel() instanceof EmbeddedChannel channel) channel.finishAndReleaseAll(); }
    }
}
