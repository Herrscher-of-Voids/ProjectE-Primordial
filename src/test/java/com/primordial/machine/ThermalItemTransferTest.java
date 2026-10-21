/**
 * 【文件职责】用玩家同版 CoFH Core 11.0.2.56 库存验证热力补充闪烁、精确槽位和 EMC 结算。
 * 【变更记录 #1】2026-09-15 13:45:42（第 1 次备注 · 1.0.0-thermal-item-overflow）
 * - 删除：无；新增行为回归测试。
 * - 新增：原接口溢出复现、连续补满、大容量和固定间隔、NBT/槽位隔离、模拟与执行差异退款及 64 轮保护。
 * - 修改：CoFH 库存、配置、目标解析、任务执行与数量计算均使用真实实现，世界和 EMC 服务用测试替身提供。
 * - 用途：验证不再把满槽清成负数、不反复扣费，也不把所有库存限制为 64；不代替客户端实机画面复测。
 */
/**
 * 【变更记录 #2】2026-09-15 15:10:05（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：模拟已废弃的独立购买价接口。
 * - 新增：真实 EmcService/EmcSettlement 和找零 SavedData 参与热力回归。
 * - 修改：仅替换外部整数余额与价格，预扣、部分退款及槽位传输运行生产代码。
 * - 用途：已有满槽/闪烁/大容量测试同时验证本阶段服务接入没有回归。
 */
package com.primordial.machine;

import cofh.lib.common.inventory.ItemStorageCoFH;
import cofh.lib.common.inventory.ManagedItemHandler;
import com.primordial.data.MachineConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.data.MachineTarget;
import com.primordial.data.SampleMode;
import com.primordial.service.EmcService;
import com.primordial.task.MachineItemTransfer;
import com.primordial.task.TaskExecutor;
import com.primordial.task.TaskScheduler;
import com.primordial.util.BalanceSyncer;
import com.primordial.value.ValueManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ThermalItemTransferTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void installedCofhImplementationReproducesTheOldOverflow() {
        var storage = new ItemStorageCoFH();
        var handler = managed(storage);
        assertTrue(handler.insertItem(0, new ItemStack(Items.IRON_INGOT, 64), false).isEmpty());
        var excessive = new ItemStack(Items.IRON_INGOT, Integer.MAX_VALUE);
        // 旧路径在满槽仍试算 MAX_VALUE，CoFH 的 int 加法溢出后误报全部接受。
        assertTrue(handler.insertItem(0, excessive, true).isEmpty());
        assertEquals(64, storage.getCount());
        assertTrue(handler.insertItem(0, excessive, false).isEmpty());
        assertTrue(storage.isEmpty(), "The real CoFH implementation reproduces the disappearing input stack");
    }

    @Test void keepLevelStaysFullForHundredsOfTicksAndOnlyPaysForRealReplacements() {
        var storage = new ItemStorageCoFH();
        var exposed = spy(managed(storage));
        try (var world = new TaskWorld(exposed, 0)) {
            BigInteger initial = world.balance;
            world.tick(1);
            assertEquals(64, storage.getCount());
            assertEquals(initial.subtract(BigInteger.valueOf(640)), world.balance);
            clearInvocations(exposed);
            for (int tick = 2; tick <= 201; tick++) {
                world.tick(tick);
                assertEquals(64, storage.getCount(), "A full input must never alternate with an empty stack");
            }
            verify(exposed, never()).insertItem(anyInt(), any(), anyBoolean());
            assertEquals(1, world.payments);
            storage.extractItem(0, 1, false);
            world.tick(202);
            assertEquals(64, storage.getCount());
            assertEquals(initial.subtract(BigInteger.valueOf(650)), world.balance);
            assertEquals(2, world.payments);
            assertEquals(1, world.config.itemIn[0].filter.getCount());
        }
    }

    @Test void fixedModeWithMaximumAmountStillRespectsCapacityAndTickInterval() {
        var storage = new ItemStorageCoFH();
        storage.setItemStack(new ItemStack(Items.IRON_INGOT, 63));
        try (var world = new TaskWorld(managed(storage), 0)) {
            world.config.itemIn[0].mode = SampleMode.FIXED_RATE;
            world.config.itemIn[0].threshold = Integer.MAX_VALUE;
            world.config.itemIn[0].intervalTicks = 20;
            BigInteger initial = world.balance;
            world.tick(19);
            assertEquals(63, storage.getCount());
            assertEquals(0, world.payments);
            world.tick(20);
            assertEquals(64, storage.getCount());
            storage.extractItem(0, 4, false);
            world.tick(39);
            assertEquals(60, storage.getCount());
            world.tick(40);
            assertEquals(64, storage.getCount());
            assertEquals(initial.subtract(BigInteger.valueOf(50)), world.balance);
        }
    }

    @Test void nativeLargeCapacityRemainsAvailableUpToTheIntegerBoundary() {
        var storage = new ItemStorageCoFH(Integer.MAX_VALUE);
        storage.setItemStack(new ItemStack(Items.IRON_INGOT, 17));
        var target = new SingleSlotHandler(managed(storage), 0);
        var sample = new ItemStack(Items.IRON_INGOT);
        int space = Integer.MAX_VALUE - 17;
        assertEquals(space, MachineItemTransfer.simulateInsert(target, sample, Integer.MAX_VALUE));
        assertEquals(17, storage.getCount(), "Simulation must not modify the real inventory");
        assertEquals(space, MachineItemTransfer.insertCount(target, sample, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, storage.getCount());
        assertEquals(0, MachineItemTransfer.simulateInsert(target, sample, Integer.MAX_VALUE));
        assertEquals(0, MachineItemTransfer.insertCount(target, sample, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, storage.getCount());
    }

    @Test void slotBindingNbtAndInputPermissionsStayIntact() {
        var first = new ItemStorageCoFH();
        var selected = new ItemStorageCoFH();
        var output = new ItemStorageCoFH();
        var sample = new ItemStack(Items.IRON_INGOT);
        sample.getOrCreateTag().putString("grade", "A");
        selected.setItemStack(sample.copyWithCount(63));
        var exposed = new ManagedItemHandler(null, List.of(first, selected), List.of(output));
        var target = new SingleSlotHandler(exposed, 1);
        assertEquals(0, MachineItemTransfer.simulateInsert(target, new ItemStack(Items.IRON_INGOT), Integer.MAX_VALUE));
        assertEquals(1, MachineItemTransfer.simulateInsert(target, sample, Integer.MAX_VALUE));
        assertEquals(1, MachineItemTransfer.insertCount(target, sample, Integer.MAX_VALUE));
        assertEquals(64, selected.getCount());
        assertEquals("A", selected.getItemStack().getTag().getString("grade"));
        assertTrue(first.isEmpty());
        var outputTarget = new SingleSlotHandler(exposed, 2);
        assertEquals(0, MachineItemTransfer.simulateInsert(outputTarget, sample, Integer.MAX_VALUE));
        assertEquals(0, MachineItemTransfer.insertCount(outputTarget, sample, Integer.MAX_VALUE));
        assertTrue(output.isEmpty());
        assertEquals(1, sample.getCount());
    }

    @Test void executionRechecksCapacityAndRefundsTheUninsertedQuantity() {
        var storage = new ItemStorageCoFH(64);
        try (var world = new TaskWorld(managed(storage), 0)) {
            BigInteger initial = world.balance;
            // 模拟后、执行前容量缩为 1：扣费回调代替外部组件变化，不改真实传输代码。
            world.afterSpend = () -> storage.setCapacity(1);
            world.tick(1);
            assertEquals(1, storage.getCount());
            assertEquals(BigInteger.valueOf(630), world.refunds);
            assertEquals(initial.subtract(BigInteger.TEN), world.balance);
            world.tick(2);
            assertEquals(1, world.payments);
            assertEquals(1, storage.getCount());
        }
    }

    @Test void partialBalanceAndMissingKnowledgeCannotOverproduceOrCharge() {
        var storage = new ItemStorageCoFH();
        try (var world = new TaskWorld(managed(storage), 0)) {
            world.balance = BigInteger.valueOf(25);
            world.known = false;
            world.tick(1);
            assertTrue(storage.isEmpty());
            assertEquals(0, world.payments);
            world.known = true;
            world.tick(2);
            assertEquals(2, storage.getCount());
            assertEquals(BigInteger.valueOf(5), world.balance);
            world.tick(3);
            assertEquals(2, storage.getCount());
            assertEquals(1, world.payments);
        }
    }

    @Test void partialAcceptingInterfacesRemainBoundedAndSimulationDoesNotDoubleCount() {
        var slow = new ItemStackHandler(1) {
            int executions;
            @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!simulate) {
                    assertTrue(++executions <= 64, "A partial receiver must not cause an unbounded tick loop");
                    super.insertItem(slot, stack.copyWithCount(1), false);
                }
                return stack.copyWithCount(stack.getCount() - 1);
            }
        };
        var sample = new ItemStack(Items.IRON_INGOT);
        assertEquals(64, MachineItemTransfer.insertCount(slow, sample, Integer.MAX_VALUE));
        assertEquals(64, slow.getStackInSlot(0).getCount());
        var multiple = new ItemStackHandler(2);
        assertEquals(10, MachineItemTransfer.simulateInsert(multiple, sample, 10));
        assertTrue(multiple.getStackInSlot(0).isEmpty());
        assertTrue(multiple.getStackInSlot(1).isEmpty());
    }

    private static ManagedItemHandler managed(ItemStorageCoFH input) {
        return new ManagedItemHandler(null, List.of(input), List.of());
    }

    /** 按真实 SavedData → runAll → Forge 端口解析 → CoFH 库存路径执行；只替换世界和外部余额服务。 */
    private static final class TaskWorld implements AutoCloseable {
        private final MinecraftServer server = mock(MinecraftServer.class);
        private final ServerLevel level = mock(ServerLevel.class);
        private final BlockPos pos = new BlockPos(13, 65, 10);
        private final String key = "minecraft:overworld|13|65|10";
        private final UUID owner = UUID.randomUUID();
        private final MachineConfigSavedData data = new MachineConfigSavedData();
        final MachineConfig config = data.getOrCreate(key, owner);
        private final MockedStatic<MachineConfigSavedData> stores = mockStatic(MachineConfigSavedData.class);
        private final MockedStatic<EmcService> emc = mockStatic(EmcService.class, CALLS_REAL_METHODS);
        private final MockedStatic<com.primordial.data.EmcConversionSavedData> changes = mockStatic(com.primordial.data.EmcConversionSavedData.class);
        private final MockedStatic<ValueManager> prices = mockStatic(ValueManager.class);
        private final MockedStatic<BalanceSyncer> sync = mockStatic(BalanceSyncer.class);
        private final MockedStatic<MachineProxyRegistry> proxies = mockStatic(MachineProxyRegistry.class);
        BigInteger balance = BigInteger.TEN.pow(30);
        BigInteger refunds = BigInteger.ZERO;
        int payments;
        boolean known = true;
        Runnable afterSpend = () -> {};

        TaskWorld(IItemHandler exposed, int index) {
            changes.when(() -> com.primordial.data.EmcConversionSavedData.get(server)).thenReturn(new com.primordial.data.EmcConversionSavedData());
            when(server.isSameThread()).thenReturn(true);
            TaskScheduler.dropTimers(key);
            var machine = mock(BlockEntity.class);
            var proxy = ForgeCapabilityProxy.INSTANCE;
            when(server.getLevel(Level.OVERWORLD)).thenReturn(level);
            when(level.isLoaded(pos)).thenReturn(true);
            when(level.getBlockState(pos)).thenReturn(Blocks.FURNACE.defaultBlockState());
            when(level.getBlockEntity(pos)).thenReturn(machine);
            when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
            when(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP))
                    .thenReturn(LazyOptional.of(() -> exposed));
            proxies.when(() -> MachineProxyRegistry.resolve(machine)).thenReturn(proxy);
            stores.when(() -> MachineConfigSavedData.get(server)).thenReturn(data);
            var sample = config.itemIn[0];
            sample.filter = new ItemStack(Items.IRON_INGOT);
            sample.enabled = true;
            sample.mode = SampleMode.KEEP_LEVEL;
            sample.setTargets(List.of(new MachineTarget(Direction.UP.get3DDataValue(), index,
                    proxy.itemSlotKey(machine, Direction.UP, exposed, index))));
            config.itemInEnabled = true;
            prices.when(() -> ValueManager.itemValueEmc(sample.filter, server)).thenReturn(BigInteger.TEN);
            emc.when(() -> EmcService.hasKnowledge(server, owner, sample.filter)).thenAnswer(call -> known);
            emc.when(() -> EmcService.balance(server, owner)).thenAnswer(call -> balance);
            emc.when(() -> EmcService.spend(eq(server), eq(owner), any())).thenAnswer(call -> {
                BigInteger amount = call.getArgument(2);
                if (amount.signum() <= 0 || balance.compareTo(amount) < 0) return false;
                balance = balance.subtract(amount);
                payments++;
                afterSpend.run();
                return true;
            });
            emc.when(() -> EmcService.add(eq(server), eq(owner), any())).thenAnswer(call -> {
                BigInteger amount = call.getArgument(2);
                refunds = refunds.add(amount);
                balance = balance.add(amount);
                return null;
            });
        }

        void tick(long time) {
            when(level.getGameTime()).thenReturn(time);
            TaskExecutor.runAll(server);
        }

        @Override public void close() {
            proxies.close();
            sync.close();
            prices.close();
            emc.close();
            stores.close();
            changes.close();
            TaskScheduler.dropTimers(key);
        }
    }
}
