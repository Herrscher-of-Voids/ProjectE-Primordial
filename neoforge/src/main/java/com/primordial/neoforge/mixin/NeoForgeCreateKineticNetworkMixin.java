/**
 * 【文件职责】把网络中已付款机器的当 tick 应力加入 Create 原生总容量。
 * 【变更记录 #1】2026-09-16 19:59:10（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：网络容量计算尾部的应力累加及持久化剔除值。
 * - 修改：只遍历当前网络已加载成员；原生发电容量、负载和超载判定继续由 Create 计算。
 * - 用途：支持给普通机器/发电机/传动件补应力，避免伪造 FE 或绕过动力网络。
 */
/**
 * 【变更记录 #2】2026-09-16 20:30:43（第 2 次备注 · 1.0.0-create-kinetics）
 * - 删除：读取未配置机器时自动分配付款状态的行为。
 * - 新增：仅统计已有付款状态的空值检查。
 * - 修改：使用 leaseIfPresent，不改变 Create 原生应力计算。
 * - 用途：网络容量刷新不额外给所有成员创建状态对象。
 */
package com.primordial.neoforge.mixin;

import com.primordial.neoforge.resource.create.KineticAccess;
import com.primordial.neoforge.resource.create.KineticNetworkAccess;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.KineticNetwork", remap = false)
public abstract class NeoForgeCreateKineticNetworkMixin implements KineticNetworkAccess {
    @Shadow public Map<KineticBlockEntity, Float> members;
    @Unique private float primordial$paidStress;

    @Override public float primordial$paidCapacity() { return primordial$paidStress; }

    @Inject(method = "calculateCapacity", at = @At("RETURN"), cancellable = true)
    private void primordial$addPaidStress(CallbackInfoReturnable<Float> callback) {
        double extra = 0;
        for (KineticBlockEntity machine : members.keySet()) {
            if (machine instanceof KineticAccess access && access.primordial$leaseIfPresent() != null
                    && !machine.isRemoved() && machine.getLevel() != null
                    && machine.getLevel().hasChunkAt(machine.getBlockPos())
                    && machine.getLevel().getBlockEntity(machine.getBlockPos()) == machine) {
                extra += access.primordial$leaseIfPresent().activeStress;
            }
        }
        primordial$paidStress = (float) Math.min(Float.MAX_VALUE / 4, extra);
        callback.setReturnValue(callback.getReturnValueF() + primordial$paidStress);
    }
}
