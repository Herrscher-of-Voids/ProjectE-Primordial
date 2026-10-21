/**
 * 【文件职责】验证物品阶段配置、与介质隔离、知识和 NBT 变体门禁。
 * 【变更记录 #1】2026-10-02 19:15:57（第 1 次备注 · 1.2.0-item-supply-stages）
 * - 删除：无。
 * - 新增：真实 Forge 默认配置、通配叠加、配置更新、知识和阶段同时满足的回归。
 * - 修改：真实阶段规则执行，世界存储与 ProjectE 知识边界使用替身。
 * - 用途：防止关闭知识要求或修改物品 NBT 绕过补充阶段。
 */
package com.primordial.service;

import com.primordial.compat.ProjectECompat;
import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.PlayerStagesSavedData;
import com.primordial.machine.AdapterTestBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemStageRulesTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void forgeItemRulesDefaultEmptyAndDoNotModifyMediaLists() throws Exception {
        var field = net.minecraftforge.common.ForgeConfigSpec.class.getDeclaredField("childConfig");
        field.setAccessible(true);
        var previous = (com.electronwill.nightconfig.core.CommentedConfig) field.get(PrimordialServerConfig.SPEC);
        try {
            PrimordialServerConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            assertTrue(PrimordialServerConfig.itemStageRequirements().isEmpty());
            assertEquals(List.of("resource_stages", "item_supply_requirements"), PrimordialServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.getPath());
            PrimordialServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(List.of("minecraft:diamond=pack:diamond"));
            assertEquals(List.of("minecraft:diamond=pack:diamond"), PrimordialServerConfig.itemStageRequirements());
            assertTrue(PrimordialServerConfig.mediaStageRequirements(false).isEmpty());
            assertTrue(PrimordialServerConfig.mediaStageRequirements(true).isEmpty());
        } finally { PrimordialServerConfig.SPEC.setConfig(previous); }
    }

    @Test void itemRulesCombineAllStagesWithoutAffectingSameIdInMediaAndRefreshAfterConfigChanges() {
        var server = server();
        var owner = UUID.randomUUID();
        var id = new ResourceLocation("minecraft:diamond");
        var data = new PlayerStagesSavedData();
        try (var config = mockStatic(PrimordialServerConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS)) {
            saved.when(() -> PlayerStagesSavedData.get(server)).thenReturn(data);
            config.when(PrimordialServerConfig::itemStageRequirements)
                    .thenReturn(List.of("minecraft:*=pack:base", "minecraft:diamond=pack:diamond"));
            assertFalse(ResourceStages.allowsItem(server, owner, id));
            assertTrue(ResourceStages.allowsMedia(server, owner, false, id));
            assertTrue(ResourceStages.allowsMedia(server, owner, true, id));
            data.grant(owner, new ResourceLocation("pack:diamond"));
            assertFalse(ResourceStages.allowsItem(server, owner, id));
            data.grant(owner, new ResourceLocation("pack:base"));
            assertTrue(ResourceStages.allowsItem(server, owner, id));
            assertFalse(ResourceStages.allowsItem(server, UUID.randomUUID(), id));
            assertFalse(ResourceStages.allowsItem(null, owner, id));
            config.when(PrimordialServerConfig::itemStageRequirements).thenReturn(List.of());
            assertTrue(ResourceStages.allowsItem(server, UUID.randomUUID(), id));
            config.when(() -> PrimordialServerConfig.mediaStageRequirements(false)).thenReturn(List.of("minecraft:diamond=pack:fluid"));
            assertFalse(ResourceStages.allowsMedia(server, owner, false, id));
            assertTrue(ResourceStages.allowsItem(server, owner, id));
        }
    }

    @Test void stageAndKnowledgeAreBothRequiredAndEveryNbtVariantIsRestricted() {
        var server = server();
        var owner = UUID.randomUUID();
        var stage = new ResourceLocation("pack:diamond");
        var data = new PlayerStagesSavedData();
        var plain = new ItemStack(Items.DIAMOND);
        var tagged = plain.copy();
        tagged.getOrCreateTag().putString("custom_name", "variant");
        try (var config = mockStatic(PrimordialServerConfig.class);
             var knowledge = mockStatic(PrimordialKnowledgeConfig.class);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS);
             var projecte = mockStatic(ProjectECompat.class)) {
            saved.when(() -> PlayerStagesSavedData.get(server)).thenReturn(data);
            config.when(PrimordialServerConfig::itemStageRequirements).thenReturn(List.of("minecraft:diamond=pack:diamond"));
            knowledge.when(PrimordialKnowledgeConfig::required).thenReturn(true);
            projecte.when(() -> ProjectECompat.hasKnowledge(eq(server), eq(owner), any())).thenReturn(true);
            assertFalse(EmcService.hasKnowledge(server, owner, plain));
            assertFalse(EmcService.hasKnowledge(server, owner, tagged));
            knowledge.when(PrimordialKnowledgeConfig::required).thenReturn(false);
            assertFalse(EmcService.hasKnowledge(server, owner, tagged));
            data.grant(owner, stage);
            assertTrue(EmcService.hasKnowledge(server, owner, tagged));
            knowledge.when(PrimordialKnowledgeConfig::required).thenReturn(true);
            projecte.when(() -> ProjectECompat.hasKnowledge(eq(server), eq(owner), any())).thenReturn(false);
            assertFalse(EmcService.hasKnowledge(server, owner, tagged));
            projecte.when(() -> ProjectECompat.hasKnowledge(eq(server), eq(owner), any())).thenReturn(true);
            assertTrue(EmcService.hasKnowledge(server, owner, tagged));
            data.revoke(owner, stage);
            assertFalse(EmcService.hasKnowledge(server, owner, tagged));
            assertFalse(EmcService.hasKnowledge(server, owner, ItemStack.EMPTY));
            assertEquals("variant", tagged.getTag().getString("custom_name"));
        }
    }

    private static MinecraftServer server() {
        var server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(mock(ServerLevel.class));
        return server;
    }
}
