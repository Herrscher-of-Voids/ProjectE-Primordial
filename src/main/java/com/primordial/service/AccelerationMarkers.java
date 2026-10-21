/**
 * 【文件职责】从实际加速执行记录生成客户端附近目标快照。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无；新增专职快照模块。
 * - 新增：最多2048条近期位置，5 tick清理；每玩家只同步64格内最近256个目标。
 * - 修改：变化立即按批次发包，稳定时20 tick心跳；引用失效、暂停、超时或10 tick未执行清除。
 * - 用途：避免逐帧或全背包全目标扫描；退出服务器清理缓存，不保留世界对象。
 */
/**
 * 【变更记录 #2】2026-09-18 01:44:57（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：移动实体按旧位置不断创建方块标记的可能路径。
 * - 新增：按维度与实体编号更新同一标记；范围成员仍使用方块位置键。
 * - 修改：只有实际付费执行的目标才进入快照，全局时钟不显示方块倍率。
 * - 用途：同一 2048 条服务端缓存同时覆盖方块和实体，客户端最多接收附近 256 条。
 */
package com.primordial.service;

import com.primordial.network.S2CAccelerationMarkersPacket;
import com.primordial.network.S2CAccelerationMarkersPacket.Marker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.lang.ref.WeakReference;
import java.util.*;

/** 仅服务端线程记录实际执行的位置；共享有界快照，不扫描玩家全部目标或世界方块。 */
final class AccelerationMarkers {
    private static final int MAX_TRACKED = 2048;
    private static final Map<Object, Active> ACTIVE = new LinkedHashMap<>();
    private static final Map<UUID, List<Marker>> SENT = new HashMap<>();
    private record Active(Marker marker, int tick, WeakReference<ItemStack> tool, CompoundTag entry) { }
    private AccelerationMarkers() { }
    private record EntityKey(net.minecraft.resources.ResourceLocation dimension, int id) { }
    static void record(MinecraftServer server, ItemStack tool, CompoundTag entry, ServerLevel level, BlockPos pos, int rate, int entityId) {
        Object key = entityId >= 0 ? new EntityKey(level.dimension().location(), entityId) : GlobalPos.of(level.dimension(), pos);
        if (ACTIVE.size() < MAX_TRACKED || ACTIVE.containsKey(key)) ACTIVE.put(key,
                new Active(new Marker(level.dimension().location(), pos.immutable(), rate, entityId), server.getTickCount(),
                        new WeakReference<>(tool), entry));
    }
    static void synchronize(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % 5 != 0) return;
        ACTIVE.values().removeIf(active -> {
            ItemStack tool = active.tool().get();
            return tick - active.tick() > 10 || tool == null || !AccelerationBindings.enabled(active.entry())
                    || AccelerationRun.expired(tool, server);
        });
        var players = server.getPlayerList().getPlayers();
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (var player : players) {
            var nearby = ACTIVE.values().stream().map(Active::marker)
                    .filter(marker -> marker.dimension().equals(player.level().dimension().location())
                            && marker.position().distToCenterSqr(player.position()) <= 64D * 64D)
                    .sorted(Comparator.comparingDouble(marker -> marker.position().distToCenterSqr(player.position())))
                    .limit(S2CAccelerationMarkersPacket.MAX_MARKERS).toList();
            if (!nearby.equals(SENT.getOrDefault(player.getUUID(), List.of())) || !nearby.isEmpty() && tick % 20 == 0) {
                S2CAccelerationMarkersPacket.send(player, nearby);
                SENT.put(player.getUUID(), nearby);
            }
        }
    }
    static void clear() { ACTIVE.clear(); SENT.clear(); }
}
