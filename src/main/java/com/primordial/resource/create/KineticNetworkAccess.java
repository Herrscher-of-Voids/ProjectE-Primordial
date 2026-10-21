/**
 * 【文件职责】读取动力网络本次计算中来自 EMC 的临时应力容量。
 * 【变更记录 #1】2026-09-16 19:59:10（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：不含 Create 类型的已计入容量读取方法。
 * - 修改：保存网络时扣除临时容量，客户端同步仍使用实际总容量。
 * - 用途：防止卸载/重启后把已到期的 EMC 应力误当作永久原生发电容量。
 */
package com.primordial.resource.create;

public interface KineticNetworkAccess {
    float primordial$paidCapacity();
}
