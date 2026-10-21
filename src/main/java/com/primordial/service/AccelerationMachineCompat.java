/**
 * 【文件职责】延迟加载 AE2 专用加速适配器，公共目标代码不引用 AE2 API。
 * 【变更记录 #1】2026-09-18 01:33:29（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：安装检测、一次性反射工厂及原生 ticker 回退。
 * - 修改：缺少 AE2、接口不匹配或链接失败均返回空适配器；故障不反复扫描类路径。
 * - 用途：支持只有 AE2 节点调度而没有普通方块 ticker 的设备，未安装 AE2 仍能启动。
 */
package com.primordial.service;

import java.util.function.Function;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraftforge.fml.ModList;

public final class AccelerationMachineCompat {
    /** 可选适配器返回的回调可额外报告连接失效与已知空闲，通用层不引用其 API 类型。 */
    public interface CheckedTicker extends BlockEntityTicker<BlockEntity> {
        boolean valid();
        boolean idle();
    }
    private static boolean initialized;
    private static Function<BlockEntity, BlockEntityTicker<BlockEntity>> adapter;
    private AccelerationMachineCompat() { }

    @SuppressWarnings("unchecked")
    static BlockEntityTicker<BlockEntity> ticker(BlockEntity entity) {
        try {
            if (!initialized) {
                initialized = true;
                if (ModList.get() != null && ModList.get().isLoaded("ae2")) {
                    adapter = (Function<BlockEntity, BlockEntityTicker<BlockEntity>>) Class.forName(
                            "com.primordial.compat.acceleration.Ae2AccelerationAdapter").getConstructor().newInstance();
                }
            }
            return adapter == null ? null : adapter.apply(entity);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            adapter = null;
            return null;
        }
    }
}
