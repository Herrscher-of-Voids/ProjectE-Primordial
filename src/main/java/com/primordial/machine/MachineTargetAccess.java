/**
 * 【文件职责】在每次任务执行时验证绑定，并返回当前能力的单端口视图。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增绑定解析入口。
 * - 新增：方向/索引/身份、已移除机器和能量唯一端口校验。
 * - 修改：缺失、禁用或结构变化时返回空，不跨方向/槽位回退；能力不跨 tick 缓存。
 * - 用途：TaskExecutor 和 ConfiguratorMenu 共用此入口，防止 LazyOptional 失效后继续写旧库存。
 */
/**
 * 【变更记录 #2】2026-09-14 12:10:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：能量端口可解析 side=6 的无方向 capability。
 * - 修改：物品/流体访问仍调用 direction()，内部端口无法绕过精确侧面校验。
 * - 用途：配置器和任务执行器可安全读写只在 null side 暴露的 FE Utility。
 */
/**
 * 【变更记录 #3】2026-09-15 12:04:51（第 3 次备注 · 1.0.0-configurator-fixes）
 * - 删除：气体执行必须匹配 MekanismProxy 代理类型的判断。
 * - 新增：加载依赖检查后读取选定方向的真实 GAS_HANDLER。
 * - 修改：统一用 MekanismProxy 气体 key 验证罐索引，机器原有物品代理不影响气体能力。
 * - 用途：让第三方气体设备和黑洞接受精确绑定，同时拒绝卸载机器、内部方向和失效 key。
 */
/**
 * 【变更记录 #4】2026-09-16（独立资源端口）
 * - 删除：无；保留物品、流体、气体和 FE 的既有身份校验。
 * - 新增：按 MachineTarget key 查找独立资源端口，供后续配置器和任务规则复用。
 * - 修改：资源端口同样要求方向、区块和稳定 key 完全匹配，不会因为端口顺序变化而写入邻近储存。
 * - 用途：把魔力、气压、灵气、魔源、转速和应力接入现有绑定安全边界。
 */
/**
 * 【变更记录 #5】2026-10-13 22:57:15（第 5 次备注 · 1.2.0-relative-target-system）
 * - 删除：新绑定只保存世界方向、机器旋转后仍操作旧面的路径；所有历史备注保留。
 * - 新增/修改：物品、流体、FE、气体与独立资源在每次查询前按当前机器朝向解析绑定，再执行原结构身份核对；不跨面回退、不缓存旧能力。
 * - 用途：第 9 系统相对面绑定与显示、网络、存档、执行保持一致；已有绝对目标重新绑定才转为相对，避免误改输入输出。
 */
/**
 * 【变更记录 #6】2026-10-13 23:01:02（第 6 次备注 · 1.2.0-relative-target-system）
 * - 删除：旋转后身份串超过 512 字符时继续解析方向的路径。新增：解析后再次检查 bound。
 * - 修改：五种访问入口一致返回空，不抛出未绑定异常。用途：边界身份失效只暂停该条规则。
 */
package com.primordial.machine;

import com.primordial.data.MachineTarget;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.resource.EmcResourcePort;
import com.primordial.resource.ResourcePortRegistry;
import javax.annotation.Nullable;

/** 任务执行前重新读取所选面的能力并核对结构身份；不跨 tick 缓存 capability 或方块实体。 */
public final class MachineTargetAccess {
    private MachineTargetAccess() {}

    @Nullable
    public static IItemHandler items(MachineProxy proxy, BlockEntity machine, MachineTarget target) {
        if (!target.bound() || target.side() == MachineTarget.INTERNAL_SIDE || machine.isRemoved()) return null;
        target = target.resolve(machine.getBlockState());
        if (!target.bound()) return null;
        Direction side = target.direction();
        IItemHandler handler = proxy.itemHandler(machine, side);
        if (handler == null || target.index() >= handler.getSlots()) return null;
        if (!target.key().equals(proxy.itemSlotKey(machine, side, handler, target.index()))) return null;
        return proxy.targetItemHandler(machine, side, handler, target.index());
    }

    @Nullable
    public static IFluidHandler fluids(MachineProxy proxy, BlockEntity machine, MachineTarget target) {
        if (!target.bound() || target.side() == MachineTarget.INTERNAL_SIDE || machine.isRemoved()) return null;
        target = target.resolve(machine.getBlockState());
        if (!target.bound()) return null;
        Direction side = target.direction();
        IFluidHandler handler = proxy.fluidHandler(machine, side);
        if (handler == null || target.index() >= handler.getTanks()) return null;
        if (!target.key().equals(proxy.fluidTankKey(machine, side, handler, target.index()))) return null;
        return proxy.targetFluidHandler(machine, side, handler, target.index());
    }

    @Nullable
    public static IEnergyStorage energy(MachineProxy proxy, BlockEntity machine, MachineTarget target) {
        if (!target.bound() || target.index() != 0 || machine.isRemoved()) return null;
        target = target.resolve(machine.getBlockState());
        if (!target.bound()) return null;
        Direction side = target.directionOrNull();
        try {
            IEnergyStorage handler = proxy.energyHandler(machine, side);
            return handler != null && target.key().equals(energyKey(proxy, machine, side, handler)) ? handler : null;
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /** 读取 Mekanism 原生气体 handler，并核对气体罐身份；普通 Forge 代理不会伪造化学品接口。 */
    @Nullable
    public static Object gases(MachineProxy proxy, BlockEntity machine, MachineTarget target) {
        if (!com.primordial.compat.MekanismGasCompat.loaded() || !target.bound()
                || target.side() == MachineTarget.INTERNAL_SIDE || machine.isRemoved()) return null;
        target = target.resolve(machine.getBlockState());
        if (!target.bound()) return null;
        Direction side = target.directionOrNull();
        return MekanismGasCompat.boundHandler(machine, side, target.index(), target.key());
    }

    /** 按稳定绑定身份返回独立资源端口；不存在或身份变化时返回 null。 */
    @Nullable
    public static EmcResourcePort resource(BlockEntity machine, MachineTarget target) {
        if (machine == null || machine.isRemoved() || target == null || !target.bound()) return null;
        target = target.resolve(machine.getBlockState());
        if (!target.bound()) return null;
        Direction side = target.directionOrNull();
        for (EmcResourcePort port : ResourcePortRegistry.find(machine, side)) {
            if (target.key().equals(port.key())) return port;
        }
        return null;
    }

    public static String energyKey(MachineProxy proxy, BlockEntity machine, Direction side, IEnergyStorage handler) {
        return proxy.endpointKey(machine, side) + "/energy/" + handler.getClass().getName();
    }
}
