/**
 * Native powered ME-grid integration: adjacent capability discovery, account-backed transactions,
 * exact EMC settlement and node persistence. No private adapter storage or synthetic grid is used.
 */
package com.primordial.neoforge.compat.ae2;

import java.math.BigInteger;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;
import com.primordial.neoforge.transmutation.NeoForgeNetworkTransferBudget;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationInterfaceBlockEntity;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.config.ProjectEConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeAe2GridGameTests {
    private static final BlockPos INTERFACE = new BlockPos(1, 1, 1);
    private static final BlockPos CELL = new BlockPos(2, 1, 1);
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);
    private static final AEFluidKey WATER = AEFluidKey.of(Fluids.WATER);
    private static final ResourceLocation WATER_ID = ResourceLocation.parse("minecraft:water");
    private static final BigInteger FUNDING = BigInteger.TEN.pow(12);

    private NeoForgeAe2GridGameTests() {}

    public static List<TestFunction> tests() {
        return List.of(test("ae2_native_grid_power_first_and_precreate_save", NeoForgeAe2GridGameTests::powerFirst),
                test("ae2_native_grid_interface_first_real_transactions", NeoForgeAe2GridGameTests::interfaceFirst),
                test("ae2_native_grid_catalogue_scales_with_emc_and_deduplicates", NeoForgeAe2GridGameTests::catalogue),
                test("ae2_native_cpu_extracts_bulk_item_and_fluid_ingredients", NeoForgeAe2GridGameTests::bulkCraftingIngredients),
                test("ae2_native_cpu_failed_plan_refunds_exactly_without_duplicating_physical_stock", NeoForgeAe2GridGameTests::failedCraftingIngredients),
                test("ae2_native_split_grids_share_emc_without_physical_rate_limit", NeoForgeAe2GridGameTests::sharedAccount),
                test("ae2_native_grid_fluid_fraction_simulation_and_components", NeoForgeAe2GridGameTests::fluidTransactions),
                test("ae2_native_split_grids_share_fluid_emc_without_physical_rate_limit", NeoForgeAe2GridGameTests::sharedFluidAccount),
                test("ae2_native_grid_fluid_stage_forget_and_switches", NeoForgeAe2GridGameTests::fluidAuthorization),
                test("ae2_native_grid_reload_offline_suspend_and_remove", NeoForgeAe2GridGameTests::lifecycle));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("defaultBatch", name, "primordial:capability_proxy_empty", Rotation.NONE,
                650, 0, true, helper -> {
                    Fixture fixture = new Fixture(helper);
                    fixture.run(() -> body.accept(fixture.helper));
                });
    }

    // The currently running fixture is scoped to the helper, not shared between asynchronous tests.
    private static final java.util.Map<GameTestHelper, Fixture> FIXTURES = new java.util.IdentityHashMap<>();

    private static Fixture fixture(GameTestHelper helper) {
        Fixture fixture = FIXTURES.get(helper);
        check(fixture != null, "Missing native-grid test fixture");
        return fixture;
    }

    private static void powerFirst(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.placeCell(CELL);
        // Let the native AE2 node exist before the interface; both placement orders must discover each other.
        fixture.await(() -> fixture.cellNode(CELL) != null, () -> {
            var block = fixture.placeInterface(INTERFACE);
            var host = fixture.nodeHost(INTERFACE);
            check(host != null && host.getGridNode(Direction.EAST) == null,
                    "The real AE2 capability must exist before creation without creating a node on query");
            CompoundTag early = block.saveWithFullMetadata(helper.getLevel().registryAccess());
            check(!early.getCompound("NetworkPorts").getCompound("ae2").contains("aeNode"),
                    "Saving before the first native tick must not invent persisted node identity");
            fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
                IGridNode node = fixture.node(INTERFACE);
                check(fixture.player.getUUID().equals(node.getOwningPlayerProfileId()),
                        "A save before native creation must not suppress the placing owner's AE2 identity");
                for (Direction side : Direction.values())
                    check(host.getGridNode(side) == node,
                            "Every side of the cached production capability must expose the same current node");
                check(node.getConnectedSides().contains(Direction.EAST)
                                && node.getInWorldConnections().containsKey(Direction.EAST),
                        "The connection must be an actual adjacent-world AE2 connection");
                check(node.getGrid().size() == 2 && node.getGrid().getPathingService().getUsedChannels() == 1,
                        "A real powered two-node grid must allocate the interface's one required channel");
                fixture.succeed();
            }, "power-first interface never joined the powered native grid");
        }, "creative energy cell never initialized");
    }

    private static void interfaceFirst(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.placeInterface(INTERFACE);
        fixture.await(() -> fixture.node(INTERFACE) != null, () -> {
            check(!fixture.node(INTERFACE).isActive(), "An isolated unpowered AE grid must not be active");
            fixture.placeCell(CELL);
            fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
                MEStorage inventory = fixture.inventory(INTERFACE);
                IKnowledgeProvider knowledge = fixture.knowledge();
                IActionSource source = IActionSource.ofPlayer(fixture.player);
                long budget = fixture.remaining();
                boolean learning = NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.get();
                try {
                    NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(true);
                    check(inventory.getAvailableStacks().get(DIAMOND) == 0
                                    && inventory.extract(DIAMOND, 3, Actionable.SIMULATE, source) == 0
                                    && inventory.extract(DIAMOND, 3, Actionable.MODULATE, source) == 0,
                            "A powered ME grid must not generate an unknown item even with sufficient EMC");
                    check(inventory.insert(DIAMOND, 3, Actionable.SIMULATE, source) == 3,
                            "Native ME insertion simulation must report reclaimable quantity");
                    check(!knowledge.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND))
                                    && knowledge.getEmc().equals(FUNDING) && fixture.remaining() == budget,
                            "Simulated insertion must not teach, credit EMC or consume the transfer budget");
                    BigInteger buy = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)).floor();
                    BigInteger sell = NeoForgeRecoveryPrices.item(new ItemStack(Items.DIAMOND)).floor();
                    check(buy.signum() > 0 && sell.signum() > 0, "Native ProjectE must price diamonds");
                    check(inventory.insert(DIAMOND, 3, Actionable.MODULATE, source) == 3
                                    && knowledge.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND))
                                    && knowledge.getEmc().equals(FUNDING.add(sell.multiply(BigInteger.valueOf(3)))),
                            "Real ME insertion must learn and credit exactly the accepted quantity once");
                    check(inventory.getAvailableStacks().get(DIAMOND) == knowledge.getEmc().divide(buy).longValueExact(),
                            "The ME grid catalogue must expose total affordability rather than physical throughput");
                    BigInteger before = knowledge.getEmc();
                    check(inventory.extract(DIAMOND, 5, Actionable.SIMULATE, source) == 5
                                    && inventory.extract(DIAMOND, 5, Actionable.SIMULATE, source) == 5
                                    && knowledge.getEmc().equals(before) && fixture.remaining() == budget,
                            "Repeated native ME extraction simulation must be read-only");
                    check(inventory.extract(DIAMOND, 5, Actionable.MODULATE, source) == 5
                                    && knowledge.getEmc().equals(before.subtract(buy.multiply(BigInteger.valueOf(5))))
                                    && fixture.remaining() == budget,
                            "Real native ME extraction must charge delivered units exactly once");
                    check(inventory.getAvailableStacks().get(WATER) == 0
                                    && inventory.extract(WATER, 1000, Actionable.SIMULATE, source) == 0
                                    && inventory.extract(WATER, 1000, Actionable.MODULATE, source) == 0,
                            "Learning an item must not authorize generation of an unknown fluid on the same grid");
                } finally {
                    NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(learning);
                }
                fixture.succeed();
            }, "interface-first native grid never connected");
        }, "interface-first node never initialized");
    }

    private static void catalogue(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        BlockPos second = new BlockPos(2, 1, 2);
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        NeoForgeRecoveryKnowledge.recordFluid(fixture.server, fixture.owner, WATER_ID);
        fixture.placeInterface(INTERFACE);
        fixture.placeCell(CELL);
        fixture.placeInterface(second);
        fixture.await(() -> fixture.ready(INTERFACE, CELL) && fixture.ready(second, CELL), () -> {
            check(fixture.node(INTERFACE).getGrid() == fixture.node(second).getGrid(),
                    "Both interfaces must share one real powered ME network");
            MEStorage inventory = fixture.inventory(second);
            EmcAmount itemPrice = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND));
            EmcAmount fluidPrice = NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1));
            check(!itemPrice.isZero() && !fluidPrice.isZero(), "Catalogue fixtures require real buy prices");
            for (BigInteger balance : List.of(FUNDING, FUNDING.multiply(BigInteger.TWO), BigInteger.ZERO,
                    BigInteger.TEN.pow(40), FUNDING)) {
                fixture.knowledge().setEmc(balance);
                EmcAmount emc = EmcAmount.of(balance);
                check(inventory.getAvailableStacks().get(DIAMOND) == emc.affordableUnits(itemPrice, Long.MAX_VALUE)
                                && inventory.getAvailableStacks().get(WATER) == emc.affordableUnits(fluidPrice, Long.MAX_VALUE),
                        "Two interfaces must list one account's EMC-derived item/fluid supply, including zero and long saturation");
            }
            cachedCatalogue(fixture, second, itemPrice, fluidPrice, 0);
        }, "same-account interfaces never joined the powered native grid");
    }

    private static void cachedCatalogue(Fixture fixture, BlockPos survivingInterface,
                                         EmcAmount itemPrice, EmcAmount fluidPrice, int step) {
        List<BigInteger> balances = List.of(FUNDING, FUNDING.multiply(BigInteger.TWO), BigInteger.ZERO, FUNDING);
        if (step == balances.size()) {
            fixture.helper.setBlock(INTERFACE, Blocks.AIR);
            fixture.helper.runAfterDelay(21, () -> fixture.run(() -> {
                check(fixture.ready(survivingInterface, CELL),
                        "The surviving same-account interface must retain its powered native grid");
                assertCachedCatalogue(fixture, survivingInterface, EmcAmount.of(FUNDING), itemPrice, fluidPrice);
                fixture.succeed();
            }));
            return;
        }
        BigInteger balance = balances.get(step);
        fixture.knowledge().setEmc(balance);
        // Exercise the production twenty-tick refresh; do not force a storage-provider update from the test.
        fixture.helper.runAfterDelay(21, () -> fixture.run(() -> {
            assertCachedCatalogue(fixture, survivingInterface, EmcAmount.of(balance), itemPrice, fluidPrice);
            cachedCatalogue(fixture, survivingInterface, itemPrice, fluidPrice, step + 1);
        }));
    }

    private static void assertCachedCatalogue(Fixture fixture, BlockPos position, EmcAmount balance,
                                               EmcAmount itemPrice, EmcAmount fluidPrice) {
        KeyCounter cached = fixture.node(position).getGrid().getStorageService().getCachedInventory();
        check(cached.get(DIAMOND) == balance.affordableUnits(itemPrice, Long.MAX_VALUE)
                        && cached.get(WATER) == balance.affordableUnits(fluidPrice, Long.MAX_VALUE),
                "The native terminal/planner cache must refresh EMC-derived item/fluid quantities without duplicate interfaces or stale representative supply");
    }

    private static void sharedAccount(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        BlockPos first = new BlockPos(0, 1, 0), firstCell = new BlockPos(1, 1, 0);
        BlockPos second = new BlockPos(0, 1, 2), secondCell = new BlockPos(1, 1, 2);
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        fixture.placeInterface(first);
        fixture.placeCell(firstCell);
        fixture.placeCell(secondCell);
        fixture.placeInterface(second);
        fixture.await(() -> fixture.ready(first, firstCell) && fixture.ready(second, secondCell), () -> {
            check(fixture.node(first).getGrid() != fixture.node(second).getGrid(),
                    "The two account fixtures must be genuinely separate ME networks");
            MEStorage a = fixture.inventory(first), b = fixture.inventory(second);
            IActionSource source = IActionSource.ofPlayer(fixture.player);
            long limit = NeoForgeNetworkTransferBudget.ITEMS_PER_TICK;
            check(fixture.remaining() == limit, "Fresh owner must have its complete per-tick budget");
            BigInteger unit = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)).floor();
            BigInteger before = fixture.knowledge().getEmc();
            long affordable = before.divide(unit).longValueExact();
            check(a.extract(DIAMOND, Long.MAX_VALUE, Actionable.SIMULATE, source) == affordable
                            && b.extract(DIAMOND, Long.MAX_VALUE, Actionable.SIMULATE, source) == affordable
                            && fixture.knowledge().getEmc().equals(before) && fixture.remaining() == limit,
                    "Both networks must simulate total affordability without charging or reserving physical allowance");
            check(a.extract(DIAMOND, 6000, Actionable.MODULATE, source) == 6000
                            && b.extract(DIAMOND, 7000, Actionable.MODULATE, source) == 7000
                            && fixture.remaining() == limit
                            && fixture.knowledge().getEmc().equals(before.subtract(unit.multiply(BigInteger.valueOf(13000)))),
                    "Bulk native ME purchases must exceed 4096, settle exactly and leave physical transfer limits intact");
            fixture.knowledge().setEmc(unit.multiply(BigInteger.valueOf(5)));
            check(a.extract(DIAMOND, 4, Actionable.MODULATE, source) == 4
                            && b.extract(DIAMOND, 4, Actionable.MODULATE, source) == 1
                            && a.extract(DIAMOND, 1, Actionable.MODULATE, source) == 0
                            && fixture.knowledge().getEmc().signum() == 0,
                    "Independent networks must debit one shared account without duplication or overdraft");
            fixture.succeed();
        }, "isolated shared-account grids never became active");
    }

    private static void bulkCraftingIngredients(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        NeoForgeRecoveryKnowledge.recordFluid(fixture.server, fixture.owner, WATER_ID);
        fixture.placeInterface(INTERFACE);
        fixture.placeCell(CELL);
        fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
            KeyCounter required = new KeyCounter();
            required.add(DIAMOND, 6000);
            required.add(WATER, 2000001);
            EmcAmount before = fixture.spendable();
            EmcAmount cost = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)).multiply(6000)
                    .add(NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).multiply(2000001));
            CraftingExtraction extraction = extractCraftingInputs(fixture.node(INTERFACE).getGrid(), required,
                    IActionSource.ofPlayer(fixture.player));
            check(extraction.missing == null && extraction.stored.get(DIAMOND) == 6000
                            && extraction.stored.get(WATER) == 2000001
                            && fixture.spendable().equals(before.subtract(cost))
                            && fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK
                            && fixture.remainingFluid() == NeoForgeNetworkTransferBudget.FLUID_MB_PER_TICK,
                    "The real AE crafting CPU initial extraction must receive its whole bulk item/fluid plan and pay exactly once");
            fixture.succeed();
        }, "native bulk crafting ingredient grid never became active");
    }

    private record CraftingExtraction(GenericStack missing, KeyCounter stored, Throwable failure) {}

    private static void failedCraftingIngredients(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        AEItemKey emerald = AEItemKey.of(Items.EMERALD);
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.EMERALD));
        NeoForgeRecoveryKnowledge.recordFluid(fixture.server, fixture.owner, WATER_ID);
        fixture.placeInterface(INTERFACE);
        fixture.placeCell(CELL);
        fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
            IGrid grid = fixture.node(INTERFACE).getGrid();
            MEStorage inventory = grid.getStorageService().getInventory();
            IActionSource source = IActionSource.ofPlayer(fixture.player);
            PhysicalStorage physical = new PhysicalStorage();
            IStorageProvider provider = mounts -> {
                mounts.mount(physical, 1000);
                mounts.mount(physical.extractor, -1000);
            };
            double previousLoss = ProjectEConfig.server.difficulty.covalenceLoss.get();
            grid.getStorageService().addGlobalStorageProvider(provider);
            try (FluidPriceSnapshot ignored = new FluidPriceSnapshot()) {
                ProjectEConfig.server.difficulty.covalenceLoss.set(0.5);
                EmcAmount diamondBuy = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND));
                EmcAmount diamondSell = NeoForgeRecoveryPrices.item(new ItemStack(Items.DIAMOND));
                EmcAmount emeraldBuy = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.EMERALD));
                EmcAmount waterBuy = NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1));
                check(diamondBuy.affordableUnits(diamondSell, 1) == 1 && !diamondBuy.equals(diamondSell)
                                && waterBuy.equals(new EmcAmount(BigInteger.ONE, BigInteger.valueOf(3000))),
                        "Failed-plan fixtures require a native buy/sell spread and an exact fractional fluid price");

                fixture.knowledge().setEmc(emeraldBuy.multiply(2).floor());
                KeyCounter items = new KeyCounter();
                items.add(DIAMOND, 3);
                items.add(emerald, 1);
                check(inventory.extract(DIAMOND, 3, Actionable.SIMULATE, source) == 3
                                && inventory.extract(emerald, 1, Actionable.SIMULATE, source) == 1,
                        "Each ingredient must be individually affordable while their combined cost exceeds the shared EMC");
                for (int attempt = 0; attempt < 2; attempt++)
                    failedPlanRestoresAccount(fixture, grid, items, source, physical, 0);

                fixture.knowledge().setEmc(diamondBuy.add(EmcAmount.of(BigInteger.valueOf(308))).floor());
                NeoForgeEmcCredits.credit(fixture.server, fixture.owner, waterBuy.divide(2));
                KeyCounter fractional = new KeyCounter();
                fractional.add(DIAMOND, 1);
                fractional.add(WATER, 2000000);
                check(inventory.extract(DIAMOND, 1, Actionable.SIMULATE, source) == 1
                                && inventory.extract(WATER, 2000000, Actionable.SIMULATE, source) == 2000000,
                        "Each mixed ingredient must fit separately while the combined fractional purchase cannot fit");
                for (int attempt = 0; attempt < 2; attempt++)
                    failedPlanRestoresAccount(fixture, grid, fractional, source, physical, 0);

                fixture.knowledge().setEmc(emeraldBuy.multiply(2).floor());
                physical.stored.add(DIAMOND, 1);
                physical.stored.add(emerald, 1);
                KeyCounter mixed = new KeyCounter();
                mixed.add(DIAMOND, 3);
                mixed.add(emerald, 3);
                for (int attempt = 0; attempt < 2; attempt++) {
                    long extracted = physical.extracted, inserted = physical.inserted;
                    failedPlanRestoresAccount(fixture, grid, mixed, source, physical, 2);
                    check(physical.stored.get(DIAMOND) == 1 && physical.stored.get(emerald) == 1
                                    && physical.extracted == extracted + 2 && physical.inserted == inserted + 2,
                            "A failed mixed plan must return exactly its original physical items and refund only virtual portions");
                }
                exceptionalRollbackConsumesRefunded(fixture, grid, source, physical, emerald);
                rejectedRollbackPreservesCpu(fixture, grid, source, physical, emerald);
            } finally {
                ProjectEConfig.server.difficulty.covalenceLoss.set(previousLoss);
                grid.getStorageService().removeGlobalStorageProvider(provider);
            }
            transactionScopeIsolation(fixture, inventory, source);
            fixture.succeed();
        }, "native failed crafting ingredient grid never became active");
    }

    private static void failedPlanRestoresAccount(Fixture fixture, IGrid grid, KeyCounter required,
                                                  IActionSource source, PhysicalStorage physical, long physicalCount) {
        EmcAmount before = fixture.spendable();
        CraftingExtraction extraction = extractCraftingInputs(grid, required, source);
        check(extraction.missing != null && extraction.missing.amount() > 0 && extraction.stored.isEmpty()
                        && fixture.spendable().equals(before) && physical.total() == physicalCount,
                "A real failed CPU reservation must empty temporary inventory, restore exact EMC and never bank generated items");
    }

    private static void transactionScopeIsolation(Fixture fixture, MEStorage inventory, IActionSource source) {
        double previousLoss = ProjectEConfig.server.difficulty.covalenceLoss.get();
        ProjectEConfig.server.difficulty.covalenceLoss.set(0.5);
        try {
            fixture.knowledge().setEmc(BigInteger.valueOf(100000));
            EmcAmount buy = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND));
            EmcAmount sell = NeoForgeRecoveryPrices.item(new ItemStack(Items.DIAMOND));
            EmcAmount before = fixture.spendable();
            check(inventory.insert(DIAMOND, 1, Actionable.MODULATE, source) == 1
                            && fixture.spendable().equals(before.add(sell)),
                    "An ordinary sale after repeated failed CPU reservations must still use the native sell price");

            IActionSource other = IActionSource.ofPlayer(fixture.player);
            check(other != source, "Source isolation requires distinct action-source identities for the same player");
            before = fixture.spendable();
            try (var scope = NeoForgeAe2CraftingTransaction.begin(source)) {
                check(inventory.extract(DIAMOND, 1, Actionable.MODULATE, other) == 1
                                && NeoForgeAe2CraftingTransaction.returnInitialItems(
                                        inventory, DIAMOND, 1, Actionable.MODULATE, other) == 1,
                        "Foreign-source operations must complete through ordinary native transactions");
            }
            check(fixture.spendable().equals(before.subtract(buy).add(sell)),
                    "A different source must never capture another crafting scope's refund privilege");

            before = fixture.spendable();
            try (var outer = NeoForgeAe2CraftingTransaction.begin(source)) {
                check(inventory.extract(DIAMOND, 1, Actionable.MODULATE, source) == 1,
                        "Outer transaction must actually purchase its refund candidate");
                try (var inner = NeoForgeAe2CraftingTransaction.begin(other)) {
                    check(inventory.extract(DIAMOND, 1, Actionable.MODULATE, other) == 1
                                    && NeoForgeAe2CraftingTransaction.returnInitialItems(
                                            inventory, DIAMOND, 1, Actionable.MODULATE, other) == 1,
                            "The nested scope must refund only its own generated units");
                }
                check(NeoForgeAe2CraftingTransaction.returnInitialItems(
                                inventory, DIAMOND, 1, Actionable.MODULATE, source) == 1,
                        "Closing the nested scope must restore the outer source and receipt");
            }
            check(fixture.spendable().equals(before), "Nested scope refunds must conserve exact account value");

            before = fixture.spendable();
            IllegalStateException expected = new IllegalStateException("intentional AE scope cleanup fixture");
            try {
                try (var scope = NeoForgeAe2CraftingTransaction.begin(source)) {
                    check(inventory.extract(DIAMOND, 1, Actionable.MODULATE, source) == 1,
                            "Exceptional scope must contain an actual paid purchase");
                    throw expected;
                }
            } catch (IllegalStateException failure) {
                if (failure != expected) throw failure;
            }
            check(NeoForgeAe2CraftingTransaction.returnInitialItems(
                            inventory, DIAMOND, 1, Actionable.MODULATE, source) == 1
                            && fixture.spendable().equals(before.subtract(buy).add(sell)),
                    "An exception must close refund privileges before the next ordinary native sale");
        } finally {
            ProjectEConfig.server.difficulty.covalenceLoss.set(previousLoss);
        }
    }

    private static void rejectedRollbackPreservesCpu(Fixture fixture, IGrid grid, IActionSource source,
                                                      PhysicalStorage physical, AEItemKey emerald) {
        CompoundTag quarantine;
        try {
            Field field = NeoForgeEmcSavedData.class.getDeclaredField("quarantined");
            field.setAccessible(true);
            quarantine = (CompoundTag) field.get(NeoForgeEmcSavedData.get(fixture.server));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot prepare an account quarantine during native CPU reservation", failure);
        }
        String owner = fixture.owner.toString();
        check(!quarantine.contains(owner), "The native refund-refusal fixture must start with a healthy account");
        fixture.knowledge().setEmc(BigInteger.valueOf(100000));
        physical.stored.reset();
        physical.accepts = false;
        KeyCounter required = new KeyCounter();
        required.add(DIAMOND, 1);
        required.add(emerald, 1);
        List<AEItemKey> ordered = new ArrayList<>();
        for (var entry : required) ordered.add((AEItemKey) entry.getKey());
        AEItemKey first = ordered.getFirst(), second = ordered.get(1);
        EmcAmount before = fixture.spendable();
        EmcAmount cost = NeoForgeRecoveryPrices.itemPurchase(first.toStack());
        long inserted = physical.inserted;
        physical.beforeExtract = key -> {
            if (key.equals(second)) {
                check(fixture.spendable().equals(before.subtract(cost)),
                        "The first native ingredient must be paid before quarantine interrupts the second");
                quarantine.putString(owner, "intentional native CPU refund-refusal fixture");
            }
        };
        try {
            CraftingExtraction extraction = extractCraftingInputs(grid, required, source);
            check(extraction.missing != null && extraction.stored.get(first) == 1
                            && extraction.stored.get(second) == 0 && total(extraction.stored) == 1
                            && physical.total() == 0 && physical.inserted == inserted
                            && !NeoForgeEmcSavedData.get(fixture.server).canCredit(fixture.owner)
                            && fixture.spendable().equals(before.subtract(cost)),
                    "When both exact refund and physical reinsertion fail, AE must retain the paid resource in its native CPU inventory");
        } finally {
            physical.beforeExtract = key -> {};
            physical.accepts = true;
            quarantine.remove(owner);
        }
    }

    private static void exceptionalRollbackConsumesRefunded(Fixture fixture, IGrid grid, IActionSource source,
                                                            PhysicalStorage physical, AEItemKey emerald) {
        EmcAmount emeraldBuy = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.EMERALD));
        fixture.knowledge().setEmc(emeraldBuy.multiply(2).floor());
        physical.stored.reset();
        physical.stored.add(DIAMOND, 1);
        physical.stored.add(emerald, 1);
        KeyCounter required = new KeyCounter();
        required.add(DIAMOND, 3);
        required.add(emerald, 3);
        List<AEItemKey> ordered = new ArrayList<>();
        for (var entry : required) ordered.add((AEItemKey) entry.getKey());
        AEItemKey first = ordered.getFirst(), second = ordered.get(1);
        EmcAmount before = fixture.spendable();
        IllegalStateException expected = new IllegalStateException("intentional physical rollback insertion failure");
        physical.insertFailure = expected;
        physical.insertFailureKey = first;
        physical.failedInsertKey = null;
        physical.failedInsertAmount = 0;
        try {
            CraftingExtraction extraction = extractCraftingInputs(grid, required, source, true);
            long paidUnitsRetained = extraction.stored.get(second) + physical.stored.get(second) - 1;
            check(paidUnitsRetained >= 0,
                    "All original physical inputs must remain in the CPU or physical storage after a rollback exception");
            EmcAmount paid = NeoForgeRecoveryPrices.itemPurchase(second.toStack()).multiply(paidUnitsRetained);
            check(extraction.failure == expected && extraction.stored.get(first) == 1
                            && physical.stored.get(first) == 0 && first.equals(physical.failedInsertKey)
                            && physical.failedInsertAmount == 1
                            && total(extraction.stored) + physical.total() == 2 + paidUnitsRetained
                            && fixture.spendable().equals(before.subtract(paid)),
                    "A physical rollback exception after exact virtual refund must remove refunded CPU copies and conserve every physical/paid unit; target="
                            + first + ", cpuTarget=" + extraction.stored.get(first) + ", otherCpu="
                            + extraction.stored.get(second) + ", otherPhysical=" + physical.stored.get(second)
                            + ", rejected=" + physical.failedInsertAmount);
        } finally {
            physical.insertFailure = null;
            physical.insertFailureKey = null;
        }
    }

    private static long total(KeyCounter values) {
        long total = 0;
        for (var entry : values) total += entry.getLongValue();
        return total;
    }

    private static final class PhysicalStorage implements MEStorage {
        final KeyCounter stored = new KeyCounter();
        final MEStorage extractor = new MEStorage() {
            @Override public Component getDescription() { return PhysicalStorage.this.getDescription(); }
            @Override public void getAvailableStacks(KeyCounter out) { out.addAll(stored); }
            @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                return take(key, amount, mode);
            }
        };
        long extracted, inserted;
        boolean accepts = true;
        RuntimeException insertFailure;
        AEKey insertFailureKey, failedInsertKey;
        long failedInsertAmount;
        Consumer<AEKey> beforeExtract = key -> {};

        @Override public Component getDescription() { return Component.literal("Native CPU physical return fixture"); }

        @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) { return true; }

        private long take(AEKey key, long amount, Actionable mode) {
            if (mode == Actionable.MODULATE) beforeExtract.accept(key);
            long moved = Math.min(amount, stored.get(key));
            if (mode == Actionable.MODULATE && moved > 0) {
                stored.remove(key, moved);
                extracted += moved;
            }
            return moved;
        }

        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!accepts) return 0;
            if (insertFailure != null && mode == Actionable.MODULATE
                    && (insertFailureKey == null || insertFailureKey.equals(key))) {
                failedInsertKey = key;
                failedInsertAmount = amount;
                throw insertFailure;
            }
            if (mode == Actionable.MODULATE && amount > 0) {
                stored.add(key, amount);
                inserted += amount;
            }
            return amount;
        }

        long total() { return NeoForgeAe2GridGameTests.total(stored); }
    }

    private record IngredientPlan(KeyCounter usedItems) implements ICraftingPlan {
        @Override public GenericStack finalOutput() { return new GenericStack(DIAMOND, 1); }
        @Override public long bytes() { return 1; }
        @Override public boolean simulation() { return false; }
        @Override public boolean multiplePaths() { return false; }
        @Override public KeyCounter emittedItems() { return new KeyCounter(); }
        @Override public KeyCounter missingItems() { return new KeyCounter(); }
        @Override public Map<IPatternDetails, Long> patternTimes() { return Map.of(); }
    }

    private static CraftingExtraction extractCraftingInputs(IGrid grid, KeyCounter required, IActionSource source) {
        return extractCraftingInputs(grid, required, source, false);
    }

    private static CraftingExtraction extractCraftingInputs(IGrid grid, KeyCounter required, IActionSource source,
                                                            boolean captureFailure) {
        try {
            Class<?> inventoryType = Class.forName("appeng.crafting.inv.ListCraftingInventory");
            Class<?> listenerType = Class.forName("appeng.crafting.inv.ListCraftingInventory$ChangeListener");
            Object listener = java.lang.reflect.Proxy.newProxyInstance(listenerType.getClassLoader(),
                    new Class<?>[] { listenerType }, (proxy, method, arguments) -> null);
            Object inventory = inventoryType.getConstructor(listenerType).newInstance(listener);
            Class<?> helperType = Class.forName("appeng.crafting.execution.CraftingCpuHelper");
            GenericStack missing = null;
            Throwable failure = null;
            try {
                missing = (GenericStack) helperType.getMethod("tryExtractInitialItems",
                        ICraftingPlan.class, IGrid.class, inventoryType, IActionSource.class)
                        .invoke(null, new IngredientPlan(required), grid, inventory, source);
            } catch (java.lang.reflect.InvocationTargetException thrown) {
                if (!captureFailure) throw thrown;
                failure = thrown.getCause();
            }
            return new CraftingExtraction(missing, (KeyCounter) inventoryType.getField("list").get(inventory), failure);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot execute the installed native AE crafting CPU ingredient extraction", failure);
        }
    }

    private static void fluidTransactions(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        fixture.placeInterface(INTERFACE);
        fixture.placeCell(CELL);
        fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
            // Publish through the production parser only inside this synchronous server callback.
            // Restore its previous in-memory snapshot before another test can run; no price file is edited.
            try (FluidPriceSnapshot ignored = new FluidPriceSnapshot()) {
                MEStorage inventory = fixture.inventory(INTERFACE);
                IActionSource source = IActionSource.ofPlayer(fixture.player);
                EmcAmount unit = new EmcAmount(BigInteger.ONE, BigInteger.valueOf(3000));
                check(NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).equals(unit)
                                && AEFluidKey.AMOUNT_BUCKET == 1000,
                        "The native key must use mB while prices retain the exact one-third EMC per bucket");
                long limit = NeoForgeNetworkTransferBudget.FLUID_MB_PER_TICK;
                CompoundTag ledger = fixture.ledger();
                check(inventory.getAvailableStacks().get(WATER) == 0
                                && inventory.extract(WATER, 1, Actionable.SIMULATE, source) == 0
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 0,
                        "An item-aware ME network must not generate unknown water");
                check(inventory.insert(WATER, 37, Actionable.SIMULATE, source) == 37
                                && inventory.insert(WATER, 37, Actionable.SIMULATE, source) == 37
                                && !NeoForgeRecoveryKnowledge.knowsFluid(fixture.server, fixture.owner, WATER_ID)
                                && fixture.knowledge().getEmc().equals(FUNDING)
                                && fixture.ledger().equals(ledger) && fixture.remainingFluid() == limit,
                        "Repeated fluid insertion simulation must not learn, credit, reserve or consume anything");

                FluidStack decorated = new FluidStack(Fluids.WATER, 1);
                decorated.set(DataComponents.CUSTOM_NAME, Component.literal("discarded fluid payload"));
                AEFluidKey decoratedKey = AEFluidKey.of(decorated);
                check(!decoratedKey.equals(WATER), "The native AE2 key must preserve the test fluid component");
                check(inventory.insert(decoratedKey, 37, Actionable.MODULATE, source) == 37
                                && NeoForgeRecoveryKnowledge.knowsFluid(fixture.server, fixture.owner, WATER_ID)
                                && fixture.spendable().equals(EmcAmount.of(FUNDING).add(unit.multiply(37)))
                                && fixture.remainingFluid() == limit,
                        "Real component-bearing fluid reclamation must destroy 37 mB, learn only its ID and credit its exact value");
                check(inventory.getAvailableStacks().get(WATER) == fixture.spendable().affordableUnits(unit, Long.MAX_VALUE)
                                && inventory.getAvailableStacks().get(decoratedKey) == 0
                                && inventory.getAvailableStacks().get(DIAMOND) == fixture.spendable().affordableUnits(
                                        NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)), Long.MAX_VALUE),
                        "One native ME grid must list affordable plain fluids and items without throughput caps");
                EmcAmount before = fixture.spendable();
                ledger = fixture.ledger();
                check(inventory.extract(decoratedKey, 1, Actionable.SIMULATE, source) == 0
                                && inventory.extract(decoratedKey, 1, Actionable.MODULATE, source) == 0
                                && inventory.extract(WATER, 5, Actionable.SIMULATE, source) == 5
                                && inventory.extract(WATER, 5, Actionable.SIMULATE, source) == 5
                                && fixture.spendable().equals(before) && fixture.ledger().equals(ledger)
                                && fixture.remainingFluid() == limit,
                        "A base fluid identity must not recreate discarded components; all extraction simulations stay read-only");
                check(inventory.extract(WATER, 5, Actionable.MODULATE, source) == 5
                                && fixture.spendable().equals(before.subtract(unit.multiply(5)))
                                && fixture.remainingFluid() == limit,
                        "Native ME extraction must deliver five mB and debit the exact fractional price once");
                long fluidBudget = fixture.remainingFluid();
                check(inventory.extract(DIAMOND, 3, Actionable.MODULATE, source) == 3
                                && fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK
                                && fixture.remainingFluid() == fluidBudget,
                        "A real item trade on the same grid must not consume any fluid allowance");

                // The fractional ledger contains 32/3000 EMC after these integer item purchases.
                // Remove only whole native EMC to expose the one-mB affordability boundary.
                fixture.knowledge().setEmc(BigInteger.ZERO);
                check(fixture.spendable().equals(unit.multiply(32))
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 1
                                && fixture.spendable().equals(unit.multiply(31))
                                && inventory.extract(WATER, Long.MAX_VALUE, Actionable.SIMULATE, source) == 31
                                && inventory.extract(WATER, Long.MAX_VALUE, Actionable.MODULATE, source) == 31
                                && fixture.spendable().isZero()
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 0,
                        "A fraction-only owner must receive exactly its affordable mB with no rounding, free final unit or overdraft");
                check(fixture.remainingFluid() == limit
                                && fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK,
                        "Native virtual trades must not consume physical interface throughput allowances");
            }
            fixture.succeed();
        }, "native fluid transaction grid never became active");
    }

    private static void sharedFluidAccount(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        BlockPos first = new BlockPos(0, 1, 0), firstCell = new BlockPos(1, 1, 0);
        BlockPos second = new BlockPos(0, 1, 2), secondCell = new BlockPos(1, 1, 2);
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        NeoForgeRecoveryKnowledge.recordFluid(fixture.server, fixture.owner, WATER_ID);
        fixture.placeInterface(first);
        fixture.placeCell(firstCell);
        fixture.placeInterface(second);
        fixture.placeCell(secondCell);
        fixture.await(() -> fixture.ready(first, firstCell) && fixture.ready(second, secondCell), () -> {
            check(fixture.node(first).getGrid() != fixture.node(second).getGrid(),
                    "Fluid account sharing must be exercised by genuinely separate native ME networks");
            MEStorage a = fixture.inventory(first), b = fixture.inventory(second);
            IActionSource source = IActionSource.ofPlayer(fixture.player);
            long limit = NeoForgeNetworkTransferBudget.FLUID_MB_PER_TICK;
            FluidStack sample = new FluidStack(Fluids.WATER, 1);
            EmcAmount before = fixture.spendable();
            EmcAmount buyPrice = NeoForgeRecoveryPrices.fluidPurchase(sample);
            check(fixture.remainingFluid() == limit
                            && a.extract(WATER, Long.MAX_VALUE, Actionable.SIMULATE, source)
                                    == before.affordableUnits(buyPrice, Long.MAX_VALUE)
                            && b.insert(WATER, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE
                            && fixture.remainingFluid() == limit && fixture.spendable().equals(before),
                    "Native fluid simulation must use total mB affordability without mutation or a physical throughput cap");
            EmcAmount sold = NeoForgeRecoveryPrices.fluid(sample).multiply(600000);
            EmcAmount bought = buyPrice.multiply(limit + 600000);
            check(a.insert(WATER, 600000, Actionable.MODULATE, source) == 600000
                            && b.extract(WATER, limit + 600000, Actionable.MODULATE, source) == limit + 600000
                            && fixture.remainingFluid() == limit
                            && fixture.spendable().equals(before.add(sold).subtract(bought)),
                    "Native grids must settle bulk fluid trades above one million mB without spending physical allowance");
            check(inventoryItemTradeUsesNoPhysicalBudget(fixture, b, source),
                    "Bulk fluid trading must leave item transactions and physical allowances available");
            long affordable = fixture.spendable().affordableUnits(buyPrice, Long.MAX_VALUE);
            check(a.extract(WATER, Long.MAX_VALUE, Actionable.MODULATE, source) == affordable
                            && b.extract(WATER, 1, Actionable.MODULATE, source) == 0
                            && fixture.remainingFluid() == limit,
                    "Separate ME grids must not spend the same fluid EMC twice");
            fixture.succeed();
        }, "isolated shared-fluid-account grids never became active");
    }

    private static boolean inventoryItemTradeUsesNoPhysicalBudget(Fixture fixture, MEStorage inventory, IActionSource source) {
        return fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK
                && inventory.extract(DIAMOND, 1, Actionable.MODULATE, source) == 1
                && fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK
                && fixture.remainingFluid() == NeoForgeNetworkTransferBudget.FLUID_MB_PER_TICK;
    }

    private static void fluidAuthorization(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        NeoForgeRecoveryKnowledge.recordFluid(fixture.server, fixture.owner, WATER_ID);
        fixture.placeInterface(INTERFACE);
        fixture.placeCell(CELL);
        fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
            MEStorage inventory = fixture.inventory(INTERFACE);
            IActionSource source = IActionSource.ofPlayer(fixture.player);
            ResourceLocation stage = ResourceLocation.parse("primordial:ae2_fluid_gate");
            var stages = NeoForgePlayerStagesSavedData.get(fixture.server);
            List<? extends String> stageRules = List.copyOf(NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.get());
            List<? extends String> blacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
            boolean purchase = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
            boolean sale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
            try {
                NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=" + stage));
                EmcAmount before = fixture.spendable();
                long budget = fixture.remainingFluid();
                check(inventory.getAvailableStacks().get(WATER) == 0
                                && inventory.extract(WATER, 1, Actionable.SIMULATE, source) == 0
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 0
                                && fixture.spendable().equals(before) && fixture.remainingFluid() == budget,
                        "A native network must apply the owner's missing stage to both catalogue and extraction without mutation");
                check(inventory.insert(WATER, 17, Actionable.MODULATE, source) == 17,
                        "A purchase stage must not block real fluid reclamation on an active ME grid");
                stages.grant(fixture.owner, stage);
                check(inventory.getAvailableStacks().get(WATER) > 0
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 1,
                        "A learned fluid must become purchasable through the real network after its stage is granted");
                stages.revoke(fixture.owner, stage);
                check(inventory.getAvailableStacks().get(WATER) == 0
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 0
                                && NeoForgeRecoveryKnowledge.knowsFluid(fixture.server, fixture.owner, WATER_ID),
                        "Revoking a stage must hide native fluid supply while preserving the owner's media knowledge");
                stages.grant(fixture.owner, stage);
                check(NeoForgeResourceKnowledge.forgetMedia(fixture.server, fixture.owner, Kind.FLUID, WATER_ID)
                                && inventory.getAvailableStacks().get(WATER) == 0
                                && inventory.extract(WATER, 1, Actionable.SIMULATE, source) == 0
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 0,
                        "Explicit forgetting must immediately reject a previously learned native fluid key");
                check(inventory.insert(WATER, 1, Actionable.MODULATE, source) == 1
                                && inventory.getAvailableStacks().get(WATER) > 0,
                        "Only actual fluid reclamation may relearn a forgotten fluid through the ME network");

                NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:water"));
                before = fixture.spendable();
                budget = fixture.remainingFluid();
                check(inventory.insert(WATER, 1, Actionable.SIMULATE, source) == 0
                                && inventory.insert(WATER, 1, Actionable.MODULATE, source) == 0
                                && inventory.extract(WATER, 1, Actionable.SIMULATE, source) == 0
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 0
                                && inventory.getAvailableStacks().get(WATER) == 0
                                && fixture.spendable().equals(before) && fixture.remainingFluid() == budget,
                        "A blacklisted fluid must be refused in both directions without consuming EMC or transfer allowance");
                NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
                NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(false);
                check(inventory.getAvailableStacks().get(WATER) == 0
                                && inventory.extract(WATER, 1, Actionable.SIMULATE, source) == 0
                                && inventory.extract(WATER, 1, Actionable.MODULATE, source) == 0,
                        "Turning off fluid purchases must remove known fluids from native network supply");
                NeoForgeServerConfig.FLUID_SALE_ENABLED.set(false);
                check(inventory.insert(WATER, 1, Actionable.SIMULATE, source) == 0
                                && inventory.insert(WATER, 1, Actionable.MODULATE, source) == 0
                                && fixture.spendable().equals(before) && fixture.remainingFluid() == budget,
                        "Turning off fluid sales must refuse native insertion without credit or budget loss");
            } finally {
                stages.revoke(fixture.owner, stage);
                NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(stageRules);
                NeoForgeServerConfig.FLUID_BLACKLIST.set(blacklist);
                NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(purchase);
                NeoForgeServerConfig.FLUID_SALE_ENABLED.set(sale);
            }
            fixture.succeed();
        }, "native fluid authorization grid never became active");
    }

    private static void lifecycle(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        NeoForgeResourceKnowledge.learnMedia(fixture.server, fixture.owner, Kind.FLUID, WATER_ID);
        var block = fixture.placeInterface(INTERFACE);
        fixture.placeCell(CELL);
        fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
            MEStorage liveInventory = fixture.inventory(INTERFACE);
            IActionSource source = IActionSource.ofPlayer(fixture.player);
            check(liveInventory.getAvailableStacks().get(WATER) > 0
                            && liveInventory.insert(WATER, 3, Actionable.MODULATE, source) == 3
                            && liveInventory.extract(WATER, 1, Actionable.MODULATE, source) == 1,
                    "The lifecycle fixture must start with an actual learned fluid catalogue and working transactions");
            IInWorldGridNodeHost cachedHost = fixture.nodeHost(INTERFACE);
            IGridNode originalNode = fixture.node(INTERFACE);
            deferredStateRefresh(block, originalNode);
            int ownerId = originalNode.getOwningPlayerId();
            MEStorage oldStorage = mountedStorage(originalNode);
            CompoundTag saved = block.saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag nodeData = saved.getCompound("NetworkPorts").getCompound("ae2").getCompound("aeNode");
            check(!nodeData.isEmpty(), "The active real AE2 node must save native identity");
            fixture.logout();
            block.loadWithComponents(saved, helper.getLevel().registryAccess());
            check(cachedHost.getGridNode(Direction.EAST) == null,
                    "A cached production capability must not expose the previous node during NBT reload");
            rejectsTransactions(oldStorage, "old storage after same-host NBT reload");
            CompoundTag pending = block.saveWithFullMetadata(helper.getLevel().registryAccess());
            check(nodeData.equals(pending.getCompound("NetworkPorts").getCompound("ae2").getCompound("aeNode")),
                    "A save between NBT load and first tick must preserve the loaded native AE2 identity");
            fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
                IGridNode restored = fixture.node(INTERFACE);
                deferredStateRefresh(block, restored);
                check(restored != originalNode && restored.getOwningPlayerId() == ownerId
                                && fixture.owner.equals(restored.getOwningPlayerProfileId())
                                && cachedHost.getGridNode(Direction.EAST) == restored,
                        "Offline owner restore must create one new node and refresh cached host discovery");
                check(fixture.inventory(INTERFACE).getAvailableStacks().get(DIAMOND) > 0
                                && fixture.inventory(INTERFACE).getAvailableStacks().get(WATER) > 0,
                        "The native grid inventory must remount both item and fluid storage for the offline restored owner");
                MEStorage resumedStorage = mountedStorage(restored);
                block.onChunkUnloaded();
                check(cachedHost.getGridNode(Direction.EAST) == null,
                        "Suspending the chunk must hide the live AE2 node from cached capability callers");
                rejectsTransactions(resumedStorage, "storage retained across chunk suspension");
                CompoundTag suspended = block.saveWithFullMetadata(helper.getLevel().registryAccess());
                check(!suspended.getCompound("NetworkPorts").getCompound("ae2").getCompound("aeNode").isEmpty(),
                        "Chunk suspension must retain the native node snapshot");
                block.onLoad();
                fixture.await(() -> fixture.ready(INTERFACE, CELL), () -> {
                    IGridNode resumed = fixture.node(INTERFACE);
                    deferredStateRefresh(block, resumed);
                    check(resumed != restored && resumed.getOwningPlayerId() == ownerId,
                            "Resuming a suspended interface must retain owner identity on a fresh native node");
                    MEStorage finalStorage = mountedStorage(resumed);
                    MEStorage finalNetwork = fixture.inventory(INTERFACE);
                    var finalPort = block.networkPort("ae2");
                    helper.setBlock(INTERFACE, Blocks.AIR);
                    closedStateRefresh(finalPort, resumed);
                    check(cachedHost.getGridNode(Direction.EAST) == null
                                    && helper.getLevel().getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                                    helper.absolutePos(INTERFACE), null) == null,
                            "Actual removal must invalidate cached and fresh AE2 host discovery");
                    rejectsTransactions(finalStorage, "storage retained after actual block removal");
                    check(finalNetwork.getAvailableStacks().get(DIAMOND) == 0
                                    && finalNetwork.extract(DIAMOND, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                                    && finalNetwork.getAvailableStacks().get(WATER) == 0
                                    && finalNetwork.extract(WATER, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                                    && finalNetwork.insert(WATER, 1, Actionable.MODULATE, IActionSource.empty()) == 0,
                            "Actual removal must unmount both item and fluid storage from the native grid inventory");
                    fixture.succeed();
                }, "chunk-resumed interface never rejoined the native grid");
            }, "offline-loaded interface never rejoined the native grid");
        }, "lifecycle native grid never became active");
    }

    private static void deferredStateRefresh(NeoForgeTransmutationInterfaceBlockEntity block, IGridNode node) {
        var port = block.networkPort("ae2");
        check(port != null, "The active native node must retain its production AE2 port");
        var service = (appeng.api.networking.IGridServiceProvider) node.getGrid().getStorageService();
        try {
            Field pending = port.getClass().getDeclaredField("refreshPending");
            pending.setAccessible(true);
            service.removeNode(node);
            try {
                // Reproduce the callback before its storage service is mounted, using the real native node.
                node.getClass().getMethod("notifyStatusChange", appeng.api.networking.IGridNodeListener.State.class)
                        .invoke(node, appeng.api.networking.IGridNodeListener.State.CHANNEL);
                check(pending.getBoolean(port), "A native state callback must queue its refresh without querying an unmounted service");
            } finally {
                service.addNode(node, null);
            }
            block.tickNetworks();
            check(!pending.getBoolean(port), "The next host tick must consume the deferred storage refresh");
            block.tickNetworks();
            check(!pending.getBoolean(port) && block.networkPort("ae2") == port
                            && node.getGrid().getStorageService().getInventory().getAvailableStacks().get(DIAMOND) > 0,
                    "A stable native grid must not reschedule refreshes or lose its item catalogue");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect the native AE2 state callback", failure);
        }
    }

    private static void closedStateRefresh(Object port, IGridNode node) {
        try {
            Field pending = port.getClass().getDeclaredField("refreshPending");
            pending.setAccessible(true);
            node.getClass().getMethod("notifyStatusChange", appeng.api.networking.IGridNodeListener.State.class)
                    .invoke(node, appeng.api.networking.IGridNodeListener.State.CHANNEL);
            check(!pending.getBoolean(port), "A late native callback must not schedule storage refreshes after block removal");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect a late native AE2 state callback", failure);
        }
    }

    private static MEStorage mountedStorage(IGridNode node) {
        IStorageProvider provider = node.getService(IStorageProvider.class);
        check(provider != null, "The live native node must expose its official storage-provider service");
        List<MEStorage> storages = new ArrayList<>();
        provider.mountInventories((storage, priority) -> storages.add(storage));
        check(storages.size() == 1, "An active interface must mount exactly one native virtual storage");
        return storages.getFirst();
    }

    private static void rejectsTransactions(MEStorage storage, String phase) {
        check(storage.getAvailableStacks().isEmpty()
                        && storage.extract(DIAMOND, 1, Actionable.SIMULATE, IActionSource.empty()) == 0
                        && storage.extract(DIAMOND, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                        && storage.insert(DIAMOND, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                        && storage.extract(WATER, 1, Actionable.SIMULATE, IActionSource.empty()) == 0
                        && storage.extract(WATER, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                        && storage.insert(WATER, 1, Actionable.MODULATE, IActionSource.empty()) == 0,
                "Closed " + phase + " must reject stale transactions and catalogue reads");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** Tests the production parser/publication with a fraction-only quote, without editing administrator files. */
    private static final class FluidPriceSnapshot implements AutoCloseable {
        private final Field snapshot;
        private final Field loaded;
        private final Object before;
        private final boolean wasLoaded;

        FluidPriceSnapshot() {
            try {
                snapshot = NeoForgeRecoveryPrices.class.getDeclaredField("snapshot");
                loaded = NeoForgeRecoveryPrices.class.getDeclaredField("loaded");
                snapshot.setAccessible(true);
                loaded.setAccessible(true);
                before = snapshot.get(null);
                wasLoaded = loaded.getBoolean(null);
                JsonObject root = new JsonObject();
                JsonObject values = new JsonObject();
                values.addProperty("minecraft:water", "1/3");
                root.add("fluid_values", values);
                var prepare = NeoForgeRecoveryPrices.class.getDeclaredMethod("prepareEdited", JsonObject.class, boolean.class);
                prepare.setAccessible(true);
                Object prepared = prepare.invoke(null, root, true);
                var publish = NeoForgeRecoveryPrices.class.getDeclaredMethod("publishEdited", prepared.getClass());
                publish.setAccessible(true);
                publish.invoke(null, prepared);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot prepare the native fluid fractional-price fixture", failure);
            }
        }

        @Override public void close() {
            try {
                snapshot.set(null, before);
                loaded.setBoolean(null, wasLoaded);
                NeoForgeRecoveryPrices.invalidatePrices();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot restore the native fluid price fixture", failure);
            }
        }
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final MinecraftServer server;
        final ServerPlayer player;
        final UUID owner;
        final List<BlockPos> placed = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            server = helper.getLevel().getServer();
            player = login(server);
            owner = player.getUUID();
            knowledge().clearKnowledge();
            knowledge().setEmc(FUNDING);
            FIXTURES.put(helper, this);
        }

        IKnowledgeProvider knowledge() {
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null, "Real ProjectE player attachment must provide EMC and knowledge");
            return knowledge;
        }

        NeoForgeTransmutationInterfaceBlockEntity placeInterface(BlockPos position) {
            placed.add(position);
            helper.setBlock(position, NeoForgeModRegistries.TRANSMUTATION_INTERFACE.get());
            check(helper.getBlockEntity(position) instanceof NeoForgeTransmutationInterfaceBlockEntity,
                    "Expected the production transmutation interface block entity");
            var block = (NeoForgeTransmutationInterfaceBlockEntity) helper.getBlockEntity(position);
            block.bindOwner(owner);
            block.onLoad();
            return block;
        }

        void placeCell(BlockPos position) {
            Block cell = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell"));
            check(cell != Blocks.AIR, "Installed AE2 must provide its registered creative energy cell");
            placed.add(position);
            helper.setBlock(position, cell);
        }

        IInWorldGridNodeHost nodeHost(BlockPos position) {
            return helper.getLevel().getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    helper.absolutePos(position), null);
        }

        IGridNode node(BlockPos position) {
            return GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.EAST);
        }

        IGridNode cellNode(BlockPos position) {
            return GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.WEST);
        }

        boolean ready(BlockPos position, BlockPos cellPosition) {
            IGridNode node = node(position), cell = cellNode(cellPosition);
            return node != null && cell != null && node.getGrid() == cell.getGrid() && node.isActive();
        }

        MEStorage inventory(BlockPos position) {
            IGridNode node = node(position);
            check(node != null && node.isActive(), "Transactions must use a real active native ME grid");
            return node.getGrid().getStorageService().getInventory();
        }

        long remaining() {
            return NeoForgeNetworkTransferBudget.remainingItems(server, owner);
        }

        long remainingFluid() {
            return NeoForgeNetworkTransferBudget.remainingFluid(server, owner);
        }

        CompoundTag ledger() {
            return NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess());
        }

        EmcAmount spendable() {
            var ledger = NeoForgeEmcSavedData.get(server);
            check(ledger.debit(owner).signum() == 0, "The online native EMC account must acknowledge every whole debit");
            return EmcAmount.of(knowledge().getEmc()).add(ledger.pending(owner));
        }

        void await(BooleanSupplier condition, Runnable action, String description) {
            await(condition, action, description, 180);
        }

        private void await(BooleanSupplier condition, Runnable action, String description, int retries) {
            run(() -> {
                if (condition.getAsBoolean()) action.run();
                else if (retries <= 0) throw new GameTestAssertException(description);
                else helper.runAfterDelay(1, () -> await(condition, action, description, retries - 1));
            });
        }

        void run(Runnable action) {
            if (closed) return;
            try { action.run(); }
            catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        void succeed() {
            close();
            helper.succeed();
        }

        void close() {
            if (closed) return;
            closed = true;
            FIXTURES.remove(helper);
            try {
                for (BlockPos position : placed) helper.setBlock(position, Blocks.AIR);
            } finally { logout(); }
        }

        void logout() {
            try {
                if (server.getPlayerList().getPlayer(owner) == player) server.getPlayerList().remove(player);
            } finally {
                if (player.connection != null
                        && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                    channel.finishAndReleaseAll();
            }
        }

        private static ServerPlayer login(MinecraftServer server) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), "ae2-grid-trader");
            CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(),
                    false, ConnectionType.NEOFORGE);
            ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(connection);
            try {
                NetworkRegistry.configureMockConnection(connection);
                server.getPlayerList().placeNewPlayer(connection, player, cookie);
                return player;
            } catch (RuntimeException | Error failure) {
                try {
                    if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                        server.getPlayerList().remove(player);
                } finally { channel.finishAndReleaseAll(); }
                throw failure;
            }
        }
    }
}
