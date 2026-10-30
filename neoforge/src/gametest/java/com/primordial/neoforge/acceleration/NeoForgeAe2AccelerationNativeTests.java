package com.primordial.neoforge.acceleration;

import java.util.EnumSet;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;

/** Native managed nodes and the public grid service; no internal AE2 scheduler classes. */
public final class NeoForgeAe2AccelerationNativeTests {
    private NeoForgeAe2AccelerationNativeTests() {}

    public static void publicApiNodeLifecycle(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos cellPos = pos.east();
        helper.getLevel().setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        var device = new TickableFurnace(pos);
        helper.getLevel().setBlockEntity(device);
        device.managed.create(helper.getLevel(), pos);
        helper.getLevel().setBlockAndUpdate(cellPos, BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        helper.assertTrue(new Ae2AccelerationAdapter().apply(device) == null,
                "An unpowered native tick-service node must not be accelerated");
        awaitCell(helper, device, cellPos, 160);
    }

    private static void awaitCell(GameTestHelper helper, TickableFurnace device, BlockPos cellPos, int retries) {
        try {
            var cellHost = GridHelper.getNodeHost(helper.getLevel(), cellPos);
            IGridNode cell = cellHost == null ? null : cellHost.getGridNode(Direction.WEST);
            if (cell == null) {
                helper.assertTrue(retries > 0, "The native creative energy node must initialize");
                helper.runAfterDelay(1, () -> awaitCell(helper, device, cellPos, retries - 1));
                return;
            }
            // The fixture owns a public direct host; connect its actual native node through AE2's API.
            GridHelper.createConnection(device.managed.getNode(), cell);
            awaitPowered(helper, device, cellPos, retries);
        } catch (RuntimeException | Error failure) { cleanup(helper, device, cellPos); throw failure; }
    }

    private static void awaitPowered(GameTestHelper helper, TickableFurnace device, BlockPos cellPos, int retries) {
        try {
            if (!device.managed.isActive()) {
                helper.assertTrue(retries > 0, "The native fixture node must join a powered grid");
                helper.runAfterDelay(1, () -> awaitPowered(helper, device, cellPos, retries - 1));
                return;
            }
            try {
                var raw = AccelerationMachineCompat.ticker(device);
                helper.assertTrue(raw instanceof AccelerationMachineCompat.CheckedTicker,
                        "The lazy NeoForge integration must resolve the public AE2 tick-service adapter");
                var ticker = (AccelerationMachineCompat.CheckedTicker) raw;
                helper.assertTrue(ticker.valid() && !ticker.idle(), "The active native device must be a valid awake target");
                int before = device.requests;
                ticker.tick(helper.getLevel(), device.getBlockPos(), device.getBlockState(), device);
                helper.assertTrue(device.requests == before + 1 && device.lastElapsed == 1,
                        "Six exposed sides must invoke exactly one selected native service with one extra tick");
                device.sleeping = true;
                ticker.tick(helper.getLevel(), device.getBlockPos(), device.getBlockState(), device);
                helper.assertTrue(ticker.idle() && device.requests == before + 1,
                        "A sleeping public ticking request cannot receive acceleration callbacks");
                device.sleeping = false; device.sleepAfterRequest = true;
                ticker.tick(helper.getLevel(), device.getBlockPos(), device.getBlockState(), device);
                ticker.tick(helper.getLevel(), device.getBlockPos(), device.getBlockState(), device);
                helper.assertTrue(ticker.idle() && device.requests == before + 2,
                        "A service returning SLEEP must stop further extra requests in the current accelerator run");
                device.managed.destroy();
                helper.assertTrue(!ticker.valid() && new Ae2AccelerationAdapter().apply(device) == null,
                        "Destroyed or replaced native nodes invalidate previously bound service callbacks");
                helper.getLevel().setBlockAndUpdate(device.getBlockPos(), Blocks.AIR.defaultBlockState());
                ticker.tick(helper.getLevel(), device.getBlockPos(), device.getBlockState(), device);
                helper.assertTrue(device.requests == before + 2, "A removed block cannot receive a stale AE2 acceleration call");
                helper.succeed();
            } finally { cleanup(helper, device, cellPos); }
        } catch (RuntimeException | Error failure) { cleanup(helper, device, cellPos); throw failure; }
    }

    private static void cleanup(GameTestHelper helper, TickableFurnace device, BlockPos cellPos) {
        device.managed.destroy();
        helper.getLevel().setBlockAndUpdate(device.getBlockPos(), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(cellPos, Blocks.AIR.defaultBlockState());
    }

    private static final class TickableFurnace extends FurnaceBlockEntity implements IInWorldGridNodeHost, IGridTickable {
        final IManagedGridNode managed;
        int requests, lastElapsed;
        boolean sleeping, sleepAfterRequest;
        TickableFurnace(BlockPos pos) {
            super(pos, Blocks.FURNACE.defaultBlockState());
            managed = GridHelper.createManagedNode(this, (owner, node) -> owner.setChanged())
                    .setInWorldNode(true).setExposedOnSides(EnumSet.allOf(Direction.class))
                    .setIdlePowerUsage(0).addService(IGridTickable.class, this);
        }
        @Override public IGridNode getGridNode(Direction side) { return managed.getNode(); }
        @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(20, 20, sleeping); }
        @Override public TickRateModulation tickingRequest(IGridNode node, int elapsed) {
            requests++; lastElapsed = elapsed;
            return sleepAfterRequest ? TickRateModulation.SLEEP : TickRateModulation.SAME;
        }
    }
}
