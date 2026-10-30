package com.primordial.neoforge.crafting;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcTransactions;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeKnowledgeCraftGameTests {
    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void mutatingDynamicRecipeCannotAlterThePaidPlan(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var original = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        ResourceLocation id = ResourceLocation.parse("primordial:mutating_crafting_probe");
        var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.CRAFTING_TABLE),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS))) {
            @Override public boolean matches(CraftingInput input, Level level) {
                if (input.isEmpty()) return false;
                input.getItem(0).setCount(64);
                return true;
            }
        };
        var recipes = new ArrayList<>(original);
        recipes.add(new RecipeHolder<>(id, recipe));
        manager.replaceRecipes(recipes);
        try (var fixture = new Fixture(helper)) {
            fixture.learn(new ItemStack(Items.OAK_PLANKS));
            check(!NeoForgeKnowledgeCraftPayload.transfer(fixture.player,
                    new NeoForgeKnowledgeCraftPayload(fixture.menu.containerId, id, false)),
                    "A recipe that mutates the probe must not inflate a one-item paid plan");
            check(fixture.balance().equals(BigInteger.valueOf(100)) && fixture.menu.getSlot(63).getItem().isEmpty(),
                    "A rejected dynamic callback must preserve balance and native input slots");
        } finally { manager.replaceRecipes(original); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativeRecipeReusesThreePlanksAndBuysOnlyOne(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.learn(new ItemStack(Items.OAK_PLANKS));
            fixture.menu.getSlot(27).set(new ItemStack(Items.OAK_PLANKS, 3));
            check(fixture.transfer(false), "Known missing material must fill the actual native crafting grid");
            check(fixture.balance().equals(BigInteger.valueOf(92)), "Only one missing plank costs eight EMC");
            check(fixture.menu.getSlot(27).getItem().isEmpty(), "Physical inputs must be consumed before buying");
            check(fixture.menu.getSlot(72).getItem().is(Items.CRAFTING_TABLE), "Native result callbacks must assemble the real recipe");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void unknownInsufficientOrNoReturnSpaceDoesNotSpendOrMoveInputs(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.menu.getSlot(27).set(new ItemStack(Items.OAK_PLANKS, 3));
            check(!fixture.transfer(false), "Unknown missing inputs must not be bought");
            fixture.learn(new ItemStack(Items.OAK_PLANKS));
            fixture.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.valueOf(7));
            check(!fixture.transfer(false), "Insufficient balance must reject the entire plan");
            check(fixture.menu.getSlot(27).getItem().getCount() == 3 && fixture.balance().equals(BigInteger.valueOf(7)),
                    "Rejected plans must preserve inventory and balance");
            fixture.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.valueOf(100));
            for (int i = 27; i < 63; i++) fixture.menu.getSlot(i).set(new ItemStack(Items.COBBLESTONE, 64));
            fixture.menu.getSlot(63).set(new ItemStack(Items.DIAMOND));
            check(!fixture.transfer(false), "Unrelated grid contents require real return space");
            check(fixture.menu.getSlot(63).getItem().is(Items.DIAMOND) && fixture.balance().equals(BigInteger.valueOf(100)),
                    "A full inventory must not discard leftovers or charge");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void maximumTransferPreservesLearnedComponentsAndExactCost(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            ItemStack named = new ItemStack(Items.OAK_PLANKS);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("已学材料"));
            fixture.learn(named);
            fixture.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.valueOf(320));
            check(fixture.transfer(true), "Maximum must find ten affordable complete recipes");
            for (int index : new int[]{63, 64, 66, 67}) {
                ItemStack stack = fixture.menu.getSlot(index).getItem();
                check(stack.getCount() == 10 && ItemStack.isSameItemSameComponents(stack, named),
                        "Every purchased ingredient must retain the learned safe components");
            }
            check(fixture.balance().signum() == 0, "Forty planks cost exactly 320 EMC");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void staleMenusNonemptyCursorAndInvalidRecipeAreRejected(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            fixture.learn(new ItemStack(Items.OAK_PLANKS));
            check(!NeoForgeKnowledgeCraftPayload.transfer(fixture.player, new NeoForgeKnowledgeCraftPayload(999,
                    ResourceLocation.parse("minecraft:crafting_table"), false)), "Old menu ids cannot spend");
            fixture.menu.setCarried(new ItemStack(Items.DIAMOND));
            check(!fixture.transfer(false), "A nonempty cursor blocks recipe transfer");
            fixture.menu.setCarried(ItemStack.EMPTY);
            check(!NeoForgeKnowledgeCraftPayload.transfer(fixture.player, new NeoForgeKnowledgeCraftPayload(411,
                    ResourceLocation.parse("minecraft:missing_recipe"), false)), "Recipe lookup is server authoritative");
            check(fixture.balance().equals(BigInteger.valueOf(100)), "Invalid requests must not spend");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void maximumPlannerHandlesNonmonotonicReturnCapacityAndCopiesComponents(GameTestHelper helper) {
        List<Ingredient> ingredients = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        ingredients.set(0, Ingredient.of(Items.OAK_PLANKS));
        List<ItemStack> grid = new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY));
        ItemStack named = new ItemStack(Items.OAK_PLANKS, 64);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("完整旧堆叠"));
        grid.set(0, named);
        var inventory = List.of(new ItemStack(Items.COBBLESTONE, 64));
        check(NeoForgeCraftingTransferPlan.create(ingredients, inventory, grid, List.of(), s -> BigInteger.ZERO,
                BigInteger.ZERO, 1) == null, "One recipe cannot return sixty-three leftovers into a full inventory");
        var plan = NeoForgeCraftingTransferPlan.createMaximum(ingredients, inventory, grid, List.of(),
                s -> BigInteger.ZERO, BigInteger.ZERO);
        check(plan != null && plan.grid().getFirst().getCount() == 64 && plan.cost().signum() == 0,
                "Maximum must reuse the full old stack even when a one-recipe plan fails");
        plan.grid().getFirst().set(DataComponents.CUSTOM_NAME, Component.literal("修改副本"));
        check(named.getHoverName().getString().equals("完整旧堆叠") && named.getCount() == 64,
                "Planned output must not alias or consume the source grid");
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerPlayer player;
        final TransmutationContainer menu;
        final Object2LongOpenHashMap<ItemInfo> original = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());

        Fixture(GameTestHelper helper) {
            var values = new Object2LongOpenHashMap<>(original);
            values.put(ItemInfo.fromItem(Items.OAK_PLANKS), 8L);
            EMCMappingHandler.updateEmcValues(values);
            var server = helper.getLevel().getServer();
            var profile = new GameProfile(UUID.randomUUID(), "knowledge-craft");
            var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
            player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.getInventory().clearContent();
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.valueOf(100));
            menu = new TransmutationContainer(411, player.getInventory());
            player.containerMenu = menu;
        }

        void learn(ItemStack stack) { player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).addKnowledge(ItemInfo.fromStack(stack)); }
        BigInteger balance() { return NeoForgeEmcTransactions.balance(player.server, player.getUUID()).floor(); }
        boolean transfer(boolean maximum) { return NeoForgeKnowledgeCraftPayload.transfer(player,
                new NeoForgeKnowledgeCraftPayload(menu.containerId, ResourceLocation.parse("minecraft:crafting_table"), maximum)); }
        @Override public void close() {
            try { menu.removed(player); player.containerMenu = player.inventoryMenu; player.server.getPlayerList().remove(player); }
            finally {
                EMCMappingHandler.updateEmcValues(original);
                if (player.connection.getConnection().channel() instanceof EmbeddedChannel channel) channel.finishAndReleaseAll();
            }
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
