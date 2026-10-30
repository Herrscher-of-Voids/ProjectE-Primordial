/** Real adjacent RS2 controller networks exercise production discovery and account-backed storage. */
package com.primordial.neoforge.compat.rs2;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
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
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.network.storage.StorageProvider;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.common.support.resource.FluidResource;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class NeoForgeRs2GridGameTests {
    private static final BlockPos INTERFACE = new BlockPos(1, 1, 1);
    private static final BlockPos CONTROLLER = new BlockPos(2, 1, 1);
    private static final ItemResource DIAMOND = new ItemResource(Items.DIAMOND);
    private static final FluidResource WATER = new FluidResource(Fluids.WATER);
    private static final ResourceLocation WATER_ID = ResourceLocation.parse("minecraft:water");
    private static final BigInteger FUNDING = BigInteger.TEN.pow(12);
    private static final java.util.Map<GameTestHelper, Fixture> FIXTURES = new java.util.IdentityHashMap<>();

    private NeoForgeRs2GridGameTests() {}

    public static List<TestFunction> tests() {
        return List.of(test("rs2_native_grid_controller_first", NeoForgeRs2GridGameTests::controllerFirst),
                test("rs2_native_grid_interface_first_item_transactions", NeoForgeRs2GridGameTests::itemTransactions),
                test("rs2_native_grid_fluid_fraction_and_simulation", NeoForgeRs2GridGameTests::fluidTransactions),
                test("rs2_native_split_grids_share_owner_item_and_fluid_budgets", NeoForgeRs2GridGameTests::sharedBudgets),
                test("rs2_native_grid_fluid_authorization", NeoForgeRs2GridGameTests::fluidAuthorization),
                test("rs2_native_grid_reload_offline_suspend_and_remove", NeoForgeRs2GridGameTests::lifecycle));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("defaultBatch", name, "primordial:capability_proxy_empty", Rotation.NONE,
                650, 0, true, helper -> {
                    Fixture fixture = new Fixture(helper);
                    fixture.run(() -> body.accept(helper));
                });
    }

    private static Fixture fixture(GameTestHelper helper) {
        Fixture fixture = FIXTURES.get(helper);
        check(fixture != null, "Missing native RS2 fixture");
        return fixture;
    }

    private static void controllerFirst(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.placeController(CONTROLLER);
        f.await(() -> f.node(CONTROLLER) != null && f.node(CONTROLLER).getNetwork() != null, () -> {
            var block = f.placeInterface(INTERFACE);
            block.saveWithFullMetadata(helper.getLevel().registryAccess());
            f.await(() -> f.ready(INTERFACE, CONTROLLER), () -> {
                Network network = f.node(INTERFACE).getNetwork();
                check(network.getComponent(GraphNetworkComponent.class).getContainers().size() == 2,
                        "The interface must join the real two-node world graph without manually merging networks");
                for (Direction side : Direction.values())
                    check(f.provider(INTERFACE, side) != null
                                    && f.provider(INTERFACE, side).getContainers().stream()
                                    .anyMatch(container -> container.getNode() == f.node(INTERFACE)),
                            "Every side must advertise the actual production RS2 node-container capability");
                check(f.owner.equals(block.owner()), "A save before connection must retain the placing owner");
                f.succeed();
            }, "controller-first interface never joined the powered native RS2 graph");
        }, "real RS2 creative controller never initialized");
    }

    private static void itemTransactions(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.placeInterface(INTERFACE);
        f.await(() -> f.node(INTERFACE) != null, () -> {
            check(!f.active(INTERFACE), "An isolated interface must not supply an unpowered RS2 network");
            f.placeController(CONTROLLER);
            f.await(() -> f.ready(INTERFACE, CONTROLLER), () -> {
                StorageNetworkComponent inventory = f.inventory(INTERFACE);
                IKnowledgeProvider knowledge = f.knowledge();
                long budget = f.remainingItems();
                boolean learning = NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.get();
                try {
                    NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(true);
                    check(inventory.get(DIAMOND) == 0
                                    && inventory.extract(DIAMOND, 3, Action.SIMULATE, Actor.EMPTY) == 0
                                    && inventory.extract(DIAMOND, 3, Action.EXECUTE, Actor.EMPTY) == 0,
                            "A funded owner must not generate unknown items through native RS2 storage");
                    check(inventory.insert(DIAMOND, 3, Action.SIMULATE, Actor.EMPTY) == 3
                                    && inventory.insert(DIAMOND, 3, Action.SIMULATE, Actor.EMPTY) == 3
                                    && !knowledge.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND))
                                    && knowledge.getEmc().equals(FUNDING) && f.remainingItems() == budget
                                    && inventory.get(DIAMOND) == 0,
                            "Repeated native RS2 insertion simulation must not teach, pay or reserve owner allowance");
                    BigInteger sell = NeoForgeRecoveryPrices.item(new ItemStack(Items.DIAMOND)).floor();
                    BigInteger buy = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)).floor();
                    check(sell.signum() > 0 && buy.signum() > 0, "Real ProjectE must price diamonds");
                    check(inventory.insert(DIAMOND, 3, Action.EXECUTE, Actor.EMPTY) == 3
                                    && knowledge.hasKnowledge(ItemInfo.fromItem(Items.DIAMOND))
                                    && knowledge.getEmc().equals(FUNDING.add(sell.multiply(BigInteger.valueOf(3))))
                                    && f.remainingItems() == budget - 3
                                    && inventory.get(DIAMOND) == budget - 3,
                            "Native RS2 reclamation must publish precisely 4093 virtual items, teach and credit only accepted units");
                    BigInteger before = knowledge.getEmc();
                    check(inventory.extract(DIAMOND, 5, Action.SIMULATE, Actor.EMPTY) == 5
                                    && inventory.extract(DIAMOND, 5, Action.SIMULATE, Actor.EMPTY) == 5
                                    && knowledge.getEmc().equals(before) && f.remainingItems() == budget - 3
                                    && inventory.get(DIAMOND) == budget - 3,
                            "Repeated native RS2 extraction simulation must not debit or reserve anything");
                    check(inventory.extract(DIAMOND, 5, Action.EXECUTE, Actor.EMPTY) == 5
                                    && knowledge.getEmc().equals(before.subtract(buy.multiply(BigInteger.valueOf(5))))
                                    && f.remainingItems() == budget - 8
                                    && inventory.get(DIAMOND) == budget - 8,
                            "Native RS2 extraction must charge once and publish exactly 4088 remaining virtual items");
                    check(inventory.extract(WATER, 1000, Action.EXECUTE, Actor.EMPTY) == 0,
                            "Item knowledge must not authorize unrelated fluid generation");
                } finally {
                    NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(learning);
                }
                helper.runAfterDelay(1, () -> f.await(
                        () -> inventory.get(DIAMOND) == f.remainingItems(), f::succeed,
                        "The real RS2 root catalogue did not refresh to the next tick's exact owner allowance"));
            }, "interface-first RS2 network never became powered");
        }, "isolated RS2 interface capability never initialized");
    }

    private static void fluidTransactions(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        f.placeInterface(INTERFACE);
        f.placeController(CONTROLLER);
        f.await(() -> f.ready(INTERFACE, CONTROLLER), () -> {
            // The exact price is published and restored within one synchronous callback; administrator files stay untouched.
            try (FluidPriceSnapshot ignored = new FluidPriceSnapshot()) {
                StorageNetworkComponent inventory = f.inventory(INTERFACE);
                EmcAmount unit = new EmcAmount(BigInteger.ONE, BigInteger.valueOf(3000));
                check(Platform.INSTANCE.getBucketAmount() == 1000
                                && NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).equals(unit),
                        "NeoForge RS2 fluid amounts must use mB with the exact one-third EMC bucket quote");
                long budget = f.remainingFluid();
                CompoundTag ledger = f.ledger();
                check(inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && inventory.insert(WATER, 37, Action.SIMULATE, Actor.EMPTY) == 37
                                && inventory.insert(WATER, 37, Action.SIMULATE, Actor.EMPTY) == 37
                                && !NeoForgeRecoveryKnowledge.knowsFluid(f.server, f.owner, WATER_ID)
                                && f.knowledge().getEmc().equals(FUNDING) && f.ledger().equals(ledger)
                                && f.remainingFluid() == budget,
                        "RS2 simulation must not teach fluid knowledge, credit fractional EMC or consume bandwidth");
                FluidStack decorated = new FluidStack(Fluids.WATER, 1);
                decorated.set(DataComponents.CUSTOM_NAME, Component.literal("non-generatable component variant"));
                FluidResource variant = new FluidResource(Fluids.WATER, decorated.getComponentsPatch());
                check(!variant.equals(WATER)
                                && inventory.insert(variant, 37, Action.EXECUTE, Actor.EMPTY) == 37
                                && NeoForgeRecoveryKnowledge.knowsFluid(f.server, f.owner, WATER_ID)
                                && f.spendable().equals(EmcAmount.of(FUNDING).add(unit.multiply(37)))
                                    && f.remainingFluid() == budget - 37
                                    && inventory.get(WATER) == budget - 37 && inventory.get(variant) == 0,
                        "Actual component-bearing fluid reclamation must learn the base source and credit exact mB");
                EmcAmount before = f.spendable();
                check(inventory.extract(variant, 1, Action.SIMULATE, Actor.EMPTY) == 0
                                && inventory.extract(variant, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && inventory.extract(WATER, 5, Action.SIMULATE, Actor.EMPTY) == 5
                                && inventory.extract(WATER, 5, Action.SIMULATE, Actor.EMPTY) == 5
                                && f.spendable().equals(before) && f.remainingFluid() == budget - 37
                                && inventory.get(WATER) == budget - 37 && inventory.get(variant) == 0,
                        "Fluid extraction must reject arbitrary components and keep plain-fluid simulation read-only");
                check(inventory.extract(WATER, 5, Action.EXECUTE, Actor.EMPTY) == 5
                                && f.spendable().equals(before.subtract(unit.multiply(5)))
                                && f.remainingFluid() == budget - 42 && inventory.get(WATER) == budget - 42,
                        "Plain source fluid extraction must charge exact fractions for the accepted mB");
                f.knowledge().setEmc(BigInteger.ZERO);
                check(f.spendable().equals(unit.multiply(32))
                                && inventory.extract(WATER, Long.MAX_VALUE, Action.SIMULATE, Actor.EMPTY) == 32
                                && inventory.extract(WATER, Long.MAX_VALUE, Action.EXECUTE, Actor.EMPTY) == 32
                                && f.spendable().isZero()
                                && inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && f.remainingFluid() == budget - 74 && inventory.get(WATER) == 0,
                        "Fraction-only RS2 purchases must deliver exactly affordable mB without free rounding or overdraft");
                check(f.remainingItems() == NeoForgeNetworkTransferBudget.ITEMS_PER_TICK,
                        "Fluid operations must leave the independent item allowance untouched");
            }
            f.succeed();
        }, "fluid-trading native RS2 network never became powered");
    }

    private static void sharedBudgets(GameTestHelper helper) {
        Fixture f = fixture(helper);
        BlockPos first = new BlockPos(0, 1, 0), firstController = new BlockPos(1, 1, 0);
        BlockPos second = new BlockPos(0, 1, 2), secondController = new BlockPos(1, 1, 2);
        f.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        NeoForgeRecoveryKnowledge.recordFluid(f.server, f.owner, WATER_ID);
        f.placeInterface(first);
        f.placeController(firstController);
        f.placeController(secondController);
        f.placeInterface(second);
        f.await(() -> f.ready(first, firstController) && f.ready(second, secondController), () -> {
            check(f.node(first).getNetwork() != f.node(second).getNetwork(),
                    "Owner limits must be exercised by two genuinely separate native RS2 world networks");
            StorageNetworkComponent a = f.inventory(first), b = f.inventory(second);
            long items = NeoForgeNetworkTransferBudget.ITEMS_PER_TICK;
            long fluid = NeoForgeNetworkTransferBudget.FLUID_MB_PER_TICK;
            check(a.extract(DIAMOND, Long.MAX_VALUE, Action.SIMULATE, Actor.EMPTY) == items
                            && b.extract(DIAMOND, Long.MAX_VALUE, Action.SIMULATE, Actor.EMPTY) == items
                            && a.insert(WATER, Long.MAX_VALUE, Action.SIMULATE, Actor.EMPTY) == fluid
                            && f.remainingItems() == items && f.remainingFluid() == fluid,
                    "Simulation on distinct networks must cap requests without consuming either shared owner allowance");
            BigInteger beforeItems = f.knowledge().getEmc();
            BigInteger buy = NeoForgeRecoveryPrices.itemPurchase(new ItemStack(Items.DIAMOND)).floor();
            check(a.extract(DIAMOND, 3000, Action.EXECUTE, Actor.EMPTY) == 3000
                            && b.extract(DIAMOND, 3000, Action.EXECUTE, Actor.EMPTY) == items - 3000
                            && a.insert(DIAMOND, 1, Action.EXECUTE, Actor.EMPTY) == 0
                            && f.remainingItems() == 0 && f.remainingFluid() == fluid
                            && f.knowledge().getEmc().equals(beforeItems.subtract(buy.multiply(BigInteger.valueOf(items)))),
                    "Independent RS2 grids must share one item allowance and charge only accepted units");
            EmcAmount beforeFluid = f.spendable();
            FluidStack sample = new FluidStack(Fluids.WATER, 1);
            EmcAmount sale = NeoForgeRecoveryPrices.fluid(sample).multiply(600000);
            EmcAmount purchase = NeoForgeRecoveryPrices.fluidPurchase(sample).multiply(fluid - 600000);
            check(a.insert(WATER, 600000, Action.EXECUTE, Actor.EMPTY) == 600000
                            && b.extract(WATER, Long.MAX_VALUE, Action.EXECUTE, Actor.EMPTY) == fluid - 600000
                            && a.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                            && b.insert(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                            && f.remainingFluid() == 0
                            && f.spendable().equals(beforeFluid.add(sale).subtract(purchase)),
                    "Both trade directions across RS2 grids must share one million mB and settle exact accepted amounts");
            helper.runAfterDelay(1, () -> f.run(() -> {
                check(f.remainingItems() == items && f.remainingFluid() == fluid
                                && b.extract(DIAMOND, 1, Action.EXECUTE, Actor.EMPTY) == 1
                                && a.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 1,
                        "Both shared budgets must reset on the next real server tick");
                f.succeed();
            }));
        }, "separate native RS2 budget networks never became powered");
    }

    private static void fluidAuthorization(GameTestHelper helper) {
        Fixture f = fixture(helper);
        NeoForgeRecoveryKnowledge.recordFluid(f.server, f.owner, WATER_ID);
        f.placeInterface(INTERFACE);
        f.placeController(CONTROLLER);
        f.await(() -> f.ready(INTERFACE, CONTROLLER), () -> {
            StorageNetworkComponent inventory = f.inventory(INTERFACE);
            Storage source = f.mountedStorage(INTERFACE);
            ResourceLocation gate = ResourceLocation.parse("primordial:rs2_fluid_gate");
            var stages = NeoForgePlayerStagesSavedData.get(f.server);
            List<? extends String> stageRules = List.copyOf(NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.get());
            List<? extends String> blacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
            boolean purchase = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
            boolean sale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
            try {
                NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=" + gate));
                EmcAmount before = f.spendable();
                long budget = f.remainingFluid();
                check(source.getAll().stream().noneMatch(value -> value.resource().equals(WATER))
                                && inventory.extract(WATER, 1, Action.SIMULATE, Actor.EMPTY) == 0
                                && inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && f.spendable().equals(before) && f.remainingFluid() == budget,
                        "Native RS2 supply and transactions must enforce the owner's missing stage");
                check(inventory.insert(WATER, 17, Action.EXECUTE, Actor.EMPTY) == 17,
                        "A purchase stage must leave actual fluid reclamation available");
                stages.grant(f.owner, gate);
                check(source.getAll().stream().anyMatch(value -> value.resource().equals(WATER))
                                && inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 1,
                        "Granting the personal stage must enable real RS2 fluid purchases");
                stages.revoke(f.owner, gate);
                check(inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0,
                        "Revoking the personal stage must immediately deny a cached native RS2 fluid key");
                stages.grant(f.owner, gate);
                check(NeoForgeResourceKnowledge.forgetMedia(f.server, f.owner, Kind.FLUID, WATER_ID)
                                && inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && source.getAll().stream().noneMatch(value -> value.resource().equals(WATER)),
                        "Explicit forgetting must deny previously learned RS2 supply");
                check(inventory.insert(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 1
                                && NeoForgeRecoveryKnowledge.knowsFluid(f.server, f.owner, WATER_ID),
                        "Only actual fluid reclamation may relearn forgotten fluid knowledge");
                before = f.spendable();
                budget = f.remainingFluid();
                NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:water"));
                check(inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && inventory.insert(WATER, 1, Action.SIMULATE, Actor.EMPTY) == 0
                                && inventory.insert(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && f.spendable().equals(before) && f.remainingFluid() == budget,
                        "Blacklisting must deny both RS2 trade directions without mutation");
                NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
                NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(false);
                NeoForgeServerConfig.FLUID_SALE_ENABLED.set(false);
                check(inventory.extract(WATER, 1, Action.SIMULATE, Actor.EMPTY) == 0
                                && inventory.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && inventory.insert(WATER, 1, Action.SIMULATE, Actor.EMPTY) == 0
                                && inventory.insert(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                && f.spendable().equals(before) && f.remainingFluid() == budget,
                        "Purchase/sale switches must deny RS2 operations without EMC or bandwidth loss");
            } finally {
                stages.revoke(f.owner, gate);
                NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(stageRules);
                NeoForgeServerConfig.FLUID_BLACKLIST.set(blacklist);
                NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(purchase);
                NeoForgeServerConfig.FLUID_SALE_ENABLED.set(sale);
            }
            f.succeed();
        }, "native RS2 authorization network never became powered");
    }

    private static void lifecycle(GameTestHelper helper) {
        Fixture f = fixture(helper);
        f.knowledge().addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
        NeoForgeRecoveryKnowledge.recordFluid(f.server, f.owner, WATER_ID);
        var block = f.placeInterface(INTERFACE);
        f.placeController(CONTROLLER);
        f.await(() -> f.ready(INTERFACE, CONTROLLER), () -> {
            NetworkNodeContainerProvider cached = f.provider(INTERFACE, Direction.EAST);
            NetworkNode original = f.node(INTERFACE);
            Storage old = f.mountedStorage(INTERFACE);
            check(old.getAll().stream().anyMatch(value -> value.resource().equals(DIAMOND))
                            && old.getAll().stream().anyMatch(value -> value.resource().equals(WATER)),
                    "The native storage provider must expose the owner's learned item and fluid");
            CompoundTag saved = block.saveWithFullMetadata(helper.getLevel().registryAccess());
            f.logout();
            block.loadWithComponents(saved, helper.getLevel().registryAccess());
            check(cached.getContainers().isEmpty(), "NBT reload must hide the previous live RS2 container");
            rejects(old, "NBT reload");
            f.await(() -> f.ready(INTERFACE, CONTROLLER), () -> {
                check(f.node(INTERFACE) != original && f.owner.equals(block.owner())
                                && cached.getContainers().stream().anyMatch(c -> c.getNode() == f.node(INTERFACE)),
                        "Offline restore must preserve owner and refresh cached production capability discovery");
                Storage restored = f.mountedStorage(INTERFACE);
                check(restored.getAll().stream().anyMatch(value -> value.resource().equals(DIAMOND))
                                && restored.getAll().stream().anyMatch(value -> value.resource().equals(WATER)),
                        "An offline owner's real RS2 supply must remount both learned resource types");
                block.onChunkUnloaded();
                check(cached.getContainers().isEmpty(), "Chunk suspension must hide the live RS2 container");
                rejects(restored, "chunk suspension");
                block.onLoad();
                f.await(() -> f.ready(INTERFACE, CONTROLLER), () -> {
                    Storage finalSource = f.mountedStorage(INTERFACE);
                    StorageNetworkComponent finalRoot = f.inventory(INTERFACE);
                    helper.setBlock(INTERFACE, Blocks.AIR);
                    check(cached.getContainers().isEmpty() && f.provider(INTERFACE, Direction.EAST) == null,
                            "Actual block removal must invalidate cached and fresh RS2 discovery");
                    rejects(finalSource, "actual removal");
                    check(!finalRoot.hasSource(source -> source == finalSource) && finalRoot.getAll().isEmpty()
                                    && finalRoot.get(DIAMOND) == 0 && finalRoot.get(WATER) == 0
                                    && finalRoot.getStored() == 0
                                    && finalRoot.extract(DIAMOND, 1, Action.EXECUTE, Actor.EMPTY) == 0
                                    && finalRoot.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0,
                            "Actual removal must unmount virtual supply from the native RS2 root storage");
                    f.succeed();
                }, "chunk-resumed RS2 interface never rejoined the native graph");
            }, "offline-loaded RS2 interface never rejoined the native graph");
        }, "native RS2 lifecycle network never became powered");
    }

    private static void rejects(Storage storage, String phase) {
        check(storage.getAll().isEmpty() && storage.getStored() == 0
                        && storage.extract(DIAMOND, 1, Action.SIMULATE, Actor.EMPTY) == 0
                        && storage.extract(DIAMOND, 1, Action.EXECUTE, Actor.EMPTY) == 0
                        && storage.insert(DIAMOND, 1, Action.EXECUTE, Actor.EMPTY) == 0
                        && storage.extract(WATER, 1, Action.SIMULATE, Actor.EMPTY) == 0
                        && storage.extract(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0
                        && storage.insert(WATER, 1, Action.EXECUTE, Actor.EMPTY) == 0,
                "Retained storage after " + phase + " must reject stale reads and trades");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

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
                JsonObject fluids = new JsonObject();
                fluids.addProperty("minecraft:water", "1/3");
                root.add("fluid_values", fluids);
                var prepare = NeoForgeRecoveryPrices.class.getDeclaredMethod("prepareEdited", JsonObject.class, boolean.class);
                prepare.setAccessible(true);
                Object prepared = prepare.invoke(null, root, true);
                var publish = NeoForgeRecoveryPrices.class.getDeclaredMethod("publishEdited", prepared.getClass());
                publish.setAccessible(true);
                publish.invoke(null, prepared);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot prepare RS2 fractional-price fixture", failure);
            }
        }

        @Override public void close() {
            try {
                snapshot.set(null, before);
                loaded.setBoolean(null, wasLoaded);
                NeoForgeRecoveryPrices.invalidatePrices();
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot restore RS2 fractional-price fixture", failure);
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
                    "Expected the production transmutation interface");
            var block = (NeoForgeTransmutationInterfaceBlockEntity) helper.getBlockEntity(position);
            block.bindOwner(owner);
            block.onLoad();
            return block;
        }

        void placeController(BlockPos position) {
            Block controller = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("refinedstorage:creative_controller"));
            check(controller != Blocks.AIR, "Installed RS2 must register its creative controller");
            placed.add(position);
            helper.setBlock(position, controller);
        }

        NetworkNodeContainerProvider provider(BlockPos position, Direction side) {
            return helper.getLevel().getCapability(
                    RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
                    helper.absolutePos(position), side);
        }

        NetworkNode node(BlockPos position) {
            NetworkNodeContainerProvider provider = provider(position, Direction.EAST);
            if (provider == null || provider.getContainers().isEmpty()) return null;
            check(provider.getContainers().size() == 1, "Each fixture block must advertise exactly one native container");
            return provider.getContainers().iterator().next().getNode();
        }

        boolean active(BlockPos position) {
            return node(position) instanceof AbstractNetworkNode node && node.isActive();
        }

        boolean ready(BlockPos position, BlockPos controllerPosition) {
            NetworkNode node = node(position), controller = node(controllerPosition);
            return node != null && controller != null && node.getNetwork() != null
                    && node.getNetwork() == controller.getNetwork() && active(position);
        }

        StorageNetworkComponent inventory(BlockPos position) {
            check(active(position), "Transactions must use a real powered native RS2 graph");
            return node(position).getNetwork().getComponent(StorageNetworkComponent.class);
        }

        Storage mountedStorage(BlockPos position) {
            check(node(position) instanceof StorageProvider,
                    "Production RS2 node must implement the official storage-provider contract");
            return ((StorageProvider) node(position)).getStorage();
        }

        long remainingItems() { return NeoForgeNetworkTransferBudget.remainingItems(server, owner); }
        long remainingFluid() { return NeoForgeNetworkTransferBudget.remainingFluid(server, owner); }
        CompoundTag ledger() { return NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess()); }

        EmcAmount spendable() {
            var ledger = NeoForgeEmcSavedData.get(server);
            check(ledger.debit(owner).signum() == 0, "Online native EMC must acknowledge each whole debit");
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

        void succeed() { close(); helper.succeed(); }

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
            GameProfile profile = new GameProfile(UUID.randomUUID(), "rs2-grid-trader");
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
