/**
 * 【文件职责】在真实任务、FE/流体接口和黑洞持久化链上验证统一结算的业务接入。
 * 【变更记录 #1】2026-09-15 15:24:12（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：机器低价流体/FE、手持物品充能、黑洞分数队列及重载/移除结算测试。
 * - 修改：世界与整数账户用测试替身，任务/网络处理/库存/找零与队列使用生产代码。
 * - 用途：证明新服务被实际消费者调用，防止只测数学工具却漏接真实交易。
 */
package com.primordial.machine;

import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.blockentity.VoidHandlers;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.data.MachineTarget;
import com.primordial.data.SampleMode;
import com.primordial.menu.PlayerInventoryMenu;
import com.primordial.network.C2SPlayerActionPacket;
import com.primordial.service.EmcAmount;
import com.primordial.task.TaskExecutor;
import com.primordial.task.TaskScheduler;
import com.primordial.value.ValueManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmcTransferIntegrationTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void machineFluidAndFeTransactionsShareTheSamePlayerFraction() {
        try (var account = new EmcSettlementTest.Account(BigInteger.valueOf(2));
             var stores = mockStatic(MachineConfigSavedData.class); var proxies = mockStatic(MachineProxyRegistry.class);
             var prices = mockStatic(ValueManager.class)) {
            var level = mock(ServerLevel.class);
            var machine = mock(BlockEntity.class);
            var pos = new BlockPos(3, 64, 3);
            String key = "minecraft:overworld|3|64|3";
            var data = new MachineConfigSavedData();
            var cfg = data.getOrCreate(key, account.owner);
            var proxy = ForgeCapabilityProxy.INSTANCE;
            var tank = new FluidTank(1000);
            var battery = new EnergyStorage(1000, 20);
            when(account.server.getLevel(Level.OVERWORLD)).thenReturn(level);
            when(level.isLoaded(pos)).thenReturn(true);
            when(level.getBlockState(pos)).thenReturn(Blocks.FURNACE.defaultBlockState());
            when(level.getBlockEntity(pos)).thenReturn(machine);
            when(machine.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());
            when(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP)).thenReturn(LazyOptional.of(() -> tank));
            when(machine.getCapability(ForgeCapabilities.ENERGY, Direction.UP)).thenReturn(LazyOptional.of(() -> battery));
            stores.when(() -> MachineConfigSavedData.get(account.server)).thenReturn(data);
            proxies.when(() -> MachineProxyRegistry.resolve(machine)).thenReturn(proxy);
            prices.when(() -> ValueManager.fluidPricePerMB(Fluids.WATER)).thenReturn(EmcAmount.parse("1/1000"));
            cfg.fluidInEnabled = true;
            cfg.fluidIn[0].filter = new FluidStack(Fluids.WATER, 1);
            cfg.fluidIn[0].enabled = true;
            cfg.fluidIn[0].mode = SampleMode.FIXED_RATE;
            cfg.fluidIn[0].threshold = 20;
            cfg.fluidIn[0].intervalTicks = 1;
            cfg.fluidIn[0].setTargets(List.of(new MachineTarget(1, 0, proxy.fluidTankKey(machine, Direction.UP, tank, 0))));
            cfg.energy.supplyEnabled = true;
            cfg.energy.supplyMode = SampleMode.KEEP_LEVEL;
            cfg.energy.supplyPort = new MachineTarget(1, 0, MachineTargetAccess.energyKey(proxy, machine, Direction.UP, battery));
            TaskScheduler.dropTimers(key);
            for (long tick = 1; tick <= 50; tick++) {
                when(level.getGameTime()).thenReturn(tick);
                TaskExecutor.runAll(account.server);
            }
            assertEquals(1000, tank.getFluidAmount());
            assertEquals(1000, battery.getEnergyStored());
            assertEquals(EmcAmount.ZERO, account.total());
            cfg.fluidInEnabled = false;
            cfg.energy.supplyEnabled = false;
            cfg.fluidOutEnabled = true;
            cfg.fluidOut[0] = cfg.fluidIn[0];
            cfg.energy.recoverEnabled = true;
            cfg.energy.recoverMode = SampleMode.KEEP_LEVEL;
            cfg.energy.recoverPort = cfg.energy.supplyPort;
            for (long tick = 51; tick <= 100; tick++) {
                when(level.getGameTime()).thenReturn(tick);
                TaskExecutor.runAll(account.server);
            }
            assertEquals(0, tank.getFluidAmount());
            assertEquals(0, battery.getEnergyStored());
            assertEquals(EmcAmount.parse("2"), account.total());
            // 未定价时抽取入口不得销毁流体。
            tank.fill(new FluidStack(Fluids.WATER, 10), FluidAction.EXECUTE);
            prices.when(() -> ValueManager.fluidPricePerMB(Fluids.WATER)).thenReturn(EmcAmount.ZERO);
            when(level.getGameTime()).thenReturn(101L);
            TaskExecutor.runAll(account.server);
            assertEquals(10, tank.getFluidAmount());
            TaskScheduler.dropTimers(key);
        }
    }

    @Test void heldChargingUsesTheCurrentItemAndNeverCreatesInventoryRules() throws Exception {
        try (var account = new EmcSettlementTest.Account(BigInteger.ONE);
             var rules = mockStatic(com.primordial.data.PlayerInventoryConfigSavedData.class)) {
            var player = mock(ServerPlayer.class);
            var held = mock(ItemStack.class);
            var battery = new EnergyStorage(1000, 20);
            var inventory = mock(net.minecraft.world.entity.player.Inventory.class);
            var serverField = ServerPlayer.class.getDeclaredField("server");
            serverField.setAccessible(true); serverField.set(player, account.server);
            when(player.getUUID()).thenReturn(account.owner);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getMainHandItem()).thenReturn(held);
            when(held.getCapability(ForgeCapabilities.ENERGY)).thenReturn(LazyOptional.of(() -> battery));
            player.containerMenu = mock(PlayerInventoryMenu.class);
            var context = mock(NetworkEvent.Context.class);
            when(context.getSender()).thenReturn(player);
            when(context.enqueueWork(any())).thenAnswer(call -> {
                call.<Runnable>getArgument(0).run(); return CompletableFuture.completedFuture(null);
            });
            // 生产服务负责节流同步；充能入口无需立即发送第二份余额包。
            for (int i = 0; i < 50; i++) C2SPlayerActionPacket.handle(new C2SPlayerActionPacket(2), () -> context);
            assertEquals(1000, battery.getEnergyStored());
            assertEquals(EmcAmount.ZERO, account.total());
            when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
            C2SPlayerActionPacket.handle(new C2SPlayerActionPacket(2), () -> context);
            rules.verifyNoInteractions();
            verify(inventory, never()).setItem(anyInt(), any());
        }
    }

    @Test void blackholePreservesFractionAcrossNbtReloadAndSettlesOnlyOnce() throws Exception {
        try (var account = new EmcSettlementTest.Account(BigInteger.ZERO);
             var prices = mockStatic(ValueManager.class); var config = mockStatic(PrimordialServerConfig.class)) {
            config.when(PrimordialServerConfig::fePerEmc).thenReturn(BigInteger.valueOf(1000));
            config.when(PrimordialServerConfig::blackholeMaxEnergyPerTick).thenReturn(1000);
            config.when(PrimordialServerConfig::blackholeMaxFluidMbPerTick).thenReturn(1000);
            prices.when(() -> ValueManager.fluidPricePerMB(Fluids.WATER)).thenReturn(EmcAmount.parse("1/1000"));
            var be = blackhole(account);
            var fe = new VoidHandlers.VoidEnergyHandler(be);
            var fluids = new VoidHandlers.VoidFluidHandler(be);
            assertEquals(20, fe.receiveEnergy(20, true));
            assertEquals(20, fluids.fill(new FluidStack(Fluids.WATER, 20), FluidAction.SIMULATE));
            assertEquals(EmcAmount.ZERO, savedAmount(be));
            for (int i = 0; i < 25; i++) {
                assertEquals(20, fe.receiveEnergy(20, false));
                assertEquals(20, fluids.fill(new FluidStack(Fluids.WATER, 20), FluidAction.EXECUTE));
            }
            assertEquals(EmcAmount.ONE, savedAmount(be));
            assertEquals(EmcAmount.ZERO, account.total());
            var saved = save(be);
            var restored = blackhole(account);
            restored.load(saved);
            restored.settleBeforeRemoval();
            assertEquals(EmcAmount.ONE, account.total());
            restored.settleBeforeRemoval();
            assertEquals(EmcAmount.ONE, account.total());
            assertEquals(EmcAmount.ZERO, savedAmount(restored));
            // 新输入的 1/50 EMC 即使拆除黑洞也进入玩家找零，而非被方块一起删除。
            assertEquals(20, new VoidHandlers.VoidEnergyHandler(restored).receiveEnergy(20, false));
            restored.settleBeforeRemoval();
            account.reload();
            assertEquals(EmcAmount.parse("51/50"), account.total());
        }
    }

    private BlackholeBlockEntity blackhole(EmcSettlementTest.Account account) throws Exception {
        var be = mock(BlackholeBlockEntity.class, CALLS_REAL_METHODS);
        var field = BlackholeBlockEntity.class.getDeclaredField("buffer"); field.setAccessible(true);
        field.set(be, new java.util.ArrayList<ItemStack>());
        var level = mock(ServerLevel.class);
        when(level.getServer()).thenReturn(account.server);
        when(level.getGameTime()).thenReturn(1L);
        be.setLevel(level);
        doReturn(level).when(be).getLevel();
        doNothing().when(be).puffParticles();
        doNothing().when(be).setChanged();
        var initial = new CompoundTag(); initial.putUUID("owner", account.owner);
        be.load(initial);
        return be;
    }
    private CompoundTag save(BlackholeBlockEntity be) throws Exception {
        var method = BlackholeBlockEntity.class.getDeclaredMethod("saveAdditional", CompoundTag.class);
        method.setAccessible(true);
        var result = new CompoundTag(); method.invoke(be, result); return result;
    }
    private EmcAmount savedAmount(BlackholeBlockEntity be) throws Exception {
        var saved = save(be);
        return saved.contains("pending_emc_n") ? new EmcAmount(new BigInteger(saved.getByteArray("pending_emc_n")),
                new BigInteger(saved.getByteArray("pending_emc_d"))) : EmcAmount.ZERO;
    }
}
