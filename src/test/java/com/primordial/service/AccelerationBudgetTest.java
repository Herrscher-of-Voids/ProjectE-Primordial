/**
 * 【文件职责】以可控时钟验证高倍率执行的全服预算、去重、异常和收费边界。
 * 【变更记录 #1】2026-09-16 13:35:51（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：总次数截断、耗时截断、慢调用、目标消失和部分执行计费回归。
 * - 修改：直接运行生产预算器，时钟无需 sleep，避免性能用例受机器负载影响而波动。
 * - 用途：确保 1024 倍及多个目标不会绕过共享工作上限，也不免费回滚已执行工作。
 */
/**
 * 【变更记录 #2】2026-09-18 01:08:12（第 2 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：已移除的逐次向上取整计费断言。
 * - 新增：无；精确结算由 AccelerationPricingTest 覆盖实际账户和多次限流。
 * - 修改：本类集中验证次数、耗时、异常和目标替换预算。
 * - 用途：性能上限不随秒价调整而放宽，避免重复测试废弃算法。
 */
package com.primordial.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccelerationBudgetTest {
    @Test void totalCallsAreBoundedAndTargetsCannotStack() {
        var budget = new AccelerationBudget(64, 4096, 2000, () -> 0L);
        var calls = new AtomicInteger();
        assertTrue(budget.claim("first"));
        assertFalse(budget.claim("first"));
        for (int i = 0; i < 64; i++) {
            if (budget.claim("machine" + i)) budget.run(1023, () -> true, calls::incrementAndGet);
        }
        assertEquals(4096, calls.get());
        assertFalse(budget.available());
    }

    @Test void deadlineStopsFurtherCallsWithoutAccumulatingBacklog() {
        var now = new AtomicLong();
        var budget = new AccelerationBudget(64, 4096, 2000, now::get);
        var result = budget.run(1023, () -> true, () -> now.addAndGet(600));
        assertEquals(4, result.attempted());
        assertFalse(result.slow());
        assertEquals(0, budget.run(1023, () -> true, () -> fail("budget exceeded")).attempted());
    }

    @Test void singleSlowCallStopsTheTargetAndFaultyCallIsCharged() {
        var now = new AtomicLong();
        var budget = new AccelerationBudget(1, 4096, 2000, now::get);
        var slow = budget.run(1023, () -> true, () -> now.addAndGet(3000));
        assertTrue(slow.slow());
        assertEquals(1, slow.attempted());
        var faultBudget = new AccelerationBudget(1, 4096, 2000, () -> 0L);
        var calls = new AtomicInteger();
        var result = faultBudget.run(1023, () -> true, () -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("target failed after changing state");
        });
        assertEquals(2, result.attempted());
        assertNotNull(result.failure());
        assertEquals(2, calls.get());
    }

    @Test void replacementStopsBeforeTouchingRemovedEntity() {
        var calls = new AtomicInteger();
        var result = new AccelerationBudget(1, 4096, 2000, () -> 0L)
                .run(1023, () -> calls.get() < 3, calls::incrementAndGet);
        assertEquals(3, result.attempted());
        assertTrue(result.invalid());
    }

}
