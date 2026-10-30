/**
 * 【文件职责】验收精确收益账本的 NBT 持久化、账户写入失败、通知失败与重入提交边界。
 * 真实世界/玩家附件由其它 GameTest 覆盖；本组以小型账户替身控制提交和通知故障。
 *
 * 【变更记录 #1】2026-10-22 21:07:57（第 1 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：大数与超长分母往返、写入重试、同步异常不重付、重入新增收益和损坏条目隔离五项测试。
 * - 修改：通过生产 flushToAccount 和 SavedData 入口执行，不绕过真实金额计算或确认扣除逻辑。
 * - 用途：锁定账本接收与 ProjectE 整数提交的边界；不宣称多文件异常断电下具有原子事务。
 *
 * 【变更记录 #2】2026-10-22 21:11:53（第 2 次备注 · 2.0.0-neo-dev 异常账本只读保护）
 * - 删除：未知版本加载时抛异常的旧预期。
 * - 新增：未知版本、缺少收益列表和版本类型错误均拒绝入账且原样保存的断言。
 * - 修改：通过加载后的只读账本检查阻断语义，保留完整原始 NBT。
 * - 用途：避免存储加载器吞掉异常后新建空账本，覆盖尚未识别的原始收益记录。
 *
 * 【变更记录 #3】2026-10-22 21:20:58（第 3 次备注 · 2.0.0-neo-dev 余额通知恢复）
 * - 删除：没有新增整数收益时不再尝试失败通知的旧预期。
 * - 新增：通知恢复独立于新收益、恢复成功后停止重试的断言。
 * - 修改：失败重试只增加通知次数；恢复和后续正常收益均核对真实余额写入次数。
 * - 用途：保证客户端最终收到已提交余额，同时避免通知故障导致重复入账。
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
public final class NeoForgeEmcCreditsGameTests {
    private NeoForgeEmcCreditsGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesFractionsHugeBalancesAndLongDenominatorsAcrossNbt(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
        EmcAmount small = new EmcAmount(BigInteger.valueOf(2), BigInteger.valueOf(7));
        EmcAmount tiny = new EmcAmount(BigInteger.ONE, BigInteger.TEN.pow(5000).add(BigInteger.ONE));
        EmcAmount huge = EmcAmount.of(BigInteger.TEN.pow(200));
        data.credit(owner, huge);
        data.credit(owner, small);
        data.credit(owner, tiny);
        EmcAmount expected = huge.add(small).add(tiny);
        check(NeoForgeEmcNbt.read(NeoForgeEmcNbt.write(expected)).equals(expected),
                "Amount NBT must preserve a denominator beyond the former text limit");
        NeoForgeEmcSavedData restored = reload(helper, data);
        check(restored.pending(owner).equals(expected), "Ledger reload must preserve the complete rational amount");
        MemoryAccount account = new MemoryAccount(BigInteger.valueOf(17));
        check(NeoForgeEmcCredits.flushToAccount(restored, owner, account), "Huge integer credit must commit");
        check(account.balance.equals(BigInteger.valueOf(17).add(expected.floor())),
                "Only the exact whole part may enter the account");
        check(reload(helper, restored).pending(owner).equals(expected.fraction()),
                "The exact fractional remainder must survive a second save and reload");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void retainsFullCreditAfterWriteFailureAndCommitsRetryOnlyOnce(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
        EmcAmount queued = new EmcAmount(BigInteger.valueOf(21), BigInteger.TWO);
        data.credit(owner, queued);
        MemoryAccount account = new MemoryAccount(BigInteger.valueOf(100));
        account.rejectWrites = true;
        check(!NeoForgeEmcCredits.flushToAccount(data, owner, account), "A rejected account write must report failure");
        check(account.balance.equals(BigInteger.valueOf(100)) && account.successfulWrites == 0,
                "A rejected write must leave the native balance unchanged");
        check(reload(helper, data).pending(owner).equals(queued),
                "Both whole credit and fraction must remain durable after a rejected write");
        account.rejectWrites = false;
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account), "The restored account must accept the retry");
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account), "An additional flush must safely do no work");
        check(account.balance.equals(BigInteger.valueOf(110)) && account.successfulWrites == 1,
                "A recovered write must credit ten EMC exactly once");
        check(data.pending(owner).equals(new EmcAmount(BigInteger.ONE, BigInteger.TWO)),
                "A successful retry must retain exactly one half EMC");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void doesNotRecreditCommittedEmcWhenClientSyncFails(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
        data.credit(owner, new EmcAmount(BigInteger.valueOf(28), BigInteger.valueOf(3)));
        MemoryAccount account = new MemoryAccount(BigInteger.valueOf(100));
        account.rejectSync = true;
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account),
                "Client notification failure must not turn a committed credit into a rejection");
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account), "A later flush must not repeat committed credit");
        check(account.balance.equals(BigInteger.valueOf(109)) && account.successfulWrites == 1
                && account.syncCalls == 2, "Failed notification must retry without repeating the nine EMC write");
        check(data.pending(owner).equals(new EmcAmount(BigInteger.ONE, BigInteger.valueOf(3))),
                "Notification failure must preserve only the exact uncommitted fraction");
        account.rejectSync = false;
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account),
                "Pending notification must recover even without newly earned whole credit");
        check(account.balance.equals(BigInteger.valueOf(109)) && account.successfulWrites == 1
                && account.syncCalls == 3, "Notification recovery must not write the committed amount again");
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account), "A recovered notification must stop retrying");
        check(account.successfulWrites == 1 && account.syncCalls == 3,
                "A settled ledger must stop both network notifications and account writes after recovery");
        data.credit(owner, new EmcAmount(BigInteger.TWO, BigInteger.valueOf(3)));
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account), "Future earned credit must still flush normally");
        check(account.balance.equals(BigInteger.valueOf(110)) && data.pending(owner).isZero()
                && account.successfulWrites == 2 && account.syncCalls == 4,
                "New fractions must combine into exactly one more EMC after notification recovers");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesNewCreditAndPreventsDuplicateSubmissionDuringReentrantWrite(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeEmcSavedData data = new NeoForgeEmcSavedData();
        data.credit(owner, new EmcAmount(BigInteger.valueOf(11), BigInteger.TWO));
        MemoryAccount account = new MemoryAccount(BigInteger.ZERO);
        account.duringWrite = () -> {
            data.credit(owner, new EmcAmount(BigInteger.valueOf(29), BigInteger.valueOf(4)));
            NeoForgeEmcCredits.flushToAccount(data, owner, account);
        };
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account), "The original five EMC write must commit");
        check(account.balance.equals(BigInteger.valueOf(5)) && account.successfulWrites == 1,
                "Reentrant flush must not submit the pending whole amount twice");
        check(data.pending(owner).equals(new EmcAmount(BigInteger.valueOf(31), BigInteger.valueOf(4))),
                "Acknowledgement must subtract from the latest ledger and preserve newly earned credit");
        check(NeoForgeEmcCredits.flushToAccount(data, owner, account), "Newly earned whole credit must flush later");
        check(account.balance.equals(BigInteger.valueOf(12)) && account.successfulWrites == 2,
                "The later flush must commit only the remaining seven whole EMC");
        check(data.pending(owner).equals(new EmcAmount(BigInteger.valueOf(3), BigInteger.valueOf(4))),
                "Reentrant credit must leave the exact three-quarter remainder");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesMalformedEntriesWithoutBlockingHealthyAccountsAndCleansZeroEntries(GameTestHelper helper) {
        UUID healthy = UUID.randomUUID();
        UUID quarantined = UUID.randomUUID();
        UUID zero = UUID.randomUUID();
        CompoundTag malformed = new CompoundTag();
        malformed.putByteArray("n", new byte[]{1});
        malformed.putByteArray("d", new byte[]{0});
        malformed.putString("evidence", "preserve this invalid denominator");
        CompoundTag entries = new CompoundTag();
        entries.put(healthy.toString(), NeoForgeEmcNbt.write(EmcAmount.ONE));
        entries.put(quarantined.toString(), malformed);
        entries.put(zero.toString(), NeoForgeEmcNbt.write(EmcAmount.ZERO));
        entries.put("invalid-owner", StringTag.valueOf("preserve this invalid UUID entry"));
        CompoundTag source = new CompoundTag();
        source.putInt("schema", 1);
        source.put("credits", entries);
        NeoForgeEmcSavedData data = NeoForgeEmcSavedData.load(source, helper.getLevel().registryAccess());
        expectFailure(IllegalStateException.class, () -> data.credit(quarantined, EmcAmount.ONE),
                "A quarantined owner must reject new credit instead of overwriting evidence");
        MemoryAccount account = new MemoryAccount(BigInteger.ZERO);
        check(NeoForgeEmcCredits.flushToAccount(data, healthy, account),
                "A malformed neighbour must not prevent a healthy account from settling");
        CompoundTag savedEntries = data.save(new CompoundTag(), helper.getLevel().registryAccess()).getCompound("credits");
        check(account.balance.equals(BigInteger.ONE) && !savedEntries.contains(healthy.toString()),
                "Successfully settled zero balances must disappear from the saved ledger");
        check(!savedEntries.contains(zero.toString()), "Loaded zero entries must not remain in the saved ledger");
        check(malformed.equals(savedEntries.get(quarantined.toString()))
                && entries.get("invalid-owner").equals(savedEntries.get("invalid-owner")),
                "Malformed entries must survive saving with their original raw NBT unchanged");
        CompoundTag unsupported = source.copy();
        unsupported.putInt("schema", 2);
        assertBlockedSnapshot(helper, unsupported, healthy, "An unknown schema");
        CompoundTag missingCredits = source.copy();
        missingCredits.remove("credits");
        missingCredits.putString("evidence", "preserve a ledger whose credits list is missing");
        assertBlockedSnapshot(helper, missingCredits, healthy, "A missing credits list");
        CompoundTag wrongSchemaType = source.copy();
        wrongSchemaType.putString("schema", "1");
        assertBlockedSnapshot(helper, wrongSchemaType, healthy, "A schema with the wrong NBT type");
        helper.succeed();
    }

    private static void assertBlockedSnapshot(GameTestHelper helper, CompoundTag snapshot, UUID owner, String label) {
        NeoForgeEmcSavedData blocked = NeoForgeEmcSavedData.load(snapshot, helper.getLevel().registryAccess());
        expectFailure(IllegalStateException.class, () -> blocked.credit(owner, EmcAmount.ONE),
                label + " must remain read-only instead of becoming an empty writable ledger");
        check(snapshot.equals(blocked.save(new CompoundTag(), helper.getLevel().registryAccess())),
                label + " must survive saving with the entire raw NBT unchanged");
    }

    private static NeoForgeEmcSavedData reload(GameTestHelper helper, NeoForgeEmcSavedData data) {
        return NeoForgeEmcSavedData.load(data.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
    }

    private static void expectFailure(Class<? extends RuntimeException> expected, Runnable operation, String message) {
        try {
            operation.run();
        } catch (RuntimeException failure) {
            check(expected.isInstance(failure), message + ": unexpected exception " + failure.getClass().getSimpleName());
            return;
        }
        throw new GameTestAssertException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** 模拟确定在写入前失败的 setter；通知失败单独发生在余额已经成功修改之后。 */
    private static final class MemoryAccount implements NeoForgeEmcCredits.Account {
        private BigInteger balance;
        private boolean rejectWrites;
        private boolean rejectSync;
        private int successfulWrites;
        private int syncCalls;
        private Runnable duringWrite;

        private MemoryAccount(BigInteger initial) {
            balance = initial;
        }

        @Override
        public BigInteger balance() {
            return balance;
        }

        @Override
        public void setBalance(BigInteger amount) {
            if (rejectWrites) throw new IllegalStateException("Expected test account write rejection before mutation");
            balance = amount;
            successfulWrites++;
            Runnable callback = duringWrite;
            duringWrite = null;
            if (callback != null) callback.run();
        }

        @Override
        public void sync() {
            syncCalls++;
            if (rejectSync) throw new IllegalStateException("Expected test client notification failure after commit");
        }
    }
}
