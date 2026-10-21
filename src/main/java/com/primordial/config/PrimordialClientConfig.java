/**
 * 【变更记录 #2】2026-09-15（客户端配置中文化）
 * - 删除：无；保留 EMC 显示模式的枚举校验和非法值回退逻辑。
 * - 新增：为 emc_display_mode 绑定 config.primordial.emc_display_mode 翻译键。
 * - 修改：配置界面标签由语言资源提供中文名称，默认模式仍为 COMPACT（紧凑显示）。
 * - 用途：让中文客户端能够直接理解显示模式设置，同时保持配置文件键名稳定。
 */
/**
 * 【文件职责】
 * - 定义客户端 EMC 显示模式配置，并校验配置值只能是 COMPACT、ENGINEERING 或 SCIENTIFIC。
 * - 向 EmcFormatter 提供安全的枚举访问，非法配置回退到 COMPACT。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：无；该文件为客户端显示配置的新入口。
 * - 新增：ForgeConfigSpec、emc_display_mode 配置项和默认 COMPACT 模式。
 * - 修改：无旧逻辑可替换；配置值读取统一转为大写枚举并处理空值/非法值。
 * - 用途：集中管理显示偏好，避免 HUD、配置器和未来界面各自解析字符串；该配置不应参与服务端 EMC 结算。
 */
package com.primordial.config;

import java.util.Locale;

import net.minecraftforge.common.ForgeConfigSpec;

/** 客户端显示配置。 */
public final class PrimordialClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> EMC_DISPLAY_MODE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("display");
        EMC_DISPLAY_MODE = builder
                .comment("EMC display mode: COMPACT, ENGINEERING or SCIENTIFIC")
                .translation("config.primordial.emc_display_mode")
                .define("emc_display_mode", "COMPACT",
                        value -> value instanceof String s && switch (s.toUpperCase(Locale.ROOT)) {
                            case "COMPACT", "ENGINEERING", "SCIENTIFIC" -> true;
                            default -> false;
                        });
        builder.pop();
        SPEC = builder.build();
    }

    private PrimordialClientConfig() {}

    public static DisplayMode displayMode() {
        String value = EMC_DISPLAY_MODE.get();
        try {
            return DisplayMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return DisplayMode.COMPACT;
        }
    }

    public enum DisplayMode {
        COMPACT,
        ENGINEERING,
        SCIENTIFIC
    }
}
