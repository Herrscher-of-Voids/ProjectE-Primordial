/**
 * Native powered ME-grid integration: adjacent capability discovery, account-backed transactions,
 * per-owner budgets and node persistence. No private adapter storage or synthetic grid is used.
 */
package com.primordial.neoforge.compat.ae2;

import java.math.BigInteger;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
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
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
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
                test("ae2_native_split_grids_share_owner_tick_budget", NeoForgeAe2GridGameTests::sharedBudget),
                test("ae2_native_grid_fluid_fraction_simulation_and_components", NeoForgeAe2GridGameTests::fluidTransactions),
                test("ae2_native_split_grids_share_fluid_tick_budget", NeoForgeAe2GridGameTests::sharedFluidBudget),
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
                    check(inventory.getAvailableStacks().get(DIAMOND) == budget - 3,
                            "The ME grid catalogue must expose the owner's affordable learned item and current limit");
                    BigInteger before = knowledge.getEmc();
                    check(inventory.extract(DIAMOND, 5, Actionable.SIMULATE, source) == 5
                                    && inventory.extract(DIAMOND, 5, Actionable.SIMULATE, source) == 5
                                    && knowledge.getEmc().equals(before) && fixture.remaining() == budget - 3,
                            "Repeated native ME extraction simulation must be read-only");
                    check(inventory.extract(DIAMOND, 5, Actionable.MODULATE, source) == 5
                                    && knowledge.getEmc().equals(before.subtract(buy.multiply(BigInteger.valueOf(5))))
                                    && fixture.remaining() == budget - 8,
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

    private static void sharedBudget(GameTestHelper helper) {
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
                    "The two budget fixtures must be genuinely separate ME networks");
            MEStorage a = fixture.inventory(first), b = fixture.inventory(second);
            IActionSource source = IActionSource.ofPlayer(fixture.player);
            long limit = NeoForgeNetworkTransferBudget.ITEMS_PER_TICK;
            check(fixture.remaining() == limit, "Fresh owner must have its complete per-tick budget");
            check(a.extract(DIAMOND, Long.MAX_VALUE, Actionable.SIMULATE, source) == limit
                            && b.extract(DIAMOND, Long.MAX_VALUE, Actionable.SIMULATE, source) == limit,
                    "Simulation on either real grid must not reserve any owner budget");
            BigInteger unit = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)).floor();
            BigInteger before = fixture.knowledge().getEmc();
            check(a.extract(DIAMOND, 3000, Actionable.MODULATE, source) == 3000
                            && b.extract(DIAMOND, 3000, Actionable.MODULATE, source) == limit - 3000
                            && a.extract(DIAMOND, 1, Actionable.MODULATE, source) == 0
                            && b.insert(DIAMOND, 1, Actionable.MODULATE, source) == 0
                            && fixture.remaining() == 0,
                    "Purchases and reclamation on independent ME grids must share the same owner budget");
            check(fixture.knowledge().getEmc().equals(before.subtract(unit.multiply(BigInteger.valueOf(limit)))),
                    "Splitting a request across grids must charge exactly the shared accepted quantity");
            fixture.helper.runAfterDelay(1, () -> fixture.run(() -> {
                check(fixture.remaining() == limit
                                && b.extract(DIAMOND, 1, Actionable.MODULATE, source) == 1,
                        "The shared owner budget must become usable again on the next real server tick");
                fixture.succeed();
            }));
        }, "isolated shared-budget grids never became active");
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
                                && fixture.remainingFluid() == limit - 37,
                        "Real component-bearing fluid reclamation must destroy 37 mB, learn only its ID and credit its exact value");
                check(inventory.getAvailableStacks().get(WATER) == limit - 37
                                && inventory.getAvailableStacks().get(decoratedKey) == 0
                                && inventory.getAvailableStacks().get(DIAMOND) == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK,
                        "One native ME grid must list plain learned fluids and items using independent per-kind budgets");
                EmcAmount before = fixture.spendable();
                ledger = fixture.ledger();
                check(inventory.extract(decoratedKey, 1, Actionable.SIMULATE, source) == 0
                                && inventory.extract(decoratedKey, 1, Actionable.MODULATE, source) == 0
                                && inventory.extract(WATER, 5, Actionable.SIMULATE, source) == 5
                                && inventory.extract(WATER, 5, Actionable.SIMULATE, source) == 5
                                && fixture.spendable().equals(before) && fixture.ledger().equals(ledger)
                                && fixture.remainingFluid() == limit - 37,
                        "A base fluid identity must not recreate discarded components; all extraction simulations stay read-only");
                check(inventory.extract(WATER, 5, Actionable.MODULATE, source) == 5
                                && fixture.spendable().equals(before.subtract(unit.multiply(5)))
                                && fixture.remainingFluid() == limit - 42,
                        "Native ME extraction must deliver five mB and debit the exact fractional price once");
                long fluidBudget = fixture.remainingFluid();
                check(inventory.extract(DIAMOND, 3, Actionable.MODULATE, source) == 3
                                && fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK - 3
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
                check(fixture.remainingFluid() == limit - 74
                                && fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK - 3,
                        "Only accepted fluid units may consume the independent owner budget");
            }
            fixture.succeed();
        }, "native fluid transaction grid never became active");
    }

    private static void sharedFluidBudget(GameTestHelper helper) {
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
                    "Fluid budget sharing must be exercised by genuinely separate native ME networks");
            MEStorage a = fixture.inventory(first), b = fixture.inventory(second);
            IActionSource source = IActionSource.ofPlayer(fixture.player);
            long limit = NeoForgeNetworkTransferBudget.FLUID_MB_PER_TICK;
            check(fixture.remainingFluid() == limit
                            && a.extract(WATER, Long.MAX_VALUE, Actionable.SIMULATE, source) == limit
                            && b.insert(WATER, Long.MAX_VALUE, Actionable.SIMULATE, source) == limit
                            && fixture.remainingFluid() == limit,
                    "Native grids must cap simulated fluid amounts in mB without reserving owner allowance");
            EmcAmount before = fixture.spendable();
            FluidStack sample = new FluidStack(Fluids.WATER, 1);
            EmcAmount sold = NeoForgeRecoveryPrices.fluid(sample).multiply(600000);
            EmcAmount bought = NeoForgeRecoveryPrices.fluidPurchase(sample).multiply(limit - 600000);
            check(a.insert(WATER, 600000, Actionable.MODULATE, source) == 600000
                            && b.extract(WATER, Long.MAX_VALUE, Actionable.MODULATE, source) == limit - 600000
                            && a.extract(WATER, 1, Actionable.MODULATE, source) == 0
                            && b.insert(WATER, 1, Actionable.MODULATE, source) == 0
                            && fixture.remainingFluid() == 0
                            && fixture.spendable().equals(before.add(sold).subtract(bought)),
                    "Reclamation and purchases across separate grids must share one million mB and settle only accepted units");
            check(inventoryItemTradeAfterFluidLimit(fixture, b, source),
                    "Exhausting the fluid budget must leave the independent item budget available");
            fixture.helper.runAfterDelay(1, () -> fixture.run(() -> {
                check(fixture.remainingFluid() == limit
                                && b.extract(WATER, 1, Actionable.MODULATE, source) == 1
                                && fixture.remainingFluid() == limit - 1,
                        "Every owner's shared fluid allowance must reset on the next real server tick");
                fixture.succeed();
            }));
        }, "isolated shared-fluid-budget grids never became active");
    }

    private static boolean inventoryItemTradeAfterFluidLimit(Fixture fixture, MEStorage inventory, IActionSource source) {
        return fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK
                && inventory.extract(DIAMOND, 1, Actionable.MODULATE, source) == 1
                && fixture.remaining() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK - 1
                && fixture.remainingFluid() == 0;
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
                    check(resumed != restored && resumed.getOwningPlayerId() == ownerId,
                            "Resuming a suspended interface must retain owner identity on a fresh native node");
                    MEStorage finalStorage = mountedStorage(resumed);
                    MEStorage finalNetwork = fixture.inventory(INTERFACE);
                    helper.setBlock(INTERFACE, Blocks.AIR);
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
