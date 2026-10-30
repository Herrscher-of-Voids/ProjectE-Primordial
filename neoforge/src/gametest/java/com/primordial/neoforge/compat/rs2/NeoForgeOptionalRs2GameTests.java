/** Discovers native RS2 tests only when the optional runtime mod is installed. */
package com.primordial.neoforge.compat.rs2;

import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeOptionalRs2GameTests {
    private NeoForgeOptionalRs2GameTests() {}

    @GameTestGenerator
    @SuppressWarnings("unchecked")
    public static List<TestFunction> tests() {
        if (ModList.get().isLoaded("refinedstorage")) {
            try {
                return (List<TestFunction>) Class.forName(
                        "com.primordial.neoforge.compat.rs2.NeoForgeRs2GridGameTests")
                        .getMethod("tests").invoke(null);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot discover installed RS2 native-grid integration tests", failure);
            }
        }
        return List.of(new TestFunction("defaultBatch", "rs2_optional_dependency_absent",
                "primordial:capability_proxy_empty", Rotation.NONE, 100, 0, true, helper -> {
                    try {
                        Class.forName("com.refinedmods.refinedstorage.api.network.Network");
                        throw new GameTestAssertException("RS2 API leaked into the no-RS2 runtime");
                    } catch (ClassNotFoundException expected) {
                        helper.succeed();
                    }
                }));
    }
}
