/**
 * 【文件职责】验证 Immersive Engineering、Ender IO 和 GregTech Modern 专用适配器的 Forge capability 行为。
 * - 核心输入：Mockito 方块实体、Forge 物品/流体/能源 capability 和边界数量。
 * - 核心输出：三种适配器的身份、方向访问、单槽视图、模拟/执行语义和异常回退结果。
 * - 调用链：测试替身 → 专用适配器 → Forge capability → SingleSlotHandler/SingleTankView。
 *
 * 【变更记录 #1】2026-09-15 22:09:32（第 1 次备注 · 1.0.0-stage5-adapters2）
 * - 删除：无；不引入第三方模组内部类的伪造实现。
 * - 新增：三个适配器的统一能力访问、单罐边界、能源模拟和异常隔离测试。
 * - 修改：测试使用真实 Forge ItemStackHandler、FluidTank 和 EnergyStorage，避免只验证常量或同实现伪方法。
 * - 用途：在可选模组未启动的纯 JUnit 环境中验证公共 capability 契约；通过不代表所有附属模组的实机联调。
 */
/**
 * 【变更记录 #2】2026-09-15 22:17:42（第 2 次备注 · 1.0.0-stage5-adapters2）
 * - 删除：无。
 * - 新增：同一测试方块的不同方向端点 key 不相同的断言。
 * - 修改：继续复用真实 Forge capability 测试对象，覆盖新增方向身份规则。
 * - 用途：防止后续重构移除方向段后再次允许跨面复用配置。
 */
package com.primordial.machine;

import com.primordial.machine.adapters.EnderIOProxy;
import com.primordial.machine.adapters.GregTechModernProxy;
import com.primordial.machine.adapters.ImmersiveEngineeringProxy;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 三个适配器共享 Forge capability，但各自注册独立 modId 和类包匹配。 */
class SecondaryAdaptersTest {
    @BeforeAll
    static void bootstrap() {
        AdapterTestBootstrap.initialize();
    }

    private BlockEntity machine(ItemStackHandler items, IFluidHandler fluids, EnergyStorage energy) {
        BlockEntity machine = mock(BlockEntity.class);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH))
                .thenReturn(LazyOptional.of(() -> items));
        when(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH))
                .thenReturn(LazyOptional.of(() -> fluids));
        when(machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH))
                .thenReturn(LazyOptional.of(() -> energy));
        return machine;
    }

    @Test
    void adaptersExposeStableDistinctIdentity() {
        assertEquals("immersiveengineering", ImmersiveEngineeringProxy.INSTANCE.id());
        assertEquals("enderio", EnderIOProxy.INSTANCE.id());
        assertEquals("gtceu", GregTechModernProxy.INSTANCE.id());
        var machine = machine(new ItemStackHandler(1), new FluidTank(1000), new EnergyStorage(1000));
        String ie = ImmersiveEngineeringProxy.INSTANCE.endpointKey(machine, Direction.NORTH);
        String ender = EnderIOProxy.INSTANCE.endpointKey(machine, Direction.NORTH);
        String gt = GregTechModernProxy.INSTANCE.endpointKey(machine, Direction.NORTH);
        assertNotEquals(ie, ender);
        assertNotEquals(ender, gt);
        assertTrue(ie.contains("class/" + machine.getClass().getName()));
        assertNotEquals(ie, ImmersiveEngineeringProxy.INSTANCE.endpointKey(machine, Direction.SOUTH));
    }

    @Test
    void itemAndEnergyCapabilitiesKeepSelectedSideAndSimulation() {
        var items = new ItemStackHandler(1);
        var energy = new EnergyStorage(1000);
        var machine = machine(items, new FluidTank(1000), energy);
        for (var proxy : new com.primordial.machine.MachineProxy[]{
                ImmersiveEngineeringProxy.INSTANCE, EnderIOProxy.INSTANCE, GregTechModernProxy.INSTANCE}) {
            var handler = proxy.itemHandler(machine, Direction.NORTH);
            assertNotNull(handler);
            assertEquals(1, handler.getSlots());
            assertTrue(proxy.targetItemHandler(machine, Direction.NORTH, handler, 0)
                    .insertItem(0, new ItemStack(Items.IRON_INGOT, 2), true).isEmpty());
            assertTrue(items.getStackInSlot(0).isEmpty());
            var power = proxy.energyHandler(machine, Direction.NORTH);
            assertNotNull(power);
            assertEquals(100, power.receiveEnergy(100, true));
            assertEquals(0, power.getEnergyStored());
            assertEquals(100, power.receiveEnergy(100, false));
            assertEquals(100, power.getEnergyStored());
            energy.extractEnergy(100, false);
        }
    }

    @Test
    void singleFluidTankViewPreservesFluidAndRejectsAggregatedTanks() {
        var tank = new FluidTank(1000);
        var machine = machine(new ItemStackHandler(1), tank, new EnergyStorage(1000));
        for (var proxy : new com.primordial.machine.MachineProxy[]{
                ImmersiveEngineeringProxy.INSTANCE, EnderIOProxy.INSTANCE, GregTechModernProxy.INSTANCE}) {
            var handler = proxy.fluidHandler(machine, Direction.NORTH);
            var target = proxy.targetFluidHandler(machine, Direction.NORTH, handler, 0);
            assertNotNull(target);
            assertEquals(500, target.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE));
            assertEquals(500, target.drain(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE).getAmount());
        }
        IFluidHandler aggregate = mock(IFluidHandler.class);
        when(aggregate.getTanks()).thenReturn(2);
        assertNull(ImmersiveEngineeringProxy.INSTANCE.targetFluidHandler(machine, Direction.NORTH, aggregate, 0));
        assertNull(EnderIOProxy.INSTANCE.targetFluidHandler(machine, Direction.NORTH, aggregate, 1));
        assertNull(GregTechModernProxy.INSTANCE.targetFluidHandler(machine, Direction.NORTH, aggregate, -1));
    }

    @Test
    void capabilityFailuresReturnNullInsteadOfBreakingTaskExecution() {
        BlockEntity machine = mock(BlockEntity.class);
        when(machine.isRemoved()).thenReturn(false);
        when(machine.getCapability(eq(ForgeCapabilities.ITEM_HANDLER), eq(Direction.NORTH)))
                .thenThrow(new IllegalStateException("capability unavailable"));
        when(machine.getCapability(eq(ForgeCapabilities.FLUID_HANDLER), eq(Direction.NORTH)))
                .thenThrow(new IllegalStateException("capability unavailable"));
        when(machine.getCapability(eq(ForgeCapabilities.ENERGY), eq(Direction.NORTH)))
                .thenThrow(new IllegalStateException("capability unavailable"));
        for (var proxy : new com.primordial.machine.MachineProxy[]{
                ImmersiveEngineeringProxy.INSTANCE, EnderIOProxy.INSTANCE, GregTechModernProxy.INSTANCE}) {
            assertNull(proxy.itemHandler(machine, Direction.NORTH));
            assertNull(proxy.fluidHandler(machine, Direction.NORTH));
            assertNull(proxy.energyHandler(machine, Direction.NORTH));
        }
    }
}
