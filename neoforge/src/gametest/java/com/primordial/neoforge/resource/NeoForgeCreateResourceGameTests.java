package com.primordial.neoforge.resource;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.resource.create.KineticLease;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The always-loaded test class contains no Create API references. */
@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeCreateResourceGameTests {
    private NeoForgeCreateResourceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void kineticPaymentsShareCapsAndExpireWithoutOptionalApis(GameTestHelper helper) {
        var lease = new KineticLease();
        helper.assertTrue(lease.receive(10, false, Long.MAX_VALUE, 256, true) == 256
                && lease.reserved(10, false) == 0 && lease.due(11, false) == 0,
                "A kinetic simulation must not create a paid reservation or power");
        helper.assertTrue(lease.receive(10, false, 200, 256, false) == 200
                && lease.receive(10, false, 200, 256, false) == 56
                && lease.receive(10, false, 200, 256, false) == 0
                && lease.receive(10, true, 4096, 8192, false) == 4096,
                "Every side shares one speed cap and a separate stress cap");
        helper.assertTrue(lease.due(10, false) == 0 && lease.due(11, false) == 256
                && lease.due(11, true) == 4096 && lease.due(12, false) == 0
                && lease.due(500, true) == 0 && lease.reserved(11, false) == 0,
                "Payment authorizes only the next world tick and never survives a time jump");
        lease.receive(11, false, 32, 256, false);
        helper.assertTrue(lease.due(12, false) == 32 && lease.due(12, true) == 0
                && lease.beginTick(12) && !lease.beginTick(12) && lease.beginTick(13),
                "A new tick cannot renew unpaid stress and accelerated same-tick calls cannot prolong authorization");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
        var furnace = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)));
        helper.assertTrue(NeoForgeResourcePortRegistry.find(furnace, Direction.UP).stream()
                .noneMatch(port -> port.unit().getNamespace().equals("create")),
                "Ordinary machines cannot expose artificial kinetic endpoints");
        if (!ModList.get().isLoaded("create")) helper.assertTrue(!NeoForgeResourcePortRegistry.registeredIds().contains("create"),
                "Without Create, adapter discovery must load successfully without registering an optional endpoint");
        helper.succeed();
    }

    @GameTestGenerator
    public static List<TestFunction> nativeKinetics() {
        List<TestFunction> tests = new ArrayList<>();
        if (ModList.get().isLoaded("create")) for (String method : List.of(
                "nativeNetworkPaymentPropagationAndExpiry", "nativeGeneratorAndTransmissionPorts"))
            tests.add(new TestFunction("native_create", "native_create." + method,
                    "primordial:capability_proxy_empty", 100, 0L, true, helper -> runNative(method, helper)));
        return tests;
    }

    private static void runNative(String method, GameTestHelper helper) {
        try {
            Class.forName("com.primordial.neoforge.resource.create.NeoForgeCreateNativeTests")
                    .getMethod(method, GameTestHelper.class).invoke(null, helper);
        } catch (InvocationTargetException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Native kinetic test failed", cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Load installed Create test", failure); }
    }
}
