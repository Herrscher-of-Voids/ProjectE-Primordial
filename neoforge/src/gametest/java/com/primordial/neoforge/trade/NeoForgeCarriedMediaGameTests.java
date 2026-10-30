/** Real native-menu cursor transactions and exact ProjectE-account assertions. Test fixtures stay out of production. */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.compat.NeoForgeCarriedMekanismBridge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.transmutation.*;
import com.primordial.neoforge.transmutation.NeoForgeMediaTradeResult.Status;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.neoforge.value.NeoForgePriceEditing;
import com.primordial.service.EmcAmount;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeCarriedMediaGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final BigInteger INITIAL = BigInteger.valueOf(1000000000);
    private static TransmutationContainer menu(ServerPlayer player, ItemStack carried) {
        var menu = new TransmutationContainer(96, player.getInventory());
        player.containerMenu = menu;
        menu.setCarried(carried);
        knowledge(player).setEmc(INITIAL);
        NeoForgeResourceKnowledge.learnMedia(player.server, player.getUUID(), Kind.FLUID, WATER);
        return menu;
    }
    private static EmcAmount balance(ServerPlayer player) {
        return EmcAmount.of(knowledge(player).getEmc()).add(NeoForgeEmcSavedData.get(player.server).pending(player.getUUID()));
    }
    private static NeoForgeMediaTradeResult trade(ServerPlayer player, boolean buy) {
        return NeoForgeTransmutationMediaTrade.trade(player, 96, buy, false, buy ? WATER : null);
    }
    private static NeoForgeMediaTradeResult tradeEnergy(ServerPlayer player, boolean buy) {
        return NeoForgeTransmutationMediaTrade.trade(player, 96, buy, false, Kind.ENERGY.unit());
    }
    private static EmcAmount waterPrice(boolean buy) {
        var sample = new FluidStack(Fluids.WATER, 1);
        return buy ? NeoForgeRecoveryPrices.fluidPurchase(sample) : NeoForgeRecoveryPrices.fluid(sample);
    }
    private static void close(ServerPlayer player) {
        CarriedMediaTestFixture.onExecute = null;
        player.containerMenu.removed(player);
        player.containerMenu = player.inventoryMenu;
        logout(player.server, player);
    }

    @GameTest(template = "capability_proxy_empty")
    public static void bucketPurchaseAndRecoveryReturnRealShellAndSettleExactValue(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        try {
            var menu = menu(player, new ItemStack(Items.BUCKET));
            var bought = trade(player, true);
            check(bought.traded() && bought.units() == 1000 && menu.getCarried().is(Items.WATER_BUCKET), "Purchase must deliver a real water bucket");
            check(bought.emc().equals(waterPrice(true).multiply(1000).toString())
                    && balance(player).equals(EmcAmount.of(INITIAL).subtract(waterPrice(true).multiply(1000))), "Receipt and account must charge the actual bucket volume");
            NeoForgeResourceKnowledge.forgetMedia(player.server, player.getUUID(), Kind.FLUID, WATER);
            var sold = trade(player, false);
            check(sold.traded() && sold.units() == 1000 && menu.getCarried().is(Items.BUCKET), "Recovery must return the empty vanilla bucket");
            check(NeoForgeResourceKnowledge.allowsMedia(player.server, player.getUUID(), Kind.FLUID, WATER), "Only real recovery must relearn forgotten water");
            check(balance(player).equals(EmcAmount.of(INITIAL).subtract(waterPrice(true).multiply(1000)).add(waterPrice(false).multiply(1000))), "Recovery must pay the actual volume exactly once");
        } finally { close(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void stackedBucketsSplitOneAndFullInventoryRefundsWithoutChangingOriginal(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        try {
            var menu = menu(player, new ItemStack(Items.BUCKET, 3));
            for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            var rejected = trade(player, true);
            check(rejected.status() == Status.INVENTORY_FULL && menu.getCarried().is(Items.BUCKET) && menu.getCarried().getCount() == 3
                    && balance(player).equals(EmcAmount.of(INITIAL)), "No space must refund the reservation and retain all original buckets");
            player.getInventory().setItem(12, ItemStack.EMPTY);
            check(trade(player, true).traded() && menu.getCarried().getCount() == 2 && menu.getCarried().is(Items.BUCKET)
                    && player.getInventory().getItem(12).is(Items.WATER_BUCKET), "Exactly one completed bucket must go to the available main-inventory slot");
        } finally { close(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void indivisibleBucketCannotConsumePartialFundingOrBypassKnowledge(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        try {
            var menu = menu(player, new ItemStack(Items.BUCKET));
            NeoForgeResourceKnowledge.forgetMedia(player.server, player.getUUID(), Kind.FLUID, WATER);
            check(trade(player, true).status() == Status.UNKNOWN && menu.getCarried().is(Items.BUCKET)
                    && balance(player).equals(EmcAmount.of(INITIAL)), "A funded but explicitly forgotten resource must not be bought");
            NeoForgeResourceKnowledge.learnMedia(player.server, player.getUUID(), Kind.FLUID, WATER);
            BigInteger funding = waterPrice(true).multiply(500).floor();
            knowledge(player).setEmc(funding);
            check(!trade(player, true).traded() && menu.getCarried().is(Items.BUCKET) && balance(player).equals(EmcAmount.of(funding)),
                    "An indivisible bucket must not swallow an affordable partial fill or its reservation");
        } finally { close(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void energyContainerRequiresCertificateAndSettlesActualFeExactly(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        String previousRatio = NeoForgeServerConfig.FE_PER_EMC.get();
        try {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            var menu = menu(player, CarriedMediaTestFixture.battery(0));
            var original = menu.getCarried().copy();
            check(tradeEnergy(player, true).status() == Status.UNKNOWN
                    && ItemStack.matches(menu.getCarried(), original) && balance(player).equals(EmcAmount.of(INITIAL)),
                    "An unlearned energy certificate must not charge an item battery");
            check(knowledge(player).addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample())),
                    "The real ProjectE knowledge attachment must learn the energy certificate");

            var bought = tradeEnergy(player, true);
            check(bought.traded() && bought.units() == 250 && CarriedMediaTestFixture.energy(menu.getCarried()) == 250,
                    "The detached battery must receive only its actual 250 FE capability transfer");
            check(bought.emc().equals("1/4")
                    && balance(player).equals(EmcAmount.of(INITIAL).subtract(NeoForgeRecoveryPrices.energy().multiply(250))),
                    "The FE receipt and ProjectE account must charge exactly one quarter EMC");

            var reclaimed = tradeEnergy(player, false);
            check(reclaimed.traded() && reclaimed.units() == 250 && CarriedMediaTestFixture.energy(menu.getCarried()) == 0,
                    "Recovery must extract the actual stored FE and return the empty battery");
            check(reclaimed.emc().equals("1/4") && balance(player).equals(EmcAmount.of(INITIAL)),
                    "Recovery must credit the exact FE value and restore the original balance");
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(previousRatio);
            close(player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void partialCapabilityExecutionChargesOnlyDeliveredVolumeAndPreservesComponents(GameTestHelper helper) throws Exception {
        var player = login(helper.getLevel().getServer());
        NeoForgeRecoveryPrices.reload();
        var priceFile = FMLPaths.CONFIGDIR.get().resolve("primordial_values.json");
        byte[] oldPrices = Files.readAllBytes(priceFile);
        try {
            check(NeoForgePriceEditing.mutate(player.server.createCommandSourceStack(), (byte) 1, WATER, "1/3").success(),
                    "The public administrator service must establish an exact fractional bucket price");
            ItemStack original = CarriedMediaTestFixture.stack(CarriedMediaTestFixture.PARTIAL);
            original.set(DataComponents.CUSTOM_NAME, Component.literal("Keep this name"));
            var menu = menu(player, original);
            var bought = trade(player, true);
            check(bought.units() == 250 && CarriedMediaTestFixture.amount(menu.getCarried()) == 250
                    && CarriedMediaTestFixture.amount(original) == 0 && menu.getCarried().getHoverName().getString().equals("Keep this name"),
                    "Only the completed detached copy may replace the named original");
            check(balance(player).equals(EmcAmount.of(INITIAL).subtract(waterPrice(true).multiply(250)))
                    && bought.emc().equals("1/12"), "Unused simulated capacity must be refunded, leaving the exact delivered cost of 1/12 EMC");
            check(trade(player, false).units() == 250 && CarriedMediaTestFixture.amount(menu.getCarried()) == 0, "Partial handler must also return its emptied shell");
            check(balance(player).equals(EmcAmount.of(INITIAL)), "Fractional purchase change and recovery credit must recombine exactly");
        } finally {
            try { Files.write(priceFile, oldPrices); NeoForgeRecoveryPrices.reload(); }
            finally { close(player); }
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void failedOrOverReportingDetachedHandlersCannotConsumeBalanceOrPublishCopies(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        try {
            var menu = menu(player, ItemStack.EMPTY);
            for (var id : List.of(CarriedMediaTestFixture.THROWING, CarriedMediaTestFixture.EXCESS)) {
                ItemStack original = CarriedMediaTestFixture.stack(id);
                menu.setCarried(original);
                check(trade(player, true).status() == Status.ERROR && ItemStack.matches(menu.getCarried(), original)
                        && CarriedMediaTestFixture.amount(menu.getCarried()) == 0 && balance(player).equals(EmcAmount.of(INITIAL)),
                        "Detached capability errors or excess reports must discard the mutated copy and refund completely");
            }
        } finally { close(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void reentryAndChangedCursorCannotOverwriteTheLiveContainer(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        try {
            var menu = menu(player, CarriedMediaTestFixture.stack(CarriedMediaTestFixture.PARTIAL));
            CarriedMediaTestFixture.onExecute = () -> {
                check(trade(player, true).status() == Status.NO_TRANSFER, "Reentrant trade must not acquire another reservation");
                menu.setCarried(new ItemStack(Items.DIAMOND));
            };
            check(!trade(player, true).traded() && menu.getCarried().is(Items.DIAMOND) && balance(player).equals(EmcAmount.of(INITIAL)),
                    "A changed live cursor must win over the detached result and refund the reservation");
            menu.setCarried(CarriedMediaTestFixture.stack(CarriedMediaTestFixture.PARTIAL));
            CarriedMediaTestFixture.onExecute = () -> player.containerMenu = player.inventoryMenu;
            check(!trade(player, true).traded() && CarriedMediaTestFixture.amount(menu.getCarried()) == 0 && balance(player).equals(EmcAmount.of(INITIAL)),
                    "Replacing the live menu must reject publication even when its original cursor still matches");
            player.containerMenu = menu;
        } finally { close(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void protocolRejectsReplaysWrongMenusAndNegativeQuantities(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            var menu = menu(player, new ItemStack(Items.BUCKET, 3));
            var request = new NeoForgeMediaTradePayloads.Trade(96, true, false, WATER.toString(), 15);
            NeoForgeMediaTradePayloads.Trade.STREAM_CODEC.encode(buffer, request);
            check(request.equals(NeoForgeMediaTradePayloads.Trade.STREAM_CODEC.decode(buffer)), "Trade intent must round trip without client quantities or prices");
            var receipt = NeoForgeMediaTradePayloads.tradeFor(player, request);
            check(receipt != null && receipt.result().traded(), "Current authenticated native menu must execute once");
            NeoForgeMediaTradePayloads.Receipt.STREAM_CODEC.encode(buffer, receipt);
            check(receipt.equals(NeoForgeMediaTradePayloads.Receipt.STREAM_CODEC.decode(buffer)), "Actual volume, exact EMC and request identity must round trip");
            EmcAmount after = balance(player);
            check(NeoForgeMediaTradePayloads.tradeFor(player, request) == null && menu.getCarried().getCount() == 2 && balance(player).equals(after), "Replaying the sequence must not buy a second bucket");
            check(NeoForgeMediaTradePayloads.tradeFor(player, new NeoForgeMediaTradePayloads.Trade(97, true, false, WATER.toString(), 16)) == null,
                    "A stale or unrelated menu ID must never trade");
            buffer.clear();
            buffer.writeVarInt(96); buffer.writeBoolean(true); buffer.writeBoolean(false); buffer.writeVarLong(16);
            buffer.writeEnum(Status.SUCCESS); buffer.writeUtf(WATER.toString()); buffer.writeVarLong(-1); buffer.writeUtf("1");
            boolean rejected = false;
            try { NeoForgeMediaTradePayloads.Receipt.STREAM_CODEC.decode(buffer); } catch (DecoderException expected) { rejected = true; }
            check(rejected, "Negative receipt quantities must be rejected at the codec");
            player.containerMenu = player.inventoryMenu;
            check(NeoForgeMediaTradePayloads.tradeFor(player, new NeoForgeMediaTradePayloads.Trade(96, true, false, WATER.toString(), 16)) == null, "Closing the menu must reject delayed transactions");
            player.containerMenu = menu;
        } finally { buffer.release(); close(player); }
        helper.succeed();
    }

    @GameTestGenerator
    public static List<TestFunction> optionalTanks() {
        if (!ModList.get().isLoaded("mekanism")) return List.of();
        return List.of(new TestFunction("defaultBatch", "carried_media.real_mekanism_tanks", "primordial:capability_proxy_empty", 100, 0, true,
                NeoForgeCarriedMediaGameTests::realMekanismTanks));
    }
    private static void realMekanismTanks(GameTestHelper helper) {
        var player = login(helper.getLevel().getServer());
        try {
            var tank = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:basic_fluid_tank")));
            var menu = menu(player, tank);
            var handler = tank.getCapability(Capabilities.FluidHandler.ITEM);
            check(handler != null && handler.getTankCapacity(0) > 1000, "Actual Mekanism item must expose its fluid tank");
            int capacity = handler.getTankCapacity(0);
            check(trade(player, true).units() == capacity && trade(player, false).units() == capacity,
                    "Manual interaction must fill and empty the actual tank capacity without its automation rate cap");
            var hydrogen = ResourceLocation.parse("mekanism:hydrogen");
            NeoForgeResourceKnowledge.learnMedia(player.server, player.getUUID(), Kind.GAS, hydrogen);
            var chemicalTank = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:basic_chemical_tank")));
            menu.setCarried(chemicalTank);
            var port = NeoForgeCarriedMekanismBridge.open(chemicalTank.copy(), hydrogen);
            check(port != null, "Actual Mekanism chemical tank must expose the official unified capability");
            long chemicalCapacity = port.move(Long.MAX_VALUE, true, true);
            check(chemicalCapacity > 1000, "Chemical simulation must see full real tank capacity");
            EmcAmount before = balance(player);
            var bought = NeoForgeTransmutationMediaTrade.trade(player, 96, true, true, hydrogen);
            check(bought.units() == chemicalCapacity, "Manual chemical fill must bypass the automation rate cap");
            var sold = NeoForgeTransmutationMediaTrade.trade(player, 96, false, true, null);
            check(sold.units() == chemicalCapacity && NeoForgeCarriedMekanismBridge.open(menu.getCarried().copy(), null).id() == null,
                    "Actual chemical recovery must empty and retain the real tank shell");
            EmcAmount buyPrice = NeoForgeRecoveryPrices.chemicalPurchase(hydrogen, NeoForgeMediaRegistry.chemicalTags(hydrogen));
            EmcAmount sellPrice = NeoForgeRecoveryPrices.chemical(hydrogen, NeoForgeMediaRegistry.chemicalTags(hydrogen));
            check(balance(player).equals(before.subtract(buyPrice.multiply(chemicalCapacity)).add(sellPrice.multiply(chemicalCapacity))), "Chemical balance must settle the full actual volumes");
        } finally { close(player); }
        helper.succeed();
    }
}
