/**
 * 【文件职责】增量发现范围内的可加速方块，按位置黑白名单和空闲策略轮转调度。
 * 【变更记录 #1】2026-09-18 01:36:30（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：最多 64 个弱引用范围缓存、每范围每 tick 最多 64 次渐进扫描、20 tick 重扫及位置名单。
 * - 修改：已发现目标逐个走统一付费工作入口；重叠位置不叠加，未知工作状态不猜测空闲。
 * - 用途：15×15×15 范围不做每 tick 全扫描；100 tick 持续空闲时降至最多 4 倍，按实际档位收费。
 */
/**
 * 【变更记录 #2】2026-09-18 02:30:44（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：64 KiB NBT 内存估算上限，与默认 64 个绑定相冲突的限制。
 * - 新增：工具 NBT 统一限制为 1 MiB，新增绑定或位置名单后超限回滚。
 * - 修改：目标数量仍受配置和 256 硬上限保护，网络只同步无位置数组的有界摘要。
 * - 用途：保证默认数量可以绑定，同时避免多份大型范围名单无限扩大物品数据。
 */
package com.primordial.service;

import java.lang.ref.WeakReference;
import java.util.*;
import com.primordial.item.EmcAcceleratorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

final class AccelerationArea {
    static final int MAX_MARKS = 3375;
    static final int MAX_TOOL_BYTES = 1048576;
    private static final Set<String> ACTIVITY_PROPERTIES = Set.of("lit", "active", "working", "running", "crafting");
    private static final Map<UUID, Cache> CACHES = new LinkedHashMap<>();
    private AccelerationArea() { }

    static boolean valid(ServerLevel level, CompoundTag entry) {
        if (level == null) return false;
        BlockPos anchor = AccelerationBindings.pos(entry);
        var settings = AccelerationSettings.read(entry);
        var min = settings.minimum(anchor);
        var max = min.offset(settings.sizeX() - 1, settings.sizeY() - 1, settings.sizeZ() - 1);
        return level.hasChunkAt(anchor) && level.shouldTickBlocksAt(anchor) && min.getY() >= level.getMinBuildHeight()
                && max.getY() < level.getMaxBuildHeight() && level.getWorldBorder().isWithinBounds(min)
                && level.getWorldBorder().isWithinBounds(max);
    }

    static void process(MinecraftServer server, ServerPlayer player, ItemStack tool, CompoundTag entry, AccelerationBudget budget) {
        ServerLevel level = AccelerationTargets.level(server, entry);
        if (!valid(level, entry)) return;
        UUID id = AccelerationBindings.id(entry);
        var settings = AccelerationSettings.read(entry);
        Cache cache = CACHES.get(id);
        if (cache == null || cache.tool.get() != tool || !cache.settings.equals(settings)) {
            if (CACHES.size() >= 64) CACHES.remove(CACHES.keySet().iterator().next());
            cache = new Cache(tool, settings);
            CACHES.put(id, cache);
        }
        int now = server.getTickCount();
        cache.lastUse = now;
        BlockPos anchor = AccelerationBindings.pos(entry);
        if (now >= cache.nextScan) {
            for (int scanned = 0; scanned < 64 && budget.scan(); scanned++) {
                BlockPos pos = settings.position(anchor, cache.scan++);
                if (allows(entry, pos, false) && AccelerationTargets.block(level, pos) != null) {
                    cache.found.add(pos);
                    if (cache.firstPass) cache.targets.add(pos);
                }
                if (cache.scan >= settings.volume()) {
                    cache.targets = new ArrayList<>(cache.found);
                    cache.idleSince.keySet().retainAll(cache.found);
                    cache.found.clear(); cache.scan = 0; cache.firstPass = false; cache.nextScan = now + 20;
                    break;
                }
            }
        }
        for (int inspected = 0; inspected < cache.targets.size() && budget.acceptsTarget(); inspected++) {
            if (!AccelerationBindings.enabled(entry)) break;
            cache.cursor = Math.floorMod(cache.cursor, cache.targets.size());
            BlockPos pos = cache.targets.get(cache.cursor++);
            if (!allows(entry, pos, false)) continue;
            AccelerationTarget target = AccelerationTargets.block(level, pos);
            if (target == null) continue;
            int rate = effectiveRate(cache, entry, target, now);
            EmcAccelerationService.execute(server, player, tool, entry, budget, AccelerationTargets.work(target), rate);
        }
    }

    private static int effectiveRate(Cache cache, CompoundTag entry, AccelerationTarget target, int now) {
        if (!cache.settings.idleThrottle() || !allows(entry, target.pos(), true) || !knownIdle(target)) {
            cache.idleSince.remove(target.pos());
            return cache.settings.rate();
        }
        int since = cache.idleSince.computeIfAbsent(target.pos(), pos -> now);
        return now - since >= 100 ? Math.min(4, cache.settings.rate()) : cache.settings.rate();
    }

    private static boolean knownIdle(AccelerationTarget target) {
        if (target.ticker() instanceof AccelerationMachineCompat.CheckedTicker ticker) {
            try { return ticker.idle(); }
            catch (RuntimeException | LinkageError unavailable) { return false; }
        }
        var state = target.level().getBlockState(target.pos());
        boolean known = false;
        for (var property : state.getProperties()) {
            if (property instanceof BooleanProperty value && ACTIVITY_PROPERTIES.contains(value.getName())) {
                known = true;
                if (state.getValue(value)) return false;
            }
        }
        return known;
    }

    static boolean allows(CompoundTag entry, BlockPos pos, boolean idle) {
        String prefix = idle ? "Idle" : "Area";
        long[] marks = entry.getLongArray(prefix + "Marks");
        if (marks.length > MAX_MARKS) return false;
        boolean listed = Arrays.binarySearch(marks, pos.asLong()) >= 0;
        return entry.getBoolean(prefix + "Whitelist") == listed;
    }

    /** 标记操作来自服务端实际右键位置；同类匹配最多检查一个有界范围，不加载其它区块。 */
    static boolean mark(ItemStack tool, ServerPlayer player, ServerLevel level, BlockPos pos, boolean sameType) {
        CompoundTag root = tool.getOrCreateTag();
        if (!root.hasUUID("EditingArea")) return false;
        CompoundTag entry = AccelerationBindings.find(tool, root.getUUID("EditingArea"));
        if (entry == null || AccelerationSettings.kind(entry) != AccelerationSettings.Kind.AREA
                || !entry.getString(EmcAcceleratorItem.DIMENSION_KEY).equals(level.dimension().location().toString())) return false;
        var settings = AccelerationSettings.read(entry);
        BlockPos anchor = AccelerationBindings.pos(entry);
        if (!settings.bounds(anchor).contains(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) || !level.hasChunkAt(pos)) return false;
        boolean idle = AccelerationSettings.kind(root) == AccelerationSettings.Kind.IDLE_FILTER;
        String key = idle ? "IdleMarks" : "AreaMarks";
        long[] previous = entry.getLongArray(key);
        if (previous.length > MAX_MARKS) return false;
        var marks = new TreeSet<Long>();
        for (long value : previous) marks.add(value);
        boolean remove = marks.contains(pos.asLong());
        var selectedBlock = level.getBlockState(pos).getBlock();
        int count = sameType ? settings.volume() : 1;
        for (int i = 0; i < count; i++) {
            BlockPos candidate = sameType ? settings.position(anchor, i) : pos;
            if (!level.hasChunkAt(candidate) || level.getBlockState(candidate).getBlock() != selectedBlock) continue;
            if (remove) marks.remove(candidate.asLong()); else marks.add(candidate.asLong());
        }
        entry.putLongArray(key, marks.stream().mapToLong(Long::longValue).toArray());
        if (root.sizeInBytes() > MAX_TOOL_BYTES) { entry.putLongArray(key, previous); return false; }
        AccelerationConfirmation.cancel(player.getUUID());
        AccelerationBindings.pause(entry);
        invalidate(AccelerationBindings.id(entry));
        return true;
    }

    static void invalidate(UUID id) { CACHES.remove(id); }
    static void clear() { CACHES.clear(); }
    static void expire(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0)
            CACHES.values().removeIf(cache -> cache.tool.get() == null || server.getTickCount() - cache.lastUse > 200);
    }

    private static final class Cache {
        final WeakReference<ItemStack> tool;
        final AccelerationSettings settings;
        final Set<BlockPos> found = new LinkedHashSet<>();
        final Map<BlockPos, Integer> idleSince = new HashMap<>();
        List<BlockPos> targets = new ArrayList<>();
        int scan, cursor, nextScan, lastUse;
        boolean firstPass = true;
        Cache(ItemStack tool, AccelerationSettings settings) { this.tool = new WeakReference<>(tool); this.settings = settings; }
    }
}
