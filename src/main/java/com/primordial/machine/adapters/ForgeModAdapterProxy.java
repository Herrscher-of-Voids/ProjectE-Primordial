/**
 * 【文件职责】为不需要专用第三方类路径的主流模组适配器提供安全 Forge capability 基础实现。
 * - 核心输入：方块实体、外部方向和 Forge 物品/流体/能源 capability。
 * - 核心输出：按模组适配器生成稳定端点、槽位身份和单槽访问视图。
 * - 调用链：MachineProxyRegistry 选择子类 → 配置器或 TaskExecutor 查询能力 → MachineTargetAccess 复核身份。
 *
 * 【变更记录 #1】2026-09-15 22:09:32（第 1 次备注 · 1.0.0-stage5-adapters2）
 * - 删除：无；保留既有 ForgeCapabilityProxy 的通用回退路径。
 * - 新增：异常隔离的物品、流体和能源 capability 查询，模组/方块实体类与方向身份端点，以及严格的槽位和储罐边界检查。
 * - 修改：多储罐仍拒绝通用聚合写入，单储罐交给 SingleTankView；物品槽使用 SingleSlotHandler 的精确索引。
 * - 用途：Immersive Engineering、Ender IO 和 GregTech Modern 的专用子类共享同一套安全边界，避免重复实现造成行为漂移。
 */
/**
 * 【变更记录 #2】2026-09-15 22:17:42（第 2 次备注 · 1.0.0-stage5-adapters2）
 * - 删除：无。
 * - 新增：端点身份加入 `side` 段，内部能源端口使用 `internal` 标识。
 * - 修改：同一方块实体不同方向现在生成不同 key，减少绑定后换面的误匹配；单罐/单槽安全边界保持不变。
 * - 用途：MachineTargetAccess 复核时同时约束模组、机器实现类和方向，防止相同 capability 形状的其它面复用旧绑定。
 */
package com.primordial.machine.adapters;

import com.primordial.machine.ModAdapterProxy;
import com.primordial.machine.SingleSlotHandler;
import com.primordial.machine.SingleTankView;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * 只使用 Forge 公共 capability 的专用适配器基类；所有外部 API 查询失败都会安全回退为空能力。
 */
abstract class ForgeModAdapterProxy extends ModAdapterProxy {
    protected ForgeModAdapterProxy(String id) {
        super(id);
    }

    @Override
    @Nullable
    public IItemHandler itemHandler(BlockEntity machine, @Nullable Direction side) {
        if (machine == null || side == null || machine.isRemoved()) return null;
        try {
            return machine.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Override
    @Nullable
    public IFluidHandler fluidHandler(BlockEntity machine, @Nullable Direction side) {
        if (machine == null || side == null || machine.isRemoved()) return null;
        try {
            return machine.getCapability(ForgeCapabilities.FLUID_HANDLER, side).resolve().orElse(null);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Override
    @Nullable
    public IEnergyStorage energyHandler(BlockEntity machine, @Nullable Direction side) {
        if (machine == null || machine.isRemoved()) return null;
        try {
            return machine.getCapability(ForgeCapabilities.ENERGY, side).resolve().orElse(null);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Override
    public String endpointKey(BlockEntity machine, Direction side) {
        if (machine == null) return id() + "/null";
        try {
            return super.endpointKey(machine, side) + "/side/"
                    + (side == null ? "internal" : side.getName())
                    + "/class/" + machine.getClass().getName();
        } catch (RuntimeException | LinkageError unavailable) {
            return id() + "/side/" + (side == null ? "internal" : side.getName())
                    + "/class/" + machine.getClass().getName();
        }
    }

    @Override
    public String itemSlotKey(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        if (handler == null || slot < 0 || slot >= handler.getSlots()) return "";
        return endpointKey(machine, side) + "/item/" + handler.getClass().getName()
                + "/" + handler.getSlots() + "/" + slot;
    }

    @Override
    public String fluidTankKey(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        if (handler == null || tank < 0 || tank >= handler.getTanks()) return "";
        return endpointKey(machine, side) + "/fluid/" + handler.getClass().getName()
                + "/" + handler.getTanks() + "/" + tank;
    }

    @Override
    @Nullable
    public IItemHandler targetItemHandler(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        if (handler == null || slot < 0 || slot >= handler.getSlots()) return null;
        return new SingleSlotHandler(handler, slot);
    }

    @Override
    @Nullable
    public IFluidHandler targetFluidHandler(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        if (handler == null || handler.getTanks() != 1 || tank != 0) return null;
        return new SingleTankView(handler, tank) {
            @Override
            public int fill(net.minecraftforge.fluids.FluidStack stack, FluidAction action) {
                return source.fill(stack, action);
            }

            @Override
            public net.minecraftforge.fluids.FluidStack drain(int amount, FluidAction action) {
                return amount <= 0 ? net.minecraftforge.fluids.FluidStack.EMPTY : source.drain(amount, action);
            }
        };
    }

    /** 允许附属模组通过公开包名复用专用适配器，而不把其 API 类加入生产编译路径。 */
    protected static boolean classInPackage(BlockEntity machine, String packagePrefix) {
        return machine != null && machine.getClass().getName().startsWith(packagePrefix);
    }
}
