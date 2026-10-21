/**
 * 【文件职责】以真实 Mekanism ProxyFluidHandler 检验专用流体适配。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增行为测试。
 * - 新增：按罐模拟/执行、返回剩余量语义、关闭面的读写权限和同尺寸槽列表重排用例。
 * - 修改：世界/侧面容器通过测试替身提供，ProxyFluidHandler 与本模组适配器执行真实分支。
 * - 用途：防止绕过 Mekanism 自动化权限或把旧索引误指向其它全局槽。
 */
package com.primordial.machine;

import com.primordial.machine.adapters.MekanismProxy;
import com.primordial.data.MachineTarget;
import mekanism.api.Action;
import mekanism.api.fluid.ISidedFluidHandler;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.IHolder;
import mekanism.common.capabilities.proxy.ProxyFluidHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * 【变更记录 #2】2026-09-15 16:48:00（第 2 次备注 · 1.0.0-stage5-adapters）
 * - 删除：无。
 * - 新增：原生 IMekanismInventory/IMekanismFluidHandler 的侧面委托，以及 STRICT_ENERGY 转 FE 的回归。
 * - 修改：测试覆盖固定罐索引、模拟 Action 和 J→FE 的取整边界。
 * - 用途：证明专用代理不会退回聚合能力或把能量单位重复换算。
 */
/** 使用真实 Mekanism ProxyFluidHandler 检查指定罐、剩余量语义以及只读/关闭面的自动化权限。 */
class MekanismAdapterTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void nativeTankSimulationAndExecuteRespectSideRestrictions() {
        FluidTank[] tanks = {new FluidTank(1000), new FluidTank(1000)};
        ISidedFluidHandler nativeHandler = mock(ISidedFluidHandler.class);
        IHolder permissions = mock(IHolder.class);
        when(permissions.canInsert(Direction.NORTH)).thenReturn(true);
        when(permissions.canExtract(Direction.NORTH)).thenReturn(true);
        when(nativeHandler.getTanks(Direction.NORTH)).thenReturn(2);
        when(nativeHandler.getFluidInTank(anyInt(), eq(Direction.NORTH)))
                .thenAnswer(call -> tanks[(int) call.getArgument(0)].getFluid());
        when(nativeHandler.insertFluid(anyInt(), any(FluidStack.class), eq(Direction.NORTH), any(Action.class)))
                .thenAnswer(call -> {
                    FluidStack input = call.getArgument(1);
                    FluidStack rest = input.copy();
                    rest.shrink(tanks[(int) call.getArgument(0)].fill(input,
                            call.getArgument(3) == Action.EXECUTE ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE));
                    return rest;
                });
        when(nativeHandler.extractFluid(anyInt(), anyInt(), eq(Direction.NORTH), any(Action.class)))
                .thenAnswer(call -> tanks[(int) call.getArgument(0)].drain((int) call.getArgument(1),
                        call.getArgument(3) == Action.EXECUTE ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE));
        var proxy = new ProxyFluidHandler(nativeHandler, Direction.NORTH, permissions);
        var view = MekanismProxy.INSTANCE.targetFluidHandler(mock(BlockEntity.class), Direction.NORTH, proxy, 1);
        var water = new FluidStack(Fluids.WATER, 1400);
        assertEquals(1000, view.fill(water, IFluidHandler.FluidAction.SIMULATE));
        assertEquals(0, tanks[1].getFluidAmount());
        assertEquals(1000, view.fill(water, IFluidHandler.FluidAction.EXECUTE));
        assertEquals(0, tanks[0].getFluidAmount());
        assertEquals(1000, tanks[1].getFluidAmount());
        assertEquals(400, view.drain(new FluidStack(Fluids.WATER, 400), IFluidHandler.FluidAction.EXECUTE).getAmount());
        when(permissions.canInsert(Direction.NORTH)).thenReturn(false);
        assertEquals(0, view.fill(water, IFluidHandler.FluidAction.EXECUTE));
        when(permissions.canExtract(Direction.NORTH)).thenReturn(false);
        assertTrue(view.drain(400, IFluidHandler.FluidAction.EXECUTE).isEmpty());
        assertNull(MekanismProxy.INSTANCE.fluidHandler(mock(BlockEntity.class), null));
    }

    @Test void sameSizeSideReorderDoesNotRedirectBoundItemSlot() {
        BlockEntity machine = mock(BlockEntity.class, withSettings().extraInterfaces(IMekanismInventory.class));
        IMekanismInventory nativeInv = (IMekanismInventory) machine;
        var a = mock(IInventorySlot.class); var b = mock(IInventorySlot.class);
        var exposed = new ItemStackHandler(2);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)).thenReturn(LazyOptional.of(() -> exposed));
        when(nativeInv.getInventorySlots(null)).thenReturn(List.of(a, b));
        when(nativeInv.getInventorySlots(Direction.NORTH)).thenReturn(List.of(a, b));
        String identity = MekanismProxy.INSTANCE.itemSlotKey(machine, Direction.NORTH, exposed, 0);
        var target = new MachineTarget(2, 0, identity);
        assertNotNull(MachineTargetAccess.items(MekanismProxy.INSTANCE, machine, target));
        when(nativeInv.getInventorySlots(Direction.NORTH)).thenReturn(List.of(b, a));
        assertNull(MachineTargetAccess.items(MekanismProxy.INSTANCE, machine, target));
    }

    @Test void nativeInventoryHandlerKeepsTheSelectedSideAndSlot() {
        BlockEntity machine = mock(BlockEntity.class, withSettings().extraInterfaces(IMekanismInventory.class));
        IMekanismInventory inventory = (IMekanismInventory) machine;
        var slot = mock(IInventorySlot.class);
        when(inventory.getInventorySlots(Direction.SOUTH)).thenReturn(List.of(slot));
        when(inventory.getStackInSlot(0, Direction.SOUTH)).thenReturn(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));
        when(inventory.getSlotLimit(0, Direction.SOUTH)).thenReturn(64);
        when(inventory.isItemValid(eq(0), any(), eq(Direction.SOUTH))).thenReturn(true);
        when(inventory.insertItem(eq(0), any(), eq(Direction.SOUTH), eq(Action.EXECUTE))).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        var view = MekanismProxy.INSTANCE.itemHandler(machine, Direction.SOUTH);
        assertEquals(1, view.getSlots());
        assertEquals(2, view.getStackInSlot(0).getCount());
        assertTrue(view.insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 1), false).isEmpty());
        verify(inventory).insertItem(eq(0), any(), eq(Direction.SOUTH), eq(Action.EXECUTE));
        assertNull(MekanismProxy.INSTANCE.itemHandler(machine, null));
    }

    @Test void nativeFluidHandlerTargetNeverFallsBackToAnotherTank() {
        BlockEntity machine = mock(BlockEntity.class, withSettings().extraInterfaces(IMekanismFluidHandler.class));
        IMekanismFluidHandler fluids = (IMekanismFluidHandler) machine;
        var first = mock(mekanism.api.fluid.IExtendedFluidTank.class);
        var second = mock(mekanism.api.fluid.IExtendedFluidTank.class);
        when(fluids.getFluidTanks(Direction.WEST)).thenReturn(List.of(first, second));
        when(fluids.getFluidInTank(anyInt(), eq(Direction.WEST))).thenReturn(FluidStack.EMPTY);
        when(fluids.getTankCapacity(anyInt(), eq(Direction.WEST))).thenReturn(1000);
        when(fluids.isFluidValid(anyInt(), any(), eq(Direction.WEST))).thenReturn(true);
        when(fluids.insertFluid(anyInt(), any(), eq(Direction.WEST), eq(Action.EXECUTE))).thenAnswer(call -> call.getArgument(1));
        var handler = MekanismProxy.INSTANCE.fluidHandler(machine, Direction.WEST);
        var target = MekanismProxy.INSTANCE.targetFluidHandler(machine, Direction.WEST, handler, 1);
        assertEquals(0, target.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE));
        verify(fluids).insertFluid(eq(1), any(), eq(Direction.WEST), eq(Action.EXECUTE));
        verify(fluids, never()).insertFluid(eq(0), any(), any(), any());
    }

    @Test void strictEnergyCapabilityIsExposedAsForgeEnergyWithNativeConversion() {
        BlockEntity machine = mock(BlockEntity.class);
        var strict = mock(mekanism.api.energy.IStrictEnergyHandler.class);
        when(machine.getCapability(mekanism.common.capabilities.Capabilities.STRICT_ENERGY, Direction.UP))
                .thenReturn(LazyOptional.of(() -> strict));
        when(strict.getEnergyContainerCount()).thenReturn(1);
        when(strict.getEnergy(0)).thenReturn(mekanism.api.math.FloatingLong.create(10));
        when(strict.getMaxEnergy(0)).thenReturn(mekanism.api.math.FloatingLong.create(100));
        when(strict.getNeededEnergy(0)).thenReturn(mekanism.api.math.FloatingLong.create(90));
        when(strict.insertEnergy(eq(0), any(), eq(Action.SIMULATE))).thenReturn(mekanism.api.math.FloatingLong.ZERO);
        when(strict.insertEnergy(eq(0), any(), eq(Action.EXECUTE))).thenReturn(mekanism.api.math.FloatingLong.ZERO);
        var energy = MekanismProxy.INSTANCE.energyHandler(machine, Direction.UP);
        assertNotNull(energy);
        // Mekanism 默认 1 FE = 2.5 J，因此 10 J 和 100 J 分别显示为 4 FE 与 40 FE。
        assertEquals(4, energy.getEnergyStored());
        assertEquals(40, energy.getMaxEnergyStored());
        assertEquals(20, energy.receiveEnergy(20, true));
        assertEquals(20, energy.receiveEnergy(20, false));
        verify(strict).insertEnergy(eq(0), any(), eq(Action.SIMULATE));
        verify(strict).insertEnergy(eq(0), any(), eq(Action.EXECUTE));
    }
}
