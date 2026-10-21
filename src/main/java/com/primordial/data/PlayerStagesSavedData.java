/**
 * 【文件职责】在主世界存档按玩家 UUID 保存任务阶段，供命令和离线机器查询。
 * 【变更记录 #1】2026-10-02 19:05:26（第 1 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：阶段增删、只读快照、NBT 往返及损坏记录跳过；阶段名限制 128 字符。
 * - 修改：无；阶段与 ProjectE 知识独立保存，死亡、换维度和离线不清除。
 * - 用途：任务奖励由服务端命令授予阶段，重复操作不标脏，玩家之间互不影响。
 */
package com.primordial.data;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlayerStagesSavedData extends SavedData {
    private final Map<UUID, Set<ResourceLocation>> players = new ConcurrentHashMap<>();

    public static PlayerStagesSavedData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Stages require the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(
                PlayerStagesSavedData::load, PlayerStagesSavedData::new, "primordial_player_stages");
    }

    public static boolean validStage(ResourceLocation stage) {
        return stage != null && stage.toString().length() <= 128 && !stage.getPath().isEmpty();
    }

    public synchronized boolean has(UUID player, ResourceLocation stage) {
        return player != null && validStage(stage) && players.getOrDefault(player, Set.of()).contains(stage);
    }

    public synchronized Set<ResourceLocation> stages(UUID player) {
        return player == null ? Set.of() : Set.copyOf(players.getOrDefault(player, Set.of()));
    }

    public synchronized boolean grant(UUID player, ResourceLocation stage) {
        if (player == null || !validStage(stage)) return false;
        boolean changed = players.computeIfAbsent(player, ignored -> ConcurrentHashMap.newKeySet()).add(stage);
        if (changed) setDirty();
        return changed;
    }

    public synchronized boolean revoke(UUID player, ResourceLocation stage) {
        if (player == null || !validStage(stage)) return false;
        Set<ResourceLocation> values = players.get(player);
        if (values == null || !values.remove(stage)) return false;
        if (values.isEmpty()) players.remove(player);
        setDirty();
        return true;
    }

    @Override public synchronized CompoundTag save(CompoundTag tag) {
        CompoundTag entries = new CompoundTag();
        players.forEach((player, stages) -> {
            ListTag list = new ListTag();
            stages.stream().sorted().forEach(stage -> list.add(StringTag.valueOf(stage.toString())));
            entries.put(player.toString(), list);
        });
        tag.put("players", entries);
        return tag;
    }

    public static PlayerStagesSavedData load(CompoundTag tag) {
        var data = new PlayerStagesSavedData();
        CompoundTag entries = tag.getCompound("players");
        for (String key : entries.getAllKeys()) {
            try {
                UUID player = UUID.fromString(key);
                ListTag list = entries.getList(key, Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    String text = list.getString(i);
                    if (text.length() <= 128) data.grant(player, ResourceLocation.tryParse(text));
                }
            } catch (IllegalArgumentException ignored) {
                // 损坏的玩家键不影响其它玩家阶段。
            }
        }
        data.setDirty(false);
        return data;
    }
}
