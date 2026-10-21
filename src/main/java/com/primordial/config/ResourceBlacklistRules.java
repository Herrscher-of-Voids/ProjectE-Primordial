/**
 * 【文件职责】编译现有三类黑名单，并提供有界、可重载的命中原因查询。
 * 【变更记录 #1】2026-10-13 23:25:00（第 1 次备注 · 1.2.0-blacklist-system）
 * - 删除：无。新增：规范化 ID/命名空间/标签、每类最多 4096 个查询缓存及配置/标签失效事件。
 * - 修改：规则仅在配置列表更换或 Forge 重载后解析；无匹配不限制，精确 ID 优先用于说明命中原因。
 * - 用途：已有交易与作者诊断共用规则，不修改价格或知识；非法条目单独记录并跳过，气体 API 仍隔离。
 */
package com.primordial.config;

import com.primordial.PrimordialMod;
import com.primordial.compat.MekanismGasCompat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public final class ResourceBlacklistRules {
    public enum Kind { FLUID, GAS, RESOURCE }
    private static final int MAX_CACHED_IDS = 4096;
    private static final Snapshot[] snapshots = new Snapshot[Kind.values().length];
    private ResourceBlacklistRules() { }

    private record Rule(ResourceLocation id, boolean namespace, boolean tag, String text) { }
    private record Snapshot(List<? extends String> source, Map<ResourceLocation, String> ids,
            Map<String, String> namespaces, Map<ResourceLocation, String> tags,
            ConcurrentHashMap<ResourceLocation, String> results) { }

    private static Rule parse(Object entry, boolean tags) {
        if (!(entry instanceof String raw) || raw.length() > 512) return null;
        String text = raw.trim();
        boolean tag = text.startsWith("#");
        boolean namespace = text.endsWith(":*");
        if (namespace && text.length() <= 2) return null;
        if (tag && (!tags || namespace)) return null;
        String name = tag ? text.substring(1) : namespace ? text.substring(0, text.length() - 1) + "all" : text;
        if (name.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || id.getPath().isEmpty()) return null;
        String canonical = tag ? "#" + id : namespace ? id.getNamespace() + ":*" : id.toString();
        return new Rule(id, namespace, tag, canonical);
    }

    public static boolean validEntry(Object entry, boolean allowTags) { return parse(entry, allowTags) != null; }

    /** 供作者命令/脚本只读查询；返回实际命中规则，不代表知识、余额或价格允许交易。 */
    public static synchronized Optional<String> matchingRule(Kind kind, ResourceLocation id) {
        if (kind == null || id == null) return Optional.empty();
        Snapshot snapshot = snapshot(kind);
        String result = snapshot.results.get(id);
        if (result == null) {
            result = snapshot.ids.get(id);
            if (result == null) result = snapshot.namespaces.get(id.getNamespace());
            if (result == null) {
                for (var tag : snapshot.tags.entrySet()) {
                    if (inTag(kind, id, tag.getKey())) { result = tag.getValue(); break; }
                }
            }
            if (result == null) result = "";
            if (snapshot.results.size() >= MAX_CACHED_IDS) snapshot.results.clear();
            snapshot.results.put(id, result);
            if (!result.isEmpty()) PrimordialMod.LOGGER.debug("[Primordial] {} blacklist: {} matched {}", kind, id, result);
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private static Snapshot snapshot(Kind kind) {
        List<? extends String> source = !PrimordialServerConfig.SPEC.isLoaded() ? List.of() : switch (kind) {
            case FLUID -> PrimordialServerConfig.FLUID_BLACKLIST.get();
            case GAS -> PrimordialServerConfig.GAS_BLACKLIST.get();
            case RESOURCE -> PrimordialServerConfig.RESOURCE_BLACKLIST.get();
        };
        Snapshot current = snapshots[kind.ordinal()];
        // Forge ConfigValue 缓存列表实例；热重载事件另行清空，避免每次交易遍历完整配置。
        if (current != null && current.source == source) return current;
        Map<ResourceLocation, String> ids = new LinkedHashMap<>(), tags = new LinkedHashMap<>();
        Map<String, String> namespaces = new LinkedHashMap<>();
        for (Object raw : source) {
            Rule rule = parse(raw, kind != Kind.RESOURCE);
            if (rule == null) {
                PrimordialMod.LOGGER.warn("[Primordial] Ignoring invalid {} blacklist entry: {}", kind, raw);
                continue;
            }
            if (rule.tag) tags.putIfAbsent(rule.id, rule.text);
            else if (rule.namespace) namespaces.putIfAbsent(rule.id.getNamespace(), rule.text);
            else ids.putIfAbsent(rule.id, rule.text);
        }
        current = new Snapshot(source, Map.copyOf(ids), Map.copyOf(namespaces),
                java.util.Collections.unmodifiableMap(tags), new ConcurrentHashMap<>());
        snapshots[kind.ordinal()] = current;
        PrimordialMod.LOGGER.info("[Primordial] {} blacklist loaded: {} IDs, {} namespaces, {} tags", kind, ids.size(), namespaces.size(), tags.size());
        return current;
    }

    private static boolean inTag(Kind kind, ResourceLocation id, ResourceLocation tag) {
        if (kind == Kind.GAS) return MekanismGasCompat.gasInTag(id, tag);
        if (kind != Kind.FLUID || !BuiltInRegistries.FLUID.containsKey(id)) return false;
        return BuiltInRegistries.FLUID.getHolder(net.minecraft.resources.ResourceKey.create(Registries.FLUID, id))
                .map(holder -> holder.is(TagKey.create(Registries.FLUID, tag))).orElse(false);
    }

    /** 配置卸载、加载与重载清除全部快照，防止切换世界沿用旧服务器规则。 */
    public static void onConfigChange(ModConfigEvent event) {
        if (event.getConfig().getSpec() == PrimordialServerConfig.SPEC) invalidate();
    }

    /** /reload 与客户端标签同步都使缓存失效；不保存标签成员的旧判断。 */
    public static void onTagsUpdated(TagsUpdatedEvent event) { invalidate(); }

    public static synchronized void invalidate() { java.util.Arrays.fill(snapshots, null); }
}
