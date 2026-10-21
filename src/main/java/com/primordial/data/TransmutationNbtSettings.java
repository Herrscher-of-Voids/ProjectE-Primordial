/**
 * 【文件职责】保存玩家转化桌 NBT 学习偏好。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：按 UUID 存储三个选项及 NBT 读写。
 * - 修改：沿用 ProjectE 知识、价格和同步，设置仅影响之后的学习。
 * - 用途：保留物品变体，阻止容器内容进入可重复生成的知识；不改变历史知识。
 */
package com.primordial.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class TransmutationNbtSettings extends SavedData {
    public static final int DEFAULT = 7;
    private final Map<UUID, Integer> settings = new ConcurrentHashMap<>();
    public static TransmutationNbtSettings get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                TransmutationNbtSettings::load, TransmutationNbtSettings::new, "primordial_nbt_settings");
    }
    public int flags(UUID player) { return settings.getOrDefault(player, DEFAULT); }
    public void set(UUID player, int flags) {
        if (player == null || flags < 0 || flags > 7) throw new IllegalArgumentException("Invalid NBT settings");
        if (flags(player) != flags) { settings.put(player, flags); setDirty(); }
    }
    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag players = new CompoundTag();
        settings.forEach((id, flags) -> players.putInt(id.toString(), flags));
        tag.put("players", players);
        return tag;
    }
    public static TransmutationNbtSettings load(CompoundTag tag) {
        var data = new TransmutationNbtSettings();
        var players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            try {
                int flags = players.getInt(key);
                if (players.contains(key, 3) && flags >= 0 && flags <= 7) data.settings.put(UUID.fromString(key), flags);
            } catch (IllegalArgumentException ignored) { }
        }
        return data;
    }
}
