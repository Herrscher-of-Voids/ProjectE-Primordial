/**
 * 【文件职责】按实际模组列表发现独立化学品集成测试，公共测试类不引用 Mekanism API。
 * 【变更记录 #1】2026-10-22 22:21:10（第 1 次备注 · 2.0.0-neo-dev 可选联动双环境验收）
 * - 删除：无。
 * - 新增：缺席时验证 API 确实不在运行时；存在时反射生成原生能力测试。
 * - 修改：测试发现失败直接使验收失败，不把可选测试被静默跳过当成通过。
 * - 用途：证明 compileOnly 没有掩盖未安装 Mekanism 的服务端类加载问题。
 */
package com.primordial.neoforge.blackhole;

import java.util.List;
import java.util.ArrayList;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.compat.NeoForgeMachineChemicalBridge;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeOptionalChemicalGameTests {
    private NeoForgeOptionalChemicalGameTests() {}

    @GameTestGenerator
    @SuppressWarnings("unchecked")
    public static List<TestFunction> tests() {
        if (ModList.get().isLoaded("mekanism")) {
            try {
                List<TestFunction> tests = new ArrayList<>((List<TestFunction>) Class.forName(
                        "com.primordial.neoforge.compat.NeoForgeMekanismRecoveryTests")
                        .getMethod("tests").invoke(null));
                tests.addAll((List<TestFunction>) Class.forName(
                        "com.primordial.neoforge.machine.NeoForgeMachineChemicalGameTests")
                        .getMethod("tests").invoke(null));
                return List.copyOf(tests);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot load installed Mekanism integration tests", failure);
            }
        }
        return List.of(new TestFunction("defaultBatch", "chemical_optional_dependency_absent",
                "primordial:capability_proxy_empty", Rotation.NONE, 100, 0, true, helper -> {
                    if (NeoForgeMachineChemicalBridge.available())
                        throw new GameTestAssertException("Machine chemical bridge must stay unavailable without Mekanism");
                    try {
                        Class.forName("mekanism.api.chemical.IChemicalHandler");
                        throw new GameTestAssertException("Mekanism API leaked into no-Mekanism runtime");
                    } catch (ClassNotFoundException expected) {
                        helper.succeed();
                    }
                }));
    }
}
