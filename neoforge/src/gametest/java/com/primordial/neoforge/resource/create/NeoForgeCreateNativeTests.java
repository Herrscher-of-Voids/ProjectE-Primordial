package com.primordial.neoforge.resource.create;

import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.resource.NeoForgeResourcePort;
import com.primordial.neoforge.resource.NeoForgeResourcePortRegistry;
import com.primordial.neoforge.resource.NeoForgeResourceUnits;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Invoked reflectively only in the installed Create test profile. */
public final class NeoForgeCreateNativeTests {
    private NeoForgeCreateNativeTests() {}

    public static void nativeNetworkPaymentPropagationAndExpiry(GameTestHelper helper) {
        ServerPlayer player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        BlockPos sourcePos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos pressPos = sourcePos.east();
        var shaftState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:shaft"))
                .defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        var pressState = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:mechanical_press"))
                .defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST);
        helper.getLevel().setBlockAndUpdate(sourcePos, shaftState);
        helper.getLevel().setBlockAndUpdate(pressPos, pressState);
        try {
            var shaft = (KineticBlockEntity) helper.getLevel().getBlockEntity(sourcePos);
            var press = (KineticBlockEntity) helper.getLevel().getBlockEntity(pressPos);
            var access = (KineticAccess) shaft;
            var speed = find(shaft, false, Direction.WEST);
            var secondSide = find(shaft, false, Direction.EAST);
            var stress = find(shaft, true, Direction.WEST);
            helper.assertTrue(speed != null && stress != null && secondSide != null,
                    "An actual native shaft must expose speed and stress ports backed by its mixin lease");
            var before = NeoForgeTestPlayers.balance(player);
            helper.assertTrue(speed.receive(32, true) == 32 && speed.amount() == 0 && shaft.getTheoreticalSpeed() == 0
                    && access.primordial$leaseIfPresent() == null,
                    "Native kinetic simulation must not allocate a lease, change speed or create a network");
            helper.assertTrue(purchase(player, speed, 32) == 0 && purchase(player, stress, 4096) == 0
                    && access.primordial$leaseIfPresent() == null && NeoForgeTestPlayers.balance(player).equals(before),
                    "Missing resource certificates block both real kinetic reservation and EMC debit");
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            knowledge.addKnowledge(ItemInfo.fromStack(Kind.SPEED.sample()));
            knowledge.addKnowledge(ItemInfo.fromStack(Kind.STRESS.sample()));
            helper.assertTrue(purchase(player, speed, 32) == 32 && purchase(player, stress, 4096) == 4096,
                    "Learned native kinetic purchases reserve the actual delivered units");
            helper.assertTrue(NeoForgeTestPlayers.balance(player).equals(before
                    .subtract(NeoForgeResourceUnits.price(speed.unit()).multiply(32))
                    .subtract(NeoForgeResourceUnits.price(stress.unit()).multiply(4096))),
                    "Native speed and stress debit exact fractional EMC separately");
            helper.assertTrue(secondSide.amount() == 32 && speed.receive(Long.MAX_VALUE, true) == speed.capacity() - 32
                    && speed.extract(32, false) == 0 && !stress.canExtract(),
                    "All native sides share reservations; temporary kinetics cannot be reclaimed for EMC");
            long tick = helper.getLevel().getGameTime();
            var lease = access.primordial$leaseIfPresent();
            CreateKinetics.advance(shaft, lease, tick + 1);
            helper.assertTrue(shaft.getTheoreticalSpeed() == 32 && press.getTheoreticalSpeed() == 32
                    && shaft.hasNetwork() && shaft.network.equals(press.network),
                    "Paid source speed must propagate through Create's actual rotation topology to the press");
            var network = shaft.getOrCreateNetwork();
            helper.assertTrue(network.members.containsKey(shaft) && network.members.containsKey(press)
                    && network.calculateCapacity() == 4096 && network.calculateStress() > 0
                    && press.getSpeed() == 32 && !press.isOverStressed(),
                    "Paid stress must enter the actual network capacity and power a real native load");
            var savedShaft = shaft.saveWithFullMetadata(helper.getLevel().registryAccess());
            var savedPress = press.saveWithFullMetadata(helper.getLevel().registryAccess());
            var client = shaft.getUpdateTag(helper.getLevel().registryAccess());
            helper.assertTrue(savedShaft.getFloat("Speed") == 0 && !savedShaft.contains("Network")
                    && savedShaft.getBoolean("NeedsSpeedUpdate") && savedPress.getCompound("Network").getFloat("Capacity") == 0,
                    "Saving strips temporary generator speed and paid capacity even from an ordinary network follower");
            helper.assertTrue(client.getFloat("Speed") == 32 && client.getCompound("Network").getFloat("Capacity") == 4096,
                    "Native client packets must still display the real paid speed and capacity");
            CreateKinetics.advance(shaft, lease, tick + 1);
            helper.assertTrue(shaft.getOrCreateNetwork() == network && shaft.getTheoreticalSpeed() == 32,
                    "An accelerated duplicate tick cannot rebuild or extend an already-applied kinetic lease");
            CreateKinetics.advance(shaft, lease, tick + 2);
            helper.assertTrue(lease.activeSpeed == 0 && lease.activeStress == 0 && shaft.getGeneratedSpeed() == 0
                    && shaft.getTheoreticalSpeed() == 0 && press.getTheoreticalSpeed() == 0,
                    "The next unpaid tick must revoke the native source and propagate the stopped state");
            helper.getLevel().setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
            helper.assertTrue(speed.receive(1, false) == 0 && speed.receive(1, true) == 0
                    && NeoForgeResourcePortRegistry.find(shaft, Direction.UP).isEmpty(),
                    "Removed native blocks cannot accept payment through old port references");
        } finally {
            helper.getLevel().setBlockAndUpdate(pressPos, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(sourcePos, Blocks.AIR.defaultBlockState());
            NeoForgeTestPlayers.logout(player);
        }
        helper.succeed();
    }

    public static void nativeGeneratorAndTransmissionPorts(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        for (String id : new String[] { "create:creative_motor", "create:gearbox" }) {
            helper.getLevel().setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState());
            var machine = (KineticBlockEntity) helper.getLevel().getBlockEntity(pos);
            var ports = NeoForgeResourcePortRegistry.find(machine, Direction.UP);
            helper.assertTrue(!CreateKinetics.canDrive(machine) && ports.stream().noneMatch(p -> p.unit().equals(NeoForgeCreateResourceAdapter.SPEED))
                    && ports.stream().anyMatch(p -> p.unit().equals(NeoForgeCreateResourceAdapter.STRESS)),
                    "Native generators and directional transmission keep their own source logic and expose only stress supplementation");
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    private static NeoForgeResourcePort find(KineticBlockEntity machine, boolean stress, Direction side) {
        ResourceLocation unit = stress ? NeoForgeCreateResourceAdapter.STRESS : NeoForgeCreateResourceAdapter.SPEED;
        return NeoForgeResourcePortRegistry.find(machine, side).stream().filter(port -> port.unit().equals(unit)).findFirst().orElse(null);
    }
    private static long purchase(ServerPlayer player, NeoForgeResourcePort port, long requested) {
        return NeoForgeResourceTrades.purchaseResource(player.server, player.getUUID(), port.unit(), requested,
                units -> port.receive(units, false));
    }
}
