package com.primordial.neoforge.generator;

import java.math.BigInteger;
import java.util.List;
import java.util.function.Consumer;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeEntityRecovery;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import com.primordial.service.EmcAmount;
import io.netty.buffer.Unpooled;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEntityGeneratorGameTests {
    private NeoForgeEntityGeneratorGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void catalogueAndCodecUseBoundedServerOffers(GameTestHelper helper) {
        ServerPlayer player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        try {
            var offers = NeoForgeEntityGeneratorService.catalog(helper.getLevel());
            helper.assertTrue(!offers.isEmpty() && offers.size() <= NeoForgeEntityGeneratorService.MAX_OFFERS,
                    "The server catalogue must be finite and contain actual purchasable entities");
            String previous = "";
            for (var offer : offers) {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(offer.type()).toString();
                helper.assertTrue(id.compareTo(previous) >= 0 && offer.price().signum() > 0
                        && offer.type().canSummon() && offer.type().canSerialize()
                        && offer.type() != EntityType.PLAYER && offer.type() != EntityType.ITEM
                        && offer.type() != EntityType.EXPERIENCE_ORB && offer.type() != EntityType.ARROW,
                        "Catalogue entries must be sorted, spawnable, serializable and safely priced");
                previous = id;
            }
            var quote = pig(helper);
            helper.assertTrue(offers.stream().anyMatch(o -> o.type() == EntityType.PIG && o.price().equals(quote.price())),
                    "Generator and blackhole recovery must use the same base-entity price");
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                buf.writeBlockPos(BlockPos.ZERO);
                NeoForgeEntityGeneratorMenu.writeOffers(buf, offers);
                var decoded = new NeoForgeEntityGeneratorMenu(940, player.getInventory(), buf);
                helper.assertTrue(decoded.offers().equals(offers), "The opening codec preserves exact server-owned quotes");
                buf.clear(); buf.writeBlockPos(BlockPos.ZERO); buf.writeVarInt(NeoForgeEntityGeneratorService.MAX_OFFERS + 1);
                helper.assertTrue(rejected(() -> new NeoForgeEntityGeneratorMenu(941, player.getInventory(), buf)),
                        "Oversized catalogue payloads are rejected before allocation");
                buf.clear(); buf.writeBlockPos(BlockPos.ZERO); buf.writeVarInt(1);
                buf.writeResourceLocation(ResourceLocation.parse("primordial:missing_entity")); buf.writeUtf("1", 128);
                helper.assertTrue(rejected(() -> new NeoForgeEntityGeneratorMenu(942, player.getInventory(), buf)),
                        "Unknown entity IDs cannot decode as a registry fallback");
                buf.clear(); buf.writeBlockPos(BlockPos.ZERO); buf.writeVarInt(1);
                buf.writeResourceLocation(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG)); buf.writeUtf("0", 128);
                helper.assertTrue(rejected(() -> new NeoForgeEntityGeneratorMenu(943, player.getInventory(), buf)),
                        "Nonpositive menu quotes are rejected");
            } finally { buf.release(); }
        } finally { NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void operatorBuysOneFreshEntityAtExactPrice(GameTestHelper helper) {
        ServerPlayer owner = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        ServerPlayer other = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        BlockPos generator = setup(helper, owner);
        other.setPos(generator.getX() + 3.5, generator.getY() + 1, generator.getZ() + .5);
        try {
            var quote = pig(helper);
            var before = NeoForgeTestPlayers.balance(owner);
            var otherBefore = NeoForgeTestPlayers.balance(other);
            var menu = new NeoForgeEntityGeneratorMenu(944, owner.getInventory(), generator, List.of(quote));
            owner.containerMenu = menu;
            helper.assertTrue(menu.clickMenuButton(owner, 0) && menu.feedback() == 1 && pigs(helper, generator).size() == 1,
                    "The current operator menu purchases exactly one real fresh pig without an extra knowledge gate");
            helper.assertTrue(NeoForgeTestPlayers.balance(owner).equals(before.subtract(EmcAmount.of(quote.price())))
                    && NeoForgeTestPlayers.balance(other).equals(otherBefore),
                    "Only the operator pays the exact server price");
            var after = NeoForgeTestPlayers.balance(owner);
            helper.assertTrue(!menu.clickMenuButton(owner, 0) && menu.feedback() == 2 && pigs(helper, generator).size() == 1
                    && NeoForgeTestPlayers.balance(owner).equals(after), "Occupied spawn space cannot duplicate entities or debit again");
        } finally {
            pigs(helper, generator).forEach(Entity::discard);
            NeoForgeTestPlayers.logout(other); NeoForgeTestPlayers.logout(owner);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void staleForeignAndInvalidMenuRequestsDoNotSpend(GameTestHelper helper) {
        ServerPlayer owner = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        ServerPlayer other = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        BlockPos generator = setup(helper, owner);
        try {
            var quote = pig(helper);
            var before = NeoForgeTestPlayers.balance(owner);
            var menu = new NeoForgeEntityGeneratorMenu(945, owner.getInventory(), generator, List.of(quote));
            owner.containerMenu = menu; other.containerMenu = menu;
            helper.assertTrue(!menu.clickMenuButton(other, 0) && !menu.clickMenuButton(owner, -1)
                    && !menu.clickMenuButton(owner, 1), "Foreign owners and out-of-range buttons cannot buy");
            owner.containerMenu = owner.inventoryMenu;
            helper.assertTrue(!menu.clickMenuButton(owner, 0), "Closed menus cannot buy");
            owner.containerMenu = menu;
            owner.setPos(generator.getX() + 10.5, generator.getY() + 1, generator.getZ() + .5);
            helper.assertTrue(!menu.clickMenuButton(owner, 0), "Remote owners cannot buy through an old menu");
            owner.setPos(generator.getX() + 2.5, generator.getY() + 1, generator.getZ() + .5);
            helper.assertTrue(!NeoForgeEntityGeneratorService.spawn(owner, generator,
                    new NeoForgeEntityGeneratorService.Offer(EntityType.PIG, quote.price().add(BigInteger.ONE))),
                    "A stale or forged quote must match the freshly created entity price before debit");
            helper.getLevel().setBlockAndUpdate(generator, Blocks.AIR.defaultBlockState());
            helper.assertTrue(!menu.clickMenuButton(owner, 0) && !NeoForgeEntityGeneratorService.spawn(owner, generator, quote),
                    "Breaking the original generator invalidates menu and service purchases");
            helper.assertTrue(NeoForgeTestPlayers.balance(owner).equals(before) && pigs(helper, generator).isEmpty(),
                    "Rejected requests must neither debit EMC nor add an entity");
        } finally { NeoForgeTestPlayers.logout(other); NeoForgeTestPlayers.logout(owner); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void blockedOrUnaffordableEntitiesLeaveBalanceIntact(GameTestHelper helper) {
        ServerPlayer player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        BlockPos generator = setup(helper, player);
        try {
            var quote = pig(helper);
            var before = NeoForgeTestPlayers.balance(player);
            helper.getLevel().setBlockAndUpdate(generator.above(), Blocks.STONE.defaultBlockState());
            helper.assertTrue(!NeoForgeEntityGeneratorService.spawn(player, generator, quote)
                    && NeoForgeTestPlayers.balance(player).equals(before), "A colliding fresh entity cannot spend EMC");
            helper.getLevel().setBlockAndUpdate(generator.above(), Blocks.AIR.defaultBlockState());
            player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.ZERO);
            helper.assertTrue(!NeoForgeEntityGeneratorService.spawn(player, generator, quote)
                    && NeoForgeTestPlayers.balance(player).isZero() && pigs(helper, generator).isEmpty(),
                    "An unaffordable entity must not be delivered or produce a negative balance");
        } finally { NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void cancelledJoinRefundsAndRejectsReentry(GameTestHelper helper) {
        ServerPlayer player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        BlockPos generator = setup(helper, player);
        var quote = pig(helper);
        var seen = new java.util.concurrent.atomic.AtomicBoolean();
        var reentered = new java.util.concurrent.atomic.AtomicBoolean();
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (!(event.getEntity() instanceof Pig) || event.getLevel() != helper.getLevel()
                    || !area(generator).contains(event.getEntity().position())) return;
            seen.set(true);
            reentered.set(NeoForgeEntityGeneratorService.spawn(player, generator, quote));
            event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, cancel);
        try {
            var before = NeoForgeTestPlayers.balance(player);
            helper.assertTrue(!NeoForgeEntityGeneratorService.spawn(player, generator, quote) && seen.get() && !reentered.get(),
                    "A cancelled native join fails delivery and cannot reenter the paying account");
            helper.assertTrue(pigs(helper, generator).isEmpty() && NeoForgeTestPlayers.balance(player).equals(before),
                    "Confirmed zero delivery refunds the full exact reservation without leaving an entity");
        } finally { NeoForge.EVENT_BUS.unregister(cancel); NeoForgeTestPlayers.logout(player); }
        helper.succeed();
    }

    private static BlockPos setup(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, NeoForgeModRegistries.ENTITY_GENERATOR.get().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.above(2), Blocks.AIR.defaultBlockState());
        player.setPos(pos.getX() + 2.5, pos.getY() + 1, pos.getZ() + .5);
        return pos;
    }

    private static NeoForgeEntityGeneratorService.Offer pig(GameTestHelper helper) {
        var pig = EntityType.PIG.create(helper.getLevel());
        return new NeoForgeEntityGeneratorService.Offer(EntityType.PIG, NeoForgeEntityRecovery.price(pig));
    }
    private static AABB area(BlockPos pos) { return new AABB(pos.above()).inflate(.25, 1, .25); }
    private static List<Pig> pigs(GameTestHelper helper, BlockPos pos) { return helper.getLevel().getEntitiesOfClass(Pig.class, area(pos)); }
    private static boolean rejected(Runnable action) {
        try { action.run(); return false; } catch (IllegalArgumentException expected) { return true; }
    }
}
