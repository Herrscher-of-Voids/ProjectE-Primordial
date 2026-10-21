/**
 * 【文件职责】在转化桌卖入本地容器时返还内容并按空容器结算。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：识别原版本地容器，返还内容；未知存储保留原物品，避免共享库存复制。
 * - 修改：沿用 ProjectE 知识、价格和同步，设置仅影响之后的学习。
 * - 用途：保留物品变体，阻止容器内容进入可重复生成的知识；不改变历史知识。
 */
/**
 * 【变更记录 #2】2026-10-03 03:34:00（第 2 次备注 · 1.2.0-transmutation-crafting）
 * - 删除：将新增合成槽误判为玩家背包的索引范围。
 * - 新增：玩家背包固定 27-62 的边界校验。
 * - 修改：合成网格由 TransmutationCraftingMixin 独立处理 Shift 点击。
 * - 用途：合成材料或容器不会进入 ProjectE 的背包卖入分支。
 */
package com.primordial.mixin;

import com.primordial.service.TransmutationContainerReturn;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransmutationContainer.class, remap = false)
public abstract class TransmutationContainerSaleMixin {
    @Shadow @Final public TransmutationInventory transmutationInventory;
    @Inject(method = {"quickMoveStack", "m_7648_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$returnContents(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        var menu = (TransmutationContainer)(Object)this;
        if (index <= 26 || index >= 63 || index >= menu.slots.size()) return;
        var slot = menu.getSlot(index);
        ItemStack stack = slot.getItem();
        if (!TransmutationContainerReturn.handles(stack)) return;
        if (transmutationInventory.isServer() && !TransmutationContainerReturn.sell(transmutationInventory, stack,
                () -> slot.set(ItemStack.EMPTY)))
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.primordial.container_return_unsupported"), true);
        cir.setReturnValue(ItemStack.EMPTY);
    }
}
