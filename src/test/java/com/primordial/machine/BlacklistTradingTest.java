/**
 * 【文件职责】以真实价表、黑名单和 EMC 结算验证禁用/恢复及自动化回收边界。
 * 【变更记录 #1】2026-10-13 23:31:00（第 1 次备注 · 1.2.0-blacklist-system）
 * - 删除：无。新增：购买/卖出、AE2/RS 模拟执行、黑洞余量与价格/知识保留回归。
 * - 修改：只替换服务器账户、注册和世界存储边界；使用隔离配置目录写入真实定价。
 * - 用途：黑名单不能吞资源或扣款，移除黑名单恢复原定价和已学知识。
 */
package com.primordial.machine;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.blockentity.MekanismGasSink;
import com.primordial.blockentity.VoidHandlers;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.config.ResourceBlacklistRules;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.service.TransmutationNetworkTrade;
import com.primordial.value.ValueManager;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fml.loading.FMLPaths;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BlacklistTradingTest {
    @TempDir(factory = com.primordial.util.RetryingTestTempDirFactory.class, cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) Path directory;
    private CommentedConfig previous;
    private Path previousConfig;
    private java.lang.reflect.Field configPath;
    private final ResourceLocation water = new ResourceLocation("minecraft:water");
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @BeforeEach void setup() throws Exception {
        configPath = FMLPaths.class.getDeclaredField("absolutePath"); configPath.setAccessible(true);
        previousConfig = FMLPaths.CONFIGDIR.get(); configPath.set(FMLPaths.CONFIGDIR, directory);
        var field = ForgeConfigSpec.class.getDeclaredField("childConfig"); field.setAccessible(true);
        previous = (CommentedConfig) field.get(PrimordialServerConfig.SPEC);
        PrimordialServerConfig.SPEC.setConfig(CommentedConfig.inMemory());
        ResourceBlacklistRules.invalidate(); ValueManager.clear();
    }
    @AfterEach void restore() throws Exception {
        ValueManager.clear(); PrimordialServerConfig.SPEC.setConfig(previous); ResourceBlacklistRules.invalidate();
        configPath.set(FMLPaths.CONFIGDIR, previousConfig);
    }

    @Test void blockedTradesAndNetworkRequestsDoNotMoveResourcesOrEmcAndRemovingRuleRestoresKnowledgeAndPrices() {
        var gasId = new ResourceLocation("mekanism:hydrogen");
        assertTrue(ValueManager.setFluidValue(water, EmcAmount.parse("2000")));
        assertTrue(ValueManager.setGasValue(gasId, BigInteger.valueOf(3)));
        try (var account = new EmcSettlementTest.Account(BigInteger.valueOf(1000));
             var saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS)) {
            var level = mock(ServerLevel.class); when(account.server.overworld()).thenReturn(level);
            var knowledge = new MediaKnowledgeSavedData();
            knowledge.learn(account.owner, false, water); knowledge.learn(account.owner, true, gasId);
            saved.when(() -> MediaKnowledgeSavedData.get(account.server)).thenReturn(knowledge);
            PrimordialServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:water"));
            PrimordialServerConfig.GAS_BLACKLIST.set(List.of("mekanism:*"));
            assertEquals(0, EmcService.purchaseFluid(account.server, account.owner, Fluids.WATER, 10, n -> fail("fluid delivered")));
            assertEquals(0, EmcService.reclaimFluid(account.server, account.owner, Fluids.WATER, 10, n -> fail("fluid consumed")));
            assertEquals(0, EmcService.purchaseGas(account.server, account.owner, gasId, 10, n -> fail("gas delivered")));
            assertEquals(0, EmcService.reclaimGas(account.server, account.owner, gasId, 10, n -> fail("gas consumed")));
            var input = new FluidStack(Fluids.WATER, 1000);
            assertFalse(TransmutationNetworkTrade.canInsertFluid(account.server, account.owner, input));
            for (boolean execute : new boolean[]{false, true}) {
                assertEquals(0, TransmutationNetworkTrade.extractFluid(account.server, account.owner, Fluids.WATER, 1000, execute));
                assertEquals(0, TransmutationNetworkTrade.insertFluid(account.server, account.owner, input, 1000, execute));
            }
            assertTrue(TransmutationNetworkTrade.fluidCatalog(account.server, account.owner).stacks().stream().noneMatch(s -> s.getFluid() == Fluids.WATER));
            assertEquals(EmcAmount.parse("1000"), account.total());
            assertEquals(0, account.payments); assertEquals(0, account.credits);
            assertEquals(1000, input.getAmount());
            assertTrue(knowledge.knows(account.owner, false, water)); assertTrue(knowledge.knows(account.owner, true, gasId));
            assertEquals(EmcAmount.parse("2000"), ValueManager.fluidSnapshot().get(water.toString()));
            assertEquals(BigInteger.valueOf(3), ValueManager.gasSnapshot().get(gasId.toString()));
            PrimordialServerConfig.FLUID_BLACKLIST.set(List.of()); PrimordialServerConfig.GAS_BLACKLIST.set(List.of());
            assertEquals(10, EmcService.purchaseFluid(account.server, account.owner, Fluids.WATER, 10, n -> n));
            assertEquals(10, EmcService.purchaseGas(account.server, account.owner, gasId, 10, n -> n));
            assertEquals(5, EmcService.reclaimFluid(account.server, account.owner, Fluids.WATER, 5, n -> n));
            assertEquals(EmcAmount.parse("960"), account.total());
            when(level.getGameTime()).thenReturn(20L);
            assertTrue(TransmutationNetworkTrade.fluidCatalog(account.server, account.owner).stacks().stream().anyMatch(s -> s.getFluid() == Fluids.WATER));
        }
    }

    @Test void blackHoleReturnsAllBlockedFluidAndGasWithoutBudgetOrQueueChanges() {
        try (var registry = new GasTestRegistry(); var account = new EmcSettlementTest.Account(BigInteger.TEN)) {
            PrimordialServerConfig.FLUID_BLACKLIST.set(List.of("minecraft:*"));
            PrimordialServerConfig.GAS_BLACKLIST.set(List.of("mekanism:*"));
            var hole = mock(BlackholeBlockEntity.class);
            var level = mock(ServerLevel.class); when(level.getServer()).thenReturn(account.server);
            when(hole.getOwner()).thenReturn(account.owner); when(hole.getLevel()).thenReturn(level);
            var fluids = new VoidHandlers.VoidFluidHandler(hole);
            var input = new FluidStack(Fluids.WATER, 1000);
            assertFalse(fluids.isFluidValid(0, input));
            assertEquals(0, fluids.fill(input, FluidAction.SIMULATE));
            assertEquals(0, fluids.fill(input, FluidAction.EXECUTE));
            var gases = new MekanismGasSink(hole);
            var gas = new GasStack(registry.gas, 1000);
            assertFalse(gases.isValid(0, gas));
            assertEquals(1000, gases.insertChemical(0, gas, Action.SIMULATE).getAmount());
            assertEquals(1000, gases.insertChemical(0, gas, Action.EXECUTE).getAmount());
            assertEquals(1000, input.getAmount()); assertEquals(1000, gas.getAmount());
            verify(hole, never()).remainingFluidBudget(); verify(hole, never()).remainingGasBudget();
            verify(hole, never()).queueEmc(any());
            assertEquals(EmcAmount.parse("10"), account.total());
        }
    }
}
