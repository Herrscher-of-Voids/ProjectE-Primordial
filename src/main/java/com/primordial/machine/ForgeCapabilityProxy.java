/**
 * 变更记录 #1（2026-09-13）：新增通用 Forge capability 机器代理。
 * - 新增：按方向读取 ITEM_HANDLER、FLUID_HANDLER、ENERGY 能力。
 * - 用途：为未实现专用适配器的模组提供统一回退入口。
 */
/**
 * 【变更记录 #3】2026-09-14 12:10:00（第 3 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：energyHandler 允许 null side 查询方块实体内部 FE capability。
 * - 修改：物品和流体继续拒绝 null side，只有能量接口可使用内部端口。
 * - 用途：兼容龙之研究能量塔等仅从 FE Utility 暴露无方向能力的方块。
 */
/**
 * 【文件职责】读取未知机器六个外部面的 Forge 物品、流体、FE 能力。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：删除无方向优先扫描和每次创建方向集合。
 * - 新增：只读的六面常量集合及已移除机器检查。
 * - 修改：null 面返回空，实际访问保持原 capability 的自动化权限。
 * - 用途：用于专用适配器的通用回退；所有任务仍须显式绑定，不能绕过侧面禁用。
 */
package com.primordial.machine;

import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/** 读取标准 Forge capability 的通用代理。 */
public final class ForgeCapabilityProxy implements MachineProxy {
    public static final ForgeCapabilityProxy INSTANCE = new ForgeCapabilityProxy();
    private static final Set<Direction> SIDES = java.util.Collections.unmodifiableSet(java.util.EnumSet.allOf(Direction.class));
    private ForgeCapabilityProxy() {}

    @Override public String id() { return "forge_capabilities"; }

    @Override
    public Set<Direction> supportedSides(BlockEntity machine) {
        return SIDES;
    }

    @Override
    public IItemHandler itemHandler(BlockEntity machine, @Nullable Direction side) {
        return side == null || machine.isRemoved() ? null : machine.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null);
    }

    @Override
    public IFluidHandler fluidHandler(BlockEntity machine, @Nullable Direction side) {
        return side == null || machine.isRemoved() ? null : machine.getCapability(ForgeCapabilities.FLUID_HANDLER, side).resolve().orElse(null);
    }

    @Override
    public IEnergyStorage energyHandler(BlockEntity machine, @Nullable Direction side) {
        return machine.isRemoved() ? null : machine.getCapability(ForgeCapabilities.ENERGY, side).resolve().orElse(null);
    }
}
