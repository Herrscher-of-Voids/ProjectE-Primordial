/**
 * 【文件职责】验证动力付款期限、共享额度和时间加速边界，无需可选 API 或伪造世界。
 * 【变更记录 #1】2026-09-16 20:02:43（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：模拟不修改、方向合计上限、只供下一 tick、欠费终止及重复机器 tick 去重测试。
 * - 修改：测试纯付款状态；实际 Create 传播由隔离 Forge 环境验证。
 * - 用途：防止停用后永久供能和多方向重复付费/突破上限。
 */
package com.primordial.resource;

import com.primordial.resource.create.KineticLease;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KineticLeaseTest {
    @Test void simulationDoesNotReserveOrPower() {
        var lease = new KineticLease();
        assertEquals(256, lease.receive(10, false, Long.MAX_VALUE, 256, true));
        assertEquals(0, lease.reserved(10, false));
        assertEquals(0, lease.due(11, false));
    }

    @Test void multiplePortsShareLimitAndDifferentResourcesDoNotOverwrite() {
        var lease = new KineticLease();
        assertEquals(200, lease.receive(10, false, 200, 256, false));
        assertEquals(56, lease.receive(10, false, 200, 256, false));
        assertEquals(0, lease.receive(10, false, 200, 256, false));
        assertEquals(4096, lease.receive(10, true, 4096, 8192, false));
        assertEquals(256, lease.due(11, false));
        assertEquals(4096, lease.due(11, true));
    }

    @Test void onePaymentOnlyPowersNextTickAndNeverSurvivesTimeJump() {
        var lease = new KineticLease();
        lease.receive(10, false, 128, 256, false);
        assertEquals(0, lease.due(10, false));
        assertEquals(128, lease.due(11, false));
        assertEquals(0, lease.due(12, false));
        assertEquals(0, lease.due(500, false));
        assertEquals(0, lease.reserved(11, false));
    }

    @Test void renewalDoesNotCarryUnpaidStressForward() {
        var lease = new KineticLease();
        lease.receive(10, true, 4096, 8192, false);
        lease.receive(11, false, 32, 256, false);
        assertEquals(0, lease.due(12, true));
        assertEquals(32, lease.due(12, false));
        assertTrue(lease.beginTick(12));
        assertFalse(lease.beginTick(12));
        assertTrue(lease.beginTick(13));
    }
}
