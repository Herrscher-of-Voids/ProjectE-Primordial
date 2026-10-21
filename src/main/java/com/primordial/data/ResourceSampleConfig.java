/**
 * 【文件职责】保存一个独立资源（魔力、气压、灵气、魔源、转速或应力）的输入/输出规则。
 *
 * 变更记录 #1（2026-09-16）：新增资源样本配置。规则使用与物品、流体相同的补充/固定
 * 模式、数量、tick 间隔和多目标端口绑定；资源单位写入 NBT，旧机器配置没有该字段时
 * 自动保持未启用，不影响已有存档。
 */
package com.primordial.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import com.primordial.resource.ResourceExchangeConversions;

/** 独立资源的一条服务端权威规则。 */
public final class ResourceSampleConfig {
    public ResourceLocation unit;
    public MachineTarget target = MachineTarget.UNBOUND;
    public final List<MachineTarget> targets = new ArrayList<>();
    public SampleMode mode = SampleMode.FIXED_RATE;
    /** 固定模式每次传输的资源数量；补充模式忽略此值并按容量缺口计算。 */
    public int amount = 1000;
    /** 固定模式两次执行之间的 tick 间隔；补充模式每 tick 检查。 */
    public int intervalTicks = 20;
    public boolean enabled;

    public ResourceSampleConfig(ResourceLocation unit) { this.unit = unit; }

    public static ResourceSampleConfig create(int index) {
        List<ResourceLocation> units = ResourceExchangeConversions.supportedUnits();
        return new ResourceSampleConfig(units.get(Math.max(0, Math.min(index, units.size() - 1))));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (unit != null) tag.putString("unit", unit.toString());
        tag.putString("m", mode.name());
        tag.putInt("a", ConfigLimits.amount(amount, 1));
        tag.putInt("it", ConfigLimits.intervalTicks(intervalTicks));
        tag.putBoolean("e", enabled);
        tag.put("target", target.save());
        ListTag list = new ListTag();
        for (MachineTarget value : effectiveTargets()) list.add(value.save());
        tag.put("targets", list);
        return tag;
    }

    public static ResourceSampleConfig load(CompoundTag tag, int index) {
        ResourceLocation fallback = ResourceExchangeConversions.supportedUnits()
                .get(Math.max(0, Math.min(index, ResourceExchangeConversions.supportedUnits().size() - 1)));
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("unit"));
        ResourceSampleConfig c = new ResourceSampleConfig(parsed == null ? fallback : parsed);
        c.mode = ItemSampleConfig.parseMode(tag.getString("m"));
        c.amount = ConfigLimits.amount(tag.getInt("a"), 1);
        c.intervalTicks = ConfigLimits.intervalTicks(tag.contains("it") ? tag.getInt("it") : 20);
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
}
