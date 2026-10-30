/**
 * 【文件职责】为数据包提供 primordial:knowledge_required 目标版加载条件。
 * 【变更记录 #1】2026-10-23 13:30:35（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：无参数 MapCodec 与 NeoForge 条件注册器。
 * - 修改：条件直接读取与交易、原生价表相同的 COMMON 启动快照。
 * - 用途：控制仍存在的九张凭证配方；流体/化学品凭证没有本模组默认配方。
 */
package com.primordial.neoforge.knowledge;

import com.mojang.serialization.MapCodec;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public record NeoForgeKnowledgeRequiredCondition() implements ICondition {
    public static final MapCodec<NeoForgeKnowledgeRequiredCondition> CODEC =
            MapCodec.unit(new NeoForgeKnowledgeRequiredCondition());
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, PrimordialNeoForgeMod.MOD_ID);

    static { CONDITIONS.register("knowledge_required", () -> CODEC); }

    public static void register(IEventBus modBus) { CONDITIONS.register(modBus); }
    @Override public boolean test(IContext context) { return NeoForgeKnowledgeConfig.required(); }
    @Override public MapCodec<? extends ICondition> codec() { return CODEC; }
}
