/**
 * 【文件职责】
 * - 在 Forge GUI overlay 层渲染左上角余额 HUD。
 * - 读取客户端 EMC 缓存并通过统一格式化器生成玩家选择的显示文本。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：EMC 余额格式化调用。
 * - 新增：EmcFormatter 调用和 EMC 文案输出。
 * - 修改：HUD 数据源从 EMC long 改为 BigInteger EMC，继续保留隐藏 HUD、hideGui 和玩家存在性判断。
 * - 用途：保留原左上角 HUD 交互位置，同时支持紧凑、工程和科学计数法；渲染线程只做格式化，不修改服务端数据。
 */
/**
 * 【变更记录 #2】2026-09-16 14:41:00（第 2 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：无；保留三种余额显示模式。
 * - 新增：余额下方显示最近 10 tick 的实际加速扣款，用分组整数保留精度。
 * - 修改：明细独占下一行并按视口换行，不遮挡原余额；无消费时自动隐藏。
 * - 用途：在 Z/Y 等大余额显示不变时仍能核实 EMC 消耗，数据只来自服务端结算。
 */
package com.primordial.client;

import com.primordial.util.EmcFormatter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** 左上角 EMC HUD。 */
public class BalanceHudOverlay implements IGuiOverlay {
    public static final BalanceHudOverlay HUD = new BalanceHudOverlay();

    private BalanceHudOverlay() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics gg, float partialTick, int width, int height) {
        if (!ClientBalanceState.hudVisible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        String text = Component.translatable("hud.primordial.balance",
                EmcFormatter.format(ClientBalanceState.getEmc())).getString();
        // y 向下挪动，避免完全顶格遮挡其他 UI（如等价交换等 mod）
        gg.drawString(mc.font, text, 4, 18, 0xFF55FFFF, true);
        java.math.BigInteger charged = ClientBalanceState.getAccelerationCharge();
        if (charged.signum() > 0) {
            Component detail = Component.translatable("hud.primordial.acceleration_charge", EmcFormatter.toFullGrouped(charged));
            int y = 20 + mc.font.lineHeight;
            for (var line : mc.font.split(detail, Math.max(1, width - 8))) {
                gg.drawString(mc.font, line, 4, y, 0xFFFFD47A, true);
                y += mc.font.lineHeight + 1;
            }
        }
    }
}
