/**
 * 【文件职责】回归验证 FTB 阶段奖励接受的阶段 ID 格式及存储长度边界。
 * 【变更记录 #1】2026-10-13 03:56:00（第 1 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：无。
 * - 新增：覆盖命名空间/路径合法值、缺少命名空间、非法字符、空路径、长度溢出和 null。
 * - 修改：无；测试只调用阶段格式验证，不连接任务存档或 FTB 运行服务。
 * - 用途：确保任务编辑及序列化数据使用同一阶段 ID 限制，避免发放无法存储的阶段。
 */
package com.primordial.compat.ftbquest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrimordialStageRewardTest {
    @Test
    void acceptsNamespacedStageIdsWithinStorageLimit() {
        assertTrue(PrimordialStageReward.validStage("pack:fluid_tier_1"));
        assertTrue(PrimordialStageReward.validStage("pack:fluid/tier-1"));
        assertFalse(PrimordialStageReward.validStage("fluid_tier_1"));
        assertFalse(PrimordialStageReward.validStage("Pack:Fluid"));
        assertFalse(PrimordialStageReward.validStage("pack:"));
        assertFalse(PrimordialStageReward.validStage("pack:" + "a".repeat(124)));
        assertFalse(PrimordialStageReward.validStage(null));
    }
}
