/**
 * 变更记录 #1（2026-09-13）：新增机器代理注册表和通用 Forge 回退代理。
 * - 新增：按方块实体类型或模组 ID 注册专用代理。
 * - 修改：未知机器统一回退 ForgeCapabilityProxy，保持现有配置器可用。
 */
/**
 * 【文件职责】按注册优先级为方块实体选择专用代理，未命中时回退 Forge。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：删除同一注册 ID 重复追加的行为。
 * - 新增：同步注册和 ID 幂等检查。
 * - 修改：CopyOnWriteArrayList 查询保持无额外锁；重复初始化不会叠加适配器。
 * - 用途：服务端 tick、配置器快照共用注册表，不缓存世界或方块实体，避免跨存档泄漏。
 */
package com.primordial.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 线程安全的机器代理注册表；注册发生在初始化阶段，查询发生在服务端 tick。 */
public final class MachineProxyRegistry {
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private MachineProxyRegistry() {}

    public static synchronized void register(String id, Predicate<BlockEntity> matcher, MachineProxy proxy) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(matcher);
        Objects.requireNonNull(proxy);
        if (ENTRIES.stream().anyMatch(entry -> entry.id.equals(id))) return;
        ENTRIES.add(new Entry(id, matcher, proxy));
    }

    public static MachineProxy resolve(BlockEntity machine) {
        if (machine != null) {
            for (Entry entry : ENTRIES) {
                if (entry.matcher.test(machine)) return entry.proxy;
            }
        }
        return ForgeCapabilityProxy.INSTANCE;
    }

    public static List<String> registeredIds() {
        List<String> ids = new ArrayList<>();
        for (Entry entry : ENTRIES) ids.add(entry.id);
        return List.copyOf(ids);
    }

    public static void registerModProxy(String modId, MachineProxy proxy) {
        register("mod:" + modId, machine -> {
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE
                    .getKey(machine.getType());
            return key != null && modId.equals(key.getNamespace());
        }, proxy);
    }

    private record Entry(String id, Predicate<BlockEntity> matcher, MachineProxy proxy) {}
}
