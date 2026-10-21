/**
 * 【变更记录 #1】2026-10-08 02:06:10（第 1 次备注 · 1.2.0-jei-counts）
 * - 删除：无；新增：任意精度兑换数量与紧凑文本计算；修改：不使用浮点金额。
 * - 用途：巨大 EMC 不溢出，未学习或无价物品不显示数量。
 */
/** 【变更记录 #2】2026-10-08（1.2.0-jei-media-counts）
 * 删除：无。新增：BigInteger 单价的 JEI 数量格式化。修改：物品和介质共用同一显示规则。用途：避免流体/气体价格超过 long 时溢出。 */
package com.primordial.client;
import java.math.BigInteger;
import com.primordial.service.EmcAmount;
public final class EmcCountText {
    private EmcCountText() { }
    public static String label(boolean learned, BigInteger balance, long price) {
        return label(learned, balance, price <= 0 ? BigInteger.ZERO : BigInteger.valueOf(price));
    }
    public static String label(boolean learned, BigInteger balance, BigInteger price) {
        if (!learned || price == null || price.signum() <= 0) return "";
        String digits = balance.max(BigInteger.ZERO).divide(price).toString();
        if (digits.length() <= 3) return digits;
        int group = (digits.length() - 1) / 3;
        if (group > 4) return digits.charAt(0) + "e" + (digits.length() - 1);
        int leading = digits.length() - group * 3;
        String suffix = new String[]{"", "K", "M", "B", "T"}[group];
        return digits.substring(0, leading) + (leading == 1 && digits.charAt(leading) != '0' ? "." + digits.charAt(leading) : "") + suffix;
    }
    public static String label(boolean learned, BigInteger balance, EmcAmount price) {
        if (!learned || price == null || price.isZero()) return "";
        BigInteger units = balance.max(BigInteger.ZERO).multiply(price.denominator()).divide(price.numerator());
        return label(true, units, BigInteger.ONE);
    }
}
