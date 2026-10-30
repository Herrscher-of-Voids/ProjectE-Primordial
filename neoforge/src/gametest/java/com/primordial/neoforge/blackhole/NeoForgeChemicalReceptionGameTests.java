/**
 * 【文件职责】不加载 Mekanism 类型，通过公共化学品 ID 桥验收黑洞交易的重入、线程和预算边界。
 * 【变更记录 #1】2026-10-22 22:23:40（第 1 次备注 · 2.0.0-neo-dev 统一化学品回收）
 * - 删除：无。
 * - 新增：标签报价期间的缓存端口/换绑拒绝、回调移除和原位 NBT 加载、失效引用与异线程拒绝测试。
 * - 修改：使用真实方块、世界账本和知识服务；一个独立批次顺序验证模拟、long 请求及跨 tick 预算。
 * - 用途：证明只有实际接受量产生一次 EMC 和化学品知识；配置只临时 set 并在 finally 恢复，不改价表文件。
 */
package com.primordial.neoforge.blackhole;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeChemicalReceptionGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);
    private static final ResourceLocation CHEMICAL = ResourceLocation.parse("primordial:chemical_reception_probe");
    private static final ResourceLocation SECOND = ResourceLocation.parse("primordial:chemical_reception_other");
    private static final Predicate<ResourceLocation> NOT_IN_TAG = tag -> false;

    private NeoForgeChemicalReceptionGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "chemical_reception_config", setupTicks = 0, required = true)
    public static void protectsPublicChemicalTransactionsAndRefreshesSharedBudget(GameTestHelper helper) {
        ConfigSnapshot config = new ConfigSnapshot();
        try {
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of("#primordial:chemical_reception_probe"));
            NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.set(7);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of());
            check(NeoForgeRecoveryPrices.chemical(CHEMICAL, NOT_IN_TAG).equals(EmcAmount.ONE),
                    "The common chemical fixture must use the normal one EMC per mB default");
            rejectsOtherPortsAndOwnerChangesWhileQuoting(helper);
            rejectsQuoteAfterLoadEvenWithUnchangedOwner(helper);
            rejectsQuoteAfterRemovalAndRejectsAllStalePorts(helper);
            rejectsFailedTagLookupWithoutSpendingBudget(helper);
            rejectsWorkerThreadWithoutCallingExternalPredicate(helper);
            clipsLongRequestsWithoutOverflowOrSimulationSideEffects(helper);

            NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.set(7);
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            check(block.recycleChemical(CHEMICAL, 7, false, NOT_IN_TAG) == 7
                            && block.recycleChemical(SECOND, 1, false, NOT_IN_TAG) == 0,
                    "Different chemical IDs must share one seven-mB budget within the same world tick");
            helper.runAfterDelay(2, () -> {
                try {
                    check(block.recycleChemical(SECOND, 100, true, NOT_IN_TAG) == 7
                                    && block.recycleChemical(SECOND, 5, false, NOT_IN_TAG) == 5
                                    && block.recycleChemical(CHEMICAL, 100, false, NOT_IN_TAG) == 2,
                            "A later world tick must restore one shared budget and simulations must not consume it");
                    check(block.settlePending() && ledger(helper).pending(owner).equals(EmcAmount.of(BigInteger.valueOf(14))),
                            "Two ticks must credit exactly fourteen accepted mB, including partial transfers");
                    helper.succeed();
                } finally {
                    config.close();
                    helper.setBlock(POSITION, Blocks.AIR);
                }
            });
        } catch (RuntimeException | Error failure) {
            config.close();
            helper.setBlock(POSITION, Blocks.AIR);
            throw failure;
        }
    }

    private static void rejectsOtherPortsAndOwnerChangesWhileQuoting(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        IItemHandler items = block.itemHandler();
        IFluidHandler fluids = block.fluidHandler();
        IEnergyStorage energy = block.energyHandler();
        ItemStack source = new ItemStack(Items.DIAMOND, 3);
        FluidStack water = new FluidStack(Fluids.WATER, 5);
        check(items.insertItem(0, source, true).isEmpty()
                        && fluids.fill(water, IFluidHandler.FluidAction.SIMULATE) == 5
                        && energy.receiveEnergy(5, true) == 5,
                "The other native ports must accept valid simulations before chemical quotation begins");
        CompoundTag before = save(helper, block);
        AtomicInteger calls = new AtomicInteger();
        Predicate<ResourceLocation> reentrant = tag -> {
            calls.incrementAndGet();
            check(!block.canRecycleResources()
                            && block.recycleChemical(SECOND, 1, false, NOT_IN_TAG) == 0
                            && !block.isChemicalValid(SECOND, NOT_IN_TAG),
                    "A chemical quote must close recursive chemical transfers and validity queries");
            check(items.insertItem(0, source, true).getCount() == 3
                            && items.insertItem(0, source, false).getCount() == 3 && !items.isItemValid(0, source),
                    "A chemical quote must close both simulated and actual cached item input");
            check(fluids.fill(water, IFluidHandler.FluidAction.SIMULATE) == 0
                            && fluids.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0 && !fluids.isFluidValid(0, water),
                    "A chemical quote must close both simulated and actual cached fluid input");
            check(energy.receiveEnergy(5, true) == 0 && energy.receiveEnergy(5, false) == 0 && !energy.canReceive(),
                    "A chemical quote must close both simulated and actual cached FE input");
            boolean rejected = false;
            try { block.setOwner(UUID.randomUUID()); }
            catch (IllegalStateException expected) { rejected = true; }
            check(rejected && owner.equals(block.owner()), "A nested owner change must be rejected before it can redirect credit");
            return false;
        };
        check(block.recycleChemical(CHEMICAL, Long.MAX_VALUE, true, reentrant) == 7
                        && block.isChemicalValid(CHEMICAL, reentrant) && calls.get() == 2,
                "The predicate must execute for real valid quotes while nested resources remain closed");
        check(before.equals(save(helper, block)) && ledger(helper).pending(owner).isZero()
                        && !knows(helper, owner, CHEMICAL),
                "Simulated chemistry and recursive rejected resources must change no persisted state, credit or knowledge");
        check(block.recycleChemical(CHEMICAL, 7, false, reentrant) == 7 && calls.get() == 3
                        && block.pendingCredit().equals(EmcAmount.of(BigInteger.valueOf(7))) && knows(helper, owner, CHEMICAL),
                "Actual acceptance must confirm seven EMC and learn the chemical exactly once for the bound owner");
        check(source.getCount() == 3 && water.getAmount() == 5
                        && !save(helper, block).contains("buffer"),
                "Rejected nested resources must retain caller-owned inputs and create no item buffer");
    }

    private static void rejectsFailedTagLookupWithoutSpendingBudget(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        check(block.recycleChemical(CHEMICAL, 7, false, tag -> {
            throw new IllegalStateException("deliberate unavailable tag lookup");
        }) == 0 && !block.isChemicalValid(CHEMICAL, tag -> {
            throw new NoClassDefFoundError("deliberate incompatible tag lookup");
        }), "External runtime and linkage failures must reject chemistry without escaping into the source transfer");
        check(block.pendingCredit().isZero() && ledger(helper).pending(owner).isZero() && !knows(helper, owner, CHEMICAL)
                        && block.recycleChemical(CHEMICAL, 7, false, NOT_IN_TAG) == 7,
                "A failed tag callback must create no credit or knowledge and must leave the full budget for a fresh quote");
    }

    private static void rejectsQuoteAfterLoadEvenWithUnchangedOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        CompoundTag before = save(helper, block);
        Predicate<ResourceLocation> loading = tag -> {
            block.loadWithComponents(before.copy(), helper.getLevel().registryAccess());
            return false;
        };
        check(block.recycleChemical(CHEMICAL, 7, false, loading) == 0
                        && !block.isChemicalValid(CHEMICAL, loading) && owner.equals(block.owner()),
                "An in-place NBT load must invalidate the outstanding quote even when its owner UUID remains the same");
        check(before.equals(save(helper, block)) && ledger(helper).pending(owner).isZero() && !knows(helper, owner, CHEMICAL),
                "Invalidated quotes must create no pending EMC, ledger credit or chemical knowledge");
        check(block.recycleChemical(CHEMICAL, 7, false, NOT_IN_TAG) == 7,
                "After loading finishes a fresh quote must still access the complete unspent budget");
    }

    private static void rejectsQuoteAfterRemovalAndRejectsAllStalePorts(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        IItemHandler items = block.itemHandler();
        IFluidHandler fluids = block.fluidHandler();
        IEnergyStorage energy = block.energyHandler();
        AtomicInteger calls = new AtomicInteger();
        long accepted = block.recycleChemical(CHEMICAL, 7, false, tag -> {
            calls.incrementAndGet();
            helper.setBlock(POSITION, Blocks.AIR);
            return false;
        });
        check(calls.get() == 1 && accepted == 0 && block.isRemoved(),
                "A callback removing the real block must invalidate its quote before any amount is accepted");
        check(!block.isChemicalValid(CHEMICAL, NOT_IN_TAG)
                        && block.recycleChemical(CHEMICAL, 7, true, NOT_IN_TAG) == 0
                        && block.recycleChemical(CHEMICAL, 7, false, NOT_IN_TAG) == 0
                        && items.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1
                        && fluids.fill(new FluidStack(Fluids.WATER, 5), IFluidHandler.FluidAction.EXECUTE) == 0
                        && energy.receiveEnergy(5, false) == 0,
                "Every cached port and the common chemical bridge must reject a removed block entity");
        check(block.pendingCredit().isZero() && ledger(helper).pending(owner).isZero() && !knows(helper, owner, CHEMICAL),
                "An input rejected after native removal must neither create credit nor teach its ID");
    }

    private static void rejectsWorkerThreadWithoutCallingExternalPredicate(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        CompoundTag before = save(helper, block);
        AtomicBoolean predicateCalled = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                Predicate<ResourceLocation> predicate = tag -> { predicateCalled.set(true); return false; };
                check(!block.canRecycleResources() && !block.isChemicalValid(CHEMICAL, predicate)
                                && block.recycleChemical(CHEMICAL, 7, true, predicate) == 0
                                && block.recycleChemical(CHEMICAL, 7, false, predicate) == 0,
                        "Off-thread chemical queries must reject simulation and execution");
            } catch (Throwable caught) { failure.set(caught); }
        }, "primordial-chemical-reception-test");
        worker.setDaemon(true);
        worker.start();
        try { worker.join(3000); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GameTestAssertException("Interrupted while checking off-thread chemical rejection");
        }
        check(!worker.isAlive() && failure.get() == null && !predicateCalled.get(),
                "Off-thread checks must finish without exceptions or invoking the external tag predicate");
        check(before.equals(save(helper, block)) && ledger(helper).pending(owner).isZero()
                        && !knows(helper, owner, CHEMICAL), "Off-thread inputs must leave no resource, credit or knowledge changes");
    }

    private static void clipsLongRequestsWithoutOverflowOrSimulationSideEffects(GameTestHelper helper) {
        NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.set(Integer.MAX_VALUE);
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        CompoundTag before = save(helper, block);
        check(block.recycleChemical(CHEMICAL, 0, false, NOT_IN_TAG) == 0
                        && block.recycleChemical(CHEMICAL, -1, false, NOT_IN_TAG) == 0
                        && block.recycleChemical(null, 1, false, NOT_IN_TAG) == 0,
                "Invalid IDs and nonpositive requests must reject before consuming a quote or budget");
        check(block.recycleChemical(CHEMICAL, Long.MAX_VALUE, true, NOT_IN_TAG) == Integer.MAX_VALUE
                        && block.recycleChemical(SECOND, Long.MAX_VALUE, true, NOT_IN_TAG) == Integer.MAX_VALUE
                        && before.equals(save(helper, block)) && !knows(helper, owner, CHEMICAL),
                "Repeated long-sized simulations must expose the whole int-sized budget without storing or learning anything");
        check(block.recycleChemical(CHEMICAL, Long.MAX_VALUE, false, NOT_IN_TAG) == Integer.MAX_VALUE
                        && block.recycleChemical(SECOND, Long.MAX_VALUE, false, NOT_IN_TAG) == 0
                        && block.pendingCredit().equals(EmcAmount.of(BigInteger.valueOf(Integer.MAX_VALUE))),
                "Long requests must clip before int accounting and credit exactly 2147483647 EMC without overflow");
    }

    private static NeoForgeBlackholeBlockEntity place(GameTestHelper helper, UUID owner) {
        helper.setBlock(POSITION, Blocks.AIR);
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        check(helper.getBlockEntity(POSITION) instanceof NeoForgeBlackholeBlockEntity, "Expected the registered blackhole block entity");
        NeoForgeBlackholeBlockEntity block = (NeoForgeBlackholeBlockEntity) helper.getBlockEntity(POSITION);
        block.setOwner(owner);
        return block;
    }

    private static CompoundTag save(GameTestHelper helper, NeoForgeBlackholeBlockEntity block) {
        return block.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    private static NeoForgeEmcSavedData ledger(GameTestHelper helper) {
        return NeoForgeEmcSavedData.get(helper.getLevel().getServer());
    }

    private static boolean knows(GameTestHelper helper, UUID owner, ResourceLocation id) {
        return NeoForgeRecoveryKnowledge.knowsChemical(helper.getLevel().getServer(), owner, id);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class ConfigSnapshot implements AutoCloseable {
        private final boolean gasSale = NeoForgeServerConfig.GAS_SALE_ENABLED.get();
        private final int maxGas = NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.get();
        private final boolean fluidSale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        private final List<? extends String> gasBlacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        private final List<? extends String> fluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        private final List<? extends String> resourceBlacklist = List.copyOf(NeoForgeServerConfig.RESOURCE_BLACKLIST.get());

        @Override
        public void close() {
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(gasSale);
            NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.set(maxGas);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(fluidSale);
            NeoForgeServerConfig.GAS_BLACKLIST.set(gasBlacklist);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(fluidBlacklist);
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(resourceBlacklist);
        }
    }
}
