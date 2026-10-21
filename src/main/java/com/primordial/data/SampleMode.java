/**
 * 【变更记录 #1】2026-09-16（资源端口规则说明）
 * - 修改：补充模式明确为每 tick 检查，并忽略固定数量和间隔；固定模式统一使用 intervalTicks。
 * - 用途：物品、流体、能量及独立资源的界面和任务执行器使用同一模式语义。
 */
package com.primordial.data;

/** 统一的资源传输模式；补充模式始终每 tick 检查，固定模式由规则的 intervalTicks 控制。 */
public enum SampleMode {
    /** 固定模式：到达 intervalTicks 时传输配置的固定数量。 */
    FIXED_RATE,
    /** 补充模式：每 tick 按目标容量缺口或当前存量尽量补足/抽空，不使用固定数量和间隔。 */
    KEEP_LEVEL;

    public static final SampleMode[] VALUES = values();

    public SampleMode next() {
        return this == FIXED_RATE ? KEEP_LEVEL : FIXED_RATE;
    }
}
