/**
 * 【文件职责】在报价与授权回调前后核对策略快照，拒绝重入修改造成的跨代授权或过期报价。
 * 【变更记录 #1】2026-10-23 13:40:26（第 1 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：无。
 * - 新增：配置开关/不可变黑名单/FE 比率、知识与价格发布代数的双向快照检查。
 * - 修改：外部报价只调用一次，随后仅复查授权和快照；知识撤销或配置修改时在预扣与交付之前拒绝。
 * - 用途：共用购买与回收稳定性边界，防标签回调遗忘资源、关闭交易或重载价表后仍沿用旧授权。
 * 【变更记录 #2】2026-10-23 16:19:30（第 2 次备注 · 2.0.0-neoforge-stages）
 * - 删除：报价只追踪知识而漏过阶段撤销或规则变更的窗口。
 * - 新增：逐玩家阶段变更代数、四类不可变阶段配置的前后快照。
 * - 修改：阶段先撤销再授回也废弃旧报价；不把阶段本身用于阻止回收或卖入学习。
 * - 用途：任务奖励/命令变更即时影响后续购买，避免外部标签回调利用旧授权预扣或交付。
 */
package com.primordial.neoforge.trade;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;

import net.minecraft.server.MinecraftServer;

final class NeoForgeTradeQuote {
    private record State(long prices, long knowledge, long stages, boolean required,
                         boolean fluidBuy, boolean fluidSell, boolean chemicalBuy, boolean chemicalSell,
                         List<? extends String> fluids, List<? extends String> chemicals,
                         List<? extends String> resources, BigInteger feRatio,
                         List<? extends String> itemStages, List<? extends String> fluidStages,
                         List<? extends String> chemicalStages, List<? extends String> resourceStages,
                         List<? extends String> nativeRatios) {}

    private NeoForgeTradeQuote() {}

    /** 一次报价、两次授权；任何外部回调改变策略都返回零，不重新执行有副作用的标签谓词。 */
    static EmcAmount stable(MinecraftServer server, UUID owner, BooleanSupplier authorized, Supplier<EmcAmount> quote) {
        State before = capture(server, owner);
        if (before.knowledge() < 0 || !authorized.getAsBoolean() || !before.equals(capture(server, owner))) return EmcAmount.ZERO;
        EmcAmount price = quote.get();
        if (price == null || price.isZero() || !before.equals(capture(server, owner))) return EmcAmount.ZERO;
        if (!authorized.getAsBoolean()) return EmcAmount.ZERO;
        return before.equals(capture(server, owner)) ? price : EmcAmount.ZERO;
    }

    private static State capture(MinecraftServer server, UUID owner) {
        return new State(NeoForgeRecoveryPrices.revision(), NeoForgeResourceKnowledge.revision(server, owner),
                NeoForgePlayerStagesSavedData.get(server).revision(owner),
                NeoForgeKnowledgeConfig.required(), NeoForgeServerConfig.fluidPurchaseEnabled(),
                NeoForgeServerConfig.fluidSaleEnabled(), NeoForgeServerConfig.gasPurchaseEnabled(),
                NeoForgeServerConfig.gasSaleEnabled(), List.copyOf(NeoForgeServerConfig.fluidBlacklist()),
                List.copyOf(NeoForgeServerConfig.gasBlacklist()), List.copyOf(NeoForgeServerConfig.resourceBlacklist()),
                NeoForgeServerConfig.fePerEmc(), List.copyOf(NeoForgeServerConfig.itemStageRequirements()),
                List.copyOf(NeoForgeServerConfig.mediaStageRequirements(false)),
                List.copyOf(NeoForgeServerConfig.mediaStageRequirements(true)),
                List.copyOf(NeoForgeServerConfig.resourceStageRequirements()),
                List.copyOf(NeoForgeServerConfig.nativeResourceRatios()));
    }
}
