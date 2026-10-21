/**
 * 【变更记录 #7】2026-09-15（配置界面中文化）
 * - 删除：无；保留所有既有配置项、默认值、范围校验和服务端同步行为。
 * - 新增：为每个服务端配置项显式绑定 config.primordial.* 翻译键，避免 Forge 配置界面回退到英文的自动标题。
 * - 修改：auto_learn_reclaimed_items、性能预算和粒子间隔等配置统一使用资源语言文件中的中文名称；气体配置继续沿用已有翻译键。
 * - 用途：确保中文客户端打开配置界面时看到稳定、可读且与配置文件含义一致的中文标签。
 */
/**
 * 【变更记录 #2】2026-09-15 00:40:01（第 2 次备注 · 黑洞性能参数）
 * - 删除：无；保留既有 ProjectE 自动学习配置及其历史兼容读取。
 * - 新增：黑洞每 tick 的掉落实体、物品数量、流体 mB、FE 和粒子间隔配置，并提供经过 Forge 范围校验的读取方法。
 * - 修改：无；新参数只限制黑洞输入吞吐，不改变 EMC 价格、FE 换算比例或学习规则。
 * - 用途：让整合包作者在性能和吞吐之间调节预算，默认值优先保护 TPS、堆内存和日志体积。
 */
/**
 * 文件职责：定义 Primordial 服务端规则配置，并集中提供经校验的读取入口。
 * 【变更记录 #14】2026-10-02（1.2.0-media-blacklist）
 * - 新增：fluid_blacklist 与 gas_blacklist，支持精确注册名和 namespace:* 通配。
 * 【变更记录 #18】2026-10-08（1.2.0-resource-blacklist）
 * - 新增：resource_blacklist，统一禁止独立资源单位的购买、回收和自动供应。
 * - 修改：黑名单匹配继续采用精确 ID 和 namespace:* 通配。
 * 【变更记录 #19】2026-10-08（1.2.0-resource-stages）
 * - 新增：独立资源阶段规则，限制资源端口的购买和自动补充；默认空列表保持兼容。
 * - 用途：整合包作者可以禁用指定流体或气体的购买与回收，而不必逐项改价。
 *
 * 变更记录 #1（2026-09-14）：新增回收自动学习开关。
 * - 删除：无。
 * - 新增：auto_learn_reclaimed_items，默认开启；该选项决定配置器/黑洞回收物品后是否写入 ProjectE 知识。
 * - 修改：无。
 * - 用途：规则由服务端权威控制，客户端不能通过本地配置绕过“已学习物品才能输出”的限制。
 */
/**
 * 【变更记录 #3】2026-09-15 12:04:51（第 3 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：blackhole_max_gas_mb_per_tick，默认 100000 mB，范围 1 至 Integer.MAX_VALUE。
 * - 修改：提供独立气体预算查询与中英注释/翻译键；不与流体共享计数。
 * - 用途：限制每个黑洞每 tick 的气体接收量，余量由管道保留并在后续 tick 重试。
 */
/**
 * 【变更记录 #4】2026-09-15 14:08:10（第 4 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：energy.fe_per_emc 双向统一比例及 native_fe_ratios 精确原生单位换算表。
 * - 修改：比例移入服务端 Forge 配置并自动同步；默认 1000 FE = 1 EMC，原生接口不重复换算。
 * - 用途：供机器、黑洞、手持充能和配方报价共用；未知独立能源不猜价，表值采用 分子/分母。
 */
/**
 * 【变更记录 #5】2026-09-15 15:04:02（第 5 次备注 · 1.0.0-emc-service）
 * - 删除：每笔 FE 交易重新解析配置字符串。
 * - 新增：数值缓存和中英文说明的单位边界。
 * - 修改：配置改变才解析 BigInteger；FE/RF 固定等价，热量/应力需要独立适配器和显式比率。
 * - 用途：能量生成、回收共用服务端配置与同一比例，客户端不能修改服务器计价。
 */
/**
 * 【变更记录 #6】2026-09-15 15:12:00（第 6 次备注 · 1.0.0-stage8-blackhole-charm）
 * - 删除：无移动黑洞饰品时只有方块黑洞的性能预算配置。
 * - 新增：护符扫描半径、实体上限、物品上限和扫描间隔配置，并提供范围校验读取方法。
 * - 修改：参数独立于方块黑洞预算，默认值限制每名玩家的扫描和 EMC 同步开销。
 * - 用途：让整合包作者可在功能范围与 TPS/内存峰值之间调整黑洞饰品吞吐。
 */
/**
 * 【变更记录 #7】2026-09-16（独立资源 EMC 换算）
 * - 删除：无；保留 FE/RF 原生能量换算配置。
 * - 新增：energy.native_resource_ratios，覆盖植物魔法魔力、气动工艺气压、自然灵气、
 *   新生魔艺魔源、机械动力转速与应力的 EMC/单位比例。
 * - 修改：默认值统一为 1/1000 EMC/单位，并以精确分数解析，避免 double 舍入和大数溢出。
 * - 用途：所有独立资源端口通过 EmcService 结算；未知单位没有价格，不会被自动交易。
 */
/**
 * 【变更记录 #8】2026-09-16 13:29:00（第 8 次备注 · 1.0.0-acceleration-safety）
 * - 删除：仅靠每 tick 目标数保证加速性能的假设。
 * - 新增：全服额外调用上限、耗时预算、正常 tick 接近 45 ms 时暂停、目标黑名单配置及中文说明。
 * - 修改：费用支持 128 位整数并缓存；配置未加载时使用默认值，客户端物品提示不触发未加载异常。
 * - 用途：服务器优先保证正常机器工作，加速器受共享预算约束，不补跑历史积压。
 */
/**
 * 【变更记录 #9】2026-09-16 14:36:38（第 9 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：每 2 倍速计价的旧配置键，避免已有存档的旧值覆盖新定价。
 * - 新增：每倍率每 tick 单价，默认 1000 EMC；每把加速器默认最多绑定 64 个目标，可配置 1 至 256。
 * - 修改：费用变为目标倍率乘以单价，各目标独立结算；全服执行预算保持共用。
 * - 用途：明确多目标费用与数量边界，旧存档首次加载使用新价格默认值。
 */
/**
 * 【变更记录 #10】2026-09-16 20:02:43（第 10 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：energy.create_stress_per_tick，默认每台每 tick 1048576 SU，允许 1 至 2147483647。
 * - 修改：独立资源配置未加载时使用默认值；注明 Create 价格按 RPM·tick / SU·tick 计算。
 * - 用途：提供应力供给与经济平衡上限，转速仍遵循 Create 自己的最大转速配置。
 */
/**
 * 【变更记录 #11】2026-09-18 01:06:25（第 11 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：按每 tick 收取倍率单价的配置键，防止旧配置覆盖新的计费单位。
 * - 新增：projecte.acceleration_emc_per_multiplier_second，默认每倍率每秒 1000 EMC。
 * - 修改：一秒按 20 个游戏 tick 分摊，限流费用和小数找零交由统一结算服务处理。
 * - 用途：服务端执行、物品提示及高倍率确认读取同一报价；原有性能预算保持有效。
 */
/**
 * 【变更记录 #12】2026-09-18 01:33:29（第 12 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：实体与全局时钟加速开关、实体黑名单，以及全服每 tick 范围扫描上限 2048。
 * - 修改：扫描与执行共用耗时预算，新增功能仍使用既有秒价、目标数和额外调用总限制。
 * - 用途：整合包作者可以限制新模式；未加载配置时使用安全默认值，不引入可选模组类型。
 */
/**
 * 【变更记录 #13】2026-09-18 02:30:44（第 13 次备注 · 1.0.0-acceleration-modes）
 * - 删除：总开关备注只描述方块实体 tick 的过时说明。
 * - 新增：明确总开关覆盖方块、范围、实体及全局时钟。
 * - 修改：仅更新中文配置注释和字段职责，现有开关值、默认值与执行判断保持一致。
 * - 用途：避免整合包作者误以为关闭总开关后实体或昼夜天气仍会被加速。
 */
/**
 * 【变更记录 #15】2026-10-02 18:54:32（第 15 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：media_trading 下流体/气体买入、卖出四个独立服务端开关，默认全部开启。
 * - 修改：配置通过 Forge SERVER 同步，按交易读取；未加载时保持默认允许。
 * - 用途：整合包可保留回收学习但关闭资源生成，不改价格、知识及现有黑名单。
 */
/**
 * 【变更记录 #16】2026-10-02 19:05:26（第 16 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：resource_stages 下流体/气体阶段规则列表、格式校验和双语配置说明。
 * - 修改：默认列表为空，配置变化由 ResourceStages 在下一次购买时读取。
 * - 用途：指定资源必须同时满足全部匹配阶段；不改变卖出、知识或黑名单。
 */
/**
 * 【变更记录 #17】2026-10-02 19:15:08（第 17 次备注 · 1.2.0-item-supply-stages）
 * - 删除：无。
 * - 新增：resource_stages.item_supply_requirements，默认空列表，支持精确物品 ID 和 namespace:*。
 * - 修改：沿用已有规则验证器，添加独立物品配置读取与双语界面说明。
 * - 用途：约束原初机器和背包补充，不预设阶段，不修改 ProjectE 原生取物和配方。
 */
/** 【变更记录 #20】2026-10-08 03:32:46（1.2.0-media-blacklist-tags）
 * 删除：无。新增：流体黑名单的 #namespace:tag 校验与 Holder 标签匹配。修改：fluid_blacklist 使用专用校验器，旧 ID 和 namespace:* 规则不变。用途：统一拦截价格查询、交易和黑洞回收。 */
/** 【变更记录 #21】2026-10-10 01:55:50（第 21 次备注 · 1.2.0-gas-blacklist-tags）
 * - 删除：气体黑名单不接受标签的限制。新增：#namespace:tag 校验与可选气体标签查询。
 * - 修改：精确 ID、命名空间先匹配，再查询实时标签；同步配置说明。
 * - 用途：气体定价及交易共用标签禁用规则，标签重载后不使用旧匹配缓存。 */
/**
 * 【变更记录 #22】2026-10-13 19:40:05（第 22 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：在同一 Forge 服务端 spec 注册 MediaAccessConfig，Configured 界面使用相同翻译键；无新增加载器或独立文件。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #23】2026-10-13 20:14:16（第 23 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
/**
 * 【变更记录 #24】2026-10-13 23:31:57（第 24 次备注 · 1.2.0-blacklist-system）
 * - 删除/新增/修改：删除每笔交易线性扫描/解析黑名单及流体 ID 重载漏标签的实现；新增统一 ResourceBlacklistRules 委托，允许规范化的省略命名空间 ID 和首尾空格；默认仍为空，既有 Configured spec 路径和含义不变。
 * - 用途：第 10 系统保持作者主动配置才生效；缓存和诊断共用真实规则，不删除已有价格与知识，不新增默认交易门槛。
 */
/**
 * 【变更记录 #25】2026-10-14 16:01:56（第 25 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：entity_emc.overrides 可逐实体覆盖同一买卖价格，0 禁用。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
/**
 * 【变更记录 #26】2026-10-14 17:10:00（第 26 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：铁魔法法力及 Goety 灵魂能量的默认换算条目。
 * - 修改：新增单位均为 1/1000 EMC/单位，作者可覆盖，不改变既有资源的价格。
 * - 用途：新玩家端口与机器资源共用服务器配置和精确分数账本。
 */
package com.primordial.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Primordial 服务端配置。 */
public final class PrimordialServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_LEARN_RECLAIMED_ITEMS;
    public static final ForgeConfigSpec.BooleanValue FLUID_PURCHASE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue FLUID_SALE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue GAS_PURCHASE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue GAS_SALE_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FLUID_STAGE_REQUIREMENTS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> GAS_STAGE_REQUIREMENTS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ITEM_SUPPLY_STAGE_REQUIREMENTS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> RESOURCE_STAGE_REQUIREMENTS;
    /** 是否在服务器启动时建立未知物品的配方 EMC 快照。 */
    public static final ForgeConfigSpec.BooleanValue AUTO_CALCULATE_RECIPE_EMC;
    /** 是否允许加速器执行任何模式的额外工作。 */
    public static final ForgeConfigSpec.BooleanValue ACCELERATION_ENABLED;
    /** 每个目标每秒（20 个游戏 tick）的完整费用为倍率乘以此单价。 */
    public static final ForgeConfigSpec.ConfigValue<String> ACCELERATION_EMC_PER_MULTIPLIER_SECOND;
    public static final ForgeConfigSpec.IntValue ACCELERATION_MAX_BOUND_TARGETS;
    /** 单个服务端 tick 最多处理的加速目标数，防止大量玩家同时加速拖垮服务器。 */
    public static final ForgeConfigSpec.IntValue ACCELERATION_MAX_TARGETS_PER_TICK;
    public static final ForgeConfigSpec.IntValue ACCELERATION_MAX_EXTRA_TICKS;
    public static final ForgeConfigSpec.IntValue ACCELERATION_TIME_BUDGET_MICROS;
    public static final ForgeConfigSpec.IntValue ACCELERATION_PAUSE_ABOVE_MILLIS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ACCELERATION_BLOCKLIST;
    public static final ForgeConfigSpec.BooleanValue ACCELERATION_ENTITIES_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ACCELERATION_WORLD_ENABLED;
    public static final ForgeConfigSpec.IntValue ACCELERATION_MAX_SCANS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ACCELERATION_ENTITY_BLOCKLIST;
    private static String cachedAccelerationText = "1000";
    private static java.math.BigInteger cachedAccelerationValue = java.math.BigInteger.valueOf(1000);
    public static final ForgeConfigSpec.ConfigValue<String> FE_PER_EMC;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> NATIVE_FE_RATIOS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FLUID_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> GAS_BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> RESOURCE_BLACKLIST;
    /** 独立资源每单位对应的 EMC，格式为 namespace:unit=分子/分母。 */
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> NATIVE_RESOURCE_RATIOS;
    public static final ForgeConfigSpec.IntValue CREATE_STRESS_PER_TICK;
    private static String cachedFeText = "1000";
    private static java.math.BigInteger cachedFeValue = java.math.BigInteger.valueOf(1000);
    /** 黑洞每 tick 最多处理的掉落实体数；限制实体列表和 NBT 复制带来的瞬时内存峰值。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_MAX_ITEM_ENTITIES_PER_TICK;
    /** 黑洞每 tick 最多接收的物品数量；管道发送超大堆时只接收预算内部分并返回余量。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_MAX_ITEM_COUNT_PER_TICK;
    /** 黑洞每 tick 最多接收的流体数量（mB）；防止异常管道请求占满服务器线程。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_MAX_FLUID_MB_PER_TICK;
    /** 气体使用 long API，但每 tick 接收预算为有限整数，余量留给管道重试。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_MAX_GAS_MB_PER_TICK;
    /** 黑洞每 tick 最多接收的 Forge Energy；超出部分由能量管道在后续 tick 重试。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_MAX_ENERGY_PER_TICK;
    /** 黑洞粒子最小间隔；粒子仅用于反馈，不能影响物品/流体/能量结算。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_PARTICLE_INTERVAL_TICKS;
    /** 黑洞饰品的掉落物扫描半径（方块）。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_CHARM_RADIUS;
    /** 每名玩家的黑洞饰品每次扫描最多处理的实体数。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_CHARM_MAX_ENTITIES_PER_TICK;
    /** 每名玩家的黑洞饰品每次扫描最多吸收的物品数量。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK;
    /** 黑洞饰品扫描之间的最小 tick 间隔。 */
    public static final ForgeConfigSpec.IntValue BLACKHOLE_CHARM_SCAN_INTERVAL_TICKS;

    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> ENTITY_EMC_OVERRIDES;
    public static java.util.List<? extends String> entityEmcOverrides(){return SPEC.isLoaded()?ENTITY_EMC_OVERRIDES.get():java.util.List.of();}

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("projecte");
        AUTO_LEARN_RECLAIMED_ITEMS = builder
                .comment(
                        "回收物品后自动写入 ProjectE 知识（Configurator/Black Hole）。",
                        "Automatically learn an item in ProjectE when the configurator or black hole reclaims it.",
                        "关闭时仍结算 EMC，但不会改变 ProjectE 知识。",
                        "When false, reclaim still grants EMC but does not change ProjectE knowledge.")
                .translation("config.primordial.auto_learn_reclaimed_items")
                .define("auto_learn_reclaimed_items", true);
        AUTO_CALCULATE_RECIPE_EMC = builder
                .comment("服务器启动时按已注册配方自动计算没有人工或 ProjectE 价格的物品。",
                        "Calculate recipe EMC for items without manual or native ProjectE prices at server startup.",
                        "关闭时只使用人工价格和 ProjectE 原生价格。",
                        "When false, only manual and native ProjectE prices are used.")
                .translation("config.primordial.auto_calculate_recipe_emc")
                .define("auto_calculate_recipe_emc", true);
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

        builder.push("energy");
        FE_PER_EMC = builder.comment("每 1 EMC 对应的 FE 数，生成和回收共用。使用正整数文本，最大 128 位；默认 1000。",
                        "FE per EMC for both supply and reclaim. Positive integer string, up to 128 digits.")
                .translation("config.primordial.fe_per_emc")
                .define("fe_per_emc", "1000", v -> v instanceof String s && s.length() <= 128
                        && s.matches("[0-9]+") && new java.math.BigInteger(s).signum() > 0);
        NATIVE_FE_RATIOS = builder.comment("原生能源每单位对应多少 FE，格式 命名空间:单位=分子/分母。仅用于原生单位报价，已提供 FE 的机器不再乘此比例。",
                        "Native unit to FE ratios: namespace:unit=numerator/denominator. Never applied again to a Forge Energy capability.",
                        "默认跟随 MEK、AE2 自身转换配置；这里可覆盖，例如 mekanism:joule=2/5、ae2:ae=2/1。",
                        "Mekanism and AE2 defaults follow their native settings. Overrides are optional. Unknown units have no EMC value.",
                        "forge:fe 与 cofh:rf 固定等价，不能在此覆盖。热量/应力仅登记价格并不创建机器接口，须另写原生适配器。",
                        "FE/RF cannot be overridden here. Pricing heat/stress does not create machine capabilities; a native adapter is required.")
                .translation("config.primordial.native_fe_ratios")
                .defineListAllowEmpty("native_fe_ratios", java.util.List.of(),
                        com.primordial.energy.EnergyConversions::validEntry);
        NATIVE_RESOURCE_RATIOS = builder.comment(
                        "独立资源每单位对应多少 EMC，格式 命名空间:单位=分子/分母。默认均为 1/1000 EMC/单位。",
                        "Standalone resource EMC per unit, format namespace:unit=numerator/denominator. Defaults are 1/1000 EMC per unit.",
                        "支持 botania:mana、pneumaticcraft:pressure、naturesaura:aura、ars_nouveau:source、create:rotation_speed、create:stress、irons_spellbooks:mana 和 goety:soul_energy。",
                        "Supported defaults: Botania mana, PneumaticCraft pressure, Nature's Aura aura, Ars Nouveau source, Create speed and stress.",
                        "仅已注册且具备原生读写接口的端口会使用这些价格；未知单位不会自动生成资源。",
                        "Only registered ports with native read/write methods use these prices; unknown units are never generated automatically.")
                .translation("config.primordial.native_resource_ratios")
                .defineListAllowEmpty("native_resource_ratios", java.util.List.of(
                        "botania:mana=1/1000",
                        "pneumaticcraft:pressure=1/1000",
                        "naturesaura:aura=1/1000",
                        "ars_nouveau:source=1/1000",
                        "create:rotation_speed=1/1000",
                        "create:stress=1/1000",
                        "irons_spellbooks:mana=1/1000",
                        "goety:soul_energy=1/1000"),
                com.primordial.resource.ResourceExchangeConversions::validEntry);
        FLUID_BLACKLIST = builder.comment(
                        "禁止转换的 Forge 流体注册名，支持 namespace:* 和 #namespace:tag；默认空列表。匹配后不能购买或回收，不删除知识和定价。",
                        "Forge fluid registry names blocked from conversion; supports namespace:* and #namespace:tag. Empty by default; stored knowledge and prices are preserved.")
                .translation("config.primordial.fluid_blacklist")
                .defineListAllowEmpty("fluid_blacklist", java.util.List.of(), PrimordialServerConfig::validFluidBlacklistEntry);
        GAS_BLACKLIST = builder.comment(
                        "禁止转换的 Mekanism 气体注册名，支持 namespace:* 和 #namespace:tag；默认空列表。匹配后不能购买或回收，不删除知识和定价。",
                        "Mekanism gas registry names blocked from conversion; supports namespace:* and #namespace:tag. Empty by default; stored knowledge and prices are preserved.")
                .translation("config.primordial.gas_blacklist")
                .defineListAllowEmpty("gas_blacklist", java.util.List.of(), PrimordialServerConfig::validFluidBlacklistEntry);
        RESOURCE_BLACKLIST = builder.comment(
                        "禁止转换的独立资源单位注册名，支持 namespace:*；默认空列表。匹配后不能购买或回收，不删除知识和定价。",
                        "Native resource units blocked from conversion; supports namespace:*. Empty by default; no registry tags for virtual units." )
                .translation("config.primordial.resource_blacklist")
                .defineListAllowEmpty("resource_blacklist", java.util.List.of(), PrimordialServerConfig::validMediaBlacklistEntry);
        CREATE_STRESS_PER_TICK = builder.comment(
                        "每台 Create 动力机器每 tick 可购买的最大应力容量（SU），默认 1048576。补充模式每 tick 买满此额度。",
                        "转速按 RPM×tick，应力按 SU×tick 使用上面的 native_resource_ratios 计费；暂停或欠费后停止续供。",
                        "固定模式每次购买只供给 1 tick；持续动力请将间隔设为 1 tick。转速上限使用 Create 的 maxRotationSpeed。")
                .translation("config.primordial.create_stress_per_tick")
                .defineInRange("create_stress_per_tick", 1048576, 1, Integer.MAX_VALUE);
        builder.pop();

        builder.push("media_trading");
        FLUID_PURCHASE_ENABLED = builder.comment("允许用 EMC 购买流体（转化桌和自动供应）；关闭不删除已学知识。",
                        "Allow fluid purchases and automatic supply. Existing knowledge is retained.")
                .translation("config.primordial.fluid_purchase_enabled").define("fluid_purchase_enabled", true);
        FLUID_SALE_ENABLED = builder.comment("允许流体卖入和自动回收，包括黑洞；关闭时不抽取流体、不入账或学习。",
                        "Allow fluid sales and reclamation, including black holes. Disabled transactions consume nothing.")
                .translation("config.primordial.fluid_sale_enabled").define("fluid_sale_enabled", true);
        GAS_PURCHASE_ENABLED = builder.comment("允许用 EMC 购买气体（转化桌和自动供应）；关闭不删除已学知识。",
                        "Allow gas purchases and automatic supply. Existing knowledge is retained.")
                .translation("config.primordial.gas_purchase_enabled").define("gas_purchase_enabled", true);
        GAS_SALE_ENABLED = builder.comment("允许气体卖入和自动回收，包括黑洞；关闭时不抽取气体、不入账或学习。",
                        "Allow gas sales and reclamation, including black holes. Disabled transactions consume nothing.")
                .translation("config.primordial.gas_sale_enabled").define("gas_sale_enabled", true);
        builder.pop();
        builder.push("resource_stages");
        ITEM_SUPPLY_STAGE_REQUIREMENTS = builder.comment(
                        "原初机器和玩家背包补充物品所需阶段，格式 物品注册名=阶段名；默认空列表。",
                        "Stages for Primordial machine/inventory item supply: item_id=stage_id. Empty by default.",
                        "支持 namespace:*，所有匹配阶段均需满足，覆盖该物品的所有 NBT 变体。不限制回收或 ProjectE 原生转化桌取物。",
                        "Supports namespace:*; all matching stages are required for all NBT variants. Does not restrict reclamation or native ProjectE table extraction.")
                .translation("config.primordial.item_supply_stage_requirements")
                .defineListAllowEmpty("item_supply_requirements", java.util.List.of(), com.primordial.service.ResourceStages::validRule);
        FLUID_STAGE_REQUIREMENTS = builder.comment(
                        "流体购买/机器补充所需阶段，格式 资源注册名=阶段名；支持 namespace:*，所有匹配条件必须满足。",
                        "Fluid purchase/supply stages: resource_id=stage_id. Supports namespace:*; all matching stages are required.",
                        "例如 minecraft:lava=pack:nether；使用 /primordial stage grant <玩家> pack:nether 解锁。卖入学习不受阶段限制。",
                        "Example: minecraft:lava=pack:nether. Grant via /primordial stage grant <player> pack:nether. Sales remain available.")
                .translation("config.primordial.fluid_stage_requirements")
                .defineListAllowEmpty("fluid_requirements", java.util.List.of(), com.primordial.service.ResourceStages::validRule);
        GAS_STAGE_REQUIREMENTS = builder.comment(
                        "气体购买/机器补充所需阶段，格式 资源注册名=阶段名；凭证和免知识配置不能绕过阶段。",
                        "Gas purchase/supply stages: resource_id=stage_id. Certificates and disabled knowledge checks do not bypass stages.",
                        "例如 mekanism:antimatter=pack:endgame；精确规则与 namespace:* 通配规则叠加，所有阶段都必须满足。",
                        "Example: mekanism:antimatter=pack:endgame. Exact and namespace:* rules combine; all stages must be met.")
                .translation("config.primordial.gas_stage_requirements")
                .defineListAllowEmpty("gas_requirements", java.util.List.of(), com.primordial.service.ResourceStages::validRule);
        RESOURCE_STAGE_REQUIREMENTS = builder.comment(
                        "独立资源购买/机器补充所需阶段，格式 resource_id=stage_id；支持 namespace:*。",
                        "Standalone resource purchase/supply stages: resource_id=stage_id; supports namespace:*." )
                .translation("config.primordial.resource_stage_requirements")
                .defineListAllowEmpty("resource_requirements", java.util.List.of(), com.primordial.service.ResourceStages::validRule);
        builder.pop();
        builder.push("performance");
        BLACKHOLE_MAX_ITEM_ENTITIES_PER_TICK = builder
                .comment(
                        "黑洞每 tick 最多吸收的掉落实体数量。较小值可降低大量掉落物同时进入时的 CPU 和内存峰值。",
                        "Maximum item entities absorbed by one black hole per tick. Lower values reduce CPU and memory spikes.")
                .translation("config.primordial.blackhole_max_item_entities_per_tick")
                .defineInRange("blackhole_max_item_entities_per_tick", 16, 1, 4096);
        BLACKHOLE_MAX_ITEM_COUNT_PER_TICK = builder
                .comment(
                        "黑洞每 tick 最多接收的物品数量。IItemHandler 超出此预算时必须返回余量，避免管道重复发送无限大堆。",
                        "Maximum item count accepted by one black hole per tick; excess input is returned to the transport pipe.")
                .translation("config.primordial.blackhole_max_item_count_per_tick")
                .defineInRange("blackhole_max_item_count_per_tick", 4096, 1, Integer.MAX_VALUE);
        BLACKHOLE_MAX_FLUID_MB_PER_TICK = builder
                .comment(
                        "黑洞每 tick 最多接收的流体数量（mB）。",
                        "Maximum fluid amount in millibuckets accepted by one black hole per tick.")
                .translation("config.primordial.blackhole_max_fluid_mb_per_tick")
                .defineInRange("blackhole_max_fluid_mb_per_tick", 100000, 1, Integer.MAX_VALUE);
        BLACKHOLE_MAX_GAS_MB_PER_TICK = builder
                .comment("黑洞每 tick 最多接收的 Mekanism 气体数量（mB）；未覆盖气体默认按 1 EMC/mB，价表中的 0 表示禁用。",
                        "Maximum Mekanism gas mB accepted per black hole per tick; unlisted gases default to 1 EMC/mB and an explicit zero disables conversion.")
                .translation("config.primordial.blackhole_max_gas_mb_per_tick")
                .defineInRange("blackhole_max_gas_mb_per_tick", 100000, 1, Integer.MAX_VALUE);
        BLACKHOLE_MAX_ENERGY_PER_TICK = builder
                .comment(
                        "黑洞每 tick 最多接收的 Forge Energy。FE 与 EMC 的换算余数会保留到后续 tick。",
                        "Maximum Forge Energy accepted by one black hole per tick; conversion remainders are preserved.")
                .translation("config.primordial.blackhole_max_energy_per_tick")
                .defineInRange("blackhole_max_energy_per_tick", 4000000, 1, Integer.MAX_VALUE);
        BLACKHOLE_PARTICLE_INTERVAL_TICKS = builder
                .comment(
                        "黑洞粒子的最小生成间隔（tick）。设为 1 表示每 tick 最多一批粒子。",
                        "Minimum particle interval in ticks; one particle burst at most per interval.")
                .translation("config.primordial.blackhole_particle_interval_ticks")
                .defineInRange("blackhole_particle_interval_ticks", 5, 1, 200);
        BLACKHOLE_CHARM_RADIUS = builder
                .comment("黑洞饰品扫描玩家周围的掉落物半径（方块）。", "Black hole charm item scan radius in blocks.")
                .translation("config.primordial.blackhole_charm_radius")
                .defineInRange("blackhole_charm_radius", 8, 1, 32);
        BLACKHOLE_CHARM_MAX_ENTITIES_PER_TICK = builder
                .comment("黑洞饰品每次扫描最多处理的掉落实体数量。", "Maximum item entities handled by one charm scan.")
                .translation("config.primordial.blackhole_charm_max_entities_per_tick")
                .defineInRange("blackhole_charm_max_entities_per_tick", 32, 1, 512);
        BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK = builder
                .comment("黑洞饰品每次扫描最多吸收的物品数量。", "Maximum item count absorbed by one charm scan.")
                .translation("config.primordial.blackhole_charm_max_items_per_tick")
                .defineInRange("blackhole_charm_max_items_per_tick", 4096, 1, Integer.MAX_VALUE);
        BLACKHOLE_CHARM_SCAN_INTERVAL_TICKS = builder
                .comment("黑洞饰品扫描间隔（tick）。", "Minimum interval between charm scans in ticks.")
                .translation("config.primordial.blackhole_charm_scan_interval_ticks")
                .defineInRange("blackhole_charm_scan_interval_ticks", 1, 1, 200);
        builder.pop();
        builder.push("entity_emc");
        ENTITY_EMC_OVERRIDES = builder.comment("实体生成与黑洞吸收共用的单价覆盖，格式 minecraft:zombie=5120；0 禁止兑换/吸收。",
                "默认基础生命值每点 128 EMC，怪物 2 倍，凋灵/末影龙 64 倍；非生物实体默认 1024。",
                "玩家、掉落物、经验、投射物、技能黑洞不参与实体兑换；模组实体需显式正价覆盖才加入生成目录。")
                .defineListAllowEmpty("overrides",java.util.List.of(),value -> value instanceof String text && text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+=[0-9]{1,128}"));
        builder.pop();
        SPEC = builder.build();
    }

    private PrimordialServerConfig() {}

    public static synchronized java.math.BigInteger fePerEmc() {
        String text = SPEC.isLoaded() ? FE_PER_EMC.get() : "1000";
        if (!text.equals(cachedFeText)) {
            cachedFeValue = new java.math.BigInteger(text);
            cachedFeText = text;
        }
        return cachedFeValue;
    }

    public static java.util.List<? extends String> nativeFeRatios() {
        return SPEC.isLoaded() ? NATIVE_FE_RATIOS.get() : java.util.List.of();
    }

    /** 返回当前服务端配置；Forge 会在配置文件异常时回退到默认值。 */
    public static boolean autoLearnReclaimedItems() {
        return AUTO_LEARN_RECLAIMED_ITEMS.get();
    }

    public static boolean autoCalculateRecipeEmc() { return AUTO_CALCULATE_RECIPE_EMC.get(); }

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

    public static boolean mediaPurchaseDisabled(boolean gas) {
        return SPEC.isLoaded() && !(gas ? GAS_PURCHASE_ENABLED : FLUID_PURCHASE_ENABLED).get();
    }

    public static java.util.List<? extends String> mediaStageRequirements(boolean gas) {
        return SPEC.isLoaded() ? (gas ? GAS_STAGE_REQUIREMENTS : FLUID_STAGE_REQUIREMENTS).get() : java.util.List.of();
    }

    public static java.util.List<? extends String> itemStageRequirements() {
        return SPEC.isLoaded() ? ITEM_SUPPLY_STAGE_REQUIREMENTS.get() : java.util.List.of();
    }

    public static java.util.List<? extends String> resourceStageRequirements() {
        return SPEC.isLoaded() ? RESOURCE_STAGE_REQUIREMENTS.get() : java.util.List.of();
    }

    public static boolean mediaSaleDisabled(boolean gas) {
        return SPEC.isLoaded() && !(gas ? GAS_SALE_ENABLED : FLUID_SALE_ENABLED).get();
    }

    public static int blackholeMaxItemEntitiesPerTick() {
        return BLACKHOLE_MAX_ITEM_ENTITIES_PER_TICK.get();
    }

    public static int blackholeMaxItemCountPerTick() {
        return BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.get();
    }

    public static int blackholeMaxFluidMbPerTick() {
        return BLACKHOLE_MAX_FLUID_MB_PER_TICK.get();
    }

    public static int blackholeMaxEnergyPerTick() {
        return BLACKHOLE_MAX_ENERGY_PER_TICK.get();
    }

    public static int blackholeMaxGasMbPerTick() {
        return BLACKHOLE_MAX_GAS_MB_PER_TICK.get();
    }

    public static int blackholeParticleIntervalTicks() {
        return BLACKHOLE_PARTICLE_INTERVAL_TICKS.get();
    }

    public static int blackholeCharmRadius() { return BLACKHOLE_CHARM_RADIUS.get(); }
    public static int blackholeCharmMaxEntitiesPerTick() { return BLACKHOLE_CHARM_MAX_ENTITIES_PER_TICK.get(); }
    public static int blackholeCharmMaxItemsPerTick() { return BLACKHOLE_CHARM_MAX_ITEMS_PER_TICK.get(); }
    public static int blackholeCharmScanIntervalTicks() { return BLACKHOLE_CHARM_SCAN_INTERVAL_TICKS.get(); }

    /** 返回独立资源换算配置的只读快照，调用者不能修改 Forge 配置内部列表。 */
    public static java.util.List<? extends String> nativeResourceRatios() {
        java.util.List<? extends String> entries = SPEC.isLoaded() ? NATIVE_RESOURCE_RATIOS.get() : java.util.List.of();
        return entries == null ? java.util.List.of() : java.util.List.copyOf(entries);
    }

    private static boolean validMediaBlacklistEntry(Object value) {
        return ResourceBlacklistRules.validEntry(value, false);
    }

    private static boolean validFluidBlacklistEntry(Object value) {
        return ResourceBlacklistRules.validEntry(value, true);
    }

    /** 对象和注册名入口使用同一份规则，标签不会因调用重载不同而失效。 */
    public static boolean fluidBlocked(net.minecraft.world.level.material.Fluid fluid) {
        return fluid == null || fluidBlocked(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid));
    }

    public static boolean fluidBlocked(net.minecraft.resources.ResourceLocation id) {
        return id == null || ResourceBlacklistRules.matchingRule(ResourceBlacklistRules.Kind.FLUID, id).isPresent();
    }

    public static boolean gasBlocked(net.minecraft.resources.ResourceLocation id) {
        return id == null || ResourceBlacklistRules.matchingRule(ResourceBlacklistRules.Kind.GAS, id).isPresent();
    }

    public static boolean resourceBlocked(net.minecraft.resources.ResourceLocation id) {
        return id == null || ResourceBlacklistRules.matchingRule(ResourceBlacklistRules.Kind.RESOURCE, id).isPresent();
    }

    public static int createStressPerTick() { return SPEC.isLoaded() ? CREATE_STRESS_PER_TICK.get() : 1048576; }
}
