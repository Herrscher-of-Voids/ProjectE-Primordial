/**
 * 【文件职责】可选超越维度存储的只读目录及请求提取桥接。
 * 【变更记录 #1】2026-10-14 14:37:00（第 1 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：父存储 getStorage/getStackByKey/extract 返回处合并挂载目录与真实购买量。
 * - 修改：没有挂载接口的存储不受影响；不触碰序列化、实际数量表或对方前置权限事件。
 * - 用途：终端目录显示可购买资源，但保存只包含真实库存，避免虚拟数量变成免费物品。
 */
/**
 * 【变更记录 #2】2026-10-14 14:46:00（第 2 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：无。
 * - 新增：getBucket 与 getOutStackByKey 的只读合并入口。
 * - 修改：对方能力包装器的枚举索引也涵盖虚拟商品，不写入真实类型桶。
 * - 用途：保证终端和物品/流体导出看到同一目录。
 */
package com.primordial.mixin;

import com.primordial.compat.storage.BeyondDimensionsStorageAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Pseudo
@Mixin(targets="com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler",remap=false)
public abstract class BeyondDimensionsStorageMixin {
    @Inject(method="getBucket",at=@At("RETURN"),cancellable=true,remap=false)
    private void primordial$bucket(net.minecraft.resources.ResourceLocation type,CallbackInfoReturnable<java.util.Optional<?>> cir){
        cir.setReturnValue(BeyondDimensionsStorageAdapter.bucket(this,type,cir.getReturnValue()));
    }
    @Inject(method="getOutStackByKey",at=@At("RETURN"),cancellable=true,remap=false)
    private void primordial$output(@Coerce Object key,CallbackInfoReturnable<Object> cir){
        cir.setReturnValue(BeyondDimensionsStorageAdapter.outputStack(this,key,cir.getReturnValue()));
    }
    @Inject(method="getStorage",at=@At("RETURN"),cancellable=true,remap=false)
    private void primordial$catalog(CallbackInfoReturnable<List<?>> cir){cir.setReturnValue(BeyondDimensionsStorageAdapter.overlay(this,cir.getReturnValue()));}
    @Inject(method="getStackByKey",at=@At("RETURN"),cancellable=true,remap=false)
    private void primordial$stack(@Coerce Object key,CallbackInfoReturnable<Object> cir){
        if(key!=null)cir.setReturnValue(BeyondDimensionsStorageAdapter.stack(this,key,cir.getReturnValue()));
    }
    @Inject(method="extract(Lcom/wintercogs/beyonddimensions/api/storage/key/IStackKey;JZZ)Lcom/wintercogs/beyonddimensions/api/storage/key/KeyAmount;",at=@At("RETURN"),cancellable=true,remap=false)
    private void primordial$extract(@Coerce Object key,long amount,boolean simulate,boolean fuzzy,CallbackInfoReturnable<Object> cir){
        if(key!=null)cir.setReturnValue(BeyondDimensionsStorageAdapter.extract(this,key,amount,simulate,cir.getReturnValue()));
    }
}
