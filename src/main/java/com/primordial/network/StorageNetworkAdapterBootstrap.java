/**
 * 文件职责：把可选 AE2/RS 存储适配器限制在模组存在时的反射加载边界。
 * 变更记录 #1（2026-10-11 23:44:54 · 1.2.0-storage-interface-system）
 * - 删除：无；新增单独启动器，不让公共服务引用第三方 API。
 * - 新增：AE2 与 Refined Storage 独立检测、注册及 LinkageError 降级日志。
 * - 修改：适配器注册失败只禁用对应网络，Forge capability 和其它模组仍可继续运行。
 * - 用途：满足无可选模组时正常启动的类加载约束；AE2 capability 在更早的注册事件中单独装配。
 */
/**
 * 【变更记录 #2】2026-10-14 14:14:39（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无；旧外部总线兼容端保留。
 * - 新增：以隔离工厂创建 AE2/RS/超越维度网络节点，普通方块实体仅持有公共生命周期接口。
 * - 修改：先检查可选模组加载，再反射调用适配器 createPort，失败记录并禁用该端口。
 * - 用途：转化接口可直接接线，未安装可选模组不解析第三方接口类型。
 */
/**
 * 【变更记录 #3】2026-10-14 14:53:00（第 3 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：注册失败后仍调用该端口工厂的路径。
 * - 新增：成功注册的适配器集合。
 * - 修改：可选 API 初始化失败时不创建半初始化端口，其他网络继续工作。
 * - 用途：防止版本不兼容造成后续世界 tick 崩溃；旧外部总线注册已由原生节点替代。
 */
package com.primordial.network;

import com.primordial.PrimordialMod;
import net.minecraftforge.fml.ModList;

public final class StorageNetworkAdapterBootstrap {
    private static final java.util.Set<String> AVAILABLE=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Map<String,String> ADAPTERS=java.util.Map.of(
            "ae2","com.primordial.compat.storage.AE2StorageAdapter",
            "refinedstorage","com.primordial.compat.storage.RSStorageAdapter",
            "beyonddimensions","com.primordial.compat.storage.BeyondDimensionsStorageAdapter");
    private StorageNetworkAdapterBootstrap() { }

    public static void registerAll() {
        registerIfLoaded("ae2", "com.primordial.compat.storage.AE2StorageAdapter");
        registerIfLoaded("refinedstorage", "com.primordial.compat.storage.RSStorageAdapter");
        registerIfLoaded("beyonddimensions", "com.primordial.compat.storage.BeyondDimensionsStorageAdapter");
    }

    public static TransmutationNetworkPort createPort(String id,com.primordial.blockentity.TransmutationInterfaceBlockEntity be){
        if(!ModList.get().isLoaded(id)||!AVAILABLE.contains(id))return null;
        try{
            return (TransmutationNetworkPort)Class.forName(ADAPTERS.get(id)).getMethod("createPort",com.primordial.blockentity.TransmutationInterfaceBlockEntity.class).invoke(null,be);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){
            PrimordialMod.LOGGER.warn("[Primordial] {} direct network port unavailable at {}",id,be.getBlockPos(),failure);return null;
        }
    }
    public static java.util.Set<String> portIds(){return ADAPTERS.keySet();}

    private static void registerIfLoaded(String modId, String adapterClassName) {
        if (!ModList.get().isLoaded(modId)) return;
        try {
            Class<?> adapter = Class.forName(adapterClassName, true, StorageNetworkAdapterBootstrap.class.getClassLoader());
            adapter.getMethod("register").invoke(null);
            AVAILABLE.add(modId);
            PrimordialMod.LOGGER.info("[Primordial] {} storage interface registered", modId);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn("[Primordial] {} storage interface disabled", modId, failure);
        }
    }
}
