/**
 * 【文件职责】保存知识要求的 COMMON 启动快照，供配方、原生 EMC 和购买授权共用。
 * 【变更记录 #1】2026-10-23 13:30:35（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：默认 true 的 require_transmutation_knowledge，Loading 时发布快照。
 * - 修改：热重载不改变正在运行的知识策略，避免配方、客户端价格和服务端购买权限不同步。
 * - 用途：通过已有 NeoForge 原生配置界面编辑；关闭知识仍保留显式遗忘、价格和安全检查。
 */
package com.primordial.neoforge.config;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeKnowledgeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue REQUIRE_KNOWLEDGE;
    private static volatile boolean activeRequirement = true;

    static {
        var builder = new ModConfigSpec.Builder();
        REQUIRE_KNOWLEDGE = builder
                .comment("输出是否需要 ProjectE 已学习知识；仅持有凭证不算学习。",
                        "流体/化学品可真实卖入逐种学习或由类别凭证授权；其它资源需要对应凭证。",
                        "关闭只跳过知识检查，不覆盖显式遗忘、安全策略或价格/黑名单；修改后重启生效。",
                        "Fluid/gas certificates have no default recipe. Other certificate recipes require this startup setting.")
                .translation("config.primordial.require_transmutation_knowledge")
                .worldRestart().define("require_transmutation_knowledge", true);
        SPEC = builder.build();
    }

    private NeoForgeKnowledgeConfig() {}

    public static void register(ModContainer container, IEventBus modBus) {
        container.registerConfig(ModConfig.Type.COMMON, SPEC);
        modBus.addListener(NeoForgeKnowledgeConfig::onLoad);
    }

    public static boolean required() { return activeRequirement; }

    /** 只接受启动 Loading，原生界面的 Reloading 留待下次启动，不能即时改变配方与价格。 */
    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) activeRequirement = REQUIRE_KNOWLEDGE.get();
    }
}
