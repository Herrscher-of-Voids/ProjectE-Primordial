package com.primordial.neoforge.equipment;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeEntityRecovery;
import com.primordial.neoforge.test.NeoForgeTestPlayers;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeSingularityGameTests {
    private NeoForgeSingularityGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void lifetimeSaveAndPullRemainBounded(GameTestHelper helper) {
        var position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 30, 1)));
        var hole = NeoForgeSingularityEntity.create(helper.getLevel(), UUID.randomUUID(), BigInteger.ONE, position);
        helper.assertTrue(hole.remainingTicks().intValueExact() == 600 && hole.radius() == 8,
                "A level-one singularity must preserve the original radius and 600-tick life");
        var motion = NeoForgeSingularityEntity.boundedPullVelocity(new Vec3(Double.POSITIVE_INFINITY, 0, 0),
                new Vec3(200, 0, 0), 60_000_000);
        helper.assertTrue(Double.isFinite(motion.lengthSqr()) && motion.length() <= 2.5,
                "Restored or excessive item velocity must be finite and capped");
        var fusedMotion = NeoForgeSingularityEntity.boundedPullVelocity(Vec3.ZERO, new Vec3(200, 0, 0), 60_000_000, 1e12);
        helper.assertTrue(fusedMotion.length() <= 8 && NeoForgeSingularityEntity.radius(BigInteger.TEN.pow(30)) == 60_000_000,
                "Fusion pull and huge levels must retain engine-safe numerical limits");
        var tag = new CompoundTag(); hole.addAdditionalSaveData(tag);
        tag.putString("RemainingTicks", "999999");
        var loaded = new NeoForgeSingularityEntity(NeoForgeModRegistries.SINGULARITY.get(), helper.getLevel());
        loaded.readAdditionalSaveData(tag);
        helper.assertTrue(loaded.owner().equals(hole.owner()) && loaded.remainingTicks().intValueExact() == 600,
                "Loading an unfused effect cannot grant extra lifetime");
        loaded.tick();
        helper.assertTrue(loaded.remainingTicks().intValueExact() == 20,
                "An absent owner must stop the effect and enter only the closing animation");
        for (int i = 0; i < 20; i++) loaded.tick();
        helper.assertTrue(loaded.isRemoved(), "Owner loss must remove the effect after twenty closing ticks");
        tag.putString("RemainingTicks", "bad"); loaded.readAdditionalSaveData(tag);
        helper.assertTrue(loaded.remainingTicks().signum() == 0, "Malformed lifetime must fail closed");
        var debris = new NeoForgeSingularityDebrisEntity(NeoForgeModRegistries.SINGULARITY_DEBRIS.get(), helper.getLevel());
        debris.initialize(Blocks.GOLD_BLOCK.defaultBlockState(), UUID.randomUUID(), position);
        var saved = new CompoundTag(); debris.addAdditionalSaveData(saved);
        var restored = new NeoForgeSingularityDebrisEntity(NeoForgeModRegistries.SINGULARITY_DEBRIS.get(), helper.getLevel());
        restored.readAdditionalSaveData(saved); restored.tick();
        helper.assertTrue(restored.block().is(Blocks.GOLD_BLOCK) && restored.isRemoved(),
                "Debris must retain its source block and disappear when its parent is unavailable");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void activeAndClosingEffectsNeverLoadTerrain(GameTestHelper helper) {
        var world = helper.getLevel();
        ServerPlayer player = NeoForgeTestPlayers.login(world.getServer());
        Vec3 local = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 104, 1)));
        Vec3 cold = new Vec3(28_000_000.5, world.getMinBuildHeight() + 80.5, -28_000_000.5);
        var parent = NeoForgeSingularityEntity.create(world, player.getUUID(), BigInteger.ONE, local);
        var active = NeoForgeSingularityEntity.create(world, player.getUUID(), BigInteger.ONE, cold);
        var closing = NeoForgeSingularityEntity.create(world, UUID.randomUUID(), BigInteger.ONE, cold);
        var debris = new ArrayList<NeoForgeSingularityDebrisEntity>();
        try {
            player.setPos(local);
            helper.assertTrue(world.addFreshEntity(parent), "The debris parent must be visible in the loaded test chunk");
            int chunkX = ((int) Math.floor(cold.x)) >> 4, chunkZ = ((int) Math.floor(cold.z)) >> 4;
            for (int x = chunkX - 1; x <= chunkX + 5; x++)
                helper.assertTrue(world.getChunkSource().getChunkNow(x, chunkZ) == null, "The distant test chunks must start unloaded");
            int loaded = world.getChunkSource().getLoadedChunksCount();
            EmcAmount before = NeoForgeTestPlayers.balance(player);
            active.tick();
            helper.assertTrue(active.remainingTicks().intValueExact() == 599 && !active.isRemoved(),
                    "An active effect must retain its original lifetime while skipping unavailable terrain");
            var saved = new CompoundTag(); active.addAdditionalSaveData(saved);
            saved.putString("RemainingTicks", "1"); active.readAdditionalSaveData(saved); active.tick();
            closing.tick();
            for (int i = 0; i < 20; i++) closing.tick();
            helper.assertTrue(active.isRemoved() && closing.isRemoved(), "Expiry and owner loss must finish without reading cold terrain");
            for (int i = 0; i < 64; i++) {
                var visual = new NeoForgeSingularityDebrisEntity(NeoForgeModRegistries.SINGULARITY_DEBRIS.get(), world);
                Vec3 position = cold.add(i, 0, 0);
                visual.initialize(Blocks.STONE.defaultBlockState(), parent.getUUID(), position.add(3, 0, 0));
                visual.setPos(position); visual.tick(); debris.add(visual);
                helper.assertTrue(!visual.isRemoved() && visual.position().distanceTo(position) > 0,
                        "Live debris must retain its original inward motion without fluid or chunk physics");
            }
            parent.discard();
            for (var visual : debris) {
                visual.tick();
                helper.assertTrue(visual.isRemoved(), "Every orphaned visual must disappear on its next tick");
            }
            helper.assertTrue(world.getChunkSource().getLoadedChunksCount() == loaded && NeoForgeTestPlayers.balance(player).equals(before),
                    "Creation, active scans, expiry and debris cleanup must not load chunks or create recovery credit");
            for (int x = chunkX - 1; x <= chunkX + 5; x++)
                helper.assertTrue(world.getChunkSource().getChunkNow(x, chunkZ) == null, "Effect lifecycle must leave distant terrain unloaded");
        } finally {
            parent.discard(); active.discard(); closing.discard();
            for (var visual : debris) visual.discard();
            NeoForgeTestPlayers.logout(player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void tripwireCallbacksWaitForLoadedPaths(GameTestHelper helper) {
        var world = helper.getLevel();
        ServerPlayer player = NeoForgeTestPlayers.login(world.getServer());
        BlockPos target = new BlockPos(24_000_008, world.getMaxBuildHeight() - 8, -24_000_008);
        int chunkX = target.getX() >> 4, chunkZ = target.getZ() >> 4;
        Vec3 center = Vec3.atCenterOf(target.offset(1, 1, 1));
        var hook = Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.EAST)
                .setValue(TripWireHookBlock.ATTACHED, true);
        var holes = new ArrayList<NeoForgeSingularityEntity>();
        try {
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.getChunk(chunkX + x, chunkZ + z);
            helper.assertTrue(world.getChunkSource().getChunkNow((target.getX() + 41) >> 4, chunkZ) == null,
                    "A tripwire callback's distant path must start outside the loaded 3x3 chunks");
            int loaded = world.getChunkSource().getLoadedChunksCount();
            EmcAmount before = NeoForgeTestPlayers.balance(player);
            for (int scenario = 0; scenario < 3; scenario++) {
                BlockState state = scenario == 0 ? hook : scenario == 1 ? Blocks.TRIPWIRE.defaultBlockState() : Blocks.STONE.defaultBlockState();
                rawBlock(world, target.west(), scenario == 1 ? hook : Blocks.AIR.defaultBlockState());
                rawBlock(world, target, state);
                rawBlock(world, target.east(), scenario == 2 ? hook : Blocks.AIR.defaultBlockState());
                var hole = NeoForgeSingularityEntity.create(world, player.getUUID(), BigInteger.ONE, center); holes.add(hole);
                hole.tick();
                helper.assertTrue(world.getChunkSource().getChunkNow(chunkX, chunkZ).getBlockState(target).equals(state),
                        "A hook, wire or hook support must remain intact while its callback path is unloaded: " + scenario);
                helper.assertTrue(world.getChunkSource().getLoadedChunksCount() == loaded && NeoForgeTestPlayers.balance(player).equals(before),
                        "Rejected tripwire terrain recovery must neither load chunks nor credit EMC: " + scenario);
                hole.discard();
            }
            rawBlock(world, target.west(), Blocks.AIR.defaultBlockState());
            rawBlock(world, target.east(), Blocks.AIR.defaultBlockState());
            rawBlock(world, target, hook);
            for (int x = 2; x <= 4; x++) for (int z = -1; z <= 1; z++) world.getChunk(chunkX + x, chunkZ + z);
            loaded = world.getChunkSource().getLoadedChunksCount();
            var hole = NeoForgeSingularityEntity.create(world, player.getUUID(), BigInteger.ONE, center); holes.add(hole);
            hole.tick();
            helper.assertTrue(world.getChunkSource().getChunkNow(chunkX, chunkZ).getBlockState(target).isAir()
                            && NeoForgeTestPlayers.balance(player).equals(before.add(NeoForgeRecoveryPrices.item(new ItemStack(Items.TRIPWIRE_HOOK))))
                            && world.getChunkSource().getLoadedChunksCount() == loaded,
                    "An already loaded callback path must allow normal hook removal and its exact recovery credit without further loads");
        } finally {
            for (var hole : holes) hole.discard();
            for (BlockPos pos : List.of(target.west(), target, target.east())) rawBlock(world, pos, Blocks.AIR.defaultBlockState());
            var visuals = new ArrayList<Entity>();
            for (Entity entity : world.getAllEntities())
                if (entity instanceof NeoForgeSingularityDebrisEntity && entity.distanceToSqr(center) < 100) visuals.add(entity);
            for (var visual : visuals) visual.discard();
            NeoForgeTestPlayers.logout(player);
        }
        helper.succeed();
    }

    private static void rawBlock(net.minecraft.server.level.ServerLevel world, BlockPos pos, BlockState state) {
        // Fixture installation must not execute the very onPlace/onRemove callbacks under test.
        var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null) chunk.getSection(world.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, state);
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void impactChargesOnceAndCancelledSpawnRefunds(GameTestHelper helper) {
        ServerPlayer player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        var position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 48, 1)));
        player.setPos(position);
        var spawned = new ArrayList<NeoForgeSingularityEntity>();
        Consumer<EntityJoinLevelEvent> cancel = event -> {
            if (event.getEntity() instanceof NeoForgeSingularityEntity hole && player.getUUID().equals(hole.owner())) {
                spawned.add(hole); event.setCanceled(true);
            }
        };
        try {
            Arrow arrow = EntityType.ARROW.create(helper.getLevel()); arrow.setOwner(player);
            arrow.getPersistentData().putString(NeoForgeSingularityEntity.ARROW_LEVEL, "2");
            var event = new ProjectileImpactEvent(arrow, new BlockHitResult(position, Direction.UP, BlockPos.containing(position), false));
            var before = NeoForgeTestPlayers.balance(player);
            event.setCanceled(true); NeoForgeSingularityEntity.impact(event);
            helper.assertTrue(arrow.getPersistentData().contains(NeoForgeSingularityEntity.ARROW_LEVEL)
                            && NeoForgeTestPlayers.balance(player).equals(before),
                    "A cancelled impact must preserve the arrow marker and spend nothing");
            event.setCanceled(false);
            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, cancel);
            try { NeoForgeSingularityEntity.impact(event); }
            finally { NeoForge.EVENT_BUS.unregister(cancel); }
            helper.assertTrue(spawned.size() == 1 && NeoForgeTestPlayers.balance(player).equals(before)
                            && !arrow.getPersistentData().contains(NeoForgeSingularityEntity.ARROW_LEVEL),
                    "A rejected join must refund the complete charge while consuming the one-shot marker");
            NeoForgeSingularityEntity.impact(event);
            helper.assertTrue(spawned.size() == 1 && NeoForgeTestPlayers.balance(player).equals(before),
                    "A piercing arrow cannot retry the same impact after its marker was consumed");
            arrow.getPersistentData().putString(NeoForgeSingularityEntity.ARROW_LEVEL, "2");
            NeoForgeSingularityEntity.impact(event);
            var holes = owned(helper, player);
            helper.assertTrue(holes.size() == 1 && holes.getFirst().radius() == 16
                            && NeoForgeTestPlayers.balance(player).equals(before.subtract(EmcAmount.of(BigInteger.valueOf(2048)))),
                    "A successful level-two impact must deliver one effect and debit the original linear 2048 EMC fee");
            arrow.getPersistentData().putString(NeoForgeSingularityEntity.ARROW_LEVEL, "1");
            player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).setEmc(BigInteger.ZERO);
            NeoForgeSingularityEntity.impact(event);
            helper.assertTrue(owned(helper, player).size() == 1 && NeoForgeTestPlayers.balance(player).isZero(),
                    "An unaffordable impact must not create a free effect");
        } finally {
            for (var hole : owned(helper, player)) hole.discard();
            NeoForgeTestPlayers.logout(player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void fusionRecoveryBudgetAndProtectionStayExact(GameTestHelper helper) {
        ServerPlayer player = NeoForgeTestPlayers.login(helper.getLevel().getServer());
        BlockPos origin = helper.absolutePos(new BlockPos(1, 72, 1));
        Vec3 center = Vec3.atCenterOf(origin);
        player.setPos(center);
        player.server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        var world = helper.getLevel();
        List<Entity> created = new ArrayList<>();
        var drops = new ArrayList<ItemEntity>();
        BlockPos stone = origin.offset(-1, -1, -1), protectedStone = origin.offset(1, -1, -1), bedrock = origin.offset(-1, 0, -1), chest = origin.offset(1, 0, -1);
        Consumer<BlockEvent.BreakEvent> deny = event -> { if (event.getPos().equals(protectedStone) && event.getLevel() == world) event.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, deny);
        try {
            world.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            world.setBlockAndUpdate(protectedStone, Blocks.STONE.defaultBlockState());
            world.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
            world.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            var first = NeoForgeSingularityEntity.create(world, player.getUUID(), BigInteger.ONE, center);
            var donor = NeoForgeSingularityEntity.create(world, player.getUUID(), BigInteger.ONE, center.add(1, 0, 0));
            first.setUUID(new UUID(0, 1)); donor.setUUID(new UUID(0, 2));
            world.addFreshEntity(first); world.addFreshEntity(donor); created.add(first); created.add(donor);
            var foreign = NeoForgeSingularityEntity.create(world, UUID.randomUUID(), BigInteger.ONE, center.add(2, 0, 0));
            world.addFreshEntity(foreign); created.add(foreign);
            var pig = EntityType.PIG.create(world); pig.setPos(center); world.addFreshEntity(pig); created.add(pig);
            var wolf = EntityType.WOLF.create(world); wolf.setOwnerUUID(player.getUUID()); wolf.setTame(true, false); wolf.setPos(center);
            world.addFreshEntity(wolf); created.add(wolf);
            for (int i = 0; i < 70; i++) {
                var item = new ItemEntity(world, center.x, center.y, center.z, new ItemStack(Items.DIAMOND));
                item.setPickUpDelay(1000); world.addFreshEntity(item); drops.add(item); created.add(item);
            }
            EmcAmount before = NeoForgeTestPlayers.balance(player), unit = NeoForgeRecoveryPrices.item(new ItemStack(Items.DIAMOND));
            BigInteger pigPrice = NeoForgeEntityRecovery.price(pig);
            first.tick();
            helper.assertTrue(donor.isRemoved() && !foreign.isRemoved() && first.remainingTicks().intValueExact() == 1199
                            && first.absorptionSpeed() == 2 && Math.abs(first.radius() - NeoForgeSingularityEntity.mergedRadius(8, 8)) < .001,
                    "Only overlapping effects of one account may merge, preserving volume, force, mass and combined life");
            long consumed = drops.stream().filter(Entity::isRemoved).count();
            helper.assertTrue(consumed + (pig.isRemoved() ? 1 : 0) == 64 && wolf.isAlive() && !player.isRemoved(),
                    "Each tick may absorb at most 64 victims and must protect its owner and the owner's tamed pets");
            EmcAmount expected = unit.multiply(consumed).add(NeoForgeRecoveryPrices.item(new ItemStack(Items.STONE)));
            if (pig.isRemoved()) expected = expected.add(EmcAmount.of(pigPrice));
            helper.assertTrue(NeoForgeTestPlayers.balance(player).equals(before.add(expected)),
                    "Only actually removed items, base-price entities and blocks may contribute exact recovery credit");
            helper.assertTrue(world.getBlockState(stone).isAir() && world.getBlockState(protectedStone).is(Blocks.STONE)
                            && world.getBlockState(bedrock).is(Blocks.BEDROCK) && world.getBlockState(chest).is(Blocks.CHEST),
                    "Terrain recovery must respect cancellation, indestructible blocks and block-entity inventories");
            long debris = 0;
            for (Entity entity : world.getAllEntities()) if (entity instanceof NeoForgeSingularityDebrisEntity && entity.distanceToSqr(center) < 100) { created.add(entity); debris++; }
            helper.assertTrue(debris == 1, "Each removed terrain block must create one visual-only debris entity");
            var saved = new CompoundTag(); first.addAdditionalSaveData(saved);
            var restored = new NeoForgeSingularityEntity(NeoForgeModRegistries.SINGULARITY.get(), world); restored.readAdditionalSaveData(saved);
            helper.assertTrue(restored.remainingTicks().equals(first.remainingTicks()) && restored.absorptionSpeed() == 2
                            && Math.abs(restored.radius() - first.radius()) < .001,
                    "Fused life, mass and radius must survive save/load without reverting to a single effect");
        } finally {
            NeoForge.EVENT_BUS.unregister(deny);
            for (Entity entity : created) if (!entity.isRemoved()) entity.discard();
            for (BlockPos pos : List.of(stone, protectedStone, bedrock, chest)) world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            try { player.server.getPlayerList().deop(player.getGameProfile()); }
            finally { NeoForgeTestPlayers.logout(player); }
        }
        helper.succeed();
    }

    private static List<NeoForgeSingularityEntity> owned(GameTestHelper helper, ServerPlayer player) {
        var holes = new ArrayList<NeoForgeSingularityEntity>();
        for (Entity entity : helper.getLevel().getAllEntities())
            if (entity instanceof NeoForgeSingularityEntity hole && !hole.isRemoved() && player.getUUID().equals(hole.owner())) holes.add(hole);
        return holes;
    }
}
