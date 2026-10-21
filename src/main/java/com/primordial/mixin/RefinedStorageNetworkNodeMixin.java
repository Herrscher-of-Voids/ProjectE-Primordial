/**
 * 【文件职责】保护 Refined Storage 网络节点的客户端同步，隔离已拆除或无物品映射节点的空 ItemLike。
 * 【变更记录 #1】2026-10-14 16:42:10（第 1 次备注 · 1.2.0-rs-node-sync-crash-fix）
 * - 删除：无；保留 Refined Storage 原始节点同步和原初转化接口节点行为。
 * - 新增：getItemStack 入口检查方块物品映射；缺失映射时返回空栈，避免 RS 对 null 构造 ItemStack。
 * - 修改：只拦截无效节点，不改变有对应物品的节点名称、图标和网络拓扑；Mixin 仅在 RS 已加载时应用。
 * - 用途：修复接口拆除、网络重建或第三方路径方块遗留节点导致的服务器 Ticking entity 崩溃。
 */
package com.primordial.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = com.refinedmods.refinedstorage.apiimpl.network.node.NetworkNode.class, remap = false)
public abstract class RefinedStorageNetworkNodeMixin {
    @Shadow protected Level level;
    @Shadow protected BlockPos pos;

    @Inject(method = "getItemStack", at = @At("HEAD"), cancellable = true)
    private void primordial$emptyWhenBlockHasNoItem(CallbackInfoReturnable<ItemStack> callback) {
        if (level == null || pos == null || !Item.BY_BLOCK.containsKey(level.getBlockState(pos).getBlock()))
            callback.setReturnValue(ItemStack.EMPTY);
    }
}
