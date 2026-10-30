/**
 * 【文件职责】注册黑洞实际回收所消费的 NeoForge 服务端配置；数值、路径和默认值与 Forge 维护线一致。
 * 【变更记录 #1】2026-10-22 21:35:43（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：回收学习、FE 比率、流体卖出与黑名单、每刻预算、粒子间隔和实体价格覆盖。
 * - 修改：通过 NeoForge SERVER spec 保存与同步，未加载时返回维护线默认值；不读取 Forge 配置实例。
 * - 用途：原生配置界面与服务端执行共用规则，模拟和执行读取相同报价条件，不增加默认交易门槛。
 * 【变更记录 #2】2026-10-22 22:10:06（第 2 次备注 · 2.0.0-neo-dev 可选化学品回收）
 * - 删除：无。
 * - 新增：旧 gas_* 配置名对应的化学品卖出开关、黑名单及每刻接收预算；默认 true、空列表和 100000。
 * - 修改：保留 Forge 的 energy、media_trading、performance 路径与 [1, Integer.MAX_VALUE] 预算范围。
 * - 用途：为可选兼容层和原生配置界面提供同一规则；本文件不引用或加载 Mekanism API。
 * 【变更记录 #3】2026-10-23 11:37:39（第 3 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无。
 * - 新增：media_trading 下的 fluid_purchase_enabled 与 gas_purchase_enabled，默认均为 true，与 Forge 维护线一致。
 * - 修改：流体/化学品黑名单的原生配置提示明确同时禁止购买与回收；保存知识、价格和独立卖出开关。
 * - 用途：双向报价与原生配置界面共用购买门禁，不加载可选 API，也不增加默认交易限制。
 * 【变更记录 #4】2026-10-23 13:32:48（第 4 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：资源黑名单仅作用于回收的过时说明。
 * - 新增：FE 购买恢复后的双向交易提示。
 * - 修改：原生配置界面说明与中英译名同步；默认值和存储路径不变。
 * - 用途：已有 forge:fe 黑名单也阻止有凭证的购买，不改变用户已存规则。
 * 【变更记录 #5】2026-10-23 16:19:58（第 5 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无；保留黑洞、买卖和资源价格全部配置路径及默认值。
 * - 新增：resource_stages 下物品补充、流体、气体与独立资源四组阶段规则，默认均为空列表。
 * - 修改：沿用 Forge 的键名、翻译键及精确/namespace:* 校验；访问器提供不可变快照供授权和稳定报价比较。
 * - 用途：原生配置界面与服务端规则共用阶段要求，凭证及免知识配置不绕过作者阶段，回收学习不受影响。
 */
package com.primordial.neoforge.config;

import java.math.BigInteger;
import java.util.List;

import com.primordial.neoforge.stage.NeoForgeResourceStages;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue AUTO_LEARN_RECLAIMED_ITEMS;
    public static final ModConfigSpec.ConfigValue<String> FE_PER_EMC;
    public static final ModConfigSpec.IntValue CREATE_STRESS_PER_TICK;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NATIVE_RESOURCE_RATIOS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FLUID_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GAS_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESOURCE_BLACKLIST;
    public static final ModConfigSpec.BooleanValue FLUID_PURCHASE_ENABLED;
    public static final ModConfigSpec.BooleanValue FLUID_SALE_ENABLED;
    public static final ModConfigSpec.BooleanValue GAS_PURCHASE_ENABLED;
    public static final ModConfigSpec.BooleanValue GAS_SALE_ENABLED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_SUPPLY_STAGE_REQUIREMENTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FLUID_STAGE_REQUIREMENTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GAS_STAGE_REQUIREMENTS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESOURCE_STAGE_REQUIREMENTS;
    public static final ModConfigSpec.IntValue BLACKHOLE_MAX_ITEM_ENTITIES_PER_TICK;
    public static final ModConfigSpec.IntValue BLACKHOLE_MAX_ITEM_COUNT_PER_TICK;
    public static final ModConfigSpec.IntValue BLACKHOLE_MAX_FLUID_MB_PER_TICK;
    public static final ModConfigSpec.IntValue BLACKHOLE_MAX_GAS_MB_PER_TICK;
    public static final ModConfigSpec.IntValue BLACKHOLE_MAX_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue BLACKHOLE_PARTICLE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue BLACKHOLE_CHARM_RADIUS;
    public static final ModConfigSpec.IntValue BLACKHOLE_CHARM_MAX_ENTITIES_PER_TICK;
    public static final ModConfigSpec.IntValue BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK;
    public static final ModConfigSpec.IntValue BLACKHOLE_CHARM_SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ENTITY_EMC_OVERRIDES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("projecte");
        AUTO_LEARN_RECLAIMED_ITEMS = builder
                .comment("回收物品后自动学习其 ProjectE 知识；关闭时仍结算 EMC。",
                        "Automatically learn reclaimed items in ProjectE; disabling this does not disable EMC credit.")
                .translation("config.primordial.auto_learn_reclaimed_items")
                .define("auto_learn_reclaimed_items", true);
        builder.pop();

        builder.push("energy");
        FE_PER_EMC = builder
                .comment("每 1 EMC 对应的 FE 数，正整数文本，最多 128 位；默认 1000。",
                        "FE per EMC: positive integer string, up to 128 digits. Default: 1000.")
                .translation("config.primordial.fe_per_emc")
                .define("fe_per_emc", "1000", NeoForgeServerConfig::validFeRatio);
        CREATE_STRESS_PER_TICK = builder
                .comment("每台机器每刻可购买的 Create 原生应力上限；付款授权不保存且不可回收。",
                        "Maximum purchased Create stress per machine per tick; paid leases are temporary and cannot be reclaimed.")
                .translation("config.primordial.create_stress_per_tick")
                .defineInRange("create_stress_per_tick", 1048576, 1, Integer.MAX_VALUE);
        NATIVE_RESOURCE_RATIOS = builder
                .comment("独立资源 EMC/单位精确比例，格式 namespace:unit=1/1000；未知单位不会开放端口。",
                        "Exact EMC per native resource unit; namespace:unit=1/1000. Unknown units remain unavailable.")
                .translation("config.primordial.native_resource_ratios")
                .defineListAllowEmpty("native_resource_ratios", List.of(),
                        com.primordial.neoforge.resource.NeoForgeResourceUnits::validEntry);
        FLUID_BLACKLIST = builder
                .comment("禁止购买与回收的流体，支持注册名、namespace:* 和 #namespace:tag；不删除知识或价格。",
                        "Blocked fluids: registry IDs, namespace:* or #namespace:tag. Stored knowledge and prices are retained.")
                .translation("config.primordial.fluid_blacklist")
                .defineListAllowEmpty("fluid_blacklist", List.of(), entry -> validBlacklistEntry(entry, true));
        GAS_BLACKLIST = builder
                .comment("禁止购买与回收的 Mekanism 化学品，支持注册名、namespace:* 和 #namespace:tag；默认空列表。",
                        "沿用 Forge 的 gas_blacklist 配置名，在目标版本匹配统一化学品注册表；不删除知识或价格。",
                        "Blocked Mekanism chemicals: registry IDs, namespace:* or #namespace:tag. The legacy gas_blacklist key is retained.")
                .translation("config.primordial.gas_blacklist")
                .defineListAllowEmpty("gas_blacklist", List.of(), entry -> validBlacklistEntry(entry, true));
        RESOURCE_BLACKLIST = builder
                .comment("禁止转换的资源单位，支持注册名和 namespace:*；forge:fe 禁止 FE 买卖。",
                        "Blocked resource units: IDs or namespace:*. Blocking forge:fe disables FE purchases and reclamation.")
                .translation("config.primordial.resource_blacklist")
                .defineListAllowEmpty("resource_blacklist", List.of(), entry -> validBlacklistEntry(entry, false));
        builder.pop();

        builder.push("media_trading");
        FLUID_PURCHASE_ENABLED = builder
                .comment("允许用 EMC 购买流体与自动供应；关闭不删除已学知识或价格。",
                        "Allow fluid purchases and automatic supply. Existing knowledge and prices are retained.")
                .translation("config.primordial.fluid_purchase_enabled")
                .define("fluid_purchase_enabled", true);
        FLUID_SALE_ENABLED = builder
                .comment("允许流体卖入和自动回收；关闭时不抽取、不入账或学习。",
                        "Allow fluid sales and reclamation. Disabled transactions consume nothing and teach nothing.")
                .translation("config.primordial.fluid_sale_enabled")
                .define("fluid_sale_enabled", true);
        GAS_PURCHASE_ENABLED = builder
                .comment("允许用 EMC 购买 Mekanism 化学品与自动供应；关闭不删除已学知识或价格。",
                        "沿用 Forge 的 gas_purchase_enabled 配置名；未安装可选模组时不创建兼容端口。",
                        "Allow Mekanism chemical purchases and automatic supply. Existing knowledge and prices are retained.")
                .translation("config.primordial.gas_purchase_enabled")
                .define("gas_purchase_enabled", true);
        GAS_SALE_ENABLED = builder
                .comment("允许 Mekanism 化学品卖入和自动回收；关闭时不抽取、不入账或学习。",
                        "沿用 Forge 的 gas_sale_enabled 配置名；未安装可选模组时此设置不创建兼容端口。",
                        "Allow Mekanism chemical sales and reclamation. Disabled transactions consume nothing and teach nothing.")
                .translation("config.primordial.gas_sale_enabled")
                .define("gas_sale_enabled", true);
        builder.pop();

        builder.push("resource_stages");
        ITEM_SUPPLY_STAGE_REQUIREMENTS = builder
                .comment("原初物品生成/自动补充所需阶段，格式 item_id=stage_id；默认空列表。",
                        "支持 namespace:*，全部匹配阶段均需满足，覆盖所有组件变体；不限制回收、合成或 ProjectE 原生桌取物。",
                        "Stages for Primordial item generation/supply. All exact and namespace:* matches are required for every component variant.")
                .translation("config.primordial.item_supply_stage_requirements")
                .defineListAllowEmpty("item_supply_requirements", List.of(), NeoForgeResourceStages::validRule);
        FLUID_STAGE_REQUIREMENTS = builder
                .comment("流体购买/自动补充所需阶段，格式 minecraft:lava=pack:nether；默认空列表。",
                        "精确和 namespace:* 规则叠加，所有阶段都必须满足；卖入学习不受阶段限制。",
                        "Fluid purchase/supply stages: resource_id=stage_id. All exact and namespace:* matches are required; sales still teach.")
                .translation("config.primordial.fluid_stage_requirements")
                .defineListAllowEmpty("fluid_requirements", List.of(), NeoForgeResourceStages::validRule);
        GAS_STAGE_REQUIREMENTS = builder
                .comment("Mekanism 化学品购买/自动补充阶段，格式 mekanism:antimatter=pack:endgame；沿用旧 gas 键名。",
                        "支持 namespace:*，全部匹配阶段均需满足；凭证与关闭知识检查不能绕过。",
                        "Chemical purchase/supply stages using the legacy gas key. Certificates and disabled knowledge never bypass matching stages.")
                .translation("config.primordial.gas_stage_requirements")
                .defineListAllowEmpty("gas_requirements", List.of(), NeoForgeResourceStages::validRule);
        RESOURCE_STAGE_REQUIREMENTS = builder
                .comment("独立资源购买/自动补充所需阶段，格式 forge:fe=pack:energy；默认空列表。",
                        "支持 namespace:*，所有匹配阶段都必须满足；不影响回收。",
                        "Standalone resource purchase/supply stages: unit_id=stage_id. All exact and namespace:* matches are required.")
                .translation("config.primordial.resource_stage_requirements")
                .defineListAllowEmpty("resource_requirements", List.of(), NeoForgeResourceStages::validRule);
        builder.pop();

        builder.push("performance");
        BLACKHOLE_MAX_ITEM_ENTITIES_PER_TICK = builder
                .comment("单个黑洞每刻最多吸收的掉落实体数量。", "Maximum item entities absorbed per black hole per tick.")
                .translation("config.primordial.blackhole_max_item_entities_per_tick")
                .defineInRange("blackhole_max_item_entities_per_tick", 16, 1, 4096);
        BLACKHOLE_MAX_ITEM_COUNT_PER_TICK = builder
                .comment("单个黑洞每刻接收的物品数量上限，超额留在输入端。", "Maximum item count accepted per black hole per tick; excess stays at the source.")
                .translation("config.primordial.blackhole_max_item_count_per_tick")
                .defineInRange("blackhole_max_item_count_per_tick", 4096, 1, Integer.MAX_VALUE);
        BLACKHOLE_MAX_FLUID_MB_PER_TICK = builder
                .comment("单个黑洞每刻接收的流体上限，单位 mB。", "Maximum fluid millibuckets accepted per black hole per tick.")
                .translation("config.primordial.blackhole_max_fluid_mb_per_tick")
                .defineInRange("blackhole_max_fluid_mb_per_tick", 100000, 1, Integer.MAX_VALUE);
        BLACKHOLE_MAX_GAS_MB_PER_TICK = builder
                .comment("单个黑洞每刻接收的 Mekanism 化学品上限，单位 mB；沿用 Forge 的 gas 配置名和整数范围。",
                        "未覆盖化学品默认 1 EMC/mB，价表中的 0 禁用回收；超额数量保留在输入端。",
                        "Maximum Mekanism chemical mB accepted per black hole per tick. Legacy gas key, default 100000, range 1 to 2147483647.")
                .translation("config.primordial.blackhole_max_gas_mb_per_tick")
                .defineInRange("blackhole_max_gas_mb_per_tick", 100000, 1, Integer.MAX_VALUE);
        BLACKHOLE_MAX_ENERGY_PER_TICK = builder
                .comment("单个黑洞每刻接收的 FE 上限；不足 1 EMC 的收益保留精确余数。",
                        "Maximum FE accepted per black hole per tick; fractional EMC credit is preserved.")
                .translation("config.primordial.blackhole_max_energy_per_tick")
                .defineInRange("blackhole_max_energy_per_tick", 4000000, 1, Integer.MAX_VALUE);
        BLACKHOLE_PARTICLE_INTERVAL_TICKS = builder
                .comment("黑洞粒子的最小生成间隔，单位游戏刻。", "Minimum particle burst interval in game ticks.")
                .translation("config.primordial.blackhole_particle_interval_ticks")
                .defineInRange("blackhole_particle_interval_ticks", 5, 1, 200);
        BLACKHOLE_CHARM_RADIUS = builder.translation("config.primordial.blackhole_charm_radius")
                .defineInRange("blackhole_charm_radius", 8, 1, 32);
        BLACKHOLE_CHARM_MAX_ENTITIES_PER_TICK = builder.translation("config.primordial.blackhole_charm_max_entities_per_tick")
                .defineInRange("blackhole_charm_max_entities_per_tick", 32, 1, 512);
        BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK = builder.translation("config.primordial.blackhole_charm_max_items_per_tick")
                .defineInRange("blackhole_charm_max_items_per_tick", 4096, 1, Integer.MAX_VALUE);
        BLACKHOLE_CHARM_SCAN_INTERVAL_TICKS = builder.translation("config.primordial.blackhole_charm_scan_interval_ticks")
                .defineInRange("blackhole_charm_scan_interval_ticks", 1, 1, 200);
        builder.pop();

        builder.push("entity_emc");
        ENTITY_EMC_OVERRIDES = builder
                .comment("实体单价覆盖，格式 minecraft:zombie=5120；0 禁止回收。",
                        "默认基础生命每点 128 EMC，怪物 2 倍，凋灵与末影龙 64 倍；非生物实体默认 1024。",
                        "Entity prices: registry_id=nonnegative_integer (up to 128 digits); zero disables reclamation.")
                .translation("config.primordial.entity_emc_overrides")
                .defineListAllowEmpty("overrides", List.of(), NeoForgeServerConfig::validEntityOverride);
        builder.pop();
        SPEC = builder.build();
    }

    private NeoForgeServerConfig() {}

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    public static boolean autoLearnReclaimedItems() {
        return !SPEC.isLoaded() || AUTO_LEARN_RECLAIMED_ITEMS.get();
    }

    public static BigInteger fePerEmc() {
        return SPEC.isLoaded() ? new BigInteger(FE_PER_EMC.get()) : BigInteger.valueOf(1000);
    }

    public static int createStressPerTick() {
        return SPEC.isLoaded() ? CREATE_STRESS_PER_TICK.get() : 1048576;
    }

    public static int maxItemEntities() {
        return SPEC.isLoaded() ? BLACKHOLE_MAX_ITEM_ENTITIES_PER_TICK.get() : 16;
    }

    public static int maxItems() {
        return SPEC.isLoaded() ? BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.get() : 4096;
    }

    public static int maxFluid() {
        return SPEC.isLoaded() ? BLACKHOLE_MAX_FLUID_MB_PER_TICK.get() : 100000;
    }

    public static int maxGas() {
        return SPEC.isLoaded() ? BLACKHOLE_MAX_GAS_MB_PER_TICK.get() : 100000;
    }

    public static int maxEnergy() {
        return SPEC.isLoaded() ? BLACKHOLE_MAX_ENERGY_PER_TICK.get() : 4000000;
    }

    public static int particleInterval() {
        return SPEC.isLoaded() ? BLACKHOLE_PARTICLE_INTERVAL_TICKS.get() : 5;
    }

    public static boolean fluidSaleEnabled() {
        return !SPEC.isLoaded() || FLUID_SALE_ENABLED.get();
    }

    public static boolean fluidPurchaseEnabled() {
        return !SPEC.isLoaded() || FLUID_PURCHASE_ENABLED.get();
    }

    public static boolean gasSaleEnabled() {
        return !SPEC.isLoaded() || GAS_SALE_ENABLED.get();
    }

    public static boolean gasPurchaseEnabled() {
        return !SPEC.isLoaded() || GAS_PURCHASE_ENABLED.get();
    }

    public static List<? extends String> fluidBlacklist() {
        return SPEC.isLoaded() ? FLUID_BLACKLIST.get() : List.of();
    }

    public static List<? extends String> gasBlacklist() {
        return SPEC.isLoaded() ? GAS_BLACKLIST.get() : List.of();
    }

    public static List<? extends String> resourceBlacklist() {
        return SPEC.isLoaded() ? RESOURCE_BLACKLIST.get() : List.of();
    }

    public static List<? extends String> nativeResourceRatios() {
        return SPEC.isLoaded() ? List.copyOf(NATIVE_RESOURCE_RATIOS.get()) : List.of();
    }

    public static List<? extends String> entityOverrides() {
        return SPEC.isLoaded() ? ENTITY_EMC_OVERRIDES.get() : List.of();
    }

    public static List<? extends String> itemStageRequirements() {
        return stageRules(ITEM_SUPPLY_STAGE_REQUIREMENTS);
    }

    public static List<? extends String> mediaStageRequirements(boolean gas) {
        return stageRules(gas ? GAS_STAGE_REQUIREMENTS : FLUID_STAGE_REQUIREMENTS);
    }

    public static List<? extends String> resourceStageRequirements() {
        return stageRules(RESOURCE_STAGE_REQUIREMENTS);
    }

    private static List<? extends String> stageRules(ModConfigSpec.ConfigValue<List<? extends String>> value) {
        return SPEC.isLoaded() ? List.copyOf(value.get()) : List.of();
    }

    private static boolean validFeRatio(Object value) {
        return value instanceof String text && text.matches("[0-9]{1,128}")
                && new BigInteger(text).signum() > 0;
    }

    /** 与 Forge 规则一致，允许省略 minecraft 命名空间和首尾空格；虚拟资源单位不支持注册表标签。 */
    public static boolean validBlacklistEntry(Object value, boolean allowTags) {
        if (!(value instanceof String raw) || raw.length() > 512) return false;
        String text = raw.trim();
        boolean tag = text.startsWith("#");
        boolean namespace = text.endsWith(":*");
        if (tag && (!allowTags || namespace)) return false;
        if (namespace && text.length() <= 2) return false;
        String name = tag ? text.substring(1) : namespace ? text.substring(0, text.length() - 1) + "all" : text;
        return !name.isEmpty() && ResourceLocation.tryParse(name) != null;
    }

    private static boolean validEntityOverride(Object value) {
        return value instanceof String text && text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+=[0-9]{1,128}");
    }
}
