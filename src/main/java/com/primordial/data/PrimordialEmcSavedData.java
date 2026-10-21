/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
package com.primordial.data;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 离线玩家 EMC 待同步数据。
 * ProjectE 的玩家 capability 是在线余额的唯一来源；机器在玩家离线时产生的变化暂存于此，
 * 玩家登录后由 EmcService 合并回 ProjectE capability。所有数值以十进制 BigInteger 字符串保存。
 */
public final class PrimordialEmcSavedData extends SavedData {
    private static final String DATA_NAME = "primordial_emc_pending";
    private static final String BALANCES = "balances";
    private final Map<UUID, BigInteger> pending = new HashMap<>();

    public static PrimordialEmcSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PrimordialEmcSavedData::load, PrimordialEmcSavedData::new, DATA_NAME);
    }

    public BigInteger get(UUID player) {
        return pending.getOrDefault(player, BigInteger.ZERO);
    }

    public void add(UUID player, BigInteger delta) {
        if (delta.signum() == 0) return;
        pending.merge(player, delta, BigInteger::add);
        if (pending.get(player).signum() == 0) pending.remove(player);
        setDirty();
    }

    public BigInteger take(UUID player) {
        BigInteger value = pending.remove(player);
        if (value != null) setDirty();
        return value == null ? BigInteger.ZERO : value;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag values = new CompoundTag();
        pending.forEach((id, value) -> values.putString(id.toString(), value.toString()));
        tag.put(BALANCES, values);
        return tag;
    }

    public static PrimordialEmcSavedData load(CompoundTag tag) {
        PrimordialEmcSavedData data = new PrimordialEmcSavedData();
        CompoundTag values = tag.getCompound(BALANCES);
        for (String key : values.getAllKeys()) {
            try {
                BigInteger value = new BigInteger(values.getString(key));
                if (value.signum() != 0) data.pending.put(UUID.fromString(key), value);
            } catch (RuntimeException ignored) {
                // 损坏的单条记录不会阻止其他玩家数据加载。
            }
        }
        return data;
    }
}
