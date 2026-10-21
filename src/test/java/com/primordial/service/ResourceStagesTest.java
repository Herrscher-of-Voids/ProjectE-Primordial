/**
 * 【文件职责】验证任务阶段的持久化、规则叠加及知识和实际流体交易门禁。
 * 【变更记录 #1】2026-10-02 19:07:21（第 1 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：UUID 隔离、存档容错、规则更新、凭证不能绕过及拒绝交易无副作用的回归。
 * - 修改：使用真实阶段和知识逻辑，仅替换世界存储及账户边界。
 * - 用途：保证任务奖励不能自动授予知识，撤销阶段后立即停止资源生成。
 */
package com.primordial.service;

import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.data.PlayerStagesSavedData;
import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.value.ValueManager;
import java.util.List;
import java.util.UUID;
import java.util.function.LongUnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResourceStagesTest {
    private static final ResourceLocation WATER = new ResourceLocation("minecraft:water");
    private static final ResourceLocation STAGE = new ResourceLocation("pack:water");
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void stagesPersistPerPlayerAndDuplicateCommandsDoNotDirtyData() {
        var data = new PlayerStagesSavedData();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(data.grant(owner, STAGE));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertFalse(data.grant(owner, STAGE));
        assertFalse(data.revoke(other, STAGE));
        assertFalse(data.isDirty());
        assertThrows(UnsupportedOperationException.class, () -> data.stages(owner).clear());
        var tag = data.save(new CompoundTag());
        var corrupt = new ListTag();
        corrupt.add(StringTag.valueOf("Invalid Stage"));
        corrupt.add(StringTag.valueOf("pack:" + "a".repeat(129)));
        corrupt.add(StringTag.valueOf("pack:valid"));
        tag.getCompound("players").put(other.toString(), corrupt);
        tag.getCompound("players").put("bad uuid", corrupt);
        var loaded = PlayerStagesSavedData.load(tag);
        assertTrue(loaded.has(owner, STAGE));
        assertFalse(loaded.has(other, STAGE));
        assertEquals(1, loaded.stages(other).size());
        assertFalse(loaded.isDirty());
        assertTrue(loaded.revoke(owner, STAGE));
        assertTrue(loaded.stages(owner).isEmpty());
        assertFalse(loaded.save(new CompoundTag()).getCompound("players").contains(owner.toString()));
    }

    @Test void invalidRulesAndUnboundedStageNamesAreRejected() {
        for (String valid : List.of("minecraft:water=pack:water", "mekanism:*=pack:endgame", "water=water"))
            assertTrue(ResourceStages.validRule(valid), valid);
        for (String invalid : List.of("", "=", "minecraft:water", "minecraft:water=", "*=pack:a",
                "minecraft:*=Invalid Stage", "minecraft:water=pack:a=pack:b", "minecraft:water=pack:" + "a".repeat(129)))
            assertFalse(ResourceStages.validRule(invalid), invalid);
        assertFalse(ResourceStages.validRule(null));
        assertFalse(new PlayerStagesSavedData().grant(UUID.randomUUID(), new ResourceLocation("pack", "a".repeat(129))));
    }

    @Test void realForgeRulesDefaultEmptyAndPreserveBothLists() throws Exception {
        var field = net.minecraftforge.common.ForgeConfigSpec.class.getDeclaredField("childConfig");
        field.setAccessible(true);
        var previous = (com.electronwill.nightconfig.core.CommentedConfig) field.get(PrimordialServerConfig.SPEC);
        try {
            PrimordialServerConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            assertTrue(PrimordialServerConfig.mediaStageRequirements(false).isEmpty());
            assertTrue(PrimordialServerConfig.mediaStageRequirements(true).isEmpty());
            PrimordialServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=pack:water"));
            PrimordialServerConfig.GAS_STAGE_REQUIREMENTS.set(List.of("mekanism:*=pack:gas"));
            assertEquals(List.of("minecraft:water=pack:water"), PrimordialServerConfig.mediaStageRequirements(false));
            assertEquals(List.of("mekanism:*=pack:gas"), PrimordialServerConfig.mediaStageRequirements(true));
        } finally { PrimordialServerConfig.SPEC.setConfig(previous); }
    }

    @Test void exactWildcardAndMultipleStagesCombineAndConfigurationChangesInvalidateCache() {
        var server = server();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        var data = new PlayerStagesSavedData();
        try (var config = mockStatic(PrimordialServerConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS)) {
            saved.when(() -> PlayerStagesSavedData.get(server)).thenReturn(data);
            config.when(() -> PrimordialServerConfig.mediaStageRequirements(false))
                    .thenReturn(List.of("minecraft:*=pack:base", "minecraft:water=pack:water", "minecraft:water=pack:extra"));
            assertFalse(ResourceStages.allowsMedia(server, owner, false, WATER));
            data.grant(owner, STAGE);
            data.grant(owner, new ResourceLocation("pack:base"));
            assertFalse(ResourceStages.allowsMedia(server, owner, false, WATER));
            data.grant(owner, new ResourceLocation("pack:extra"));
            assertTrue(ResourceStages.allowsMedia(server, owner, false, WATER));
            assertFalse(ResourceStages.allowsMedia(server, other, false, WATER));
            assertTrue(ResourceStages.allowsMedia(server, other, true, WATER));
            assertTrue(ResourceStages.allowsMedia(server, other, false, new ResourceLocation("other:water")));
            assertFalse(ResourceStages.allowsMedia(null, owner, false, WATER));
            config.when(() -> PrimordialServerConfig.mediaStageRequirements(false)).thenReturn(List.of());
            assertTrue(ResourceStages.allowsMedia(server, other, false, WATER));
        }
    }

    @Test void certificatesAndDisabledKnowledgeNeverBypassStagesAndGrantsDoNotTeachResources() {
        var server = server();
        var owner = UUID.randomUUID();
        var stages = new PlayerStagesSavedData();
        var knowledge = new MediaKnowledgeSavedData();
        try (var config = mockStatic(PrimordialServerConfig.class);
             var knowledgeConfig = mockStatic(PrimordialKnowledgeConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS);
             var media = mockStatic(MediaKnowledgeSavedData.class);
             var realKnowledge = mockStatic(ResourceKnowledge.class, CALLS_REAL_METHODS)) {
            saved.when(() -> PlayerStagesSavedData.get(server)).thenReturn(stages);
            media.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(knowledge);
            for (boolean gas : new boolean[]{false, true}) {
                var kind = gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID;
                config.when(() -> PrimordialServerConfig.mediaStageRequirements(gas)).thenReturn(List.of("minecraft:water=pack:water"));
                realKnowledge.when(() -> ResourceKnowledge.allows(server, owner, kind)).thenReturn(true);
                knowledgeConfig.when(PrimordialKnowledgeConfig::required).thenReturn(true);
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, WATER));
                knowledgeConfig.when(PrimordialKnowledgeConfig::required).thenReturn(false);
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, WATER));
                stages.grant(owner, STAGE);
                assertTrue(ResourceKnowledge.allowsMedia(server, owner, kind, WATER));
                knowledgeConfig.when(PrimordialKnowledgeConfig::required).thenReturn(true);
                realKnowledge.when(() -> ResourceKnowledge.allows(server, owner, kind)).thenReturn(false);
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, WATER));
                ResourceKnowledge.learnMedia(server, owner, kind, WATER);
                assertTrue(ResourceKnowledge.allowsMedia(server, owner, kind, WATER));
                stages.revoke(owner, STAGE);
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, WATER));
                assertTrue(knowledge.knows(owner, gas, WATER));
                stages.grant(owner, STAGE);
                knowledge.forget(owner, gas, WATER);
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, WATER));
                stages.revoke(owner, STAGE);
            }
        }
    }

    @Test void fluidStageStopsTransferAndSettlementButSaleStillLearnsThenGrantAndRevokeApplyImmediately() {
        var server = server();
        var owner = UUID.randomUUID();
        var stages = new PlayerStagesSavedData();
        var knowledge = new MediaKnowledgeSavedData();
        var transfer = mock(LongUnaryOperator.class);
        var price = EmcAmount.parse("1");
        try (var config = mockStatic(PrimordialServerConfig.class);
             var knowledgeConfig = mockStatic(PrimordialKnowledgeConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS);
             var media = mockStatic(MediaKnowledgeSavedData.class);
             var values = mockStatic(ValueManager.class);
             var settlement = mockStatic(EmcSettlement.class)) {
            config.when(() -> PrimordialServerConfig.mediaStageRequirements(false)).thenReturn(List.of("minecraft:water=pack:water"));
            saved.when(() -> PlayerStagesSavedData.get(server)).thenReturn(stages);
            media.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(knowledge);
            values.when(() -> ValueManager.fluidPricePerMB(Fluids.WATER)).thenReturn(price);
            assertEquals(0, EmcService.purchaseFluid(server, owner, Fluids.WATER, 1000, transfer));
            verifyNoInteractions(transfer);
            settlement.verifyNoInteractions();
            when(transfer.applyAsLong(1000)).thenReturn(1000L);
            assertEquals(1000, EmcService.reclaimFluid(server, owner, Fluids.WATER, 1000, transfer));
            assertTrue(knowledge.knows(owner, false, WATER));
            settlement.verify(() -> EmcSettlement.credit(server, owner, price.multiply(1000)));
            stages.grant(owner, STAGE);
            settlement.when(() -> EmcSettlement.purchase(server, owner, price, 1000, transfer)).thenReturn(1000L);
            assertEquals(1000, EmcService.purchaseFluid(server, owner, Fluids.WATER, 1000, transfer));
            stages.revoke(owner, STAGE);
            settlement.clearInvocations();
            clearInvocations(transfer);
            assertEquals(0, EmcService.purchaseFluid(server, owner, Fluids.WATER, 1000, transfer));
            verifyNoInteractions(transfer);
            settlement.verifyNoInteractions();
        }
    }

    private static MinecraftServer server() {
        var server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(mock(ServerLevel.class));
        return server;
    }
}
