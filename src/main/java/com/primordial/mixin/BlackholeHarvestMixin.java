/**
 * 【变更记录 #1】2026-10-14 01:45:54（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：对照本实例 SRG 映射接入成功收获调用；开发/发布签名组必须命中一次，精准采集不掉经验。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.mixin;
import com.primordial.equipment.BlackholeHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=ServerPlayerGameMode.class,remap=false)
public abstract class BlackholeHarvestMixin {
    @Unique private boolean primordial$silk;
    @Inject(method={"destroyBlock","m_9280_"},at=@At("HEAD"))
    private void primordial$reset(BlockPos pos,CallbackInfoReturnable<Boolean> ci){primordial$silk=false;}
    @Group(name="primordialHarvest",min=1,max=1)
    @Redirect(method={"destroyBlock","m_9280_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"),require=0)
    private void primordial$harvestDev(Block block,Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool){primordial$silk=BlackholeHarvest.destroy(block,level,player,pos,state,entity,tool);}
    @Group(name="primordialHarvest",min=1,max=1)
    @Redirect(method={"destroyBlock","m_9280_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;m_6240_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V"),require=0)
    private void primordial$harvestProd(Block block,Level level,Player player,BlockPos pos,BlockState state,BlockEntity entity,ItemStack tool){primordial$silk=BlackholeHarvest.destroy(block,level,player,pos,state,entity,tool);}
    @Group(name="primordialExperience",min=1,max=1)
    @Redirect(method={"destroyBlock","m_9280_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;popExperience(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V"),require=0)
    private void primordial$xpDev(Block block,ServerLevel level,BlockPos pos,int amount){if(!primordial$silk)block.popExperience(level,pos,amount);}
    @Group(name="primordialExperience",min=1,max=1)
    @Redirect(method={"destroyBlock","m_9280_"},at=@At(value="INVOKE",target="Lnet/minecraft/world/level/block/Block;m_49805_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;I)V"),require=0)
    private void primordial$xpProd(Block block,ServerLevel level,BlockPos pos,int amount){if(!primordial$silk)block.popExperience(level,pos,amount);}
}
