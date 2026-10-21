/**
 * 变更记录 #1（2026-09-14）：新增 Mekanism 主模组代理。
 * - 新增：Mekanism 方块实体按方向的 Forge 物品、流体和能量能力读取。
 * - 用途：为 Mekanism 及其附属机器提供稳定的专用代理标识，后续可扩展 Mekanism 专属槽位策略。
 */
/**
 * 【变更记录 #4】2026-09-15 16:40:00（第 4 次备注 · 1.0.0-stage5-adapters）
 * - 删除：Mekanism 原生库存/储罐实现退回聚合 Forge 包装的路径。
 * - 新增：按外部方向直接调用原生物品、流体和严格能源接口；严格能源转换为 Forge Energy 视图。
 * - 修改：目标流体视图固定原生罐索引，模拟与执行共用同一 Action；气体 key 继续保留罐身份。
 * - 用途：避免多罐机器被聚合能力改写到其它罐，并支持只暴露 STRICT_ENERGY 的 Mekanism 机器。
 */
/**
 * 【变更记录 #3】2026-09-14 14:00:00（第 3 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：GAS_HANDLER、气体罐身份和 gas-only 能力检测。
 * - 修改：Mekanism 气体沿原生 IGasHandler 处理，不再尝试转换成 FluidStack；外部面权限仍由 capability 决定。
 * - 用途：配置器/JEI 可配置反物质等气体，TaskExecutor 使用同一罐索引进行输入输出和 EMC 结算。
 */
/**
 * 【文件职责】适配 Mekanism 原生槽位身份、按罐流体 API 和外部 Forge 能力。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：删除猜测附属命名空间以及无方向优先策略。
 * - 新增：IMekanismInventory/IMekanismFluidHandler API 匹配、全局槽身份映射、IExtendedFluidHandler 指定罐视图。
 * - 修改：null 面只读取槽对象身份；实际读写通过选定面代理，并把 insertFluid 的剩余量换算为已插入量。
 * - 用途：核对 1.20.x 源码 96c736241e308a9cf1c73f7dcfa9aa221ea559d3，编译/测试版本 10.4.16.80；J/FE 由 Mekanism 的 ForgeEnergyIntegration 按自身配置换算，化学品不作为 Forge 流体伪装。
 */
/**
 * 【变更记录 #5】2026-09-16 00:52:00（第 5 次备注 · 1.0.0-mekanism-capability-fallback）
 * - 删除：无；保留 Mekanism 原生物品、流体和气体接口的专用适配路径。
 * - 新增：NativeItemHandler、NativeFluidHandler 创建过程的 LinkageError/运行时异常降级；原生包装类无法由当前运行时类加载器解析时，回退到 Forge capability。
 * - 修改：targetFluidHandler 对 NativeFluidHandler 类型检查增加安全边界，避免类解析失败再次中断流体接口读取。
 * - 用途：部分运行环境在热替换模组 JAR、旧类加载缓存或可选 API 不完整时会报告 MekanismProxy$NativeItemHandler 找不到；此前异常会在 MachineProxy.hasAny 的第一个物品检查处终止，导致流体储罐和化学品储罐也被误判为不存在。降级后物品、Forge 流体和 Mekanism 气体能力可以继续独立探测。
 */
package com.primordial.machine.adapters;

import com.primordial.machine.ModAdapterProxy;
import com.primordial.machine.MachineProxyRegistry;
import com.primordial.machine.SingleTankView;
import mekanism.api.Action;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.fluid.IMekanismFluidHandler;
import mekanism.api.fluid.IExtendedFluidHandler;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.energy.IEnergyStorage;
import java.util.List;

/** Mekanism 10.4.16：只使用外部面代理；J→FE 由 ForgeEnergyIntegration 按 Mekanism 配置换算。 */
public final class MekanismProxy extends ModAdapterProxy {
    public static final MekanismProxy INSTANCE = new MekanismProxy();
    private MekanismProxy() { super("mekanism"); }

    public static void register() {
        MachineProxyRegistry.registerModProxy("mekanism", INSTANCE);
        MachineProxyRegistry.registerModProxy("mekanismgenerators", INSTANCE);
        // 附属只要实现原生库存/流体 API 就沿用相同规则，无需猜测附属的 modId。
        MachineProxyRegistry.register("mekanism:api", be -> be instanceof IMekanismInventory
                || be instanceof IMekanismFluidHandler || INSTANCE.gasHandler(be, null) != null, INSTANCE);
    }

    @Override
    public IItemHandler itemHandler(BlockEntity machine, Direction side) {
        if (machine instanceof IMekanismInventory inventory && side != null && !machine.isRemoved()) {
            try {
                return new NativeItemHandler(inventory, side);
            } catch (RuntimeException | LinkageError unavailable) {
                // 原生包装类加载失败时继续使用 Forge capability，不能阻断流体/气体探测。
            }
        }
        return super.itemHandler(machine, side);
    }

    @Override
    public IFluidHandler fluidHandler(BlockEntity machine, Direction side) {
        if (machine instanceof IMekanismFluidHandler fluids && side != null && !machine.isRemoved()) {
            try {
                return new NativeFluidHandler(fluids, side);
            } catch (RuntimeException | LinkageError unavailable) {
                // 原生包装类不可用时回退 Forge 流体能力，保留可用的储罐接口。
            }
        }
        return super.fluidHandler(machine, side);
    }

    /** Mekanism 的严格能源单位是 J；该视图只在缺少 Forge Energy 时启用，并转换为 FE。 */
    @Override
    public IEnergyStorage energyHandler(BlockEntity machine, Direction side) {
        IEnergyStorage forge;
        try {
            forge = super.energyHandler(machine, side);
        } catch (RuntimeException | LinkageError unavailable) {
            forge = null;
        }
        if (forge != null) return forge;
        if (machine == null || machine.isRemoved()) return null;
        try {
            IStrictEnergyHandler strict = machine.getCapability(Capabilities.STRICT_ENERGY, side).resolve().orElse(null);
            return strict == null ? null : new StrictEnergyStorage(strict);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Override
    public String itemSlotKey(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        if (machine instanceof IMekanismInventory inventory) {
            int index = canonicalIndex(inventory.getInventorySlots(null), inventory.getInventorySlots(side), slot);
            return index < 0 ? "" : endpointKey(machine, side) + "/inventory/" + index;
        }
        return super.itemSlotKey(machine, side, handler, slot);
    }

    @Override
    public String fluidTankKey(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        if (machine instanceof IMekanismFluidHandler fluids) {
            int index = canonicalIndex(fluids.getFluidTanks(null), fluids.getFluidTanks(side), tank);
            return index < 0 ? "" : endpointKey(machine, side) + "/tank/" + index;
        }
        return super.fluidTankKey(machine, side, handler, tank);
    }

    /** null 面仅用来读取槽对象身份；插入/提取仍必须经过选定面的权限代理。 */
    private static int canonicalIndex(List<?> all, List<?> visible, int index) {
        if (all == null || visible == null || index < 0 || index >= visible.size()) return -1;
        Object target = visible.get(index);
        for (int i = 0; i < all.size(); i++) if (all.get(i) == target) return i;
        return -1;
    }

    @Override
    public IFluidHandler targetFluidHandler(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        try {
            if (handler instanceof NativeFluidHandler nativeHandler) return nativeHandler.tankView(tank);
        } catch (LinkageError unavailable) {
            // 当前运行时无法解析原生包装类时，继续执行 Forge 流体处理器的通用路径。
        }
        if (!(handler instanceof IExtendedFluidHandler extended)) return super.targetFluidHandler(machine, side, handler, tank);
        return new SingleTankView(handler, tank) {
            @Override public int fill(FluidStack stack, FluidAction action) {
                if (stack.isEmpty()) return 0;
                // Mekanism insertFluid 返回未插入的剩余量，Forge fill 返回已插入量。
                return stack.getAmount() - extended.insertFluid(tank, stack, Action.fromFluidAction(action)).getAmount();
            }
            @Override public FluidStack drain(int amount, FluidAction action) {
                return amount <= 0 ? FluidStack.EMPTY : extended.extractFluid(tank, amount, Action.fromFluidAction(action));
            }
        };
    }

    /** 原生 Mekanism 物品 handler：所有索引调用都带上同一外部面。 */
    private static final class NativeItemHandler implements IItemHandler {
        private final IMekanismInventory inventory;
        private final Direction side;

        NativeItemHandler(IMekanismInventory inventory, Direction side) {
            this.inventory = inventory;
            this.side = side;
        }

        private List<mekanism.api.inventory.IInventorySlot> slots() {
            List<mekanism.api.inventory.IInventorySlot> result = inventory.getInventorySlots(side);
            return result == null ? List.of() : result;
        }

        private void check(int slot) {
            if (slot < 0 || slot >= slots().size()) throw new IndexOutOfBoundsException("Invalid Mekanism slot");
        }

        @Override public int getSlots() { return slots().size(); }
        @Override public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            check(slot); return inventory.getStackInSlot(slot, side);
        }
        @Override public int getSlotLimit(int slot) {
            check(slot); return inventory.getSlotLimit(slot, side);
        }
        @Override public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            check(slot); return inventory.isItemValid(slot, stack, side);
        }
        @Override public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            check(slot); return inventory.insertItem(slot, stack, side, mekanism.api.Action.get(!simulate));
        }
        @Override public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            check(slot); return inventory.extractItem(slot, amount, side, mekanism.api.Action.get(!simulate));
        }
    }

    /** 原生 Mekanism 流体 handler：聚合接口不会把目标罐改写到其它罐。 */
    private static final class NativeFluidHandler implements IFluidHandler {
        private final IMekanismFluidHandler handler;
        private final Direction side;

        NativeFluidHandler(IMekanismFluidHandler handler, Direction side) {
            this.handler = handler;
            this.side = side;
        }

        private void check(int tank) {
            if (tank < 0 || tank >= getTanks()) throw new IndexOutOfBoundsException("Invalid Mekanism tank");
        }

        @Override public int getTanks() {
            List<mekanism.api.fluid.IExtendedFluidTank> result = handler.getFluidTanks(side);
            return result == null ? 0 : result.size();
        }
        @Override public FluidStack getFluidInTank(int tank) { check(tank); return handler.getFluidInTank(tank, side); }
        @Override public int getTankCapacity(int tank) { check(tank); return handler.getTankCapacity(tank, side); }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { check(tank); return handler.isFluidValid(tank, stack, side); }

        @Override
        public int fill(FluidStack stack, FluidAction action) {
            if (stack == null || stack.isEmpty()) return 0;
            int accepted = 0;
            for (int tank = 0; tank < getTanks() && accepted < stack.getAmount(); tank++) {
                FluidStack portion = stack.copy();
                portion.setAmount(stack.getAmount() - accepted);
                FluidStack rest = handler.insertFluid(tank, portion, side, mekanism.api.Action.fromFluidAction(action));
                accepted += portion.getAmount() - Math.max(0, rest.getAmount());
            }
            return accepted;
        }

        @Override
        public FluidStack drain(FluidStack stack, FluidAction action) {
            if (stack == null || stack.isEmpty()) return FluidStack.EMPTY;
            for (int tank = 0; tank < getTanks(); tank++) {
                if (getFluidInTank(tank).isFluidEqual(stack)) return drainTank(tank, stack.getAmount(), action);
            }
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0) return FluidStack.EMPTY;
            for (int tank = 0; tank < getTanks(); tank++) {
                if (!getFluidInTank(tank).isEmpty()) return drainTank(tank, amount, action);
            }
            return FluidStack.EMPTY;
        }

        IFluidHandler tankView(int tank) {
            check(tank);
            return new SingleTankView(this, tank) {
                @Override public int fill(FluidStack stack, FluidAction action) {
                    if (stack == null || stack.isEmpty()) return 0;
                    FluidStack rest = handler.insertFluid(tank, stack, side, mekanism.api.Action.fromFluidAction(action));
                    return stack.getAmount() - Math.max(0, rest.getAmount());
                }

                @Override public FluidStack drain(int amount, FluidAction action) {
                    return drainTank(tank, amount, action);
                }
            };
        }

        private FluidStack drainTank(int tank, int amount, FluidAction action) {
            return amount <= 0 ? FluidStack.EMPTY
                    : handler.extractFluid(tank, amount, side, mekanism.api.Action.fromFluidAction(action));
        }
    }

    /** 将 Mekanism FloatingLong 的 J 裁剪为 Forge Energy 的 int 接口，模拟不会写入原 handler。 */
    private static final class StrictEnergyStorage implements IEnergyStorage {
        private final IStrictEnergyHandler handler;

        StrictEnergyStorage(IStrictEnergyHandler handler) {
            this.handler = handler;
        }

        private boolean valid() { return handler.getEnergyContainerCount() > 0; }
        private FloatingLong joulesFromFe(long fe) { return EnergyUnit.FORGE_ENERGY.convertFrom(FloatingLong.create(fe)); }
        private int feFromJoules(FloatingLong joules) {
            if (joules == null || joules.isZero()) return 0;
            FloatingLong fe = EnergyUnit.FORGE_ENERGY.convertTo(joules).floor();
            return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, fe.longValue()));
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0 || !valid()) return 0;
            FloatingLong remaining = joulesFromFe(maxReceive);
            FloatingLong accepted = FloatingLong.ZERO;
            for (int i = 0; i < handler.getEnergyContainerCount() && !remaining.isZero(); i++) {
                FloatingLong rest = handler.insertEnergy(i, remaining, mekanism.api.Action.get(!simulate));
                FloatingLong moved = remaining.subtract(rest).max(FloatingLong.ZERO);
                accepted = accepted.add(moved);
                remaining = rest.max(FloatingLong.ZERO);
            }
            return Math.min(maxReceive, feFromJoules(accepted));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0 || !valid()) return 0;
            FloatingLong remaining = joulesFromFe(maxExtract);
            FloatingLong extracted = FloatingLong.ZERO;
            for (int i = 0; i < handler.getEnergyContainerCount() && !remaining.isZero(); i++) {
                FloatingLong moved = handler.extractEnergy(i, remaining, mekanism.api.Action.get(!simulate)).max(FloatingLong.ZERO);
                extracted = extracted.add(moved);
                remaining = remaining.subtract(moved).max(FloatingLong.ZERO);
            }
            return Math.min(maxExtract, feFromJoules(extracted));
        }

        private FloatingLong total(java.util.function.IntFunction<FloatingLong> values) {
            FloatingLong total = FloatingLong.ZERO;
            for (int i = 0; i < handler.getEnergyContainerCount(); i++) {
                FloatingLong value = values.apply(i);
                if (value != null) total = total.add(value.max(FloatingLong.ZERO));
            }
            return total;
        }

        @Override public int getEnergyStored() { return valid() ? feFromJoules(total(handler::getEnergy)) : 0; }
        @Override public int getMaxEnergyStored() { return valid() ? feFromJoules(total(handler::getMaxEnergy)) : 0; }
        @Override public boolean canExtract() { return valid() && !total(handler::getEnergy).isZero(); }
        @Override public boolean canReceive() { return valid() && !total(handler::getNeededEnergy).isZero(); }
    }

    /** Mekanism 化学品能力不属于 Forge Fluid；暴露原生 IGasHandler 供气体页面和 JEI 桥接层使用。 */
    public IGasHandler gasHandler(BlockEntity machine, Direction side) {
        if (machine == null || machine.isRemoved()) return null;
        try {
            return machine.getCapability(Capabilities.GAS_HANDLER, side).resolve().orElse(null);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public String gasTankKey(BlockEntity machine, Direction side, IGasHandler handler, int tank) {
        if (handler == null || tank < 0 || tank >= handler.getTanks()) return "";
        return endpointKey(machine, side) + "/gas/" + handler.getClass().getName() + "/" + handler.getTanks() + "/" + tank;
    }

    @Override
    public boolean hasAny(BlockEntity machine) {
        if (super.hasAny(machine)) return true;
        for (Direction side : supportedSides(machine)) if (gasHandler(machine, side) != null) return true;
        return gasHandler(machine, null) != null;
    }
}
