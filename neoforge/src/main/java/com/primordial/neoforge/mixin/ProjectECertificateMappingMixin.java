/**
 * 【文件职责】仅在必需 ProjectE 1.1.0 的映射完成入口修补凭证价，先于原生燃料缓存和在线知识清理。
 * 【变更记录 #1】2026-10-23 13:34:21（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：精确注入 private static fireEmcRemapEvent()V 的 HEAD，require=1 且不重映射第三方名称。
 * - 修改：委托唯一兼容服务复制并发布完整价表，不取消原生事件、知识清理或同步。
 * - 用途：让已学习且没有默认配方的流体/化学品凭证在 /reload 时仍有有效 EMC，不凭空授予知识。
 */
package com.primordial.neoforge.mixin;

import com.primordial.neoforge.compat.NeoForgeCertificatePrices;

import moze_intel.projecte.emc.EMCMappingHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EMCMappingHandler.class, remap = false)
public abstract class ProjectECertificateMappingMixin {
    @Inject(method = "fireEmcRemapEvent()V", at = @At("HEAD"), remap = false, require = 1)
    private static void primordial$refreshCertificatePrices(CallbackInfo callback) {
        NeoForgeCertificatePrices.refresh();
    }
}
