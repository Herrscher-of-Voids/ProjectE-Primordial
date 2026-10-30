/**
 * 【文件职责】公共精确 EMC 购买/回收事务；按同步回调的实际资源数量结算。
 * 【变更记录 #1】2026-10-23 11:39:45（第 1 次备注 · 2.0.0-neo-dev 公共交易结算）
 * - 删除：无。
 * - 新增：只读可负担报价、持久预扣、部分交付退款、离线借项、回执及同账户重入隔离。
 * - 修改：在线预扣确认后才交付；离线仅记世界借项。异常或非法回调保留预扣，不猜测已交付量。
 * - 用途：后续网络/配置器共用实际数量计费，找零与黑洞收益共用账本；不提供资源资格或 UI。
 * 【变更记录 #2】2026-10-23 11:52:42（第 2 次备注 · 2.0.0-neo-dev 交易回执边界）
 * - 删除：用退款新增整额推断费用，混入尚未提交的旧整数收益。
 * - 新增：按起始找零与本笔实际成本计算整数费用。
 * - 修改：charged 仅记录本笔消耗的有效整数额，不包括旧收益合并及回调新增收益；零交付费用为零。
 * - 用途：后续机器预算/统计不把历史收益当成本笔退款，也不受回调内其它收入影响。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongUnaryOperator;

import com.primordial.service.EmcAmount;

import net.minecraft.server.MinecraftServer;

public final class NeoForgeEmcTransactions {
    public record Purchase(long units, BigInteger charged) {
        private static final Purchase EMPTY = new Purchase(0, BigInteger.ZERO);
    }

    private NeoForgeEmcTransactions() {}

    /** Exact read-only spendable balance, including change and pending offline debits. */
    public static EmcAmount balance(MinecraftServer server, UUID owner) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null) return EmcAmount.ZERO;
        NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
        if (!data.canCredit(owner) || data.busy(owner)) return EmcAmount.ZERO;
        try { return available(server, data, owner); }
        catch (RuntimeException | LinkageError unavailable) { return EmcAmount.ZERO; }
    }

    /** 不合并收益、不写 ProjectE、不扣额度；交易回调中同账户查询返回零。 */
    public static long affordable(MinecraftServer server, UUID owner, EmcAmount price, long requested) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || price == null || price.isZero() || requested <= 0) return 0;
        NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
        if (!data.canCredit(owner) || data.busy(owner)) return 0;
        try {
            return available(server, data, owner).affordableUnits(price, requested);
        } catch (RuntimeException | LinkageError unavailable) {
            return 0;
        }
    }

    public static long purchase(MinecraftServer server, UUID owner, EmcAmount price,
                                long requested, LongUnaryOperator transfer) {
        return purchaseWithReceipt(server, owner, price, requested, transfer).units();
    }

    /** 回调必须同步且返回 0..授权数量；抛错时交付是否发生未知，保留预扣并向调用者报告。 */
    public static Purchase purchaseWithReceipt(MinecraftServer server, UUID owner, EmcAmount price,
                                               long requested, LongUnaryOperator transfer) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || price == null || price.isZero() || requested <= 0) return Purchase.EMPTY;
        Objects.requireNonNull(transfer);
        NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
        if (!data.begin(owner)) return Purchase.EMPTY;
        try {
            long units;
            NeoForgeEmcCredits.Account online;
            try {
                units = available(server, data, owner).affordableUnits(price, requested);
                online = NeoForgeProjectEAccount.online(server, owner);
                if (server.getPlayerList().getPlayer(owner) != null && online == null) return Purchase.EMPTY;
            } catch (RuntimeException | LinkageError unavailable) {
                return Purchase.EMPTY;
            }
            if (units == 0) return Purchase.EMPTY;
            EmcAmount initialChange = data.pending(owner).fraction();
            EmcAmount cost = price.multiply(units);
            data.reserve(owner, cost);
            if (online != null && !NeoForgeEmcCredits.flushHeld(data, owner, online)) {
                // 尚未调用交付，可安全退还全部预留；不确定写入隔离后必须保留现场。
                if (data.canCredit(owner)) data.credit(owner, cost);
                return Purchase.EMPTY;
            }
            long actual = checkedActual(transfer.applyAsLong(units), units);
            if (actual < units) {
                data.credit(owner, price.multiply(units - actual));
            }
            flushOnlineHeld(server, data, owner);
            EmcAmount actualCost = price.multiply(actual);
            BigInteger charged = actualCost.isZero() || initialChange.affordableUnits(actualCost, 1) == 1
                    ? BigInteger.ZERO : actualCost.subtract(initialChange).ceil();
            return new Purchase(actual, charged);
        } finally {
            data.end(owner);
        }
    }

    /** 账本预检早于外部抽取；实际数量非法时不凭空入账，异常不能被误当成可重试的零交付。 */
    public static long reclaim(MinecraftServer server, UUID owner, EmcAmount price,
                               long requested, LongUnaryOperator extract) {
        NeoForgeEmcCredits.requireServerThread(server);
        if (owner == null || price == null || price.isZero() || requested <= 0) return 0;
        Objects.requireNonNull(extract);
        NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
        if (!data.begin(owner)) return 0;
        try {
            long actual = checkedActual(extract.applyAsLong(requested), requested);
            if (actual == 0) return 0;
            data.credit(owner, price.multiply(actual));
            // 与黑洞相同，以账本接收为成功边界，通知和写账户失败不能令来源重复投递。
            try {
                NeoForgeEmcCredits.Account online = NeoForgeProjectEAccount.online(server, owner);
                if (online != null) NeoForgeEmcCredits.flushHeld(data, owner, online);
            } catch (RuntimeException | LinkageError unavailable) {
                // 精确收益已持久入队，由登录/十 tick 轮询重试。
            }
            return actual;
        } finally {
            data.end(owner);
        }
    }

    private static EmcAmount available(MinecraftServer server, NeoForgeEmcSavedData data, UUID owner) {
        BigInteger base = NeoForgeProjectEAccount.baseBalance(server, owner);
        EmcAmount pending = data.pending(owner);
        BigInteger whole = base.add(pending.floor()).subtract(data.debit(owner));
        if (whole.signum() < 0) return EmcAmount.ZERO;
        return EmcAmount.of(whole).add(pending.fraction());
    }

    private static void flushOnlineHeld(MinecraftServer server, NeoForgeEmcSavedData data, UUID owner) {
        try {
            NeoForgeEmcCredits.Account online = NeoForgeProjectEAccount.online(server, owner);
            if (online != null) NeoForgeEmcCredits.flushHeld(data, owner, online);
        } catch (RuntimeException | LinkageError unavailable) {
            // 回调可能已退出/换掉玩家；退款留在账本，不把已交付资源报告为零。
        }
    }

    private static long checkedActual(long actual, long authorized) {
        if (actual < 0 || actual > authorized)
            throw new IllegalStateException("Resource callback returned an amount outside its authorization");
        return actual;
    }
}
