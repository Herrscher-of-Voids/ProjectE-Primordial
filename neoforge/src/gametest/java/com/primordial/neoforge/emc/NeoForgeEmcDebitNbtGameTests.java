/**
 * 【文件职责】验证离线借项扩展的 schema 1 兼容、损坏原文隔离及整数净额确认。
 * 【变更记录 #1】2026-10-23 11:50:02（第 1 次备注 · 2.0.0-neo-dev 借项存档边界）
 * - 删除：无。
 * - 新增：旧账本兼容、合法预留往返、损坏借项、坏根只读、不确定写入与未写入重试四项测试。
 * - 修改：通过生产 SavedData/flushToAccount 验证，账户替身仅控制写前抛错和静默不写。
 * - 用途：坏数据不被零金额覆盖，健康玩家仍可结算；不把普通 NBT 往返称作跨 JVM 重启。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.util.UUID;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.service.EmcAmount;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEmcDebitNbtGameTests {
    private NeoForgeEmcDebitNbtGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void readsLegacySchemaOneAndPreservesReservationAndNetAcknowledgement(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        CompoundTag legacy = root();
        legacy.getCompound("credits").put(owner.toString(), NeoForgeEmcNbt.write(fraction(1, 2)));
        NeoForgeEmcSavedData data = load(helper, legacy);
        check(data.canCredit(owner) && data.canReclaim(owner) && data.debit(owner).signum() == 0
                && data.pending(owner).equals(fraction(1, 2)),
                "Legacy schema one without optional debit/unresolved sections must remain usable");
        check(data.begin(owner), "A healthy restored owner must acquire the shared transaction gate");
        check(!data.canReclaim(owner), "A read-only reclaim simulation must reject an account in an active transaction");
        try {
            check(data.reserve(owner, EmcAmount.of(BigInteger.valueOf(6))).equals(BigInteger.valueOf(6)),
                    "A six EMC purchase with half EMC change must reserve six whole EMC");
            data.credit(owner, fraction(10, 3));
        } finally {
            data.end(owner);
        }
        NeoForgeEmcSavedData restored = load(helper, save(helper, data));
        check(restored.canReclaim(owner) && restored.debit(owner).equals(BigInteger.valueOf(6))
                && restored.pending(owner).equals(fraction(23, 6)) && !restored.busy(owner),
                "Normal NBT roundtrip must retain debt/refund while dropping the transient busy flag");
        check(restored.begin(owner), "Restored healthy transactions must be available");
        try {
            restored.acknowledgeNet(owner, BigInteger.valueOf(3), BigInteger.valueOf(6));
        } finally {
            restored.end(owner);
        }
        CompoundTag settled = save(helper, restored);
        NeoForgeEmcSavedData finalData = load(helper, settled);
        check(finalData.pending(owner).equals(fraction(5, 6)) && finalData.debit(owner).signum() == 0
                && !settled.contains("debits"),
                "Net confirmation must retain only five sixths and remove zero debt from persisted data");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesMalformedDebitEntriesAndSettlesHealthyNeighbour(GameTestHelper helper) {
        UUID negative = UUID.randomUUID();
        UUID wrongType = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        CompoundTag source = root();
        CompoundTag debts = new CompoundTag();
        debts.putByteArray(negative.toString(), BigInteger.valueOf(-3).toByteArray());
        debts.put(wrongType.toString(), StringTag.valueOf("preserve wrong debt type"));
        debts.putByteArray(empty.toString(), new byte[0]);
        debts.putByteArray("invalid-owner", BigInteger.ONE.toByteArray());
        debts.putByteArray(healthy.toString(), BigInteger.TWO.toByteArray());
        source.put("debits", debts);
        source.getCompound("credits").put(healthy.toString(), NeoForgeEmcNbt.write(fraction(1, 2)));
        NeoForgeEmcSavedData data = load(helper, source);
        for (UUID blocked : new UUID[]{negative, wrongType, empty}) {
            check(!data.canCredit(blocked) && !data.begin(blocked),
                    "A malformed canonical owner debt must block that account before any transaction");
            expectRejected(() -> data.credit(blocked, EmcAmount.ONE),
                    "Malformed debt must reject new credit instead of overwriting evidence");
        }
        RetryAccount account = new RetryAccount(BigInteger.TEN);
        check(NeoForgeEmcCredits.flushToAccount(data, healthy, account)
                && account.value.equals(BigInteger.valueOf(8)) && data.pending(healthy).equals(fraction(1, 2)),
                "Invalid neighbouring debt entries must not prevent a healthy two EMC debit from settling");
        CompoundTag savedDebts = save(helper, data).getCompound("debits");
        for (String id : new String[]{negative.toString(), wrongType.toString(), empty.toString(), "invalid-owner"}) {
            check(debts.get(id).equals(savedDebts.get(id)), "Malformed debt NBT must survive unchanged: " + id);
        }
        check(!savedDebts.contains(healthy.toString()), "Acknowledged healthy debt must disappear without touching bad entries");
        NeoForgeEmcSavedData restored = load(helper, save(helper, data));
        check(!restored.canCredit(negative) && !restored.canCredit(wrongType) && !restored.canCredit(empty)
                && restored.canCredit(healthy), "Owner isolation must survive a second load");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesWrongRootTypesAndPersistentUnresolvedEvidence(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        for (String section : new String[]{"debits", "unresolved"}) {
            CompoundTag malformed = root();
            malformed.put(section, StringTag.valueOf("retain entire malformed " + section + " root"));
            malformed.putString("evidence", "unrelated root field must also survive");
            NeoForgeEmcSavedData blocked = load(helper, malformed);
            check(!blocked.canCredit(owner) && !blocked.begin(owner)
                    && malformed.equals(save(helper, blocked)),
                    "A wrongly typed optional root section must make the entire original snapshot read-only");
            expectRejected(() -> blocked.credit(owner, EmcAmount.ONE), "A bad root must reject new credit");
        }
        NeoForgeEmcSavedData uncertain = new NeoForgeEmcSavedData();
        uncertain.credit(owner, fraction(3, 2));
        uncertain.hold(owner, BigInteger.TEN, BigInteger.valueOf(11));
        CompoundTag evidence = save(helper, uncertain);
        NeoForgeEmcSavedData restored = load(helper, evidence);
        check(!restored.canCredit(owner) && !restored.begin(owner)
                && restored.pending(owner).equals(fraction(3, 2)) && evidence.equals(save(helper, restored)),
                "A persistently unresolved write must retain both its monetary amount and confirmation evidence");
        RetryAccount account = new RetryAccount(BigInteger.TEN);
        check(!NeoForgeEmcCredits.flushToAccount(restored, owner, account) && account.attempts == 0,
                "An unresolved write must never be automatically submitted again after restoration");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void retainsDebtWhenSetterThrowsBeforeMutationOrSilentlyDoesNothing(GameTestHelper helper) {
        for (boolean throwsBefore : new boolean[]{false, true}) {
            UUID owner = UUID.randomUUID();
            NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
            data.credit(owner, fraction(1, 2));
            check(data.begin(owner), "A fresh account must accept reservation");
            try {
                data.reserve(owner, EmcAmount.of(BigInteger.valueOf(6)));
            } finally {
                data.end(owner);
            }
            RetryAccount account = new RetryAccount(BigInteger.TEN);
            account.suppressWrites = true;
            account.throwsBefore = throwsBefore;
            CompoundTag before = save(helper, data);
            check(!NeoForgeEmcCredits.flushToAccount(data, owner, account)
                    && account.value.equals(BigInteger.TEN) && data.canCredit(owner)
                    && before.equals(save(helper, data)),
                    "An unmodified account must retain complete debt without false acknowledgement or quarantine");
            account.suppressWrites = false;
            check(NeoForgeEmcCredits.flushToAccount(data, owner, account)
                    && account.value.equals(BigInteger.valueOf(4)) && account.successfulWrites == 1
                    && data.debit(owner).signum() == 0 && data.pending(owner).equals(fraction(1, 2)),
                    "The recovered setter must apply exactly the retained six EMC debt once");
            check(NeoForgeEmcCredits.flushToAccount(data, owner, account)
                    && account.successfulWrites == 1 && account.attempts == 2,
                    "An additional flush must not repeat an acknowledged debit");
        }
        helper.succeed();
    }

    private static final class RetryAccount implements NeoForgeEmcCredits.Account {
        private BigInteger value;
        private boolean suppressWrites;
        private boolean throwsBefore;
        private int attempts;
        private int successfulWrites;

        private RetryAccount(BigInteger initial) { value = initial; }
        @Override public BigInteger balance() { return value; }
        @Override public void setBalance(BigInteger amount) {
            attempts++;
            if (suppressWrites) {
                if (throwsBefore) throw new IllegalStateException("Expected account rejection before mutation");
                return;
            }
            value = amount;
            successfulWrites++;
        }
        @Override public void sync() {}
    }

    private static CompoundTag root() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.put("credits", new CompoundTag());
        return tag;
    }
    private static CompoundTag save(GameTestHelper helper, NeoForgeEmcSavedData data) {
        return data.save(new CompoundTag(), helper.getLevel().registryAccess());
    }
    private static NeoForgeEmcSavedData load(GameTestHelper helper, CompoundTag tag) {
        return NeoForgeEmcSavedData.load(tag, helper.getLevel().registryAccess());
    }
    private static EmcAmount fraction(long numerator, long denominator) {
        return new EmcAmount(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }
    private static void expectRejected(Runnable operation, String message) {
        try { operation.run(); }
        catch (IllegalStateException expected) { return; }
        throw new GameTestAssertException(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
