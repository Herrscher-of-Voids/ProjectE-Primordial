/**
 * 文件职责：验证 EMC 加速器的倍率边界、循环顺序和基础费用规则。
 *
 * 变更记录 #1（2026-09-16，EMC 加速功能）
 * - 新增：覆盖 2～1024 倍的合法倍率、超过上限后的循环和 2 倍速线性费用。
 * - 用途：防止倍率计算回到无界值，或把 4 倍速及以上的费用计算成指数增长。
 */
/**
 * 【变更记录 #2】2026-09-16 14:41:00（第 2 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：旧倍率乘 500 的价格预期。
 * - 新增：2、4、1024 倍使用倍率乘 1000 的断言。
 * - 修改：新价格来自服务端配置默认值，保持非法倍率与循环边界验证。
 * - 用途：防止改价后 tooltip 与实际扣款仍沿用旧系数。
 */
/**
 * 【变更记录 #3】2026-09-18 01:08:12（第 3 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：旧每 tick 报价入口的断言。
 * - 新增：4 倍一 tick 为 200 EMC、单次额外调用为 200/3 EMC 的精度验证。
 * - 修改：2、4、1024 倍的倍率乘 1000 改为明确验证每秒报价。
 * - 用途：保证报价与逐 tick 执行按 20:1 换算，实际账户回归在 AccelerationPricingTest 中验证。
 */
package com.primordial.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmcAccelerationServiceTest {
    @Test
    void multiplierRangeAndCycleAreBounded() {
        assertTrue(EmcAccelerationService.isMultiplier(2));
        assertTrue(EmcAccelerationService.isMultiplier(1024));
        assertFalse(EmcAccelerationService.isMultiplier(3));
        assertFalse(EmcAccelerationService.isMultiplier(2048));
        assertEquals(4, EmcAccelerationService.nextMultiplier(2));
        assertEquals(2, EmcAccelerationService.nextMultiplier(1024));
    }

    @Test
    void perSecondCostIsLinearInSpeed() {
        assertEquals("2000", EmcAccelerationService.costPerSecond(2).toString());
        assertEquals("4000", EmcAccelerationService.costPerSecond(4).toString());
        assertEquals("1024000", EmcAccelerationService.costPerSecond(1024).toString());
        assertEquals(EmcAmount.parse("200/3"), EmcAccelerationService.costPerExtraTick(4));
        assertEquals(EmcAmount.parse("200"), EmcAccelerationService.costPerExtraTick(4).multiply(3));
    }
}
