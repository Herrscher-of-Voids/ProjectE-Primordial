package com.primordial.neoforge.mixin;

import com.primordial.neoforge.equipment.NeoForgeBlackholeHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayerGameMode.class)
public abstract class NeoForgeBlackholeHarvestMixin {
    // 1.21 的掉落事件统一结算经验；精准采集组件也传给同一条原版流水线。
    @Redirect(method="destroyBlock", at=@At(value="INVOKE", target="Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void primordial$harvest(Block block,Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool) {
        NeoForgeBlackholeHarvest.destroy(block,level,player,pos,state,entity,tool);
    }
}
