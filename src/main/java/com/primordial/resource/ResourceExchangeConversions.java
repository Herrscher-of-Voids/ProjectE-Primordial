/**
 * 【文件职责】保存独立资源单位到 EMC 的精确分数换算，并读取服务端配置覆盖值。
 *
 * 变更记录 #1（2026-09-16）：新增五类模组资源的默认报价：魔力、气压、灵气、魔源、
 * 机械动力转速和应力均为 1/1000 EMC/单位。默认值只提供可预测的初始平衡，整合包作者
 * 可以在 energy.native_resource_ratios 中逐项覆盖；未知单位返回无价，不会被自动扣款。
 * 变更记录 #2（2026-10-08）：接入服务端 resource_blacklist；被禁止的单位统一返回无价。
 */
/**
 * 【变更记录 #3】2026-10-14 17:12:00（第 3 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：铁魔法法力、Goety 灵魂能量的独立单位及默认分数报价。
 * - 修改：新单位复用同一配置覆盖与黑名单；机器资源页顺序不混入玩家账户。
 * - 用途：购买实际数量统一结算，不与植物魔法魔力或新生魔艺魔源混算。
 */
package com.primordial.resource;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.service.EmcAmount;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** 独立资源的 EMC/单位换算表。 */
public final class ResourceExchangeConversions {
    public static final ResourceLocation BOTANIA_MANA = new ResourceLocation("botania", "mana");
    public static final ResourceLocation PNEUMATIC_PRESSURE = new ResourceLocation("pneumaticcraft", "pressure");
    public static final ResourceLocation NATURES_AURA = new ResourceLocation("naturesaura", "aura");
    public static final ResourceLocation ARS_SOURCE = new ResourceLocation("ars_nouveau", "source");
    public static final ResourceLocation CREATE_SPEED = new ResourceLocation("create", "rotation_speed");
    public static final ResourceLocation CREATE_STRESS = new ResourceLocation("create", "stress");
    public static final ResourceLocation IRON_MANA = new ResourceLocation("irons_spellbooks", "mana");
    public static final ResourceLocation GOETY_SOULS = new ResourceLocation("goety", "soul_energy");

    private static final EmcAmount DEFAULT = new EmcAmount(BigInteger.ONE, BigInteger.valueOf(1000));
    private static final Map<ResourceLocation, EmcAmount> DEFAULTS = Map.of(
            BOTANIA_MANA, DEFAULT,
            PNEUMATIC_PRESSURE, DEFAULT,
            NATURES_AURA, DEFAULT,
            ARS_SOURCE, DEFAULT,
            CREATE_SPEED, DEFAULT,
            CREATE_STRESS, DEFAULT,
            IRON_MANA, DEFAULT,
            GOETY_SOULS, DEFAULT);
    private static final List<ResourceLocation> SUPPORTED_UNITS = List.of(
            BOTANIA_MANA, PNEUMATIC_PRESSURE, NATURES_AURA, ARS_SOURCE, CREATE_SPEED, CREATE_STRESS);
    private static volatile List<? extends String> cachedEntries = List.of();
    private static volatile Map<ResourceLocation, EmcAmount> overrides = Map.of();

    private ResourceExchangeConversions() {}

    /** 返回 1 个资源单位对应的 EMC 分数；未知单位返回零。 */
    public static EmcAmount emcPerUnit(ResourceLocation unit) {
        if (unit == null || PrimordialServerConfig.resourceBlocked(unit)) return EmcAmount.ZERO;
        refresh();
        EmcAmount configured = overrides.get(unit);
        return configured != null ? configured : DEFAULTS.getOrDefault(unit, EmcAmount.ZERO);
    }

    public static boolean known(ResourceLocation unit) { return DEFAULTS.containsKey(unit); }

    /** 配置器资源页显示的稳定单位顺序；返回不可变列表，避免客户端改写服务端定义。 */
    public static List<ResourceLocation> supportedUnits() { return SUPPORTED_UNITS; }

    public static boolean validEntry(Object value) {
        if (!(value instanceof String text) || text.length() > 512) return false;
        String[] pair = text.split("=", -1);
        if (pair.length != 2 || ResourceLocation.tryParse(pair[0]) == null) return false;
        try {
            EmcAmount amount = EmcAmount.parse(pair[1]);
            return amount.numerator().signum() > 0;
        } catch (RuntimeException invalid) { return false; }
    }

    private static void refresh() {
        List<? extends String> entries = PrimordialServerConfig.nativeResourceRatios();
        if (entries.equals(cachedEntries)) return;
        synchronized (ResourceExchangeConversions.class) {
            if (entries.equals(cachedEntries)) return;
            Map<ResourceLocation, EmcAmount> next = new HashMap<>();
            for (String entry : entries) if (validEntry(entry)) {
                int split = entry.indexOf('=');
                try { next.put(new ResourceLocation(entry.substring(0, split)), EmcAmount.parse(entry.substring(split + 1))); }
                catch (RuntimeException ignored) { /* Forge 配置校验已过滤；再次防御运行时修改。 */ }
            }
            overrides = Map.copyOf(next);
            cachedEntries = List.copyOf(entries);
        }
    }
}
