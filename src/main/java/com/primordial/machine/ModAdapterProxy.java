/**
 * 变更记录 #1（2026-09-14）：新增主流模组代理的共享实现。
 * - 新增：按模组 ID 标记代理，能力读取仍通过 Forge 标准 capability，避免编译期依赖可选模组。
 * - 用途：Mekanism、AE2、Industrial Foregoing 适配器共享方向扫描和能力访问代码。
 */
/**
 * 【变更记录 #3】2026-09-14 12:10:00（第 3 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：专用代理的 energyHandler 允许内部 null side 访问。
 * - 修改：itemHandler/fluidHandler 仍拒绝 null side，避免绕过机器的侧面自动化权限。
 * - 用途：让专用适配器共享龙之研究等内部 FE 端口兼容逻辑。
 */
/**
 * 【文件职责】封装专用适配器共享的外部面与 Forge 能力访问。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：删除 includeNullSide 及每次分配方向集合。
 * - 新增：可复用六面常量与 null 面拒绝逻辑。
 * - 修改：专用适配器默认只使用外部自动化面，具体原生 API 由子类实现。
 * - 用途：避免 Mekanism 只读 null 代理或 Titanium 无方向全库存混入任务执行。
 */
package com.primordial.machine;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** 主流模组代理的安全基类；真实专用 API 可在子类中覆盖单项能力。 */
public abstract class ModAdapterProxy implements MachineProxy {
    private final String id;
    private static final Set<Direction> SIDES = java.util.Collections.unmodifiableSet(java.util.EnumSet.allOf(Direction.class));

    protected ModAdapterProxy(String id) {
        this.id = id;
    }

    @Override public final String id() { return id; }

    @Override
    public Set<Direction> supportedSides(BlockEntity machine) {
        return SIDES;
    }

    @Override
    @Nullable
    public IItemHandler itemHandler(BlockEntity machine, @Nullable Direction side) {
        return side == null ? null : ForgeCapabilityProxy.INSTANCE.itemHandler(machine, side);
    }

    @Override
    @Nullable
    public IFluidHandler fluidHandler(BlockEntity machine, @Nullable Direction side) {
        return side == null ? null : ForgeCapabilityProxy.INSTANCE.fluidHandler(machine, side);
    }

    @Override
    @Nullable
    public IEnergyStorage energyHandler(BlockEntity machine, @Nullable Direction side) {
        return ForgeCapabilityProxy.INSTANCE.energyHandler(machine, side);
    }
}
