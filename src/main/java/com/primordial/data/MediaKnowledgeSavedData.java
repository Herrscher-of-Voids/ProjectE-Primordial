/**
 * 【文件职责】在主世界存档中保存玩家逐种学习的流体和气体注册名。
 * 【变更记录 #1】2026-10-02 16:07:00（第 1 次备注 · 1.2.0-media-learning）
 * - 删除：无。
 * - 新增：按 UUID、介质类型和注册名去重的知识集合，以及 NBT 保存和容错加载。
 * - 修改：无；类别凭证仍由 ProjectE 原生知识管理。
 * - 用途：直接卖入一种介质后，只解锁这一种介质，不让单一流体或气体绕过全部生产流程。
 */
/**
 * 【变更记录 #2】2026-10-02 17:18:00（第 2 次备注 · 1.2.0-media-catalog）
 * - 删除：无。
 * - 新增：玩家逐种知识的只读快照入口。
 * - 修改：目录查询只能拿到副本，不能修改存档内部集合。
 * - 用途：支持转化桌按玩家知识列出可购买介质。
 */
/**
 * 【变更记录 #3】2026-10-02 18:46:06（第 3 次备注 · 1.2.0-media-forgetting）
 * - 删除：无。
 * - 新增：按玩家、类别、注册名移除单种知识，移除空集合并标记存档待保存。
 * - 修改：重复遗忘或空参数不修改存档，允许之后重新卖入学习。
 * - 用途：遗忘不影响其它玩家、另一类介质或 ProjectE 凭证知识。
 */
/**
 * 【变更记录 #4】2026-10-02 18:48:00（第 4 次备注 · 1.2.0-media-forgetting）
 * - 删除：遗忘只移除逐种学习记录的限制。
 * - 新增：按玩家及类别保存显式遗忘集合，允许屏蔽凭证授予的单种资源。
 * - 修改：遗忘建立排除记录；重新学习同种资源清除该记录；NBT 保存与加载覆盖四类集合。
 * - 用途：单种遗忘优先于整类授权，重启后保持，只有再次学习该资源才解除。
 */
package com.primordial.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class MediaKnowledgeSavedData extends SavedData {
    private static final String DATA_NAME = "primordial_media_knowledge";
    private static final String PLAYERS = "players";
    private static final String FLUIDS = "fluids";
    private static final String GASES = "gases";
    private final Map<UUID, Set<ResourceLocation>> fluids = new HashMap<>();
    private final Map<UUID, Set<ResourceLocation>> gases = new HashMap<>();
    private final Map<UUID, Set<ResourceLocation>> forgottenFluids = new HashMap<>();
    private final Map<UUID, Set<ResourceLocation>> forgottenGases = new HashMap<>();

    public static MediaKnowledgeSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MediaKnowledgeSavedData::load, MediaKnowledgeSavedData::new, DATA_NAME);
    }

    public boolean knows(UUID player, boolean gas, ResourceLocation id) {
        return player != null && id != null && table(gas).getOrDefault(player, Set.of()).contains(id);
    }

    public Set<ResourceLocation> knownIds(UUID player, boolean gas) {
        return player == null ? Set.of() : Set.copyOf(table(gas).getOrDefault(player, Set.of()));
    }

    public boolean learn(UUID player, boolean gas, ResourceLocation id) {
        if (player == null || id == null) return false;
        boolean added = table(gas).computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
        boolean restored = remove(forgottenTable(gas), player, id);
        if (added || restored) setDirty();
        return added || restored;
    }

    public boolean forget(UUID player, boolean gas, ResourceLocation id) {
        if (player == null || id == null) return false;
        boolean removed = remove(table(gas), player, id);
        boolean excluded = forgottenTable(gas).computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
        if (removed || excluded) setDirty();
        return removed || excluded;
    }

    public boolean isForgotten(UUID player, boolean gas, ResourceLocation id) {
        return player != null && id != null && forgottenTable(gas).getOrDefault(player, Set.of()).contains(id);
    }

    private static boolean remove(Map<UUID, Set<ResourceLocation>> source, UUID player, ResourceLocation id) {
        Set<ResourceLocation> ids = source.get(player);
        if (ids == null || !ids.remove(id)) return false;
        if (ids.isEmpty()) source.remove(player);
        return true;
    }

    private Map<UUID, Set<ResourceLocation>> forgottenTable(boolean gas) { return gas ? forgottenGases : forgottenFluids; }

    private Map<UUID, Set<ResourceLocation>> table(boolean gas) { return gas ? gases : fluids; }

    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        Set<UUID> ids = new HashSet<>(fluids.keySet());
        ids.addAll(gases.keySet());
        ids.addAll(forgottenFluids.keySet());
        ids.addAll(forgottenGases.keySet());
        for (UUID id : ids) {
            CompoundTag entry = new CompoundTag();
            entry.put(FLUIDS, write(fluids.get(id)));
            entry.put(GASES, write(gases.get(id)));
            entry.put("forgotten_fluids", write(forgottenFluids.get(id)));
            entry.put("forgotten_gases", write(forgottenGases.get(id)));
            players.put(id.toString(), entry);
        }
        tag.put(PLAYERS, players);
        return tag;
    }

    private static ListTag write(Set<ResourceLocation> ids) {
        ListTag list = new ListTag();
        if (ids != null) ids.stream().map(ResourceLocation::toString).sorted()
                .forEach(id -> list.add(StringTag.valueOf(id)));
        return list;
    }

    public static MediaKnowledgeSavedData load(CompoundTag tag) {
        MediaKnowledgeSavedData data = new MediaKnowledgeSavedData();
        CompoundTag players = tag.getCompound(PLAYERS);
        for (String key : players.getAllKeys()) {
            try {
                UUID player = UUID.fromString(key);
                CompoundTag entry = players.getCompound(key);
                read(entry.getList(FLUIDS, Tag.TAG_STRING), data.fluids, player);
                read(entry.getList(GASES, Tag.TAG_STRING), data.gases, player);
                read(entry.getList("forgotten_fluids", Tag.TAG_STRING), data.forgottenFluids, player);
                read(entry.getList("forgotten_gases", Tag.TAG_STRING), data.forgottenGases, player);
            } catch (IllegalArgumentException ignored) {
                // 损坏的玩家键不影响其它知识记录。
            }
        }
        data.setDirty(false);
        return data;
    }

    private static void read(ListTag list, Map<UUID, Set<ResourceLocation>> target, UUID player) {
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) target.computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
        }
    }
}
