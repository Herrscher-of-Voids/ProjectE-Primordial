/**
 * 变更记录 #1（2026-09-13）：新增机器代理接口，统一描述机器的物品、流体、电力能力及方向。
 * - 用途：配置器界面和任务执行器通过代理访问机器，专用模组适配器无需修改界面协议。
 */
/**
 * 【文件职责】为配置器和任务执行器提供机器外部能力及明确端口视图。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：无；保留首个能力查询方法供已有调用方使用。
 * - 新增：端点身份、物品槽身份、流体罐身份和单端口访问钩子。
 * - 修改：通用精确流体绑定仅接受唯一储罐；物品默认包装为单槽。
 * - 用途：Mekanism、AE2、Titanium 可覆盖原生索引/身份实现；禁止聚合多罐 fill 冒充精确写入。
 */
package com.primordial.machine;

import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** 单个机器方块实体的能力快照。实现必须在服务端线程调用。 */
public interface MachineProxy {
    /** 代理标识，用于日志和调试。 */
    String id();

    /** 返回该机器支持的方向；包含 null 表示方块实体自身的无方向能力。 */
    Set<Direction> supportedSides(BlockEntity machine);

    @Nullable IItemHandler itemHandler(BlockEntity machine, @Nullable Direction side);
    @Nullable IFluidHandler fluidHandler(BlockEntity machine, @Nullable Direction side);
    @Nullable IEnergyStorage energyHandler(BlockEntity machine, @Nullable Direction side);

    /** 身份只描述端口结构，不包含库存数量；原生适配器可追加面板或组件标识。 */
    default String endpointKey(BlockEntity machine, Direction side) {
        return id() + "/" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock());
    }

    default String itemSlotKey(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        return endpointKey(machine, side) + "/item/" + handler.getClass().getName() + "/" + handler.getSlots() + "/" + slot;
    }

    default String fluidTankKey(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        return endpointKey(machine, side) + "/fluid/" + handler.getClass().getName() + "/" + handler.getTanks() + "/" + tank;
    }

    default IItemHandler targetItemHandler(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        return new SingleSlotHandler(handler, slot);
    }

    /** Forge 标准接口没有按罐写入 API；多罐默认不允许精确绑定，交给专用适配器扩展。 */
    @Nullable
    default IFluidHandler targetFluidHandler(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        return tank == 0 && handler.getTanks() == 1 ? handler : null;
    }

    /** 在代理声明的方向中选择第一个可用能力，保留原配置器的自动方向探测行为。 */
    @Nullable
    default IItemHandler firstItemHandler(BlockEntity machine) {
        for (Direction side : supportedSides(machine)) {
            IItemHandler handler = itemHandler(machine, side);
            if (handler != null) return handler;
        }
        return null;
    }

    @Nullable
    default IFluidHandler firstFluidHandler(BlockEntity machine) {
        for (Direction side : supportedSides(machine)) {
            IFluidHandler handler = fluidHandler(machine, side);
            if (handler != null) return handler;
        }
        return null;
    }

    @Nullable
    default IEnergyStorage firstEnergyHandler(BlockEntity machine) {
        for (Direction side : supportedSides(machine)) {
            IEnergyStorage handler = energyHandler(machine, side);
            if (handler != null) return handler;
        }
        return null;
    }

    default boolean hasAny(BlockEntity machine) {
        for (Direction side : supportedSides(machine)) {
            if (itemHandler(machine, side) != null || fluidHandler(machine, side) != null
                    || energyHandler(machine, side) != null) return true;
        }
        return false;
    }

    static Set<Direction> allSides() {
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        return sides;
    }
}
