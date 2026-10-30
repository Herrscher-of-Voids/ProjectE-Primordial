/**
 * 【文件职责】通过公共交易入口和真实 ProjectE 玩家账户验收精确预扣、实际量退款及离线借项。
 * 【变更记录 #1】2026-10-23 11:41:05（第 1 次备注 · 2.0.0-neo-dev 公共资源交易）
 * - 删除：无。
 * - 新增：只读报价、分数退款、小额累计、离线购买/重登、重入、非法回调、线程和大数九项测试。
 * - 修改：交易数量使用受控回调；余额、保存、登录和同步仍经过真实 PlayerList 与 ProjectE 能力。
 * - 用途：锁定公共结算契约，不把账户前后差值冒充实体资源交付，也不宣称异常断电跨文件原子性。
 * 【变更记录 #2】2026-10-23 11:42:55（第 2 次备注 · 2.0.0-neo-dev 账户写后故障）
 * - 删除：无。
 * - 新增：写后抛错的精确确认，以及不确定回读/回读失败的持久隔离测试。
 * - 修改：故障仅由账户替身控制，仍执行生产 flushToAccount 与真实账本 NBT 编解码。
 * - 用途：保证已提交收益不重放，无法判定的账户不继续自动交易且保留原始证据。
 * 【变更记录 #3】2026-10-23 11:45:26（第 3 次备注 · 2.0.0-neo-dev 玩家账户代际）
 * - 删除：无。新增：交付回调内同 UUID 退出并重新登录的退款测试。
 * - 修改：退款断言同时检查新玩家、旧玩家和世界账本，禁止写过期附件后清账。
 * - 用途：验证实际 PlayerList 代际变化不会丢失已确认的未交付资源退款。
 * 【变更记录 #4】2026-10-23 11:59:26（第 4 次备注 · 2.0.0-neo-dev 整数费用回执）
 * - 删除：无。新增：在线部分/零交付及离线旧整数收益下的 charged 费用断言。
 * - 修改：在既有测试内验证费用只扣本笔实际成本减起始找零，不混入旧收益或整额退款。
 * - 用途：固定非负有效整数资金费用语义，保留原有十二项测试和资源数量断言。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.service.EmcAmount;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEmcTransactionGameTests {
    private NeoForgeEmcTransactionGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void quotesWithoutChangingBalanceLedgerOrCallingTransfers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, profile());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.TEN);
            NeoForgeEmcCredits.credit(server, owner, fraction(1, 2));
            CompoundTag before = snapshot(helper);
            AtomicInteger transfers = new AtomicInteger();
            for (int attempt = 0; attempt < 3; attempt++) {
                check(NeoForgeEmcTransactions.affordable(server, owner, fraction(1, 3), 100) == 31,
                        "Quote must include the exact half EMC without reserving or rounding it");
            }
            check(NeoForgeEmcTransactions.affordable(server, owner, EmcAmount.ZERO, 100) == 0,
                    "A zero price must not authorize unlimited free output");
            check(NeoForgeEmcTransactions.purchase(server, owner, EmcAmount.ONE, 0, units -> {
                transfers.incrementAndGet();
                return units;
            }) == 0, "An empty purchase must do no work");
            check(NeoForgeEmcTransactions.reclaim(server, owner, EmcAmount.ZERO, 5, units -> {
                transfers.incrementAndGet();
                return units;
            }) == 0, "Unpriced input must remain untouched");
            check(transfers.get() == 0 && knowledge(player).getEmc().equals(BigInteger.TEN)
                    && before.equals(snapshot(helper)), "Read-only and rejected operations must mutate nothing");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void refundsOnlyUndeliveredUnitsAndFullyRefundsZeroDelivery(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, profile());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.TEN);
            NeoForgeEmcCredits.credit(server, owner, fraction(1, 2));
            var receipt = NeoForgeEmcTransactions.purchaseWithReceipt(server, owner, fraction(1, 3), 30, units -> {
                check(units == 30, "Transfer must receive only the affordable pre-authorized quantity");
                return 7;
            });
            check(receipt.units() == 7 && receipt.charged().equals(BigInteger.TWO)
                    && knowledge(player).getEmc().equals(BigInteger.valueOf(8))
                    && ledger(server).pending(owner).equals(fraction(1, 6)),
                    "Seven delivered thirds must leave exactly eight EMC and one sixth");
            CompoundTag before = snapshot(helper);
            var cancelled = NeoForgeEmcTransactions.purchaseWithReceipt(server, owner, fraction(1, 3), 3, units -> 0);
            check(cancelled.units() == 0 && cancelled.charged().signum() == 0,
                    "A cancelled delivery must report zero actual units and zero integer charge");
            check(knowledge(player).getEmc().equals(BigInteger.valueOf(8)) && before.equals(snapshot(helper)),
                    "A zero delivery must restore both the integer balance and exact ledger remainder");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void combinesFiftyTinyPurchasesIntoExactlyOneEmc(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, profile());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.TWO);
            EmcAmount price = fraction(1, 1000);
            for (int attempt = 0; attempt < 50; attempt++) {
                check(NeoForgeEmcTransactions.purchase(server, owner, price, 20, units -> units) == 20,
                        "Each twenty-unit transfer must remain affordable and exact");
            }
            check(knowledge(player).getEmc().equals(BigInteger.ONE) && ledger(server).pending(owner).isZero(),
                    "Fifty purchases of twenty FE at one EMC per thousand FE must consume exactly one EMC");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void spendsUnflushedOfflineWholeCreditAndCreditsOnlyActualExtraction(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        check(server.getPlayerList().getPlayer(owner) == null, "The ledger-only test owner must be offline");
        NeoForgeEmcCredits.credit(server, owner, fraction(5, 2));
        var cancelled = NeoForgeEmcTransactions.purchaseWithReceipt(server, owner, EmcAmount.ONE, 2, units -> 0);
        check(cancelled.units() == 0 && cancelled.charged().signum() == 0
                && ledger(server).pending(owner).equals(fraction(5, 2)),
                "Full cancellation funded by old whole credit must not report a negative integer charge");
        check(NeoForgeEmcTransactions.affordable(server, owner, fraction(1, 3), 100) == 7,
                "Unflushed whole credit must remain spendable alongside its fraction");
        var partial = NeoForgeEmcTransactions.purchaseWithReceipt(server, owner, fraction(1, 3), 100, units -> 4);
        check(partial.units() == 4 && partial.charged().equals(BigInteger.ONE)
                && ledger(server).pending(owner).equals(fraction(7, 6)),
                "Four delivered thirds must consume one effective integer EMC and retain seven sixths");
        check(NeoForgeEmcTransactions.purchase(server, owner, fraction(1, 3), 3, units -> units) == 3
                && ledger(server).pending(owner).equals(fraction(1, 6)),
                "Three additional thirds must leave exactly one sixth EMC");
        check(NeoForgeEmcTransactions.reclaim(server, owner, fraction(2, 3), 11, units -> {
            check(units == 11, "The extraction callback must receive the authorized upper bound");
            return 4;
        }) == 4 && ledger(server).pending(owner).equals(fraction(17, 6)),
                "Only four actually extracted units may earn eight thirds EMC");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void persistsOfflinePurchaseAndAppliesDebtOnlyOnceAcrossRealRelogins(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = profile();
        ServerPlayer original = login(server, profile);
        ServerPlayer current = null;
        try {
            UUID owner = original.getUUID();
            knowledge(original).setEmc(BigInteger.TEN);
            logout(server, original);
            NeoForgeEmcCredits.credit(server, owner, fraction(1, 2));
            check(NeoForgeEmcTransactions.purchase(server, owner, fraction(2, 3), 9, units -> 4) == 4,
                    "An offline account must support partial delivery from its saved ProjectE balance");
            check(knowledge(original).getEmc().equals(BigInteger.TEN),
                    "Offline purchasing must not modify the logged-out player object or immutable snapshot");
            check(NeoForgeEmcTransactions.affordable(server, owner, fraction(1, 6), 100) == 47,
                    "Pending offline debt and refund must leave exactly forty-seven sixths EMC");
            current = login(server, profile);
            check(knowledge(current).getEmc().equals(BigInteger.valueOf(7))
                    && ledger(server).pending(owner).equals(fraction(5, 6)),
                    "Real login must apply net integer debt and retain the exact five-sixths remainder");
            NeoForgeEmcCredits.flush(current);
            logout(server, current);
            current = null;
            current = login(server, profile);
            check(knowledge(current).getEmc().equals(BigInteger.valueOf(7))
                    && ledger(server).pending(owner).equals(fraction(5, 6)),
                    "A second disk-backed login must not repeat already acknowledged purchase debt");
        } finally {
            logout(server, current);
            logout(server, original);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rejectsSameOwnerReentryWhilePreservingCreditAndAllowingOtherOwners(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, profile());
        ServerPlayer other = login(server, profile());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.valueOf(100));
            knowledge(other).setEmc(BigInteger.valueOf(5));
            AtomicInteger nestedTransfers = new AtomicInteger();
            long moved = NeoForgeEmcTransactions.purchase(server, owner, EmcAmount.ONE, 10, units -> {
                check(NeoForgeEmcTransactions.purchase(server, owner, EmcAmount.ONE, 1, nested -> {
                    nestedTransfers.incrementAndGet();
                    return nested;
                }) == 0, "Reentrant purchase for the same owner must reject before delivery");
                check(NeoForgeEmcTransactions.reclaim(server, owner, EmcAmount.ONE, 1, nested -> {
                    nestedTransfers.incrementAndGet();
                    return nested;
                }) == 0, "Reentrant reclamation for the same owner must reject before extraction");
                NeoForgeEmcCredits.credit(server, owner, fraction(7, 3));
                NeoForgeEmcCredits.flush(player);
                check(knowledge(player).getEmc().equals(BigInteger.valueOf(90)),
                        "Busy-account flush must not commit newly queued credit during delivery");
                check(NeoForgeEmcTransactions.purchase(server, other.getUUID(), EmcAmount.ONE, 1,
                        nested -> nested) == 1, "An independent owner must remain able to trade");
                return 4;
            });
            check(moved == 4 && nestedTransfers.get() == 0
                    && knowledge(player).getEmc().equals(BigInteger.valueOf(98))
                    && ledger(server).pending(owner).equals(fraction(1, 3))
                    && knowledge(other).getEmc().equals(BigInteger.valueOf(4)),
                    "Final refund must preserve reentrant earned credit without duplicated spending or lost change");
        } finally {
            logout(server, other);
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void retainsReservationAfterThrowingNegativeOrOversizedDelivery(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, profile());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.valueOf(100));
            EmcAmount price = EmcAmount.of(BigInteger.valueOf(3));
            expectRuntime(() -> NeoForgeEmcTransactions.purchase(server, owner, price, 2, units -> {
                throw new IllegalStateException("Expected delivery failure with unknown external mutation");
            }), "A throwing callback must report an uncertain delivery");
            check(knowledge(player).getEmc().equals(BigInteger.valueOf(94)),
                    "Unknown delivery must keep its six EMC reservation instead of granting a blind refund");
            expectRuntime(() -> NeoForgeEmcTransactions.purchase(server, owner, price, 2, units -> -1),
                    "A negative delivery count must be rejected as a contract violation");
            expectRuntime(() -> NeoForgeEmcTransactions.purchase(server, owner, price, 2, units -> units + 1),
                    "Delivery above the authorized amount must be rejected as a contract violation");
            check(knowledge(player).getEmc().equals(BigInteger.valueOf(82)) && ledger(server).pending(owner).isZero(),
                    "All three uncertain deliveries must retain their own complete reservations");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rejectsCrossThreadQuotePurchaseAndReclamationBeforeCallbacks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, profile());
        try {
            UUID owner = player.getUUID();
            knowledge(player).setEmc(BigInteger.valueOf(11));
            NeoForgeEmcCredits.credit(server, owner, fraction(1, 7));
            CompoundTag before = snapshot(helper);
            AtomicInteger callbacks = new AtomicInteger();
            List<Runnable> operations = List.of(
                    () -> NeoForgeEmcTransactions.affordable(server, owner, EmcAmount.ONE, 1),
                    () -> NeoForgeEmcTransactions.purchase(server, owner, EmcAmount.ONE, 1, units -> {
                        callbacks.incrementAndGet();
                        return units;
                    }),
                    () -> NeoForgeEmcTransactions.reclaim(server, owner, EmcAmount.ONE, 1, units -> {
                        callbacks.incrementAndGet();
                        return units;
                    }));
            AtomicInteger rejected = new AtomicInteger();
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                for (Runnable operation : operations) {
                    try {
                        operation.run();
                    } catch (IllegalStateException expected) {
                        rejected.incrementAndGet();
                    } catch (Throwable failure) {
                        unexpected.set(failure);
                    }
                }
            }, "primordial-gametest-invalid-transaction-thread");
            worker.setDaemon(true);
            worker.start();
            try {
                worker.join(5000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new GameTestAssertException("Interrupted while checking transaction thread rejection");
            }
            check(!worker.isAlive() && unexpected.get() == null && rejected.get() == 3,
                    "Every cross-thread entry point must reject without waiting for a server task");
            check(callbacks.get() == 0 && knowledge(player).getEmc().equals(BigInteger.valueOf(11))
                    && before.equals(snapshot(helper)), "Thread rejection must precede transfer and account mutation");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void clipsHugeBalancesBeforeConvertingAffordableQuantityToLong(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, profile());
        try {
            UUID owner = player.getUUID();
            BigInteger initial = BigInteger.TEN.pow(100).add(BigInteger.valueOf(73));
            knowledge(player).setEmc(initial);
            check(NeoForgeEmcTransactions.affordable(server, owner, EmcAmount.ONE, Long.MAX_VALUE) == Long.MAX_VALUE,
                    "A huge balance must clamp before converting resource quantity to long");
            long actual = Long.MAX_VALUE - 1;
            check(NeoForgeEmcTransactions.purchase(server, owner, EmcAmount.ONE, Long.MAX_VALUE,
                    units -> actual) == actual, "A huge authorized quantity must retain its exact partial result");
            check(knowledge(player).getEmc().equals(initial.subtract(BigInteger.valueOf(actual)))
                    && ledger(server).pending(owner).isZero(),
                    "Precharge and the one-unit refund must not overflow or truncate the native BigInteger balance");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void acknowledgesExactlyMatchingBalanceAfterSetterMutatesThenThrows(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
        data.credit(owner, fraction(11, 2));
        FaultAccount account = new FaultAccount(BigInteger.valueOf(100));
        account.throwAfterWrite = true;
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account),
                "A setter that throws after reaching the exact target must be confirmed by readback");
        check(account.value.equals(BigInteger.valueOf(105)) && account.writes == 1
                && data.pending(owner).equals(fraction(1, 2)) && data.canCredit(owner),
                "An acknowledged post-write failure must preserve only the fractional remainder");
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account) && account.writes == 1,
                "The confirmed integer write must not repeat on the next flush");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void persistsUncertainWriteIsolationAndKeepsHealthyOwnersWritable(GameTestHelper helper) {
        for (boolean unreadable : new boolean[]{false, true}) {
            UUID owner = UUID.randomUUID();
            NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
            data.credit(owner, fraction(11, 2));
            FaultAccount account = new FaultAccount(BigInteger.valueOf(100));
            account.throwAfterWrite = true;
            account.unreadableAfterWrite = unreadable;
            if (!unreadable) account.unexpectedTarget = BigInteger.valueOf(102);
            check(!NeoForgeEmcCredits.flushToAccount(data, owner, account) && !data.canCredit(owner),
                    "Unknown balance or unreadable write outcome must block the affected account");
            check(data.pending(owner).equals(fraction(11, 2)) && account.writes == 1,
                    "An uncertain write must retain all unacknowledged credit and stop resubmission");
            CompoundTag saved = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            NeoForgeEmcSavedData restored = NeoForgeEmcSavedData.load(saved, helper.getLevel().registryAccess());
            check(!restored.canCredit(owner)
                    && saved.equals(restored.save(new CompoundTag(), helper.getLevel().registryAccess())),
                    "The uncertainty evidence and unacknowledged amount must survive normal NBT roundtrip");
            expectRuntime(() -> restored.credit(owner, EmcAmount.ONE),
                    "An unresolved account must reject new credit instead of overwriting evidence");
            check(!NeoForgeEmcCredits.flushToAccount(restored, owner, account) && account.writes == 1,
                    "Automatic settlement must remain blocked after restoring an uncertain write");
            UUID healthy = UUID.randomUUID();
            FaultAccount healthyAccount = new FaultAccount(BigInteger.ZERO);
            restored.credit(healthy, EmcAmount.ONE);
            check(NeoForgeEmcCredits.flushToAccount(restored, healthy, healthyAccount)
                    && healthyAccount.value.equals(BigInteger.ONE),
                    "An unresolved neighbour must not prevent a healthy account from settling");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void refundsCurrentPlayerAfterDeliveryCallbackLogsOutAndRelogsSameOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = profile();
        ServerPlayer original = login(server, profile);
        AtomicReference<ServerPlayer> current = new AtomicReference<>();
        try {
            UUID owner = original.getUUID();
            knowledge(original).setEmc(BigInteger.TEN);
            long actual = NeoForgeEmcTransactions.purchase(server, owner, EmcAmount.of(BigInteger.TWO), 3, units -> {
                logout(server, original);
                current.set(login(server, profile));
                return 1;
            });
            check(actual == 1 && current.get() != original
                    && server.getPlayerList().getPlayer(owner) == current.get(),
                    "Delivery must retain its actual quantity across a real PlayerList generation change");
            check(knowledge(current.get()).getEmc().equals(BigInteger.valueOf(8))
                    && knowledge(original).getEmc().equals(BigInteger.valueOf(4))
                    && ledger(server).pending(owner).isZero() && ledger(server).debit(owner).signum() == 0,
                    "Refund must reach the current player's restored account while the old attachment remains untouched");
        } finally {
            logout(server, current.get());
            logout(server, original);
        }
        helper.succeed();
    }

    private static final class FaultAccount implements NeoForgeEmcCredits.Account {
        private BigInteger value;
        private BigInteger unexpectedTarget;
        private boolean throwAfterWrite;
        private boolean unreadableAfterWrite;
        private int writes;

        private FaultAccount(BigInteger initial) { value = initial; }
        @Override public BigInteger balance() {
            if (unreadableAfterWrite && writes > 0) throw new IllegalStateException("Expected unknown readback");
            return value;
        }
        @Override public void setBalance(BigInteger amount) {
            value = unexpectedTarget == null ? amount : unexpectedTarget;
            writes++;
            if (throwAfterWrite) throw new IllegalStateException("Expected failure after account mutation");
        }
        @Override public void sync() {}
    }

    private static GameProfile profile() { return new GameProfile(UUID.randomUUID(), "emc-trade-test"); }
    private static NeoForgeEmcSavedData ledger(MinecraftServer server) { return NeoForgeEmcSavedData.get(server); }
    private static CompoundTag snapshot(GameTestHelper helper) {
        return ledger(helper.getLevel().getServer()).save(new CompoundTag(), helper.getLevel().registryAccess());
    }
    private static EmcAmount fraction(long numerator, long denominator) {
        return new EmcAmount(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    /** 使用真实登录/退出事件和玩家文件，避免用仅存在于内存中的替身冒充离线结算。 */
    private static ServerPlayer login(MinecraftServer server, GameProfile profile) {
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(),
                false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            try { logout(server, player); }
            finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        try {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel) {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static IKnowledgeProvider knowledge(ServerPlayer player) {
        IKnowledgeProvider result = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(result != null, "The installed ProjectE mod must provide its real knowledge capability");
        return result;
    }

    private static void expectRuntime(Runnable operation, String message) {
        try { operation.run(); }
        catch (RuntimeException expected) { return; }
        throw new GameTestAssertException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
