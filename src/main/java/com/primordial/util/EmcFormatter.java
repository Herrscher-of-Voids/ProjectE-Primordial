/**
 * 【文件职责】
 * - 使用 BigInteger/BigDecimal 将 EMC 格式化为紧凑、工程、科学和完整分组文本。
 * - 统一供 HUD、配置器和 Tooltip 调用，避免不同界面产生不同舍入规则。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：无；该文件为 EMC 显示格式化的新工具类。
 * - 新增：三种显示模式、完整数字分组、正数校验和高位单位表（K 至 Y）。
 * - 修改：无旧逻辑可替换；紧凑模式向下保留两位，工程模式使用 3 位有效数字，科学模式保留约 4 位尾数。
 * - 用途：承接 BigInteger EMC 显示，避免 long 溢出和 double 精度损失；格式化只读输入，不修改配置或余额。
 */
package com.primordial.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import com.primordial.config.PrimordialClientConfig;

/** EMC 的统一显示格式化工具。所有计算使用 BigInteger，避免 long 溢出。 */
public final class EmcFormatter {
    private static final BigInteger[] COMPACT_UNITS = {
            BigInteger.TEN.pow(24),
            BigInteger.TEN.pow(21),
            BigInteger.TEN.pow(18),
            BigInteger.TEN.pow(15),
            BigInteger.TEN.pow(12),
            BigInteger.TEN.pow(9),
            BigInteger.TEN.pow(6),
            BigInteger.TEN.pow(3)
    };

    private static final String[] COMPACT_SUFFIXES = {"Y", "Z", "E", "P", "T", "G", "M", "K"};

    private EmcFormatter() {}

    public static String format(BigInteger value) {
        return switch (PrimordialClientConfig.displayMode()) {
            case ENGINEERING -> formatEngineering(value, 3);
            case SCIENTIFIC -> toScientific(value);
            case COMPACT -> formatCompact(value);
        };
    }

    /** 科学计数法，例如 2147483647 -> 2.147×10^9。 */
    public static String toScientific(BigInteger value) {
        if (value == null || value.signum() <= 0) return "0";

        String s = value.toString();
        if (s.length() <= 6) return s;

        int exponent = s.length() - 1;
        StringBuilder mantissa = new StringBuilder();
        mantissa.append(s.charAt(0)).append('.');
        mantissa.append(s, 1, Math.min(4, s.length()));

        while (mantissa.length() > 1 && mantissa.charAt(mantissa.length() - 1) == '0') {
            mantissa.deleteCharAt(mantissa.length() - 1);
        }
        if (mantissa.length() > 1 && mantissa.charAt(mantissa.length() - 1) == '.') {
            mantissa.deleteCharAt(mantissa.length() - 1);
        }
        return mantissa + "×10^" + exponent;
    }

    /** 完整数字，使用逗号进行三位分组。 */
    public static String toFullGrouped(BigInteger value) {
        if (value == null || value.signum() <= 0) return "0";

        StringBuilder result = new StringBuilder(value.toString());
        for (int i = result.length() - 3; i > 0; i -= 3) {
            result.insert(i, ',');
        }
        return result.toString();
    }

    /** 紧凑显示，例如 32767 -> 32.76K。 */
    public static String formatCompact(BigInteger value) {
        if (value == null || value.signum() <= 0) return "0";

        for (int i = 0; i < COMPACT_UNITS.length; i++) {
            BigInteger unit = COMPACT_UNITS[i];
            if (value.compareTo(unit) >= 0) {
                BigDecimal result = new BigDecimal(value)
                        .divide(new BigDecimal(unit), 2, RoundingMode.DOWN)
                        .stripTrailingZeros();
                return result.toPlainString() + COMPACT_SUFFIXES[i];
            }
        }
        return value.toString();
    }

    /** 工程计数法，默认由 format 使用 3 位有效数字。 */
    public static String formatEngineering(BigInteger value, int significantDigits) {
        if (value == null || value.signum() <= 0) return "0";
        if (significantDigits < 1) significantDigits = 3;

        return new BigDecimal(value)
                .round(new MathContext(significantDigits, RoundingMode.HALF_UP))
                .toEngineeringString();
    }
}
