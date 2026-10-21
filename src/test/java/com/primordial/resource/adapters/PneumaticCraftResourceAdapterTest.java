/**
 * 【文件职责】验证气动机器的空气容量采用安全压力等级并随容积升级改变。
 * 【变更记录 #1】2026-09-16 13:35:51（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：1000 × 20 = 20000、升级动态读取、缺少接口/非法数值/原生 int 上限测试。
 * - 修改：测试对象同时返回固定 maxPressure=10 和实际 getDangerPressure=20，防止错误 API 再次引入。
 * - 用途：不依赖可选模组启动，直接测试适配器的生产容量读取方法。
 */
package com.primordial.resource.adapters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PneumaticCraftResourceAdapterTest {
    @Test void capacityUsesSafeTierInsteadOfInheritedTenBarDefault() {
        var air = new Air();
        assertEquals(20000, PneumaticCraftResourceAdapter.maxAirCapacity(air));
        air.volume = 2000;
        assertEquals(40000, PneumaticCraftResourceAdapter.maxAirCapacity(air));
        air.pressure = 5;
        assertEquals(10000, PneumaticCraftResourceAdapter.maxAirCapacity(air));
    }

    @Test void missingOrInvalidCapacityNeverMeansInfiniteInjection() {
        assertEquals(0, PneumaticCraftResourceAdapter.maxAirCapacity(new Object()));
        var air = new Air();
        air.pressure = Double.NaN;
        assertEquals(0, PneumaticCraftResourceAdapter.maxAirCapacity(air));
        air.pressure = -1;
        assertEquals(0, PneumaticCraftResourceAdapter.maxAirCapacity(air));
        air.pressure = 20;
        air.volume = Integer.MAX_VALUE;
        assertEquals(Integer.MAX_VALUE, PneumaticCraftResourceAdapter.maxAirCapacity(air));
    }

    @Test void capacityRoundsDownToAvoidPassingSafePressure() {
        var air = new Air(); air.volume = 3; air.pressure = 1.5;
        assertEquals(4, PneumaticCraftResourceAdapter.maxAirCapacity(air));
    }

    public static final class Air {
        int volume = 1000;
        double pressure = 20;
        public int getVolume() { return volume; }
        public double getDangerPressure() { return pressure; }
        public float maxPressure() { return 10; }
    }
}
