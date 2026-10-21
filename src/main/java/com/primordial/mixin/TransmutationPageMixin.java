/**
 * 【文件职责】按页签选择 ProjectE 转化桌背景、文字和提示渲染路径。
 * 【变更记录 #1】2026-10-02 18:20:45（第 1 次备注 · 1.2.0-media-page-isolation）
 * - 删除：无。
 * - 新增：介质页替代背景及背包专属悬停提示。
 * - 修改：介质页不再执行原版物品页面背景、EMC 文字和特殊区域提示。
 * - 用途：在原版渲染之前切换页面内容，不依赖抬高覆盖层遮挡原界面。
 */
/**
 * 【变更记录 #2】2026-10-03 03:34:00（第 2 次备注 · 1.2.0-transmutation-crafting）
 * - 删除：只识别介质页的背景判断。
 * - 新增：合成页使用独立背景、背包提示与原版槽位交互。
 * - 修改：非物品页跳过 ProjectE 原有文字和特殊提示。
 * - 用途：合成材料和输出不被原版转化桌背景或物品提示遮盖。
 */
/**
 * 【变更记录 #3】2026-10-12 14:52:00（第 3 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：介质页取消 ProjectE 原生 labels 和 tooltip 渲染的分支。
 * - 新增：仅合成页继续使用自定义背景、标签与背包提示路径。
 * - 修改：介质页恢复 ProjectE 原生背景、搜索框、EMC 标签和 tooltip；自定义介质目录只覆盖槽位图标与点击动作。
 * - 用途：避免介质页失去原版 EMC/学习提示或重复仿制原生控件。
 */
/**
 * 【变更记录 #4】2026-10-12 15:03:11（第 4 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：介质页取消 renderBg、renderLabels 和 renderTooltip 的判断。
 * - 新增：无。
 * - 修改：仅合成页走自定义背景/标签/提示；介质页保留 GUITransmutation 原版绘制调用。
 * - 用途：沿用 ProjectE 实际搜索框、EMC 文字、学习提示和原生工具提示，避免 UI 重复实现。
 */
/**
 * 【变更记录 #5】2026-10-13 01:53:18（第 5 次备注 · 1.2.0-media-feedback）
 * - 删除：介质页原生特殊区域 tooltip；仍保留背包与真实容器名称。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
package com.primordial.mixin;

import com.primordial.client.TransmutationMediaButton;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GUITransmutation.class, remap = false)
public abstract class TransmutationPageMixin {
    @Inject(method = {"renderBg", "m_7286_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$background(GuiGraphics graphics, float partial, int x, int y, CallbackInfo ci) {
        if (TransmutationMediaButton.craftingPageActive(this)) {
            TransmutationMediaButton.renderMediaBackground(graphics);
            ci.cancel();
        }
    }

    @Inject(method = {"renderLabels", "m_280003_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$labels(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (TransmutationMediaButton.craftingPageActive(this)) ci.cancel();
    }

    @Inject(method = {"renderTooltip", "m_280072_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$tooltip(GuiGraphics graphics, int x, int y, CallbackInfo ci) {
        if (TransmutationMediaButton.secondaryPageActive(this)) {
            TransmutationMediaButton.renderInventoryTooltip(graphics, x, y);
            ci.cancel();
        }
    }
}
