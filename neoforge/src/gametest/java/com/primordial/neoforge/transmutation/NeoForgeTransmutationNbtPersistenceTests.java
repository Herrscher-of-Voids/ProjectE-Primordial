package com.primordial.neoforge.transmutation;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotOutput;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;

import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run write, read and verify in separate normally stopped JVMs. */
@GameTestHolder("primordial_nbt_persistence")
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationNbtPersistenceTests {
    private static final UUID OWNER = UUID.fromString("714df54b-7f3d-4b5f-b157-6001008063ae");
    private static final UUID OTHER = UUID.fromString("f97861de-6848-4cb6-8d16-6001008063ae");
    private static final UUID UNSET = UUID.fromString("b0ef29b4-29cd-49ed-8253-6001008063ae");

    private static final UUID LEARNER = UUID.fromString("bf8178cd-6490-4247-9929-6001008063ae");
    private static final BigInteger BALANCE = BigInteger.TEN.pow(12);
    private static final int MENU_ID = 93, INVENTORY_FIRST = 10;
    private record Variant(int flags, ItemInfo expected) {}

    private NeoForgeTransmutationNbtPersistenceTests() {}

    @GameTestGenerator
    public static List<TestFunction> controlledRestarts() {
        String phase = System.getProperty("primordial.test.nbtPersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        return List.of(new TestFunction("nbt_persistence", "nbt_persistence." + phase,
                "primordial_nbt_persistence:capability_proxy_empty", 100, 0L, true,
                helper -> run(helper, phase)));
    }

    private static void run(GameTestHelper helper, String phase) {
        var settings = NeoForgeTransmutationNbtSettings.get(helper.getLevel().getServer());
        helper.assertTrue(settings.flags(UNSET) == 7, "Unconfigured owners retain all three default flags");
        switch (phase) {
            case "write" -> {
                settings.set(OWNER, 0);
                settings.set(OTHER, 5);
                helper.assertTrue(settings.flags(OWNER) == 0 && settings.flags(OTHER) == 5,
                        "Write phase saves independent offline owner preferences");
            }
            case "read" -> {
                helper.assertTrue(settings.flags(OWNER) == 0 && settings.flags(OTHER) == 5,
                        "First restart preserves disabled flags and independent partial flags");
                settings.set(OWNER, 3);
                settings.set(OTHER, 7);
            }
            case "verify" -> {
                helper.assertTrue(settings.flags(OWNER) == 3 && settings.flags(OTHER) == 7,
                        "Second restart preserves the edit and reset-to-default removal");
                boolean dirty = settings.isDirty();
                settings.set(OWNER, 3);
                settings.set(OTHER, 7);
                helper.assertTrue(settings.isDirty() == dirty, "Repeated settings are not new disk mutations");
            }
            default -> throw new IllegalArgumentException("Unknown NBT persistence phase: " + phase);
        }
        learnedItemsAcrossRestarts(helper, phase);
        helper.succeed();
    }

    /** Only write seeds fixtures; read/verify must load the normal saved player attachment. */
    private static void learnedItemsAcrossRestarts(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        helper.assertTrue(server.getPlayerList().getPlayer(LEARNER) == null, "The controlled native player starts offline in every JVM");
        ServerPlayer player = login(server);
        TransmutationContainer menu = null;
        try {
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            helper.assertTrue(knowledge != null && !knowledge.hasFullKnowledge(), "The fixture requires a real non-tome ProjectE attachment");
            List<Variant> variants = variants(server);
            menu = new TransmutationContainer(MENU_ID, player.getInventory());
            player.containerMenu = menu;
            switch (phase) {
                case "write" -> {
                    helper.assertTrue(knowledge.getKnowledge().isEmpty(), "Write requires an isolated world without existing learned-item fixtures");
                    knowledge.setEmc(BALANCE);
                    ItemStack source = variants.getLast().expected().createStack();
                    CompoundTag unsafe = new CompoundTag();
                    unsafe.putUUID("Owner", LEARNER); unsafe.putInt("Energy", 12345);
                    source.set(DataComponents.CUSTOM_DATA, CustomData.of(unsafe));
                    var before = source.save(player.registryAccess());
                    for (Variant variant : variants) {
                        choose(helper, player, variant.flags());
                        menu.transmutationInventory.handleKnowledge(source);
                        helper.assertTrue(knowledge.getKnowledge().contains(variant.expected()),
                                "Native learning must retain exactly the selected independent component combination: " + variant.flags());
                    }
                    choose(helper, player, 0);
                    assertKnowledge(helper, knowledge, variants);
                    helper.assertTrue(before.equals(source.save(player.registryAccess())) && knowledge.getEmc().equals(BALANCE),
                            "Learning and changing preferences must preserve the actual sample and native EMC");
                    for (int i = 0; i < variants.size(); i++) helper.assertTrue(player.getInventory().getItem(INVENTORY_FIRST + i).isEmpty(),
                            "Write cannot pre-seed purchased physical output fixtures");
                }
                case "read" -> {
                    assertSavedPreference(helper, player);
                    assertKnowledge(helper, knowledge, variants);
                    helper.assertTrue(knowledge.getEmc().equals(BALANCE), "First real login in another JVM must restore the native EMC balance");
                    for (int i = 0; i < variants.size(); i++) {
                        helper.assertTrue(player.getInventory().getItem(INVENTORY_FIRST + i).isEmpty(), "The previous JVM did not provide a physical output");
                        buyAndStore(helper, player, menu, variants.get(i).expected(), INVENTORY_FIRST + i);
                    }
                    helper.assertTrue(knowledge.getEmc().equals(afterPurchases(variants)), "Four native purchases must debit their exact component-aware EMC values");
                    menu.transmutationInventory.handleUnlearn(variants.getFirst().expected());
                    List<Variant> retained = variants.subList(1, variants.size());
                    assertKnowledge(helper, knowledge, retained);
                    assertRejectedOutput(helper, player, menu, variants.getFirst().expected());
                    assertPhysicalOutputs(helper, player, variants);
                }
                case "verify" -> {
                    assertSavedPreference(helper, player);
                    List<Variant> retained = variants.subList(1, variants.size());
                    assertKnowledge(helper, knowledge, retained);
                    helper.assertTrue(!knowledge.hasKnowledge(variants.getFirst().expected())
                                    && knowledge.getEmc().equals(afterPurchases(variants)),
                            "The next JVM must retain variant-specific forgetting and the exact native post-purchase balance");
                    assertPhysicalOutputs(helper, player, variants);
                    assertRejectedOutput(helper, player, menu, variants.getFirst().expected());
                    for (Variant variant : retained) {
                        ItemInfo loaded = knowledge.getKnowledge().stream().filter(variant.expected()::equals).findFirst().orElseThrow();
                        menu.transmutationInventory.writeIntoOutputSlot(0, loaded.createStack());
                        helper.assertTrue(ItemStack.isSameItemSameComponents(menu.transmutationInventory.outputs.getStackInSlot(0),
                                        variant.expected().createStack()) && knowledge.getEmc().equals(afterPurchases(variants)),
                                "Surviving historical variants must remain exact valid native outputs without mutating the verification snapshot");
                    }
                    assertKnowledge(helper, knowledge, retained);
                    assertPhysicalOutputs(helper, player, variants);
                }
                default -> throw new IllegalArgumentException("Unknown learned-item persistence phase: " + phase);
            }
        } finally {
            try {
                if (menu != null) { menu.removed(player); player.containerMenu = player.inventoryMenu; }
            } finally { logout(server, player); }
        }
    }

    /** Independent expected values prevent a broken normalizer from becoming its own test oracle. */
    private static List<Variant> variants(MinecraftServer server) {
        ItemStack ordinary = new ItemStack(Items.DIAMOND_SWORD);
        ordinary.set(DataComponents.CUSTOM_NAME, Component.literal("重启验证·铭刻"));
        ordinary.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("保留此铭文"))));
        ordinary.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(6001));
        ItemStack damaged = new ItemStack(Items.DIAMOND_SWORD); damaged.setDamageValue(37);
        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
        var enchantments = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        enchanted.enchant(enchantments.getHolderOrThrow(Enchantments.SHARPNESS), 3);
        enchanted.enchant(enchantments.getHolderOrThrow(Enchantments.UNBREAKING), 2);
        ItemStack full = ordinary.copy(); full.setDamageValue(37);
        full.set(DataComponents.ENCHANTMENTS, enchanted.get(DataComponents.ENCHANTMENTS));
        return List.of(new Variant(1, ItemInfo.fromStack(ordinary)), new Variant(2, ItemInfo.fromStack(damaged)),
                new Variant(4, ItemInfo.fromStack(enchanted)), new Variant(7, ItemInfo.fromStack(full)));
    }

    private static void choose(GameTestHelper helper, ServerPlayer player, int flags) {
        var state = NeoForgeTransmutationNbtPayloads.apply(player, new NeoForgeTransmutationNbtPayloads.Settings(MENU_ID, flags));
        helper.assertTrue(state != null && state.flags() == flags, "The real menu must accept the requested learning preferences");
    }

    private static void assertSavedPreference(GameTestHelper helper, ServerPlayer player) {
        var state = NeoForgeTransmutationNbtPayloads.apply(player, new NeoForgeTransmutationNbtPayloads.Settings(MENU_ID, -1));
        helper.assertTrue(state != null && state.flags() == 0,
                "The next JVM must restore the final disabled preference without rewriting historical component variants");
    }

    private static void assertKnowledge(GameTestHelper helper, IKnowledgeProvider knowledge, List<Variant> expected) {
        Set<ItemInfo> entries = expected.stream().map(Variant::expected).collect(Collectors.toSet());
        helper.assertTrue(entries.size() == expected.size() && knowledge.getKnowledge().equals(entries),
                "Native ProjectE knowledge must contain exactly the independently expected variants, without flattened base items, unsafe data or extra combinations: "
                        + knowledge.getKnowledge());
        for (ItemInfo entry : knowledge.getKnowledge()) helper.assertTrue(!entry.createStack().has(DataComponents.CUSTOM_DATA),
                "Arbitrary identity or stored-energy data must never survive as repeatable learned knowledge");
    }

    private static void buyAndStore(GameTestHelper helper, ServerPlayer player, TransmutationContainer menu,
            ItemInfo expected, int destination) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        ItemInfo loaded = knowledge.getKnowledge().stream().filter(expected::equals).findFirst().orElseThrow();
        ItemStack sample = loaded.createStack();
        long price = IEMCProxy.INSTANCE.getValue(sample);
        helper.assertTrue(price > 0 && menu.getCarried().isEmpty(), "A saved variant must be priced and start with an empty carried slot");
        BigInteger before = knowledge.getEmc();
        // This is the native server request boundary; the sample comes from restored knowledge, not a fabricated output.
        menu.transmutationInventory.writeIntoOutputSlot(0, sample);
        Slot output = menu.slots.stream().filter(slot -> slot instanceof SlotOutput
                && ItemStack.isSameItemSameComponents(slot.getItem(), sample)).findFirst().orElseThrow();
        menu.clicked(output.index, 0, ClickType.PICKUP, player);
        ItemStack purchased = menu.getCarried();
        helper.assertTrue(purchased.getCount() == 1 && ItemStack.isSameItemSameComponents(purchased, expected.createStack())
                        && knowledge.getEmc().equals(before.subtract(BigInteger.valueOf(price))),
                "A native pickup must deliver the saved exact name/lore/model, damage and full enchantment combination, charging exactly once");
        Slot target = menu.slots.stream().filter(slot -> slot.container == player.getInventory() && slot.getSlotIndex() == destination)
                .findFirst().orElseThrow();
        helper.assertTrue(target.getItem().isEmpty(), "A physical output fixture slot must not be overwritten");
        menu.clicked(target.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && ItemStack.isSameItemSameComponents(target.getItem(), expected.createStack()),
                "The real menu must place the purchased component-bearing item into the player's inventory");
    }

    private static void assertRejectedOutput(GameTestHelper helper, ServerPlayer player, TransmutationContainer menu, ItemInfo forgotten) {
        BigInteger before = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).getEmc();
        menu.transmutationInventory.writeIntoOutputSlot(0, forgotten.createStack());
        helper.assertTrue(menu.transmutationInventory.outputs.getStackInSlot(0).isEmpty()
                        && player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).getEmc().equals(before),
                "A stale request for a forgotten historical variant must expose no output and debit no EMC");
    }

    private static void assertPhysicalOutputs(GameTestHelper helper, ServerPlayer player, List<Variant> expected) {
        for (int i = 0; i < expected.size(); i++) {
            ItemStack stack = player.getInventory().getItem(INVENTORY_FIRST + i);
            helper.assertTrue(stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, expected.get(i).expected().createStack()),
                    "A purchased physical variant must survive native player serialization; forgetting knowledge must not confiscate it: " + i);
        }
    }

    private static BigInteger afterPurchases(List<Variant> variants) {
        BigInteger spent = BigInteger.ZERO;
        for (Variant variant : variants) spent = spent.add(BigInteger.valueOf(IEMCProxy.INSTANCE.getValue(variant.expected().createStack())));
        return BALANCE.subtract(spent);
    }

    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(LEARNER, "nbt-restart");
        var information = ClientInformation.createDefault();
        var player = server.getPlayerList().getPlayerForLogin(profile, information);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player,
                    new CommonListenerCookie(profile, 0, information, false, ConnectionType.NEOFORGE));
            return player;
        } catch (RuntimeException | Error failure) {
            try { logout(server, player); } finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        try { if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player); }
        finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }
}
