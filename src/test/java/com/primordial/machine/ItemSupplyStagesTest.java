/**
 * 【文件职责】在真实机器及玩家背包任务中验证物品阶段阻止生成和扣费。
 * 【变更记录 #1】2026-10-02 19:15:57（第 1 次备注 · 1.2.0-item-supply-stages）
 * - 删除：无。
 * - 新增：固定数量和持续补满两模式的解锁、撤销、保留库存与回收测试。
 * - 修改：任务、阶段和 EMC 结算运行真实实现；世界、存储入口和 ProjectE 账户使用替身。
 * - 用途：证明机器离线主人阶段与背包补充均使用统一门禁，撤销不会吞掉原物品。
 */
/**
 * 【变更记录 #2】2026-10-02 19:17:39（第 2 次备注 · 1.2.0-item-supply-stages）
 * - 删除：对账户替身初始化也生效的 getPlayerList 零调用断言。
 * - 新增：空在线玩家列表及主人离线的显式检查。
 * - 修改：离线验证依据玩家实际缺席，而非禁止账户服务查询玩家列表。
 * - 用途：消除测试准备过程的误报，保留实际任务库存与 EMC 断言。
 */
package com.primordial.machine;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.data.MachineTarget;
import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.data.PlayerStagesSavedData;
import com.primordial.data.SampleMode;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.task.PlayerInventoryTask;
import com.primordial.task.TaskExecutor;
import com.primordial.task.TaskScheduler;
import com.primordial.value.ValueManager;
import java.math.BigInteger;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemSupplyStagesTest {
    private static final ResourceLocation STAGE = new ResourceLocation("pack:diamond");
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @ParameterizedTest @EnumSource(SampleMode.class)
    void playerInventoryGrantAndRevokeControlBothModesWithoutDeletingExistingItems(SampleMode mode) {
        try (var account = new EmcSettlementTest.Account(BigInteger.valueOf(10000));
             var config = mockStatic(PrimordialServerConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS);
             var stores = mockStatic(PlayerInventoryConfigSavedData.class);
             var prices = mockStatic(ValueManager.class)) {
            var level = mock(ServerLevel.class);
            when(account.server.overworld()).thenReturn(level);
            var stages = new PlayerStagesSavedData();
            saved.when(() -> PlayerStagesSavedData.get(account.server)).thenReturn(stages);
            config.when(PrimordialServerConfig::itemStageRequirements).thenReturn(List.of("minecraft:diamond=pack:diamond"));
            prices.when(() -> ValueManager.itemValueEmc(any(), eq(account.server))).thenReturn(BigInteger.TEN);
            var player = mock(ServerPlayer.class);
            var inventory = new Inventory(player);
            var players = mock(PlayerList.class);
            when(account.server.getPlayerList()).thenReturn(players);
            when(players.getPlayers()).thenReturn(List.of(player));
            when(player.getUUID()).thenReturn(account.owner);
            when(player.getInventory()).thenReturn(inventory);
            when(player.level()).thenReturn(level);
            var data = new PlayerInventoryConfigSavedData();
            stores.when(() -> PlayerInventoryConfigSavedData.get(account.server)).thenReturn(data);
            var filter = new ItemStack(Items.DIAMOND);
            filter.getOrCreateTag().putString("sample", "retained");
            data.set(account.owner, 0, filter, 3, 1, mode, true);
            String key = "player:" + account.owner;
            try {
                when(level.getGameTime()).thenReturn(1L);
                PlayerInventoryTask.run(account.server);
                assertTrue(inventory.getItem(0).isEmpty());
                assertEquals(0, account.payments);
                prices.verifyNoInteractions();
                stages.grant(account.owner, STAGE);
                when(level.getGameTime()).thenReturn(2L);
                PlayerInventoryTask.run(account.server);
                int expected = mode == SampleMode.KEEP_LEVEL ? 64 : 3;
                assertEquals(expected, inventory.getItem(0).getCount());
                assertEquals(filter.getTag(), inventory.getItem(0).getTag());
                assertEquals(BigInteger.valueOf(10000 - 10L * expected), account.balance);
                stages.revoke(account.owner, STAGE);
                inventory.setItem(0, filter.copyWithCount(1));
                when(level.getGameTime()).thenReturn(3L);
                PlayerInventoryTask.run(account.server);
                assertEquals(1, inventory.getItem(0).getCount());
                assertEquals(1, account.payments);
                assertTrue(data.get(account.owner, 0).enabled());
                inventory.setItem(0, ItemStack.EMPTY);
                when(level.getGameTime()).thenReturn(4L);
                PlayerInventoryTask.run(account.server);
                assertTrue(inventory.getItem(0).isEmpty());
                assertEquals(1, account.payments);
            } finally { TaskScheduler.dropTimers(key); }
        }
    }

    @ParameterizedTest @EnumSource(SampleMode.class)
    void offlineMachineOwnerStagesControlSupplyButNeverPreventReclamation(SampleMode mode) {
        try (var account = new EmcSettlementTest.Account(BigInteger.valueOf(10000));
             var config = mockStatic(PrimordialServerConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS);
             var stores = mockStatic(MachineConfigSavedData.class);
             var proxies = mockStatic(MachineProxyRegistry.class);
             var prices = mockStatic(ValueManager.class)) {
            var level = mock(ServerLevel.class);
            when(account.server.overworld()).thenReturn(level);
            when(account.server.getLevel(Level.OVERWORLD)).thenReturn(level);
            var players = mock(PlayerList.class);
            when(account.server.getPlayerList()).thenReturn(players);
            when(players.getPlayers()).thenReturn(List.of());
            var stages = new PlayerStagesSavedData();
            saved.when(() -> PlayerStagesSavedData.get(account.server)).thenReturn(stages);
            config.when(PrimordialServerConfig::itemStageRequirements).thenReturn(List.of("minecraft:diamond=pack:diamond"));
            prices.when(() -> ValueManager.itemValueEmc(any(), eq(account.server))).thenReturn(BigInteger.TEN);
            var machine = mock(BlockEntity.class);
            var pos = new BlockPos(12, 64, 12);
            String key = "minecraft:overworld|12|64|12";
            var items = new ItemStackHandler(1);
            var proxy = ForgeCapabilityProxy.INSTANCE;
            when(level.isLoaded(pos)).thenReturn(true);
            when(level.getBlockState(pos)).thenReturn(Blocks.FURNACE.defaultBlockState());
            when(level.getBlockEntity(pos)).thenReturn(machine);
            when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
            when(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)).thenReturn(LazyOptional.of(() -> items));
            proxies.when(() -> MachineProxyRegistry.resolve(machine)).thenReturn(proxy);
            var data = new MachineConfigSavedData();
            stores.when(() -> MachineConfigSavedData.get(account.server)).thenReturn(data);
            var cfg = data.getOrCreate(key, account.owner);
            cfg.itemInEnabled = true;
            var rule = cfg.itemIn[0];
            rule.filter = new ItemStack(Items.DIAMOND);
            rule.enabled = true;
            rule.mode = mode;
            rule.threshold = 3;
            rule.intervalTicks = 1;
            rule.setTargets(List.of(new MachineTarget(1, 0, proxy.itemSlotKey(machine, Direction.UP, items, 0))));
            try {
                when(level.getGameTime()).thenReturn(1L);
                TaskExecutor.runAll(account.server);
                assertTrue(items.getStackInSlot(0).isEmpty());
                assertEquals(0, account.payments);
                prices.verifyNoInteractions();
                stages.grant(account.owner, STAGE);
                when(level.getGameTime()).thenReturn(2L);
                TaskExecutor.runAll(account.server);
                int expected = mode == SampleMode.KEEP_LEVEL ? 64 : 3;
                assertEquals(expected, items.getStackInSlot(0).getCount());
                assertEquals(BigInteger.valueOf(10000 - 10L * expected), account.balance);
                stages.revoke(account.owner, STAGE);
                items.setStackInSlot(0, new ItemStack(Items.DIAMOND));
                when(level.getGameTime()).thenReturn(3L);
                TaskExecutor.runAll(account.server);
                assertEquals(1, items.getStackInSlot(0).getCount());
                assertEquals(1, account.payments);
                cfg.itemInEnabled = false;
                cfg.itemOutEnabled = true;
                cfg.itemOut[0] = rule;
                account.emc.when(() -> EmcService.itemRecoveryPrice(any(), eq(account.server))).thenReturn(EmcAmount.parse("10"));
                when(level.getGameTime()).thenReturn(4L);
                TaskExecutor.runAll(account.server);
                assertTrue(items.getStackInSlot(0).isEmpty());
                assertEquals(BigInteger.valueOf(10000 - 10L * expected + 10), account.balance);
                assertNull(players.getPlayer(account.owner));
            } finally { TaskScheduler.dropTimers(key); }
        }
    }
}
