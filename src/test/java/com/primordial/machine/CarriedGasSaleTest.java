/**
 * 【文件职责】用 Mekanism 真实限流储罐验证转化桌卖气体的最后一批结算。
 * 【变更记录 #1】2026-10-02 18:40:00（第 1 次备注 · 1.2.0-gas-sale-remainder）
 * - 删除：无。
 * - 新增：512000 mB 尾批、1 mB 尾量、满罐逐批清空及零价不抽取的回归测试。
 * - 修改：仅注册表、游戏玩家和账户持久化使用替身；抽取、气体引用变化与 reclaim 计费运行真实实现。
 * - 用途：防止抽空后气体引用变空导致不保存容器、不入账或学习错误种类。
 */
/**
 * 【变更记录 #2】2026-10-02 18:54:32（第 2 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：关闭气体卖出时真实罐内容不变，以及仅关闭买入仍允许卖出结算的测试。
 * - 修改：沿用真实限流罐和结算回调，配置只模拟四个方向的布尔结果。
 * - 用途：确保卖出开关不被转化桌绕过、两个方向相互独立。
 */
/**
 * 【变更记录 #3】2026-10-13 01:53:34（第 3 次备注 · 1.2.0-media-feedback）
 * - 删除：卖满气罐需 16 次的旧断言；更新为一次卖空、再次点击不能重复入账。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
package com.primordial.machine;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.service.ResourceKnowledge;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.chemical.item.ChemicalTankRateLimitChemicalTank.GasTankRateLimitChemicalTank;
import mekanism.common.tier.ChemicalTankTier;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.UUID;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CarriedGasSaleTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void finalRateLimitedBatchIsSoldAndLearnsOriginalGas() throws Exception {
        try (var f = new Sale(512_000)) {
            assertTrue(f.sell());
            f.assertSettled(512_000, 1);
        }
    }

    @Test void finalSingleMillibucketCanBeSold() throws Exception {
        try (var f = new Sale(1)) {
            assertTrue(f.sell());
            f.assertSettled(1, 1);
        }
    }

    @Test void fullUltimateTankSellsAllAtOnceWithoutDuplicateCredit() throws Exception {
        try (var f = new Sale(8_192_000)) {
            assertTrue(f.sell());
            f.assertSettled(8_192_000, 1);
            assertFalse(f.sell());
            f.assertSettled(8_192_000, 1);
        }
    }

    @Test void unpricedGasStaysInContainerWithoutLearningOrCredit() throws Exception {
        try (var f = new Sale(512_000)) {
            f.emc.when(() -> EmcService.gasPrice(f.registry.id)).thenReturn(EmcAmount.of(BigInteger.ZERO));
            assertFalse(f.sell());
            assertEquals(512_000, f.tank.getStored());
            assertEquals(EmcAmount.of(BigInteger.ZERO), f.credited);
            verify(f.menu, never()).setCarried(any());
            f.knowledge.verifyNoInteractions();
        }
    }

    @Test void disablingGasSalesLeavesActualTankAndAccountUnchanged() throws Exception {
        try (var f = new Sale(512_000);
             var config = mockStatic(com.primordial.config.PrimordialServerConfig.class)) {
            config.when(() -> com.primordial.config.PrimordialServerConfig.mediaSaleDisabled(true)).thenReturn(true);
            assertFalse(f.sell());
            assertEquals(512_000, f.tank.getStored());
            assertEquals(EmcAmount.of(BigInteger.ZERO), f.credited);
            verifyNoInteractions(f.menu);
            f.knowledge.verifyNoInteractions();
        }
    }

    @Test void disablingGasPurchaseStillAllowsSaleAndLearning() throws Exception {
        try (var f = new Sale(1);
             var config = mockStatic(com.primordial.config.PrimordialServerConfig.class)) {
            config.when(() -> com.primordial.config.PrimordialServerConfig.mediaPurchaseDisabled(true)).thenReturn(true);
            assertTrue(f.sell());
            f.assertSettled(1, 1);
        }
    }

    @Test void disabledBlackHoleGasSaleReturnsAllInputInSimulationAndExecution() {
        try (var registry = new GasTestRegistry();
             var config = mockStatic(com.primordial.config.PrimordialServerConfig.class)) {
            config.when(() -> com.primordial.config.PrimordialServerConfig.mediaSaleDisabled(true)).thenReturn(true);
            var be = mock(com.primordial.blockentity.BlackholeBlockEntity.class);
            var sink = new com.primordial.blockentity.MekanismGasSink(be);
            var input = new GasStack(registry.gas, 512_000);
            assertFalse(sink.isValid(0, input));
            assertEquals(512_000, sink.insertChemical(0, input, mekanism.api.Action.SIMULATE).getAmount());
            assertEquals(512_000, sink.insertChemical(0, input, mekanism.api.Action.EXECUTE).getAmount());
            assertEquals(512_000, input.getAmount());
            verifyNoInteractions(be);
        }
    }

    private static final class Sale implements AutoCloseable {
        final GasTestRegistry registry = new GasTestRegistry();
        final GasTankRateLimitChemicalTank tank = new GasTankRateLimitChemicalTank(ChemicalTankTier.ULTIMATE, null);
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final TransmutationContainer menu = mock(TransmutationContainer.class);
        final ItemStack copy = mock(ItemStack.class);
        final UUID owner = UUID.randomUUID();
        final MockedStatic<MekanismGasCompat> compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS);
        final MockedStatic<EmcService> emc = mockStatic(EmcService.class, CALLS_REAL_METHODS);
        final MockedStatic<ResourceKnowledge> knowledge = mockStatic(ResourceKnowledge.class);
        EmcAmount credited = EmcAmount.of(BigInteger.ZERO);

        Sale(long amount) throws Exception {
            tank.setStack(new GasStack(registry.gas, amount));
            when(server.isSameThread()).thenReturn(true);
            when(player.getUUID()).thenReturn(owner);
            var serverField = ServerPlayer.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(player, server);
            player.containerMenu = menu;
            when(copy.getCapability(Capabilities.GAS_HANDLER)).thenReturn(LazyOptional.of(() -> tank));
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            emc.when(() -> EmcService.gasPrice(registry.id)).thenReturn(EmcAmount.of(BigInteger.valueOf(3)));
            emc.when(() -> EmcService.credit(eq(server), eq(owner), any(EmcAmount.class))).thenAnswer(call -> {
                credited = credited.add(call.getArgument(2));
                return null;
            });
        }

        boolean sell() { return MekanismGasCompat.sellCarriedGas(player, copy); }

        void assertSettled(long amount, int batches) {
            assertTrue(tank.isEmpty());
            assertEquals(EmcAmount.of(BigInteger.valueOf(amount * 3)), credited);
            verify(menu, times(batches)).setCarried(copy);
            verify(menu, times(batches)).broadcastChanges();
            knowledge.verify(() -> ResourceKnowledge.learnMedia(server, owner,
                    ResourceKnowledge.Kind.GAS, registry.id), times(batches));
            knowledge.verifyNoMoreInteractions();
        }

        @Override public void close() {
            knowledge.close(); emc.close(); compat.close(); registry.close();
        }
    }
}
