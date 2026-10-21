/**
 * 【文件职责】接入转化桌 NBT 安全保留、学习及输出检查。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：普通安全 NBT、耐久与附魔保留入口，服务端权威检查并隔离容器状态。
 * - 修改：沿用 ProjectE 知识、价格和同步，设置仅影响之后的学习。
 * - 用途：保留物品变体，阻止容器内容进入可重复生成的知识；不改变历史知识。
 */
package com.primordial.mixin;

import com.primordial.service.TransmutationNbtPolicy;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.emc.nbt.NBTManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NBTManager.class, remap = false)
public abstract class TransmutationNbtPersistenceMixin {
    @Inject(method = "getPersistentInfo", at = @At("HEAD"), cancellable = true)
    private static void primordial$persistent(ItemInfo info, CallbackInfoReturnable<ItemInfo> cir) {
        cir.setReturnValue(TransmutationNbtPolicy.persistent(info));
    }
}
