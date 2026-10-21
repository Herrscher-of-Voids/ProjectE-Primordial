/**
 * 【文件职责】以真实 Titanium 组件检验原生侧面与目标身份。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增行为测试。
 * - 新增：命名储罐、DRAIN 输出罐、禁用面重排、物品组件身份和偏移槽过滤用例。
 * - 修改：自动化插入必须通过组件内索引过滤，关闭前面组件不能将旧绑定指向输出组件。
 * - 用途：验证回调保留的组件传输行为；完整工业先锋世界运行待整合环境联调。
 */
/**
 * 【变更记录 #2】2026-09-14 00:57:13（第 2 次备注 · 1.0.0-stage5-validation）
 * - 删除：测试库存组件构造时的原始泛型类型。
 * - 新增：菱形类型推断，使输入过滤 lambda 的槽索引保持 Integer。
 * - 修改：修正组件局部槽过滤回归用例的 Java 17 编译类型；生产代码不受影响。
 * - 用途：校验聚合偏移槽 4 对应组件内部槽 1，并确认只在该槽插入且禁止提取。
 */
package com.primordial.machine;

import com.hrznstudio.titanium.block.tile.ActiveTile;
import com.hrznstudio.titanium.component.fluid.MultiTankComponent;
import com.hrznstudio.titanium.component.fluid.SidedFluidTankComponent;
import com.hrznstudio.titanium.component.fluid.FluidTankComponent;
import com.hrznstudio.titanium.component.inventory.MultiInventoryComponent;
import com.hrznstudio.titanium.component.inventory.SidedInventoryComponent;
import com.hrznstudio.titanium.component.sideness.IFacingComponent;
import com.hrznstudio.titanium.util.FacingUtil;
import com.primordial.data.MachineTarget;
import com.primordial.machine.adapters.IndustrialForegoingProxy;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 【变更记录 #3】2026-09-15 16:48:00（第 3 次备注 · 1.0.0-stage5-adapters）
 * - 删除：无。
 * - 新增：HashSet 储罐按名称排序、负索引和组件名称稳定绑定回归。
 * - 修改：重建组件集合顺序后，目标 key 仍指向同名输入/输出罐。
 * - 用途：防止工业先锋 Titanium 的集合迭代顺序变化造成配置写入错误储罐。
 */
/** 真实 Titanium 组件回归：流体输入/输出权限及关闭面导致的端口重排，均不能写错目标。 */
@SuppressWarnings({"rawtypes", "unchecked"})
class IndustrialForegoingAdapterTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void namedTanksRespectDisabledFacesAndDrainOnlyOutput() {
        ActiveTile machine = mock(ActiveTile.class);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(machine.getFacingDirection()).thenReturn(Direction.NORTH);
        MultiTankComponent components = new MultiTankComponent();
        var input = new SidedFluidTankComponent("input", 1000, 0, 0, 0);
        var output = new SidedFluidTankComponent("output", 1000, 0, 0, 1);
        output.setTankAction(FluidTankComponent.Action.DRAIN);
        components.add(input, output);
        when(machine.getMultiTankComponent()).thenReturn(components);
        when(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH))
                .thenAnswer(call -> components.getCapabilityForSide(FacingUtil.Sideness.FRONT));
        var proxy = IndustrialForegoingProxy.INSTANCE;
        IFluidHandler all = proxy.fluidHandler(machine, Direction.NORTH);
        var target = new MachineTarget(2, 0, proxy.fluidTankKey(machine, Direction.NORTH, all, 0));
        var view = MachineTargetAccess.fluids(proxy, machine, target);
        assertNotNull(view);
        assertEquals(700, view.fill(new FluidStack(Fluids.WATER, 700), IFluidHandler.FluidAction.EXECUTE));
        assertEquals(700, input.getFluidAmount()); assertEquals(0, output.getFluidAmount());
        IFluidHandler out = proxy.targetFluidHandler(machine, Direction.NORTH, all, 1);
        assertEquals(0, out.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE));
        output.fillForced(new FluidStack(Fluids.WATER, 400), IFluidHandler.FluidAction.EXECUTE);
        assertEquals(400, out.drain(400, IFluidHandler.FluidAction.EXECUTE).getAmount());
        components.handleFacingChange("input", FacingUtil.Sideness.FRONT, IFacingComponent.FaceMode.NONE.ordinal());
        assertNull(MachineTargetAccess.fluids(proxy, machine, target));
        assertEquals(700, input.getFluidAmount());
    }

    @Test void itemComponentNamesPreventRenumberingIntoAnotherInventory() {
        ActiveTile machine = mock(ActiveTile.class);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        MultiInventoryComponent components = new MultiInventoryComponent();
        var input = new SidedInventoryComponent<>("input", 0, 0, 2, 0);
        var output = new SidedInventoryComponent<>("output", 0, 0, 2, 1);
        output.setInputFilter((stack, index) -> false);
        components.add(input, output);
        when(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH))
                .thenAnswer(call -> components.getCapabilityForSide(FacingUtil.Sideness.FRONT));
        var proxy = IndustrialForegoingProxy.INSTANCE;
        var exposed = proxy.itemHandler(machine, Direction.NORTH);
        var target = new MachineTarget(2, 1, proxy.itemSlotKey(machine, Direction.NORTH, exposed, 1));
        var selected = MachineTargetAccess.items(proxy, machine, target);
        assertTrue(selected.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty());
        assertEquals(3, input.getStackInSlot(1).getCount());
        assertTrue(input.getStackInSlot(0).isEmpty());
        components.handleFacingChange("input", FacingUtil.Sideness.FRONT, IFacingComponent.FaceMode.NONE.ordinal());
        assertNull(MachineTargetAccess.items(proxy, machine, target));
        assertTrue(output.getStackInSlot(1).isEmpty());
    }

    @Test void inputPredicateUsesComponentLocalSlotAfterAnOffset() {
        var machine = mock(ActiveTile.class);
        MultiInventoryComponent components = new MultiInventoryComponent();
        var upgrades = new SidedInventoryComponent<>("upgrades", 0, 0, 3, 0);
        var input = new SidedInventoryComponent<>("input", 0, 0, 2, 1);
        input.setInputFilter((stack, slot) -> slot == 1);
        input.setOutputFilter((stack, slot) -> false);
        components.add(upgrades, input);
        var exposed = (net.minecraftforge.items.IItemHandler) components.getCapabilityForSide(FacingUtil.Sideness.FRONT).resolve().orElseThrow();
        var view = IndustrialForegoingProxy.INSTANCE.targetItemHandler(machine, Direction.NORTH, exposed, 4);
        assertTrue(view.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty());
        assertEquals(2, input.getStackInSlot(1).getCount());
        assertTrue(input.getStackInSlot(0).isEmpty());
        assertTrue(view.extractItem(0, 2, false).isEmpty());
    }

    @Test void tanksArePresentedInStableNameOrder() {
        ActiveTile machine = mock(ActiveTile.class);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(machine.getFacingDirection()).thenReturn(Direction.NORTH);
        MultiTankComponent components = new MultiTankComponent();
        var zulu = new SidedFluidTankComponent("zulu", 1000, 0, 0, 0);
        var alpha = new SidedFluidTankComponent("alpha", 1000, 0, 0, 1);
        components.add(zulu, alpha);
        when(machine.getMultiTankComponent()).thenReturn(components);
        when(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH))
                .thenAnswer(call -> components.getCapabilityForSide(FacingUtil.Sideness.FRONT));
        var proxy = IndustrialForegoingProxy.INSTANCE;
        var exposed = proxy.fluidHandler(machine, Direction.NORTH);
        assertTrue(proxy.fluidTankKey(machine, Direction.NORTH, exposed, 0).contains("alpha"));
        assertTrue(proxy.fluidTankKey(machine, Direction.NORTH, exposed, 1).contains("zulu"));
        assertEquals("", proxy.fluidTankKey(machine, Direction.NORTH, exposed, -1));
    }
}
