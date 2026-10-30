/**
 * 【文件职责】验收报价期间重入修改知识与配置时，公共交易在预扣或抽取之前拒绝过期授权。
 * 【变更记录 #1】2026-10-23 13:41:49（第 1 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：无。
 * - 新增：真实化学品报价回调内遗忘/重学、购买与售出开关、黑名单修改的零交付和零资金变更回归。
 * - 修改：实际化学品场景仅在 Mekanism 存在时注册；通用报价稳定性测试使用真实 ProjectE 知识撤销。
 * - 用途：区分真实 typed 入口回归与注入报价任务的内部边界测试，确保外部标签谓词不会被重复执行。
 */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.check;
import static com.primordial.neoforge.trade.TradeTestPlayers.knowledge;
import static com.primordial.neoforge.trade.TradeTestPlayers.login;
import static com.primordial.neoforge.trade.TradeTestPlayers.logout;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.service.EmcAmount;

import moze_intel.projecte.api.ItemInfo;
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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTradeQuoteGameTests {
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");
    private static final List<String> TAG_RULE = List.of("#primordial:quote_callback_test");
    private static final LongUnaryOperator FORBIDDEN_TRANSFER = units -> {
        throw new AssertionError("A quote invalidated by a callback must never transfer resources");
    };

    private NeoForgeTradeQuoteGameTests() {}

    @GameTestGenerator
    public static List<TestFunction> actualChemicalQuoteCallbacks() {
        if (!ModList.get().isLoaded("mekanism")) return List.of();
        return List.of(new TestFunction("chemical_trade_quote", "chemical_trade_quote.policy_changes",
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                NeoForgeTradeQuoteGameTests::rejectsPolicyChangesDuringActualChemicalQuote));
    }

    private static void rejectsPolicyChangesDuringActualChemicalQuote(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        boolean buy = NeoForgeServerConfig.GAS_PURCHASE_ENABLED.get();
        boolean sell = NeoForgeServerConfig.GAS_SALE_ENABLED.get();
        List<? extends String> blacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.valueOf(10000));
            check(NeoForgeResourceKnowledge.validMedia(Kind.GAS, HYDROGEN), "The installed chemical must be registered and nonempty");
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.GAS, HYDROGEN);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_BLACKLIST.set(TAG_RULE);
            assertNoSettlement(server, player, tag -> {
                check(NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.GAS, HYDROGEN),
                        "The real tag callback must revoke an initially authorized chemical");
                return false;
            }, false);
            NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.GAS, HYDROGEN);
            long revision = NeoForgeResourceKnowledge.revision(server, owner);
            assertNoSettlement(server, player, tag -> {
                NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.GAS, HYDROGEN);
                NeoForgeResourceKnowledge.learnMedia(server, owner, Kind.GAS, HYDROGEN);
                return false;
            }, false);
            check(NeoForgeResourceKnowledge.revision(server, owner) > revision
                    && NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.GAS, HYDROGEN),
                    "Even an ABA revoke/regrant must invalidate that quote without deleting the final authorized state");
            assertNoSettlement(server, player, tag -> {
                NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(false);
                return false;
            }, false);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            assertNoSettlement(server, player, tag -> {
                NeoForgeServerConfig.GAS_BLACKLIST.set(List.of(HYDROGEN.toString()));
                return false;
            }, false);
            NeoForgeServerConfig.GAS_BLACKLIST.set(TAG_RULE);
            assertNoSettlement(server, player, tag -> {
                NeoForgeServerConfig.GAS_SALE_ENABLED.set(false);
                return false;
            }, true);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
            AtomicInteger calls = new AtomicInteger();
            check(NeoForgeResourceTrades.purchaseChemical(server, owner, HYDROGEN, tag -> {
                calls.incrementAndGet();
                return false;
            }, 10, units -> 3) == 3 && calls.get() == 1,
                    "A stable policy must allow the ordinary partial trade while executing the tag predicate exactly once");
        } finally {
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(buy);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(sell);
            NeoForgeServerConfig.GAS_BLACKLIST.set(blacklist);
            logout(server, player);
        }
        helper.succeed();
    }

    private static void assertNoSettlement(MinecraftServer server, ServerPlayer player,
                                           Predicate<ResourceLocation> change, boolean reclaim) {
        CompoundTag before = NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess());
        BigInteger balance = knowledge(player).getEmc();
        AtomicInteger calls = new AtomicInteger();
        Predicate<ResourceLocation> callback = tag -> {
            calls.incrementAndGet();
            return change.test(tag);
        };
        long actual = reclaim ? NeoForgeResourceTrades.reclaimChemical(server, player.getUUID(), HYDROGEN,
                callback, 10, FORBIDDEN_TRANSFER) : NeoForgeResourceTrades.purchaseChemical(server, player.getUUID(),
                HYDROGEN, callback, 10, FORBIDDEN_TRANSFER);
        check(actual == 0 && calls.get() == 1 && knowledge(player).getEmc().equals(balance)
                && NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess()).equals(before),
                "Policy changes during a single real quote must reject before resource transfer, debit, credit, or refund");
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void stableQuoteRechecksActualNativeItemKnowledgeAfterPriceTask(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            ItemStack sample = new ItemStack(Items.DIAMOND);
            knowledge(player).addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
            knowledge(player).setEmc(BigInteger.valueOf(10000));
            CompoundTag before = NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess());
            EmcAmount quote = NeoForgeTradeQuote.stable(server, player.getUUID(),
                    () -> NeoForgeTradeItemPolicy.allows(server, player.getUUID(), sample), () -> {
                        knowledge(player).removeKnowledge(ItemInfo.fromItem(Items.DIAMOND));
                        return EmcAmount.ONE;
                    });
            check(quote.isZero() && !knowledge(player).hasKnowledge(ItemInfo.fromItem(Items.DIAMOND))
                    && knowledge(player).getEmc().equals(BigInteger.valueOf(10000))
                    && NeoForgeEmcSavedData.get(server).save(new CompoundTag(), server.registryAccess()).equals(before),
                    "The quote task must not preserve an earlier native item authorization after real knowledge is removed");
            checksConfigurationAfterFinalAuthorizationQuery(server);
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    private static void checksConfigurationAfterFinalAuthorizationQuery(MinecraftServer server) {
        UUID owner = UUID.randomUUID();
        boolean previous = NeoForgeServerConfig.GAS_PURCHASE_ENABLED.get();
        try {
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            AtomicInteger checks = new AtomicInteger();
            EmcAmount quote = NeoForgeTradeQuote.stable(server, owner, () -> {
                if (checks.incrementAndGet() == 2) NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(false);
                return true;
            }, () -> EmcAmount.ONE);
            check(quote.isZero() && checks.get() == 2,
                    "The final authorization callback must not be able to mutate configuration after the last stability check");
        } finally {
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(previous);
        }
    }
}
