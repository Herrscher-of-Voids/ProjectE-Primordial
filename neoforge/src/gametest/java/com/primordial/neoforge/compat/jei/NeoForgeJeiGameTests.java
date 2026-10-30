/** JEI's real server boundary: bounded packets, knowledge, exact quotes, replay rejection and partial inventory billing. */
package com.primordial.neoforge.compat.jei;

import java.math.BigInteger;
import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.compat.jei.NeoForgeJeiPayloads.*;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeJeiGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private NeoForgeJeiGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void jeiPacketsBoundAllocationsAndPreserveExactPrices(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            var key = new Key(false, WATER);
            var request = new RequestQuotes(Long.MAX_VALUE, List.of(key));
            RequestQuotes.STREAM_CODEC.encode(buffer, request);
            check(request.equals(RequestQuotes.STREAM_CODEC.decode(buffer)), "JEI quote generations and resource IDs must round trip");
            var response = new Quotes(17, List.of(new Entry(key, "1/1000")));
            Quotes.STREAM_CODEC.encode(buffer, response);
            check(response.equals(Quotes.STREAM_CODEC.decode(buffer)), "Fractional media prices must remain exact on the wire");
            var purchase = new Purchase(2, 3, new ItemStack(Items.ENDER_PEARL, 16));
            Purchase.STREAM_CODEC.encode(buffer, purchase);
            Purchase decoded = Purchase.STREAM_CODEC.decode(buffer);
            check(decoded.menuId() == 2 && decoded.sequence() == 3 && decoded.sample().is(Items.ENDER_PEARL)
                    && decoded.sample().getCount() == 1, "Purchase packets carry a sample, never a client-controlled delivery quantity");
            ItemStack copy = purchase.sample(); copy.setCount(64);
            check(purchase.sample().getCount() == 1, "Mutating a caller's sample copy must not change the pending purchase");
            buffer.clear(); buffer.writeVarLong(1); buffer.writeVarInt(Integer.MAX_VALUE);
            reject(() -> RequestQuotes.STREAM_CODEC.decode(buffer), "Huge quote requests must reject before allocation");
            buffer.clear(); buffer.writeVarLong(1); buffer.writeVarInt(-1);
            reject(() -> Quotes.STREAM_CODEC.decode(buffer), "Negative response lengths must reject before allocation");
            buffer.clear(); buffer.writeVarInt(2); buffer.writeVarLong(-1);
            reject(() -> Purchase.STREAM_CODEC.decode(buffer), "Negative purchase generations must reject before item decoding");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void jeiPurchasesBillPartialDeliveryAndRejectUnknownReplayedOrUnsafeSamples(GameTestHelper helper) {
        var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        try {
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            EmcAmount price = NeoForgeRecoveryPrices.itemPurchase(diamond);
            check(!price.isZero(), "Real ProjectE diamond mapping must be present");
            knowledge.setEmc(price.ceil().multiply(BigInteger.valueOf(1000)));
            EmcAmount before = NeoForgeTestPlayers.balance(player);
            int menu = player.containerMenu.containerId;
            check(NeoForgeJeiPayloads.purchaseFor(player, new Purchase(menu, 1, diamond)) == 0
                    && before.equals(NeoForgeTestPlayers.balance(player)), "An unknown item must never be delivered or billed");
            knowledge.addKnowledge(ItemInfo.fromStack(diamond));
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.BEDROCK, 64));
            player.getInventory().setItem(7, new ItemStack(Items.DIAMOND, 62));
            var request = new Purchase(menu, 2, diamond);
            check(NeoForgeJeiPayloads.purchaseFor(player, request) == 2 && player.getInventory().getItem(7).getCount() == 64
                    && NeoForgeTestPlayers.balance(player).equals(before.subtract(price.multiply(2))),
                    "A full inventory with room for two diamonds must receive and pay for exactly two");
            EmcAmount partial = NeoForgeTestPlayers.balance(player);
            check(NeoForgeJeiPayloads.purchaseFor(player, request) == 0
                    && NeoForgeJeiPayloads.purchaseFor(player, new Purchase(menu + 1, 3, diamond)) == 0
                    && NeoForgeJeiPayloads.purchaseFor(player, new Purchase(menu, 3, diamond)) == 0
                    && partial.equals(NeoForgeTestPlayers.balance(player)), "Replays, stale menus and full inventories must charge nothing");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            ItemStack pearls = new ItemStack(Items.ENDER_PEARL);
            knowledge.addKnowledge(ItemInfo.fromStack(pearls));
            EmcAmount pearlPrice = NeoForgeRecoveryPrices.itemPurchase(pearls);
            check(NeoForgeJeiPayloads.purchaseFor(player, new Purchase(menu, 4, pearls)) == 16
                    && player.getInventory().getItem(0).getCount() == 16
                    && NeoForgeTestPlayers.balance(player).equals(partial.subtract(pearlPrice.multiply(16))),
                    "Shift purchase must respect a non-64 maximum stack size and charge actual units");
            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            knowledge.addKnowledge(ItemInfo.fromStack(box));
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
            before = NeoForgeTestPlayers.balance(player);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            check(NeoForgeJeiPayloads.purchaseFor(player, new Purchase(menu, 5, box)) == 0
                    && player.getInventory().getItem(0).isEmpty() && NeoForgeTestPlayers.balance(player).equals(before),
                    "Knowledge of an empty container must not copy unpaid contents from a forged JEI sample");
            NeoForgeResourceTrades.purchaseItem(player.server, player.getUUID(), diamond, 1, units -> {
                check(NeoForgeResourceTrades.purchaseItemPrice(player.server, player.getUUID(), diamond).isZero(),
                        "Read-only prices must obey the same account reentry guard as purchases");
                return 0;
            });
            before = NeoForgeTestPlayers.balance(player);
            player.server.getPlayerList().remove(player);
            check(NeoForgeJeiPayloads.purchaseFor(player, new Purchase(menu, 6, diamond)) == 0
                    && player.getInventory().getItem(0).isEmpty()
                    && before.equals(com.primordial.neoforge.emc.NeoForgeEmcTransactions.balance(player.server, player.getUUID())),
                    "A purchase queued after logout must not bill an offline account for delivery into an obsolete player inventory");
        } finally { NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void jeiMediaQuotesRequireRealKnowledgeAndExpireAuthorizationAfterForgetting(GameTestHelper helper) {
        var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        try {
            var key = new Key(false, WATER);
            EmcAmount before = NeoForgeTestPlayers.balance(player);
            check(NeoForgeJeiPayloads.quote(player, key).isZero(), "Unlearned water must not display an authorized exchange count");
            check(NeoForgeResourceKnowledge.learnMedia(player.server, player.getUUID(), Kind.FLUID, WATER), "Water must learn through the real ledger");
            EmcAmount price = NeoForgeJeiPayloads.quote(player, key);
            check(!price.isZero() && before.equals(NeoForgeTestPlayers.balance(player)), "An authorized exact quote must never debit EMC");
            var response = NeoForgeJeiPayloads.quotesFor(player, new RequestQuotes(9, List.of(key)));
            check(response != null && response.sequence() == 9 && response.entries().getFirst().price().equals(price.toString())
                    && NeoForgeJeiPayloads.quotesFor(player, new RequestQuotes(10, List.of(key))) == null,
                    "Batch quotes must preserve identity and bound repeated work to one request per ten ticks");
            check(NeoForgeResourceKnowledge.forgetMedia(player.server, player.getUUID(), Kind.FLUID, WATER)
                    && NeoForgeJeiPayloads.quote(player, key).isZero(), "A forgotten medium must lose quote authorization immediately");
            check(NeoForgeJeiPayloads.quote(player, new Key(false, ResourceLocation.parse("minecraft:flowing_water"))).isZero()
                    && NeoForgeJeiPayloads.quote(player, new Key(true, ResourceLocation.parse("missing:chemical"))).isZero(),
                    "Flowing fluids and unknown optional chemicals must never display purchasable counts");
        } finally { NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    private static void reject(Runnable action, String message) {
        try { action.run(); } catch (DecoderException expected) { return; }
        throw new GameTestAssertException(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
