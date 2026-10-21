/**
 * 【文件职责】保存单个物品样本、速率、启用状态和明确目标。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；保留原样本字段。
 * - 新增：target 字段和对应 NBT 保存/加载。
 * - 修改：每条物品样本独立绑定外部面及槽位，缺少端口配置时暂停。
 * - 用途：由配置器写入，经 S2CFullConfigSyncPacket 同步，TaskExecutor 只操作绑定槽。
 */
/**
 * 【变更记录 #2】2026-09-14 12:00:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：删除速率与补充目标的 1,000,000 硬编码上限。
 * - 新增：读取 NBT 时使用 ConfigLimits，允许补充目标和固定速率达到 Integer.MAX_VALUE。
 * - 修改：间隔仍按秒保存并限制在 1..3600 秒，和调度器的 tick 换算保持一致。
 * - 用途：为高容量机器提供完整的 int 范围，同时防止负数和异常间隔进入任务线程。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
package com.primordial.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 物品样本配置（输入或输出槽，共 4 组 × 9） */
public class ItemSampleConfig {
    public ItemStack filter = ItemStack.EMPTY;
    /** 明确的外部面和物品槽；未绑定的样本不会执行。 */
    public MachineTarget target = MachineTarget.UNBOUND;
    /** 同一过滤样本可绑定多个端口；target 保留为旧存档和旧调用方的首端口别名。 */
    public final List<MachineTarget> targets = new ArrayList<>();
    public SampleMode mode = SampleMode.FIXED_RATE;
    /** FIXED_RATE: 每秒生成 N 个 */
    public int rate = 1;
    /** KEEP_LEVEL: 补充到 M 个 */
    public int threshold = 16;
    /** KEEP_LEVEL: 检查间隔秒 */
    public int intervalTicks = 100;
    /** 输出槽启用开关（输入槽非空即工作） */
    public boolean enabled = false;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!filter.isEmpty()) tag.put("f", filter.save(new CompoundTag()));
        tag.putString("m", mode.name());
        tag.putInt("r", rate);
        tag.putInt("t", threshold);
        tag.putInt("it", intervalTicks);
        tag.putBoolean("e", enabled);
        tag.put("target", target.save());
        ListTag targetList = new ListTag();
        for (MachineTarget value : effectiveTargets()) targetList.add(value.save());
        tag.put("targets", targetList);
        return tag;
    }

    public static ItemSampleConfig load(CompoundTag tag) {
        ItemSampleConfig c = new ItemSampleConfig();
        if (tag.contains("f")) c.filter = ItemStack.of(tag.getCompound("f"));
        c.mode = parseMode(tag.getString("m"));
        c.rate = ConfigLimits.amount(tag.getInt("r"), 1);
        c.threshold = ConfigLimits.amount(tag.getInt("t"), 1);
        c.intervalTicks = ConfigLimits.intervalTicks(tag.contains("it") ? tag.getInt("it") : Math.max(1, tag.getInt("i") * 20));
        c.enabled = tag.getBoolean("e");
        c.target = MachineTarget.load(tag.getCompound("target"));
        if (tag.contains("targets", Tag.TAG_LIST)) {
            ListTag list = tag.getList("targets", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                MachineTarget value = MachineTarget.load(list.getCompound(i));
                if (value.bound() && !c.targets.contains(value)) c.targets.add(value);
            }
        }
        if (c.targets.isEmpty() && c.target.bound()) c.targets.add(c.target);
        if (!c.targets.isEmpty()) c.target = c.targets.get(0);
        return c;
    }

    public List<MachineTarget> effectiveTargets() {
        return targets.isEmpty() ? (target.bound() ? List.of(target) : List.of()) : List.copyOf(targets);
    }

    public void setTargets(Collection<MachineTarget> values) {
        targets.clear();
        if (values != null) for (MachineTarget value : values)
            if (value != null && value.bound() && !targets.contains(value)) targets.add(value);
        target = targets.isEmpty() ? MachineTarget.UNBOUND : targets.get(0);
    }

    static SampleMode parseMode(String s) {
        try {
            return SampleMode.valueOf(s);
        } catch (IllegalArgumentException e) {
            return SampleMode.FIXED_RATE;
        }
    }
}
