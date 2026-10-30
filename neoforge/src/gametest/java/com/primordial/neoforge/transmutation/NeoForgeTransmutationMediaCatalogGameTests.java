/**
 * 【文件职责】通过真实流体/可选化学品注册表和生产知识/价表验证介质目录的过滤与有界分页。
 * 【变更记录 #1】2026-10-23 21:06:00（第 1 次备注 · 2.0.0-neoforge-media-catalog）
 * - 删除：无。新增：14 源流体跨页、查询、显式遗忘、阶段、黑名单、买卖方向及无可选依赖回归。
 * - 修改：每项使用随机知识账户，临时价格文件和配置在 finally 恢复；目录查询本身不生成知识或入账。
 * - 用途：确认客户端目录仅展示服务端可购买资源，不把目录展示当作真实容器交易验收。
 */
package com.primordial.neoforge.transmutation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledgeSavedData;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;
import com.primordial.neoforge.value.NeoForgePriceEditing;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationMediaCatalogGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation LAVA = ResourceLocation.parse("minecraft:lava");
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");
    private static final ResourceLocation STAGE = ResourceLocation.parse("pack:catalog_water");

    private NeoForgeTransmutationMediaCatalogGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void realRegisteredFluidsUseSortedSixteenSlotPagesWithTwelveStepOverlap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        try (Config previous = new Config()) {
            List<ResourceLocation> ids = NeoForgeMediaCatalogTestFixture.IDS;
            for (int index = ids.size() - 1; index >= 0; index--)
                check(NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.FLUID, ids.get(index)),
                        "The fixture must be real registered source fluids, not arbitrary ledger strings");
            var first = search(server, owner, " PRIMORDIAL:CATALOG_TEST_ ", 0);
            var second = search(server, owner, "primordial:catalog_test_", 1);
            check(first.total() == 20 && first.ids().size() == 16 && second.total() == 20 && second.ids().size() == 8,
                    "Filtering must precede sixteen-slot pages that advance by twelve entries");
            check(first.ids().equals(ids.subList(0, 16).stream().map(ResourceLocation::toString).toList())
                    && second.ids().equals(ids.subList(12, 20).stream().map(ResourceLocation::toString).toList()),
                    "Stable registry-name ordering must preserve the four-entry overlap between native pages");
            check(search(server, owner, "primordial:catalog_test_", 2).ids().isEmpty()
                    && search(server, owner, "primordial:catalog_test_", 2).total() == 20,
                    "A valid page past the last match must retain the filtered total");
            boolean immutable = false;
            try { first.ids().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            check(immutable, "The network-facing catalog result must be a defensive immutable snapshot");
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.FLUID, NeoForgeMediaCatalogTestFixture.OVERSIZED_ID);
            check(search(server, owner, "primordial:catalog_oversized_", 0).total() == 0,
                    "Even a real learned registry ID cannot exceed the bounded packet string contract");
            if (NeoForgeKnowledgeConfig.required())
                check(search(server, UUID.randomUUID(), "primordial:catalog_test_", 0).total() == 0,
                        "Another player must not inherit this owner's individual fluid knowledge");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void sourceKnowledgeForgetAndStageFiltersAgreeWithPurchaseAuthorization(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        try (Config previous = new Config()) {
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.FLUID, WATER);
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.FLUID, LAVA);
            check(search(server, owner, "minecraft:", 0).ids().equals(List.of(LAVA.toString(), WATER.toString())),
                    "The catalog must exclude empty and flowing vanilla fluid variants");
            check(NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.FLUID, WATER)
                    && search(server, owner, "minecraft:", 0).ids().equals(List.of(LAVA.toString())),
                    "Explicit forgetting must remove the entry before total counting");
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.FLUID, WATER);
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=" + STAGE));
            check(search(server, owner, "minecraft:water", 0).total() == 0,
                    "Learned fluids still require every configured purchase stage");
            NeoForgePlayerStagesSavedData stages = NeoForgePlayerStagesSavedData.get(server);
            stages.grant(owner, STAGE);
            check(search(server, owner, "minecraft:water", 0).ids().equals(List.of(WATER.toString())),
                    "A real personal stage grant must make the existing knowledge discoverable");
            stages.revoke(owner, STAGE);
            check(search(server, owner, "minecraft:water", 0).total() == 0,
                    "Revoking a stage must immediately remove the entry without deleting learned knowledge");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_media_catalog")
    public static void purchaseDirectionPriceAndBlacklistFilterWithoutRequiringFunds(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        Path file = FMLPaths.CONFIGDIR.get().resolve("primordial_values.json");
        byte[] original = Files.exists(file) ? Files.readAllBytes(file) : null;
        try (Config previous = new Config()) {
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.FLUID, WATER);
            check(NeoForgePriceEditing.mutate(server.createCommandSourceStack(), (byte) 1, WATER, "1000").success(),
                    "The production command-price mutation must establish a known purchase price");
            check(search(server, owner, "minecraft:water", 0).total() == 1,
                    "No balance is needed to discover a learned, purchasable resource");
            for (String blocked : List.of("minecraft:water", "minecraft:*", "#minecraft:water")) {
                NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of(blocked));
                check(search(server, owner, "minecraft:water", 0).total() == 0,
                        "Catalog must honor exact, namespace and actual fluid-tag blacklists");
            }
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(false);
            check(search(server, owner, "minecraft:water", 0).total() == 0, "Disabled fluid purchasing must hide catalog entries");
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            check(NeoForgePriceEditing.mutate(server.createCommandSourceStack(), (byte) 1, WATER, "sell_only").success()
                    && search(server, owner, "minecraft:water", 0).total() == 0,
                    "A positive recovery price must not expose a sell-only resource as purchasable");
            check(NeoForgePriceEditing.mutate(server.createCommandSourceStack(), (byte) 1, WATER, "buy_only").success()
                    && search(server, owner, "minecraft:water", 0).total() == 1,
                    "Buy-only mode must remain discoverable even when sale is disallowed");
            check(NeoForgePriceEditing.mutate(server.createCommandSourceStack(), (byte) 1, WATER, "0").success()
                    && search(server, owner, "minecraft:water", 0).total() == 0,
                    "A saved zero price must not create a free resource catalog entry");
        } finally {
            if (original == null) Files.deleteIfExists(file); else Files.write(file, original);
            NeoForgeRecoveryPrices.reload();
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void invalidInputsAndOtherThreadsDoNotMutateKnowledge(GameTestHelper helper) throws InterruptedException {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        CompoundTag before = data.save(new CompoundTag(), server.registryAccess());
        check(search(server, owner, null, 0).total() == 0 && search(server, owner, "a".repeat(65), 0).total() == 0
                && search(server, owner, "", -1).total() == 0 && search(server, owner, "", Integer.MAX_VALUE).total() == 0
                && search(null, owner, "", 0).total() == 0 && search(server, null, "", 0).total() == 0
                && NeoForgeTransmutationMediaCatalog.search(null, false, "", 0).total() == 0,
                "Null, oversized and out-of-range requests must fail closed without truncation");
        AtomicBoolean rejected = new AtomicBoolean();
        Thread worker = new Thread(() -> rejected.set(search(server, owner, "", 0).total() == 0), "primordial-catalog-thread-test");
        worker.start();
        worker.join();
        check(rejected.get() && before.equals(data.save(new CompoundTag(), server.registryAccess())),
                "Rejected catalog queries must not touch server knowledge from another thread or invent learned resources");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void optionalChemicalCatalogUsesRealRegistryAndForgetRules(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        try (Config previous = new Config()) {
            if (ModList.get().isLoaded("mekanism")) {
                check(NeoForgeMediaRegistry.chemicalIds().contains(HYDROGEN)
                        && NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.GAS, HYDROGEN),
                        "With Mekanism installed the directory must enumerate real nonempty unified chemicals");
                check(NeoForgeTransmutationMediaCatalog.search(server, owner, true, " MEKANISM:HYDROGEN ", 0).ids().contains(HYDROGEN.toString()),
                        "Chemical selection must honor real learned knowledge and normalized search");
                NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.GAS, HYDROGEN);
                check(!NeoForgeTransmutationMediaCatalog.search(server, owner, true, "mekanism:hydrogen", 0).ids().contains(HYDROGEN.toString()),
                        "Chemical forgetting must remove the registered entry before paging");
            } else {
                check(NeoForgeMediaRegistry.chemicalIds().isEmpty()
                        && NeoForgeTransmutationMediaCatalog.search(server, owner, true, "", 0).total() == 0,
                        "Without Mekanism a chemical request must be empty without resolving optional API types");
            }
        }
        helper.succeed();
    }

    private static NeoForgeTransmutationMediaCatalog.Page search(MinecraftServer server, UUID owner, String query, int page) {
        return NeoForgeTransmutationMediaCatalog.search(server, owner, false, query, page);
    }

    private static final class Config implements AutoCloseable {
        private final boolean fluidBuy = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        private final boolean chemicalBuy = NeoForgeServerConfig.GAS_PURCHASE_ENABLED.get();
        private final List<? extends String> fluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        private final List<? extends String> chemicalBlacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        private final List<? extends String> fluidStages = List.copyOf(NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.get());
        private final List<? extends String> chemicalStages = List.copyOf(NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.get());
        private Config() {
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of());
            NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.set(List.of());
        }
        public void close() {
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(fluidBuy);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(chemicalBuy);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(fluidBlacklist);
            NeoForgeServerConfig.GAS_BLACKLIST.set(chemicalBlacklist);
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(fluidStages);
            NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.set(chemicalStages);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
