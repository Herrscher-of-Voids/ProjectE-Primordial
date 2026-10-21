/**
 * 【文件职责】黑洞方块的实体创建、所有者绑定和服务端 tick 生命周期。
 * 【变更记录 #1】2026-09-15 14:58:00（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：方块被替换前结清回收队列，并返回仍无价的缓冲物品。
 * - 修改：同一方块状态变化不结清，只有真正替换/拆除才触发。
 * - 用途：防止玩家拆除黑洞时丢失不足 1 EMC 的回收价值，整数和找零交给统一服务持久化。
 */
package com.primordial.block;

import javax.annotation.Nullable;

import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class BlackholeBlock extends BaseEntityBlock {

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof BlackholeBlockEntity be)
            be.settleBeforeRemoval();
        super.onRemove(state, level, pos, next, moving);
    }

    public BlackholeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlackholeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level instanceof ServerLevel) {
            return createTickerHelper(type, ModBlockEntities.BLACKHOLE.get(), BlackholeBlockEntity::serverTick);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            if (level.getBlockEntity(pos) instanceof BlackholeBlockEntity be && be.getOwner() == null) {
                be.setOwner(player.getUUID());
            }
        }
    }
}
