/**
 * 【文件职责】管理输出知识开关的启动快照。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：移除普通物品始终强制知识、资源没有知识限制的不一致策略。
 * - 新增：中文 COMMON 配置 require_transmutation_knowledge，默认true，关联输出门禁、配方与原生价表。
 * - 修改：只在Loading事件读取；热重载不改变本次会话，修改后重启生效。
 * - 用途：配方加载前即可读取同一规则，避免服务器配置加载太晚导致证书配方状态错误。
 */
/**
 * 【文件职责】保存启动级知识配置快照。
 * 【变更记录 #2】2026-09-16 19:46:19（第 2 次备注 · 1.0.0-review-ready）
 * - 删除：配置类上的自动事件订阅注解。
 * - 新增：由模组构造函数直接注册onLoad方法。
 * - 修改：配置对象与Forge事件发现解耦，保持相同Loading时机及热重载不生效策略。
 * - 用途：纯JUnit可校验策略而不触发Forge总线启动，实际游戏仍在配方加载前读取配置。
 *
 * 【变更记录 #3】2026-10-02 14:30:00（第 3 次备注 · 1.2.0 凭证配方策略）
 * - 删除：液体凭证和气体凭证的本模组默认合成配方；对应资源类别仍保留凭证物品和 ProjectE EMC 价值。
 * - 新增：魔力、气压、灵气、魔源、转速、应力凭证继续使用本模组条件配方，供不能直接卖入转化桌的资源学习。
 * - 修改：配置说明不再把九种凭证统一描述为“开启时有配方、关闭时全部无配方”；知识开关只控制学习门禁，液体/气体配方删除是固定资源策略。
 * - 用途：液体和气体凭证由整合包作者自行定义毕业配方，卖入转化桌后按类别解锁；其它独立资源仍有可制作的知识入口。
 */
/**
 * 【变更记录 #4】2026-10-13 23:59:40（第 4 次备注 · 1.2.0-discovery-learning-system）
 * - 删除：配置说明中流体/气体只能通过凭证学习的过时描述。
 * - 新增：实际卖入学习单种资源、作者凭证可授权类别的说明。
 * - 修改：同步 Configured 同源说明与中英文翻译，不改变默认值或重启要求。
 * - 用途：知识系统说明与真实玩法一致，无新增设置。
 */
package com.primordial.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** 转化知识是启动级规则，COMMON 配置先于配方条件加载。修改后重启游戏/服务器生效。 */
public final class PrimordialKnowledgeConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue REQUIRE_KNOWLEDGE;
    private static volatile boolean activeRequirement = true;
    static {
        var builder = new ForgeConfigSpec.Builder();
        REQUIRE_KNOWLEDGE = builder.comment("配置器输出物品和资源是否需要 ProjectE 转化桌知识。",
                "开启：物品需要自身知识；流体和气体真实卖入后学习对应资源，作者提供的类别凭证也可授权。",
                "能量及六种独立资源需要对应凭证知识；仅持有凭证不算学习。",
                "关闭：跳过上述知识检查；液体和气体凭证没有本模组默认配方，其它资源凭证配方仍由数据包条件控制。",
                "修改后必须重启游戏或服务器，确保配方、ProjectE 价表与输出策略一致。")
                .translation("config.primordial.require_transmutation_knowledge")
                .worldRestart().define("require_transmutation_knowledge", true);
        SPEC = builder.build();
    }
    private PrimordialKnowledgeConfig() { }
    public static boolean required() {
        return activeRequirement;
    }
    /** 不响应热重载，以免配方和已发给客户端的原生价格与输出规则不同步。 */
    public static void onLoad(net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) activeRequirement = REQUIRE_KNOWLEDGE.get();
    }
}
