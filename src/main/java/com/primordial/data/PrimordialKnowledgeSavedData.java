/**
 * 文件职责：持久化离线玩家待写入 ProjectE 的物品知识。
 *
 * 变更记录 #1（2026-09-14）：新增离线自动学习队列。
 * - 删除：无。
 * - 新增：按 UUID 保存 ItemInfo 集合，支持 NBT 物品去重和世界重启恢复。
 * - 修改：无。
 * - 用途：ProjectE 的离线知识代理是只读快照；机器或黑洞在玩家离线时回收物品，先在本数据中排队，登录后再写入真实 capability。
 */
package com.primordial.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import moze_intel.projecte.api.ItemInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** 离线 ProjectE 知识的持久化队列。 */
public final class PrimordialKnowledgeSavedData extends SavedData {
    private static final String DATA_NAME = "primordial_pending_knowledge";
    private static final String PLAYERS = "players";

    private final Map<UUID, Set<ItemInfo>> pending = new HashMap<>();

    public static PrimordialKnowledgeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PrimordialKnowledgeSavedData::load, PrimordialKnowledgeSavedData::new, DATA_NAME);
    }

    /** 排入一个待学习物品；ItemInfo 的 equals 同时考虑物品和 NBT。 */
    public void queue(UUID player, ItemInfo info) {
        if (player == null || info == null) return;
        if (pending.computeIfAbsent(player, ignored -> new HashSet<>()).add(info)) setDirty();
    }

    /** 取出并删除玩家队列，登录处理失败时由调用方重新排队。 */
    public Set<ItemInfo> take(UUID player) {
        Set<ItemInfo> values = pending.remove(player);
        if (values != null) setDirty();
        return values == null ? Set.of() : new HashSet<>(values);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        pending.forEach((uuid, infos) -> {
            ListTag list = new ListTag();
            for (ItemInfo info : infos) list.add(info.write(new CompoundTag()));
            if (!list.isEmpty()) players.put(uuid.toString(), list);
        });
        tag.put(PLAYERS, players);
        return tag;
    }

    public static PrimordialKnowledgeSavedData load(CompoundTag tag) {
        PrimordialKnowledgeSavedData data = new PrimordialKnowledgeSavedData();
        CompoundTag players = tag.getCompound(PLAYERS);
        for (String key : players.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                ListTag list = players.getList(key, Tag.TAG_COMPOUND);
                Set<ItemInfo> infos = data.pending.computeIfAbsent(uuid, ignored -> new HashSet<>());
                for (int i = 0; i < list.size(); i++) {
                    try {
                        ItemInfo info = ItemInfo.read(list.getCompound(i));
                        if (info != null) infos.add(info);
                    } catch (RuntimeException ignored) {
                        // 单条损坏知识不会阻止其他玩家和条目加载。
                    }
                }
                if (infos.isEmpty()) data.pending.remove(uuid);
            } catch (IllegalArgumentException ignored) {
                // 忽略损坏的 UUID 键。
            }
        }
        data.setDirty(false);
        return data;
    }
}
