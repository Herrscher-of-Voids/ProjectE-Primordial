/**
 * 【文件职责】为 Create 动力方块提供临时 EMC 动力源状态及原生 tick/保存钩子。
 * 【变更记录 #1】2026-09-16 19:59:10（第 1 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：基础动力方块发电速度、tick 前付款兑现及仅存档时清除临时动力的注入。
 * - 修改：使用字符串伪目标且不重映射第三方方法；没有 Create 时 Mixin 自动跳过。
 * - 用途：识别继承动力方块的附属机器，客户端仍通过 Create 原生同步接收动画状态。
 */
/**
 * 【变更记录 #2】2026-09-16 20:30:43（第 2 次备注 · 1.0.0-create-kinetics）
 * - 删除：对所有动力方块 tick 的注入。
 * - 新增：按需创建付款状态的只读访问入口。
 * - 修改：续供和到期改由 CreateKineticTicker 仅遍历已付款机器；未配置机器不分配状态对象。
 * - 用途：减少大规模动力网络开销，并保证停止 ticking 的方块也能撤销过期动力。
 */
package com.primordial.neoforge.mixin;

import com.primordial.neoforge.resource.create.CreateKinetics;
import com.primordial.neoforge.resource.create.KineticAccess;
import com.primordial.neoforge.resource.create.KineticLease;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.base.KineticBlockEntity", remap = false)
public abstract class NeoForgeCreateKineticBlockEntityMixin implements KineticAccess {
    @Unique private KineticLease primordial$kineticLease;

    @Override public KineticLease primordial$lease() {
        if (primordial$kineticLease == null) primordial$kineticLease = new KineticLease();
        return primordial$kineticLease;
    }
    @Override public KineticLease primordial$leaseIfPresent() { return primordial$kineticLease; }

    @Inject(method = "getGeneratedSpeed", at = @At("HEAD"), cancellable = true)
    private void primordial$generatedSpeed(CallbackInfoReturnable<Float> callback) {
        if (primordial$kineticLease != null && primordial$kineticLease.activeSpeed != 0)
            callback.setReturnValue(primordial$kineticLease.activeSpeed);
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void primordial$saveNativeCapacity(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket, CallbackInfo callback) {
        if (!clientPacket) CreateKinetics.sanitizeSavedNetwork((KineticBlockEntity) (Object) this, primordial$kineticLease, tag);
    }
}
