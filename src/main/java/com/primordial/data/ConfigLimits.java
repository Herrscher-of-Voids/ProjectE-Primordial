/**
 * 【文件职责】集中定义配置器可接受的数量、速率和间隔边界，供数据模型、网络包和界面共同使用。
 * 【变更记录 #1】2026-09-14 12:00:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：以 Integer.MAX_VALUE 作为物品、流体和 FE 数量/速率的统一上限，并保留 1 小时检查间隔上限。
 * - 修改：避免网络层、NBT 层和 GUI 层分别使用不同的 1,000,000 限制。
 * - 用途：所有配置入口调用同一组边界，确保补充模式可以填写机器允许的最高整数值。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【编码修复说明】ConfigLimits 的原备注因错误字符集转换被写成问号，已根据当前实现恢复。
 * - 删除：不可读的问号文本。
 * - 新增：职责、边界条件和失败处理说明。
 * - 修改：只修复注释内容，不改变字段、协议、算法或运行时行为。
 * - 用途：帮助维护者理解本段代码，并确保后续修改可追溯。
 */
/**
 * 【变更记录 #8】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
package com.primordial.data;

/** 配置器数值边界；使用 int 是因为 Forge 物品/流体/FE API 的数量参数本身为 int。 */
public final class ConfigLimits {
    public static final int MAX_AMOUNT = Integer.MAX_VALUE;
    public static final int MAX_INTERVAL_TICKS = 72000;
    /** @deprecated 旧 API 仍以秒为参数，仅用于读取旧存档；新代码必须调用 intervalTicks。 */
    @Deprecated public static final int MAX_INTERVAL_SECONDS = MAX_INTERVAL_TICKS / 20;

    private ConfigLimits() {}

    public static int amount(int value, int minimum) {
        return Math.max(minimum, Math.min(MAX_AMOUNT, value));
    }

    public static int intervalTicks(int value) {
        return Math.max(1, Math.min(MAX_INTERVAL_TICKS, value));
    }

    /** @deprecated 旧秒间隔入口只做 20 倍转换并交给 intervalTicks，新的配置和调度逻辑不再调用它。 */
    @Deprecated public static int interval(int seconds) {
        long ticks = Math.max(1L, seconds) * 20L;
        return intervalTicks((int) Math.min(Integer.MAX_VALUE, ticks));
    }
}
