/**
 * 【文件职责】执行生产结算器，验证分批/整批交易等价、退款、重启找零、超大值及线程门禁。
 * 【变更记录 #1】2026-09-15 15:14:04（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：真实 Forge EnergyStorage、EmcService、EmcSettlement 与 SavedData 行为测试。
 * - 修改：仅整数 ProjectE 余额、世界和定价用替身；资源交付及找零运算不模拟。
 * - 用途：防止低额资源逐次取整、部分退款复制 EMC、未知资源销毁和跨线程写世界。
 */
/**
 * 【文件职责】验证整数余额与分数找零交易。
 * 【变更记录 #2】2026-09-16 19:46:19（第 2 次备注 · 1.0.0-review-ready）
 * - 删除：测试在未注册凭证的裸JUnit环境隐式查询资源授权的前提。
 * - 新增：找零测试固定关闭知识要求，独立知识测试使用真实ProjectE能力覆盖。
 * - 修改：保留所有传输与退款断言，不模拟找零算法。
 * - 用途：使原有交易回归与新增知识门禁各自拥有明确的运行条件。
 */
/**
 * 【变更记录 #3】2026-09-18 01:10:57（第 3 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：无。
 * - 新增：购买回执的拒付、零交付、分数退款及嵌套交易独立统计验证。
 * - 修改：保留所有原资源传输测试，新增入口继续执行同一生产结算事务。
 * - 用途：确认加速器的消费统计只包含自己的付款，不把机器产出 EMC 或其它消费混入。
 */
package com.primordial.machine;

import com.primordial.data.EmcConversionSavedData;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.value.ValueManager;
import com.primordial.util.BalanceSyncer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.energy.EnergyStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmcSettlementTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void fiftyTwentyFeSuppliesCostOneEmcAndCanSpendTheLastFraction() {
        try (var world = new Account(BigInteger.ONE)) {
            var battery = new EnergyStorage(1000, 20);
            for (int i = 0; i < 50; i++) assertEquals(20, EmcService.supplyEnergy(world.server, world.owner, battery, 20));
            assertEquals(1000, battery.getEnergyStored());
            assertEquals(BigInteger.ZERO, world.balance);
            assertEquals(EmcAmount.ZERO, world.change.get(world.owner));
            assertEquals(1, world.payments);
            assertEquals(0, EmcService.supplyEnergy(world.server, world.owner, new EnergyStorage(20), 20));
        }
    }

    @Test void recoveryAcrossReloadUsesTheSameFractionAndOnlyWritesAWholeEmc() {
        try (var world = new Account(BigInteger.ZERO)) {
            var battery = new EnergyStorage(1000, 20, 20, 1000);
            for (int i = 0; i < 25; i++) assertEquals(20, EmcService.reclaimEnergy(world.server, world.owner, battery, 20));
            assertEquals(BigInteger.ZERO, world.balance);
            assertEquals(EmcAmount.parse("1/2"), world.change.get(world.owner));
            world.reload();
            for (int i = 0; i < 25; i++) assertEquals(20, EmcService.reclaimEnergy(world.server, world.owner, battery, 20));
            assertEquals(0, battery.getEnergyStored());
            assertEquals(BigInteger.ONE, world.balance);
            assertEquals(1, world.credits);
            assertEquals(EmcAmount.ZERO, world.change.get(world.owner));
        }
    }

    @Test void partialAcceptanceRefundsExactFractionAndZeroAcceptanceRestoresEverything() {
        try (var world = new Account(BigInteger.TEN)) {
            world.change.set(world.owner, EmcAmount.parse("3/7"));
            EmcAmount before = world.total();
            assertEquals(7, EmcService.purchase(world.server, world.owner, EmcAmount.parse("1/1000"), 500, n -> 7));
            assertEquals(before.subtract(EmcAmount.parse("7/1000")), world.total());
            before = world.total();
            assertEquals(0, EmcService.purchase(world.server, world.owner, EmcAmount.parse("17/31"), 10, n -> 0));
            assertEquals(before, world.total());
        }
    }

    @Test void arbitraryTransferSplitsPreserveConservation() {
        try (var world = new Account(BigInteger.valueOf(1000))) {
            var random = new java.util.Random(9126);
            EmcAmount initial = world.total(), spent = EmcAmount.ZERO, reclaimed = EmcAmount.ZERO;
            for (int i = 0; i < 300; i++) {
                var price = new EmcAmount(BigInteger.valueOf(1 + random.nextInt(9)), BigInteger.valueOf(1 + random.nextInt(99)));
                int requested = 1 + random.nextInt(50), actual = random.nextInt(requested + 1);
                long delivered = EmcService.purchase(world.server, world.owner, price, requested, n -> Math.min(n, actual));
                spent = spent.add(price.multiply(delivered));
                int recovered = random.nextInt(20);
                EmcService.reclaim(world.server, world.owner, price, recovered, n -> n);
                reclaimed = reclaimed.add(price.multiply(recovered));
                assertEquals(initial.add(reclaimed).subtract(spent), world.total());
            }
        }
    }

    @Test void rejectedDebitAndUnknownPriceNeverTouchResources() {
        try (var world = new Account(BigInteger.TEN)) {
            world.debitAllowed = false;
            AtomicBoolean moved = new AtomicBoolean();
            var transfer = (java.util.function.LongUnaryOperator) n -> { moved.set(true); return n; };
            assertEquals(0, EmcService.purchase(world.server, world.owner, EmcAmount.ONE, 5, transfer));
            assertEquals(0, EmcService.purchase(world.server, world.owner, EmcAmount.ZERO, 5, transfer));
            assertEquals(0, EmcService.reclaim(world.server, world.owner, EmcAmount.ZERO, 5, transfer));
            assertFalse(moved.get());
            assertEquals(EmcAmount.of(BigInteger.TEN), world.total());
        }
    }

    @Test void callbackFailureDoesNotRefundResourcesThatMayAlreadyHaveBeenDelivered() {
        try (var world = new Account(BigInteger.TEN)) {
            assertThrows(IllegalStateException.class, () -> EmcService.purchase(world.server, world.owner,
                    EmcAmount.parse("1/3"), 9, n -> { throw new IllegalStateException("receiver failed after mutation"); }));
            assertEquals(BigInteger.valueOf(7), world.balance);
            assertEquals(0, world.credits);
        }
    }

    @Test void clearRemovesTheFractionAndNonServerThreadCannotTransfer() {
        try (var world = new Account(BigInteger.TEN)) {
            world.change.set(world.owner, EmcAmount.parse("2/3"));
            EmcService.clearBalance(world.server, world.owner);
            assertEquals(EmcAmount.ZERO, world.total());
            assertFalse(world.change.save(new CompoundTag()).getCompound("change").contains(world.owner.toString()));
            when(world.server.isSameThread()).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> EmcService.purchase(world.server, world.owner, EmcAmount.ONE, 1, n -> fail("transfer ran")));
            assertThrows(IllegalStateException.class, () -> EmcService.reclaim(world.server, world.owner, EmcAmount.ONE, 1, n -> fail("extract ran")));
            assertEquals(EmcAmount.ZERO, world.total());
        }
    }

    @Test void hugePricesAndKnowledgeGateApplyToItemPurchases() {
        try (var world = new Account(BigInteger.TEN.pow(100)); var prices = mockStatic(ValueManager.class)) {
            var item = new ItemStack(Items.DIAMOND);
            var price = BigInteger.TEN.pow(80);
            prices.when(() -> ValueManager.itemValueEmc(item, world.server)).thenReturn(price);
            world.emc.when(() -> EmcService.hasKnowledge(world.server, world.owner, item)).thenReturn(false);
            assertEquals(0, EmcService.purchaseItem(world.server, world.owner, item, Long.MAX_VALUE, n -> fail("unknown item")));
            world.emc.when(() -> EmcService.hasKnowledge(world.server, world.owner, item)).thenReturn(true);
            assertEquals(Long.MAX_VALUE, EmcService.purchaseItem(world.server, world.owner, item, Long.MAX_VALUE, n -> n));
            assertEquals(BigInteger.TEN.pow(100).subtract(price.multiply(BigInteger.valueOf(Long.MAX_VALUE))), world.balance);
            prices.when(() -> ValueManager.itemValueEmc(item, world.server)).thenReturn(BigInteger.ZERO);
            assertEquals(0, EmcService.purchaseItem(world.server, world.owner, item, 1, n -> fail("zero price")));
        }
    }

    /** 真实找零存档，外部 ProjectE 整数账户用可审计替身表示；每项测试只拥有自己的 UUID 和世界。 */
    @Test void nestedReclaimAndPurchaseSeeReservedFundsAndRefundAgainstTheLatestChange() {
        try (var world = new Account(BigInteger.TEN)) {
            EmcService.purchase(world.server, world.owner, EmcAmount.parse("1/3"), 10, outer -> {
                EmcService.credit(world.server, world.owner, EmcAmount.parse("1/7"));
                EmcService.purchase(world.server, world.owner, EmcAmount.parse("1/5"), 3, inner -> inner);
                return 2;
            });
            assertEquals(EmcAmount.parse("10").add(EmcAmount.parse("1/7"))
                    .subtract(EmcAmount.parse("3/5")).subtract(EmcAmount.parse("2/3")), world.total());
        }
    }

    @Test void receiptTracksOnlyItsOwnDebitAndRefund() {
        try (var world = new Account(BigInteger.valueOf(1000))) {
            var receipt = EmcService.purchaseWithReceipt(world.server, world.owner, EmcAmount.parse("200/3"), 3, n -> {
                EmcService.credit(world.server, world.owner, EmcAmount.parse("500"));
                EmcService.purchase(world.server, world.owner, EmcAmount.parse("10"), 1, units -> units);
                return 1;
            });
            assertEquals(1, receipt.units());
            assertEquals(BigInteger.valueOf(67), receipt.charged());
            assertEquals(BigInteger.valueOf(1423), world.balance);
            assertEquals(EmcAmount.parse("1/3"), world.change.get(world.owner));
            var rejected = EmcService.purchaseWithReceipt(world.server, world.owner, EmcAmount.ONE, 20, n -> 0);
            assertEquals(0, rejected.units());
            assertEquals(BigInteger.ZERO, rejected.charged());
            assertEquals(BigInteger.valueOf(1423), world.balance);
            world.debitAllowed = false;
            var unpaid = EmcService.purchaseWithReceipt(world.server, world.owner, EmcAmount.ONE, 1, n -> fail("unpaid work"));
            assertEquals(0, unpaid.units());
            assertEquals(BigInteger.ZERO, unpaid.charged());
        }
    }

    @Test void fractionLargerThanTheOldTextLimitSurvivesNbtPersistence() {
        try (var world = new Account(BigInteger.ZERO)) {
            var fraction = new EmcAmount(BigInteger.ONE, BigInteger.TEN.pow(5000).add(BigInteger.ONE));
            world.change.set(world.owner, fraction);
            world.reload();
            assertEquals(fraction, world.total());
            var legacy = new CompoundTag(); var entries = new CompoundTag(); var value = new CompoundTag();
            value.putString("n", "2"); value.putString("d", "7");
            entries.put(world.owner.toString(), value); legacy.put("change", entries);
            assertEquals(EmcAmount.parse("2/7"), EmcConversionSavedData.load(legacy).get(world.owner));
        }
    }

    /** 真实找零存档，外部 ProjectE 整数账户用可审计替身表示；每项测试只拥有自己的 UUID 和世界。 */
    static final class Account implements AutoCloseable {
        final MinecraftServer server = mock(MinecraftServer.class);
        final UUID owner = UUID.randomUUID();
        EmcConversionSavedData change = new EmcConversionSavedData();
        BigInteger balance;
        int payments, credits;
        boolean debitAllowed = true;
        final MockedStatic<EmcService> emc = mockStatic(EmcService.class, CALLS_REAL_METHODS);
        private final MockedStatic<EmcConversionSavedData> stores = mockStatic(EmcConversionSavedData.class, CALLS_REAL_METHODS);
        private final MockedStatic<BalanceSyncer> sync = mockStatic(BalanceSyncer.class);
        // 本组验证找零与传输；知识门禁由 ResourceKnowledgeTest 使用真实 ProjectE capability 单独覆盖。
        private final MockedStatic<com.primordial.config.PrimordialKnowledgeConfig> knowledge =
                mockStatic(com.primordial.config.PrimordialKnowledgeConfig.class);

        Account(BigInteger balance) {
            this.balance = balance;
            when(server.isSameThread()).thenReturn(true);
            stores.when(() -> EmcConversionSavedData.get(server)).thenAnswer(call -> change);
            emc.when(() -> EmcService.balance(server, owner)).thenAnswer(call -> this.balance);
            emc.when(() -> EmcService.spend(eq(server), eq(owner), any())).thenAnswer(call -> {
                BigInteger amount = call.getArgument(2);
                if (!debitAllowed || this.balance.compareTo(amount) < 0) return false;
                this.balance = this.balance.subtract(amount);
                if (amount.signum() > 0) payments++;
                return true;
            });
            emc.when(() -> EmcService.add(eq(server), eq(owner), any())).thenAnswer(call -> {
                this.balance = this.balance.add(call.getArgument(2)); credits++; return null;
            });
        }
        EmcAmount total() { return EmcAmount.of(balance).add(change.get(owner)); }
        void reload() { change = EmcConversionSavedData.load(change.save(new CompoundTag())); }
        @Override public void close() { knowledge.close(); sync.close(); stores.close(); emc.close(); }
    }
}
