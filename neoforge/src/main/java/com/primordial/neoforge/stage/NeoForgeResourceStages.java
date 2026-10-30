/**
 * 【文件职责】解析四类作者阶段规则，供物品、流体、化学品及独立资源生成前统一授权。
 * 【变更记录 #1】2026-10-23 16:19:58（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无。
 * - 新增：精确资源 ID 与 namespace:* 的全部阶段叠加、四类独立不可变缓存及配置变化重建。
 * - 修改：规则默认空列表；无匹配时不读取阶段存档，有匹配时要求健康所有者逐项满足，支持离线 UUID。
 * - 用途：阶段不依赖 ProjectE 凭证、知识开关或 FTB 队伍，且只约束生成；回收和真实学习不调用本层。
 */
package com.primordial.neoforge.stage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.primordial.neoforge.config.NeoForgeServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class NeoForgeResourceStages {
    private record Rule(String resource, ResourceLocation stage) {}
    private record Rules(List<String> source, Map<String, Set<ResourceLocation>> requirements) {}
    private static final Rules EMPTY = new Rules(List.of(), Map.of());
    private static Rules items = EMPTY, fluids = EMPTY, chemicals = EMPTY, resources = EMPTY;

    private NeoForgeResourceStages() {}

    public static boolean validRule(Object value) {
        return parse(value) != null;
    }

    public static boolean allowsItem(MinecraftServer server, UUID owner, ResourceLocation id) {
        return allows(server, owner, id, itemRules());
    }

    public static boolean allowsMedia(MinecraftServer server, UUID owner, boolean gas, ResourceLocation id) {
        return allows(server, owner, id, mediaRules(gas));
    }

    public static boolean allowsResource(MinecraftServer server, UUID owner, ResourceLocation id) {
        return allows(server, owner, id, resourceRules());
    }

    private static Rule parse(Object value) {
        if (!(value instanceof String text) || text.length() > 512) return null;
        String[] parts = text.split("=", -1);
        if (parts.length != 2) return null;
        String resource = parts[0].trim();
        ResourceLocation stage = ResourceLocation.tryParse(parts[1].trim());
        if (!NeoForgePlayerStagesSavedData.validStage(stage)) return null;
        boolean wildcard = resource.endsWith(":*");
        ResourceLocation id = ResourceLocation.tryParse(wildcard
                ? resource.substring(0, resource.length() - 1) + "all" : resource);
        if (id == null || id.getPath().isEmpty()) return null;
        return new Rule(wildcard ? id.getNamespace() + ":*" : id.toString(), stage);
    }

    private static synchronized Rules itemRules() {
        items = updateRules(items, NeoForgeServerConfig.itemStageRequirements());
        return items;
    }

    private static synchronized Rules mediaRules(boolean gas) {
        Rules updated = updateRules(gas ? chemicals : fluids, NeoForgeServerConfig.mediaStageRequirements(gas));
        if (gas) chemicals = updated; else fluids = updated;
        return updated;
    }

    private static synchronized Rules resourceRules() {
        resources = updateRules(resources, NeoForgeServerConfig.resourceStageRequirements());
        return resources;
    }

    private static Rules updateRules(Rules current, List<? extends String> configured) {
        if (current.source().equals(configured)) return current;
        Map<String, Set<ResourceLocation>> mutable = new ConcurrentHashMap<>();
        for (String entry : configured) {
            Rule rule = parse(entry);
            if (rule != null) mutable.computeIfAbsent(rule.resource(), ignored -> ConcurrentHashMap.newKeySet()).add(rule.stage());
        }
        Map<String, Set<ResourceLocation>> frozen = new ConcurrentHashMap<>();
        mutable.forEach((id, stages) -> frozen.put(id, Set.copyOf(stages)));
        return new Rules(List.copyOf(configured), Map.copyOf(frozen));
    }

    private static boolean allows(MinecraftServer server, UUID owner, ResourceLocation id, Rules rules) {
        if (server == null || owner == null || id == null || !server.isSameThread() || server.overworld() == null) return false;
        Set<ResourceLocation> exact = rules.requirements().getOrDefault(id.toString(), Set.of());
        Set<ResourceLocation> namespace = rules.requirements().getOrDefault(id.getNamespace() + ":*", Set.of());
        if (exact.isEmpty() && namespace.isEmpty()) return true;
        NeoForgePlayerStagesSavedData data = NeoForgePlayerStagesSavedData.get(server);
        return data.available(owner) && exact.stream().allMatch(stage -> data.has(owner, stage))
                && namespace.stream().allMatch(stage -> data.has(owner, stage));
    }
}
