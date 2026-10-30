package com.primordial.neoforge.resource;

import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** No optional API links: these tests use actual installed Botania Revived blocks and native public methods. */
@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeBotaniaResourceGameTests {
    private NeoForgeBotaniaResourceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void absentBotaniaDoesNotRegisterOrExposeMana(GameTestHelper helper) {
        if (!ModList.get().isLoaded("botania")) helper.assertTrue(!NeoForgeResourcePortRegistry.registeredIds().contains("botania"),
                "Without Botania, discovering native adapters must not register a mana API");
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.FURNACE);
        helper.assertTrue(find(helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(1, 1, 1)))) == null,
                "Ordinary vanilla machines cannot expose artificial mana storage");
        helper.succeed();
    }

    @GameTestGenerator
    public static List<TestFunction> installedBotania() {
        if (!ModList.get().isLoaded("botania")) return List.of();
        return List.of(new TestFunction("native_botania", "native_botania.storage_round_trip",
                        "primordial:capability_proxy_empty", 100, 0L, true, NeoForgeBotaniaResourceGameTests::storageRoundTrip),
                new TestFunction("native_botania", "native_botania.purchase_and_recovery",
                        "primordial:capability_proxy_empty", 100, 0L, true, NeoForgeBotaniaResourceGameTests::purchaseAndRecovery));
    }

    private static void storageRoundTrip(GameTestHelper helper) {
        String[] ids = { "botania:mana_pool", "botania:diluted_pool", "botania:mana_spreader", "botania:creative_pool" };
        BlockEntity[] machines = new BlockEntity[ids.length];
        // Spreaders draw from adjacent pools on their own tick; keep the creative source two steps away.
        for (int i = 0; i < ids.length; i++) machines[i] = place(helper,
                i == 3 ? new BlockPos(2, 2, 2) : new BlockPos(i % 2 + 1, 1, i / 2 + 1), ids[i]);
        try {
            if (((Number) machines[0].getClass().getMethod("getMaxMana").invoke(machines[0])).longValue() < 0)
                helper.assertTrue(find(machines[0]) == null, "Discovery cannot initialize a pool before Botania's own first tick");
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Read native pool capacity", failure); }
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(find(machines[3]) == null, "A creative pool must never mint EMC from infinite mana");
            if (ModList.get().isLoaded("beyonddimensions")) try {
                helper.assertTrue(Boolean.FALSE.equals(Class.forName("com.wintercogs.beyonddimensions.integration.ModPresence")
                                .getMethod("isLoaded", String.class).invoke(null, "botania")),
                        "BD must skip its incompatible legacy Botania module while the actual Revived mana port remains usable");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Inspect BD Botania compatibility gate", failure); }
            if (ModList.get().isLoaded("pneumaticcraft")) try {
                Class<?> type = Class.forName("me.desht.pneumaticcraft.common.thirdparty.ThirdPartyManager");
                var integrations = type.getDeclaredField("thirdPartyMods");
                integrations.setAccessible(true);
                helper.assertTrue(((List<?>) integrations.get(type.getMethod("instance").invoke(null))).stream()
                                .noneMatch(module -> module.getClass().getName().equals("me.desht.pneumaticcraft.common.thirdparty.botania.Botania")),
                        "PNC must skip its incompatible legacy Botania module while both native Primordial resource adapters remain usable");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Inspect PNC Botania compatibility gate", failure); }
            for (int i = 0; i < 3; i++) {
                BlockEntity machine = machines[i];
                NeoForgeResourcePort port = find(machine);
                helper.assertTrue(port != null && port.capacity() == (i == 0 ? 1_000_000 : i == 1 ? 10_000 : 1_000),
                        "Actual Botania pools and spreaders expose their native finite capacity");
                long before = nativeAmount(machine);
                helper.assertTrue(port.receive(Long.MAX_VALUE, true) == port.capacity() - before
                                && port.extract(Long.MAX_VALUE, true) == before && nativeAmount(machine) == before,
                        "Both simulations are bounded and leave Botania storage unchanged");
                long received = port.receive(125, false);
                long afterReceive = nativeAmount(machine);
                long extracted = port.extract(50, false);
                long afterExtract = nativeAmount(machine);
                helper.assertTrue(received == 125 && afterReceive == before + 125 && extracted == 50 && afterExtract == before + 75,
                        "Signed native receiveMana reports actual storage deltas for " + ids[i] + ": before=" + before
                                + ", receive=" + received + ", afterReceive=" + afterReceive + ", extract=" + extracted + ", afterExtract=" + afterExtract);
                for (Direction side : Direction.values()) helper.assertTrue(NeoForgeResourcePortRegistry.find(machine, side).stream()
                                .anyMatch(other -> other.unit().equals(port.unit()) && other.amount() == before + 75),
                        "All exposed sides share the actual native mana balance");
                helper.assertTrue(port.receive(Long.MAX_VALUE, false) == port.capacity() - before - 75
                                && nativeAmount(machine) == port.capacity() && port.receive(1, false) == 0,
                        "Oversized requests cannot overflow native integer storage or overfill it");
                helper.assertTrue(port.extract(Long.MAX_VALUE, false) == port.capacity() && nativeAmount(machine) == 0
                                && port.extract(1, false) == 0 && port.receive(-1, false) == 0,
                        "Oversized extraction and negative requests never create negative mana");
                helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), Blocks.AIR.defaultBlockState());
                helper.assertTrue(port.receive(1, true) == 0 && port.receive(1, false) == 0
                                && port.extract(1, false) == 0 && !port.canReceive() && !port.canExtract(),
                        "Stale mana ports stop at machine removal, including simulation");
            }
            helper.succeed();
        });
    }

    private static void purchaseAndRecovery(GameTestHelper helper) {
        BlockEntity machine = place(helper, new BlockPos(1, 1, 1), "botania:mana_pool");
        helper.runAfterDelay(2, () -> {
            var player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
            try {
                NeoForgeResourcePort port = find(machine);
                helper.assertTrue(port != null, "The actual Botania mana pool must expose a native resource endpoint");
                var before = NeoForgeTestPlayers.balance(player);
                helper.assertTrue(NeoForgeResourceTrades.purchaseResource(player.server, player.getUUID(), port.unit(), 125,
                                amount -> port.receive(amount, false)) == 0 && nativeAmount(machine) == 0
                                && NeoForgeTestPlayers.balance(player).equals(before),
                        "Missing mana knowledge blocks both native delivery and EMC debit");
                player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).addKnowledge(ItemInfo.fromStack(Kind.MANA.sample()));
                helper.assertTrue(NeoForgeResourceTrades.purchaseResource(player.server, player.getUUID(), port.unit(), 125,
                                amount -> port.receive(amount, false)) == 125 && nativeAmount(machine) == 125
                                && NeoForgeTestPlayers.balance(player).equals(before.subtract(NeoForgeResourceUnits.price(port.unit()).multiply(125))),
                        "Learned native mana purchase debits the exact fractional EMC price");
                helper.assertTrue(NeoForgeResourceTrades.reclaimResource(player.server, player.getUUID(), port.unit(), Long.MAX_VALUE,
                                amount -> port.extract(amount, false)) == 125 && nativeAmount(machine) == 0
                                && NeoForgeTestPlayers.balance(player).equals(before),
                        "Recovery credits only the native extracted mana and exactly restores the original EMC balance");
            } finally {
                helper.getLevel().setBlockAndUpdate(machine.getBlockPos(), Blocks.AIR.defaultBlockState());
                NeoForgeTestPlayers.logout(player);
            }
            helper.succeed();
        });
    }

    private static BlockEntity place(GameTestHelper helper, BlockPos pos, String id) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        helper.assertTrue(block != Blocks.AIR, "The installed Botania port must register its actual test block " + id);
        helper.setBlock(pos, block);
        BlockEntity machine = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(machine != null, "Botania must create its actual native block entity");
        return machine;
    }

    private static NeoForgeResourcePort find(BlockEntity machine) {
        return NeoForgeResourcePortRegistry.find(machine, Direction.UP).stream()
                .filter(port -> port.unit().equals(NeoForgeBotaniaResourceAdapter.UNIT)).findFirst().orElse(null);
    }

    private static long nativeAmount(BlockEntity machine) {
        try { return ((Number) machine.getClass().getMethod("getCurrentMana").invoke(machine)).longValue(); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Read native Botania mana", failure); }
    }
}
