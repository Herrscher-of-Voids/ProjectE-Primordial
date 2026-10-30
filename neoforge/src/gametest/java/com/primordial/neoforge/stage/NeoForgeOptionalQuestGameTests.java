/**
 * 【文件职责】按实际模组列表发现 FTB 奖励测试，使基础服务端不解析任何 FTB 类型。
 * 【变更记录 #1】2026-10-23 16:22:24（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无；新增：缺席类加载断言及存在时的反射测试发现。
 * - 修改：安装 FTB 后发现失败会直接阻止测试，不能将兼容代码静默跳过当成通过。
 * - 用途：同时验证 compileOnly 隔离与实际奖励生命周期；测试不进入生产 JAR。
 */
package com.primordial.neoforge.stage;

import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeOptionalQuestGameTests {
    private NeoForgeOptionalQuestGameTests() {}

    @GameTestGenerator
    @SuppressWarnings("unchecked")
    public static List<TestFunction> tests() {
        if (ModList.get().isLoaded("ftbquests")) {
            try {
                return (List<TestFunction>) Class.forName("com.primordial.neoforge.compat.ftbquests.NeoForgeFtbStageRewardGameTests")
                        .getMethod("tests").invoke(null);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot discover installed FTB Quests stage reward tests", failure);
            }
        }
        return List.of(new TestFunction("defaultBatch", "ftb_quests_optional_dependency_absent",
                "primordial:capability_proxy_empty", Rotation.NONE, 100, 0, true, helper -> {
                    try {
                        Class.forName("dev.ftb.mods.ftbquests.quest.reward.Reward");
                        throw new GameTestAssertException("FTB Quests API leaked into the no-FTB runtime");
                    } catch (ClassNotFoundException expected) {
                        helper.succeed();
                    }
                }));
    }
}
