package com.primordial.neoforge.mixin;

import com.primordial.neoforge.equipment.NeoForgeBlackholeBowItem;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.math.BigInteger;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=AbstractArrow.class,remap=false)
public abstract class NeoForgeBlackholeArrowMixin {
    // 仅替换本模组付费箭的 byte 数量上限；仍保留原版已命中实体集合和伤害/保护判定。
    @Redirect(method="onHitEntity",at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/ints/IntOpenHashSet;size()I"),remap=false)
    private int primordial$pierceSize(IntOpenHashSet hits){
        AbstractArrow arrow=(AbstractArrow)(Object)this;
        BigInteger left=NeoForgeBlackholeModules.parse(arrow.getPersistentData().getString(NeoForgeBlackholeBowItem.PIERCE_REMAINING));
        return left!=null&&left.signum()>0?0:hits.size();
    }
    @Inject(method="onHitEntity",at=@At("RETURN"),remap=false)
    private void primordial$afterHit(EntityHitResult hit,CallbackInfo ci){
        AbstractArrow arrow=(AbstractArrow)(Object)this;
        if(arrow.level().isClientSide)return;
        BigInteger left=NeoForgeBlackholeModules.parse(arrow.getPersistentData().getString(NeoForgeBlackholeBowItem.PIERCE_REMAINING));
        if(left==null)return;
        left=left.subtract(BigInteger.ONE);
        if(left.signum()<=0)arrow.discard();
        else arrow.getPersistentData().putString(NeoForgeBlackholeBowItem.PIERCE_REMAINING,left.toString());
    }
}
