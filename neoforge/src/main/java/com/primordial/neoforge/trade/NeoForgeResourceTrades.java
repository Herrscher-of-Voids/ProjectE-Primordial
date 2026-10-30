/**
 * 【文件职责】给真实交付回调统一提供物品、流体、化学品和 FE 双向交易；所有数量只由 EMC 事务裁决。
 * 【变更记录 #1】2026-10-23 11:39:43（第 1 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无。
 * - 新增：原生知识购买门禁、ProjectE 持久组件等价校验、按实际接收量回收与成功后学习。
 * - 修改：物品报价和知识读取使用防御副本，流体只接受不携带额外组件的生成样本；第三方化学品仅传注册 ID 和标签谓词。
 * - 用途：自动化与界面复用同一结算，不抽取桶等物品容器内容；回调必须同步只交付授权数量，模拟应在调用前完成。
 * 【变更记录 #2】2026-10-23 11:46:22（第 2 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：本类内重复的物品规范化和知识解析，交给独立安全策略。
 * - 新增：只读 affordableItem/Fluid/Chemical、覆盖报价和知识回调的同账户重入门禁及价表发布代数校验。
 * - 修改：模拟与执行共用购买报价；跨报价代数拒绝成交，事务在 finally 释放会话引用，FE 生成等待系统 11 凭证。
 * - 用途：供后续网络先模拟后执行，不通过执行零量交易伪装模拟，也不让外部报价回调递归交易。
 * 【变更记录 #3】2026-10-23 13:30:27（第 3 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：FE 购买恒定返回零、介质购买只认逐种学习的临时迁移限制。
 * - 新增：统一知识 API 授权与凭证控制的 FE 购买、affordableEnergy 只读模拟。
 * - 修改：流体和化学品先检查遗忘及类别/逐种授权，FE 保留原生价格黑名单、精确预扣及实际数量退款。
 * - 用途：系统 07/11 授权与公共交易闭环一致；知识关闭不绕过物品安全、介质遗忘或资源报价限制。
 * 【变更记录 #4】2026-10-23 13:33:37（第 4 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：公共回收入口仅凭注册名文本接受不存在介质的路径。
 * - 新增：回收前复用知识 API 的真实源流体与化学品注册校验。
 * - 修改：可选模组缺失、未知 ID 或流动流体不会调用抽取或学习；黑洞自身接收实现保持独立。
 * - 用途：卖入的真实资源边界与购买目录一致，不将任意字符串转换为玩家收益或新知识。
 * 【变更记录 #5】2026-10-23 13:40:26（第 5 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：只比价表代数而漏过报价回调内知识撤销和配置开关变更的检查。
 * - 新增：购买、只读模拟和回收共用 NeoForgeTradeQuote 稳定策略快照与报价后的授权复查。
 * - 修改：健康玩家知识代数、配置及安全条件需在预扣或抽取前一致；标签报价只执行一次，不通过重试重放回调。
 * - 用途：拒绝已遗忘资源、已关闭买卖或损坏知识账户在回调重入后继续成交，避免错扣和丢学习。
 */
package com.primordial.neoforge.trade;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.LongUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.emc.NeoForgeEmcTransactions;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeResourceTrades {
    private record Account(MinecraftServer server, UUID owner) {}
    private static final Set<Account> ACTIVE = ConcurrentHashMap.newKeySet();

    private NeoForgeResourceTrades() {}

    /** Authorized exact prices for read-only JEI quotes and knowledge crafting. */
    public static EmcAmount purchaseItemPrice(MinecraftServer server, UUID owner, ItemStack sample) {
        return guardedPrice(server, owner, () -> itemPurchasePrice(server, owner, sample));
    }

    public static EmcAmount purchaseFluidPrice(MinecraftServer server, UUID owner, FluidStack sample) {
        return guardedPrice(server, owner, () -> fluidPurchasePrice(server, owner, sample));
    }

    public static EmcAmount purchaseChemicalPrice(MinecraftServer server, UUID owner, ResourceLocation id,
                                                 Predicate<ResourceLocation> inTag) {
        return guardedPrice(server, owner, () -> chemicalPurchasePrice(server, owner, id, inTag));
    }

    private static EmcAmount guardedPrice(MinecraftServer server, UUID owner, Supplier<EmcAmount> operation) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null) return EmcAmount.ZERO;
        Account account = new Account(server, owner);
        if (!ACTIVE.add(account)) return EmcAmount.ZERO;
        try { return operation.get(); }
        finally { ACTIVE.remove(account); }
    }

    /** 样本必须已是 ProjectE 可持久生成的形态；不能拿基础物品价免费复制被剥离的容器或充能数据。 */
    public static long purchaseItem(MinecraftServer server, UUID owner, ItemStack sample,
                                   long requested, LongUnaryOperator transfer) {
        return guarded(server, owner, requested, () -> transfer == null ? 0 : NeoForgeEmcTransactions.purchase(
                server, owner, itemPurchasePrice(server, owner, sample), requested, transfer));
    }

    public static long affordableItem(MinecraftServer server, UUID owner, ItemStack sample, long requested) {
        return guarded(server, owner, requested, () -> NeoForgeEmcTransactions.affordable(
                server, owner, itemPurchasePrice(server, owner, sample), requested));
    }

    /** 整件物品沿用原生售价，不把桶、储能器等容器重解释为流体或能源交易。 */
    public static long reclaimItem(MinecraftServer server, UUID owner, ItemStack sample,
                                  long requested, LongUnaryOperator extract) {
        return guarded(server, owner, requested, () -> {
            if (extract == null || sample == null || sample.isEmpty() || !NeoForgeRecoveryKnowledge.available(server)) return 0;
            ItemStack copy = sample.copyWithCount(1);
            EmcAmount price = NeoForgeTradeQuote.stable(server, owner,
                    () -> NeoForgeRecoveryKnowledge.available(server), () -> NeoForgeRecoveryPrices.item(copy));
            long actual = NeoForgeEmcTransactions.reclaim(server, owner, price, requested, extract);
            if (actual > 0 && NeoForgeServerConfig.autoLearnReclaimedItems())
                NeoForgeRecoveryKnowledge.recordItem(server, owner, copy);
            return actual;
        });
    }

    /** Read-only counterpart for storage adapters that must answer an insert simulation. */
    public static long reclaimableItem(MinecraftServer server, UUID owner, ItemStack sample, long requested) {
        return guarded(server, owner, requested, () -> {
            if (sample == null || sample.isEmpty() || !NeoForgeRecoveryKnowledge.available(server)
                    || !NeoForgeEmcSavedData.get(server).canReclaim(owner)) return 0;
            ItemStack copy = sample.copyWithCount(1);
            EmcAmount price = NeoForgeTradeQuote.stable(server, owner,
                    () -> NeoForgeRecoveryKnowledge.available(server), () -> NeoForgeRecoveryPrices.item(copy));
            return price.isZero() ? 0 : requested;
        });
    }

    public static long purchaseFluid(MinecraftServer server, UUID owner, FluidStack sample,
                                    long requested, LongUnaryOperator transfer) {
        return guarded(server, owner, requested, () -> transfer == null ? 0 : NeoForgeEmcTransactions.purchase(
                server, owner, fluidPurchasePrice(server, owner, sample), requested, transfer));
    }

    public static long affordableFluid(MinecraftServer server, UUID owner, FluidStack sample, long requested) {
        return guarded(server, owner, requested, () -> NeoForgeEmcTransactions.affordable(
                server, owner, fluidPurchasePrice(server, owner, sample), requested));
    }

    public static long reclaimFluid(MinecraftServer server, UUID owner, FluidStack sample,
                                   long requested, LongUnaryOperator extract) {
        return guarded(server, owner, requested, () -> {
            if (extract == null || sample == null || sample.isEmpty() || !NeoForgeRecoveryKnowledge.available(server)) return 0;
            FluidStack copy = sample.copyWithAmount(1);
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(copy.getFluid());
            EmcAmount price = NeoForgeTradeQuote.stable(server, owner,
                    () -> NeoForgeResourceKnowledge.validMedia(Kind.FLUID, id) && NeoForgeRecoveryKnowledge.available(server),
                    () -> NeoForgeRecoveryPrices.fluid(copy));
            long actual = NeoForgeEmcTransactions.reclaim(server, owner, price, requested, extract);
            if (actual > 0) NeoForgeRecoveryKnowledge.recordFluid(server, owner, id);
            return actual;
        });
    }

    /** Read-only sale quote; real acceptance and learning remain in reclaimFluid. */
    public static long reclaimableFluid(MinecraftServer server, UUID owner, FluidStack sample, long requested) {
        return guarded(server, owner, requested, () -> {
            if (sample == null || sample.isEmpty() || !NeoForgeRecoveryKnowledge.available(server)
                    || !NeoForgeEmcSavedData.get(server).canReclaim(owner)) return 0;
            FluidStack copy = sample.copyWithAmount(1);
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(copy.getFluid());
            EmcAmount price = NeoForgeTradeQuote.stable(server, owner,
                    () -> NeoForgeResourceKnowledge.validMedia(Kind.FLUID, id) && NeoForgeRecoveryKnowledge.available(server),
                    () -> NeoForgeRecoveryPrices.fluid(copy));
            return price.isZero() ? 0 : requested;
        });
    }

    /** 兼容层负责确认真实注册 ID 和同种交付；本层无 Mekanism 类型，未安装时仍可安全加载。 */
    public static long purchaseChemical(MinecraftServer server, UUID owner, ResourceLocation id,
                                       Predicate<ResourceLocation> inTag, long requested, LongUnaryOperator transfer) {
        return guarded(server, owner, requested, () -> transfer == null ? 0 : NeoForgeEmcTransactions.purchase(
                server, owner, chemicalPurchasePrice(server, owner, id, inTag), requested, transfer));
    }

    public static long affordableChemical(MinecraftServer server, UUID owner, ResourceLocation id,
                                         Predicate<ResourceLocation> inTag, long requested) {
        return guarded(server, owner, requested, () -> NeoForgeEmcTransactions.affordable(
                server, owner, chemicalPurchasePrice(server, owner, id, inTag), requested));
    }

    public static long reclaimChemical(MinecraftServer server, UUID owner, ResourceLocation id,
                                      Predicate<ResourceLocation> inTag, long requested, LongUnaryOperator extract) {
        return guarded(server, owner, requested, () -> {
            if (extract == null || id == null || !NeoForgeResourceKnowledge.validMedia(Kind.GAS, id)
                    || !NeoForgeRecoveryKnowledge.available(server)) return 0;
            EmcAmount price = NeoForgeTradeQuote.stable(server, owner,
                    () -> NeoForgeResourceKnowledge.validMedia(Kind.GAS, id) && NeoForgeRecoveryKnowledge.available(server),
                    () -> NeoForgeRecoveryPrices.chemical(id, inTag));
            long actual = NeoForgeEmcTransactions.reclaim(server, owner, price, requested, extract);
            if (actual > 0) NeoForgeRecoveryKnowledge.recordChemical(server, owner, id);
            return actual;
        });
    }

    public static long purchaseEnergy(MinecraftServer server, UUID owner, long requested, LongUnaryOperator transfer) {
        return guarded(server, owner, requested, () -> transfer == null ? 0 : NeoForgeEmcTransactions.purchase(
                server, owner, energyPurchasePrice(server, owner), requested, transfer));
    }

    public static long affordableEnergy(MinecraftServer server, UUID owner, long requested) {
        return guarded(server, owner, requested, () -> NeoForgeEmcTransactions.affordable(
                server, owner, energyPurchasePrice(server, owner), requested));
    }

    public static long reclaimEnergy(MinecraftServer server, UUID owner, long requested, LongUnaryOperator extract) {
        return guarded(server, owner, requested, () -> extract == null ? 0 : NeoForgeEmcTransactions.reclaim(
                server, owner, NeoForgeTradeQuote.stable(server, owner, () -> true, NeoForgeRecoveryPrices::energy),
                requested, extract));
    }

    private static long guarded(MinecraftServer server, UUID owner, long requested, LongSupplier operation) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || requested <= 0) return 0;
        Account account = new Account(server, owner);
        if (!ACTIVE.add(account)) return 0;
        try {
            return operation.getAsLong();
        } finally {
            ACTIVE.remove(account);
        }
    }

    public static long purchaseResource(MinecraftServer server, UUID owner, ResourceLocation unit,
                                        long requested, LongUnaryOperator transfer) {
        return guarded(server, owner, requested, () -> transfer == null ? 0 : NeoForgeEmcTransactions.purchase(
                server, owner, NeoForgeTradeQuote.stable(server, owner,
                        () -> NeoForgeResourceKnowledge.allows(server, owner, unit),
                        () -> com.primordial.neoforge.resource.NeoForgeResourceUnits.price(unit)), requested, transfer));
    }

    public static long reclaimResource(MinecraftServer server, UUID owner, ResourceLocation unit,
                                       long requested, LongUnaryOperator extract) {
        return guarded(server, owner, requested, () -> extract == null ? 0 : NeoForgeEmcTransactions.reclaim(
                server, owner, NeoForgeTradeQuote.stable(server, owner,
                        () -> com.primordial.neoforge.resource.NeoForgeResourceUnits.known(unit),
                        () -> com.primordial.neoforge.resource.NeoForgeResourceUnits.price(unit)), requested, extract));
    }

    private static EmcAmount itemPurchasePrice(MinecraftServer server, UUID owner, ItemStack sample) {
        if (sample == null || sample.isEmpty()) return EmcAmount.ZERO;
        ItemStack copy = sample.copyWithCount(1);
        return NeoForgeTradeQuote.stable(server, owner, () -> NeoForgeTradeItemPolicy.allows(server, owner, copy),
                () -> NeoForgeRecoveryPrices.itemPurchase(copy));
    }

    private static EmcAmount fluidPurchasePrice(MinecraftServer server, UUID owner, FluidStack sample) {
        if (sample == null || sample.isEmpty()) return EmcAmount.ZERO;
        FluidStack copy = sample.copyWithAmount(1);
        // 流体知识按 ID 保存，尚无组件定价；不能以 ID 的价格生成额外数据。
        if (!copy.getFluid().defaultFluidState().isSource()
                || !FluidStack.isSameFluidSameComponents(copy, new FluidStack(copy.getFluid(), 1))) return EmcAmount.ZERO;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(copy.getFluid());
        return NeoForgeTradeQuote.stable(server, owner,
                () -> NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.FLUID, id),
                () -> NeoForgeRecoveryPrices.fluidPurchase(copy));
    }

    private static EmcAmount chemicalPurchasePrice(MinecraftServer server, UUID owner, ResourceLocation id,
                                                   Predicate<ResourceLocation> inTag) {
        return NeoForgeTradeQuote.stable(server, owner,
                () -> id != null && NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.GAS, id),
                () -> NeoForgeRecoveryPrices.chemicalPurchase(id, inTag));
    }

    private static EmcAmount energyPurchasePrice(MinecraftServer server, UUID owner) {
        return NeoForgeTradeQuote.stable(server, owner,
                () -> NeoForgeResourceKnowledge.allows(server, owner, Kind.ENERGY), NeoForgeRecoveryPrices::energy);
    }
}
