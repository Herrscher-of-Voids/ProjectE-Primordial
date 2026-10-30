/**
 * 【文件职责】在主世界保存各玩家尚未写入 ProjectE 的精确收益，整数与不足一 EMC 的余数同档保存。
 * 【变更记录 #1】2026-10-22 21:05:30（第 1 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：UUID 到 EmcAmount 的世界账本、整数确认扣除、零项清理及损坏条目原样隔离。
 * - 修改：离线只累计账本，在线成功 setEmc 后才确认整数；每次状态变化标记 setDirty。
 * - 用途：跨机器共享玩家余数，避免小额回收归零；不提供消费/退款或跨存档迁移。
 * 【变更记录 #2】2026-10-22 21:44:20（第 2 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：只读 canCredit 预检。
 * - 修改：资源接收可提前识别未知格式和隔离账户。
 * - 用途：账本不能接收收益时保留外部资源。
 * 【变更记录 #3】2026-10-23 11:37:42（第 3 次备注 · 2.0.0-neo-dev 公共交易结算）
 * - 删除：账本仅能保存正收益的限制；保留既有 credits 和 schema 1。
 * - 新增：离线整数借项、同账户事务门禁、精确预留、净额确认及不确定写入的持久隔离。
 * - 修改：debits/unresolved 为可选区段，损坏子项保留原文并关闭该账户，坏根仍整档只读。
 * - 用途：购买不修改 ProjectE 离线快照，登录只合并一次净额；收益重入不能覆盖找零或重复消费。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import com.primordial.service.EmcAmount;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class NeoForgeEmcSavedData extends SavedData {
    private static final String DATA_NAME = "primordial_emc_credits";
    private static final Factory<NeoForgeEmcSavedData> FACTORY =
            new Factory<>(NeoForgeEmcSavedData::new, NeoForgeEmcSavedData::load);
    private final Map<UUID, EmcAmount> credits = new ConcurrentHashMap<>();
    private final Map<UUID, BigInteger> debits = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> busy = ConcurrentHashMap.newKeySet();
    private final CompoundTag quarantined = new CompoundTag();
    private final CompoundTag quarantinedDebits = new CompoundTag();
    private final CompoundTag unresolved = new CompoundTag();
    private CompoundTag blockedSnapshot;

    public static NeoForgeEmcSavedData get(MinecraftServer server) {
        NeoForgeEmcCredits.requireServerThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public synchronized EmcAmount pending(UUID owner) {
        return credits.getOrDefault(Objects.requireNonNull(owner), EmcAmount.ZERO);
    }

    public synchronized boolean canCredit(UUID owner) {
        String id = Objects.requireNonNull(owner).toString();
        return blockedSnapshot == null && !quarantined.contains(id)
                && !quarantinedDebits.contains(id) && !unresolved.contains(id);
    }

    public synchronized boolean canReclaim(UUID owner) {
        return canCredit(owner) && !busy(owner);
    }

    public synchronized BigInteger debit(UUID owner) {
        return debits.getOrDefault(Objects.requireNonNull(owner), BigInteger.ZERO);
    }

    synchronized boolean begin(UUID owner) {
        return canCredit(owner) && busy.add(owner);
    }

    synchronized boolean busy(UUID owner) { return busy.contains(owner); }

    synchronized void end(UUID owner) { busy.remove(owner); }

    /** 只改变账本，不调用外部 API；同一账户预留后，收益仍可追加但不能嵌套消费。 */
    synchronized BigInteger reserve(UUID owner, EmcAmount cost) {
        requireAccount(owner);
        if (!busy(owner)) throw new IllegalStateException("Reservation requires an account transaction");
        EmcAmount pending = pending(owner);
        BigInteger payment = BigInteger.ZERO;
        EmcAmount next;
        if (pending.affordableUnits(cost, 1) == 1) next = pending.subtract(cost);
        else {
            EmcAmount remaining = cost.subtract(pending);
            payment = remaining.ceil();
            next = EmcAmount.of(payment).subtract(remaining);
        }
        setPending(owner, next);
        if (payment.signum() > 0) debits.put(owner, debit(owner).add(payment));
        setDirty();
        return payment;
    }

    synchronized void acknowledgeNet(UUID owner, BigInteger wholeCredit, BigInteger wholeDebit) {
        requireAccount(owner);
        if (wholeCredit.signum() > 0) setPending(owner, pending(owner).subtract(EmcAmount.of(wholeCredit)));
        BigInteger left = debit(owner).subtract(wholeDebit);
        if (left.signum() < 0) throw new IllegalStateException("Debit acknowledgement exceeds reservation");
        if (left.signum() == 0) debits.remove(owner);
        else debits.put(owner, left);
        setDirty();
    }

    synchronized void hold(UUID owner, BigInteger before, BigInteger target) {
        CompoundTag evidence = new CompoundTag();
        evidence.putByteArray("before", before.toByteArray());
        evidence.putByteArray("target", target.toByteArray());
        evidence.putString("reason", "Account write result could not be confirmed; automatic settlement blocked");
        unresolved.put(owner.toString(), evidence);
        setDirty();
    }

    private void setPending(UUID owner, EmcAmount amount) {
        if (amount.isZero()) credits.remove(owner);
        else credits.put(owner, amount);
    }

    private void requireAccount(UUID owner) {
        requireWritable();
        if (!canCredit(owner)) throw new IllegalStateException("EMC account is quarantined: " + owner);
    }

    synchronized void credit(UUID owner, EmcAmount amount) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(amount);
        requireAccount(owner);
        if (amount.isZero()) return;
        credits.put(owner, pending(owner).add(amount));
        setDirty();
    }

    synchronized void acknowledge(UUID owner, BigInteger whole) {
        requireWritable();
        if (whole.signum() <= 0) throw new IllegalArgumentException("Expected positive integer credit");
        // 从最新余额扣除本次已入账整数，不能以旧快照覆盖重入期间的新收益。
        EmcAmount left = pending(owner).subtract(EmcAmount.of(whole));
        if (left.isZero()) credits.remove(owner);
        else credits.put(owner, left);
        setDirty();
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (blockedSnapshot != null) return blockedSnapshot.copy();
        CompoundTag entries = quarantined.copy();
        credits.forEach((owner, amount) -> entries.put(owner.toString(), NeoForgeEmcNbt.write(amount)));
        tag.putInt("schema", 1);
        tag.put("credits", entries);
        CompoundTag debts = quarantinedDebits.copy();
        debits.forEach((owner, amount) -> debts.putByteArray(owner.toString(), amount.toByteArray()));
        if (!debts.isEmpty()) tag.put("debits", debts);
        else tag.remove("debits");
        if (!unresolved.isEmpty()) tag.put("unresolved", unresolved.copy());
        else tag.remove("unresolved");
        return tag;
    }

    private void requireWritable() {
        if (blockedSnapshot != null) throw new IllegalStateException("EMC ledger schema is unavailable; original data preserved");
    }

    public static NeoForgeEmcSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
        // SavedData 加载器会吞掉异常并重新建档，因此未知格式必须原样保留并禁止写入。
        if (!tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1
                || !tag.contains("credits", Tag.TAG_COMPOUND)
                || tag.contains("debits") && !tag.contains("debits", Tag.TAG_COMPOUND)
                || tag.contains("unresolved") && !tag.contains("unresolved", Tag.TAG_COMPOUND)) {
            data.blockedSnapshot = tag.copy();
            LogUtils.getLogger().error("[Primordial] Unsupported EMC ledger; preserved original data and blocked credits");
            return data;
        }
        CompoundTag entries = tag.getCompound("credits");
        for (String id : entries.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(id);
                if (!owner.toString().equals(id)) throw new IllegalArgumentException("Expected canonical UUID");
                if (!entries.contains(id, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Expected EMC compound");
                EmcAmount amount = NeoForgeEmcNbt.read(entries.getCompound(id));
                if (!amount.isZero()) data.credits.put(owner, amount);
            } catch (RuntimeException invalid) {
                data.quarantined.put(id, Objects.requireNonNull(entries.get(id)).copy());
                LogUtils.getLogger().warn("[Primordial] Preserving invalid EMC credit entry {} without settling it", id);
            }
        }
        CompoundTag debts = tag.getCompound("debits");
        for (String id : debts.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(id);
                if (!owner.toString().equals(id) || !debts.contains(id, Tag.TAG_BYTE_ARRAY))
                    throw new IllegalArgumentException("Invalid debit entry");
                BigInteger amount = new BigInteger(debts.getByteArray(id));
                if (amount.signum() < 0) throw new IllegalArgumentException("Negative debit");
                if (amount.signum() > 0) data.debits.put(owner, amount);
            } catch (RuntimeException invalid) {
                data.quarantinedDebits.put(id, Objects.requireNonNull(debts.get(id)).copy());
                LogUtils.getLogger().warn("[Primordial] Preserving invalid EMC debit entry {} without settling it", id);
            }
        }
        data.unresolved.merge(tag.getCompound("unresolved").copy());
        return data;
    }
}
