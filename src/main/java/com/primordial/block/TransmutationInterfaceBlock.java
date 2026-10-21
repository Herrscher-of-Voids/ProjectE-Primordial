/**
 * 【文件职责】提供连接 AE2 / Refined Storage 外部存储的转化接口方块。
 * 【变更记录 #1】2026-10-11 23:44:54（第 1 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；新增独立方块，不修改配置器或黑洞方块行为。
 * - 新增：放置时将玩家 UUID 绑定到方块实体；方块被破坏后不留下可转移的账户状态。
 * - 修改：接口仅提供模型和账户归属，不在方块类中调用网络 API 或直接改 EMC。
 * - 用途：AE2/RS 存储访问都通过 capability/外部存储适配器，交易权限归属于放置者个人账户。
 */
/**
 * 【变更记录 #2】2026-10-14 14:14:39（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：服务端网络端口 ticker，目录按适配器周期同步；拆除仍使用方块实体移除回调。
 * - 修改：同一接口直接参与可选存储网络，账户仍为放置者个人账户。
 * - 用途：让节点缓存跟随知识和 EMC 变化刷新，不创建缓冲箱子。
 */
/**
 * 【变更记录 #3】2026-10-14 15:15:00（第 3 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：依赖实体卸载回调查询世界状态来识别拆除。
 * - 新增：方块被替换时显式通知接口删除网络节点。
 * - 修改：正常区块卸载不查询或重新加载自身区块。
 * - 用途：保存退出安全断开，实际拆除仍清理 RS 注册节点和虚拟挂载。
 */
package com.primordial.block;

import com.primordial.blockentity.TransmutationInterfaceBlockEntity;
import com.primordial.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

public final class TransmutationInterfaceBlock extends BaseEntityBlock {
    public TransmutationInterfaceBlock(Properties properties) { super(properties); }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransmutationInterfaceBlockEntity(pos, state);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock()&&level.getBlockEntity(pos) instanceof TransmutationInterfaceBlockEntity be)be.removeFromNetwork();
        super.onRemove(state,level,pos,next,moving);
    }
    @Nullable @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level,BlockState state,net.minecraft.world.level.block.entity.BlockEntityType<T> type){
        return level.isClientSide?null:createTickerHelper(type,ModBlockEntities.TRANSMUTATION_INTERFACE.get(),(world,pos,block,be)->be.tickNetworks());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof TransmutationInterfaceBlockEntity be
                && be.getOwner() == null) {
            be.setOwner(player.getUUID());
        }
    }
}
