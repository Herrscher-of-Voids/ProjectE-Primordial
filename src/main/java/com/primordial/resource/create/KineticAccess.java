/**
 * 【文件职责】动力方块的 Mixin 状态入口，接口本身不链接任何可选模组类。
 * 【变更记录 #1】2026-09-16 19:59:10（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：每方块唯一的临时付款状态访问方法。
 * - 修改：适配器和 Mixin 共用状态，不使用持有世界的静态缓存。
 * - 用途：服务端供给和原生 tick 的桥梁，方块销毁或世界释放后可一同回收。
 */
/**
 * 【变更记录 #2】2026-09-16 20:30:43（第 2 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：不创建付款状态的 leaseIfPresent 入口。
 * - 修改：网络只查询存在的付款状态，端口实际使用时才分配。
 * - 用途：普通 Create 机器无需附带付款对象，降低大规模整合包内存占用。
 */
package com.primordial.resource.create;

public interface KineticAccess {
    KineticLease primordial$lease();
    /** 只读探测不分配状态；未配置动力的普通机器保持零附加对象。 */
    KineticLease primordial$leaseIfPresent();
}
