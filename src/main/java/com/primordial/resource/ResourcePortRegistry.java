/**
 * 【文件职责】维护可选模组资源适配器，并提供安全的端口发现入口。
 *
 * 变更记录 #1（2026-09-16）：新增按模组 ID 注册的资源端口表；查询逐项捕获运行时异常
 * 和 LinkageError，第三方 API 缺失时只跳过该适配器。注册发生在 common setup，查询发生
 * 在服务端线程，因此不会在每个 tick 修改注册表结构。
 */
/**
 * 【变更记录 #2】2026-09-16（资源端口诊断）
 * - 新增：适配器命中但没有可读写端口时的单次 DEBUG 诊断，记录实际方块实体类型和方向。
 * - 修改：诊断使用线程安全集合去重，不在任务执行的每个 tick 重复构造日志或刷屏。
 * - 用途：第三方 API 版本变化时可以区分“没有匹配适配器”和“匹配后缺少完整读写接口”。
 */
package com.primordial.resource;

import com.primordial.PrimordialMod;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 第三方原生资源端口注册表。 */
public final class ResourcePortRegistry {
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final Set<String> EMPTY_DIAGNOSTICS = ConcurrentHashMap.newKeySet();

    private ResourcePortRegistry() {}

    public static synchronized void register(String id, Predicate<BlockEntity> matcher,
            ResourcePortProvider provider) {
        if (id == null || matcher == null || provider == null) throw new NullPointerException("resource adapter");
        if (ENTRIES.stream().noneMatch(entry -> entry.id().equals(id))) ENTRIES.add(new Entry(id, matcher, provider));
    }

    /** 按方块实体和指定方向发现端口；返回不可变列表，调用者可以直接遍历。 */
    public static List<EmcResourcePort> find(BlockEntity machine, Direction side) {
        if (machine == null || machine.isRemoved()) return List.of();
        List<EmcResourcePort> result = new ArrayList<>();
        for (Entry entry : ENTRIES) {
            try {
                if (entry.matcher().test(machine)) {
                    List<EmcResourcePort> ports = entry.provider().find(machine, side);
                    if (ports != null) for (EmcResourcePort port : ports) if (port != null) result.add(port);
                    if ((ports == null || ports.isEmpty())
                            && EMPTY_DIAGNOSTICS.add(entry.id() + "|" + machine.getClass().getName() + "|" + side)) {
                        PrimordialMod.LOGGER.debug("[Primordial] resource adapter {} matched {} but exposed no usable port",
                                entry.id(), machine.getClass().getName());
                    }
                }
            } catch (RuntimeException | LinkageError failure) {
                PrimordialMod.LOGGER.debug("[Primordial] resource adapter {} unavailable for {}",
                        entry.id(), machine.getClass().getName(), failure);
            }
        }
        return List.copyOf(result);
    }

    public static List<String> registeredIds() {
        return ENTRIES.stream().map(Entry::id).toList();
    }

    private record Entry(String id, Predicate<BlockEntity> matcher, ResourcePortProvider provider) {}
}
