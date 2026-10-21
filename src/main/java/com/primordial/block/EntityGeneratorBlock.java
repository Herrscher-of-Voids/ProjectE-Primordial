/**
 * 【文件职责】实体生成器右键打开服务端报价菜单，兑换在方块正上方进行。
 * 【变更记录 #1】2026-10-14 15:59:10（第 1 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：无。新增：原版菜单打开流程和有限报价目录同步。
 * - 修改：使用当前操作者个人 EMC，不创建可转移账户或实体库存。
 * - 用途：实体生成界面入口；破坏方块或离开后菜单失效。
 */
package com.primordial.block;
import com.primordial.menu.EntityGeneratorMenu;
import com.primordial.service.EntityEmc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
public final class EntityGeneratorBlock extends Block {
    public EntityGeneratorBlock(Properties properties){super(properties);}
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer server) {
            var offers=EntityEmc.catalog(server.serverLevel());
            NetworkHooks.openScreen(server,new SimpleMenuProvider((id,inventory,p)->new EntityGeneratorMenu(id,inventory,pos,offers),
                    net.minecraft.network.chat.Component.translatable("block.primordial.entity_generator")),buf->{
                buf.writeBlockPos(pos);buf.writeVarInt(offers.size());
                for(var offer:offers){buf.writeResourceLocation(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(offer.type()));buf.writeUtf(offer.price().toString(),4096);}
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
