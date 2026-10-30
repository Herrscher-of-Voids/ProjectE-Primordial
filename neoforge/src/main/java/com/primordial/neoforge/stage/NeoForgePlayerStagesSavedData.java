/**
 * 【文件职责】按玩家 UUID 持久保存个人任务阶段，为离线机器、管理员命令及可信奖励提供同一账本。
 * 【变更记录 #1】2026-10-23 16:19:58（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无。
 * - 新增：schema 1 阶段集合、幂等增删、不可变快照及逐玩家进程内变更序号；阶段完整 ID 最长 128 字符。
 * - 修改：根格式错误保留整份原始 NBT 并停止授权；坏玩家条目原文隔离且仅封锁该 UUID，健康玩家继续操作。
 * - 用途：个人阶段独立于知识、余额和团队；真实变更推进序号以识别报价回调中的撤销再授予，不读取 Forge 世界。
 */
package com.primordial.neoforge.stage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class NeoForgePlayerStagesSavedData extends SavedData {
    private static final String DATA_NAME = "primordial_player_stages";
    private static final Factory<NeoForgePlayerStagesSavedData> FACTORY = new Factory<>(
            NeoForgePlayerStagesSavedData::new, NeoForgePlayerStagesSavedData::load);
    private final Map<UUID, Set<ResourceLocation>> players = new ConcurrentHashMap<>();
    private final Map<UUID, Long> revisions = new ConcurrentHashMap<>();
    private final Set<UUID> blockedOwners = ConcurrentHashMap.newKeySet();
    private final CompoundTag quarantined = new CompoundTag();
    private CompoundTag blockedSnapshot;

    public static NeoForgePlayerStagesSavedData get(MinecraftServer server) {
        NeoForgeEmcCredits.requireServerThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static boolean validStage(ResourceLocation stage) {
        return stage != null && !stage.getPath().isEmpty() && stage.toString().length() <= 128;
    }

    public synchronized boolean available() {
        return blockedSnapshot == null;
    }

    public synchronized boolean available(UUID owner) {
        return owner != null && available() && !blockedOwners.contains(owner);
    }

    /** 序号仅标识本次进程内真实修改；坏档返回 -1，幂等查询或重复命令不会推进。 */
    public synchronized long revision(UUID owner) {
        return available(owner) ? revisions.getOrDefault(owner, 0L) : -1L;
    }

    public synchronized boolean has(UUID owner, ResourceLocation stage) {
        return available(owner) && validStage(stage) && players.getOrDefault(owner, Set.of()).contains(stage);
    }

    public synchronized Set<ResourceLocation> stages(UUID owner) {
        return available(owner) ? Set.copyOf(players.getOrDefault(owner, Set.of())) : Set.of();
    }

    public synchronized boolean grant(UUID owner, ResourceLocation stage) {
        if (!available(owner) || !validStage(stage)) return false;
        boolean changed = players.computeIfAbsent(owner, ignored -> ConcurrentHashMap.newKeySet()).add(stage);
        if (changed) changed(owner);
        return changed;
    }

    public synchronized boolean revoke(UUID owner, ResourceLocation stage) {
        if (!available(owner) || !validStage(stage)) return false;
        Set<ResourceLocation> values = players.get(owner);
        if (values == null || !values.remove(stage)) return false;
        if (values.isEmpty()) players.remove(owner);
        changed(owner);
        return true;
    }

    private void changed(UUID owner) {
        revisions.merge(owner, 1L, Long::sum);
        setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!available()) return blockedSnapshot.copy();
        CompoundTag entries = new CompoundTag();
        players.forEach((owner, stages) -> {
            ListTag values = new ListTag();
            stages.stream().map(ResourceLocation::toString).sorted().forEach(stage -> values.add(StringTag.valueOf(stage)));
            if (!values.isEmpty()) entries.put(owner.toString(), values);
        });
        tag.putInt("schema", 1);
        tag.put("players", entries);
        ListTag blocked = new ListTag();
        blockedOwners.stream().map(UUID::toString).sorted().forEach(owner -> blocked.add(StringTag.valueOf(owner)));
        if (!blocked.isEmpty()) tag.put("blocked_owners", blocked);
        if (!quarantined.isEmpty()) tag.put("quarantine", quarantined.copy());
        return tag;
    }

    public static NeoForgePlayerStagesSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NeoForgePlayerStagesSavedData data = new NeoForgePlayerStagesSavedData();
        // SavedData 不能抛错后被加载器替换为空档；未知根作为只读快照原样保存。
        if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1
                || !tag.contains("players", Tag.TAG_COMPOUND)
                || tag.contains("blocked_owners") && !tag.contains("blocked_owners", Tag.TAG_LIST)
                || tag.contains("quarantine") && !tag.contains("quarantine", Tag.TAG_COMPOUND)) {
            return data.blockRoot(tag);
        }
        if (tag.contains("quarantine")) data.quarantined.merge(tag.getCompound("quarantine").copy());
        if (tag.contains("blocked_owners")) {
            for (Tag entry : (ListTag) tag.get("blocked_owners")) {
                UUID owner = entry instanceof StringTag ? canonicalOwner(entry.getAsString()) : null;
                if (owner == null) return data.blockRoot(tag);
                data.blockedOwners.add(owner);
            }
        }
        CompoundTag entries = tag.getCompound("players");
        for (String key : entries.getAllKeys()) {
            Tag raw = entries.get(key);
            UUID owner = canonicalOwner(key);
            Set<ResourceLocation> stages = parseStages(raw);
            if (owner == null || stages == null) {
                data.quarantine("players/" + key, raw);
                // 可辨识的非标准 UUID 写法也封锁其规范 UUID，避免别名碰撞恢复授权。
                try { data.blockedOwners.add(UUID.fromString(key)); }
                catch (IllegalArgumentException ignored) { /* 无法识别的所有者原文保留，不影响健康玩家。 */ }
                continue;
            }
            if (!stages.isEmpty()) data.players.put(owner, stages);
        }
        data.setDirty(false);
        return data;
    }

    private NeoForgePlayerStagesSavedData blockRoot(CompoundTag original) {
        blockedSnapshot = original.copy();
        LogUtils.getLogger().error("[Primordial] Unsupported personal stage ledger; original data preserved, stage authorization blocked");
        return this;
    }

    private static UUID canonicalOwner(String value) {
        try {
            UUID owner = UUID.fromString(value);
            return owner.toString().equals(value) ? owner : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Set<ResourceLocation> parseStages(Tag raw) {
        if (!(raw instanceof ListTag list)) return null;
        Set<ResourceLocation> stages = ConcurrentHashMap.newKeySet();
        for (Tag entry : list) {
            if (!(entry instanceof StringTag)) return null;
            ResourceLocation stage = ResourceLocation.tryParse(entry.getAsString());
            if (!validStage(stage)) return null;
            stages.add(stage);
        }
        return stages;
    }

    private void quarantine(String path, Tag raw) {
        String key = path;
        for (int suffix = 1; quarantined.contains(key); suffix++) key = path + "#" + suffix;
        if (raw != null) quarantined.put(key, raw.copy());
        LogUtils.getLogger().warn("[Primordial] Preserving invalid personal stage entry {}; affected owner remains locked", path);
    }
}
