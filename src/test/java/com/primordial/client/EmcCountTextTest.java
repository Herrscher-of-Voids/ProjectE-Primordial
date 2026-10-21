/**
 * 【变更记录 #1】2026-10-08 02:06:10（第 1 次备注 · 1.2.0-jei-counts）
 * - 删除：无；新增：知识、无价、向下取整、大余额和缩写回归。
 * - 修改：无；用途：验证显示数量不能溢出或将未学物品标为可兑换。
 */
package com.primordial.client;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EmcCountTextTest {
    @Test void unknownAndUnpricedHaveNoLabel() {
        assertEquals("", EmcCountText.label(false, BigInteger.TEN, 1));
        assertEquals("", EmcCountText.label(true, BigInteger.TEN, 0));
        assertEquals("", EmcCountText.label(true, BigInteger.TEN, -1));
    }
    @Test void wholeAffordableItemsOnly() {
        assertEquals("3", EmcCountText.label(true, BigInteger.TEN, 3));
        assertEquals("0", EmcCountText.label(true, BigInteger.TEN, 11));
        assertEquals("0", EmcCountText.label(true, BigInteger.valueOf(-1), 1));
    }
    @Test void compactLabelsStayWithinIconWidth() {
        assertEquals("999", EmcCountText.label(true, BigInteger.valueOf(999), 1));
        assertEquals("1K", EmcCountText.label(true, BigInteger.valueOf(1000), 1));
        assertEquals("1.5K", EmcCountText.label(true, BigInteger.valueOf(1599), 1));
        assertEquals("1M", EmcCountText.label(true, BigInteger.valueOf(1000000), 1));
    }
    @Test void hugeBalancesDoNotOverflowLong() {
        assertEquals("1e100", EmcCountText.label(true, BigInteger.TEN.pow(100), 1));
        assertEquals("1", EmcCountText.label(true, BigInteger.valueOf(Long.MAX_VALUE), Long.MAX_VALUE));
    }
}
