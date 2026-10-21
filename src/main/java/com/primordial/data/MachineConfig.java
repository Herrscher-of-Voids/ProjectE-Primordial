/**
 * 【文件职责】聚合一台机器的所有样本、开关、所有者及能量配置。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无。
 * - 新增：能量端口对象复制。
 * - 修改：load 恢复 EnergyConfig 时同时恢复 supplyPort/recoverPort，防止重载丢失绑定。
 * - 用途：配合样本 NBT 保证存档与客户端全量同步都保留独立目标。
 */
/**
 * 【变更记录 #10】2026-09-16（独立资源绑定）
 * - 删除：无；保留旧物品、流体和 FE 配置的 NBT 字段。
 * - 新增：resourceIn/resourceOut 六种独立资源规则数组，支持多目标端口、模式、数量和 tick 间隔。
 * - 修改：旧存档缺少 ri/ro 时创建默认未启用规则；保存时独立写入，不改变旧字段编号。
 * - 用途：配置器资源页和 TaskExecutor 使用同一份服务端权威绑定，避免客户端只改显示而未执行。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
 */
/**
 * 【变更记录 #8】2026-09-14 16:45:00（第 8 次备注 · 1.0.0-stage5-feedback2-hotfix）
 * - 删除：运行时把旧 rate 字段当作固定模式速率的路径；旧字段仅保留给已有存档读取。
 * - 新增：固定模式使用 amount 数量字段，补充模式根据目标容量或缺口计算，不受固定数量影响。
 * - 修改：EnergyConfig、玩家背包规则、配置器界面和任务执行器统一采用“数量 + tick 间隔”语义，并继续限制非法值。
 * - 用途：避免高容量设备因为旧上限或错误速率解释而产生过量循环、配置丢失或跨槽位写入。
 */
/**
 * 【变更记录 #9】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
package com.primordial.data;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** 单台机器的完整配置 */
public class MachineConfig {
    public UUID owner;
    public final ItemSampleConfig[] itemIn = newArray(9);
    public final ItemSampleConfig[] itemOut = newArray(9);
    public final FluidSampleConfig[] fluidIn = newArrayFluid(9);
    public final FluidSampleConfig[] fluidOut = newArrayFluid(9);
    public final ResourceSampleConfig[] resourceIn = newArrayResource();
    public final ResourceSampleConfig[] resourceOut = newArrayResource();
    public final EnergyConfig energy = new EnergyConfig();

    /** 总开关：各介质的输入/输出必须先打开才工作（默认关，防误刷） */
    public boolean itemInEnabled = false;
    public boolean itemOutEnabled = false;
    public boolean fluidInEnabled = false;
    public boolean fluidOutEnabled = false;

    private static ItemSampleConfig[] newArray(int n) {
        ItemSampleConfig[] a = new ItemSampleConfig[n];
        for (int i = 0; i < n; i++) a[i] = new ItemSampleConfig();
        return a;
    }

    private static FluidSampleConfig[] newArrayFluid(int n) {
        FluidSampleConfig[] a = new FluidSampleConfig[n];
        for (int i = 0; i < n; i++) a[i] = new FluidSampleConfig();
        return a;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (owner != null) tag.putUUID("owner", owner);
        tag.put("ii", saveItems(itemIn));
        tag.put("io", saveItems(itemOut));
        tag.put("fi", saveFluids(fluidIn));
        tag.put("fo", saveFluids(fluidOut));
        tag.put("ri", saveResources(resourceIn));
        tag.put("ro", saveResources(resourceOut));
        tag.put("en", energy.save());
        tag.putBoolean("iie", itemInEnabled);
        tag.putBoolean("ioe", itemOutEnabled);
        tag.putBoolean("fie", fluidInEnabled);
        tag.putBoolean("foe", fluidOutEnabled);
        return tag;
    }

    private static ListTag saveItems(ItemSampleConfig[] arr) {
        ListTag list = new ListTag();
        for (ItemSampleConfig c : arr) list.add(c.save());
        return list;
    }

    private static ListTag saveFluids(FluidSampleConfig[] arr) {
        ListTag list = new ListTag();
        for (FluidSampleConfig c : arr) list.add(c.save());
        return list;
    }

    public static MachineConfig load(CompoundTag tag) {
        MachineConfig c = new MachineConfig();
        if (tag.hasUUID("owner")) c.owner = tag.getUUID("owner");
        loadItems(tag.getList("ii", Tag.TAG_COMPOUND), c.itemIn);
        loadItems(tag.getList("io", Tag.TAG_COMPOUND), c.itemOut);
        loadFluids(tag.getList("fi", Tag.TAG_COMPOUND), c.fluidIn);
        loadFluids(tag.getList("fo", Tag.TAG_COMPOUND), c.fluidOut);
        loadResources(tag.getList("ri", Tag.TAG_COMPOUND), c.resourceIn);
        loadResources(tag.getList("ro", Tag.TAG_COMPOUND), c.resourceOut);
        if (tag.contains("en")) {
            EnergyConfig e = EnergyConfig.load(tag.getCompound("en"));
            EnergyConfig target = c.energy;
            target.supplyPort = e.supplyPort;
            target.recoverPort = e.recoverPort;
            target.supplyEnabled = e.supplyEnabled;
            target.supplyMode = e.supplyMode;
            target.supplyRate = e.supplyRate;
            target.supplyTarget = e.supplyTarget;
            target.supplyAmount = e.supplyAmount;
            target.supplyIntervalTicks = e.supplyIntervalTicks;
            target.recoverEnabled = e.recoverEnabled;
            target.recoverMode = e.recoverMode;
            target.recoverRate = e.recoverRate;
            target.recoverTarget = e.recoverTarget;
            target.recoverAmount = e.recoverAmount;
            target.recoverIntervalTicks = e.recoverIntervalTicks;
        }
        c.itemInEnabled = tag.getBoolean("iie");
        c.itemOutEnabled = tag.getBoolean("ioe");
        c.fluidInEnabled = tag.getBoolean("fie");
        c.fluidOutEnabled = tag.getBoolean("foe");
        return c;
    }

    private static void loadItems(ListTag list, ItemSampleConfig[] arr) {
        for (int i = 0; i < arr.length && i < list.size(); i++) {
            arr[i] = ItemSampleConfig.load(list.getCompound(i));
        }
    }

    private static void loadFluids(ListTag list, FluidSampleConfig[] arr) {
        for (int i = 0; i < arr.length && i < list.size(); i++) {
            arr[i] = FluidSampleConfig.load(list.getCompound(i));
        }
    }

    private static ResourceSampleConfig[] newArrayResource() {
        ResourceSampleConfig[] a = new ResourceSampleConfig[com.primordial.resource.ResourceExchangeConversions.supportedUnits().size()];
        for (int i = 0; i < a.length; i++) a[i] = ResourceSampleConfig.create(i);
        return a;
    }

    private static ListTag saveResources(ResourceSampleConfig[] arr) {
        ListTag list = new ListTag();
        for (ResourceSampleConfig c : arr) list.add(c.save());
        return list;
    }

    private static void loadResources(ListTag list, ResourceSampleConfig[] arr) {
        for (int i = 0; i < arr.length; i++)
            if (i < list.size()) arr[i] = ResourceSampleConfig.load(list.getCompound(i), i);
    }
}
