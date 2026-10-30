/**
 * 【文件职责】以真实 ProjectE 玩家和 typed 交易验证个人阶段门禁、知识独立性及报价期撤销。
 * 【变更记录 #1】2026-10-23 16:22:45（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无。
 * - 新增：物品/水/FE/可选真实氢的购买和只读报价、全部匹配规则取 AND、个人隔离与离线结算。
 * - 修改：临时配置和知识会话快照仅在主线程测试范围内注入并 finally 恢复；生产 API 无测试开关。
 * - 用途：证明阶段不授予知识或 EMC，免知识不绕过阶段，回收仍学习；回调撤销再授予也拒绝旧报价且不重放。
 * 【变更记录 #2】2026-10-23 16:24:02（第 2 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无。
 * - 新增：安装 Mekanism 时在同一免知识回归内验证真实氢仍需独立阶段，购买不会自动学习。
 * - 修改：可选分支只传注册 ID，不引用化学品 API 类；缺模组时跳过该分支。
 * - 用途：四类公共授权全部覆盖知识开关和阶段相互独立的约定。
 */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.*;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeStageTradeGameTests {
    private static final ResourceLocation A = ResourceLocation.parse("primordial:stage_trade_a");
    private static final ResourceLocation B = ResourceLocation.parse("primordial:stage_trade_b");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");
    private static final ItemInfo DIAMOND = ItemInfo.fromItem(Items.DIAMOND);
    private static final LongUnaryOperator FORBIDDEN = units -> {
        throw new AssertionError("A denied stage quote must not transfer, debit or extract resources");
    };

    private NeoForgeStageTradeGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void itemStagesAndKnowledgeAreIndependentAndEveryMatchingRuleIsRequired(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        ServerPlayer other = null;
        try (Settings ignored = new Settings()) {
            other = login(server);
            UUID owner = player.getUUID();
            var stages = NeoForgePlayerStagesSavedData.get(server);
            var item = new ItemStack(Items.DIAMOND);
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.valueOf(1000000));
            NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(List.of("minecraft:*= " + A, "diamond=" + B));
            BigInteger before = knowledge(player).getEmc();
            CompoundTag ledger = ledger(server);
            check(stages.grant(owner, A) && stages.grant(owner, B), "Author stage grants must actually change the personal ledger");
            check(knowledge(player).getEmc().equals(before) && !knowledge(player).hasKnowledge(DIAMOND)
                    && ledger(server).equals(ledger), "Stage grants must give neither native item knowledge nor EMC");
            check(NeoForgeResourceTrades.purchaseItem(server, owner, item, 1, FORBIDDEN) == 0,
                    "All stages alone must not authorize an unknown native item");
            knowledge(player).addKnowledge(DIAMOND);
            stages.revoke(owner, B);
            denied(server, player, () -> NeoForgeResourceTrades.affordableItem(server, owner, item, 1),
                    () -> NeoForgeResourceTrades.purchaseItem(server, owner, item, 1, FORBIDDEN));
            stages.grant(other.getUUID(), B);
            check(!stages.has(owner, B), "Another player's stage grant must not satisfy this owner's exact rule");
            stages.grant(owner, B);
            CompoundTag quoted = ledger(server);
            check(NeoForgeResourceTrades.affordableItem(server, owner, item, 1) == 1
                    && ledger(server).equals(quoted) && knowledge(player).getEmc().equals(before),
                    "An authorized item simulation must stay read-only");
            BigInteger price = NeoForgeRecoveryPrices.itemPurchase(item).floor();
            check(NeoForgeResourceTrades.purchaseItem(server, owner, item, 2, units -> 1) == 1
                    && knowledge(player).getEmc().equals(before.subtract(price)), "Only the delivered native-priced item is charged");
            stages.revoke(owner, A);
            denied(server, player, () -> NeoForgeResourceTrades.affordableItem(server, owner, item, 1),
                    () -> NeoForgeResourceTrades.purchaseItem(server, owner, item, 1, FORBIDDEN));
            check(knowledge(player).hasKnowledge(DIAMOND), "Stage revocation must retain ProjectE knowledge for its own transmutation table");
        } finally {
            logout(server, other);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void fluidAndEnergyPurchasesNeedStagesButReclamationStillLearnsAndCredits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try (Settings ignored = new Settings()) {
            UUID owner = player.getUUID();
            var stages = NeoForgePlayerStagesSavedData.get(server);
            var water = new FluidStack(Fluids.WATER, 1000);
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=" + A));
            NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS.set(List.of("forge:fe=" + B));
            NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(List.of("minecraft:diamond=" + A));
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.valueOf(100000));
            knowledge(player).addKnowledge(Kind.ENERGY.sample());
            denied(server, player, () -> NeoForgeResourceTrades.affordableEnergy(server, owner, 1000),
                    () -> NeoForgeResourceTrades.purchaseEnergy(server, owner, 1000, FORBIDDEN));
            check(NeoForgeResourceTrades.reclaimFluid(server, owner, water, 10, units -> 7) == 7
                    && NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER), "Selling real water must still learn while its purchase stage is absent");
            check(NeoForgeResourceTrades.reclaimItem(server, owner, new ItemStack(Items.DIAMOND), 1, units -> units) == 1
                    && knowledge(player).hasKnowledge(DIAMOND), "Item reclamation must retain native learning despite an unmet supply stage");
            check(NeoForgeResourceTrades.reclaimEnergy(server, owner, 1000, units -> units) == 1000,
                    "The FE purchase stage must not block legitimate FE reclamation");
            denied(server, player, () -> NeoForgeResourceTrades.affordableFluid(server, owner, water, 10),
                    () -> NeoForgeResourceTrades.purchaseFluid(server, owner, water, 10, FORBIDDEN));
            stages.grant(owner, A);
            stages.grant(owner, B);
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 10
                    && NeoForgeResourceTrades.purchaseFluid(server, owner, water, 10, units -> 3) == 3,
                    "Known water becomes available only after granting its own stage");
            check(NeoForgeResourceTrades.affordableEnergy(server, owner, 1500) == 1500
                    && NeoForgeResourceTrades.purchaseEnergy(server, owner, 1500, units -> 500) == 500,
                    "A learned energy certificate and energy stage jointly authorize a partial exact FE purchase");
            stages.revoke(owner, A);
            stages.revoke(owner, B);
            check(NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER) && knowledge(player).hasKnowledge(Kind.ENERGY.sample()),
                    "Revoking either independent stage must preserve medium and certificate knowledge");
            denied(server, player, () -> NeoForgeResourceTrades.affordableFluid(server, owner, water, 1),
                    () -> NeoForgeResourceTrades.purchaseFluid(server, owner, water, 1, FORBIDDEN));
            denied(server, player, () -> NeoForgeResourceTrades.affordableEnergy(server, owner, 1),
                    () -> NeoForgeResourceTrades.purchaseEnergy(server, owner, 1, FORBIDDEN));
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void offlineUuidStageChangesGateSavedKnowledgeAndSettlePurchaseOnlyOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        ServerPlayer restored = null;
        try (Settings ignored = new Settings()) {
            UUID owner = player.getUUID();
            GameProfile profile = player.getGameProfile();
            var item = new ItemStack(Items.DIAMOND);
            BigInteger price = NeoForgeRecoveryPrices.itemPurchase(item).floor();
            knowledge(player).setEmc(price.multiply(BigInteger.TEN));
            knowledge(player).addKnowledge(DIAMOND);
            NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(List.of("minecraft:diamond=" + A));
            logout(server, player);
            var stages = NeoForgePlayerStagesSavedData.get(server);
            check(NeoForgeResourceTrades.affordableItem(server, owner, item, 1) == 0, "Saved native knowledge must not bypass a missing offline personal stage");
            stages.grant(owner, A);
            CompoundTag before = ledger(server);
            check(NeoForgeResourceTrades.affordableItem(server, owner, item, 2) == 2 && ledger(server).equals(before),
                    "Offline stage-aware simulation must not create reservations or modify native snapshots");
            check(NeoForgeResourceTrades.purchaseItem(server, owner, item, 2, units -> 1) == 1
                    && ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner).getEmc().equals(price.multiply(BigInteger.TEN)),
                    "The actual offline debit must stay in the world ledger until real login");
            stages.revoke(owner, A);
            check(NeoForgeResourceTrades.purchaseItem(server, owner, item, 1, FORBIDDEN) == 0,
                    "Offline revocation must take effect before any further delivery");
            restored = login(server, profile);
            check(knowledge(restored).getEmc().equals(price.multiply(BigInteger.valueOf(9))) && knowledge(restored).hasKnowledge(DIAMOND),
                    "Login merges one exact delivered-item debit and keeps knowledge after stage revocation");
            NeoForgeEmcCredits.flush(restored);
            check(knowledge(restored).getEmc().equals(price.multiply(BigInteger.valueOf(9))), "Repeated settlement must not replay the stage-authorized purchase");
        } finally {
            logout(server, restored);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void disablingKnowledgeDoesNotBypassAnyStageGate(GameTestHelper helper) throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        Field field = NeoForgeKnowledgeConfig.class.getDeclaredField("activeRequirement");
        field.setAccessible(true);
        boolean previous = field.getBoolean(null);
        try (Settings ignored = new Settings()) {
            field.setBoolean(null, false);
            UUID owner = player.getUUID();
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.valueOf(100000));
            var item = new ItemStack(Items.DIAMOND);
            var water = new FluidStack(Fluids.WATER, 10);
            NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(List.of("minecraft:diamond=" + A));
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=" + A));
            NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS.set(List.of("forge:fe=" + A));
            NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.set(List.of("mekanism:hydrogen=" + A));
            denied(server, player, () -> NeoForgeResourceTrades.affordableItem(server, owner, item, 1),
                    () -> NeoForgeResourceTrades.purchaseItem(server, owner, item, 1, FORBIDDEN));
            denied(server, player, () -> NeoForgeResourceTrades.affordableFluid(server, owner, water, 1),
                    () -> NeoForgeResourceTrades.purchaseFluid(server, owner, water, 1, FORBIDDEN));
            denied(server, player, () -> NeoForgeResourceTrades.affordableEnergy(server, owner, 1),
                    () -> NeoForgeResourceTrades.purchaseEnergy(server, owner, 1, FORBIDDEN));
            if (ModList.get().isLoaded("mekanism")) {
                denied(server, player, () -> NeoForgeResourceTrades.affordableChemical(server, owner, HYDROGEN, tag -> false, 1),
                        () -> NeoForgeResourceTrades.purchaseChemical(server, owner, HYDROGEN, tag -> false, 1, FORBIDDEN));
            }
            NeoForgePlayerStagesSavedData.get(server).grant(owner, A);
            check(NeoForgeResourceTrades.purchaseItem(server, owner, item, 1, units -> units) == 1
                    && NeoForgeResourceTrades.purchaseFluid(server, owner, water, 1, units -> units) == 1
                    && NeoForgeResourceTrades.purchaseEnergy(server, owner, 1, units -> units) == 1,
                    "Knowledge-off permits unknown resources only after satisfying their independent stages");
            if (ModList.get().isLoaded("mekanism")) {
                check(NeoForgeResourceTrades.purchaseChemical(server, owner, HYDROGEN, tag -> false, 1, units -> units) == 1
                        && !NeoForgeRecoveryKnowledge.knowsChemical(server, owner, HYDROGEN),
                        "An unknown real chemical can be bought after the stage grant with knowledge off, without being learned");
            }
            check(!knowledge(player).hasKnowledge(DIAMOND) && !knowledge(player).hasKnowledge(Kind.ENERGY.sample())
                    && !NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER), "Purchases and stage grants must not silently learn resources");
        } finally {
            field.setBoolean(null, previous);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void quotesInvalidateForStageAbaAndEveryStageConfigurationSnapshot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try (Settings ignored = new Settings()) {
            UUID owner = player.getUUID();
            var stages = NeoForgePlayerStagesSavedData.get(server);
            stages.grant(owner, A);
            knowledge(player).setEmc(BigInteger.valueOf(100000));
            AtomicInteger calls = new AtomicInteger();
            EmcAmount quote = NeoForgeTradeQuote.stable(server, owner, () -> stages.has(owner, A), () -> {
                calls.incrementAndGet();
                stages.revoke(owner, A);
                stages.grant(owner, A);
                return EmcAmount.ONE;
            });
            check(quote.isZero() && calls.get() == 1 && stages.has(owner, A),
                    "A revoke-and-regrant ABA must invalidate the original quote without replaying its price task");
            for (var config : stageConfigs()) {
                CompoundTag ledger = ledger(server);
                calls.set(0);
                quote = NeoForgeTradeQuote.stable(server, owner, () -> stages.has(owner, A), () -> {
                    calls.incrementAndGet();
                    config.set(List.of("primordial:unrelated=" + B));
                    return EmcAmount.ONE;
                });
                check(quote.isZero() && calls.get() == 1 && stages.has(owner, A)
                        && ledger(server).equals(ledger) && knowledge(player).getEmc().equals(BigInteger.valueOf(100000)),
                        "Each stage config snapshot invalidates a quote even when the current owner's stage still authorizes it");
                config.set(List.of());
            }
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTestGenerator
    public static List<TestFunction> actualChemicalStageQuotes() {
        if (!ModList.get().isLoaded("mekanism")) return List.of();
        return List.of(new TestFunction("chemical_stage_trade", "chemical_stage_trade.revocation",
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                NeoForgeStageTradeGameTests::chemicalRevocation));
    }

    private static void chemicalRevocation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try (Settings ignored = new Settings()) {
            UUID owner = player.getUUID();
            var stages = NeoForgePlayerStagesSavedData.get(server);
            knowledge(player).setEmc(BigInteger.valueOf(100000));
            NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.set(List.of("mekanism:hydrogen=" + A));
            check(NeoForgeResourceKnowledge.validMedia(Kind.GAS, HYDROGEN), "Installed Mekanism supplies an actual registered chemical");
            check(NeoForgeResourceTrades.reclaimChemical(server, owner, HYDROGEN, tag -> false, 10, units -> 3) == 3
                    && NeoForgeRecoveryKnowledge.knowsChemical(server, owner, HYDROGEN), "Chemical selling still learns and credits without its purchase stage");
            denied(server, player, () -> NeoForgeResourceTrades.affordableChemical(server, owner, HYDROGEN, tag -> false, 1),
                    () -> NeoForgeResourceTrades.purchaseChemical(server, owner, HYDROGEN, tag -> false, 1, FORBIDDEN));
            stages.grant(owner, A);
            check(NeoForgeResourceTrades.affordableChemical(server, owner, HYDROGEN, tag -> false, 10) == 10
                    && NeoForgeResourceTrades.purchaseChemical(server, owner, HYDROGEN, tag -> false, 10, units -> 2) == 2,
                    "Learned chemical and the personal gas stage jointly authorize an ordinary partial purchase");
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of("#primordial:stage_quote_callback"));
            chemicalQuoteDenied(server, player, tag -> { stages.revoke(owner, A); return false; }, false);
            check(NeoForgeRecoveryKnowledge.knowsChemical(server, owner, HYDROGEN), "Revoking a stage must never erase chemical knowledge");
            stages.grant(owner, A);
            chemicalQuoteDenied(server, player, tag -> {
                stages.revoke(owner, A);
                stages.grant(owner, A);
                return false;
            }, false);
            chemicalQuoteDenied(server, player, tag -> {
                stages.revoke(owner, A);
                stages.grant(owner, A);
                return false;
            }, true);
            chemicalQuoteDenied(server, player, tag -> {
                NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(List.of("minecraft:diamond=" + B));
                return false;
            }, false);
            check(stages.has(owner, A) && NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.GAS, HYDROGEN),
                    "An unrelated configuration change can invalidate a quote while final chemical authorization remains true");
        } finally { logout(server, player); }
        helper.succeed();
    }

    private static void chemicalQuoteDenied(MinecraftServer server, ServerPlayer player,
                                            Predicate<ResourceLocation> change, boolean simulate) {
        AtomicInteger calls = new AtomicInteger();
        Predicate<ResourceLocation> callback = tag -> { calls.incrementAndGet(); return change.test(tag); };
        denied(server, player, () -> simulate
                        ? NeoForgeResourceTrades.affordableChemical(server, player.getUUID(), HYDROGEN, callback, 10)
                        : NeoForgeResourceTrades.purchaseChemical(server, player.getUUID(), HYDROGEN, callback, 10, FORBIDDEN),
                () -> 0);
        check(calls.get() == 1, "A rejected actual chemical tag quote must execute the external predicate exactly once");
    }

    private static void denied(MinecraftServer server, ServerPlayer player, LongSupplier quote, LongSupplier trade) {
        CompoundTag before = ledger(server);
        BigInteger balance = knowledge(player).getEmc();
        check(quote.getAsLong() == 0 && trade.getAsLong() == 0 && ledger(server).equals(before)
                && knowledge(player).getEmc().equals(balance), "Denied stage operations must leave all native EMC and transaction ledger data unchanged");
    }

    private static CompoundTag ledger(MinecraftServer server) {
        return NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess());
    }

    private static List<ModConfigSpec.ConfigValue<List<? extends String>>> stageConfigs() {
        return List.of(NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS, NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS,
                NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS, NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS);
    }

    /** 测试隔离只还原实际接触的配置；不保存、更不覆盖测试环境之外的管理员配置文件。 */
    private static final class Settings implements AutoCloseable {
        private final List<List<? extends String>> stages = stageConfigs().stream()
                .<List<? extends String>>map(value -> List.copyOf(value.get())).toList();
        private final List<? extends String> gasBlacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        private final String ratio = NeoForgeServerConfig.FE_PER_EMC.get();
        private final boolean autoLearn = NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.get();

        private Settings() {
            stageConfigs().forEach(value -> value.set(List.of()));
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(true);
        }

        @Override public void close() {
            for (int i = 0; i < stages.size(); i++) stageConfigs().get(i).set(stages.get(i));
            NeoForgeServerConfig.GAS_BLACKLIST.set(gasBlacklist);
            NeoForgeServerConfig.FE_PER_EMC.set(ratio);
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(autoLearn);
        }
    }
}
