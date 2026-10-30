package com.primordial.neoforge.acceleration;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AccelerationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ACCELERATION_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> ACCELERATION_EMC_PER_MULTIPLIER_SECOND;
    public static final ModConfigSpec.IntValue ACCELERATION_MAX_BOUND_TARGETS;
    public static final ModConfigSpec.IntValue ACCELERATION_MAX_TARGETS_PER_TICK;
    public static final ModConfigSpec.IntValue ACCELERATION_MAX_EXTRA_TICKS;
    public static final ModConfigSpec.IntValue ACCELERATION_TIME_BUDGET_MICROS;
    public static final ModConfigSpec.IntValue ACCELERATION_PAUSE_ABOVE_MILLIS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ACCELERATION_BLOCKLIST;
    public static final ModConfigSpec.BooleanValue ACCELERATION_ENTITIES_ENABLED;
    public static final ModConfigSpec.BooleanValue ACCELERATION_WORLD_ENABLED;
    public static final ModConfigSpec.IntValue ACCELERATION_MAX_SCANS;
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ACCELERATION_ENTITY_BLOCKLIST;
    private static String cachedAccelerationText;
    private static java.math.BigInteger cachedAccelerationValue;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("projecte");
        ACCELERATION_ENABLED = builder
                .comment("EMC 加速器总开关，控制方块/机器、范围、实体和昼夜天气的额外工作。")
                .translation("config.primordial.acceleration_enabled")
                .define("acceleration_enabled", true);
        ACCELERATION_EMC_PER_MULTIPLIER_SECOND = builder
                .comment("每个目标每秒的完整费用 = 倍率 × 此值，默认 1000；2 倍为 2000，1024 倍为 1024000 EMC/秒。",
                        "一秒按 20 个游戏 tick 计算，费用逐 tick 分摊；限流按实际额外调用比例收费，未执行部分退款。",
                        "不足 1 EMC 的找零由统一结算服务保留，避免分批收费重复取整。正整数文本，最多 128 位。")
                .translation("config.primordial.acceleration_emc_per_multiplier_second")
                .define("acceleration_emc_per_multiplier_second", "1000",
                        value -> value instanceof String text && text.matches("[1-9][0-9]{0,127}"));
        ACCELERATION_MAX_BOUND_TARGETS = builder
                .comment("单把时间加速器可绑定的目标数量，默认 64；每个目标独立计费，仍受全服性能预算约束。")
                .translation("config.primordial.acceleration_max_bound_targets")
                .defineInRange("acceleration_max_bound_targets", 64, 1, 256);
        ACCELERATION_MAX_TARGETS_PER_TICK = builder
                .comment("单个服务端 tick 最多处理的 EMC 加速目标数；同一位置不会叠加多个加速器。")
                .translation("config.primordial.acceleration_max_targets_per_tick")
                .defineInRange("acceleration_max_targets_per_tick", 64, 1, 4096);
        ACCELERATION_MAX_EXTRA_TICKS = builder
                .comment("全服务器每 tick 的额外调用总上限；超过预算轮转到后续 tick，不积压补跑。")
                .translation("config.primordial.acceleration_max_extra_ticks")
                .defineInRange("acceleration_max_extra_ticks", 4096, 1, 65536);
        ACCELERATION_TIME_BUDGET_MICROS = builder
                .comment("每 tick 加速工作耗时预算，单位微秒，默认 2000 = 2 ms。",
                        "单次第三方 tick 无法中断；发现单次调用超过预算会停用该加速器。")
                .translation("config.primordial.acceleration_time_budget_micros")
                .defineInRange("acceleration_time_budget_micros", 2000, 100, 10000);
        ACCELERATION_PAUSE_ABOVE_MILLIS = builder
                .comment("正常服务器 tick 已耗时达到此毫秒数时，本轮跳过加速；默认 45 ms，为 20 TPS 留余量。")
                .translation("config.primordial.acceleration_pause_above_millis")
                .defineInRange("acceleration_pause_above_millis", 45, 5, 49);
        ACCELERATION_BLOCKLIST = builder
                .comment("禁止加速的方块 ID，支持 命名空间:*；可排除其它加速器及不兼容机器。",
                        "同时遵守 ProjectE 的 projecte:blacklist/time_watch 方块标签。")
                .translation("config.primordial.acceleration_blocklist")
                .defineListAllowEmpty("acceleration_blocklist", java.util.List.of("primordial:*", "torcherino:*"),
                        value -> value instanceof String text && net.minecraft.resources.ResourceLocation.tryParse(
                                text.endsWith(":*") ? text.substring(0, text.length() - 1) + "all" : text) != null);
        ACCELERATION_ENTITIES_ENABLED = builder.comment("允许加速非玩家实体；玩家、骑乘链和多部件实体始终排除。")
                .translation("config.primordial.acceleration_entities_enabled").define("acceleration_entities_enabled", true);
        ACCELERATION_WORLD_ENABLED = builder.comment("允许付费推进主世界昼夜与天气；分别遵守 doDaylightCycle 与 doWeatherCycle。",
                        "每种全局时钟每 tick 最多由一个加速器执行，不改变游戏总时间、计划刻或本轮 600 tick 时限。")
                .translation("config.primordial.acceleration_world_enabled").define("acceleration_world_enabled", true);
        ACCELERATION_MAX_SCANS = builder.comment("全服每 tick 最多检查的范围方块数，默认 2048；扫描也受加速耗时预算限制。",
                        "单个范围最多 15×15×15，渐进扫描并轮转执行已发现目标，不加载区块、不积压补跑。")
                .translation("config.primordial.acceleration_max_scans").defineInRange("acceleration_max_scans", 2048, 64, 16384);
        ACCELERATION_ENTITY_BLOCKLIST = builder.comment("禁止加速的实体注册 ID，支持 命名空间:*；可排除有特殊 tick 逻辑的实体。")
                .translation("config.primordial.acceleration_entity_blocklist")
                .defineListAllowEmpty("acceleration_entity_blocklist", java.util.List.of("primordial:*"),
                        value -> value instanceof String text && net.minecraft.resources.ResourceLocation.tryParse(
                                text.endsWith(":*") ? text.substring(0, text.length() - 1) + "all" : text) != null);

        builder.pop();
        SPEC = builder.build();
    }
    public static boolean accelerationEnabled() { return !SPEC.isLoaded() || ACCELERATION_ENABLED.get(); }

    public static synchronized java.math.BigInteger accelerationEmcPerMultiplierSecond() {
        String value = SPEC.isLoaded() ? ACCELERATION_EMC_PER_MULTIPLIER_SECOND.get() : "1000";
        if (!value.equals(cachedAccelerationText)) {
            cachedAccelerationValue = new java.math.BigInteger(value);
            cachedAccelerationText = value;
        }
        return cachedAccelerationValue;
    }

    public static int accelerationMaxTargetsPerTick() { return SPEC.isLoaded() ? ACCELERATION_MAX_TARGETS_PER_TICK.get() : 64; }
    public static int accelerationMaxBoundTargets() { return SPEC.isLoaded() ? ACCELERATION_MAX_BOUND_TARGETS.get() : 64; }
    public static int accelerationMaxExtraTicks() { return SPEC.isLoaded() ? ACCELERATION_MAX_EXTRA_TICKS.get() : 4096; }
    public static long accelerationTimeBudgetNanos() {
        return (SPEC.isLoaded() ? ACCELERATION_TIME_BUDGET_MICROS.get() : 2000) * 1000L;
    }
    public static long accelerationPauseAboveNanos() {
        return (SPEC.isLoaded() ? ACCELERATION_PAUSE_ABOVE_MILLIS.get() : 45) * 1_000_000L;
    }
    public static boolean accelerationBlocked(net.minecraft.resources.ResourceLocation block) {
        var entries = SPEC.isLoaded() ? ACCELERATION_BLOCKLIST.get() : java.util.List.of("primordial:*", "torcherino:*");
        return entries.contains(block.toString()) || entries.contains(block.getNamespace() + ":*");
    }

    public static boolean accelerationEntitiesEnabled() { return !SPEC.isLoaded() || ACCELERATION_ENTITIES_ENABLED.get(); }
    public static boolean accelerationWorldEnabled() { return !SPEC.isLoaded() || ACCELERATION_WORLD_ENABLED.get(); }
    public static int accelerationMaxScans() { return SPEC.isLoaded() ? ACCELERATION_MAX_SCANS.get() : 2048; }
    public static boolean accelerationEntityBlocked(net.minecraft.resources.ResourceLocation entity) {
        if (entity == null) return true;
        var entries = SPEC.isLoaded() ? ACCELERATION_ENTITY_BLOCKLIST.get() : java.util.List.of("primordial:*");
        return entries.contains(entity.toString()) || entries.contains(entity.getNamespace() + ":*");
    }

    private AccelerationConfig() {}
}
