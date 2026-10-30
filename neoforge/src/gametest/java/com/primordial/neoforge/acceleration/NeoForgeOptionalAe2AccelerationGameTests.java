package com.primordial.neoforge.acceleration;

import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeOptionalAe2AccelerationGameTests {
    private NeoForgeOptionalAe2AccelerationGameTests() {}
    @GameTestGenerator
    public static List<TestFunction> tests() {
        if (!ModList.get().isLoaded("ae2")) return List.of();
        return List.of(new TestFunction("native_ae2_acceleration", "native_ae2_acceleration.public_api_node_lifecycle",
                "primordial:capability_proxy_empty", Rotation.NONE, 400, 0, true, helper -> {
                    try {
                        Class.forName("com.primordial.neoforge.acceleration.NeoForgeAe2AccelerationNativeTests")
                                .getMethod("publicApiNodeLifecycle", net.minecraft.gametest.framework.GameTestHelper.class)
                                .invoke(null, helper);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                        if (failure.getCause() instanceof Error error) throw error;
                        throw new IllegalStateException("AE2 acceleration test failed", failure.getCause());
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException("Load installed AE2 acceleration test", failure);
                    }
                }));
    }
}
