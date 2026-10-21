/**
 * 【文件职责】保存配置器的供能和回收参数，并负责与机器目标端口一起持久化。
 * TaskExecutor 读取本对象决定从 EMC 生成 FE 或从机器回收 FE；ConfiguratorMenu 和
 * ConfiguratorScreen 通过 MachineConfig 访问它，所有数值在这里统一经过边界清洗。
 *
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；保留原有 FE 参数。
 * - 新增：supplyPort 和 recoverPort 两个独立的机器目标端口及其 NBT 字段。
 * - 修改：供能与回收分别绑定端口，端口失效时各自暂停，不自动改用其他方向。
 * - 用途：TaskExecutor、菜单预览和配置同步使用同一组明确端口。
 *
 * 【变更记录 #2】2026-09-14 12:00:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：供能/回收速率的 1,000,000 固定上限。
 * - 新增：ConfigLimits 对 FE 数量、目标值和间隔统一校验，允许 Integer.MAX_VALUE 数量。
 * - 修改：旧的秒间隔字段读取后换算为 tick，避免 GUI、NBT 和执行器使用不同单位。
 * - 用途：高容量 FE 设备可以使用一致的 int 边界，同时拒绝负数和非法间隔。
 *
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：以秒为单位解释供能和回收间隔的旧路径。
 * - 新增：supplyIntervalTicks 和 recoverIntervalTicks，明确表示固定模式的 tick 间隔。
 * - 修改：旧 NBT 的 si/ri 字段读取时乘以 20 转换为 tick；补充模式每 tick 检查。
 * - 用途：固定模式的时间行为在重载前后保持一致，避免出现二十倍速率偏差。
 *
 * 【变更记录 #8】2026-09-14 16:45:00（第 8 次备注 · 1.0.0-stage5-feedback2-hotfix）
 * - 删除：运行时把 supplyRate/recoverRate 当作实际速率的路径；字段仅为旧存档兼容保留。
 * - 新增：supplyAmount/recoverAmount，表示固定模式每次执行的 FE 数量。
 * - 修改：固定模式使用数量和 tick 间隔；补充模式按机器容量每 tick 计算并忽略固定数量。
 * - 用途：与物品、流体配置统一为“数量 + tick 间隔”，兼容 Mekanism 等高容量接口。
 *
 * 【变更记录 #9】2026-09-15 01:05:00（第 9 次备注 · 编码修复）
 * - 删除：原文件中因错误字符集写入而变成问号的两段历史备注文本。
 * - 新增：根据已有 changelog 和当前代码恢复 #7/#8 的可读记录，并补充字段级职责说明。
 * - 修改：明确旧 rate 字段只用于 NBT 兼容，固定模式使用 amount，间隔统一为 tick。
 * - 用途：保证维护者能从本文件准确理解数据格式和运行时语义；本次只修复注释，不改变业务逻辑。
 */
package com.primordial.data;

import net.minecraft.nbt.CompoundTag;

/** 能量页配置：供能和回收各自拥有端口、模式、数量与 tick 间隔。 */
public class EnergyConfig {
    /** 供能和回收各自绑定外部面，能力缺失时暂停当前方向，不自动换面。 */
    public MachineTarget supplyPort = MachineTarget.UNBOUND;
    public MachineTarget recoverPort = MachineTarget.UNBOUND;

    // ---- 供能（从玩家 EMC 生成 FE 并注入机器） ----
    public boolean supplyEnabled = false;
    public SampleMode supplyMode = SampleMode.FIXED_RATE;
    /** 旧存档兼容字段；运行时不再把它作为固定模式速率，界面也不显示。 */
    @Deprecated
    public int supplyRate = 100;
    /** KEEP_LEVEL：机器需要维持的目标 FE；补充模式由执行器按机器容量计算缺口。 */
    public int supplyTarget = 100000;
    /** FIXED_RATE：每次执行注入的 FE 数量；KEEP_LEVEL 模式忽略此字段。 */
    public int supplyAmount = 100;
    /** FIXED_RATE：两次执行之间的间隔，单位为 tick；KEEP_LEVEL 每 tick 检查一次。 */
    public int supplyIntervalTicks = 20;

    // ---- 回收（从机器提取 FE 并换算为 EMC） ----
    public boolean recoverEnabled = false;
    public SampleMode recoverMode = SampleMode.FIXED_RATE;
    /** 旧存档兼容字段；运行时不再把它作为固定模式速率，界面也不显示。 */
    @Deprecated
    public int recoverRate = 100;
    /** KEEP_LEVEL：机器高于此 FE 时才回收多出的能量。 */
    public int recoverTarget = 0;
    /** FIXED_RATE：每次执行提取的 FE 数量；KEEP_LEVEL 模式按目标值计算。 */
    public int recoverAmount = 100;
    /** FIXED_RATE：两次执行之间的间隔，单位为 tick；KEEP_LEVEL 每 tick 检查一次。 */
    public int recoverIntervalTicks = 20;

    /** 将端口、模式、数量和 tick 间隔写入 NBT；旧 rate 字段继续写入以兼容已有存档。 */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("supplyPort", supplyPort.save());
        tag.put("recoverPort", recoverPort.save());
        tag.putBoolean("se", supplyEnabled);
        tag.putString("sm", supplyMode.name());
        tag.putInt("sr", supplyRate);
        tag.putInt("st", supplyTarget);
        tag.putInt("sa", supplyAmount);
        tag.putInt("sit", supplyIntervalTicks);
        tag.putBoolean("re", recoverEnabled);
        tag.putString("rm", recoverMode.name());
        tag.putInt("rr", recoverRate);
        tag.putInt("rt", recoverTarget);
        tag.putInt("ra", recoverAmount);
        tag.putInt("rit", recoverIntervalTicks);
        return tag;
    }

    /** 从 NBT 恢复配置；旧 si/ri 秒字段转换为 tick，非法数值交给 ConfigLimits 截断。 */
    public static EnergyConfig load(CompoundTag tag) {
        EnergyConfig c = new EnergyConfig();
        c.supplyPort = MachineTarget.load(tag.getCompound("supplyPort"));
        c.recoverPort = MachineTarget.load(tag.getCompound("recoverPort"));
        c.supplyEnabled = tag.getBoolean("se");
        c.supplyMode = ItemSampleConfig.parseMode(tag.getString("sm"));
        c.supplyRate = ConfigLimits.amount(tag.getInt("sr"), 1);
        c.supplyTarget = ConfigLimits.amount(tag.getInt("st"), 0);
        c.supplyAmount = ConfigLimits.amount(tag.contains("sa") ? tag.getInt("sa") : tag.getInt("sr"), 1);
        c.supplyIntervalTicks = ConfigLimits.intervalTicks(tag.contains("sit")
                ? tag.getInt("sit") : Math.max(1, tag.getInt("si") * 20));
        c.recoverEnabled = tag.getBoolean("re");
        c.recoverMode = ItemSampleConfig.parseMode(tag.getString("rm"));
        c.recoverRate = ConfigLimits.amount(tag.getInt("rr"), 1);
        c.recoverTarget = ConfigLimits.amount(tag.getInt("rt"), 0);
        c.recoverAmount = ConfigLimits.amount(tag.contains("ra") ? tag.getInt("ra") : tag.getInt("rr"), 1);
        c.recoverIntervalTicks = ConfigLimits.intervalTicks(tag.contains("rit")
                ? tag.getInt("rit") : Math.max(1, tag.getInt("ri") * 20));
        return c;
    }
}
