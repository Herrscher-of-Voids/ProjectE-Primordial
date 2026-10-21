/**
 * 【文件职责】将精确资源价值结算到 ProjectE 整数余额和不足 1 EMC 的持久化找零。
 * 【变更记录 #1】2026-09-15 14:06:10（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：购买前预留、按真实接受量退款、回收累计和超大余额可负担数量裁剪。
 * - 修改：同一玩家的不同传输共用找零；模拟不写余额，零价资源拒绝购买。
 * - 用途：20 FE × 50 次只消耗 1 EMC；失败返回零时全额恢复，第三方抛异常时不盲目退款造成复制。
 */
/**
 * 【变更记录 #2】2026-09-15 14:48:20（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：将整数回收与找零写入拆给调用方的 splitCredit 接口。
 * - 新增：服务端线程校验、同一世界结算锁和清零找零入口。
 * - 修改：统一预扣及部分退款，回调异常保留预扣并向上报告，避免已交付资源再次获全额退款。
 * - 用途：EmcService 唯一调用本结算器；机器、背包和黑洞共用每玩家余数，回收时一并提交整数。
 */
/**
 * 【变更记录 #3】2026-09-18 01:06:25（第 3 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：无；原购买入口继续返回实际交付数量。
 * - 新增：购买回执，记录实际数量及本笔整数预扣减退款，不混入回调中的其它交易。
 * - 修改：退款内部返回实际返还的整数 EMC；分数找零、主线程校验及异常预扣规则沿用原事务。
 * - 用途：加速器使用精确分数单价并统计真实扣款，不用账户前后差值猜测加速费用。
 */
package com.primordial.service;

import com.primordial.data.EmcConversionSavedData;
import net.minecraft.server.MinecraftServer;
import java.math.BigInteger;
import java.util.UUID;
import java.util.function.LongUnaryOperator;

/** 必须在服务端线程内完整执行；transfer 返回实际数量，不得异步修改库存。 */
public final class EmcSettlement {
    private EmcSettlement() {}

    public static long purchase(MinecraftServer server, UUID owner, EmcAmount price, long requested, LongUnaryOperator transfer) {
        return purchaseWithReceipt(server, owner, price, requested, transfer).units();
    }

    /** charged 只记录本次整数收支；嵌套交易改变找零时，退款可能使其为负。 */
    public record Purchase(long units, BigInteger charged) {
        private static final Purchase EMPTY = new Purchase(0, BigInteger.ZERO);
    }

    public static Purchase purchaseWithReceipt(MinecraftServer server, UUID owner, EmcAmount price,
                                               long requested, LongUnaryOperator transfer) {
        if (server == null || owner == null || requested <= 0 || price.isZero()) return Purchase.EMPTY;
        requireServerThread(server);
        EmcConversionSavedData data = EmcConversionSavedData.get(server);
        synchronized (data) {
        EmcAmount change = data.get(owner);
        BigInteger balance = EmcService.balance(server, owner);
        long units = EmcAmount.of(balance).add(change).affordableUnits(price, requested);
        if (units == 0) return Purchase.EMPTY;
        EmcAmount cost = price.multiply(units);
        // 先用总额补齐为整数，再减成本；等价于 ceil(cost - change)，且不构造负分数。
        BigInteger payment = cost.ceil();
        EmcAmount remainder = change.add(EmcAmount.of(payment)).subtract(cost);
        BigInteger excess = remainder.floor();
        payment = payment.subtract(excess);
        remainder = remainder.fraction();
        if (payment.signum() > 0 && !EmcService.spend(server, owner, payment)) return Purchase.EMPTY;
        data.set(owner, remainder);
        long actual = Math.max(0L, Math.min(units, transfer.applyAsLong(units)));
        BigInteger refund = actual < units ? creditWhole(server, owner, price.multiply(units - actual)) : BigInteger.ZERO;
        return new Purchase(actual, payment.subtract(refund));
        }
    }

    /** 回收和退款共用此入口；在同一临界区完成整数入账和找零更新。 */
    public static void credit(MinecraftServer server, UUID owner, EmcAmount amount) {
        creditWhole(server, owner, amount);
    }

    private static BigInteger creditWhole(MinecraftServer server, UUID owner, EmcAmount amount) {
        if (server == null || owner == null || amount.isZero()) return BigInteger.ZERO;
        requireServerThread(server);
        EmcConversionSavedData data = EmcConversionSavedData.get(server);
        synchronized (data) {
        EmcAmount total = data.get(owner).add(amount);
        BigInteger whole = total.floor();
        if (whole.signum() > 0) EmcService.add(server, owner, whole);
        data.set(owner, total.fraction());
        return whole;
        }
    }

    public static void clear(MinecraftServer server, UUID owner) {
        requireServerThread(server);
        EmcConversionSavedData data = EmcConversionSavedData.get(server);
        synchronized (data) {
            BigInteger whole = EmcService.balance(server, owner);
            if (EmcService.spend(server, owner, whole)) data.set(owner, EmcAmount.ZERO);
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("EMC settlement requires the server thread");
    }
}
