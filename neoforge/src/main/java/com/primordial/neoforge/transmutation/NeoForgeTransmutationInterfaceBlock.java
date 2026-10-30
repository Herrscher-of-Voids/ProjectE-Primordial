/**
 * 【文件职责】NeoForge 转化接口方块身份、放置归属及服务端网络端口生命周期。
 * 【变更记录 #1】2026-10-22 22:47:07（第 1 次备注 · 2.0.0-neo-dev 转化接口基础迁移）
 * - 删除：无。
 * - 新增：1.21.1 MapCodec、模型渲染、首次放置绑定、类型安全 ticker 和替换前断网回调。
 * - 修改：沿用 Forge 的放置者账户与网络端口生命周期；不暴露普通库存能力或菜单。
 * - 用途：支持独立新档中的接口放置、保存和拆除，明确提示 AE2/RS 网络交易仍待迁移。
 */
package com.primordial.neoforge.transmutation;


import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.primordial.neoforge.NeoForgeModRegistries;

import net.minecraft.core.BlockPos;
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

public final class NeoForgeTransmutationInterfaceBlock extends BaseEntityBlock {
    public static final MapCodec<NeoForgeTransmutationInterfaceBlock> CODEC =
            simpleCodec(NeoForgeTransmutationInterfaceBlock::new);

    public NeoForgeTransmutationInterfaceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<NeoForgeTransmutationInterfaceBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NeoForgeTransmutationInterfaceBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                NeoForgeModRegistries.TRANSMUTATION_INTERFACE_ENTITY.get(),
                (world, pos, blockState, blockEntity) -> blockEntity.tickNetworks());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof NeoForgeTransmutationInterfaceBlockEntity blockEntity) {
            blockEntity.removeFromNetwork();
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof NeoForgeTransmutationInterfaceBlockEntity blockEntity) {
            blockEntity.bindOwner(player.getUUID());
        }
    }

}
