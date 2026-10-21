/**
 * 【文件职责】在介质页从容器渲染、命中和交易入口排除隐藏的 ProjectE 槽位。
 * 【变更记录 #1】2026-10-02 18:20:45（第 1 次备注 · 1.2.0-media-page-isolation）
 * - 删除：无。
 * - 新增：槽位绘制、悬停与点击入口的客户端页签过滤。
 * - 修改：仅作用于当前转化桌介质页；背包可正常拿放，但禁止向隐藏槽位快捷转移。
 * - 用途：释放、拖拽、数字键和提示共同遵守页签状态；双方法名对应开发环境及 Forge 1.20.1 SRG 环境。
 */
/**
 * 【变更记录 #2】2026-10-03 03:34:00（第 2 次备注 · 1.2.0-transmutation-crafting）
 * - 删除：只在介质页检查隐藏槽的限制。
 * - 新增：物品、介质、合成三页共同过滤非本页槽位。
 * - 修改：介质页继续禁用快捷转移；合成页允许材料和结果的正常点击。
 * - 用途：不允许在不可见页面点击或拖入合成槽。
 */
/**
 * 【变更记录 #3】2026-10-12 23:38:49（第 3 次备注 · 1.2.0-media-real-slots）
 * - 删除：旧介质页不完整的交互路径。
 * - 新增：允许充电及遗忘槽 Shift 取回背包，继续阻断隐藏物品卖入。
 * - 修改：对照 ProjectE 真实槽位及原生控件处理。
 * - 用途：介质页状态、容器安全与显示一致，保留全部历史。
 */
package com.primordial.mixin;

import com.primordial.client.TransmutationMediaButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractContainerScreen.class, remap = false)
public abstract class TransmutationSlotMixin {
    @Inject(method = {"renderSlot", "m_280092_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$renderSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (TransmutationMediaButton.hiddenSlot(this, slot)) ci.cancel();
    }

    @Inject(method = {"isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", "m_97774_"},
            at = @At("HEAD"), cancellable = true)
    private void primordial$hoverSlot(Slot slot, double x, double y, CallbackInfoReturnable<Boolean> ci) {
        if (TransmutationMediaButton.hiddenSlot(this, slot)) ci.setReturnValue(false);
    }

    @Inject(method = {"slotClicked", "m_6597_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$clickSlot(Slot slot, int index, int button, ClickType type, CallbackInfo ci) {
        if (TransmutationMediaButton.hiddenSlot(this, slot)
                || (TransmutationMediaButton.mediaPageActive(this)
                && (type == ClickType.PICKUP_ALL || type == ClickType.QUICK_MOVE && index < 73))
                || (TransmutationMediaButton.craftingPageActive(this)
                && type == ClickType.QUICK_MOVE && slot != null
                && index >= 27 && index < 63)) ci.cancel();
    }
}
