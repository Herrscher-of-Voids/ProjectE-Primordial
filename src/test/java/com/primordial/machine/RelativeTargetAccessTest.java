/**
 * 【文件职责】验证相对绑定旋转后仍通过严格端口身份访问五类介质，旧绑定不改变连接面。
 * 【变更记录 #1】2026-10-13 22:55:00（第 1 次备注 · 1.2.0-relative-target-system）
 * - 删除：无。新增：相对面存档、旋转物品/流体/FE/气体/独立资源、无效方向与结构拒绝回归。
 * - 修改：使用真实物品槽、储罐、电池和原生气体 handler 查询，只替换机器/注册边界。
 * - 用途：确保旋转不串槽、内部面不变、损坏 NBT 不退回默认端口。
 */
/**
 * 【变更记录 #2】2026-10-13 23:01:02（第 2 次备注 · 1.2.0-relative-target-system）
 * - 删除：无。新增：方向字符串增长导致身份越过 512 字符的边界断言。
 * - 修改：验证返回空而非抛错。用途：失效目标不影响同机器其它规则。
 */
/**
 * 【变更记录 #3】2026-10-13 23:03:12（第 3 次备注 · 1.2.0-relative-target-system）
 * - 删除：边界测试硬编码的错误字符串长度。新增：按 MAX_KEY_LENGTH 计算填充。
 * - 修改：确保初始键正好 512 字符，旋转后才越界。用途：准确覆盖解析后长度校验。
 */
package com.primordial.machine;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.data.MachineConfig;
import com.primordial.data.MachineTarget;
import com.primordial.machine.adapters.MekanismProxy;
import com.primordial.resource.EmcResourcePort;
import com.primordial.resource.ResourcePortRegistry;
import com.primordial.util.RelativeMachineSides;
import java.util.List;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RelativeTargetAccessTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    private static BlockState facing(Direction direction) {
        return Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
    }

    @Test void everyFacePersistsAndFollowsAllHorizontalRotations() {
        for (Direction initial : Direction.Plane.HORIZONTAL) {
            var sides = RelativeMachineSides.from(facing(initial));
            for (var face : RelativeMachineSides.Face.values()) {
                var target = MachineTarget.relative(sides.direction(face).get3DDataValue(), 3, "stable/slot/3", facing(initial));
                var restored = MachineTarget.load(target.save());
                assertEquals(face, restored.relativeFace());
                assertEquals(target, restored);
                for (Direction rotated : Direction.Plane.HORIZONTAL) {
                    assertEquals(RelativeMachineSides.from(facing(rotated)).direction(face), restored.resolve(facing(rotated)).direction());
                    assertEquals(3, restored.resolve(facing(rotated)).index());
                    assertEquals("stable/slot/3", restored.resolve(facing(rotated)).key());
                }
            }
        }
    }

    @Test void legacyAndInternalBindingsKeepTheirOriginalMeaningAndCorruptNbtIsUnbound() {
        var legacy = new MachineTarget(Direction.WEST.get3DDataValue(), 4, "old");
        var internal = MachineTarget.relative(MachineTarget.INTERNAL_SIDE, 0, "internal", facing(Direction.NORTH));
        assertNull(internal.relativeFace());
        for (Direction front : Direction.Plane.HORIZONTAL) {
            assertEquals(legacy, MachineTarget.load(legacy.save()).resolve(facing(front)));
            assertNull(internal.resolve(facing(front)).directionOrNull());
        }
        for (String invalid : List.of("", "north", "FRONT", "unknown")) {
            var tag = legacy.save(); tag.putString("relative_face", invalid);
            assertEquals(MachineTarget.UNBOUND, MachineTarget.load(tag));
        }
        var wrongType = legacy.save(); wrongType.putInt("relative_face", 0);
        assertEquals(MachineTarget.UNBOUND, MachineTarget.load(wrongType));
        var internalWithFace = internal.save(); internalWithFace.putString("relative_face", "front");
        assertEquals(MachineTarget.UNBOUND, MachineTarget.load(internalWithFace));
        var invalidSide = legacy.save(); invalidSide.putInt("side", 7);
        assertEquals(MachineTarget.UNBOUND, MachineTarget.load(invalidSide));
        assertEquals(MachineTarget.UNBOUND, MachineTarget.load(new CompoundTag()));
    }

    @Test void allConfigurationSectionsPreserveRelativeTargetsAndDistinctInputOutput() {
        var cfg = new MachineConfig();
        var front = MachineTarget.relative(2, 0, "input", facing(Direction.NORTH));
        var back = MachineTarget.relative(3, 1, "output", facing(Direction.NORTH));
        cfg.itemIn[0].setTargets(List.of(front)); cfg.itemOut[0].setTargets(List.of(back));
        cfg.fluidIn[0].setTargets(List.of(front)); cfg.fluidOut[0].setTargets(List.of(back));
        cfg.resourceIn[0].setTargets(List.of(front)); cfg.resourceOut[0].setTargets(List.of(back));
        cfg.energy.supplyPort = front; cfg.energy.recoverPort = back;
        var loaded = MachineConfig.load(cfg.save());
        assertEquals(List.of(front), loaded.itemIn[0].effectiveTargets());
        assertEquals(List.of(back), loaded.itemOut[0].effectiveTargets());
        assertEquals(List.of(front), loaded.fluidIn[0].effectiveTargets());
        assertEquals(List.of(back), loaded.fluidOut[0].effectiveTargets());
        assertEquals(List.of(front), loaded.resourceIn[0].effectiveTargets());
        assertEquals(List.of(back), loaded.resourceOut[0].effectiveTargets());
        assertEquals(front, loaded.energy.supplyPort); assertEquals(back, loaded.energy.recoverPort);
    }

    @Test void itemFluidAndEnergyTransfersUseRotatedSideAndRetainStructureChecks() {
        var machine = mock(BlockEntity.class);
        when(machine.getBlockState()).thenReturn(facing(Direction.NORTH));
        var items = new ItemStackHandler(2);
        var fluid = new FluidTank(4000);
        var energy = new EnergyStorage(4000);
        var internal = new EnergyStorage(8000);
        var proxy = new ModAdapterProxy("test_rotate") {
            private boolean front(BlockEntity block, Direction side) {
                return side == RelativeMachineSides.from(block.getBlockState()).front();
            }
            @Override public String endpointKey(BlockEntity block, Direction side) {
                return super.endpointKey(block, side) + "/side/" + (side == null ? "internal" : side.getName()) + "/class/" + block.getClass().getName();
            }
            @Override public net.minecraftforge.items.IItemHandler itemHandler(BlockEntity b, Direction s) { return front(b, s) ? items : null; }
            @Override public IFluidHandler fluidHandler(BlockEntity b, Direction s) { return front(b, s) ? fluid : null; }
            @Override public net.minecraftforge.energy.IEnergyStorage energyHandler(BlockEntity b, Direction s) { return s == null ? internal : front(b, s) ? energy : null; }
        };
        var itemTarget = MachineTarget.relative(2, 1, proxy.itemSlotKey(machine, Direction.NORTH, items, 1), machine.getBlockState());
        var fluidTarget = MachineTarget.relative(2, 0, proxy.fluidTankKey(machine, Direction.NORTH, fluid, 0), machine.getBlockState());
        var energyTarget = MachineTarget.relative(2, 0, MachineTargetAccess.energyKey(proxy, machine, Direction.NORTH, energy), machine.getBlockState());
        var internalTarget = MachineTarget.relative(6, 0, MachineTargetAccess.energyKey(proxy, machine, null, internal), machine.getBlockState());
        when(machine.getBlockState()).thenReturn(facing(Direction.EAST));
        var prefix = "test/side/east/class/";
        var longKey = prefix + "x".repeat(MachineTarget.MAX_KEY_LENGTH - prefix.length());
        var longTarget = MachineTarget.relative(5, 0, longKey, facing(Direction.EAST));
        assertTrue(longTarget.bound());
        when(machine.getBlockState()).thenReturn(facing(Direction.NORTH));
        assertFalse(longTarget.resolve(machine.getBlockState()).bound());
        assertNull(MachineTargetAccess.items(proxy, machine, longTarget));
        assertNull(MachineTargetAccess.fluids(proxy, machine, longTarget));
        assertNull(MachineTargetAccess.energy(proxy, machine, longTarget));
        when(machine.getBlockState()).thenReturn(facing(Direction.EAST));
        var itemPort = MachineTargetAccess.items(proxy, machine, itemTarget);
        assertNotNull(itemPort);
        assertTrue(itemPort.insertItem(0, new ItemStack(Items.IRON_INGOT, 9), false).isEmpty());
        assertTrue(items.getStackInSlot(0).isEmpty()); assertEquals(9, items.getStackInSlot(1).getCount());
        assertEquals(4, itemPort.extractItem(0, 4, false).getCount());
        var fluidPort = MachineTargetAccess.fluids(proxy, machine, fluidTarget);
        assertNotNull(fluidPort);
        assertEquals(1000, fluidPort.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE));
        assertEquals(300, fluidPort.drain(300, IFluidHandler.FluidAction.EXECUTE).getAmount());
        var energyPort = MachineTargetAccess.energy(proxy, machine, energyTarget);
        assertNotNull(energyPort); assertEquals(600, energyPort.receiveEnergy(600, false));
        assertEquals(200, energyPort.extractEnergy(200, false));
        assertSame(internal, MachineTargetAccess.energy(proxy, machine, internalTarget));
        assertNull(MachineTargetAccess.items(proxy, machine, new MachineTarget(2, 1, itemTarget.key())));
        items.setSize(3);
        assertNull(MachineTargetAccess.items(proxy, machine, itemTarget));
        assertNull(MachineTargetAccess.fluids(proxy, machine, new MachineTarget(2, 0, "wrong-structure", fluidTarget.relativeFace())));
        when(machine.isRemoved()).thenReturn(true);
        assertNull(MachineTargetAccess.energy(proxy, machine, energyTarget));
    }

    @Test void gasResolvesRotatedCapabilityButNeverAcceptsChangedTankIdentity() {
        var machine = mock(BlockEntity.class);
        when(machine.getBlockState()).thenReturn(facing(Direction.NORTH));
        var gas = mock(IGasHandler.class);
        when(gas.getTanks()).thenReturn(2);
        when(machine.getCapability(eq(Capabilities.GAS_HANDLER), any(Direction.class))).thenAnswer(call ->
                call.getArgument(1) == RelativeMachineSides.from(machine.getBlockState()).front()
                        ? LazyOptional.of(() -> gas) : LazyOptional.empty());
        var target = MachineTarget.relative(2, 1, MekanismProxy.INSTANCE.gasTankKey(machine, Direction.NORTH, gas, 1), machine.getBlockState());
        try (var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS)) {
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            when(machine.getBlockState()).thenReturn(facing(Direction.SOUTH));
            assertSame(gas, MachineTargetAccess.gases(MekanismProxy.INSTANCE, machine, target));
            assertNull(MachineTargetAccess.gases(MekanismProxy.INSTANCE, machine, new MachineTarget(2, 1, target.key())));
            when(gas.getTanks()).thenReturn(3);
            assertNull(MachineTargetAccess.gases(MekanismProxy.INSTANCE, machine, target));
        }
    }

    @Test void resourceIdentityRotatesOnlyRecognizedDirectionField() {
        var machine = mock(BlockEntity.class);
        when(machine.getBlockState()).thenReturn(facing(Direction.NORTH));
        var target = MachineTarget.relative(2, 0, "botania/test.ManaPool/north/botania:mana", machine.getBlockState());
        var port = mock(EmcResourcePort.class);
        when(port.key()).thenReturn("botania/test.ManaPool/west/botania:mana");
        try (var registry = mockStatic(ResourcePortRegistry.class)) {
            registry.when(() -> ResourcePortRegistry.find(machine, Direction.WEST)).thenReturn(List.of(port));
            when(machine.getBlockState()).thenReturn(facing(Direction.WEST));
            assertSame(port, MachineTargetAccess.resource(machine, target));
            when(port.key()).thenReturn("botania/test.OtherPool/west/botania:mana");
            assertNull(MachineTargetAccess.resource(machine, target));
            registry.verify(() -> ResourcePortRegistry.find(machine, Direction.WEST), times(2));
            registry.verifyNoMoreInteractions();
        }
        var unknown = MachineTarget.relative(2, 0, "thirdparty/test.Device/north/custom", facing(Direction.NORTH));
        assertEquals(unknown.key(), unknown.resolve(facing(Direction.WEST)).key());
    }
}
