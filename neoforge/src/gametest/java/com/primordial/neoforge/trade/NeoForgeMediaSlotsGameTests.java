/** Real native-menu media slots: page gates, continuous FE, detached failures, container forgetting and exact close returns. */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.*;
import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.transmutation.*;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import com.primordial.service.EmcAmount;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMediaSlotsGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private NeoForgeMediaSlotsGameTests() {}

    @GameTest(template = "capability_proxy_empty")
    public static void realMediaSlotsMatchNativePositionsChargeOncePerTickAndReturnOnClose(GameTestHelper helper) {
        var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        var menu = new TransmutationContainer(116, player.getInventory());
        player.containerMenu = menu;
        try {
            var state = (NeoForgeMediaChargingMenu) menu;
            var slot = menu.getSlot(NeoForgeTransmutationTable.MEDIA_FIRST_SLOT);
            check(menu.slots.size() == 82 && slot.x == menu.getSlot(0).x && slot.y == menu.getSlot(0).y
                    && menu.getSlot(81).x == 89 && menu.getSlot(81).y == 97, "Real media slots must match Forge indices and native page coordinates");
            check(!slot.isActive() && !slot.mayPlace(CarriedMediaTestFixture.battery(0))
                    && !NeoForgeTransmutationPayloads.setPageFor(player, new NeoForgeTransmutationPayloads.PageState(117, true, false))
                    && !state.primordial$mediaPage(), "A hidden or stale menu must not activate charging slots");
            check(NeoForgeTransmutationPayloads.setPageFor(player, new NeoForgeTransmutationPayloads.PageState(116, true, false))
                    && slot.isActive() && slot.mayPlace(CarriedMediaTestFixture.battery(0))
                    && !slot.mayPlace(new ItemStack(Items.DIRT)) && slot.getMaxStackSize() == 1,
                    "Only an active media page must accept a single FE container");
            slot.set(CarriedMediaTestFixture.battery(0));
            EmcAmount before = NeoForgeTestPlayers.balance(player);
            player.tickCount++; menu.broadcastChanges();
            check(CarriedMediaTestFixture.energy(slot.getItem()) == 0 && before.equals(NeoForgeTestPlayers.balance(player)),
                    "Placing a physical battery must not grant energy knowledge");
            knowledge(player).addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample()));
            player.tickCount++; menu.broadcastChanges();
            EmcAmount after = before.subtract(NeoForgeRecoveryPrices.energy().multiply(250));
            check(CarriedMediaTestFixture.energy(slot.getItem()) == 250 && after.equals(NeoForgeTestPlayers.balance(player)),
                    "A learned energy certificate must continuously charge the actual FE accepted each server tick");
            menu.broadcastChanges();
            check(CarriedMediaTestFixture.energy(slot.getItem()) == 250 && after.equals(NeoForgeTestPlayers.balance(player)),
                    "Repeated menu broadcasts during the same tick must not multiply charging throughput or billing");
            NeoForgeTransmutationPayloads.setPageFor(player, new NeoForgeTransmutationPayloads.PageState(116, false, false));
            player.tickCount++; menu.broadcastChanges();
            menu.clicked(73, 0, ClickType.PICKUP, player);
            check(!slot.isActive() && CarriedMediaTestFixture.energy(slot.getItem()) == 250 && menu.getCarried().isEmpty()
                    && after.equals(NeoForgeTestPlayers.balance(player)), "A hidden media page must stop charging and reject native slot clicks");
            menu.removed(player);
            check(slot.getItem().isEmpty() && player.getInventory().countItem(CarriedMediaTestFixture.stack(CarriedMediaTestFixture.BATTERY).getItem()) == 1,
                    "Closing from any tab must return each stored battery once");
        } finally { close(player, menu); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void mediaSlotChargingIsolatesSimulationThrowingAndOverreportingCapabilities(GameTestHelper helper) {
        var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        var menu = new TransmutationContainer(118, player.getInventory());
        player.containerMenu = menu;
        try {
            ((NeoForgeMediaChargingMenu) menu).primordial$setMediaPage(true, false);
            knowledge(player).addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample()));
            var slot = menu.getSlot(73);
            for (String flag : List.of("mutate_energy_simulation", "throw_energy_execution", "overreport_energy_execution",
                    "mutate_energy_execution_count")) {
                ItemStack battery = CarriedMediaTestFixture.battery(0);
                var data = battery.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                data.putBoolean(flag, true); battery.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
                slot.set(battery);
                EmcAmount before = NeoForgeTestPlayers.balance(player);
                long actual = NeoForgeMediaSlotTransactions.charge(player, slot);
                int expected = flag.equals("mutate_energy_simulation") ? 250 : 0;
                check(actual == expected && slot.getItem().getCount() == 1 && CarriedMediaTestFixture.energy(slot.getItem()) == expected
                        && NeoForgeTestPlayers.balance(player).equals(before.subtract(NeoForgeRecoveryPrices.energy().multiply(expected))),
                        "Simulation and execution must use independent copies; invalid detached execution must retain the original and refund everything");
            }
        } finally { close(player, menu); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void pickAllFromVisibleInventoryCannotCollectHiddenPageSlots(GameTestHelper helper) {
        var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        var menu = new TransmutationContainer(120, player.getInventory());
        player.containerMenu = menu;
        try {
            var state = (NeoForgeMediaChargingMenu) menu;
            var media = menu.getSlot(73);
            media.set(CarriedMediaTestFixture.battery(0));
            state.primordial$setMediaPage(false, false);
            menu.setCarried(CarriedMediaTestFixture.battery(0));
            menu.clicked(27, 0, ClickType.PICKUP_ALL, player);
            check(menu.getCarried().getCount() == 1 && media.getItem().getCount() == 1 && !media.mayPickup(player),
                    "PICKUP_ALL initiated from an ordinary inventory slot must not collect a hidden media slot");
            state.primordial$setMediaPage(true, false);
            menu.getSlot(0).set(CarriedMediaTestFixture.battery(0));
            menu.getSlot(63).set(CarriedMediaTestFixture.battery(0));
            menu.getSlot(28).set(CarriedMediaTestFixture.battery(0));
            menu.setCarried(CarriedMediaTestFixture.battery(0));
            menu.clicked(27, 0, ClickType.PICKUP_ALL, player);
            check(menu.getCarried().getCount() == 3 && media.getItem().isEmpty() && menu.getSlot(28).getItem().isEmpty()
                    && menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(63).getItem().getCount() == 1,
                    "Media-page PICKUP_ALL must collect visible media and inventory items while leaving hidden native and crafting slots untouched");
        } finally { close(player, menu); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty")
    public static void realForgetSlotPreservesContainerAndQuickMoveReturnsItWithoutSelling(GameTestHelper helper) {
        var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        var menu = new TransmutationContainer(119, player.getInventory());
        player.containerMenu = menu;
        try {
            ((NeoForgeMediaChargingMenu) menu).primordial$setMediaPage(true, false);
            var slot = menu.getSlot(81);
            NeoForgeResourceKnowledge.learnMedia(player.server, player.getUUID(), Kind.FLUID, WATER);
            ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
            EmcAmount before = NeoForgeTestPlayers.balance(player);
            slot.set(bucket.copy());
            check(ItemStack.matches(bucket, slot.getItem()) && !NeoForgeResourceKnowledge.allowsMedia(player.server, player.getUUID(), Kind.FLUID, WATER)
                    && before.equals(NeoForgeTestPlayers.balance(player)), "The native forget slot must revoke only the sampled fluid without draining, destroying or charging its container");
            check(menu.quickMoveStack(player, 81).is(Items.WATER_BUCKET) && slot.getItem().isEmpty()
                    && player.getInventory().countItem(Items.WATER_BUCKET) == 1 && before.equals(NeoForgeTestPlayers.balance(player)),
                    "Shift-taking a stored forget container must return its full contents to inventory without selling it");
            NeoForgeResourceKnowledge.learnMedia(player.server, player.getUUID(), Kind.FLUID, WATER);
            ((NeoForgeMediaChargingMenu) menu).primordial$setMediaPage(true, true);
            slot.set(bucket.copy());
            check(NeoForgeResourceKnowledge.allowsMedia(player.server, player.getUUID(), Kind.FLUID, WATER),
                    "The chemical page's forget slot must not mistake a water bucket for chemical knowledge");
        } finally { close(player, menu); }
        helper.succeed();
    }

    private static void close(net.minecraft.server.level.ServerPlayer player, TransmutationContainer menu) {
        try { menu.removed(player); player.containerMenu = player.inventoryMenu; }
        finally { NeoForgeTestPlayers.logout(player); }
    }
}
