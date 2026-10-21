/**
 * 文件职责：在目标模组实际存在时，注册对应的机器适配器。
 *
 * 变更记录 #1（2026-09-16，可选依赖类加载修复）
 * - 删除：主类对 Mekanism、AE2、工业先锋、沉浸工程、末影接口和格雷 API 适配器的直接导入。
 * - 新增：按模组 ID 反射加载适配器，并捕获缺失 API、版本不兼容和初始化错误。
 * - 修改：目标模组未安装时不创建适配器 lambda，避免 JVM 在加载主模组时解析第三方类型。
 * - 用途：所有联动模组保持可选；缺少任意一个目标模组时，原初模组仍可正常启动。
 *
 * 说明：适配器类本身可以继续使用目标模组的原生 API，但只能从本文件的反射边界进入。
 * 该边界必须保持在通用机器逻辑之外，不能把可选 API 类型写入通用任务、菜单或网络包的签名。
 */
package com.primordial.machine;

import com.primordial.PrimordialMod;
import net.minecraftforge.fml.ModList;

public final class ModAdapterBootstrap {
    private static boolean registered;

    private ModAdapterBootstrap() {
    }

    public static synchronized void registerAll() {
        if (registered) {
            return;
        }

        registerIfLoaded("mekanism", "com.primordial.machine.adapters.MekanismProxy");
        registerIfLoaded("ae2", "com.primordial.machine.adapters.AE2Proxy");
        registerIfLoaded("industrialforegoing", "com.primordial.machine.adapters.IndustrialForegoingProxy");
        registerIfLoaded("immersiveengineering", "com.primordial.machine.adapters.ImmersiveEngineeringProxy");
        registerIfLoaded("enderio", "com.primordial.machine.adapters.EnderIOProxy");
        registerIfLoaded("gtceu", "com.primordial.machine.adapters.GregTechModernProxy");
        com.primordial.resource.ResourceAdapterBootstrap.registerAll();

        registered = true;
        PrimordialMod.LOGGER.info("[Primordial] machine adapters: {}", MachineProxyRegistry.registeredIds());
    }

    /**
     * 只有 ModList 确认目标模组存在后才解析适配器类。
     * Class.forName 的 LinkageError 会覆盖缺少 API 类、版本变更和错误映射等情况；
     * 这些错误只会禁用当前适配器，不会传播到原初模组的公共启动路径。
     */
    private static void registerIfLoaded(String modId, String adapterClassName) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        try {
            Class<?> adapterClass = Class.forName(adapterClassName, true, ModAdapterBootstrap.class.getClassLoader());
            adapterClass.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn(
                    "[Primordial] {} adapter unavailable; Forge capability fallback remains active",
                    modId, failure);
        }
    }
}
