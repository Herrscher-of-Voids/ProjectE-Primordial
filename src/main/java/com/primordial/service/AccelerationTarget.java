/**
 * 【文件职责】为本 tick 捕获已加载目标的原生 ticker 或随机 tick 回调，不跨 tick 缓存方块实体。
 * 【变更记录 #1】2026-09-16 13:32:00（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无；从加速服务拆出世界访问校验。
 * - 新增：黑名单、ProjectE 时间黑名单标签、区块正常运行范围及实体替换检查。
 * - 修改：每次额外调用读取当前 BlockState，避免炉子亮灭或作物生长后继续使用旧状态。
 * - 用途：支持原生机器 ticker 和随机生长方块；普通装饰方块拒绝绑定，不强制加载区块。
 */
/**
 * 【变更记录 #2】2026-09-18 01:33:29（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：只有普通 ticker 的方块实体才可绑定的限制。
 * - 新增：AE2 延迟适配器、雷暴期间顶端避雷针的额外引雷机会。
 * - 修改：机器优先使用专用节点回调；避雷针仍按实际额外逻辑步计费，天气或无遮挡条件不满足时不生成雷电。
 * - 用途：覆盖法杖的特殊方块加速，仍遵守区块、黑名单、替换检查及统一预算。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
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
            new ResourceLocation("projecte", "blacklist/time_watch"));
    private static final TagKey<BlockEntityType<?>> WATCH_ENTITY_BLACKLIST = TagKey.create(
            Registries.BLOCK_ENTITY_TYPE, new ResourceLocation("projecte", "blacklist/time_watch"));

    @SuppressWarnings("unchecked")
    static AccelerationTarget read(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.shouldTickBlocksAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.is(WATCH_BLACKLIST) || PrimordialServerConfig.accelerationBlocked(
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
