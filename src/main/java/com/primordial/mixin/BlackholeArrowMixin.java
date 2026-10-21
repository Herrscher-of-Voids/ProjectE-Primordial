/**
 * 【变更记录 #1】2026-10-14 01:59:18（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：付费穿透次数保存为大整数，绕过原版 byte 穿透上限，保持实体去重和正常命中判定。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.mixin;

import com.primordial.equipment.BlackholeBowItem;
import com.primordial.equipment.BlackholeModules;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.math.BigInteger;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=AbstractArrow.class,remap=false)
public abstract class BlackholeArrowMixin {
    // 仅替换本模组付费箭的 byte 数量上限；仍保留原版已命中实体集合和伤害/保护判定。
    @Redirect(method={"onHitEntity","m_5790_"},at=@At(value="INVOKE",target="Lit/unimi/dsi/fastutil/ints/IntOpenHashSet;size()I"),remap=false)
    private int primordial$pierceSize(IntOpenHashSet hits){
        AbstractArrow arrow=(AbstractArrow)(Object)this;
        BigInteger left=BlackholeModules.parse(arrow.getPersistentData().getString(BlackholeBowItem.PIERCE_REMAINING));
        return left!=null&&left.signum()>0?0:hits.size();
    }
    @Inject(method={"onHitEntity","m_5790_"},at=@At("RETURN"),remap=false)
    private void primordial$afterHit(EntityHitResult hit,CallbackInfo ci){
        AbstractArrow arrow=(AbstractArrow)(Object)this;
        if(arrow.level().isClientSide)return;
        BigInteger left=BlackholeModules.parse(arrow.getPersistentData().getString(BlackholeBowItem.PIERCE_REMAINING));
        if(left==null)return;
        left=left.subtract(BigInteger.ONE);
        if(left.signum()<=0)arrow.discard();
        else arrow.getPersistentData().putString(BlackholeBowItem.PIERCE_REMAINING,left.toString());
    }
}
