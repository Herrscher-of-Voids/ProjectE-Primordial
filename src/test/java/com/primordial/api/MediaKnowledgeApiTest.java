/**
 * 【文件职责】验证作者接口的数据隔离、存档、线程边界和原购买规则。
 * 【变更记录 #1】2026-10-13 20:09:00（第 1 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：无。新增：知识往返、非法请求、气体兼容边界和购买查询回归。
 * - 修改：只模拟服务端存储及外部授权边界，使用真实知识集合和 NBT 序列化。
 * - 用途：作者调用不赠送资源、不修改 EMC、不绕过原有购买规则。
 */
/**
 * 【变更记录 #2】2026-10-13 20:18:14（第 2 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：静态替身误拦截真实私有 remove 帮助函数的行为。新增：无。
 * - 修改：知识存档仅替换 get 入口，其余静态方法调用真实实现。
 * - 用途：实际验证遗忘和重新学习，不让测试替身改变生产存档行为。
 */
package com.primordial.api;

import com.primordial.api.MediaKnowledgeApi.Media;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcSettlement;
import com.primordial.service.ResourceKnowledge;
import com.primordial.service.ResourceStages;
import com.primordial.value.ValueManager;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaKnowledgeApiTest {
    private static final ResourceLocation WATER = new ResourceLocation("minecraft:water");
    private final UUID owner = UUID.randomUUID();
    private final MinecraftServer server = mock(MinecraftServer.class);

    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    private void prepareServer() {
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(mock(ServerLevel.class));
    }

    @Test void grantRevokeAndRelearnPersistWithoutChangingEmcOrOtherPlayers() {
        prepareServer();
        var data = new MediaKnowledgeSavedData();
        try (var saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS);
             var settlement = mockStatic(EmcSettlement.class)) {
            saved.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(data);
            assertFalse(MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER));
            assertFalse(MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER));
            assertFalse(MediaKnowledgeApi.hasLearned(server, UUID.randomUUID(), Media.FLUID, WATER));
            assertFalse(data.knows(owner, true, WATER));
            assertTrue(MediaKnowledgeSavedData.load(data.save(new CompoundTag())).knows(owner, false, WATER));
            assertTrue(MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER));
            assertFalse(MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeSavedData.load(data.save(new CompoundTag())).isForgotten(owner, false, WATER));
            assertFalse(MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER));
            assertFalse(data.isForgotten(owner, false, WATER));
            settlement.verifyNoInteractions();
        }
    }

    @Test void invalidResourcesAndOffThreadCallsNeverAccessWorldData() {
        prepareServer();
        try (var saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS)) {
            for (var id : new ResourceLocation[]{new ResourceLocation("minecraft:missing"),
                    new ResourceLocation("minecraft:empty"), new ResourceLocation("minecraft:flowing_water")}) {
                assertFalse(MediaKnowledgeApi.learn(server, owner, Media.FLUID, id));
                assertFalse(MediaKnowledgeApi.forget(server, owner, Media.FLUID, id));
            }
            assertFalse(MediaKnowledgeApi.learn(server, owner, null, WATER));
            assertFalse(MediaKnowledgeApi.learn(null, owner, Media.FLUID, WATER));
            assertFalse(MediaKnowledgeApi.learn(server, null, Media.FLUID, WATER));
            when(server.isSameThread()).thenReturn(false);
            assertFalse(MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER));
            assertFalse(MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER));
            assertFalse(MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER));
            assertFalse(MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, WATER));
            saved.verifyNoInteractions();
        }
    }

    @Test void gasUsesOptionalRegistryBoundaryAndSeparateKnowledge() {
        prepareServer();
        var gas = new ResourceLocation("mekanism:hydrogen");
        var data = new MediaKnowledgeSavedData();
        try (var compat = mockStatic(MekanismGasCompat.class);
             var saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS)) {
            saved.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(data);
            assertFalse(MediaKnowledgeApi.learn(server, owner, Media.GAS, gas));
            assertTrue(data.knownIds(owner, true).isEmpty());
            compat.when(() -> MekanismGasCompat.validGas(gas)).thenReturn(true);
            assertTrue(MediaKnowledgeApi.learn(server, owner, Media.GAS, gas));
            assertTrue(MediaKnowledgeApi.hasLearned(server, owner, Media.GAS, gas));
            assertFalse(data.knows(owner, false, gas));
            assertTrue(MediaKnowledgeApi.forget(server, owner, Media.GAS, gas));
            assertFalse(MediaKnowledgeApi.hasLearned(server, owner, Media.GAS, gas));
        }
    }

    @Test void knowledgeGrantDoesNotBypassAuthorStageOrPricingAndForgetOverridesCertificate() {
        prepareServer();
        var data = new MediaKnowledgeSavedData();
        try (var saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS);
             var config = mockStatic(PrimordialServerConfig.class);
             var required = mockStatic(PrimordialKnowledgeConfig.class);
             var stages = mockStatic(ResourceStages.class);
             var knowledge = mockStatic(ResourceKnowledge.class);
             var values = mockStatic(ValueManager.class);
             var settlement = mockStatic(EmcSettlement.class)) {
            saved.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(data);
            required.when(PrimordialKnowledgeConfig::required).thenReturn(true);
            knowledge.when(() -> ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID, WATER)).thenCallRealMethod();
            stages.when(() -> ResourceStages.allowsMedia(server, owner, false, WATER)).thenReturn(true);
            values.when(() -> ValueManager.canBuyFluid(Fluids.WATER)).thenReturn(true);
            values.when(() -> ValueManager.fluidPricePerMB(Fluids.WATER)).thenReturn(EmcAmount.of(java.math.BigInteger.ONE));
            assertFalse(MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, WATER));
            stages.when(() -> ResourceStages.allowsMedia(server, owner, false, WATER)).thenReturn(false);
            assertFalse(MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER));
            stages.when(() -> ResourceStages.allowsMedia(server, owner, false, WATER)).thenReturn(true);
            knowledge.when(() -> ResourceKnowledge.allows(server, owner, ResourceKnowledge.Kind.FLUID)).thenReturn(true);
            assertFalse(MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, WATER));
            assertTrue(MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER));
            values.when(() -> ValueManager.canBuyFluid(Fluids.WATER)).thenReturn(false);
            assertFalse(MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, WATER));
            settlement.verifyNoInteractions();
        }
    }
}
