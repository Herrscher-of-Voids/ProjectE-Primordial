/**
 * 【文件职责】读取所选机器面的一页端口供 GUI 选择，输出图标、数量、容量及绑定身份。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增分页快照。
 * - 新增：每页 27 个端口、页码边界及身份长度检查。
 * - 修改：不支持精确绑定的多罐端口显示内容但 key 为空；查询仅模拟/读取，不转移资源。
 * - 用途：C2SMachineTargetPacket 调用；大量槽位只读取当前页，避免一次同步整个外部仓库。
 */
/**
 * 【变更记录 #3】2026-09-14 13:25:00（第 3 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：Mekanism 气体 handler 端口快照，沿用 Port 身份/分页协议并以 GAS 图标提示。
 * - 修改：流体为空且代理提供气体时优先枚举化学品罐，不把 GasStack 伪装成 FluidStack。
 * - 用途：配置器可从气体页选择反物质等化学品罐，实际读写由 MachineTargetAccess.gases 完成。
 */
/**
 * 【变更记录 #14】2026-09-16（独立资源端口）
 * - 删除：无；保留旧物品、流体、气体和 FE 端口字段及网络顺序。
 * - 新增：资源端口单位字段，允许配置器显示魔力、气压、灵气、魔源、转速和应力端口。
 * - 修改：资源端口沿用同一页大小、方向和稳定 key；没有伪造 ItemStack 图标，界面显示单位缩写。
 * - 用途：MachineTargetPicker 和 C2SMachineTargetPacket 可以绑定独立资源而不影响旧槽位。
 */
/**
 * 【变更记录 #2】2026-09-14 12:10:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：允许能量页读取 null side，并将其显示为单个内部 FE 端口。
 * - 修改：物品/流体页仍由外部方向请求，避免把内部 capability 当成普通槽位。
 * - 用途：为能量塔提供可绑定的“内部/无方向”端口快照。
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
/**
 * 【变更记录 #9】2026-09-15 12:04:51（第 9 次备注 · 1.0.0-configurator-fixes）
 * - 删除：仅 MekanismProxy 机器才能列出气体的限制，以及流体页隐式回退气体的路径。
 * - 新增：气体页读取任何机器公开的 Mekanism GAS_HANDLER。
 * - 修改：通过同一气体适配器生成绑定 key，严格按 gasMode 区分介质。
 * - 用途：黑洞和第三方气体储罐均能选择精确端口；分页仍最多读取 27 个槽。
 */
package com.primordial.machine;

import com.primordial.data.MachineTarget;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import com.primordial.compat.MekanismGasCompat;
import net.minecraftforge.items.IItemHandler;
import java.util.ArrayList;
import java.util.List;
import com.primordial.resource.EmcResourcePort;
import com.primordial.resource.ResourcePortRegistry;
import com.primordial.resource.ResourceExchangeConversions;

/** 服务端分页快照；每页最多 27 个端口，不把无限仓库或所有方向一次性发给客户端。 */
public record MachinePortPage(String proxyId, int page, int total, List<Port> ports) {
    public static final int PAGE_SIZE = 27;
    public record Port(int index, String key, ItemStack item, FluidStack fluid, int amount, int capacity,
                       boolean gas, String resourceUnit) {
        public Port(int index, String key, ItemStack item, FluidStack fluid, int amount, int capacity, boolean gas) {
            this(index, key, item, fluid, amount, capacity, gas, "");
        }
        public Port(int index, String key, ItemStack item, FluidStack fluid, int amount, int capacity) {
            this(index, key, item, fluid, amount, capacity, false, "");
        }
        public boolean bindable() { return !key.isEmpty(); }
        public boolean resource() { return resourceUnit != null && !resourceUnit.isEmpty(); }
    }

    public static MachinePortPage read(BlockEntity machine, int section, Direction side, int page) {
        return read(machine, section, side, page, false);
    }

    /** Mekanism 端口页同时枚举普通流体和 Gas handler，并为每个方向生成稳定的可绑定槽位 key。 */
    public static MachinePortPage read(BlockEntity machine, int section, Direction side, int page, boolean gasMode) {
        return read(machine, section, side, page, gasMode, null);
    }

    /** 资源页可传入当前样本单位，避免把同一机器上的其它资源端口显示到错误列表。 */
    public static MachinePortPage read(BlockEntity machine, int section, Direction side, int page,
            boolean gasMode, net.minecraft.resources.ResourceLocation resourceUnit) {
        MachineProxy proxy = MachineProxyRegistry.resolve(machine);
        IItemHandler items = section < 2 ? proxy.itemHandler(machine, side) : null;
        IFluidHandler fluids = section >= 2 && section < 4 && !gasMode ? proxy.fluidHandler(machine, side) : null;
        IEnergyStorage energy = section >= 4 && section < 6 ? proxy.energyHandler(machine, side) : null;
        Object gases = section >= 2 && section < 4 && gasMode
                ? MekanismGasCompat.handler(machine, side) : null;
        List<EmcResourcePort> resources = section >= 6 ? ResourcePortRegistry.find(machine, side).stream()
                .filter(port -> resourceUnit == null || resourceUnit.equals(port.unit())).toList() : List.of();
        int total = items != null ? items.getSlots() : fluids != null ? fluids.getTanks()
                : gases != null ? MekanismGasCompat.tankCount(gases) : energy != null ? 1 : resources.size();
        int selectedPage = Math.max(0, Math.min(page, Math.max(0, total - 1) / PAGE_SIZE));
        int start = selectedPage * PAGE_SIZE;
        int end = (int) Math.min((long) total, (long) start + PAGE_SIZE);
        List<Port> ports = new ArrayList<>();
        for (int index = start; index < end; index++) {
            if (items != null) {
                ItemStack stack = items.getStackInSlot(index);
                ItemStack icon = stack.copy();
                if (!icon.isEmpty()) icon.setCount(1);
                ports.add(new Port(index, limited(proxy.itemSlotKey(machine, side, items, index)),
                        icon, FluidStack.EMPTY, stack.getCount(), items.getSlotLimit(index)));
            } else if (fluids != null) {
                // key 为空的多罐端口只展示内容，不向玩家提供会误写其它罐的绑定按钮。
                String key = proxy.targetFluidHandler(machine, side, fluids, index) == null ? ""
                        : limited(proxy.fluidTankKey(machine, side, fluids, index));
                ports.add(new Port(index, key, ItemStack.EMPTY, fluids.getFluidInTank(index).copy(),
                        fluids.getFluidInTank(index).getAmount(), fluids.getTankCapacity(index)));
            } else if (gases != null) {
                Object gas = MekanismGasCompat.tank(gases, index);
                String key = limited(MekanismGasCompat.tankKey(machine, side, gases, index));
                ports.add(new Port(index, key, ItemStack.EMPTY, FluidStack.EMPTY,
                        (int) Math.min(Integer.MAX_VALUE, Math.max(0L, MekanismGasCompat.gasAmount(gas))),
                        (int) Math.min(Integer.MAX_VALUE, Math.max(0L, MekanismGasCompat.tankCapacity(gases, index))), true));
            } else if (!resources.isEmpty()) {
                EmcResourcePort resource = resources.get(index);
                ports.add(new Port(index, limited(resource.key()), ItemStack.EMPTY, FluidStack.EMPTY,
                        (int) Math.min(Integer.MAX_VALUE, Math.max(0L, resource.amount())),
                        (int) Math.min(Integer.MAX_VALUE, Math.max(0L, resource.capacity())), false,
                        resource.unit().toString()));
            } else {
                ports.add(new Port(0, limited(MachineTargetAccess.energyKey(proxy, machine, side, energy)),
                        ItemStack.EMPTY, FluidStack.EMPTY, energy.getEnergyStored(), energy.getMaxEnergyStored()));
            }
        }
        return new MachinePortPage(proxy.id(), selectedPage, total, List.copyOf(ports));
    }

    private static String limited(String key) {
        return key.length() <= MachineTarget.MAX_KEY_LENGTH ? key : "";
    }
}
