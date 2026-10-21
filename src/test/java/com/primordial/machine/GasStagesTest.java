/**
 * 【文件职责】用真实 Mekanism 储罐检查阶段对购买、自动补充、卖入和目录的影响。
 * 【变更记录 #1】2026-10-02 19:08:37（第 1 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：未解锁不转移或扣款、卖入学习、解锁后生成、撤销后停止和目录过滤回归。
 * - 修改：真实气体能力及阶段判断执行，仅注册表、世界与账户结算边界使用替身。
 * - 用途：避免转化桌和机器中的任何一个入口绕过任务阶段。
 */
/**
 * 【变更记录 #2】2026-10-13 01:56:05（第 2 次备注 · 1.2.0-media-feedback）
 * - 删除：无限速买入后仍未满罐的旧假设。
 * - 新增：一次买满和满罐再买拒绝断言；各独立授权路径从部分容量开始。
 * - 修改：继续验证阶段授予、撤销和机器入口，避免满罐掩盖授权检查。
 * - 用途：无限速只影响转化桌，阶段门禁和机器供应数量保持准确。
 */
package com.primordial.machine;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.FluidSampleConfig;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.data.PlayerStagesSavedData;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.service.EmcSettlement;
import com.primordial.service.TransmutationMediaCatalog;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongUnaryOperator;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.chemical.item.ChemicalTankRateLimitChemicalTank.GasTankRateLimitChemicalTank;
import mekanism.common.tier.ChemicalTankTier;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GasStagesTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void allGasPurchasePathsRespectGrantAndRevokeWhileSaleStillTeachesKnowledge() throws Exception {
        try (var registry = new GasTestRegistry();
             var config = mockStatic(PrimordialServerConfig.class);
             var knowledgeConfig = mockStatic(PrimordialKnowledgeConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS);
             var media = mockStatic(MediaKnowledgeSavedData.class);
             var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS);
             var emc = mockStatic(EmcService.class, CALLS_REAL_METHODS);
             var settlement = mockStatic(EmcSettlement.class)) {
            var server = mock(MinecraftServer.class);
            when(server.isSameThread()).thenReturn(true);
            when(server.overworld()).thenReturn(mock(ServerLevel.class));
            var owner = UUID.randomUUID();
            var stages = new PlayerStagesSavedData();
            var knowledge = new MediaKnowledgeSavedData();
            var stage = new ResourceLocation("pack:endgame");
            saved.when(() -> PlayerStagesSavedData.get(server)).thenReturn(stages);
            media.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(knowledge);
            config.when(() -> PrimordialServerConfig.mediaStageRequirements(true))
                    .thenReturn(List.of("mekanism:antimatter=pack:endgame"));
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            compat.when(MekanismGasCompat::gasIds).thenReturn(Set.of(registry.id));
            emc.when(() -> EmcService.gasPrice(registry.id)).thenReturn(EmcAmount.parse("3"));
            var player = mock(ServerPlayer.class);
            when(player.getUUID()).thenReturn(owner);
            var serverField = ServerPlayer.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(player, server);
            player.containerMenu = mock(TransmutationContainer.class);
            var container = mock(ItemStack.class);
            var tank = new GasTankRateLimitChemicalTank(ChemicalTankTier.ULTIMATE, null);
            tank.setStack(new GasStack(registry.gas, 1));
            when(container.getCapability(Capabilities.GAS_HANDLER)).thenReturn(LazyOptional.of(() -> tank));
            var sample = new FluidSampleConfig();
            sample.gasFilter = new GasStack(registry.gas, 1);
            sample.threshold = 1000;

            assertFalse(MekanismGasCompat.buySelectedCarriedGas(player, container, registry.id));
            assertFalse(MekanismGasCompat.buyCarriedGas(player, container));
            assertEquals(0, MekanismGasCompat.supply(server, owner, sample, tank, 0));
            assertEquals(1, tank.getStored());
            settlement.verifyNoInteractions();
            assertTrue(TransmutationMediaCatalog.search(player, true, "", 0).ids().isEmpty());
            assertTrue(MekanismGasCompat.sellCarriedGas(player, container));
            assertTrue(tank.isEmpty());
            assertTrue(knowledge.knows(owner, true, registry.id));
            settlement.verify(() -> EmcSettlement.credit(server, owner, EmcAmount.parse("3")));

            stages.grant(owner, stage);
            settlement.when(() -> EmcSettlement.purchase(eq(server), eq(owner), any(), anyLong(), any()))
                    .thenAnswer(call -> call.<LongUnaryOperator>getArgument(4).applyAsLong(call.getArgument(3)));
            assertEquals(List.of(registry.id.toString()), TransmutationMediaCatalog.search(player, true, "", 0).ids());
            assertTrue(MekanismGasCompat.buySelectedCarriedGas(player, container, registry.id));
            assertEquals(tank.getCapacity(), tank.getStored());
            assertFalse(MekanismGasCompat.buyCarriedGas(player, container));
            tank.setStack(new GasStack(registry.gas, 1));
            assertTrue(MekanismGasCompat.buyCarriedGas(player, container));
            tank.setStack(new GasStack(registry.gas, 1));
            assertEquals(1000, MekanismGasCompat.supply(server, owner, sample, tank, 0));
            long stored = tank.getStored();
            stages.revoke(owner, stage);
            settlement.clearInvocations();
            assertFalse(MekanismGasCompat.buySelectedCarriedGas(player, container, registry.id));
            assertFalse(MekanismGasCompat.buyCarriedGas(player, container));
            assertEquals(0, MekanismGasCompat.supply(server, owner, sample, tank, 0));
            assertEquals(stored, tank.getStored());
            settlement.verifyNoInteractions();
            assertTrue(TransmutationMediaCatalog.search(player, true, "", 0).ids().isEmpty());
        }
    }
}
