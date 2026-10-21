/**
 * 【文件职责】用真实 ProjectE 账户验证秒报价、分数找零及加速运行额度。
 * 【变更记录 #1】2026-09-18 01:10:57（第 1 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：无。
 * - 新增：20 tick 各倍率收费、600 tick 到期、小额单价、限流分批、暂停续用找零及嵌套收支测试。
 * - 修改：复用 AccelerationFlowTest 的真实 NBT/账户/找零夹具，仅替代世界和网络边界。
 * - 用途：防止只改报价未改扣款、逐 tick 舍入多收、反复启停丢失找零或机器收支污染消费上限。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.machine.AdapterTestBootstrap;
import java.math.BigInteger;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccelerationPricingTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(ints = {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024})
    void twentyTicksDebitExactlyTheQuotedSecond(int rate) throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(rate)) {
            f.bind();
            if (rate >= 256) EmcAccelerationService.confirm(f.player, f.warning.get().token(), true);
            for (int tick = 0; tick < 20; tick++) {
                when(f.server.getTickCount()).thenReturn(tick);
                f.run(4096);
            }
            assertEquals(20 * (rate - 1), f.calls.get());
            assertEquals(BigInteger.valueOf(10_000_000 - rate * 1000L), f.knowledge.getEmc());
            assertEquals(String.valueOf(rate * 1000L), f.tag.getString("RunSpent"));
            assertEquals(EmcAmount.ZERO, f.change.get(f.owner));
        }
    }

    @Test void thirtySecondsCostThirtyQuotesAndThenStop() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            f.bind();
            for (int tick = 0; tick < 600; tick++) {
                when(f.server.getTickCount()).thenReturn(tick);
                f.run(4096);
            }
            assertEquals(1800, f.calls.get());
            assertEquals(BigInteger.valueOf(9_880_000), f.knowledge.getEmc());
            assertEquals("120000", f.tag.getString("RunSpent"));
            when(f.server.getTickCount()).thenReturn(600);
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(1800, f.calls.get());
            assertEquals(BigInteger.valueOf(9_880_000), f.knowledge.getEmc());
        }
    }

    @ParameterizedTest @CsvSource({"1,2", "1,4", "3,4", "1001,4"})
    void nonDivisibleSecondPricesDoNotRoundUpEachTick(long price, int rate) throws Exception {
        try (var config = mockStatic(PrimordialServerConfig.class, CALLS_REAL_METHODS);
             var f = new AccelerationFlowTest.Fixture(rate)) {
            config.when(PrimordialServerConfig::accelerationEmcPerMultiplierSecond).thenReturn(BigInteger.valueOf(price));
            f.knowledge.setEmc(BigInteger.valueOf(price * rate));
            f.bind();
            for (int tick = 0; tick < 20; tick++) f.run(4096);
            assertEquals(20 * (rate - 1), f.calls.get());
            assertEquals(BigInteger.ZERO, f.knowledge.getEmc());
            assertEquals(EmcAmount.ZERO, f.change.get(f.owner));
            assertEquals(String.valueOf(price * rate), f.tag.getString("RunSpent"));
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(20 * (rate - 1), f.calls.get());
        }
    }

    @Test void throttlingAcrossTicksChargesTheSameAsOneFullTick() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            f.bind();
            f.run(1);
            assertEquals(EmcAmount.parse("1/3"), f.change.get(f.owner));
            f.run(1);
            assertEquals(EmcAmount.parse("2/3"), f.change.get(f.owner));
            f.run(1);
            assertEquals(BigInteger.valueOf(9_999_800), f.knowledge.getEmc());
            assertEquals(EmcAmount.ZERO, f.change.get(f.owner));
            assertEquals("200", f.tag.getString("RunSpent"));
        }
    }

    @Test void restartingTheToolDoesNotDiscardPaidChange() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            f.bind();
            for (int restart = 0; restart < 3; restart++) {
                f.run(1);
                f.bind();
                f.bind();
            }
            assertEquals(3, f.calls.get());
            assertEquals(BigInteger.valueOf(9_999_800), f.knowledge.getEmc());
            assertEquals(EmcAmount.ZERO, f.change.get(f.owner));
        }
    }

    @Test void partialBalanceBuysOnlyAffordableExtraTicks() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            f.knowledge.setEmc(BigInteger.valueOf(100));
            f.bind();
            f.run(4096);
            assertEquals(1, f.calls.get());
            assertEquals(BigInteger.valueOf(33), f.knowledge.getEmc());
            assertEquals(EmcAmount.parse("1/3"), f.change.get(f.owner));
            assertEquals("67", f.tag.getString("RunSpent"));
            f.run(4096);
            assertFalse(f.enabled());
            assertEquals(1, f.calls.get());
        }
    }

    @Test void budgetExhaustedBeforeFirstCallRefundsTheEntireReservation() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(4)) {
            f.bind();
            var clock = new java.util.concurrent.atomic.AtomicInteger();
            var budget = new AccelerationBudget(64, 4096, 2000, () -> clock.incrementAndGet() <= 3 ? 0L : 2000L);
            EmcAccelerationService.process(f.server, f.player, f.stack, budget);
            assertEquals(0, f.calls.get());
            assertEquals(BigInteger.valueOf(10_000_000), f.knowledge.getEmc());
            assertEquals(EmcAmount.ZERO, f.change.get(f.owner));
            assertEquals("0", f.tag.getString("RunSpent"));
            assertTrue(f.enabled());
        }
    }

    @Test void machineIncomeDoesNotHideAccelerationChargesFromTheRunCap() throws Exception {
        try (var f = new AccelerationFlowTest.Fixture(2)) {
            var target = new AccelerationTarget(f.level, f.pos, Blocks.FURNACE.defaultBlockState(), f.level.getBlockEntity(f.pos),
                    (world, pos, state, entity) -> {
                        f.calls.incrementAndGet();
                        EmcService.add(f.server, f.owner, BigInteger.valueOf(1000));
                    });
            f.targets.when(() -> AccelerationTarget.read(f.level, f.pos)).thenReturn(target);
            f.bind();
            f.tag.putString("RunSpent", "2099999900");
            f.run(4096);
            assertEquals(BigInteger.valueOf(10_000_900), f.knowledge.getEmc());
            assertEquals("2100000000", f.tag.getString("RunSpent"));
            assertFalse(f.enabled());
            assertEquals(1, f.calls.get());
        }
    }

    @Test void largeCustomSecondPriceCannotBypassTheRunLimit() throws Exception {
        try (var config = mockStatic(PrimordialServerConfig.class, CALLS_REAL_METHODS);
             var f = new AccelerationFlowTest.Fixture(2)) {
            config.when(PrimordialServerConfig::accelerationEmcPerMultiplierSecond).thenReturn(BigInteger.TEN.pow(100));
            f.knowledge.setEmc(BigInteger.TEN.pow(120));
            f.bind(); f.run(4096);
            assertFalse(f.enabled());
            assertEquals(0, f.calls.get());
            assertEquals(BigInteger.TEN.pow(120), f.knowledge.getEmc());
            assertEquals("0", f.tag.getString("RunSpent"));
        }
    }
}
