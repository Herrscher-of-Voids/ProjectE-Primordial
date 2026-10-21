/**
 * 【文件职责】描述介质价格的交易方向状态，并提供配置文件解析。
 * 【变更记录 #1】2026-10-08：新增继承原生、定制、禁用、仅买和仅卖五种持久化状态。
 */
package com.primordial.value;

public enum PriceMode {
    INHERIT_NATIVE, CUSTOM_VALUE, DISABLED, SELL_ONLY, BUY_ONLY;
    public boolean allowsBuy() { return this == INHERIT_NATIVE || this == CUSTOM_VALUE || this == BUY_ONLY; }
    public boolean allowsSell() { return this == INHERIT_NATIVE || this == CUSTOM_VALUE || this == SELL_ONLY; }
    public static PriceMode parse(String text) {
        if (text == null) throw new IllegalArgumentException("Missing price mode");
        return switch (text.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "inherit", "inherit_native", "native" -> INHERIT_NATIVE;
            case "custom", "custom_value", "value" -> CUSTOM_VALUE;
            case "disabled", "disable", "off" -> DISABLED;
            case "sell_only", "sell-only", "sell" -> SELL_ONLY;
            case "buy_only", "buy-only", "buy" -> BUY_ONLY;
            default -> throw new IllegalArgumentException("Unknown price mode");
        };
    }
}
