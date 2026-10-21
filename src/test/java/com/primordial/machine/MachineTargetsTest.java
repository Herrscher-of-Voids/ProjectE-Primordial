/**
 * 【文件职责】回归单物品槽、绑定有效性、配置 NBT 和菜单整数同步。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增行为测试。
 * - 新增：模拟不写入、精确槽容量、目标失效不回退、通用多罐拒绝及 32 位能量边界用例。
 * - 修改：输入堆栈使用复制以遵循 IItemHandler 的对象所有权，校验余量与实际库存。
 * - 用途：验证错误方向/身份及移除机器不会写其它槽，旧 NBT 不产生意外默认绑定。
 */
package com.primordial.machine;

import com.primordial.data.MachineConfig;
import com.primordial.data.MachineTarget;
import com.primordial.menu.ConfigMenuData;
import com.primordial.menu.ConfiguratorMenu;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 行为回归：指定槽、模拟无副作用、失效停止、NBT 持久化以及菜单 16 位传输边界。 */
class MachineTargetsTest {
    @BeforeAll static void bootstrap() {
        AdapterTestBootstrap.initialize();
    }

    @Test void onlySelectedSlotReceivesAndExtracts() {
        var inventory = new ItemStackHandler(3);
        var view = new SingleSlotHandler(inventory, 1);
        ItemStack input = new ItemStack(Items.IRON_INGOT, 40);
        assertTrue(view.insertItem(0, input, true).isEmpty());
        assertTrue(inventory.getStackInSlot(1).isEmpty());
        assertTrue(view.insertItem(0, input.copy(), false).isEmpty());
        assertEquals(40, inventory.getStackInSlot(1).getCount());
        assertTrue(inventory.getStackInSlot(0).isEmpty());
        assertTrue(inventory.getStackInSlot(2).isEmpty());
        assertEquals(16, view.insertItem(0, input.copy(), false).getCount());
        assertEquals(12, view.extractItem(0, 12, false).getCount());
        assertEquals(52, inventory.getStackInSlot(1).getCount());
        assertThrows(IndexOutOfBoundsException.class, () -> view.insertItem(1, input, false));
    }

    @Test void unavailableDirectionAndChangedIdentityDoNotFallback() {
        BlockEntity machine = mock(BlockEntity.class);
        when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
        var inventory = new ItemStackHandler(2);
        var proxy = new ModAdapterProxy("test") {
            @Override public net.minecraftforge.items.IItemHandler itemHandler(BlockEntity be, Direction side) {
                return side == Direction.NORTH ? inventory : null;
            }
        };
        String key = proxy.itemSlotKey(machine, Direction.NORTH, inventory, 1);
        assertNull(MachineTargetAccess.items(proxy, machine, MachineTarget.UNBOUND));
        assertNull(MachineTargetAccess.items(proxy, machine, new MachineTarget(Direction.SOUTH.get3DDataValue(), 1, key)));
        assertNull(MachineTargetAccess.items(proxy, machine, new MachineTarget(Direction.NORTH.get3DDataValue(), 1, "old-layout")));
        var target = new MachineTarget(Direction.NORTH.get3DDataValue(), 1, key);
        assertNotNull(MachineTargetAccess.items(proxy, machine, target));
        when(machine.isRemoved()).thenReturn(true);
        assertNull(MachineTargetAccess.items(proxy, machine, target));
    }

    @Test void genericMultipleTanksCannotPretendToTargetOne() {
        IFluidHandler aggregate = mock(IFluidHandler.class);
        when(aggregate.getTanks()).thenReturn(2);
        assertNull(ForgeCapabilityProxy.INSTANCE.targetFluidHandler(mock(BlockEntity.class), Direction.NORTH, aggregate, 1));
        when(aggregate.getTanks()).thenReturn(1);
        assertSame(aggregate, ForgeCapabilityProxy.INSTANCE.targetFluidHandler(mock(BlockEntity.class), Direction.NORTH, aggregate, 0));
    }

    @Test void fullConfigurationPreservesIndependentTargetsAndOldNbtStaysUnbound() {
        MachineConfig cfg = new MachineConfig();
        cfg.itemIn[3].target = new MachineTarget(2, 12, "item-12");
        cfg.fluidOut[2].target = new MachineTarget(3, 4, "output-fluid");
        cfg.energy.supplyPort = new MachineTarget(1, 0, "energy-input");
        cfg.energy.recoverPort = new MachineTarget(0, 0, "energy-output");
        MachineConfig restored = MachineConfig.load(cfg.save());
        assertEquals(cfg.itemIn[3].target, restored.itemIn[3].target);
        assertEquals(cfg.fluidOut[2].target, restored.fluidOut[2].target);
        assertEquals(cfg.energy.supplyPort, restored.energy.supplyPort);
        assertEquals(cfg.energy.recoverPort, restored.energy.recoverPort);
        assertFalse(MachineTarget.load(new CompoundTag()).bound());
        CompoundTag invalid = cfg.itemIn[3].target.save();
        invalid.putInt("side", 6);
        assertEquals(MachineTarget.UNBOUND, MachineTarget.load(invalid));
    }

    @Test void energyMenuValuesSurviveSignedSixteenBitTransport() {
        ConfiguratorMenu server = mock(ConfiguratorMenu.class), client = mock(ConfiguratorMenu.class);
        ConfigMenuData source = new ConfigMenuData(server), sink = new ConfigMenuData(client);
        for (int value : new int[]{0, 32768, 65535, 65536, 1_000_000, Integer.MAX_VALUE}) {
            server.machineEnergyFE = value;
            server.machineEnergyMax = Integer.MAX_VALUE;
            for (int i = 0; i < source.getCount(); i++) sink.set(i, (short) source.get(i));
            assertEquals(value, client.machineEnergyFE);
            assertEquals(Integer.MAX_VALUE, client.machineEnergyMax);
        }
    }
}
