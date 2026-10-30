/**
 * 【文件职责】统一物品、逐种介质和 11 类凭证的服务端知识授权；本层不报价或交付资源。
 * 【变更记录 #1】2026-10-23 13:34:30（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：原生在线/离线知识读取、类别凭证归一化、有效资源校验及遗忘优先规则。
 * - 修改：免知识仅跳过知识要求，损坏账本和显式遗忘继续拒绝；玩家遗忘必须已有购买授权。
 * - 用途：公共交易和整合包 API 复用同一规则，不替代物品安全、价格或后续作者阶段检查。
 * 【变更记录 #2】2026-10-23 13:39:36（第 2 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：只读 revision(server,owner)；坏档或非法线程返回 -1。
 * - 修改：授权序号由账本真实变更发布，不能由查询或同值授予触发。
 * - 用途：公共交易在外部回调后核对知识快照，发现遗忘/重新学习的同结果变化。
 * 【变更记录 #3】2026-10-23 16:19:30（第 3 次备注 · 2.0.0-neoforge-stages）
 * - 删除：仅凭知识或免知识配置就能绕过整合包阶段的购买授权。
 * - 新增：物品补充、流体/化学品和独立资源单位的公共阶段前置校验。
 * - 修改：阶段与知识分别判定，授予阶段不学习资源，真实回收和作者知识授予不经过阶段。
 * - 用途：现有公共购买/只读模拟及后续目录共用门禁，不干预 ProjectE 原生物品取出。
 */
package com.primordial.neoforge.knowledge;

import java.util.UUID;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledgeSavedData;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.registry.NeoForgeCertificates;
import com.primordial.neoforge.stage.NeoForgeResourceStages;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;

public final class NeoForgeResourceKnowledge {
    public enum Kind {
        FLUID("fluid", null), GAS("gas", null), ENERGY("energy", "forge:fe"),
        MANA("mana", "botania:mana"), PRESSURE("pressure", "pneumaticcraft:pressure"),
        AURA("aura", "naturesaura:aura"), SOURCE("source", "ars_nouveau:source"),
        SPEED("speed", "create:rotation_speed"), STRESS("stress", "create:stress"),
        IRON_MANA("iron_mana", "irons_spellbooks:mana"), SOUL_ENERGY("soul_energy", "goety:soul_energy");

        private final String id;
        private final ResourceLocation unit;
        Kind(String id, String unit) { this.id = id; this.unit = unit == null ? null : ResourceLocation.parse(unit); }
        public String itemId() { return id + "_certificate"; }
        public ResourceLocation unit() { return unit; }
        public ItemStack sample() { return new ItemStack(NeoForgeCertificates.CERTIFICATES.get(this).get()); }
    }

    private NeoForgeResourceKnowledge() {}

    public static long revision(MinecraftServer server, UUID owner) {
        return ready(server, owner) ? NeoForgeRecoveryKnowledgeSavedData.get(server).revision(owner) : -1L;
    }

    /** 检查原初补充的阶段和知识；调用方仍须保留容器内容、组件和附件防复制验证。 */
    public static boolean allowsItem(MinecraftServer server, UUID owner, ItemStack sample) {
        if (sample == null || sample.isEmpty() || !ready(server, owner)) return false;
        if (!NeoForgeResourceStages.allowsItem(server, owner, BuiltInRegistries.ITEM.getKey(sample.getItem()))) return false;
        return !NeoForgeKnowledgeConfig.required() || knows(server, owner, knowledgeSample(sample));
    }

    public static boolean allows(MinecraftServer server, UUID owner, Kind kind) {
        return kind != null && ready(server, owner)
                && (kind.unit() == null || NeoForgeResourceStages.allowsResource(server, owner, kind.unit()))
                && (!NeoForgeKnowledgeConfig.required() || knows(server, owner, kind.sample()));
    }

    public static boolean allows(MinecraftServer server, UUID owner, ResourceLocation unit) {
        if (unit == null) return false;
        for (Kind kind : Kind.values()) if (unit.equals(kind.unit())) return allows(server, owner, kind);
        return false;
    }

    public static boolean allowsMedia(MinecraftServer server, UUID owner, Kind kind, ResourceLocation id) {
        if (!validMedia(kind, id) || !ready(server, owner)) return false;
        if (!NeoForgeResourceStages.allowsMedia(server, owner, kind == Kind.GAS, id)) return false;
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        boolean chemical = kind == Kind.GAS;
        if (data.isForgotten(owner, chemical, id)) return false;
        return !NeoForgeKnowledgeConfig.required() || knows(server, owner, kind.sample())
                || (chemical ? data.knowsChemical(owner, id) : data.knowsFluid(owner, id));
    }

    /** 实际卖入或显式可信授予后调用；学习不消耗 EMC，也不检查购买开关或价格。 */
    public static boolean learnMedia(MinecraftServer server, UUID owner, Kind kind, ResourceLocation id) {
        return validMedia(kind, id) && ready(server, owner)
                && NeoForgeRecoveryKnowledgeSavedData.get(server).learn(owner, kind == Kind.GAS, id);
    }

    /** 玩家路径不能遗忘未获授权或已经遗忘的资源；作者撤销由 MediaKnowledgeApi 单独提供。 */
    public static boolean forgetMedia(MinecraftServer server, UUID owner, Kind kind, ResourceLocation id) {
        return allowsMedia(server, owner, kind, id)
                && NeoForgeRecoveryKnowledgeSavedData.get(server).forget(owner, kind == Kind.GAS, id);
    }

    public static boolean validMedia(Kind kind, ResourceLocation id) {
        if (id == null) return false;
        if (kind == Kind.GAS) return NeoForgeMediaRegistry.validChemical(id);
        return kind == Kind.FLUID && BuiltInRegistries.FLUID.containsKey(id)
                && BuiltInRegistries.FLUID.get(id) != Fluids.EMPTY
                && BuiltInRegistries.FLUID.get(id).defaultFluidState().isSource();
    }

    public static Kind certificateKind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (var entry : NeoForgeCertificates.CERTIFICATES.entrySet())
            if (stack.is(entry.getValue().get())) return entry.getKey();
        return null;
    }

    /** 凭证显示组件不形成新的类别；普通物品保持原样交给 ProjectE 的持久组件策略。 */
    public static ItemStack knowledgeSample(ItemStack sample) {
        Kind kind = certificateKind(sample);
        return kind == null ? sample : kind.sample();
    }

    private static boolean ready(MinecraftServer server, UUID owner) {
        if (server == null || owner == null || !server.isSameThread() || server.overworld() == null) return false;
        try { return NeoForgeRecoveryKnowledgeSavedData.get(server).available(owner); }
        catch (RuntimeException | LinkageError failure) { return false; }
    }

    private static boolean knows(MinecraftServer server, UUID owner, ItemStack sample) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            IKnowledgeProvider knowledge = player == null ? ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(owner)
                    : player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            return knowledge != null && knowledge.hasKnowledge(ItemInfo.fromStack(sample));
        } catch (RuntimeException | LinkageError failure) { return false; }
    }
}
