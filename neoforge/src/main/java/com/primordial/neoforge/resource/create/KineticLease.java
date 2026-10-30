/**
 * 【文件职责】保存一台动力机器已付费的单 tick 转速/应力额度，不包含 Create 类型或世界引用。
 * 【变更记录 #1】2026-09-16 19:59:10（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：按游戏时间隔离的预约额度、模拟接收和实际生效值。
 * - 修改：动力不是储罐；本 tick 付款只授权下一 tick，多方向共用同一额度上限。
 * - 用途：适配器经 EmcService 付款后预约；机器 tick 消费，暂停、欠费、卸载不会留下永久动力。
 */
package com.primordial.neoforge.resource.create;

/** 只在服务端线程读写；不序列化，时间加速器重复调用同 tick 也不会延长授权。 */
public final class KineticLease {
    private long paidTick = Long.MIN_VALUE;
    private long speed;
    private long stress;
    private long appliedTick = Long.MIN_VALUE;
    public float activeSpeed;
    public long activeStress;

    public long reserved(long tick, boolean stressPort) {
        return paidTick == tick ? (stressPort ? stress : speed) : 0;
    }

    /** 模拟只报告接收量；不同端口实例、不同方向都累计到相同的每 tick 额度。 */
    public long receive(long tick, boolean stressPort, long requested, long capacity, boolean simulate) {
        long accepted = Math.min(Math.max(0, requested), Math.max(0, capacity - reserved(tick, stressPort)));
        if (simulate || accepted == 0) return accepted;
        if (paidTick != tick) {
            speed = 0;
            stress = 0;
            paidTick = tick;
        }
        if (stressPort) stress += accepted;
        else speed += accepted;
        return accepted;
    }

    public long due(long tick, boolean stressPort) {
        return paidTick != Long.MIN_VALUE && paidTick == tick - 1 ? (stressPort ? stress : speed) : 0;
    }

    /** 同一游戏 tick 至多更新一次动力拓扑；额外机器 tick 复用已经购买的当 tick 供给。 */
    public boolean beginTick(long tick) {
        if (appliedTick == tick) return false;
        appliedTick = tick;
        return true;
    }
}
