/**
 * 【文件职责】解析流体/气体阶段规则，在购买和机器补充前检查所有匹配阶段。
 * 【变更记录 #1】2026-10-02 19:05:26（第 1 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：资源注册名或 namespace:* 到阶段名的映射，精确和通配条件叠加；按配置变化重建缓存。
 * - 修改：无；阶段检查独立于知识、凭证、价格和交易总开关，卖入学习不检查阶段。
 * - 用途：服务端以玩家 UUID 判定，包括离线机器；配置默认空列表，不限制已有整合包。
 */
/**
 * 【变更记录 #2】2026-10-02 19:15:08（第 2 次备注 · 1.2.0-item-supply-stages）
 * - 删除：规则解析和阶段检查只能用于两种介质的限制。
 * - 新增：物品补充独立规则缓存与 allowsItem 查询入口。
 * - 修改：三类规则共用解析和阶段判定，缓存互相隔离，物品按注册名匹配所有 NBT 变体。
 * - 用途：机器及玩家背包补充使用同一阶段要求，不干预 ProjectE 原生转化桌取物。
 * 【变更记录 #3】2026-10-08 03:20:00（1.2.0-resource-stages）
 * - 新增：独立资源单位阶段规则，与物品、流体、气体规则独立缓存。
 */
/**
 * 【变更记录 #4】2026-10-13 03:04:47（第 4 次备注 · 1.2.0-quest-system）
 * - 删除：无。新增：四类公共阶段入口同时检查 ResourceQuestPolicy。
 * - 修改：个人阶段与队伍任务全部满足才允许输出；管理员 grant 不覆盖任务条件。
 * - 用途：目录、购买、机器、背包与 AE2/RS 共用检查，回收学习保持原行为。
 * 【变更记录 #5】2026-10-13（第 5 次备注 · FTB 阶段奖励）
 * - 删除：通过资源 ID 直接绑定任务 ID 的临时规则。
 * - 修改：阶段仍按此处规则判定；FTB Quests 自定义奖励通过玩家阶段存档发放阶段 ID。
 * - 用途：整合包作者将任务奖励和流体/气体阶段规则组合，不在交易热路径查询 FTB。
 * 【变更记录 #6】2026-10-13 03:56:00（第 6 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：无；保留既有流体、气体、物品和独立资源阶段规则。
 * - 新增：无。FTB 自定义奖励写入同一 PlayerStagesSavedData，现有精确规则及 namespace:* 规则直接读取该阶段。
 * - 修改：仅由 stage 存档决定阶段门禁；FTB 奖励不介入交易查询。未配置阶段继续允许，配置阶段缺失时拒绝购买/补充。
 * - 用途：将任务解锁和目录、购买、机器/背包补充统一到已有服务端阶段判定；卖入学习仍不经过此门禁。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.PlayerStagesSavedData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class ResourceStages {
    private ResourceStages() { }
    private record Rule(String resource, ResourceLocation stage) { }
    private record Rules(List<String> source, Map<String, Set<ResourceLocation>> requirements) { }
    private static final Rules EMPTY = new Rules(List.of(), Map.of());
    private static Rules fluids = EMPTY, gases = EMPTY, items = EMPTY, resources = EMPTY;

    public static boolean validRule(Object value) { return parse(value) != null; }

    private static Rule parse(Object value) {
        if (!(value instanceof String text) || text.length() > 512) return null;
        String[] parts = text.split("=", -1);
        if (parts.length != 2) return null;
        String resource = parts[0].trim();
        ResourceLocation stage = ResourceLocation.tryParse(parts[1].trim());
        if (!PlayerStagesSavedData.validStage(stage)) return null;
        boolean wildcard = resource.endsWith(":*");
        ResourceLocation id = ResourceLocation.tryParse(wildcard
                ? resource.substring(0, resource.length() - 1) + "all" : resource);
        if (id == null || id.getPath().isEmpty()) return null;
        return new Rule(wildcard ? id.getNamespace() + ":*" : id.toString(), stage);
    }

    private static synchronized Rules rules(boolean gas) {
        List<? extends String> configured = PrimordialServerConfig.mediaStageRequirements(gas);
        Rules current = gas ? gases : fluids;
        Rules replacement = updateRules(current, configured);
        if (gas) gases = replacement; else fluids = replacement;
        return replacement;
    }

    private static synchronized Rules itemRules() {
        items = updateRules(items, PrimordialServerConfig.itemStageRequirements());
        return items;
    }

    private static synchronized Rules resourceRules() {
        resources = updateRules(resources, PrimordialServerConfig.resourceStageRequirements());
        return resources;
    }

    private static Rules updateRules(Rules current, List<? extends String> configured) {
        if (current.source.equals(configured)) return current;
        Map<String, Set<ResourceLocation>> mutable = new HashMap<>();
        for (String entry : configured) {
            Rule rule = parse(entry);
            if (rule != null) mutable.computeIfAbsent(rule.resource, ignored -> new HashSet<>()).add(rule.stage);
        }
        Map<String, Set<ResourceLocation>> frozen = new HashMap<>();
        mutable.forEach((id, stages) -> frozen.put(id, Set.copyOf(stages)));
        return new Rules(List.copyOf(configured), Map.copyOf(frozen));
    }

    public static boolean allowsMedia(MinecraftServer server, UUID owner, boolean gas, ResourceLocation id) {
        return allows(server, owner, id, rules(gas));
    }

    public static boolean allowsItem(MinecraftServer server, UUID owner, ResourceLocation id) {
        return allows(server, owner, id, itemRules());
    }

    public static boolean allowsResource(MinecraftServer server, UUID owner, ResourceLocation id) {
        return allows(server, owner, id, resourceRules());
    }

    private static boolean allows(MinecraftServer server, UUID owner, ResourceLocation id, Rules rules) {
        if (id == null || owner == null) return false;
        var requirements = rules.requirements;
        Set<ResourceLocation> exact = requirements.getOrDefault(id.toString(), Set.of());
        Set<ResourceLocation> namespace = requirements.getOrDefault(id.getNamespace() + ":*", Set.of());
        if (exact.isEmpty() && namespace.isEmpty()) return true;
        if (server == null || !server.isSameThread() || server.overworld() == null) return false;
        var data = PlayerStagesSavedData.get(server);
        return exact.stream().allMatch(stage -> data.has(owner, stage))
                && namespace.stream().allMatch(stage -> data.has(owner, stage));
    }
}
