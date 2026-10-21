/**
 * 【文件职责】验证自动配方 EMC 计算的整数边界和非零输出规则。
 *
 * 【变更记录 #1】2026-09-15 15:02:00（第 1 次备注 · 1.0.0-stage7-recipe-emc）
 * - 删除：无；保留纯 EMC 分数测试，不启动完整服务器。
 * - 新增：物品、流体、能源成本合计后的产量除法、向上取整和非法产量边界测试。
 * - 修改：通过公开 outputValue 契约验证计算结果，避免复制私有配方遍历实现。
 * - 用途：确保极小流体成本和多产物配方不会变成零价或因整数截断丢失 EMC。
 */
package com.primordial.machine;

import com.primordial.service.EmcAmount;
import com.primordial.value.RecipeEmcCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeEmcCalculatorTest {
    @Test void outputValueSumsFractionalCostAndRoundsOnlyAtOutputBoundary() {
        EmcAmount cost = EmcAmount.of(BigInteger.valueOf(2000))
                .add(EmcAmount.parse("1/3"))
                .add(EmcAmount.of(BigInteger.valueOf(5)));
        assertEquals(BigInteger.valueOf(1003), RecipeEmcCalculator.outputValue(cost, 2));
    }

    @Test void nonZeroRecipeNeverBecomesFreeAndInvalidOutputIsRejected() {
        assertEquals(BigInteger.ONE, RecipeEmcCalculator.outputValue(EmcAmount.parse("1/1000000"), 3));
        assertEquals(BigInteger.ZERO, RecipeEmcCalculator.outputValue(EmcAmount.ONE, 0));
        assertEquals(BigInteger.ZERO, RecipeEmcCalculator.outputValue(null, 1));
    }
}
