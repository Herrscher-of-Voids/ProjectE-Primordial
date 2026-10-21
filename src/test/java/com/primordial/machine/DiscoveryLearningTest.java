/**
 * 【文件职责】验证真实卖入学习系统的自动化、失败、个人存档及目录刷新边界。
 * 【变更记录 #1】2026-10-14 00:02:00（第 1 次备注 · 1.2.0-discovery-learning-system）
 * - 删除：无。新增：黑洞气体/流体模拟与部分接收、拒收、失败交易、网络学习/遗忘回归。
 * - 修改：真实 GasStack/FluidStack、知识存档与交易入口执行，替换注册、服务器和外部结算边界。
 * - 用途：验证实际接受 1 mB 才学习，精确 EMC、玩家/类别隔离、重载和同 tick 目录更新。
 */
package com.primordial.machine;

import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.blockentity.MekanismGasSink;
import com.primordial.blockentity.VoidHandlers;
import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.service.*;
import com.primordial.value.ValueManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscoveryLearningTest {
    private static final ResourceLocation WATER = new ResourceLocation("minecraft:water");
    private static final EmcAmount PRICE = EmcAmount.parse("1/3");
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void gasBlackholeLearnsOnlyItsOwnerAfterActualPartialAcceptanceAndPersistsKnowledge() {
        try (var f = new Fixture()) {
            var sink = new MekanismGasSink(f.hole);
            var input = new GasStack(f.registry.gas, Long.MAX_VALUE);
            assertFalse(ResourceKnowledge.allowsMedia(f.server, f.owner, ResourceKnowledge.Kind.GAS, f.registry.id));
            assertEquals(Long.MAX_VALUE - 1, sink.insertChemical(0, input, Action.SIMULATE).getAmount());
            assertFalse(f.known.knows(f.owner, true, f.registry.id));
            assertEquals(EmcAmount.ZERO, f.pending.get());
            assertEquals(1, f.budget.get());
            assertEquals(Long.MAX_VALUE - 1, sink.insertChemical(0, input, Action.EXECUTE).getAmount());
            assertEquals(Long.MAX_VALUE, input.getAmount());
            assertEquals(PRICE, f.pending.get());
            assertTrue(ResourceKnowledge.allowsMedia(f.server, f.owner, ResourceKnowledge.Kind.GAS, f.registry.id));
            assertFalse(f.known.knows(f.other, true, f.registry.id));
            assertFalse(f.known.knows(f.owner, false, f.registry.id));
            assertEquals(Long.MAX_VALUE, sink.insertChemical(0, input, Action.EXECUTE).getAmount());
            assertEquals(PRICE, f.pending.get());
            f.known = MediaKnowledgeSavedData.load(f.known.save(new CompoundTag()));
            assertTrue(ResourceKnowledge.allowsMedia(f.server, f.owner, ResourceKnowledge.Kind.GAS, f.registry.id));
            f.settlement.verifyNoInteractions(); // 黑洞只排队，既有 tick 统一入账。
        }
    }

    @Test void fluidBlackholeSimulationDoesNotLearnButOneAcceptedMbRestoresForgottenKnowledge() {
        try (var f = new Fixture()) {
            var sink = new VoidHandlers.VoidFluidHandler(f.hole);
            var input = new FluidStack(Fluids.WATER, 1000);
            f.known.forget(f.owner, false, WATER);
            assertEquals(1, sink.fill(input, FluidAction.SIMULATE));
            assertTrue(f.known.isForgotten(f.owner, false, WATER));
            assertEquals(EmcAmount.ZERO, f.pending.get());
            assertEquals(1, sink.fill(input, FluidAction.EXECUTE));
            assertEquals(1000, input.getAmount());
            assertEquals(PRICE, f.pending.get());
            assertTrue(f.known.knows(f.owner, false, WATER));
            assertFalse(f.known.isForgotten(f.owner, false, WATER));
            assertFalse(f.known.knows(f.other, false, WATER));
            assertEquals(0, sink.fill(input, FluidAction.EXECUTE));
            assertEquals(PRICE, f.pending.get());
        }
    }

    @Test void rejectedBlackholeTransfersNeverLearnQueueOrSpendBudget() {
        try (var f = new Fixture()) {
            var gases = new MekanismGasSink(f.hole);
            var fluids = new VoidHandlers.VoidFluidHandler(f.hole);
            var gas = new GasStack(f.registry.gas, 10);
            var fluid = new FluidStack(Fluids.WATER, 10);
            assertEquals(10, gases.insertChemical(1, gas, Action.EXECUTE).getAmount());
            when(f.server.isSameThread()).thenReturn(false);
            assertEquals(10, gases.insertChemical(0, gas, Action.EXECUTE).getAmount());
            assertEquals(0, fluids.fill(fluid, FluidAction.EXECUTE));
            when(f.server.isSameThread()).thenReturn(true);
            when(f.hole.getOwner()).thenReturn(null);
            assertEquals(10, gases.insertChemical(0, gas, Action.EXECUTE).getAmount());
            assertEquals(0, fluids.fill(fluid, FluidAction.EXECUTE));
            when(f.hole.getOwner()).thenReturn(f.owner);
            f.emc.when(() -> EmcService.gasPrice(f.registry.id)).thenReturn(EmcAmount.ZERO);
            f.emc.when(() -> EmcService.fluidPrice(Fluids.WATER)).thenReturn(EmcAmount.ZERO);
            assertEquals(10, gases.insertChemical(0, gas, Action.EXECUTE).getAmount());
            assertEquals(0, fluids.fill(fluid, FluidAction.EXECUTE));
            assertFalse(f.known.isDirty());
            assertEquals(EmcAmount.ZERO, f.pending.get());
            assertEquals(1, f.budget.get());
            verify(f.hole, never()).queueEmc(any());
            f.settlement.verifyNoInteractions();
        }
    }

    @Test void failedOrZeroSalesDoNotTeachAndPartialSalesCreditOnlyActualUnits() {
        try (var f = new Fixture()) {
            assertEquals(0, EmcService.reclaimFluid(f.server, f.owner, Fluids.WATER, 1000, n -> 0));
            assertEquals(0, EmcService.reclaimGas(f.server, f.owner, f.registry.id, 1000, n -> 0));
            assertFalse(f.known.isDirty());
            assertThrows(IllegalStateException.class, () -> EmcService.reclaimGas(f.server, f.owner,
                    f.registry.id, 1000, n -> { throw new IllegalStateException("failed container commit"); }));
            assertFalse(f.known.isDirty());
            f.settlement.clearInvocations();
            assertEquals(1, EmcService.reclaimFluid(f.server, f.owner, Fluids.WATER, 1000, n -> 1));
            assertTrue(f.known.knows(f.owner, false, WATER));
            assertFalse(f.known.knows(f.owner, true, f.registry.id));
            assertEquals(2, EmcService.reclaimGas(f.server, f.owner, f.registry.id, 1000, n -> 2));
            assertTrue(f.known.knows(f.owner, true, f.registry.id));
            f.settlement.verify(() -> EmcSettlement.credit(f.server, f.owner, PRICE));
            f.settlement.verify(() -> EmcSettlement.credit(f.server, f.owner, PRICE.multiply(2)));
            f.settlement.verifyNoMoreInteractions();
        }
    }

    @Test void networkSimulationCannotTeachAndLearnForgetRelearnRefreshOnlyChangedOwnersCatalogs() {
        try (var f = new Fixture()) {
            var input = new FluidStack(Fluids.WATER, 1000);
            var initial = TransmutationNetworkTrade.fluidCatalog(f.server, f.owner);
            var other = TransmutationNetworkTrade.fluidCatalog(f.server, f.other);
            assertTrue(initial.stacks().isEmpty());
            assertEquals(1000, TransmutationNetworkTrade.insertFluid(f.server, f.owner, input, 1000, false));
            assertFalse(f.known.isDirty());
            assertSame(initial, TransmutationNetworkTrade.fluidCatalog(f.server, f.owner));
            assertEquals(1, TransmutationNetworkTrade.insertFluid(f.server, f.owner, input, 1, true));
            var learned = TransmutationNetworkTrade.fluidCatalog(f.server, f.owner);
            assertEquals(1, learned.stacks().size());
            assertEquals(Fluids.WATER, learned.stacks().get(0).getFluid());
            ResourceKnowledge.learnMedia(f.server, f.owner, ResourceKnowledge.Kind.FLUID, WATER);
            assertSame(learned, TransmutationNetworkTrade.fluidCatalog(f.server, f.owner));
            assertTrue(ResourceKnowledge.forgetMedia(f.server, f.owner, ResourceKnowledge.Kind.FLUID, WATER));
            assertTrue(TransmutationNetworkTrade.fluidCatalog(f.server, f.owner).stacks().isEmpty());
            assertEquals(0, EmcService.purchaseFluid(f.server, f.owner, Fluids.WATER, 1, n -> fail("forgotten purchase")));
            assertEquals(1, EmcService.reclaimFluid(f.server, f.owner, Fluids.WATER, 1, n -> 1));
            assertEquals(1, TransmutationNetworkTrade.fluidCatalog(f.server, f.owner).stacks().size());
            assertSame(other, TransmutationNetworkTrade.fluidCatalog(f.server, f.other));
            assertEquals(1000, input.getAmount());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GasTestRegistry registry = new GasTestRegistry();
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        final BlackholeBlockEntity hole = mock(BlackholeBlockEntity.class);
        final AtomicInteger budget = new AtomicInteger(1);
        final AtomicReference<EmcAmount> pending = new AtomicReference<>(EmcAmount.ZERO);
        MediaKnowledgeSavedData known = new MediaKnowledgeSavedData();
        final MockedStatic<PrimordialServerConfig> config = mockStatic(PrimordialServerConfig.class);
        final MockedStatic<PrimordialKnowledgeConfig> requirements = mockStatic(PrimordialKnowledgeConfig.class);
        final MockedStatic<MediaKnowledgeSavedData> saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS);
        final MockedStatic<ValueManager> values = mockStatic(ValueManager.class);
        final MockedStatic<EmcService> emc = mockStatic(EmcService.class, CALLS_REAL_METHODS);
        final MockedStatic<EmcSettlement> settlement = mockStatic(EmcSettlement.class);
        // 不启动 Forge 物品注册；只替换类别凭证查询，单资源知识逻辑保持真实。
        final MockedStatic<ResourceKnowledge> knowledge = mockStatic(ResourceKnowledge.class, CALLS_REAL_METHODS);
        Fixture() {
            when(server.isSameThread()).thenReturn(true); when(server.overworld()).thenReturn(level);
            when(level.getServer()).thenReturn(server);
            when(hole.getLevel()).thenReturn(level); when(hole.getOwner()).thenReturn(owner);
            when(hole.remainingGasBudget()).thenAnswer(call -> budget.get());
            when(hole.remainingFluidBudget()).thenAnswer(call -> budget.get());
            doAnswer(call -> { budget.addAndGet(-call.<Integer>getArgument(0)); return null; }).when(hole).recordGasAccepted(anyInt());
            doAnswer(call -> { budget.addAndGet(-call.<Integer>getArgument(0)); return null; }).when(hole).recordFluidAccepted(anyInt());
            doAnswer(call -> { pending.set(pending.get().add(call.getArgument(0))); return null; }).when(hole).queueEmc(any());
            requirements.when(PrimordialKnowledgeConfig::required).thenReturn(true);
            knowledge.when(() -> ResourceKnowledge.allows(any(), any(), any(ResourceKnowledge.Kind.class))).thenReturn(false);
            saved.when(() -> MediaKnowledgeSavedData.get(server)).thenAnswer(call -> known);
            emc.when(() -> EmcService.fluidPrice(Fluids.WATER)).thenReturn(PRICE);
            emc.when(() -> EmcService.gasPrice(registry.id)).thenReturn(PRICE);
            values.when(() -> ValueManager.canSellFluid(Fluids.WATER)).thenReturn(true);
            values.when(() -> ValueManager.canBuyFluid(Fluids.WATER)).thenReturn(true);
        }
        @Override public void close() {
            TransmutationNetworkTrade.invalidate(owner); TransmutationNetworkTrade.invalidate(other);
            knowledge.close(); settlement.close(); emc.close(); values.close(); saved.close();
            requirements.close(); config.close(); registry.close();
        }
    }
}
