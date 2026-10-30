/** Discovers native AE2 tests only when the optional runtime mod is installed. */
package com.primordial.neoforge.compat.ae2;

import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeOptionalAe2GameTests {
    private NeoForgeOptionalAe2GameTests() {}

    @GameTestGenerator
    @SuppressWarnings("unchecked")
    public static List<TestFunction> tests() {
        if (ModList.get().isLoaded("ae2")) {
            try {
                return (List<TestFunction>) Class.forName(
                        "com.primordial.neoforge.compat.ae2.NeoForgeAe2GridGameTests")
                        .getMethod("tests").invoke(null);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot discover installed AE2 native-grid integration tests", failure);
            }
        }
        return List.of(new TestFunction("defaultBatch", "ae2_optional_dependency_absent",
                "primordial:capability_proxy_empty", Rotation.NONE, 100, 0, true, helper -> {
                    try {
                        Class.forName("appeng.api.networking.GridHelper");
                        throw new GameTestAssertException("AE2 API leaked into the no-AE2 runtime");
                    } catch (ClassNotFoundException expected) {
                        helper.succeed();
                    }
                }));
    }
}
