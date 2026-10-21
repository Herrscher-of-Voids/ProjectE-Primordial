/**
 * 【文件职责】在转化桌卖入本地容器时返还内容并按空容器结算。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：识别原版本地容器，返还内容；未知存储保留原物品，避免共享库存复制。
 * - 修改：沿用 ProjectE 知识、价格和同步，设置仅影响之后的学习。
 * - 用途：保留物品变体，阻止容器内容进入可重复生成的知识；不改变历史知识。
 */
package com.primordial.mixin;

import com.primordial.service.TransmutationContainerReturn;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SlotConsume.class, remap = false)
public abstract class TransmutationConsumeSaleMixin {
    @Shadow @Final private TransmutationInventory inv;
    @Inject(method = {"set", "m_5852_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$returnContents(ItemStack stack, CallbackInfo ci) {
        if (!TransmutationContainerReturn.handles(stack)) return;
        if (inv.isServer() && !TransmutationContainerReturn.sell(inv, stack, () -> { })) {
            inv.player.getInventory().placeItemBackInInventory(stack.copy());
            inv.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.primordial.container_return_unsupported"), true);
        }
        ci.cancel();
    }
}
