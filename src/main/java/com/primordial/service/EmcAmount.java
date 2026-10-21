/**
 * 【文件职责】精确表示资源换算中的非负 EMC 分数；玩家的整数余额仍由 ProjectE 保存。
 * 【变更记录 #1】2026-09-15 14:06:10（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：约分的 BigInteger 分子/分母、整数上下取整、数量乘法和可负担数量计算。
 * - 修改：换算过程不经过 double，不在每 mB/FE 处提前取整。
 * - 用途：供流体、能源和结算找零共用；零分母、负金额和负结果明确拒绝。
 */
/**
 * 【变更记录 #2】2026-09-15 14:48:20（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：同分母计算仍扩大分母的路径。
 * - 新增：有界文本解析、分数除法及精确字符串，供流体价格和能源配置共用。
 * - 修改：加减乘优先约分，零金额快速返回；拒绝负数量和超长输入。
 * - 用途：保护高频结算精度及内存，不使用浮点数或提前整数取整。
 */
/**
 * 【变更记录 #3】2026-09-15 15:40:11（第 3 次备注 · 1.0.0-emc-service）
 * - 删除：十进制与分数字符串边界不一致的解析条件。
 * - 新增：约分结果的统一分子/分母位数校验。
 * - 修改：允许 128 位小数对应的 129 位分母；分子最多 4096 位。
 * - 用途：所有接受的价格均能以精确分数写出再读入，避免极小流体价格重载后被禁用。
 */
package com.primordial.service;

import java.math.BigInteger;

public record EmcAmount(BigInteger numerator, BigInteger denominator) {
    public static final EmcAmount ZERO = new EmcAmount(BigInteger.ZERO, BigInteger.ONE);
    public static final EmcAmount ONE = new EmcAmount(BigInteger.ONE, BigInteger.ONE);

    public EmcAmount {
        if (numerator.signum() < 0 || denominator.signum() <= 0) throw new IllegalArgumentException("Invalid EMC fraction");
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd);
        denominator = denominator.divide(gcd);
    }

    public static EmcAmount of(BigInteger integer) { return new EmcAmount(integer, BigInteger.ONE); }
    public boolean isZero() { return numerator.signum() == 0; }
    public BigInteger floor() { return numerator.divide(denominator); }
    public BigInteger ceil() { return numerator.add(denominator).subtract(BigInteger.ONE).divide(denominator); }
    public EmcAmount fraction() { return new EmcAmount(numerator.remainder(denominator), denominator); }

    public EmcAmount add(EmcAmount other) {
        if (other.isZero()) return this;
        if (isZero()) return other;
        BigInteger common = denominator.gcd(other.denominator);
        BigInteger leftScale = other.denominator.divide(common);
        BigInteger rightScale = denominator.divide(common);
        return new EmcAmount(numerator.multiply(leftScale).add(other.numerator.multiply(rightScale)), denominator.multiply(leftScale));
    }

    public EmcAmount subtract(EmcAmount other) {
        if (other.isZero()) return this;
        BigInteger common = denominator.gcd(other.denominator);
        BigInteger leftScale = other.denominator.divide(common);
        BigInteger rightScale = denominator.divide(common);
        return new EmcAmount(numerator.multiply(leftScale).subtract(other.numerator.multiply(rightScale)),
                denominator.multiply(leftScale));
    }

    public EmcAmount multiply(long units) {
        if (units < 0) throw new IllegalArgumentException("Negative resource count");
        if (units == 0 || isZero()) return ZERO;
        return new EmcAmount(numerator.multiply(BigInteger.valueOf(units)), denominator);
    }

    public EmcAmount multiply(EmcAmount other) {
        BigInteger a = numerator.gcd(other.denominator), b = other.numerator.gcd(denominator);
        return new EmcAmount(numerator.divide(a).multiply(other.numerator.divide(b)),
                denominator.divide(b).multiply(other.denominator.divide(a)));
    }

    public EmcAmount divide(long units) {
        if (units <= 0) throw new IllegalArgumentException("Non-positive divisor");
        return new EmcAmount(numerator, denominator.multiply(BigInteger.valueOf(units)));
    }

    /** 接受整数、普通十进制或分子/分母；限制指数与小数位，避免恶意输入分配巨大幂。 */
    public static EmcAmount parse(String text) {
        if (text == null || text.length() > 4354) throw new IllegalArgumentException("EMC text too long");
        String[] parts = text.split("/", -1);
        if (parts.length == 2 && parts[0].matches("[0-9]{1,4096}") && parts[1].matches("[0-9]{1,129}"))
            return new EmcAmount(new BigInteger(parts[0]), new BigInteger(parts[1]));
        if (parts.length != 1) throw new IllegalArgumentException("Invalid EMC fraction");
        java.math.BigDecimal decimal = new java.math.BigDecimal(text);
        if (decimal.signum() < 0 || decimal.scale() > 128 || decimal.scale() < -4096
                || (long) decimal.precision() - decimal.scale() > 4096)
            throw new IllegalArgumentException("EMC out of bounds");
        EmcAmount amount = decimal.scale() >= 0 ? new EmcAmount(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale()))
                : of(decimal.toBigIntegerExact());
        if (amount.numerator.toString().length() > 4096 || amount.denominator.toString().length() > 129)
            throw new IllegalArgumentException("EMC fraction out of bounds");
        return amount;
    }

    @Override public String toString() {
        return denominator.equals(BigInteger.ONE) ? numerator.toString() : numerator + "/" + denominator;
    }

    /** 按总可用价值计算单位数，在转为 long 之前裁剪，超大余额不会溢出。 */
    public long affordableUnits(EmcAmount unitPrice, long limit) {
        if (limit <= 0 || unitPrice.isZero()) return 0;
        return numerator.multiply(unitPrice.denominator).divide(denominator.multiply(unitPrice.numerator))
                .min(BigInteger.valueOf(limit)).longValue();
    }
}
