/**
 * 【文件职责】适配 Immersive Engineering 1.20.1 的机器方块实体，读取其外部 Forge 物品、流体和能源能力。
 * - 核心输入：Immersive Engineering 方块实体、选定外部方向和 Forge capability。
 * - 核心输出：稳定的端点/槽位身份及只访问绑定槽的单槽视图。
 * - 调用链：ModAdapterBootstrap 注册 → MachineProxyRegistry 按 modId 或包名命中 → ForgeModAdapterProxy 读取能力。
 *
 * 【变更记录 #1】2026-09-15 22:09:32（第 1 次备注 · 1.0.0-stage5-adapters2）
 * - 删除：无；Immersive Engineering 原有 Forge capability 不做二次包装或能量换算。
 * - 新增：immersiveengineering modId 和公开 Java 包名匹配，覆盖主模组及附属方块实体。
 * - 修改：多流体储罐拒绝通用聚合写入，仅允许单储罐绑定；物品和能源沿外部方向读取并保留原能力权限。
 * - 用途：为 IE 的机器、泵、冶炼设备和附属设备提供安全的配置器端点，避免绑定一个槽时误写其它储罐。
 */
package com.primordial.machine.adapters;

import com.primordial.machine.MachineProxyRegistry;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Immersive Engineering 专用 Forge capability 适配器。 */
public final class ImmersiveEngineeringProxy extends ForgeModAdapterProxy {
    public static final ImmersiveEngineeringProxy INSTANCE = new ImmersiveEngineeringProxy();

    private ImmersiveEngineeringProxy() {
        super("immersiveengineering");
    }

    public static void register() {
        MachineProxyRegistry.registerModProxy("immersiveengineering", INSTANCE);
        MachineProxyRegistry.register("immersiveengineering:api",
                be -> classInPackage(be, "blusunrize.immersiveengineering."), INSTANCE);
    }
}
