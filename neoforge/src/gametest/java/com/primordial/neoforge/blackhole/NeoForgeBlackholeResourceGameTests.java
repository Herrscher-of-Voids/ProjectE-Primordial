/**
 * 【文件职责】通过正式 NeoForge 方块能力验收黑洞物品、流体与 FE 的真实资源回收边界。
 * 【变更记录 #1】2026-10-22 21:40:39（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：全方向能力、模拟无副作用、预算刷新、64 堆容量、原生物品报价、精确余数、禁用及移除测试。
 * - 修改：使用真实 ProjectE 售价、世界账本和正式能力入口；配置在 finally 恢复，跨 tick 的配置测试单独分批。
 * - 用途：锁定实际接收量、源资源余量与到账收益一致，不把模拟或持久化回读当作新的资源交易。
 * 【变更记录 #2】2026-10-22 21:44:30（第 2 次备注 · 2.0.0-neo-dev 回收知识边界）
 * - 删除：无。
 * - 新增：模拟不学习物品或流体、实际回收才记录离线知识的账本断言。
 * - 修改：临时启用物品自动学习，并在测试结束后恢复原服务器配置。
 * - 用途：使用正式知识服务验证学习时点，不以黑洞内部字段代替世界知识状态。
 */
package com.primordial.neoforge.blackhole;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeBlackholeResourceGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeBlackholeResourceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void exposesNativeSinkCapabilitiesOnEverySideIncludingNull(GameTestHelper helper) {
        place(helper, POSITION, UUID.randomUUID());
        for (Direction side : Direction.values()) checkPorts(helper, side);
        checkPorts(helper, null);
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void simulatesWithoutChangingBuffersCreditBudgetsOrInputStacks(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.set(3);
            NeoForgeServerConfig.BLACKHOLE_MAX_FLUID_MB_PER_TICK.set(7);
            NeoForgeServerConfig.BLACKHOLE_MAX_ENERGY_PER_TICK.set(11);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of());
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(true);
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
            IItemHandler items = items(helper);
            IFluidHandler fluids = fluids(helper);
            IEnergyStorage energy = energy(helper);
            ItemStack source = new ItemStack(Items.DIAMOND, 5);
            ItemStack original = source.copy();
            FluidStack water = new FluidStack(Fluids.WATER, 20);
            CompoundTag before = save(helper, blackhole);
            for (int i = 0; i < 3; i++) {
                check(items.insertItem(0, source, true).getCount() == 2, "Repeated item simulation must leave a two-item remainder");
                check(fluids.fill(water, IFluidHandler.FluidAction.SIMULATE) == 7, "Fluid simulation must see the complete seven-mB budget");
                check(energy.receiveEnergy(100, true) == 11, "FE simulation must see the complete eleven-FE budget");
            }
            check(before.equals(save(helper, blackhole)) && ledger(helper).pending(owner).isZero(),
                    "Simulation must not change persisted buffer, pending credit or world account");
            NeoForgeRecoveryKnowledgeSavedData knowledge = NeoForgeRecoveryKnowledgeSavedData.get(helper.getLevel().getServer());
            ResourceLocation waterId = ResourceLocation.fromNamespaceAndPath("minecraft", "water");
            check(knowledge.pendingItemCount(owner) == 0 && !knowledge.knowsFluid(owner, waterId),
                    "Simulated inputs must not teach items or fluids to an offline owner");
            check(source.getCount() == original.getCount() && ItemStack.isSameItemSameComponents(source, original)
                    && water.getAmount() == 20, "Simulation must not mutate caller-owned resource stacks");
            check(items.insertItem(0, source, false).getCount() == 2
                    && items.insertItem(0, source, false).getCount() == 5,
                    "Execution must accept exactly three items once, proving simulations consumed no budget");
            check(fluids.fill(water, IFluidHandler.FluidAction.EXECUTE) == 7
                    && fluids.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0,
                    "Fluid execution must consume exactly its simulated budget");
            check(energy.receiveEnergy(100, false) == 11 && energy.receiveEnergy(100, false) == 0,
                    "FE execution must consume exactly its simulated budget");
            tick(helper, blackhole);
            EmcAmount expected = NeoForgeRecoveryPrices.item(source).multiply(3)
                    .add(NeoForgeRecoveryPrices.fluid(water).multiply(7))
                    .add(NeoForgeRecoveryPrices.energy().multiply(11));
            check(ledger(helper).pending(owner).equals(expected), "Only the actually accepted three resources may earn EMC");
            check(knowledge.pendingItemCount(owner) == 1 && knowledge.knowsFluid(owner, waterId),
                    "Actual item settlement and fluid acceptance must record exactly one offline knowledge entry each");
            check(source.getCount() == 5 && water.getAmount() == 20, "Execution must report acceptance without mutating the caller's input");
        }
        helper.succeed();
    }

    @GameTest(batch = "blackhole_resource_budget", template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void sharesItemBudgetAcrossCallsAndRefreshesItOnNextWorldTick(GameTestHelper helper) {
        ConfigSnapshot config = new ConfigSnapshot();
        try {
            NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.set(2);
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
            IItemHandler handler = items(helper);
            ItemStack source = new ItemStack(Items.DIAMOND, 5);
            check(handler.insertItem(0, source, false).getCount() == 3, "First input must consume exactly two items");
            check(handler.insertItem(0, source, true).getCount() == 5
                    && handler.insertItem(0, source, false).getCount() == 5,
                    "Subsequent requests in the same world tick must share the exhausted budget");
            helper.runAfterDelay(2, () -> {
                try {
                    check(handler.insertItem(0, source, false).getCount() == 3, "A later world tick must make the two-item budget available again");
                    tick(helper, blackhole);
                    check(ledger(helper).pending(owner).equals(NeoForgeRecoveryPrices.item(source).multiply(4)),
                            "Two ticks of partial transfers must account for exactly four items");
                    helper.succeed();
                } finally {
                    config.close();
                }
            });
        } catch (RuntimeException | Error failure) {
            config.close();
            throw failure;
        }
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void simulatesTheRealSixtyFourStackBufferCapacityForUnstackableItems(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.set(Integer.MAX_VALUE);
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
            IItemHandler handler = items(helper);
            ItemStack oversized = new ItemStack(Items.DIAMOND_SWORD, 65);
            check(!NeoForgeRecoveryPrices.item(oversized).isZero(), "The real ProjectE map must price the chosen unstackable item");
            CompoundTag before = save(helper, blackhole);
            check(handler.insertItem(0, oversized, true).getCount() == 1,
                    "Simulation must account for the actual 64-stack capacity, not only the per-tick item budget");
            check(before.equals(save(helper, blackhole)), "Capacity simulation must not allocate persisted buffer entries");
            check(handler.insertItem(0, oversized, false).getCount() == 1, "Execution must match the simulated 64-item acceptance");
            ItemStack extra = new ItemStack(Items.DIAMOND_SWORD);
            check(handler.insertItem(0, extra, true).getCount() == 1
                    && handler.insertItem(0, extra, false).getCount() == 1,
                    "A full unstackable buffer must reject the next item during both simulation and execution");
            check(oversized.getCount() == 65, "An anomalous oversized stack must not be mutated by the sink");
            helper.setBlock(POSITION, Blocks.AIR);
            check(ledger(helper).pending(owner).equals(NeoForgeRecoveryPrices.item(extra).multiply(64)),
                    "Removing a full buffer must settle exactly the accepted 64 items");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void usesNativeSellPriceAndPreservesItemComponentsThroughBufferReload(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
        ItemStack source = new ItemStack(Items.DIAMOND, 3);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("component preservation probe"));
        ItemStack original = source.copy();
        long nativeQuote = IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromStack(source));
        check(nativeQuote > 0 && NeoForgeRecoveryPrices.item(source).equals(EmcAmount.of(BigInteger.valueOf(nativeQuote))),
                "Recovery must use the installed ProjectE sell value for the actual component-bearing stack");
        check(items(helper).insertItem(0, source, false).isEmpty(), "The component-bearing source must be accepted");
        check(source.getCount() == 3 && ItemStack.isSameItemSameComponents(source, original),
                "Input acceptance must not rewrite caller-owned components or item count");
        CompoundTag saved = save(helper, blackhole);
        blackhole.loadWithComponents(new CompoundTag(), helper.getLevel().registryAccess());
        blackhole.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(saved.equals(save(helper, blackhole)), "Target-version item component data must survive an in-place buffer save/load");
        tick(helper, blackhole);
        tick(helper, blackhole);
        check(ledger(helper).pending(owner).equals(EmcAmount.of(BigInteger.valueOf(nativeQuote)).multiply(3)),
                "Reloaded buffer items must settle at the native sell price exactly once");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void persistsSingleFeCreditAndCombinesItWithAnotherBlackhole(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of());
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity first = place(helper, POSITION, owner);
            IEnergyStorage firstPort = energy(helper);
            check(firstPort.receiveEnergy(1, true) == 1 && first.pendingCredit().isZero(), "Simulated one FE must not create a fractional credit");
            check(firstPort.receiveEnergy(1, false) == 1, "One FE must be accepted without integer rounding");
            CompoundTag saved = save(helper, first);
            first.loadWithComponents(new CompoundTag(), helper.getLevel().registryAccess());
            first.loadWithComponents(saved, helper.getLevel().registryAccess());
            check(first.pendingCredit().equals(new EmcAmount(BigInteger.ONE, BigInteger.valueOf(1000))),
                    "A single FE must survive block NBT as exactly one thousandth EMC");
            tick(helper, first);
            check(ledger(helper).pending(owner).equals(new EmcAmount(BigInteger.ONE, BigInteger.valueOf(1000))),
                    "The offline world ledger must keep the one-FE remainder");
            NeoForgeBlackholeBlockEntity second = place(helper, new BlockPos(2, 1, 1), owner);
            IEnergyStorage secondPort = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                    second.getBlockPos(), Direction.NORTH);
            check(secondPort != null && secondPort.receiveEnergy(999, false) == 999, "Another blackhole must accept the remaining 999 FE");
            tick(helper, second);
            check(ledger(helper).pending(owner).equals(EmcAmount.ONE), "The same owner's cross-blackhole FE fractions must combine to exactly one EMC");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rejectsUnpricedDisabledAndOwnerlessResourcesWithoutSpendingBudgets(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, UUID.randomUUID());
            IItemHandler itemPort = items(helper);
            IFluidHandler fluidPort = fluids(helper);
            IEnergyStorage energyPort = energy(helper);
            ItemStack unpriced = new ItemStack(Items.BARRIER, 7);
            check(NeoForgeRecoveryPrices.item(unpriced).isZero(), "Barrier must be unpriced in the installed native ProjectE map");
            check(itemPort.insertItem(0, unpriced, true).getCount() == 7
                    && itemPort.insertItem(0, unpriced, false).getCount() == 7, "Unpriced items must be left with the caller");
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:water"));
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of("forge:fe"));
            FluidStack water = new FluidStack(Fluids.WATER, 17);
            check(fluidPort.fill(water, IFluidHandler.FluidAction.SIMULATE) == 0
                    && fluidPort.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0,
                    "Blacklisted fluids must reject both simulated and actual transfer");
            check(energyPort.receiveEnergy(17, true) == 0 && energyPort.receiveEnergy(17, false) == 0,
                    "Blacklisted FE must reject both simulated and actual transfer");
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(false);
            check(fluidPort.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0,
                    "The fluid-sale switch must also stop the automation port");
            check(blackhole.pendingCredit().isZero(), "Rejected transfers must create no exact credit");
            blackhole.setOwner(null);
            check(itemPort.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1
                    && fluidPort.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0
                    && energyPort.receiveEnergy(17, false) == 0, "An ownerless sink must reject all resources");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rejectsTransfersThroughPreviouslyCachedHandlersAfterBlockRemoval(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
        IItemHandler itemPort = items(helper);
        IFluidHandler fluidPort = fluids(helper);
        IEnergyStorage energyPort = energy(helper);
        helper.setBlock(POSITION, Blocks.AIR);
        check(blackhole.isRemoved(), "The native replacement path must invalidate the old block entity");
        ItemStack source = new ItemStack(Items.DIAMOND, 5);
        check(itemPort.insertItem(0, source, true).getCount() == 5
                && itemPort.insertItem(0, source, false).getCount() == 5, "Cached removed item handlers must retain all input");
        FluidStack water = new FluidStack(Fluids.WATER, 13);
        check(fluidPort.fill(water, IFluidHandler.FluidAction.SIMULATE) == 0
                && fluidPort.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0, "Cached removed fluid handlers must accept nothing");
        check(energyPort.receiveEnergy(13, true) == 0 && energyPort.receiveEnergy(13, false) == 0,
                "Cached removed FE handlers must accept nothing");
        check(ledger(helper).pending(owner).isZero() && source.getCount() == 5 && water.getAmount() == 13,
                "Removed handlers must neither mutate source resources nor create credit");
        helper.succeed();
    }

    private static void checkPorts(GameTestHelper helper, Direction side) {
        BlockPos pos = helper.absolutePos(POSITION);
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        IFluidHandler fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        check(items != null && fluids != null && energy != null, "All three sink capabilities must be exposed on side " + side);
        check(items.getStackInSlot(0).isEmpty() && items.extractItem(0, 1, false).isEmpty(), "The item sink must expose no extractable stock");
        check(fluids.getFluidInTank(0).isEmpty() && fluids.drain(1, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "The fluid sink must expose no extractable stock");
        check(energy.getEnergyStored() == 0 && !energy.canExtract() && energy.extractEnergy(1, false) == 0,
                "The FE sink must expose no extractable energy");
    }

    private static IItemHandler items(GameTestHelper helper) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(POSITION), Direction.UP);
        check(handler != null, "Expected the actual registered item capability");
        return handler;
    }

    private static IFluidHandler fluids(GameTestHelper helper) {
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(POSITION), Direction.UP);
        check(handler != null, "Expected the actual registered fluid capability");
        return handler;
    }

    private static IEnergyStorage energy(GameTestHelper helper) {
        IEnergyStorage handler = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(POSITION), Direction.UP);
        check(handler != null, "Expected the actual registered FE capability");
        return handler;
    }

    private static NeoForgeBlackholeBlockEntity place(GameTestHelper helper, BlockPos position, UUID owner) {
        helper.setBlock(position, NeoForgeModRegistries.BLACKHOLE.get());
        check(helper.getBlockEntity(position) instanceof NeoForgeBlackholeBlockEntity, "Expected registered blackhole entity");
        NeoForgeBlackholeBlockEntity blackhole = (NeoForgeBlackholeBlockEntity) helper.getBlockEntity(position);
        blackhole.setOwner(owner);
        return blackhole;
    }

    private static NeoForgeEmcSavedData ledger(GameTestHelper helper) {
        return NeoForgeEmcSavedData.get(helper.getLevel().getServer());
    }

    private static CompoundTag save(GameTestHelper helper, NeoForgeBlackholeBlockEntity blackhole) {
        return blackhole.saveWithFullMetadata(helper.getLevel().registryAccess());
    }

    private static void tick(GameTestHelper helper, NeoForgeBlackholeBlockEntity blackhole) {
        NeoForgeBlackholeBlockEntity.serverTick(helper.getLevel(), blackhole.getBlockPos(), blackhole.getBlockState(), blackhole);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class ConfigSnapshot implements AutoCloseable {
        private final int items = NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.get();
        private final int fluids = NeoForgeServerConfig.BLACKHOLE_MAX_FLUID_MB_PER_TICK.get();
        private final int energy = NeoForgeServerConfig.BLACKHOLE_MAX_ENERGY_PER_TICK.get();
        private final String ratio = NeoForgeServerConfig.FE_PER_EMC.get();
        private final boolean fluidSale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        private final boolean autoLearn = NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.get();
        private final List<? extends String> fluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        private final List<? extends String> resourceBlacklist = List.copyOf(NeoForgeServerConfig.RESOURCE_BLACKLIST.get());

        @Override
        public void close() {
            NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.set(items);
            NeoForgeServerConfig.BLACKHOLE_MAX_FLUID_MB_PER_TICK.set(fluids);
            NeoForgeServerConfig.BLACKHOLE_MAX_ENERGY_PER_TICK.set(energy);
            NeoForgeServerConfig.FE_PER_EMC.set(ratio);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(fluidSale);
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(autoLearn);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(fluidBlacklist);
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(resourceBlacklist);
        }
    }
}
