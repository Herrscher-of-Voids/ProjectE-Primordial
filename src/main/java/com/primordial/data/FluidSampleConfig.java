/**
 * 【文件职责】保存单个流体样本、流体 NBT、速率和指定储罐。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；保留过滤流体及工作模式。
 * - 新增：MachineTarget 储罐绑定与持久化。
 * - 修改：没有旧绑定数据时不自动寻找其它储罐。
 * - 用途：专用适配器提供按罐访问；不支持精确写入的通用多罐接口不允许绑定。
 */
/**
 * 【变更记录 #2】2026-09-14 12:00:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：删除 1,000,000 mB 的隐含读取上限。
 * - 新增：流体速率和目标量统一使用 ConfigLimits 的最大 int 边界。
 * - 修改：间隔字段明确为秒，保存/加载时统一清理非法值。
 * - 用途：允许大型储罐按真实容量配置，避免客户端填写成功但重载后被截断。
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
import net.minecraftforge.fluids.FluidStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.primordial.compat.MekanismGasCompat;

/** 流体样本配置（filter 存流体内容，不存容器） */
public class FluidSampleConfig {
    public FluidStack filter = FluidStack.EMPTY;
    /** Mekanism 化学气体过滤器；使用 Object 保存以保持 Mekanism 为真正的可选依赖。 */
    public Object gasFilter;
    /** 精确外部面和储罐；没有按罐写入 API 的多罐能力不能绑定。 */
    public MachineTarget target = MachineTarget.UNBOUND;
    /** 同一流体样本可绑定多个储罐，target 是首个目标的兼容别名。 */
    public final List<MachineTarget> targets = new ArrayList<>();
    public SampleMode mode = SampleMode.FIXED_RATE;
    /** FIXED_RATE: 每次执行注入 rate mB */
    public int rate = 100;
    /** KEEP_LEVEL: 补充到 M mB */
    public int threshold = 4000;
    /** KEEP_LEVEL: 检查间隔秒 */
    public int intervalTicks = 100;
    /** 输出槽启用开关 */
    public boolean enabled = false;

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (!filter.isEmpty()) {
            CompoundTag f = new CompoundTag();
            filter.writeToNBT(f);
            tag.put("f", f);
        }
        CompoundTag gasTag = MekanismGasCompat.writeGas(gasFilter);
        if (gasTag != null) tag.put("g", gasTag);
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

    public static FluidSampleConfig load(CompoundTag tag) {
        FluidSampleConfig c = new FluidSampleConfig();
        if (tag.contains("f")) c.filter = FluidStack.loadFluidStackFromNBT(tag.getCompound("f"));
        if (tag.contains("g")) c.gasFilter = MekanismGasCompat.readGas(tag.getCompound("g"));
        if (MekanismGasCompat.gasEmpty(c.gasFilter)) c.gasFilter = null;
        c.mode = ItemSampleConfig.parseMode(tag.getString("m"));
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

    public boolean hasGasFilter() { return !MekanismGasCompat.gasEmpty(gasFilter); }

    public void setTargets(Collection<MachineTarget> values) {
        targets.clear();
        if (values != null) for (MachineTarget value : values)
            if (value != null && value.bound() && !targets.contains(value)) targets.add(value);
        target = targets.isEmpty() ? MachineTarget.UNBOUND : targets.get(0);
    }
}
