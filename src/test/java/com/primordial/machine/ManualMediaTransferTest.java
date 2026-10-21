/**
 * 【文件职责】用真实 Mekanism 限速储罐验证转化桌手动流体/气体交易。
 * 【变更记录 #1】2026-10-13 01:54:35（第 1 次备注 · 1.2.0-media-feedback）
 * - 删除：无。新增：完整流体模拟/执行、气体买满与余额限制回归。
 * - 修改：储罐和分数结算执行生产实现；只替换账户及注册表启动边界。
 * - 用途：证明手动交易不受自动化速率限制，模拟不改内容，余额不足仍按交付扣款。
 */
package com.primordial.machine;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.service.*;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.chemical.item.ChemicalTankRateLimitChemicalTank.GasTankRateLimitChemicalTank;
import mekanism.common.capabilities.fluid.item.RateLimitFluidHandler.RateLimitFluidTank;
import mekanism.common.tier.ChemicalTankTier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualMediaTransferTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void fluidSimulationAndExecutionBypassRateButRespectCapacityAndIdentity() {
        try (var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS)) {
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            var tank = new RateLimitFluidTank(() -> 10, () -> 1_000_000, null);
            var handler = mock(mekanism.api.fluid.IMekanismFluidHandler.class, CALLS_REAL_METHODS);
            when(handler.getFluidTanks(null)).thenReturn(java.util.List.of(tank));
            var water = new FluidStack(Fluids.WATER, 1_000_000);
            assertEquals(10, tank.fill(water, FluidAction.SIMULATE));
            assertEquals(1_000_000, TransmutationMediaTrade.manualFluid(handler, water, true, FluidAction.SIMULATE));
            assertTrue(tank.isEmpty());
            assertEquals(1_000_000, TransmutationMediaTrade.manualFluid(handler, water, true, FluidAction.EXECUTE));
            assertEquals(0, TransmutationMediaTrade.manualFluid(handler, water, true, FluidAction.EXECUTE));
            assertEquals(0, TransmutationMediaTrade.manualFluid(handler, new FluidStack(Fluids.LAVA, 1_000_000), false, FluidAction.EXECUTE));
            assertEquals(1_000_000, TransmutationMediaTrade.manualFluid(handler, water, false, FluidAction.SIMULATE));
            assertEquals(1_000_000, tank.getFluidAmount());
            assertEquals(1_000_000, TransmutationMediaTrade.manualFluid(handler, water, false, FluidAction.EXECUTE));
            assertTrue(tank.isEmpty());
        }
    }

    private void purchaseGas(BigInteger balance, long expected) throws Exception {
        try (var registry = new GasTestRegistry();
             var account = new EmcSettlementTest.Account(balance);
             var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS);
             var knowledge = mockStatic(ResourceKnowledge.class);
             var config = mockStatic(com.primordial.config.PrimordialServerConfig.class)) {
            var tank = new GasTankRateLimitChemicalTank(ChemicalTankTier.ULTIMATE, null);
            ServerPlayer player = mock(ServerPlayer.class);
            var menu = mock(moze_intel.projecte.gameObjs.container.TransmutationContainer.class);
            ItemStack copy = mock(ItemStack.class);
            var field = ServerPlayer.class.getDeclaredField("server");
            field.setAccessible(true); field.set(player, account.server);
            when(player.getUUID()).thenReturn(account.owner);
            player.containerMenu = menu;
            when(copy.getCapability(Capabilities.GAS_HANDLER)).thenReturn(LazyOptional.of(() -> tank));
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            knowledge.when(() -> ResourceKnowledge.allowsMedia(account.server, account.owner, ResourceKnowledge.Kind.GAS, registry.id)).thenReturn(true);
            account.emc.when(() -> EmcService.gasPrice(registry.id)).thenReturn(EmcAmount.parse("1/1000"));
            assertTrue(MekanismGasCompat.buySelectedCarriedGas(player, copy, registry.id));
            assertEquals(expected, tank.getStored());
            assertEquals(EmcAmount.of(balance).subtract(EmcAmount.parse("1/1000").multiply(expected)), account.total());
            verify(menu).setCarried(copy);
        }
    }

    @Test void gasBuyFillsUltimateTankInOneTransaction() throws Exception {
        purchaseGas(BigInteger.valueOf(10_000), 8_192_000);
    }
    @Test void unlimitedGasRateStillStopsAtAvailableBalance() throws Exception {
        purchaseGas(BigInteger.valueOf(1000), 1_000_000);
    }
}
