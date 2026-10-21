/**
 * 文件职责：按已安装模组注册独立资源端口适配器。
 *
 * 变更记录 #2（2026-09-16，可选依赖类加载修复）
 * - 删除：对各个资源适配器类的直接导入。
 * - 新增：只有目标模组已安装时，才通过反射解析并实例化适配器。
 * - 修改：缺少第三方 API、类链接失败或构造失败时，仅跳过当前资源端口。
 * - 用途：Botania、气动工艺、自然灵气、新生魔艺和机械动力均保持可选，不影响主模组启动。
 *
 * 适配器实现可以使用目标模组的 API；本类是它们进入通用资源服务前的唯一加载边界。
 */
package com.primordial.resource;

import com.primordial.PrimordialMod;
import net.minecraftforge.fml.ModList;

public final class ResourceAdapterBootstrap {
    private static boolean registered;

    private ResourceAdapterBootstrap() {
    }

    public static synchronized void registerAll() {
        if (registered) {
            return;
        }

        registerIfLoaded("botania", "botania", "com.primordial.resource.adapters.BotaniaResourceAdapter");
        registerIfLoaded("pneumaticcraft", "pneumaticcraft", "com.primordial.resource.adapters.PneumaticCraftResourceAdapter");
        registerIfLoaded("naturesaura", "naturesaura", "com.primordial.resource.adapters.NaturesAuraResourceAdapter");
        registerIfLoaded("ars_nouveau", "ars_nouveau", "com.primordial.resource.adapters.ArsNouveauResourceAdapter");
        registerCreateIfLoaded("create", "create_speed", false);
        registerCreateIfLoaded("create", "create_stress", true);

        registered = true;
        PrimordialMod.LOGGER.info("[Primordial] resource adapters: {}", ResourcePortRegistry.registeredIds());
    }

    private static void registerIfLoaded(String modId, String id, String adapterClassName) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        try {
            Class<?> type = Class.forName(adapterClassName, true, ResourceAdapterBootstrap.class.getClassLoader());
            Object value = type.getDeclaredConstructor().newInstance();
            if (!(value instanceof ReflectiveResourceAdapter adapter)) {
                throw new IllegalStateException("Adapter does not extend ReflectiveResourceAdapter: " + adapterClassName);
            }
            ResourcePortRegistry.register(id, adapter.matcher(), adapter);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn("[Primordial] {} resource adapter is unavailable", id, failure);
        }
    }

    private static void registerCreateIfLoaded(String modId, String id, boolean stress) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        try {
            Class<?> type = Class.forName("com.primordial.resource.adapters.CreateResourceAdapter", true,
                    ResourceAdapterBootstrap.class.getClassLoader());
            Object value = type.getDeclaredConstructor(boolean.class).newInstance(stress);
            if (!(value instanceof ReflectiveResourceAdapter adapter)) {
                throw new IllegalStateException("Create adapter has an invalid base type");
            }
            ResourcePortRegistry.register(id, adapter.matcher(), adapter);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn("[Primordial] {} resource adapter is unavailable", id, failure);
        }
    }
}
