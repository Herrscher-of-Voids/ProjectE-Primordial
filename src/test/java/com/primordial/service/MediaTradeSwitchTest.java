/**
 * 【文件职责】验证资源交易开关在能力传输及 EMC 结算前生效。
 * 【变更记录 #1】2026-10-02 18:54:32（第 1 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：流体双向独立、气体所有公开交易入口及黑洞流体拒收回归。
 * - 修改：真实业务入口运行，账户/配置替身隔离存档，拒绝时验证回调和能力均未访问。
 * - 用途：防止出现按钮拒绝但自动化仍然执行，或关闭买入连带阻断卖入学习。
 */
package com.primordial.service;

import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.blockentity.VoidHandlers;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.value.ValueManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.function.LongUnaryOperator;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaTradeSwitchTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void realForgeConfigKeysDefaultToEnabledAndEachDirectionCanBeDisabledIndependently() throws Exception {
        var field = net.minecraftforge.common.ForgeConfigSpec.class.getDeclaredField("childConfig");
        field.setAccessible(true);
        var previous = (com.electronwill.nightconfig.core.CommentedConfig) field.get(PrimordialServerConfig.SPEC);
        try {
            PrimordialServerConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            var switches = java.util.List.of(PrimordialServerConfig.FLUID_PURCHASE_ENABLED,
                    PrimordialServerConfig.GAS_PURCHASE_ENABLED, PrimordialServerConfig.FLUID_SALE_ENABLED,
                    PrimordialServerConfig.GAS_SALE_ENABLED);
            for (var option : switches) assertTrue(option.get());
            for (int i = 0; i < switches.size(); i++) {
                switches.get(i).set(false);
                assertEquals(i == 0, PrimordialServerConfig.mediaPurchaseDisabled(false));
                assertEquals(i == 1, PrimordialServerConfig.mediaPurchaseDisabled(true));
                assertEquals(i == 2, PrimordialServerConfig.mediaSaleDisabled(false));
                assertEquals(i == 3, PrimordialServerConfig.mediaSaleDisabled(true));
                switches.get(i).set(true);
            }
        } finally { PrimordialServerConfig.SPEC.setConfig(previous); }
    }

    @Test void fluidBuyAndSaleSwitchesAreIndependentAndBlockedTransfersNeverRun() {
        var server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        var owner = UUID.randomUUID();
        var water = new ResourceLocation("minecraft:water");
        var transfer = mock(LongUnaryOperator.class);
        when(transfer.applyAsLong(1000)).thenReturn(1000L);
        var price = EmcAmount.parse("1");
        try (var config = mockStatic(PrimordialServerConfig.class);
             var knowledge = mockStatic(ResourceKnowledge.class);
             var values = mockStatic(ValueManager.class);
             var settlement = mockStatic(EmcSettlement.class)) {
            values.when(() -> ValueManager.fluidPricePerMB(Fluids.WATER)).thenReturn(price);
            knowledge.when(() -> ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID, water))
                    .thenReturn(true);
            config.when(() -> PrimordialServerConfig.mediaPurchaseDisabled(false)).thenReturn(true);
            assertEquals(0, EmcService.purchaseFluid(server, owner, Fluids.WATER, 1000, transfer));
            verifyNoInteractions(transfer);
            settlement.verifyNoInteractions();
            assertEquals(1000, EmcService.reclaimFluid(server, owner, Fluids.WATER, 1000, transfer));
            settlement.verify(() -> EmcSettlement.credit(server, owner, price.multiply(1000)));
            knowledge.verify(() -> ResourceKnowledge.learnMedia(server, owner, ResourceKnowledge.Kind.FLUID, water));
            clearInvocations(transfer);
            settlement.clearInvocations();
            knowledge.clearInvocations();
            config.when(() -> PrimordialServerConfig.mediaSaleDisabled(false)).thenReturn(true);
            assertEquals(0, EmcService.reclaimFluid(server, owner, Fluids.WATER, 1000, transfer));
            verifyNoInteractions(transfer);
            settlement.verifyNoInteractions();
            knowledge.verifyNoInteractions();
            config.when(() -> PrimordialServerConfig.mediaPurchaseDisabled(false)).thenReturn(false);
            settlement.when(() -> EmcSettlement.purchase(server, owner, price, 1000, transfer)).thenReturn(1000L);
            assertEquals(1000, EmcService.purchaseFluid(server, owner, Fluids.WATER, 1000, transfer));
            settlement.verify(() -> EmcSettlement.purchase(server, owner, price, 1000, transfer));
            assertEquals(price, EmcService.fluidPrice(Fluids.WATER));
        }
    }

    @Test void disabledGasPathsDoNotReadContainerCapabilitiesOrRunTransfers() {
        var player = mock(ServerPlayer.class);
        var container = mock(ItemStack.class);
        try (var config = mockStatic(PrimordialServerConfig.class)) {
            config.when(() -> PrimordialServerConfig.mediaPurchaseDisabled(true)).thenReturn(true);
            config.when(() -> PrimordialServerConfig.mediaSaleDisabled(true)).thenReturn(true);
            assertFalse(MekanismGasCompat.buyCarriedGas(player, container));
            assertFalse(MekanismGasCompat.buySelectedCarriedGas(player, container, new ResourceLocation("mekanism:hydrogen")));
            assertFalse(MekanismGasCompat.sellCarriedGas(player, container));
            assertEquals(0, MekanismGasCompat.supply(null, null, null, new Object(), 0));
            assertEquals(0, MekanismGasCompat.reclaim(null, null, null, new Object(), 0));
            verifyNoInteractions(player, container);
        }
    }

    @Test void blackHoleFluidRejectionIncludesSimulationAndDirectReclamation() {
        var be = mock(BlackholeBlockEntity.class);
        var fluid = new FluidStack(Fluids.WATER, 1000);
        var handler = new VoidHandlers.VoidFluidHandler(be);
        try (var config = mockStatic(PrimordialServerConfig.class)) {
            config.when(() -> PrimordialServerConfig.mediaSaleDisabled(false)).thenReturn(true);
            assertFalse(handler.isFluidValid(0, fluid));
            assertEquals(0, handler.fill(fluid, IFluidHandler.FluidAction.SIMULATE));
            assertEquals(0, handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE));
            assertFalse(VoidHandlers.reclaimFluid(be, fluid));
            assertEquals(1000, fluid.getAmount());
            verifyNoInteractions(be);
        }
    }
}
