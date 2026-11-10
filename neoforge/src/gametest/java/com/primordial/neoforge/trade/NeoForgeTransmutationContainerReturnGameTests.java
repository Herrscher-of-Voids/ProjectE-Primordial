/** Verify native transmutation-table return of local container contents and empty-shell EMC settlement. */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.*;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationContainerReturn;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationContainerReturnGameTests {
    private NeoForgeTransmutationContainerReturnGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void shiftSaleReturnsShulkerContentsAndPaysOnlyTheSafeShell(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        try {
            menu = menu(player, 101);
            ItemStack nestedBundle = new ItemStack(Items.BUNDLE);
            nestedBundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.EMERALD, 4))));
            ItemStack box = new ItemStack(Items.RED_SHULKER_BOX);
            box.set(DataComponents.CUSTOM_NAME, Component.literal("Named storage"));
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3), nestedBundle)));
            ItemStack original = box.copy();
            ItemStack shell = box.copy();
            shell.remove(DataComponents.CONTAINER);
            EmcAmount expected = NeoForgeRecoveryPrices.item(shell);
            check(expected.floor().signum() > 0, "The empty shulker shell must have a positive native ProjectE sale price");
            ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(shell));
            check(persistent != null && IEMCProxy.INSTANCE.getSellValue(persistent) > 0,
                    "ProjectE must retain a priced, empty named-shulker shell");
            check(NeoForgeTransmutationContainerReturn.handles(box)
                            && NeoForgeTransmutationContainerReturn.prepare(box, player.registryAccess()) != null,
                    "A normal named shulker with ItemContainerContents must produce a safe one-layer return plan");

            menu.getSlot(27).set(box);
            BigInteger before = knowledge(player).getEmc();
            check(menu.quickMoveStack(player, 27).isEmpty(), "A handled local container must not fall through to ProjectE's opaque sale path");
            check(menu.getSlot(27).getItem().isEmpty(), "Successful shift sale must consume the single filled shulker box");
            check(player.getInventory().countItem(Items.DIAMOND) == 3,
                    "Direct contained items must return to inventory without being sold");
            check(find(player, nestedBundle), "A nested container must be returned intact, without recursively emptying it");
            check(knowledge(player).getEmc().subtract(before).equals(expected.floor()),
                    "The player must receive only the empty shell value, not the contents' EMC");
            check(ItemStack.matches(original, box) && box.getHoverName().getString().equals("Named storage"),
                    "Container processing must not mutate the original source object");
        } finally {
            if (menu != null) menu.removed(player);
            logout(player.server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void directConsumeSlotReturnsBundleContentsExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        try {
            menu = menu(player, 102);
            ItemStack nestedBox = new ItemStack(Items.SHULKER_BOX);
            nestedBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.GOLD_INGOT, 2))));
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.EMERALD, 5), nestedBox)));
            ItemStack shell = bundle.copy();
            shell.remove(DataComponents.BUNDLE_CONTENTS);
            EmcAmount expected = NeoForgeRecoveryPrices.item(shell);
            ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(shell));
            check(persistent != null && IEMCProxy.INSTANCE.getSellValue(persistent) > 0,
                    "ProjectE must retain a priced empty-bundle shell");
            check(NeoForgeTransmutationContainerReturn.handles(bundle)
                            && NeoForgeTransmutationContainerReturn.prepare(bundle, player.registryAccess()) != null,
                    "A normal bundle with BundleContents must produce a safe one-layer return plan");
            BigInteger before = knowledge(player).getEmc();
            SlotConsume consume = menu.slots.stream().filter(SlotConsume.class::isInstance)
                    .map(SlotConsume.class::cast).findFirst().orElseThrow();
            menu.setCarried(bundle.copy());
            menu.clicked(menu.slots.indexOf(consume), 0, ClickType.PICKUP, player);
            check(menu.getCarried().isEmpty(), "A successful consume-slot click must consume the carried filled bundle");
            check(player.getInventory().countItem(Items.EMERALD) == 5 && find(player, nestedBox),
                    "Direct consume-slot sale must return each bundle child once and keep nested contents intact");
            check(knowledge(player).getEmc().subtract(before).equals(expected.floor()),
                    "Direct sale must use the empty bundle's native value exactly once");
        } finally {
            if (menu != null) menu.removed(player);
            logout(player.server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void malformedLootAndStackedContainersStayInTheSourceSlot(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        try {
            menu = menu(player, 103);
            ItemStack malformed = new ItemStack(Items.BARREL);
            CompoundTag invalidData = new CompoundTag();
            invalidData.putString("Items", "not a list");
            malformed.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.CustomData.of(invalidData));
            ItemStack unresolved = new ItemStack(Items.SHULKER_BOX);
            unresolved.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(
                    ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("minecraft:chests/simple_dungeon")), 42L));
            ItemStack stacked = new ItemStack(Items.CHEST, 2);
            stacked.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
            ItemStack duplicateSlots = new ItemStack(Items.BARREL);
            CompoundTag duplicateSlotData = new CompoundTag();
            ListTag duplicateEntries = new ListTag();
            duplicateEntries.add(storedItem(0, "minecraft:diamond"));
            duplicateEntries.add(storedItem(0, "minecraft:emerald"));
            duplicateSlotData.put("Items", duplicateEntries);
            duplicateSlots.set(DataComponents.BLOCK_ENTITY_DATA,
                    net.minecraft.world.item.component.CustomData.of(duplicateSlotData));
            ItemStack outOfRangeSlot = new ItemStack(Items.SHULKER_BOX);
            CompoundTag outOfRangeData = new CompoundTag();
            ListTag outOfRangeEntries = new ListTag();
            outOfRangeEntries.add(storedItem(27, "minecraft:diamond"));
            outOfRangeData.put("Items", outOfRangeEntries);
            outOfRangeSlot.set(DataComponents.BLOCK_ENTITY_DATA,
                    net.minecraft.world.item.component.CustomData.of(outOfRangeData));
            ItemStack contradictory = new ItemStack(Items.BARREL);
            contradictory.set(DataComponents.CONTAINER,
                    ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
            CompoundTag hiddenData = new CompoundTag();
            ListTag hiddenEntries = new ListTag();
            hiddenEntries.add(storedItem(0, "minecraft:emerald"));
            hiddenData.put("Items", hiddenEntries);
            contradictory.set(DataComponents.BLOCK_ENTITY_DATA,
                    net.minecraft.world.item.component.CustomData.of(hiddenData));
            for (ItemStack unsafe : List.of(malformed, unresolved, stacked, duplicateSlots, outOfRangeSlot, contradictory)) {
                menu.getSlot(27).set(unsafe.copy());
                BigInteger before = knowledge(player).getEmc();
                check(menu.quickMoveStack(player, 27).isEmpty(), "Unsafe local-container data must stop the native shift-sale handler");
                check(ItemStack.matches(menu.getSlot(27).getItem(), unsafe),
                        "Malformed, unresolved or stacked contents must remain unchanged in the source slot");
                check(knowledge(player).getEmc().equals(before), "Rejected storage data must not credit EMC");
                check(player.getInventory().countItem(Items.DIAMOND) == 0,
                        "Rejected storage data must not return potentially duplicated contents");
                menu.getSlot(27).set(ItemStack.EMPTY);
            }
        } finally {
            if (menu != null) menu.removed(player);
            logout(player.server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void moddedContainersSellEmptyOrFilledThroughBothNativeEntrances(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        var prices = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        try {
            menu = menu(player, 104);
            ItemStack storage = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.STORAGE);
            var testPrices = new Object2LongOpenHashMap<>(prices);
            testPrices.put(ItemInfo.fromStack(storage), 321L);
            EMCMappingHandler.updateEmcValues(testPrices);
            check(NeoForgeTransmutationContainerReturn.prepare(storage, player.registryAccess()) != null,
                    "An empty modded item handler must be sellable, not blanket-rejected as unknown storage");
            menu.setCarried(storage.copy());
            SlotConsume consume = menu.slots.stream().filter(SlotConsume.class::isInstance).map(SlotConsume.class::cast).findFirst().orElseThrow();
            BigInteger before = knowledge(player).getEmc();
            menu.clicked(menu.slots.indexOf(consume), 0, ClickType.PICKUP, player);
            check(menu.getCarried().isEmpty() && knowledge(player).getEmc().subtract(before).equals(BigInteger.valueOf(321)),
                    "The normal consume-slot click must accept an empty modded storage item and pay its shell value");

            storage.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 2))));
            menu.getSlot(27).set(storage);
            before = knowledge(player).getEmc();
            menu.quickMoveStack(player, 27);
            check(menu.getSlot(27).getItem().isEmpty(), "Shift sale must consume the modded container");
            check(player.getInventory().countItem(Items.DIAMOND) == 3 && player.getInventory().countItem(Items.EMERALD) == 2,
                    "Filled modded storage must return each child once instead of learning or selling its contents");
            check(storage.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).getSlots() == 0,
                    "The live storage must actually be drained before the shell is consumed");
            check(knowledge(player).getEmc().subtract(before).equals(BigInteger.valueOf(321)), "Only the empty shell may contribute EMC");
            ItemStack learned = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(storage)).createStack();
            check(!learned.has(DataComponents.CONTAINER), "The learned/re-created shell must not retain the storage component");

            ItemStack twoEmpty = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.STORAGE);
            twoEmpty.setCount(2);
            before = knowledge(player).getEmc();
            check(NeoForgeTransmutationContainerReturn.sell(menu.transmutationInventory, twoEmpty, () -> twoEmpty.setCount(0)),
                    "Empty stacked containers must be settled even when consume clears the source stack");
            check(knowledge(player).getEmc().subtract(before).equals(BigInteger.valueOf(642)),
                    "Settlement must capture the original count before the consume callback clears it");
        } finally {
            if (menu != null) menu.removed(player);
            logout(player.server, player);
            EMCMappingHandler.updateEmcValues(prices);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void moddedExtractionFailureRollsBackAndReentryCannotSellTwice(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        var prices = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        try {
            menu = menu(player, 105);
            ItemStack storage = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.FAILING);
            ItemStack ordinary = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.STORAGE);
            var testPrices = new Object2LongOpenHashMap<>(prices);
            testPrices.put(ItemInfo.fromStack(storage), 321L);
            testPrices.put(ItemInfo.fromStack(ordinary), 321L);
            EMCMappingHandler.updateEmcValues(testPrices);
            storage.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 2))));
            ItemStack beforeStack = storage.copy();
            menu.getSlot(27).set(storage);
            BigInteger before = knowledge(player).getEmc();
            menu.quickMoveStack(player, 27);
            check(ItemStack.matches(menu.getSlot(27).getItem(), beforeStack) && knowledge(player).getEmc().equals(before),
                    "A handler throwing after partial extraction must restore all original contents without consuming or paying the shell");
            check(player.getInventory().countItem(Items.DIAMOND) == 0 && player.getInventory().countItem(Items.EMERALD) == 0,
                    "Rolled-back extraction must not give the player snapshots of contents still in storage");

            ordinary.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
            menu.getSlot(27).set(ordinary);
            TransmutationContainer active = menu;
            ContainerSaleTestFixture.onExtract = () -> check(!NeoForgeTransmutationContainerReturn.sell(
                    active.transmutationInventory, ordinary, () -> { throw new AssertionError("Reentrant consume"); }),
                    "An extraction callback must not re-enter settlement and sell the same shell twice");
            menu.quickMoveStack(player, 27);
            check(player.getInventory().countItem(Items.DIAMOND) == 1 && knowledge(player).getEmc().subtract(before).equals(BigInteger.valueOf(321)),
                    "The outer non-reentrant sale must still return and pay exactly once");
            ContainerSaleTestFixture.onExtract = null;
            ItemStack interrupted = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.STORAGE);
            interrupted.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.EMERALD, 2))));
            ItemStack interruptedCopy = interrupted.copy();
            menu.getSlot(27).set(interrupted);
            before = knowledge(player).getEmc();
            check(!NeoForgeTransmutationContainerReturn.sell(menu.transmutationInventory, interrupted, () -> {
                active.getSlot(27).set(ItemStack.EMPTY);
                throw new IllegalStateException("Intentional failure after consuming the source");
            }), "A failed consume callback must reject settlement");
            check(ItemStack.matches(menu.getSlot(27).getItem(), interruptedCopy) && knowledge(player).getEmc().equals(before),
                    "A commit failure must restore the consumed source with its contents and original EMC");

            for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            ItemStack full = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.STORAGE);
            full.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 128), new ItemStack(Items.EMERALD))));
            menu.getSlot(27).set(full);
            ItemStack fullCopy = full.copy();
            menu.quickMoveStack(player, 27);
            check(ItemStack.matches(menu.getSlot(27).getItem(), fullCopy) && knowledge(player).getEmc().equals(before),
                    "Insufficient return space must preserve the whole container before draining, not drop an oversized item entity");
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setItem(1, ItemStack.EMPTY);
            player.getInventory().setItem(2, ItemStack.EMPTY);
            menu.quickMoveStack(player, 27);
            check(player.getInventory().countItem(Items.DIAMOND) == 128 && player.getInventory().countItem(Items.EMERALD) == 1,
                    "With sufficient room, oversized storage counts must be split into legal inventory stacks without losses");
            for (ItemStack item : player.getInventory().items)
                check(item.isEmpty() || item.getCount() <= item.getMaxStackSize(), "No returned inventory stack may exceed its item's normal stack limit");
        } finally {
            ContainerSaleTestFixture.onExtract = null;
            if (menu != null) menu.removed(player);
            logout(player.server, player);
            EMCMappingHandler.updateEmcValues(prices);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void sophisticatedBackpackReturnsItemsAndUpgradesWithoutCopyingUuidStorage(GameTestHelper helper) throws Exception {
        if (!ModList.get().isLoaded("sophisticatedbackpacks")) { helper.succeed(); return; }
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        var prices = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        try {
            menu = menu(player, 106);
            ItemStack backpack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
            ItemStack upgrade = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:pickup_upgrade")));
            check(!backpack.isEmpty() && !upgrade.isEmpty(), "The real installed backpack and pickup upgrade must exist");
            check(IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromStack(backpack)) > 0,
                    "The default ProjectE mapping must price an empty basic Sophisticated Backpack");
            var testPrices = new Object2LongOpenHashMap<>(prices);
            testPrices.put(ItemInfo.fromStack(backpack), 1234L);
            EMCMappingHandler.updateEmcValues(testPrices);
            check(NeoForgeTransmutationContainerReturn.prepare(backpack, player.registryAccess()) != null,
                    "A newly crafted, unopened Sophisticated Backpack must be sellable");
            Class<?> type = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
            Object wrapper = type.getMethod("fromStack", ItemStack.class).invoke(null, backpack);
            IItemHandlerModifiable items = (IItemHandlerModifiable) type.getMethod("getInventoryHandler").invoke(wrapper);
            items.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            IItemHandlerModifiable upgrades = (IItemHandlerModifiable) type.getMethod("getUpgradeHandler").invoke(wrapper);
            check(upgrades.getSlots() > 0, "The real wrapper must expose its separate upgrade inventory");
            upgrades.setStackInSlot(0, upgrade.copy());
            ItemStack alias = backpack.copy();
            menu.getSlot(27).set(backpack);
            BigInteger before = knowledge(player).getEmc();
            menu.quickMoveStack(player, 27);
            check(menu.getSlot(27).getItem().isEmpty() && player.getInventory().countItem(Items.DIAMOND) == 3 && find(player, upgrade),
                    "The real backpack must sell its shell and return inventory contents plus installed upgrades exactly once");
            check(knowledge(player).getEmc().subtract(before).equals(BigInteger.valueOf(1234)), "The backpack must pay only its empty shell price");
            Object aliasWrapper = type.getMethod("fromStack", ItemStack.class).invoke(null, alias);
            IItemHandlerModifiable aliasItems = (IItemHandlerModifiable) type.getMethod("getInventoryHandler").invoke(aliasWrapper);
            IItemHandlerModifiable aliasUpgrades = (IItemHandlerModifiable) type.getMethod("getUpgradeHandler").invoke(aliasWrapper);
            check(aliasItems.getStackInSlot(0).isEmpty() && aliasUpgrades.getStackInSlot(0).isEmpty(),
                    "Another reference to the same UUID must see drained storage, preventing duplicated returned contents");
            ItemStack learned = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(alias)).createStack();
            check(learned.getComponentsPatch().isEmpty(), "Learned backpack output must not carry a shared storage UUID");
        } finally {
            if (menu != null) menu.removed(player);
            logout(player.server, player);
            EMCMappingHandler.updateEmcValues(prices);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void failureAfterCreditRestoresRealKleinStarAccountKnowledgeAndContainer(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        var prices = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        try {
            menu = menu(player, 107);
            var provider = knowledge(player);
            IItemHandlerModifiable inputs = (IItemHandlerModifiable) provider.getInputAndLocks();
            ItemStack star = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("projecte:klein_star_ein")));
            var holder = star.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY);
            check(holder != null && holder == star.getItem(), "The real ProjectE Klein Star must supply its native EMC capability");
            long maximum = holder.getMaximumEmc(star);
            check(maximum > 100 && holder.insertEmc(star, maximum - 100, EmcAction.EXECUTE) == maximum - 100,
                    "The real Klein Star must start with exactly 100 EMC of free capacity");
            inputs.setStackInSlot(0, star);
            inputs.setStackInSlot(1, new ItemStack(Items.STONE, 2));
            inputs.setStackInSlot(8, new ItemStack(Items.EMERALD));
            List<ItemStack> beforeInputs = new ArrayList<>();
            for (int slot = 0; slot < inputs.getSlots(); slot++) beforeInputs.add(inputs.getStackInSlot(slot).copy());

            ItemStack storage = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.STORAGE);
            ItemInfo shell = ItemInfo.fromStack(storage);
            var testPrices = new Object2LongOpenHashMap<>(prices);
            testPrices.put(shell, 321L);
            EMCMappingHandler.updateEmcValues(testPrices);
            storage.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 2))));
            ItemStack beforeStorage = storage.copy();
            menu.getSlot(27).set(storage);
            provider.setEmc(BigInteger.valueOf(19));
            provider.setFullKnowledge(false);
            provider.removeKnowledge(shell);
            provider.addKnowledge(ItemInfo.fromStack(new ItemStack(Items.STONE)));
            BigInteger beforeEmc = provider.getEmc();
            Set<ItemInfo> beforeKnowledge = Set.copyOf(provider.getKnowledge());
            boolean beforeFull = provider.hasFullKnowledge();
            boolean[] credited = {false};
            TransmutationInventory failingInventory = new TransmutationInventory(player) {
                @Override public void addEmc(BigInteger amount) {
                    super.addEmc(amount);
                    credited[0] = holder.getStoredEmc(inputs.getStackInSlot(0)) == maximum
                            && provider.getEmc().equals(beforeEmc.add(BigInteger.valueOf(221)));
                    throw new IllegalStateException("Intentional failure after real Klein Star and account credit");
                }
            };
            TransmutationContainer active = menu;
            check(!NeoForgeTransmutationContainerReturn.sell(failingInventory, storage, () -> active.getSlot(27).set(ItemStack.EMPTY)),
                    "A failure after addEmc must reject settlement");
            check(credited[0], "The regression must fail only after real ProjectE addEmc has charged both the Klein Star and the account remainder");
            for (int slot = 0; slot < beforeInputs.size(); slot++)
                check(ItemStack.matches(inputs.getStackInSlot(slot), beforeInputs.get(slot)), "Rollback must restore every input and lock stack, including Klein Star EMC");
            check(ItemStack.matches(menu.getSlot(27).getItem(), beforeStorage) && ItemStack.matches(storage, beforeStorage),
                    "Rollback must restore both the source container and its complete original contents");
            check(player.getInventory().countItem(Items.DIAMOND) == 0 && player.getInventory().countItem(Items.EMERALD) == 0,
                    "Rejected settlement must not publish returned contents");
            check(provider.getEmc().equals(beforeEmc) && Set.copyOf(provider.getKnowledge()).equals(beforeKnowledge)
                            && provider.hasFullKnowledge() == beforeFull,
                    "Rollback must restore account EMC, all original knowledge, and the full-knowledge flag");

            menu.quickMoveStack(player, 27);
            check(menu.getSlot(27).getItem().isEmpty() && holder.getStoredEmc(inputs.getStackInSlot(0)) == maximum
                            && provider.getEmc().equals(beforeEmc.add(BigInteger.valueOf(221))),
                    "A subsequent successful sale must still charge the supported Klein Star once and credit only the account remainder");
            check(player.getInventory().countItem(Items.DIAMOND) == 3 && player.getInventory().countItem(Items.EMERALD) == 2
                            && provider.hasKnowledge(shell),
                    "Successful settlement must return contents once and learn only the empty storage shell");
        } finally {
            if (menu != null) menu.removed(player);
            logout(player.server, player);
            EMCMappingHandler.updateEmcValues(prices);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void externalEmcHolderRejectsBeforeDrainOrConsumeButLockSlotDoesNotCharge(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        var prices = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        try {
            menu = menu(player, 108);
            var provider = knowledge(player);
            IItemHandlerModifiable inputs = (IItemHandlerModifiable) provider.getInputAndLocks();
            ItemStack external = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.EXTERNAL_EMC);
            ContainerSaleTestFixture.externalEmc = 77;
            ContainerSaleTestFixture.externalEmcInsertions = 0;
            inputs.setStackInSlot(0, external);
            var holder = external.copy().getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY);
            check(holder != null && holder.getStoredEmc(external.copy()) == 77,
                    "The fixture must expose EMC outside the copied ItemStack");
            ItemStack storage = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.STORAGE);
            ItemInfo shell = ItemInfo.fromStack(storage);
            var testPrices = new Object2LongOpenHashMap<>(prices);
            testPrices.put(shell, 321L);
            EMCMappingHandler.updateEmcValues(testPrices);
            storage.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3))));
            ItemStack beforeStorage = storage.copy();
            menu.getSlot(27).set(storage);
            BigInteger beforeEmc = provider.getEmc();
            Set<ItemInfo> beforeKnowledge = Set.copyOf(provider.getKnowledge());
            boolean[] drained = {false};
            boolean[] consumed = {false};
            ContainerSaleTestFixture.onExtract = () -> drained[0] = true;
            TransmutationContainer active = menu;
            check(!NeoForgeTransmutationContainerReturn.sell(menu.transmutationInventory, storage, () -> {
                consumed[0] = true;
                active.getSlot(27).set(ItemStack.EMPTY);
            }), "An unknown externally backed EMC holder must reject the sale safely");
            check(!drained[0] && !consumed[0] && ContainerSaleTestFixture.externalEmcInsertions == 0
                            && ContainerSaleTestFixture.externalEmc == 77,
                    "Unsafe holder rejection must occur before draining storage, consuming the shell, or inserting EMC");
            check(ItemStack.matches(menu.getSlot(27).getItem(), beforeStorage) && provider.getEmc().equals(beforeEmc)
                            && Set.copyOf(provider.getKnowledge()).equals(beforeKnowledge),
                    "Rejected holder support must preserve the source, contents, account, and knowledge");

            inputs.setStackInSlot(0, ItemStack.EMPTY);
            inputs.setStackInSlot(8, external);
            ContainerSaleTestFixture.onExtract = null;
            menu.quickMoveStack(player, 27);
            check(menu.getSlot(27).getItem().isEmpty() && provider.getEmc().equals(beforeEmc.add(BigInteger.valueOf(321)))
                            && ContainerSaleTestFixture.externalEmcInsertions == 0 && ContainerSaleTestFixture.externalEmc == 77,
                    "An unknown holder in the non-charging lock slot must neither block settlement nor receive EMC");
        } finally {
            ContainerSaleTestFixture.onExtract = null;
            ContainerSaleTestFixture.externalEmc = 0;
            ContainerSaleTestFixture.externalEmcInsertions = 0;
            if (menu != null) menu.removed(player);
            logout(player.server, player);
            EMCMappingHandler.updateEmcValues(prices);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void linkageFailureDuringRestoreStillRestoresRemainingStorageSlots(GameTestHelper helper) {
        ServerPlayer player = login(helper.getLevel().getServer());
        TransmutationContainer menu = null;
        var prices = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        try {
            menu = menu(player, 109);
            ItemStack storage = ContainerSaleTestFixture.stack(ContainerSaleTestFixture.RESTORE_LINKAGE);
            var testPrices = new Object2LongOpenHashMap<>(prices);
            testPrices.put(ItemInfo.fromStack(storage), 321L);
            EMCMappingHandler.updateEmcValues(testPrices);
            storage.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.EMERALD, 2))));
            ItemStack beforeStorage = storage.copy();
            menu.getSlot(27).set(storage);
            BigInteger beforeEmc = knowledge(player).getEmc();
            menu.quickMoveStack(player, 27);
            check(ItemStack.matches(menu.getSlot(27).getItem(), beforeStorage),
                    "A LinkageError after restoring one slot must not skip restoration of later storage slots");
            check(knowledge(player).getEmc().equals(beforeEmc) && player.getInventory().countItem(Items.DIAMOND) == 0
                            && player.getInventory().countItem(Items.EMERALD) == 0,
                    "A restoration fault must neither pay for the rejected shell nor duplicate its retained contents");
        } finally {
            if (menu != null) menu.removed(player);
            logout(player.server, player);
            EMCMappingHandler.updateEmcValues(prices);
        }
        helper.succeed();
    }

    private static TransmutationContainer menu(ServerPlayer player, int id) {
        TransmutationContainer menu = new TransmutationContainer(id, player.getInventory());
        player.containerMenu = menu;
        return menu;
    }

    private static boolean find(ServerPlayer player, ItemStack expected) {
        for (ItemStack stack : player.getInventory().items)
            if (ItemStack.matches(stack, expected)) return true;
        return false;
    }

    private static CompoundTag storedItem(int slot, String id) {
        CompoundTag item = new CompoundTag();
        item.putByte("Slot", (byte) slot);
        item.putString("id", id);
        item.putInt("count", 1);
        return item;
    }
}
