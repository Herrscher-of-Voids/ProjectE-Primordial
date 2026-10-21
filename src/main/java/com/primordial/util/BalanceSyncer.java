/**
 * 变更记录 #3（2026-09-13）：统一通过 EmcService 门面访问余额，并完成 ProjectE/Curios API 审计。
 * - 新增：能量换算配置键 energy_to_emc_rate 与 emc_to_energy_rate。
 * - 修改：业务层不再直接调用 ProjectECompat，保证后续适配器共享同一 EMC 结算规则。
 */
/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 【变更记录 #4】2026-09-16 14:41:00（第 4 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：无；余额仍每 10 tick 合并同步。
 * - 新增：实际加速净扣款累加，与原生余额在同一网络包发送，并在离线和停服时清理。
 * - 修改：同步明细使用结算后的扣款，预算退款和未执行任务不会虚报消费。
 * - 用途：极大余额的紧凑显示可能不变，独立明细让玩家能核对真实支出且不增加逐目标网络包。
 */
package com.primordial.util;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CSyncBalancePacket;

/** 余额变更节流同步：markDirty 收集，每 10 tick flush 给在线玩家 */
public final class BalanceSyncer {
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static final java.util.Map<UUID, java.math.BigInteger> ACCELERATION_CHARGES = new java.util.HashMap<>();

    private BalanceSyncer() {}

    public static void markDirty(UUID playerId) {
        synchronized (DIRTY) {
            DIRTY.add(playerId);
        }
    }

    public static void recordAccelerationCharge(UUID playerId, java.math.BigInteger amount) {
        if (amount.signum() <= 0) return;
        synchronized (DIRTY) {
            ACCELERATION_CHARGES.merge(playerId, amount, java.math.BigInteger::add);
            DIRTY.add(playerId);
        }
    }

    public static void clear(UUID playerId) {
        synchronized (DIRTY) { DIRTY.remove(playerId); ACCELERATION_CHARGES.remove(playerId); }
    }

    public static void clearAll() {
        synchronized (DIRTY) { DIRTY.clear(); ACCELERATION_CHARGES.clear(); }
    }

    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        Set<UUID> batch;
        java.util.Map<UUID, java.math.BigInteger> charges;
        synchronized (DIRTY) {
            if (DIRTY.isEmpty()) return;
            batch = new HashSet<>(DIRTY);
            charges = new java.util.HashMap<>(ACCELERATION_CHARGES);
            DIRTY.clear();
            ACCELERATION_CHARGES.clear();
        }
        for (UUID id : batch) {
            java.math.BigInteger emc = com.primordial.service.EmcService.balance(server, id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CSyncBalancePacket(emc, charges.getOrDefault(id, java.math.BigInteger.ZERO)));
            }
        }
    }
}
