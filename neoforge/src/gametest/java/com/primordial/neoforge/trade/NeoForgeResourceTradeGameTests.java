/**
 * 【文件职责】用真实 ProjectE 知识与余额验收受控资源买卖，不依赖第三方存储网络或容器界面。
 * 【变更记录 #1】2026-10-23 11:42:14（第 1 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无。
 * - 新增：未知拒买、零量不学、部分交付、离线购买重登、组件等价、知识开关、介质分离与跨线程拒收测试。
 * - 修改：每项测试使用随机账户；临时配置在 finally 恢复，不创建假账户或修改管理员价表。
 * - 用途：证明回调前的知识与数据门禁以及实际成交后的学习和结算；FE 无凭证购买保持关闭，不冒充完整系统验收。
 * 【变更记录 #2】2026-10-23 11:49:03（第 2 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无。
 * - 新增：原生水桶合法购买、非空收纳袋拒绝、只读模拟、报价期重入与重载、可选真实能量方块能力回归。
 * - 修改：化学品 ID 测试仅验收公共服务；Mekanism 在场才额外查询实际注册物品通用 FE 能力，不冒充原生化学品网络测试。
 * - 用途：保证防复制边界不会误伤固有流体桶，并使报价不通过执行零量交易消耗账户状态。
 * 【变更记录 #3】2026-10-23 13:31:26（第 3 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：把 FE 拒买解释为凭证系统尚未迁移的旧断言描述。
 * - 新增：四类只读报价入口的跨线程拒绝回归。
 * - 修改：无凭证 FE 购买继续拒绝；具备真实凭证、知识关闭和遗忘优先另由独立授权测试覆盖。
 * - 用途：保持公共交易旧回归有效，同时区分已实现的授权与尚未接入的网络或界面。
 * 【变更记录 #4】2026-10-23 13:33:37（第 4 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：未安装 Mekanism 时通过任意化学品 ID 完成正向购买的旧测试路径。
 * - 新增：无原生注册时即使低层账本有 ID 也拒买、拒卖的验证。
 * - 修改：真实化学品交易与其报价回调边界只在已安装且注册存在时运行；无模组分支不冒充原生资源交付。
 * - 用途：与系统 07 真实注册资源知识授权一致，通用纯结算另由 EMC 事务测试覆盖。
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongUnaryOperator;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeResourceTradeGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation CHEMICAL = ResourceLocation.parse("mekanism:hydrogen");
    private static final LongUnaryOperator FORBIDDEN_TRANSFER = units -> {
        throw new AssertionError("Rejected or unauthorized trade must not call the transfer callback");
    };

    private NeoForgeResourceTradeGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void itemTradeRequiresKnowledgeAndSettlesOnlyActualUnits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        boolean learning = NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.get();
        try {
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(true);
            ItemStack sample = new ItemStack(Items.DIAMOND, 7);
            ItemStack before = sample.copy();
            BigInteger buy = NeoForgeRecoveryPrices.itemPurchase(sample).floor();
            BigInteger sell = NeoForgeRecoveryPrices.item(sample).floor();
            check(buy.signum() > 0 && sell.signum() > 0, "Real ProjectE must price diamonds");
            BigInteger initial = buy.multiply(BigInteger.TEN);
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(initial);
            UUID owner = player.getUUID();
            check(NeoForgeResourceTrades.purchaseItem(server, owner, sample, 3, FORBIDDEN_TRANSFER) == 0,
                    "A funded owner must not buy an unknown item");
            check(NeoForgeResourceTrades.reclaimItem(server, owner, sample, 7, units -> 0) == 0
                    && !knowledge(player).hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)),
                    "A cancelled extraction must neither learn nor credit an item");
            check(NeoForgeResourceTrades.reclaimItem(server, owner, sample, 7, units -> 2) == 2
                    && knowledge(player).hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)),
                    "Only an actual positive sale must teach the real ProjectE item knowledge");
            check(knowledge(player).getEmc().equals(initial.add(sell.multiply(BigInteger.TWO))),
                    "Partial item extraction must credit precisely two native sell prices");
            AtomicInteger calls = new AtomicInteger();
            check(NeoForgeResourceTrades.purchaseItem(server, owner, sample, 3, units -> {
                check(units == 3, "The callback must receive the authorized item quantity");
                calls.incrementAndGet();
                return 2;
            }) == 2 && calls.get() == 1, "A partial purchase must execute once and report two delivered units");
            check(knowledge(player).getEmc().equals(initial.add(sell.multiply(BigInteger.TWO)).subtract(buy.multiply(BigInteger.TWO))),
                    "Only delivered items must be charged, after refunding the unused reservation");
            check(sample.getCount() == before.getCount() && ItemStack.isSameItemSameComponents(sample, before),
                    "The service must not mutate the caller's source sample");
        } finally {
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(learning);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void itemGenerationRejectsStrippedPayloadAndKeepsNativeSafeVariant(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            knowledge(player).setEmc(BigInteger.TEN.pow(20));
            ItemStack container = new ItemStack(Items.SHULKER_BOX);
            container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
            ItemInfo normalized = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(container));
            check(!ItemStack.isSameItemSameComponents(container, normalized.createStack()),
                    "Default ProjectE policy must strip this unpaid container payload");
            knowledge(player).addKnowledge(normalized);
            check(NeoForgeResourceTrades.purchaseItem(server, player.getUUID(), container, 1, FORBIDDEN_TRANSFER) == 0,
                    "Knowledge of a normalized container must not permit copying a filled source sample");
            ItemStack potion = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            ItemInfo safe = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(potion));
            check(ItemStack.isSameItemSameComponents(potion, safe.createStack())
                    && !ItemStack.isSameItemSameComponents(potion, new ItemStack(Items.POTION)),
                    "A real mapped water-potion variant must retain its safe native component semantics");
            BigInteger price = NeoForgeRecoveryPrices.itemPurchase(potion).floor();
            check(price.signum() > 0, "Real ProjectE must price the safe water-potion variant");
            knowledge(player).addKnowledge(safe);
            BigInteger before = knowledge(player).getEmc();
            check(NeoForgeResourceTrades.purchaseItem(server, player.getUUID(), potion, 1, units -> units) == 1
                    && knowledge(player).getEmc().equals(before.subtract(price)),
                    "Persistent safe components must remain purchasable at the native variant price");
            ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
            knowledge(player).addKnowledge(IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(bucket)));
            BigInteger bucketPrice = NeoForgeRecoveryPrices.itemPurchase(bucket).floor();
            check(bucketPrice.signum() > 0 && NeoForgeResourceTrades.purchaseItem(server, player.getUUID(), bucket, 1, units -> units) == 1,
                    "A native water bucket's inherent contents must remain purchasable as an item, at ProjectE's item price");
            ItemStack bundle = new ItemStack(Items.BUNDLE);
            bundle.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(new ItemStack(Items.DIAMOND))));
            knowledge(player).setFullKnowledge(true);
            check(!NeoForgeTradeItemPolicy.allows(server, player.getUUID(), bundle)
                    && NeoForgeResourceTrades.purchaseItem(server, player.getUUID(), bundle, 1, FORBIDDEN_TRANSFER) == 0,
                    "Even full knowledge must not authorize generation of a non-empty bundle");
            if (ModList.get().isLoaded("mekanism")) {
                ResourceLocation cubeId = ResourceLocation.parse("mekanism:basic_energy_cube");
                check(BuiltInRegistries.ITEM.containsKey(cubeId), "The installed Mekanism must register its real basic energy cube item");
                ItemStack cube = new ItemStack(BuiltInRegistries.ITEM.get(cubeId));
                var energy = cube.getCapability(Capabilities.EnergyStorage.ITEM);
                check(energy != null && energy.receiveEnergy(1000, false) > 0 && energy.getEnergyStored() > 0,
                        "This optional test must charge the actual Mekanism item's NeoForge FE capability");
                check(!NeoForgeTradeItemPolicy.allows(server, player.getUUID(), cube)
                        && NeoForgeResourceTrades.purchaseItem(server, player.getUUID(), cube, 1, FORBIDDEN_TRANSFER) == 0,
                        "A charged actual optional energy item must not pass the generation safety policy");
            }
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void offlineItemPurchaseUsesSavedKnowledgeAndAppliesDebitOnLoginOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer original = login(server);
        ServerPlayer restored = null;
        try {
            ItemStack sample = new ItemStack(Items.DIAMOND);
            BigInteger price = NeoForgeRecoveryPrices.itemPurchase(sample).floor();
            BigInteger initial = price.multiply(BigInteger.TEN);
            knowledge(original).setEmc(initial);
            knowledge(original).addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
            GameProfile profile = original.getGameProfile();
            UUID owner = original.getUUID();
            logout(server, original);
            check(NeoForgeResourceTrades.purchaseItem(server, owner, sample, 3, units -> 2) == 2,
                    "An offline owner with saved native knowledge and balance must be able to buy actual units");
            check(ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner).getEmc().equals(initial),
                    "Offline purchases must not mutate ProjectE's read-only snapshot");
            check(NeoForgeEmcSavedData.get(server).debit(owner).signum() > 0,
                    "The world ledger must retain the offline debit for login settlement");
            restored = login(server, profile);
            BigInteger expected = initial.subtract(price.multiply(BigInteger.TWO));
            check(knowledge(restored).getEmc().equals(expected), "The real login must merge the exact delivered-item debit");
            NeoForgeEmcCredits.flush(restored);
            NeoForgeEmcCredits.flush(restored);
            check(knowledge(restored).getEmc().equals(expected)
                    && NeoForgeEmcSavedData.get(server).debit(owner).signum() == 0,
                    "Repeated login settlement must not replay an acknowledged purchase debit");
        } finally {
            logout(server, restored);
            logout(server, original);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void itemSaleRespectsLearningSwitchAndKeepsOriginalResourceIdentity(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        boolean learning = NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.get();
        try {
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(false);
            knowledge(player).clearKnowledge();
            knowledge(player).setEmc(BigInteger.ZERO);
            ItemStack sample = new ItemStack(Items.DIAMOND, 4);
            check(NeoForgeResourceTrades.reclaimItem(server, player.getUUID(), sample, 1, units -> units) == 1
                    && knowledge(player).getEmc().signum() > 0
                    && !knowledge(player).hasKnowledge(ItemInfo.fromItem(Items.DIAMOND)),
                    "Disabling auto-learning must retain valid sale credit without teaching item knowledge");
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(true);
            ItemStack mutable = new ItemStack(Items.EMERALD, 3);
            check(NeoForgeResourceTrades.reclaimItem(server, player.getUUID(), mutable, 1, units -> {
                mutable.setCount(0);
                return units;
            }) == 1 && knowledge(player).hasKnowledge(ItemInfo.fromItem(Items.EMERALD)),
                    "Learning after a callback must use the immutable pre-transfer resource identity");
            check(NeoForgeResourceTrades.purchaseItem(server, player.getUUID(), new ItemStack(Items.DIRT), 0, FORBIDDEN_TRANSFER) == 0,
                    "A zero request must never invoke transfer or teach any item");
        } finally {
            NeoForgeServerConfig.AUTO_LEARN_RECLAIMED_ITEMS.set(learning);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void fluidTradeLearnsOnlyAcceptedTypeAndRejectsAdditionalComponents(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            UUID owner = player.getUUID();
            FluidStack sample = new FluidStack(Fluids.WATER, 1000);
            knowledge(player).setEmc(BigInteger.ZERO);
            check(!NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER), "Each random owner must begin without water knowledge");
            check(NeoForgeResourceTrades.purchaseFluid(server, owner, sample, 1000, FORBIDDEN_TRANSFER) == 0,
                    "Unknown water must not be generated");
            check(NeoForgeResourceTrades.reclaimFluid(server, owner, sample, 1000, units -> 0) == 0
                    && !NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER), "Cancelled water sale must not teach water");
            long actual = NeoForgeResourceTrades.reclaimFluid(server, owner, sample, 1000, units -> 37);
            check(actual == 37 && NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER),
                    "Actual water reception must teach only the water media identity");
            EmcAmount sold = NeoForgeRecoveryPrices.fluid(sample).multiply(37);
            BigInteger before = knowledge(player).getEmc();
            check(EmcAmount.of(before).add(NeoForgeEmcSavedData.get(server).pending(owner)).equals(sold),
                    "Partial media sale must retain its exact EMC value across the account and fractional ledger");
            FluidStack decorated = sample.copy();
            decorated.set(DataComponents.CUSTOM_NAME, Component.literal("unpriced fluid payload"));
            check(NeoForgeResourceTrades.purchaseFluid(server, owner, decorated, 1, FORBIDDEN_TRANSFER) == 0,
                    "ID-only media knowledge must not authorize unpriced component payload generation");
            long units = NeoForgeResourceTrades.purchaseFluid(server, owner, sample, 1000, authorized -> Math.min(11, authorized));
            check(units > 0 && units <= 11 && sample.getAmount() == 1000 && sample.isComponentsPatchEmpty(),
                    "A known media purchase must settle the actual partial quantity and leave its sample unchanged");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void chemicalTradeUsesIndependentKnowledgeAndFailClosedTagLookup(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        List<? extends String> blocked = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.valueOf(10000));
            if (!ModList.get().isLoaded("mekanism")) {
                NeoForgeRecoveryKnowledge.recordChemical(server, owner, CHEMICAL);
                check(NeoForgeResourceTrades.affordableChemical(server, owner, CHEMICAL, tag -> false, 1) == 0
                        && NeoForgeResourceTrades.purchaseChemical(server, owner, CHEMICAL, tag -> false, 1, FORBIDDEN_TRANSFER) == 0
                        && NeoForgeResourceTrades.reclaimChemical(server, owner, CHEMICAL, tag -> false, 1, FORBIDDEN_TRANSFER) == 0,
                        "A low-level saved ID cannot authorize nonexistent chemical generation or reclamation without its native registry");
                helper.succeed();
                return;
            }
            check(NeoForgeResourceTrades.purchaseChemical(server, owner, CHEMICAL, tag -> false, 1, FORBIDDEN_TRANSFER) == 0,
                    "An unfamilar chemical must remain unknown even for a funded owner");
            check(NeoForgeResourceTrades.reclaimChemical(server, owner, CHEMICAL, tag -> false, 10, units -> 0) == 0
                    && !NeoForgeRecoveryKnowledge.knowsChemical(server, owner, CHEMICAL),
                    "A zero chemical extraction must not teach chemical knowledge");
            check(NeoForgeResourceTrades.reclaimChemical(server, owner, CHEMICAL, tag -> false, 10, units -> 3) == 3
                    && NeoForgeRecoveryKnowledge.knowsChemical(server, owner, CHEMICAL)
                    && !NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER),
                    "Chemical knowledge must be learned independently of item and fluid knowledge");
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of("#primordial:trade_test"));
            check(NeoForgeResourceTrades.purchaseChemical(server, owner, CHEMICAL, tag -> {
                throw new IllegalStateException("Expected failed optional tag lookup");
            }, 1, FORBIDDEN_TRANSFER) == 0, "A failed optional tag lookup must reject before any transfer");
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
            check(NeoForgeResourceTrades.purchaseChemical(server, owner, CHEMICAL, tag -> false, 10, units -> 2) == 2,
                    "A known chemical must support ordinary partial delivery through the generic core");
        } finally {
            NeoForgeServerConfig.GAS_BLACKLIST.set(blocked);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void energySalePreservesFractionAndPurchaseRequiresLearnedCertificate(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        String ratio = NeoForgeServerConfig.FE_PER_EMC.get();
        try {
            NeoForgeServerConfig.FE_PER_EMC.set("1000");
            knowledge(player).setEmc(BigInteger.ZERO);
            UUID owner = player.getUUID();
            check(NeoForgeResourceTrades.reclaimEnergy(server, owner, 2000, units -> 1500) == 1500,
                    "Energy reclamation must count the actual FE quantity");
            check(knowledge(player).getEmc().equals(BigInteger.ONE)
                    && NeoForgeEmcSavedData.get(server).pending(owner).equals(new EmcAmount(BigInteger.ONE, BigInteger.TWO)),
                    "1500 FE must credit one EMC and keep one exact half in the ledger");
            check(NeoForgeResourceTrades.purchaseEnergy(server, owner, 500, FORBIDDEN_TRANSFER) == 0,
                    "An owner without learned energy authorization must not generate FE");
        } finally {
            NeoForgeServerConfig.FE_PER_EMC.set(ratio);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void everyTypedTradeRejectsCrossThreadBeforeCallbackOrKnowledgeMutation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        List<Runnable> attempts = List.of(
                () -> NeoForgeResourceTrades.purchaseItem(server, owner, new ItemStack(Items.DIAMOND), 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.reclaimItem(server, owner, new ItemStack(Items.DIAMOND), 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.purchaseFluid(server, owner, new FluidStack(Fluids.WATER, 1), 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.reclaimFluid(server, owner, new FluidStack(Fluids.WATER, 1), 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.purchaseChemical(server, owner, CHEMICAL, tag -> false, 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.reclaimChemical(server, owner, CHEMICAL, tag -> false, 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.purchaseEnergy(server, owner, 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.reclaimEnergy(server, owner, 1, FORBIDDEN_TRANSFER),
                () -> NeoForgeResourceTrades.affordableItem(server, owner, new ItemStack(Items.DIAMOND), 1),
                () -> NeoForgeResourceTrades.affordableFluid(server, owner, new FluidStack(Fluids.WATER, 1), 1),
                () -> NeoForgeResourceTrades.affordableChemical(server, owner, CHEMICAL, tag -> false, 1),
                () -> NeoForgeResourceTrades.affordableEnergy(server, owner, 1));
        for (Runnable attempt : attempts) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    attempt.run();
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            }, "primordial-invalid-resource-trade");
            worker.setDaemon(true);
            worker.start();
            try {
                worker.join(5000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while verifying rejected thread", interrupted);
            }
            check(!worker.isAlive() && failure.get() instanceof IllegalStateException,
                    "Every resource operation must reject a worker thread before resolving knowledge or transferring resources");
        }
        check(NeoForgeEmcSavedData.get(server).pending(owner).isZero()
                && !NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER)
                && !NeoForgeRecoveryKnowledge.knowsChemical(server, owner, CHEMICAL),
                "Rejected worker-thread operations must leave no credit or learned media");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void quotesAreReadOnlyAndRejectReentryAndMidQuoteReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        List<? extends String> blacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());
        try {
            UUID owner = player.getUUID();
            ItemStack item = new ItemStack(Items.DIAMOND);
            knowledge(player).clearKnowledge();
            BigInteger price = NeoForgeRecoveryPrices.itemPurchase(item).floor();
            knowledge(player).setEmc(price.multiply(BigInteger.TWO));
            NeoForgeEmcCredits.credit(server, owner, new EmcAmount(BigInteger.ONE, BigInteger.valueOf(3)));
            var ledger = NeoForgeEmcSavedData.get(server);
            var before = ledger.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess());
            BigInteger balance = knowledge(player).getEmc();
            check(NeoForgeResourceTrades.affordableItem(server, owner, item, 10) == 0,
                    "A funded unknown-item simulation must authorize zero units");
            knowledge(player).addKnowledge(ItemInfo.fromItem(Items.DIAMOND));
            check(NeoForgeResourceTrades.affordableItem(server, owner, item, 10) == 2,
                    "Known-item simulation must apply the exact real account plus fraction budget");
            FluidStack water = new FluidStack(Fluids.WATER, 1);
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 0,
                    "Item knowledge must not authorize unknown fluid simulation");
            NeoForgeRecoveryKnowledge.recordFluid(server, owner, WATER);
            check(NeoForgeResourceTrades.affordableFluid(server, owner, water, 10) == 10,
                    "Known-fluid simulation must use the same native buy gate as actual purchase");
            check(NeoForgeResourceTrades.affordableChemical(server, owner, CHEMICAL, tag -> false, 10) == 0,
                    "Fluid knowledge must not authorize chemical simulation");
            if (ModList.get().isLoaded("mekanism")) {
                NeoForgeRecoveryKnowledge.recordChemical(server, owner, CHEMICAL);
                check(NeoForgeResourceTrades.affordableChemical(server, owner, CHEMICAL, tag -> false, 10) == 10,
                        "Known registered-chemical simulation must authorize affordable units without a transfer callback");
            }
            check(knowledge(player).getEmc().equals(balance)
                    && ledger.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()).equals(before),
                    "Read-only quotes must not reserve, refund, flush, or otherwise change the EMC ledger or balance");
            if (!ModList.get().isLoaded("mekanism")) {
                helper.succeed();
                return;
            }
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of("#primordial:trade_test"));
            check(NeoForgeResourceTrades.affordableChemical(server, owner, CHEMICAL, tag -> {
                check(NeoForgeResourceTrades.affordableItem(server, owner, item, 1) == 0
                        && NeoForgeResourceTrades.reclaimEnergy(server, owner, 1, FORBIDDEN_TRANSFER) == 0,
                        "The guard must reject nested typed queries and transactions while resolving an external tag");
                return false;
            }, 10) == 10, "A non-blocking tag must still permit the outer read-only quote");
            long revision = NeoForgeRecoveryPrices.revision();
            check(NeoForgeResourceTrades.purchaseChemical(server, owner, CHEMICAL, tag -> {
                NeoForgeRecoveryPrices.reload();
                return false;
            }, 1, FORBIDDEN_TRANSFER) == 0 && NeoForgeRecoveryPrices.revision() > revision,
                    "A callback that republishes prices must invalidate the outer quote before any transfer or charge");
            check(knowledge(player).getEmc().equals(balance)
                    && ledger.save(new net.minecraft.nbt.CompoundTag(), server.registryAccess()).equals(before),
                    "Rejected quote-generation changes must also leave the original account and fractional ledger unchanged");
        } finally {
            NeoForgeServerConfig.GAS_BLACKLIST.set(blacklist);
            logout(server, player);
        }
        helper.succeed();
    }
}
