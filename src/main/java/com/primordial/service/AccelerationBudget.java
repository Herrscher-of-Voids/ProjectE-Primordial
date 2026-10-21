/**
 * 【文件职责】限制一个服务端 tick 的额外工作次数、目标数和主线程耗时，不持有世界对象。
 * 【变更记录 #1】2026-09-16 13:29:00（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无；新增可独立验证的预算器。
 * - 新增：全服预算、目标去重、调用前存活检查、异常及单次慢调用反馈。
 * - 修改：耗时或次数用完即停止；已经进入的第三方调用无法安全抢占，失败调用仍计入付费工作量。
 * - 用途：EmcAccelerationService 用结果退款、暂停故障机器，测试可注入时钟而无需等待真实时间。
 */
/**
 * 【变更记录 #2】2026-09-18 01:33:29（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：有界范围扫描额度，与所有目标共用当前 tick 的耗时截止。
 * - 修改：同一个预算对象用于单点、范围、实体及全局时钟的去重与额外调用。
 * - 用途：大量空范围也不能在主线程上无界扫描；测试继续注入时钟验证边界。
 */
package com.primordial.service;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

final class AccelerationBudget {
    private final LongSupplier clock;
    private final long start;
    private final long nanos;
    private final int targetLimit;
    private final Set<Object> targets = new HashSet<>();
    private int ticksLeft;
    private int scansLeft = com.primordial.config.PrimordialServerConfig.accelerationMaxScans();

    AccelerationBudget(int targetLimit, int tickLimit, long nanos, LongSupplier clock) {
        this.targetLimit = Math.max(0, targetLimit);
        this.ticksLeft = Math.max(0, tickLimit);
        this.nanos = Math.max(0, nanos);
        this.clock = clock;
        this.start = clock.getAsLong();
    }

    boolean available() { return ticksLeft > 0 && clock.getAsLong() - start < nanos; }
    boolean acceptsTarget() { return targets.size() < targetLimit && available(); }
    boolean claim(Object target) { return acceptsTarget() && targets.add(target); }
    boolean scan() {
        if (scansLeft <= 0 || !available()) return false;
        scansLeft--;
        return true;
    }

    /** 每次调用前检查世界状态和预算；异常调用可能已改写机器，因此也计入 attempted。 */
    Result run(int requested, BooleanSupplier valid, Runnable tick) {
        int attempted = 0;
        for (int i = 0; i < requested && available(); i++) {
            long before = clock.getAsLong();
            try {
                if (!valid.getAsBoolean()) return new Result(attempted, true, false, null);
                if (!available()) break;
                ticksLeft--;
                attempted++;
                tick.run();
            } catch (RuntimeException | LinkageError failure) {
                return new Result(attempted, false, false, failure);
            }
            if (clock.getAsLong() - before >= nanos) return new Result(attempted, false, true, null);
        }
        return new Result(attempted, false, false, null);
    }

    record Result(int attempted, boolean invalid, boolean slow, Throwable failure) { }
}
