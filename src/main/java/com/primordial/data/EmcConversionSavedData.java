/**
 * 【文件职责】持久化每名玩家不足 1 EMC 的换算找零；不保存或替代 ProjectE 的整数余额。
 * 【变更记录 #1】2026-09-15 14:06:10（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：UUID 对应约分分数的 SavedData、零条目清理和损坏条目隔离。
 * - 修改：不同机器、流体和能源共用玩家找零，拆分传输与合并传输的净费用相同。
 * - 用途：防止每 tick 向上取整重复收费；保存时只写非零分数，机器解绑不丢失找零。
 */
/**
 * 【变更记录 #2】2026-09-15 15:22:01（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：未同步的持久化 Map 访问。
 * - 新增：并发容器与读写/保存同步门禁。
 * - 修改：EmcSettlement 仍在服务器线程持有同一个 data 锁，嵌套退款重入安全。
 * - 用途：避免存档遍历与结算写入冲突；不同玩家仍各自保存一项不足 1 EMC 的找零。
 */
/**
 * 【变更记录 #3】2026-09-15 15:44:04（第 3 次备注 · 1.0.0-emc-service）
 * - 删除：读取时静默丢弃超过 4096 位找零分母的限制。
 * - 新增：分子和分母按 BigInteger 字节数组持久化，兼容原十进制 n/d 字段。
 * - 修改：避免 NBT 字符串长度上限截断不同价格分母累计产生的精确找零。
 * - 用途：本地存档能完整保存运行时生成的分数；外部价格输入仍使用独立的有界解析。
 */
package com.primordial.data;

import com.primordial.service.EmcAmount;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

/** 所有变更由服务端线程执行；无静态世界引用，切换存档不会串用找零。 */
public final class EmcConversionSavedData extends SavedData {
    private final Map<UUID, EmcAmount> change = new java.util.concurrent.ConcurrentHashMap<>();

    public static EmcConversionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                EmcConversionSavedData::load, EmcConversionSavedData::new, "primordial_emc_conversion");
    }

    public synchronized EmcAmount get(UUID owner) { return change.getOrDefault(owner, EmcAmount.ZERO); }

    public synchronized void set(UUID owner, EmcAmount value) {
        if (value.floor().signum() != 0) throw new IllegalArgumentException("Only fractional EMC belongs in conversion data");
        if (get(owner).equals(value)) return;
        if (value.isZero()) change.remove(owner); else change.put(owner, value);
        setDirty();
    }

    @Override public synchronized CompoundTag save(CompoundTag tag) {
        CompoundTag entries = new CompoundTag();
        change.forEach((owner, amount) -> {
            CompoundTag value = new CompoundTag();
            value.putByteArray("n_bytes", amount.numerator().toByteArray());
            value.putByteArray("d_bytes", amount.denominator().toByteArray());
            entries.put(owner.toString(), value);
        });
        tag.put("change", entries);
        return tag;
    }

    public static EmcConversionSavedData load(CompoundTag tag) {
        EmcConversionSavedData data = new EmcConversionSavedData();
        CompoundTag entries = tag.getCompound("change");
        for (String id : entries.getAllKeys()) {
            try {
                CompoundTag value = entries.getCompound(id);
                BigInteger n = value.contains("n_bytes", net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)
                        ? new BigInteger(value.getByteArray("n_bytes")) : new BigInteger(value.getString("n"));
                BigInteger d = value.contains("d_bytes", net.minecraft.nbt.Tag.TAG_BYTE_ARRAY)
                        ? new BigInteger(value.getByteArray("d_bytes")) : new BigInteger(value.getString("d"));
                EmcAmount amount = new EmcAmount(n, d);
                if (!amount.isZero() && amount.floor().signum() == 0) data.change.put(UUID.fromString(id), amount);
            } catch (RuntimeException invalid) {
                com.primordial.PrimordialMod.LOGGER.warn("[Primordial] Ignoring invalid EMC conversion entry {}", id);
            }
        }
        return data;
    }
}
