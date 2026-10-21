/**
 * 【文件职责】验证资源凭证与原生ProjectE知识及价格的关系。
 * 【变更记录 #1】2026-09-16 19:46:19（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：九类学习前后与遗忘、未学零输出零扣款、知识开关和原生价表增删回归。
 * - 修改：只用原版物品替代未经过Forge注册的凭证身份，真实知识实现与EmcService执行。
 * - 用途：证明持有样本不解锁资源，关闭配置同时取消配方条件及凭证EMC。
 * 【变更记录 #2】2026-10-02 00:00:00（1.2.0-core-trading）
 * - 新增：验证凭证学习样本按资源类别归一化，普通物品样本保持原样。
 */
/**
 * 【变更记录 #3】2026-10-02 16:10:00（第 3 次备注 · 1.2.0-media-learning）
 * - 删除：无。
 * - 新增：逐种流体/气体知识的玩家隔离、类别隔离、凭证授权和存档往返验证。
 * - 修改：独立运行时先初始化 Forge 路径；只模拟世界数据存储入口，知识集合和 NBT 序列化继续执行。
 * - 用途：防止卖入一种介质后错误解锁另一种或另一玩家的资源。
 */
/**
 * 【变更记录 #4】2026-10-02 18:48:00（第 4 次备注 · 1.2.0-media-forgetting）
 * - 删除：无。
 * - 新增：气体和流体显式遗忘覆盖凭证、目录同步排除、禁止凭证重新解锁以及重新卖入恢复的测试。
 * - 修改：同时验证关闭知识门禁仍尊重主动遗忘，未知及未解锁 ID 不产生排除记录。
 * - 用途：避免遗忘只影响界面而继续允许购买或自动供应。
 */
/**
 * 【变更记录 #5】2026-10-10 04:07:17（第 5 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：目录排除仅卖资源、能源凭证独立解锁 FE 和 FE 黑名单报价过滤。
 * - 修改：知识使用真实 ProjectE provider，目录继续检查每一类凭证身份。
 * - 用途：确保液体凭证不能越权解锁能源，目录资格与服务端购买保持一致。
 */
/**
 * 【变更记录 #6】2026-10-10 04:08:38（第 6 次备注 · 1.2.0-resource-system-one）
 * - 删除：目录测试玩家缺少 server 与 UUID 的初始化缺口。
 * - 新增：匹配目录查询真实上下文的玩家身份初始化。
 * - 修改：知识 provider 和目录权限继续运行真实实现，不放宽生产代码校验。
 * - 用途：使新回归实际进入服务端目录流程，而不是因测试替身缺失提前返回空目录。
 */
/**
 * 【变更记录 #7】2026-10-10 04:14:04（第 7 次备注 · 1.2.0-resource-system-one）
 * - 删除：测试结束时直接单次删除临时目录的易波动清理路径。
 * - 新增：使用 RetryingTestTempDirFactory 的有界清理重试，持续失败仍导致测试失败。
 * - 修改：临时目录仍每项/每类独立，模组持久化代码和交易断言保持原有检查；工厂负责最终删除。
 * - 用途：Windows 短暂目录占用不再误判业务失败，绝不关闭真实保存或回滚验证。
 */
/**
 * 【变更记录 #8】2026-10-12 23:43:00（第 8 次备注 · 1.2.0-media-real-slots）
 * - 删除：FE 位于介质外圈首槽的测试假设。新增：内圈第 12 项及空外圈断言。
 * - 修改：FE 不计入介质分页数量；用途：验证能源与流体知识分区、仍受真实知识授权。
 */
/**
 * 【变更记录 #9】2026-10-13 01:53:34（第 9 次备注 · 1.2.0-media-feedback）
 * - 删除：有能源知识时显示 FE 的旧断言；现在能源知识也不产生转化桌 FE 条目。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
package com.primordial.service;

import com.primordial.compat.ProjectECertificatePrices;
import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.registry.ModItems;
import com.primordial.recipe.KnowledgeRequiredCondition;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT.EmcPKTInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.util.*;
import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用原生 ProjectE 知识实现，仅替换 Forge 注册表启动边界，校验学习前后与关闭规则的真实服务入口。 */
class ResourceKnowledgeTest {
    @TempDir(factory = com.primordial.util.RetryingTestTempDirFactory.class, cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) static Path gameDirectory;
    private static final Map<FMLPaths, Path> originalPaths = new EnumMap<>(FMLPaths.class);
    private final MinecraftServer server = mock(MinecraftServer.class);
    private final UUID owner = UUID.randomUUID();
    private final ServerPlayer player = mock(ServerPlayer.class);
    private final moze_intel.projecte.api.capabilities.IKnowledgeProvider knowledge = KnowledgeImpl.getDefault();
    private MockedStatic<PrimordialKnowledgeConfig> config;
    private EmcPKTInfo[] oldPrices;
    private final List<Object> oldItems = new ArrayList<>();
    private java.lang.reflect.Field value;

    @BeforeAll static void init() {
        AdapterTestBootstrap.initialize();
        for (FMLPaths path : FMLPaths.values()) originalPaths.put(path, path.get());
        FMLPaths.loadAbsolutePaths(gameDirectory);
        ProjectEConfig.common.getConfigSpec().setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
    }
    @AfterAll static void restorePaths() throws Exception {
        var field = FMLPaths.class.getDeclaredField("absolutePath");
        field.setAccessible(true);
        for (var entry : originalPaths.entrySet()) field.set(entry.getKey(), entry.getValue());
    }
    @BeforeEach void prepare() throws Exception {
        config = mockStatic(PrimordialKnowledgeConfig.class);
        config.when(PrimordialKnowledgeConfig::required).thenReturn(true);
        var players = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(players); when(players.getPlayer(owner)).thenReturn(player);
        when(player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY)).thenReturn(LazyOptional.of(() -> knowledge));
        value = RegistryObject.class.getDeclaredField("value"); value.setAccessible(true);
        Item[] standIns = {Items.PAPER, Items.STICK, Items.REDSTONE, Items.EMERALD, Items.IRON_INGOT,
                Items.OAK_SAPLING, Items.AMETHYST_SHARD, Items.CLOCK, Items.ANVIL, Items.LAPIS_LAZULI, Items.SOUL_SAND};
        int index = 0;
        for (var kind : ResourceKnowledge.Kind.values()) {
            var handle = ModItems.CERTIFICATES.get(kind);
            oldItems.add(value.get(handle)); value.set(handle, standIns[index++]);
            var sample = ResourceKnowledge.Kind.class.getDeclaredField("sample"); sample.setAccessible(true); sample.set(kind, null);
        }
        oldPrices = EMCMappingHandler.createPacketData();
        EMCMappingHandler.fromPacket(new EmcPKTInfo[]{new EmcPKTInfo(Items.DIAMOND, null, 8192)});
        ProjectECertificatePrices.onRemap(new EMCRemapEvent());
    }
    @AfterEach void restore() throws Exception {
        EMCMappingHandler.fromPacket(oldPrices);
        int i = 0;
        for (var kind : ResourceKnowledge.Kind.values()) {
            value.set(ModItems.CERTIFICATES.get(kind), oldItems.get(i++));
            var sample = ResourceKnowledge.Kind.class.getDeclaredField("sample"); sample.setAccessible(true); sample.set(kind, null);
        }
        config.close();
    }

    @Test void everyResourceRequiresItsOwnLearnedCertificateAndForgetRevokesAccess() {
        for (var kind : ResourceKnowledge.Kind.values()) {
            assertFalse(ResourceKnowledge.allows(server, owner, kind));
            ItemStack certificate = kind.sample();
            assertTrue(EMCMappingHandler.getStoredEmcValue(ItemInfo.fromStack(certificate)) > 0);
            knowledge.addKnowledge(certificate);
            assertTrue(ResourceKnowledge.allows(server, owner, kind));
            knowledge.removeKnowledge(certificate);
            assertFalse(ResourceKnowledge.allows(server, owner, kind));
        }
    }
    @Test void unlearnedResourcesCannotSpendOrTouchRecipients() {
        try (var settlement = mockStatic(EmcSettlement.class)) {
            var battery = new EnergyStorage(1000);
            assertEquals(0, EmcService.supplyEnergy(server, owner, battery, 1000));
            assertEquals(0, battery.getEnergyStored());
            assertEquals(0, EmcService.purchaseFluid(server, owner, Fluids.WATER, 1000, n -> fail("fluid generated")));
            for (var unit : com.primordial.resource.ResourceExchangeConversions.supportedUnits()) {
                var port = mock(com.primordial.resource.EmcResourcePort.class);
                when(port.unit()).thenReturn(unit); when(port.canReceive()).thenReturn(true);
                assertEquals(0, EmcService.supplyResource(server, owner, port, 1000));
                verify(port, never()).receive(anyLong(), anyBoolean());
            }
            assertEquals(0, com.primordial.compat.MekanismGasCompat.supply(server, owner,
                    new com.primordial.data.FluidSampleConfig(), null, 0));
            settlement.verifyNoInteractions();
        }
    }
    @Test void disablingKnowledgeRemovesRecipesAndNativeCertificateEmcOnly() {
        assertTrue(new KnowledgeRequiredCondition().test(null));
        config.when(PrimordialKnowledgeConfig::required).thenReturn(false);
        assertFalse(new KnowledgeRequiredCondition().test(null));
        ProjectECertificatePrices.onRemap(new EMCRemapEvent());
        for (var kind : ResourceKnowledge.Kind.values()) {
            assertTrue(ResourceKnowledge.allows(server, owner, kind));
            assertFalse(EMCMappingHandler.hasEmcValue(ItemInfo.fromStack(kind.sample())));
        }
        assertEquals(8192, EMCMappingHandler.getStoredEmcValue(ItemInfo.fromItem(Items.DIAMOND)));
        assertTrue(EmcService.hasKnowledge(server, owner, new ItemStack(Items.DIAMOND)));
        config.when(PrimordialKnowledgeConfig::required).thenReturn(true);
        ProjectECertificatePrices.onRemap(new EMCRemapEvent());
        assertTrue(new KnowledgeRequiredCondition().test(null));
        assertFalse(EmcService.hasKnowledge(server, owner, new ItemStack(Items.DIAMOND)));
    }
    @Test void learnedEnergyCertificateReachesSettlementAndPlainItemsStillNeedOwnKnowledge() {
        knowledge.addKnowledge(ResourceKnowledge.Kind.ENERGY.sample());
        var battery = new EnergyStorage(1000);
        try (var settlement = mockStatic(EmcSettlement.class)) {
            settlement.when(() -> EmcSettlement.purchase(eq(server), eq(owner), any(), eq(1000L), any()))
                    .thenAnswer(call -> call.<java.util.function.LongUnaryOperator>getArgument(4).applyAsLong(1000));
            assertEquals(1000, EmcService.supplyEnergy(server, owner, battery, 1000));
            assertEquals(1000, battery.getEnergyStored());
        }
        assertFalse(EmcService.hasKnowledge(server, owner, new ItemStack(Items.DIAMOND)));
        knowledge.addKnowledge(new ItemStack(Items.DIAMOND));
        assertTrue(EmcService.hasKnowledge(server, owner, new ItemStack(Items.DIAMOND)));
    }

    @Test void certificatesNormalizeToTheirCategorySampleWhilePlainItemsRemainUnchanged() {
        for (var kind : new ResourceKnowledge.Kind[]{ResourceKnowledge.Kind.FLUID, ResourceKnowledge.Kind.GAS}) {
            ItemStack certificate = kind.sample().copy();
            certificate.setCount(7);
            assertSame(kind, ResourceKnowledge.certificateKind(certificate));
            assertTrue(ResourceKnowledge.knowledgeSample(certificate).is(kind.sample().getItem()));
            assertEquals(1, ResourceKnowledge.knowledgeSample(certificate).getCount());
        }
        ItemStack plain = new ItemStack(Items.DIAMOND);
        plain.setCount(7);
        assertSame(plain, ResourceKnowledge.knowledgeSample(plain));
    }

    @Test void soldMediaKnowledgeIsPerPlayerPerKindAndPerId() {
        MediaKnowledgeSavedData data = new MediaKnowledgeSavedData();
        ResourceLocation water = new ResourceLocation("minecraft", "water");
        ResourceLocation lava = new ResourceLocation("minecraft", "lava");
        UUID other = UUID.randomUUID();
        when(server.overworld()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        when(server.isSameThread()).thenReturn(true);
        try (var saved = mockStatic(MediaKnowledgeSavedData.class)) {
            saved.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(data);
            assertFalse(ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID, water));
            ResourceKnowledge.learnMedia(server, owner, ResourceKnowledge.Kind.FLUID, water);
            assertTrue(ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID, water));
            assertFalse(ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.FLUID, lava));
            assertFalse(ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.GAS, water));
            assertFalse(ResourceKnowledge.allowsMedia(server, other, ResourceKnowledge.Kind.FLUID, water));
            assertFalse(ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.ENERGY, water));
            knowledge.addKnowledge(ResourceKnowledge.Kind.GAS.sample());
            assertTrue(ResourceKnowledge.allowsMedia(server, owner, ResourceKnowledge.Kind.GAS, lava));
        }
    }

    @Test void mediaKnowledgeSurvivesSaveAndIgnoresMalformedEntries() {
        MediaKnowledgeSavedData original = new MediaKnowledgeSavedData();
        ResourceLocation water = new ResourceLocation("minecraft", "water");
        ResourceLocation gas = new ResourceLocation("mekanism", "hydrogen");
        assertTrue(original.learn(owner, false, water));
        assertFalse(original.learn(owner, false, water));
        assertTrue(original.learn(owner, true, gas));
        var tag = original.save(new net.minecraft.nbt.CompoundTag());
        tag.getCompound("players").put("invalid-uuid", new net.minecraft.nbt.CompoundTag());
        MediaKnowledgeSavedData loaded = MediaKnowledgeSavedData.load(tag);
        assertTrue(loaded.knows(owner, false, water));
        assertTrue(loaded.knows(owner, true, gas));
        assertFalse(loaded.knows(owner, true, water));
        assertFalse(loaded.knows(UUID.randomUUID(), false, water));
    }

    @Test void forgettingOverridesCertificatesAndCatalogUntilThatResourceIsRelearned() throws Exception {
        when(server.overworld()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        when(server.isSameThread()).thenReturn(true);
        when(player.getUUID()).thenReturn(owner);
        var serverField = ServerPlayer.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(player, server);
        for (boolean gas : new boolean[]{false, true}) {
            var kind = gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID;
            var id = new ResourceLocation(gas ? "mekanism:hydrogen" : "minecraft:water");
            var other = new ResourceLocation(gas ? "mekanism:oxygen" : "minecraft:lava");
            var data = new MediaKnowledgeSavedData();
            try (var saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS);
                 var gases = mockStatic(com.primordial.compat.MekanismGasCompat.class);
                 var emc = mockStatic(EmcService.class, CALLS_REAL_METHODS)) {
                saved.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(data);
                gases.when(() -> com.primordial.compat.MekanismGasCompat.validGas(id)).thenReturn(true);
                gases.when(() -> com.primordial.compat.MekanismGasCompat.gasIds()).thenReturn(Set.of(id, other));
                gases.when(() -> com.primordial.compat.MekanismGasCompat.validGas(other)).thenReturn(true);
                emc.when(() -> EmcService.gasPrice(any())).thenReturn(EmcAmount.parse("1"));
                emc.when(() -> EmcService.fluidPrice(any())).thenReturn(EmcAmount.parse("1"));
                knowledge.addKnowledge(kind.sample());
                assertTrue(ResourceKnowledge.allowsMedia(server, owner, kind, id));
                assertTrue(ResourceKnowledge.forgetMedia(server, owner, kind, id));
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, id));
                assertTrue(ResourceKnowledge.allowsMedia(server, owner, kind, other));
                assertFalse(TransmutationMediaCatalog.search(player, gas, "", 0).ids().contains(id.toString()));
                assertTrue(TransmutationMediaCatalog.search(player, gas, "", 0).ids().contains(other.toString()));
                knowledge.removeKnowledge(kind.sample());
                knowledge.addKnowledge(kind.sample());
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, id));
                config.when(PrimordialKnowledgeConfig::required).thenReturn(false);
                assertFalse(ResourceKnowledge.allowsMedia(server, owner, kind, id));
                config.when(PrimordialKnowledgeConfig::required).thenReturn(true);
                assertFalse(ResourceKnowledge.forgetMedia(server, owner, kind, id));
                ResourceKnowledge.learnMedia(server, owner, kind, id);
                assertTrue(ResourceKnowledge.allowsMedia(server, owner, kind, id));
                assertTrue(TransmutationMediaCatalog.search(player, gas, "", 0).ids().contains(id.toString()));
            }
        }
    }

    @Test void catalogExcludesSellOnlyAndEnergyNeedsItsOwnCertificate() throws Exception {
        var serverField = ServerPlayer.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(player, server);
        when(player.getUUID()).thenReturn(owner);
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        var water = new ResourceLocation("minecraft:water");
        var fe = com.primordial.energy.EnergyConversions.FE;
        try (var values = mockStatic(com.primordial.value.ValueManager.class);
             var emc = mockStatic(EmcService.class, CALLS_REAL_METHODS);
             var data = mockStatic(MediaKnowledgeSavedData.class)) {
            data.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(new MediaKnowledgeSavedData());
            knowledge.addKnowledge(ResourceKnowledge.Kind.FLUID.sample());
            emc.when(() -> EmcService.fluidPrice(any())).thenReturn(EmcAmount.ONE);
            emc.when(() -> EmcService.energyPrice(fe)).thenReturn(EmcAmount.parse("1/1000"));
            values.when(() -> com.primordial.value.ValueManager.canBuyFluid(any())).thenReturn(false);
            assertFalse(TransmutationMediaCatalog.search(player, false, "", 0).ids().contains(water.toString()));
            assertFalse(TransmutationMediaCatalog.search(player, false, "", 0).ids().contains(fe.toString()));
            values.when(() -> com.primordial.value.ValueManager.canBuyFluid(Fluids.WATER)).thenReturn(true);
            assertEquals(List.of(water.toString()), TransmutationMediaCatalog.search(player, false, "water", 0).ids());
            knowledge.addKnowledge(ResourceKnowledge.Kind.ENERGY.sample());
            assertTrue(TransmutationMediaCatalog.search(player, false, "forge:fe", 0).ids().isEmpty());
            emc.when(() -> EmcService.energyPrice(fe)).thenReturn(EmcAmount.ZERO);
            assertTrue(TransmutationMediaCatalog.search(player, false, "forge:fe", 0).ids().isEmpty());
        }
    }

    @Test void forgettingRejectsUnknownOrLockedResourcesWithoutCreatingExclusions() {
        when(server.overworld()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        when(server.isSameThread()).thenReturn(true);
        var data = new MediaKnowledgeSavedData();
        var water = new ResourceLocation("minecraft:water");
        var invalid = new ResourceLocation("test:does_not_exist");
        try (var saved = mockStatic(MediaKnowledgeSavedData.class)) {
            saved.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(data);
            assertFalse(ResourceKnowledge.forgetMedia(server, owner, ResourceKnowledge.Kind.FLUID, water));
            knowledge.addKnowledge(ResourceKnowledge.Kind.FLUID.sample());
            assertFalse(ResourceKnowledge.forgetMedia(server, owner, ResourceKnowledge.Kind.FLUID, invalid));
            assertFalse(data.isDirty());
            assertFalse(data.isForgotten(owner, false, invalid));
            assertFalse(data.isForgotten(owner, false, water));
        }
    }
}
