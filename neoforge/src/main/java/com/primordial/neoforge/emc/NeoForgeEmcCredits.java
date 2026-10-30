/**
 * 【文件职责】服务端精确收益移交与在线 ProjectE 整数入账；只提供回收入账，不实现消费事务。
 * 【变更记录 #1】2026-10-22 21:05:30（第 1 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：主线程校验、先入世界账本后提交真实在线账户、写失败保留、同步异常隔离和重试。
 * - 修改：credit 的接收成功以世界账本接收为界，后续 API 失败不冒充拒收；离线不写只读代理。
 * - 用途：黑洞队列成功移交后可清零；正常保存可恢复，不宣称玩家文件与世界/方块数据崩溃原子性。
 * 【变更记录 #2】2026-10-22 21:21:18（第 2 次备注 · 2.0.0-neo-dev 入账后通知恢复）
 * - 删除：整数已清账后永远跳过失败通知的提前返回。
 * - 新增：在线轮询独立重试 ProjectE 余额通知，成功后清除会话标记。
 * - 修改：通知重试不修改账户或账本；失败每会话只记录一次。
 * - 用途：网络暂时不可用后可恢复客户端显示，同时避免重复发放已提交收益。
 * 【变更记录 #3】2026-10-23 11:37:42（第 3 次备注 · 2.0.0-neo-dev 双向交易结算）
 * - 删除：只提交正整数收益和独立 FLUSHING 门禁。
 * - 新增：与购买共用账本门禁，按收益减离线借项合并；失败写入检查真实余额，无法确认则隔离。
 * - 修改：已确认写后异常不重付；回调收益保留，通知故障仍仅重试通知。
 * - 用途：登录同时恢复收益和购买支出，避免离线重复消费、嵌套预扣及重复提交。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.logging.LogUtils;
import com.primordial.service.EmcAmount;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class NeoForgeEmcCredits {
    private static final Set<UUID> WRITE_WARNED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SYNC_WARNED = ConcurrentHashMap.newKeySet();

    interface Account {
        BigInteger balance();
        void setBalance(BigInteger amount);
        void sync();
    }

    private NeoForgeEmcCredits() {}

    /** 验证先于写入；账本一旦接收，任何后续 ProjectE 故障均不能使调用方重新投递同一笔。 */
    public static void credit(MinecraftServer server, UUID owner, EmcAmount amount) {
        requireServerThread(server);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(amount);
        if (amount.isZero()) return;
        NeoForgeEmcSavedData.get(server).credit(owner, amount);
        flush(server, owner);
    }

    public static void flush(ServerPlayer player) {
        flush(player.getServer(), player.getUUID());
    }

    private static void flush(MinecraftServer server, UUID owner) {
        requireServerThread(server);
        try {
            NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
            if (data.pending(owner).floor().signum() == 0 && data.debit(owner).signum() == 0
                    && !SYNC_WARNED.contains(owner)) return;
            Account account = NeoForgeProjectEAccount.online(server, owner);
            if (account != null) flushToAccount(data, owner, account);
        } catch (RuntimeException | LinkageError failure) {
            warnWrite(owner, failure);
        }
    }

    /** 写账户失败时仍保留账本；成功后的网络同步单独隔离，不能触发收益重投。 */
    static boolean flushToAccount(NeoForgeEmcSavedData data, UUID owner, Account account) {
        if (!data.begin(owner)) return false;
        try {
            return flushHeld(data, owner, account);
        } finally {
            data.end(owner);
        }
    }

    /** 调用者已持有同一账户事务；确认使用旧的整数快照，不能清除回调新增的收益。 */
    static boolean flushHeld(NeoForgeEmcSavedData data, UUID owner, Account account) {
        if (!data.busy(owner) || !data.canCredit(owner)) return false;
        BigInteger whole = data.pending(owner).floor();
        BigInteger debt = data.debit(owner);
        BigInteger delta = whole.subtract(debt);
        if (delta.signum() == 0) {
            if (whole.signum() > 0) data.acknowledgeNet(owner, whole, debt);
            if (SYNC_WARNED.contains(owner)) syncAccount(owner, account);
            return true;
        }
        BigInteger current;
        try {
            current = account.balance();
            if (current.signum() < 0 || current.add(delta).signum() < 0)
                throw new IllegalStateException("ProjectE balance cannot cover the pending debit");
        } catch (RuntimeException | LinkageError failure) {
            warnWrite(owner, failure);
            return false;
        }
        BigInteger target = current.add(delta);
        try {
            account.setBalance(target);
        } catch (RuntimeException | LinkageError failure) {
            warnWrite(owner, failure);
        }
        // 即使 setter 正常返回也确认写入；只读提供者或写后抛错不能造成免费交付或重复提交。
        try {
            BigInteger observed = account.balance();
            if (!target.equals(observed)) {
                if (!current.equals(observed)) data.hold(owner, current, target);
                return false;
            }
        } catch (RuntimeException | LinkageError unreadable) {
            data.hold(owner, current, target);
            warnWrite(owner, unreadable);
            return false;
        }
        data.acknowledgeNet(owner, whole, debt);
        WRITE_WARNED.remove(owner);
        syncAccount(owner, account);
        return true;
    }

    public static BigInteger onlineBalance(ServerPlayer player) {
        requireServerThread(player.getServer());
        return NeoForgeProjectEAccount.onlineBalance(player);
    }

    private static void syncAccount(UUID owner, Account account) {
        try {
            account.sync();
            SYNC_WARNED.remove(owner);
        } catch (RuntimeException | LinkageError failure) {
            if (SYNC_WARNED.add(owner)) LogUtils.getLogger().warn(
                    "[Primordial] EMC settled for {}, but ProjectE client sync failed; notification will retry",
                    owner, failure);
        }
    }

    public static void forget(UUID owner) {
        WRITE_WARNED.remove(owner);
        SYNC_WARNED.remove(owner);
    }

    public static void clearSession() {
        WRITE_WARNED.clear();
        SYNC_WARNED.clear();
    }

    public static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("EMC credit requires the server thread");
        }
    }

    private static void warnWrite(UUID owner, Throwable failure) {
        if (WRITE_WARNED.add(owner)) LogUtils.getLogger().warn(
                "[Primordial] ProjectE EMC write unavailable for {}; pending settlement retained", owner, failure);
    }
}
