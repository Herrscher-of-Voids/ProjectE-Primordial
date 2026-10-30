/** Discovers Beyond Dimensions integration tests only when the optional runtime mod is installed. */
package com.primordial.neoforge.compat.beyonddimensions;

import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeOptionalBeyondDimensionsGameTests {
    private NeoForgeOptionalBeyondDimensionsGameTests() {}

    @GameTestGenerator
    @SuppressWarnings("unchecked")
    public static List<TestFunction> tests() {
        if (ModList.get().isLoaded("beyonddimensions")) {
            try {
                return (List<TestFunction>) Class.forName(
                        "com.primordial.neoforge.compat.beyonddimensions.NeoForgeBeyondDimensionsGameTests")
                        .getMethod("tests").invoke(null);
            } catch (ReflectiveOperationException | LinkageError failure) {
                throw new IllegalStateException("Cannot discover installed Beyond Dimensions integration tests", failure);
            }
        }
        return List.of(new TestFunction("defaultBatch", "beyond_dimensions_optional_dependency_absent",
                "primordial:capability_proxy_empty", Rotation.NONE, 100, 0, true, helper -> {
                    try {
                        Class.forName("com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet");
                        throw new GameTestAssertException("Beyond Dimensions API leaked into the no-mod runtime");
                    } catch (ClassNotFoundException expected) {
                        helper.succeed();
                    }
                }));
    }
}
