/**
 * 【文件职责】通过生产能力代理验收测试方块实体的方向、资源转移、主动失效和存档生命周期。
 * GameTest 只操作 NeoForgeMachineTestFixture，不代表生产机器已完成移植或游戏验收。
 *
 * 【变更记录 #1】2026-10-22 10:53:18（第 1 次备注 · 2.0.0-neo-dev 机器平台验收）
 * - 删除：无。
 * - 新增：四项独立 GameTest，覆盖三资源模拟/执行、容量余量、端口开关缓存失效、ticker 和 NBT 重载。
 * - 修改：无生产代码；所有资源接口均由 NeoForgeCapabilityProxy 查询，失效回调仅记录通知。
 * - 用途：验证目标版平台契约，缓存重新查询延至下一 tick，不假定失效会撤销已取得的 handler 引用。
 *
 * 【变更记录 #2】2026-10-22 11:00:12（第 2 次备注 · 2.0.0-neo-dev 机器平台验收）
 * - 删除：无。
 * - 新增：记录 1.21.1 ItemStackHandler 默认槽上限为 99 的容量断言。
 * - 修改：物品传输测试改用目标版绝对堆叠上限，避免把 1.20.1 常见的 64 写成跨版本契约。
 * - 用途：让测试精确反映 NeoForge 21.1.264 的实际资源接口行为。
 *
 * 【变更记录 #3】2026-10-22 11:02:18（第 3 次备注 · 2.0.0-neo-dev 机器平台验收）
 * - 删除：无。
 * - 新增：区分槽位绝对上限 99 与石头自身堆叠上限 64 的物品容量断言。
 * - 修改：物品传输序列恢复为单堆叠实际可达到的 64，并保留超过堆叠上限的剩余量检查。
 * - 用途：避免将 ItemStackHandler 槽位上限误当成具体物品的可堆叠数量。
 */
package com.primordial.neoforge.machine;

import java.util.concurrent.atomic.AtomicInteger;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.machine.NeoForgeMachineTestFixture.FixtureBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeMachineLifecycleGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeMachineLifecycleGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void respectsPortSidesAndRejectsInvalidBlockEntities(GameTestHelper helper) {
        FixtureBlockEntity be = placeFixture(helper);
        for (Direction side : Direction.values()) {
            check((NeoForgeCapabilityProxy.INSTANCE.itemHandler(be, side) != null) == (side == Direction.NORTH),
                    "Only the north side may expose the fixture item port");
            check((NeoForgeCapabilityProxy.INSTANCE.fluidHandler(be, side) != null) == (side == Direction.SOUTH),
                    "Only the south side may expose the fixture fluid port");
            check(NeoForgeCapabilityProxy.INSTANCE.energyHandler(be, side) == null,
                    "The fixture energy port must reject physical sides");
        }
        check(NeoForgeCapabilityProxy.INSTANCE.itemHandler(be, null) == null,
                "Item queries must reject a null side");
        check(NeoForgeCapabilityProxy.INSTANCE.fluidHandler(be, null) == null,
                "Fluid queries must reject a null side");
        require(NeoForgeCapabilityProxy.INSTANCE.energyHandler(be, null), "Expected the internal energy port");

        assertNoPorts(null);
        FixtureBlockEntity detached = new FixtureBlockEntity(POSITION,
                NeoForgeMachineTestFixture.BLOCK.defaultBlockState());
        assertNoPorts(detached);
        helper.setBlock(POSITION, Blocks.AIR);
        check(be.isRemoved(), "Replacing the fixture must remove its block entity");
        assertNoPorts(be);
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void honorsSimulatedTransfersAndCapacityRemainders(GameTestHelper helper) {
        FixtureBlockEntity be = placeFixture(helper);
        IItemHandler items = itemPort(be);
        check(items.getSlots() == 1 && items.getSlotLimit(0) == 99,
                "Expected one 99-item slot, got slots=" + items.getSlots() + " limit=" + items.getSlotLimit(0));
        check(items.insertItem(0, new ItemStack(Items.STONE, 16), true).isEmpty(),
                "Simulated item insertion must accept 16 items");
        assertItems(items, 0);
        check(items.insertItem(0, new ItemStack(Items.STONE, 16), false).isEmpty(),
                "Actual item insertion must accept 16 items");
        assertItems(items, 16);
        check(items.insertItem(0, new ItemStack(Items.STONE, 64), false).getCount() == 16,
                "Actual insertion must return 16 items beyond the stone stack limit");
        assertItems(items, 64);
        check(items.insertItem(0, new ItemStack(Items.STONE, 64), true).getCount() == 64,
                "Simulated insertion into a full stone stack must return all 64 items");
        assertItems(items, 64);
        check(items.insertItem(0, new ItemStack(Items.STONE, 64), false).getCount() == 64,
                "Actual insertion into a full stone stack must return all 64 items");
        assertItems(items, 64);
        check(items.extractItem(0, 5, true).getCount() == 5, "Simulated extraction must return 5 items");
        assertItems(items, 64);
        check(items.extractItem(0, 5, false).getCount() == 5, "Actual extraction must return 5 items");
        assertItems(items, 59);

        IFluidHandler fluids = fluidPort(be);
        check(fluids.getTanks() == 1 && fluids.getTankCapacity(0) == 1000,
                "Expected one 1000 mB fluid tank");
        check(fluids.fill(new FluidStack(Fluids.WATER, 500), FluidAction.SIMULATE) == 500,
                "Simulated filling must accept 500 mB");
        assertFluid(fluids, 0);
        check(fluids.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE) == 500,
                "Actual filling must accept 500 mB");
        check(fluids.fill(new FluidStack(Fluids.WATER, 750), FluidAction.SIMULATE) == 500,
                "Simulated filling must respect the remaining 500 mB capacity");
        assertFluid(fluids, 500);
        check(fluids.fill(new FluidStack(Fluids.WATER, 750), FluidAction.EXECUTE) == 500,
                "Actual filling must accept only the remaining 500 mB");
        assertFluid(fluids, 1000);
        assertWater(fluids.drain(175, FluidAction.SIMULATE), 175);
        assertFluid(fluids, 1000);
        assertWater(fluids.drain(175, FluidAction.EXECUTE), 175);
        assertFluid(fluids, 825);

        IEnergyStorage energy = energyPort(be);
        check(energy.getMaxEnergyStored() == 1000, "Expected a 1000 FE energy buffer");
        check(energy.receiveEnergy(500, true) == 500, "Simulated receive must accept 500 FE");
        check(energy.getEnergyStored() == 0, "Simulated receive must preserve stored FE");
        check(energy.receiveEnergy(500, false) == 500, "Actual receive must accept 500 FE");
        check(energy.receiveEnergy(750, true) == 500, "Simulated receive must respect remaining capacity");
        check(energy.getEnergyStored() == 500, "Simulated capacity query must preserve stored FE");
        check(energy.receiveEnergy(750, false) == 500, "Actual receive must accept only remaining capacity");
        check(energy.extractEnergy(175, true) == 175, "Simulated extraction must return 175 FE");
        check(energy.getEnergyStored() == 1000, "Simulated extraction must preserve stored FE");
        check(energy.extractEnergy(175, false) == 175 && energy.getEnergyStored() == 825,
                "Actual extraction must leave exactly 825 FE");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void invalidatesAllCachesWhenPortsToggleAndPreservesContents(GameTestHelper helper) {
        FixtureBlockEntity be = placeFixture(helper);
        seedContents(be);
        AtomicInteger itemInvalidations = new AtomicInteger();
        AtomicInteger fluidInvalidations = new AtomicInteger();
        AtomicInteger energyInvalidations = new AtomicInteger();
        BlockPos absolutePos = helper.absolutePos(POSITION);
        var itemCache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, helper.getLevel(),
                absolutePos, Direction.NORTH, () -> true, itemInvalidations::incrementAndGet);
        var fluidCache = BlockCapabilityCache.create(Capabilities.FluidHandler.BLOCK, helper.getLevel(),
                absolutePos, Direction.SOUTH, () -> true, fluidInvalidations::incrementAndGet);
        var energyCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, helper.getLevel(),
                absolutePos, null, () -> true, energyInvalidations::incrementAndGet);
        require(itemCache.getCapability(), "Expected the item cache to resolve");
        require(fluidCache.getCapability(), "Expected the fluid cache to resolve");
        require(energyCache.getCapability(), "Expected the energy cache to resolve");

        be.setPortsEnabled(false);
        assertInvalidations(itemInvalidations, fluidInvalidations, energyInvalidations, 1);
        helper.runAfterDelay(1, () -> {
            check(itemCache.getCapability() == null && fluidCache.getCapability() == null
                    && energyCache.getCapability() == null, "Disabled ports must resolve no cached capabilities");
            assertNoPorts(be);
            be.setPortsEnabled(true);
            assertInvalidations(itemInvalidations, fluidInvalidations, energyInvalidations, 2);
            helper.runAfterDelay(1, () -> {
                assertItems(require(itemCache.getCapability(), "Expected the item cache to recover"), 16);
                assertFluid(require(fluidCache.getCapability(), "Expected the fluid cache to recover"), 500);
                check(require(energyCache.getCapability(), "Expected the energy cache to recover")
                        .getEnergyStored() == 500, "Re-enabling ports must preserve 500 FE");
                check(be.portsEnabled(), "Ports must be enabled again");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void savesResourcesDisabledPortsAndServerTicksAcrossReload(GameTestHelper helper) {
        FixtureBlockEntity original = placeFixture(helper);
        seedContents(original);
        helper.runAfterDelay(3, () -> {
            int savedTicks = original.serverTicks();
            check(savedTicks > 0, "The fixture server ticker must run before saving");
            original.setPortsEnabled(false);
            CompoundTag saved = original.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.setBlock(POSITION, Blocks.AIR);
            FixtureBlockEntity restored = placeFixture(helper);
            check(original != restored && original.isRemoved(), "Reload must use a new block entity");
            restored.loadWithComponents(saved, helper.getLevel().registryAccess());
            check(!restored.portsEnabled(), "Reload must preserve the disabled port state");
            check(restored.serverTicks() == savedTicks, "Reload must preserve the saved server tick count");
            assertNoPorts(restored);
            restored.setPortsEnabled(true);
            assertItems(itemPort(restored), 16);
            assertFluid(fluidPort(restored), 500);
            check(energyPort(restored).getEnergyStored() == 500, "Reload must preserve exactly 500 FE");
            helper.runAfterDelay(2, () -> {
                check(restored.serverTicks() > savedTicks, "The server ticker must resume after reload");
                helper.succeed();
            });
        });
    }

    private static FixtureBlockEntity placeFixture(GameTestHelper helper) {
        helper.setBlock(POSITION, NeoForgeMachineTestFixture.BLOCK);
        return require(helper.getBlockEntity(POSITION), "Expected the registered fixture block entity");
    }

    private static void seedContents(FixtureBlockEntity be) {
        check(itemPort(be).insertItem(0, new ItemStack(Items.STONE, 16), false).isEmpty(),
                "Fixture must accept the saved 16 stone items");
        check(fluidPort(be).fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE) == 500,
                "Fixture must accept the saved 500 mB water");
        check(energyPort(be).receiveEnergy(500, false) == 500, "Fixture must accept the saved 500 FE");
    }

    private static IItemHandler itemPort(FixtureBlockEntity be) {
        return require(NeoForgeCapabilityProxy.INSTANCE.itemHandler(be, Direction.NORTH), "Missing item port");
    }

    private static IFluidHandler fluidPort(FixtureBlockEntity be) {
        return require(NeoForgeCapabilityProxy.INSTANCE.fluidHandler(be, Direction.SOUTH), "Missing fluid port");
    }

    private static IEnergyStorage energyPort(FixtureBlockEntity be) {
        return require(NeoForgeCapabilityProxy.INSTANCE.energyHandler(be, null), "Missing internal energy port");
    }

    private static void assertNoPorts(FixtureBlockEntity be) {
        check(NeoForgeCapabilityProxy.INSTANCE.itemHandler(be, Direction.NORTH) == null
                && NeoForgeCapabilityProxy.INSTANCE.fluidHandler(be, Direction.SOUTH) == null
                && NeoForgeCapabilityProxy.INSTANCE.energyHandler(be, null) == null,
                "Unavailable block entities must expose no queried ports");
    }

    private static void assertItems(IItemHandler items, int count) {
        ItemStack stack = items.getStackInSlot(0);
        check(stack.getCount() == count && (count == 0 || stack.is(Items.STONE)),
                "Expected exactly " + count + " stone items in slot zero");
    }

    private static void assertFluid(IFluidHandler fluids, int amount) {
        FluidStack stack = fluids.getFluidInTank(0);
        if (amount == 0) check(stack.isEmpty(), "Expected an empty fluid tank");
        else assertWater(stack, amount);
    }

    private static void assertWater(FluidStack stack, int amount) {
        check(stack.is(Fluids.WATER) && stack.getAmount() == amount,
                "Expected exactly " + amount + " mB of water");
    }

    private static void assertInvalidations(AtomicInteger items, AtomicInteger fluids,
            AtomicInteger energy, int expected) {
        check(items.get() == expected && fluids.get() == expected && energy.get() == expected,
                "Each resource cache must receive exactly " + expected + " invalidation notifications");
    }

    private static <T> T require(T value, String message) {
        check(value != null, message);
        return value;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
