/**
 * 【文件职责】以真实 AE2 GenericStackInv 检验物品与流体视图。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增行为测试。
 * - 新增：混合库存指定罐、完整流体 NBT、模拟无副作用、禁止提取的库存策略用例。
 * - 修改：使用原生 insert/extract 和容量方法，不以相同实现的伪算法替代待测功能。
 * - 用途：检验接口/样板返回缓存的传输约束，不模拟整个 ME 网络或机器 tick。
 */
package com.primordial.machine;

import appeng.api.stacks.AEKeyType;
import appeng.capabilities.Capabilities;
import appeng.helpers.externalstorage.GenericStackInv;
import com.primordial.machine.adapters.AE2Proxy;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 【变更记录 #2】2026-09-15 16:48:00（第 2 次备注 · 1.0.0-stage5-adapters）
 * - 删除：无。
 * - 新增：GenericInternalInventory 总开关、混合介质槽位拒绝和无效输入回归。
 * - 修改：测试覆盖模拟/执行都不能越过 AE2 的 canInsert、canExtract 与 isAllowed。
 * - 用途：确保专用代理绑定单槽后不会把流体写入物品槽，也不会从禁止抽取的缓存取出资源。
 */
/** 真实 AE2 GenericStackInv 行为：混合库存精确罐、NBT 匹配、只许插入的返回缓存不允许回收。 */
class AE2AdapterTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    private BlockEntity machine(GenericStackInv inventory) {
        BlockEntity machine = mock(BlockEntity.class);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        when(machine.getCapability(Capabilities.GENERIC_INTERNAL_INV, Direction.EAST))
                .thenReturn(LazyOptional.of(() -> inventory));
        return machine;
    }

    @Test void indexedFluidTransferKeepsOtherGenericSlotsAndExactNbt() {
        var inv = new GenericStackInv(null, 3);
        inv.setCapacity(AEKeyType.fluids(), 3000);
        var machine = machine(inv);
        var handler = AE2Proxy.INSTANCE.fluidHandler(machine, Direction.EAST);
        var view = AE2Proxy.INSTANCE.targetFluidHandler(machine, Direction.EAST, handler, 2);
        var water = new FluidStack(Fluids.WATER, 1200);
        CompoundTag tag = new CompoundTag(); tag.putString("batch", "A"); water.setTag(tag);
        assertEquals(1200, view.fill(water, IFluidHandler.FluidAction.SIMULATE));
        assertNull(inv.getKey(2));
        assertEquals(1200, view.fill(water, IFluidHandler.FluidAction.EXECUTE));
        assertNull(inv.getKey(0)); assertNull(inv.getKey(1));
        assertEquals(1200, inv.getAmount(2));
        assertTrue(view.drain(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE).isEmpty());
        FluidStack extracted = view.drain(water, IFluidHandler.FluidAction.EXECUTE);
        assertEquals(1200, extracted.getAmount());
        assertEquals(tag, extracted.getTag());
    }

    @Test void nativeInventoryExtractionPolicyIsNotBypassed() {
        var inv = new GenericStackInv(null, 2) {
            @Override public boolean canExtract() { return false; }
        };
        inv.setCapacity(AEKeyType.items(), 64);
        inv.setCapacity(AEKeyType.fluids(), 1000);
        var machine = machine(inv);
        var items = new SingleSlotHandler(AE2Proxy.INSTANCE.itemHandler(machine, Direction.EAST), 1);
        assertTrue(items.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false).isEmpty());
        assertTrue(items.extractItem(0, 4, false).isEmpty());
        var fluids = AE2Proxy.INSTANCE.fluidHandler(machine, Direction.EAST);
        var tank = AE2Proxy.INSTANCE.targetFluidHandler(machine, Direction.EAST, fluids, 0);
        tank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        assertTrue(tank.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty());
        assertEquals(1000, inv.getAmount(0));
        assertNull(AE2Proxy.INSTANCE.itemHandler(machine, null));
    }

    @Test void genericInventoryPermissionsAndMixedSlotTypesAreExplicitlyRespected() {
        var inv = new GenericStackInv(null, 1) {
            @Override public boolean canInsert() { return false; }
            @Override public boolean canExtract() { return false; }
        };
        inv.setCapacity(AEKeyType.items(), 64);
        inv.setCapacity(AEKeyType.fluids(), 1000);
        var machine = machine(inv);
        var fluids = AE2Proxy.INSTANCE.fluidHandler(machine, Direction.EAST);
        var target = AE2Proxy.INSTANCE.targetFluidHandler(machine, Direction.EAST, fluids, 0);
        assertEquals(0, target.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE));
        assertTrue(target.drain(100, IFluidHandler.FluidAction.SIMULATE).isEmpty());
    }

    @Test void fluidTargetRefusesToReplaceAnExistingItemKey() {
        var inv = new GenericStackInv(null, 1);
        inv.setCapacity(AEKeyType.items(), 64);
        inv.setCapacity(AEKeyType.fluids(), 1000);
        inv.setStack(0, appeng.api.stacks.GenericStack.fromItemStack(new ItemStack(Items.IRON_INGOT, 1)));
        var machine = machine(inv);
        var fluids = AE2Proxy.INSTANCE.fluidHandler(machine, Direction.EAST);
        var target = AE2Proxy.INSTANCE.targetFluidHandler(machine, Direction.EAST, fluids, 0);
        assertEquals(0, target.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE));
        assertEquals(1, inv.getAmount(0));
    }
}
