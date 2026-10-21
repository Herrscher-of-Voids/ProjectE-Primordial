/**
 * 【文件职责】表示资源交易结果及按实际数量计算的精确金额。
 * 【变更记录 #1】2026-10-10 03:53:52（第 1 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：成交类别、注册名、数量、金额和有界失败状态，供服务端交易与客户端反馈共用。
 * - 修改：复用统一 EMC 结算及服务端主线程，不接受客户端价格或实际数量。
 * - 用途：完整提供资源系统的真实交易与失败保护；可选 API 保持在兼容层。
 */
package com.primordial.service;

public record ResourceTradeResult(Status status, String unit, long units, String emc) {
    public enum Status {
        SUCCESS, EMPTY, INVALID_RESOURCE, DISABLED, UNKNOWN, UNPRICED,
        NO_CONTAINER, FULL, NO_FUNDS, INVENTORY_FULL, NO_TRANSFER, ERROR
    }
    public boolean traded() { return status == Status.SUCCESS && units > 0; }
    public static ResourceTradeResult failure(Status status) { return new ResourceTradeResult(status, "", 0, "0"); }
    public static ResourceTradeResult success(String unit, long units, EmcAmount price) {
        return new ResourceTradeResult(Status.SUCCESS, unit, units, price.multiply(units).toString());
    }
}

