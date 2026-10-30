/** Verify native transmutation-table return of local container contents and empty-shell EMC settlement. */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.*;

import java.math.BigInteger;
import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationContainerReturn;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import net.minecraft.core.component.DataComponents;
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
