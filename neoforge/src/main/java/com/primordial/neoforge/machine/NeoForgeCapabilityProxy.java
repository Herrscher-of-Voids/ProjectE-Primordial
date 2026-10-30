/**
 * 【文件职责】通过 Level 的标准 BlockCapability 查询机器物品、流体和能量，不缓存 handler。
 * 【变更记录 #1】2026-10-22 11:36:36（第 1 次备注 · 2.0.0-neo-dev 能力平台契约）
 * - 删除：无。
 * - 新增：六方向枚举、无世界/已移除实体防御、物品与流体空侧拒绝、能量空侧查询。
 * - 修改：每次使用目标世界的 capability provider，资源与方向行为由对应 provider 决定。
 * - 用途：为后续机器系统隔离平台 API；自动测试覆盖查询、传输和 provider 失效生命周期。
 */
package com.primordial.neoforge.machine;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class NeoForgeCapabilityProxy implements NeoForgeMachineProxy {
    public static final NeoForgeCapabilityProxy INSTANCE = new NeoForgeCapabilityProxy();
    private static final Set<Direction> SIDES =
            Collections.unmodifiableSet(EnumSet.allOf(Direction.class));

    private NeoForgeCapabilityProxy() {}

    @Override
    public String id() {
        return "neoforge_capabilities";
    }

    @Override
    public Set<Direction> supportedSides(BlockEntity machine) {
        return SIDES;
    }

    @Override
    @Nullable
    public IItemHandler itemHandler(BlockEntity machine, @Nullable Direction side) {
        if (side == null) return null;
        Level level = usableLevel(machine);
        return level == null ? null : level.getCapability(Capabilities.ItemHandler.BLOCK,
                machine.getBlockPos(), machine.getBlockState(), machine, side);
    }

    @Override
    @Nullable
    public IFluidHandler fluidHandler(BlockEntity machine, @Nullable Direction side) {
        if (side == null) return null;
        Level level = usableLevel(machine);
        return level == null ? null : level.getCapability(Capabilities.FluidHandler.BLOCK,
                machine.getBlockPos(), machine.getBlockState(), machine, side);
    }

    @Override
    @Nullable
    public IEnergyStorage energyHandler(BlockEntity machine, @Nullable Direction side) {
        Level level = usableLevel(machine);
        return level == null ? null : level.getCapability(Capabilities.EnergyStorage.BLOCK,
                machine.getBlockPos(), machine.getBlockState(), machine, side);
    }

    @Nullable
    private static Level usableLevel(@Nullable BlockEntity machine) {
        if (machine == null || machine.isRemoved()) return null;
        return machine.getLevel();
    }
}
