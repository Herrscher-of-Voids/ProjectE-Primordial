/**
 * 【文件职责】转化接口公共端口工厂注册表；生产环境暂不注册尚未迁移的网络适配器。
 * 【变更记录 #1】2026-10-22 22:54:44（第 1 次备注 · 2.0.0-neo-dev 转化接口基础）
 * - 删除：无。新增：唯一 ID 注册、稳定顺序枚举和按宿主创建端口。
 * - 修改：使用并发映射；重复 ID 拒绝覆盖，空工厂结果表示该宿主不支持此端口。
 * - 用途：让兼容层在条件加载后接入；公共注册表不解析任何可选模组 API。
 */
package com.primordial.neoforge.transmutation;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public final class NeoForgeTransmutationNetworkRegistry {
    private static final ConcurrentHashMap<String, Function<NeoForgeTransmutationInterfaceBlockEntity,
            NeoForgeTransmutationNetworkPort>> FACTORIES = new ConcurrentHashMap<>();

    private NeoForgeTransmutationNetworkRegistry() {}

    public static void register(String id, Function<NeoForgeTransmutationInterfaceBlockEntity,
            NeoForgeTransmutationNetworkPort> factory) {
        if (id == null || ResourceLocation.tryParse(id) == null) throw new IllegalArgumentException("Invalid network port id");
        if (FACTORIES.putIfAbsent(id, Objects.requireNonNull(factory)) != null)
            throw new IllegalStateException("Network port already registered: " + id);
    }

    public static List<String> registeredIds() { return FACTORIES.keySet().stream().sorted().toList(); }

    static boolean contains(String id) { return id != null && FACTORIES.containsKey(id); }

    @Nullable
    public static NeoForgeTransmutationNetworkPort create(String id, NeoForgeTransmutationInterfaceBlockEntity host) {
        var factory = FACTORIES.get(id);
        return factory == null ? null : factory.apply(host);
    }
}
