/**
 * 【文件职责】NeoForge 1.21.1 黑洞注册侧生命周期与服务端队列结算；资源入口和菜单未启用。
 *
 * 【变更记录 #1】2026-10-22 11:11:02（第 1 次备注 · 2.0.0-neo-dev 黑洞系统迁移）
 * - 删除：无。
 * - 新增：黑洞方块实体创建、服务端 ticker、放置时 owner UUID 绑定和替换生命周期边界。
 * - 修改：以 NeoForge BlockEntityType 进行类型检查，不复用 Forge 注册对象或能力接口。
 * - 用途：先验证方块实体能在目标平台稳定放置、重载和移除，再接入能力与 EMC 结算。
 *
 * 【变更记录 #2】2026-10-22 11:18:30（第 2 次备注 · 2.0.0-neo-dev 黑洞系统迁移）
 * - 删除：无实际结算行为的生产 ticker 和强制类型转换。
 * - 新增：1.21.1 BaseEntityBlock 所需 MapCodec，以及明确标注回收尚未启用的物品提示。
 * - 修改：沿用父类的空 ticker；未接入 EMC 时既不每刻空转，也不接收或销毁资源。
 * - 用途：把首切限定为注册、放置归属、存档和移除，避免将测试观测逻辑带入生产生命周期。
 *
 * 【变更记录 #3】2026-10-22 21:13:00（第 3 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：服务端类型检查 ticker 与真实替换前的队列结算。
 * - 修改：同方块状态更新不触发拆除；客户端不提交金额。
 * - 用途：待结算收益在 tick 或拆除时转入世界账本；资源回收和能力仍未启用。
 */
package com.primordial.neoforge.blackhole;


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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

public final class NeoForgeBlackholeBlock extends BaseEntityBlock {
    public static final MapCodec<NeoForgeBlackholeBlock> CODEC = simpleCodec(NeoForgeBlackholeBlock::new);

    public NeoForgeBlackholeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<NeoForgeBlackholeBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NeoForgeBlackholeBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                NeoForgeModRegistries.BLACKHOLE_ENTITY.get(), NeoForgeBlackholeBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof NeoForgeBlackholeBlockEntity blackhole) {
            blackhole.settleOnRemoval();
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof NeoForgeBlackholeBlockEntity blackhole
                && blackhole.owner() == null) {
            blackhole.setOwner(player.getUUID());
        }
    }

}
