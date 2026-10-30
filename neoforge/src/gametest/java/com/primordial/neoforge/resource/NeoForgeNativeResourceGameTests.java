package com.primordial.neoforge.resource;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual installed block entities; the base test environment never links optional API classes. */
@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeNativeResourceGameTests {
    private NeoForgeNativeResourceGameTests() {}

    @GameTestGenerator
    public static List<TestFunction> installedPorts() {
        List<TestFunction> tests = new ArrayList<>();
        if (ModList.get().isLoaded("naturesaura")) tests.add(test("nature_altar", h -> nativeRoundTrip(h,
                "naturesaura:nature_altar", "naturesaura:aura", false)));
        if (ModList.get().isLoaded("pneumaticcraft")) tests.add(test("pneumatic_compressor", h -> nativeRoundTrip(h,
                "pneumaticcraft:air_compressor", "pneumaticcraft:pressure", false)));
        if (ModList.get().isLoaded("ars_nouveau")) tests.add(test("source_jar", h -> nativeRoundTrip(h,
                "ars_nouveau:source_jar", "ars_nouveau:source", true)));
        return tests;
    }

    private static TestFunction test(String id, java.util.function.Consumer<GameTestHelper> run) {
        return new TestFunction("native_resource", "native_resource." + id,
                "primordial:capability_proxy_empty", 100, 0L, true, run);
    }

    private static void nativeRoundTrip(GameTestHelper helper, String blockId, String unitId, boolean initializeSource) {
        BlockPos relative = new BlockPos(1, 1, 1);
        ResourceLocation id = ResourceLocation.parse(blockId);
        Block block = BuiltInRegistries.BLOCK.get(id);
        helper.assertTrue(block != Blocks.AIR, "The optional mod must register its actual test block " + id);
        helper.setBlock(relative, block);
        BlockEntity machine = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        helper.assertTrue(machine != null, "The actual optional block entity must exist");
        if (initializeSource) {
            try { machine.getClass().getMethod("setSource", int.class).invoke(machine, 0); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Initialize native Source storage", failure); }
        }
        ResourceLocation unit = ResourceLocation.parse(unitId);
        Direction selectedSide = Direction.UP;
        NeoForgeResourcePort port = null;
        for (Direction side : Direction.values()) {
            var candidate = NeoForgeResourcePortRegistry.find(machine, side).stream()
                    .filter(p -> p.unit().equals(unit)).findFirst().orElse(null);
            if (candidate != null) { port = candidate; selectedSide = side; break; }
        }
        helper.assertTrue(port != null, "Actual optional machine must expose the expected resource port " + unit);
        long before = port.amount();
        long maximum = port.capacity();
        helper.assertTrue(maximum > before && maximum <= Integer.MAX_VALUE, "Native capacity must be finite and writable");
        long requested = Math.min(125, maximum - before);
        helper.assertTrue(port.receive(requested, true) == requested && port.amount() == before,
                "Native receive simulation must not change resource state");
        long delivered = port.receive(requested, false);
        helper.assertTrue(delivered == requested && port.amount() == before + requested,
                "Native receive reports the actual storage delta");
        helper.assertTrue(port.extract(delivered, true) == delivered && port.amount() == before + delivered,
                "Native extract simulation must remain read-only");
        helper.assertTrue(port.extract(delivered, false) == delivered && port.amount() == before,
                "Native recovery restores the original storage without a return-value assumption");
        if (unitId.equals("pneumaticcraft:pressure")) assertSafePressureCapacity(helper, machine, selectedSide, maximum);
        helper.setBlock(relative, Blocks.AIR);
        helper.assertTrue(port.receive(1, false) == 0 && NeoForgeResourcePortRegistry.find(machine, Direction.UP).isEmpty(),
                "Removed native machines cannot accept resources through a stale port");
        helper.succeed();
    }

    private static void assertSafePressureCapacity(GameTestHelper helper, BlockEntity machine, Direction side, long maximum) {
        try {
            Class<?> api = Class.forName("me.desht.pneumaticcraft.api.PNCCapabilities");
            var result = (java.util.Optional<?>) api.getMethod("getAirHandler", BlockEntity.class, Direction.class)
                    .invoke(null, machine, side);
            Object air = result.orElseThrow();
            long volume = ((Number) air.getClass().getMethod("getVolume").invoke(air)).longValue();
            double danger = ((Number) air.getClass().getMethod("getDangerPressure").invoke(air)).doubleValue();
            helper.assertTrue(maximum == (long) Math.floor(Math.min(Integer.MAX_VALUE, volume * danger)),
                    "PNC capacity uses the live upgraded volume multiplied by danger pressure");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Read native pneumatic capacity", failure); }
    }
}
