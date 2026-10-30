/**
 * 【文件职责】仅在 Mekanism 存在时读取目标版统一化学品注册表及标签。
 * 【变更记录 #1】2026-10-23 13:34:30（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：实际注册 ID、非空化学品和标签成员的校验。
 * - 修改：公共代码只反射 ResourceLocation 签名，不装可选模组时不载入本类。
 * - 用途：支持全部目标版统一化学品，避免旧气体 API 或不存在的 ID 获得购买授权。
 */
package com.primordial.neoforge.compat;

import mekanism.api.MekanismAPI;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public final class NeoForgeChemicalKnowledgeBridge {
    private NeoForgeChemicalKnowledgeBridge() {}
    public static boolean valid(ResourceLocation id) {
        return id != null && MekanismAPI.CHEMICAL_REGISTRY.containsKey(id)
                && !MekanismAPI.CHEMICAL_REGISTRY.get(id).isEmptyType();
    }
    public static boolean inTag(ResourceLocation id, ResourceLocation tag) {
        return valid(id) && MekanismAPI.CHEMICAL_REGISTRY.get(id).is(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tag));
    }
    public static Set<ResourceLocation> ids() {
        return MekanismAPI.CHEMICAL_REGISTRY.keySet().stream().filter(NeoForgeChemicalKnowledgeBridge::valid)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
