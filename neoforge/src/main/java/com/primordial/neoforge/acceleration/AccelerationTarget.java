package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.AccelerationConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

record AccelerationTarget(ServerLevel level, BlockPos pos, BlockState initial,
                          BlockEntity entity, BlockEntityTicker<BlockEntity> ticker) {
    private static final TagKey<Block> WATCH_BLACKLIST = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath("projecte", "blacklist/time_watch"));
    private static final TagKey<BlockEntityType<?>> WATCH_ENTITY_BLACKLIST = TagKey.create(
            Registries.BLOCK_ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("projecte", "blacklist/time_watch"));

    @SuppressWarnings("unchecked")
    static AccelerationTarget read(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.shouldTickBlocksAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.is(WATCH_BLACKLIST) || AccelerationConfig.accelerationBlocked(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return null;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null) {
            if (entity.isRemoved() || !(state.getBlock() instanceof EntityBlock block)) return null;
            if (BuiltInRegistries.BLOCK_ENTITY_TYPE.wrapAsHolder(entity.getType()).is(WATCH_ENTITY_BLACKLIST)) return null;
            BlockEntityTicker<BlockEntity> ticker = AccelerationMachineCompat.ticker(entity);
            if (ticker == null) ticker = (BlockEntityTicker<BlockEntity>) block.getTicker(level, state, entity.getType());
            return ticker == null ? null : new AccelerationTarget(level, pos, state, entity, ticker);
        }
        return state.isRandomlyTicking() || state.getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock
                ? new AccelerationTarget(level, pos, state, null, null) : null;
    }

    String blockId() { return BuiltInRegistries.BLOCK.getKey(initial.getBlock()).toString(); }

    boolean valid() {
        if (ticker instanceof AccelerationMachineCompat.CheckedTicker checked && !checked.valid()) return false;
        if (!level.hasChunkAt(pos) || !level.shouldTickBlocksAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() != initial.getBlock()) return false;
        return entity != null ? !entity.isRemoved() && level.getBlockEntity(pos) == entity
                : (state.isRandomlyTicking() || state.getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock)
                        && level.getBlockEntity(pos) == null;
    }

    void tick() {
        BlockState state = level.getBlockState(pos);
        if (ticker != null) ticker.tick(level, pos, state, entity);
        else if (state.getBlock() instanceof net.minecraft.world.level.block.LightningRodBlock) tickLightningRod();
        else state.randomTick(level, pos, level.random);
    }

    private void tickLightningRod() {
        if (!level.isThundering() || !level.canSeeSky(pos)
                || pos.getY() != level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1
                || level.getRandom().nextInt(20000) != 0) return;
        var bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos.above()));
            level.addFreshEntity(bolt);
        }
    }
}
