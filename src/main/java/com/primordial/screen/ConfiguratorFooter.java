/**
 * 【文件职责】管理既有价格幽灵槽、价格输入、清零和 EMC 页脚绘制。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：从主 Screen 移入相关控件与绘制方法，无功能删除。
 * - 新增：有限数价格输入校验和长物品 ID 文案裁剪。
 * - 修改：生命周期随主界面重建；目标选择页不创建或绘制价格控件。
 * - 用途：与三种 EMC 格式化显示共用 EmcFormatter，价格和清零仍使用既有网络操作。
 */
/**
 * 【变更记录 #2】2026-09-15 15:07:10（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：旧控件的浮点输入和传输。
 * - 新增：BigInteger 价格及菜单身份参数。
 * - 修改：保留旧页脚源码的编译兼容，实际机器页继续不创建价格控件。
 * - 用途：消除项目中遗留的 double 定价入口，与统一价格协议保持一致。
 */
/**
 * 【变更记录 #3】2026-09-15 15:25:00（第 3 次备注 · 1.0.0-stage9-gui-style）
 * - 删除：页脚独立维护的面板、边框和槽位颜色字面量。
 * - 新增：统一引用 PrimordialGuiStyle 调色板。
 * - 修改：保留价格输入、EMC 余额和清零操作，仅统一视觉颜色。
 * - 用途：页脚与机器配置、玩家背包界面的 16×16 槽位和深色面板保持一致。
 */
package com.primordial.screen;

import com.primordial.client.ClientBalanceState;
import com.primordial.network.C2SPlayerActionPacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.util.EmcFormatter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** 配置器原有价格编辑和 EMC 页脚；单独管理控件与绘制，避免主 Screen 持续膨胀。 */
final class ConfiguratorFooter {
    private final ConfiguratorScreen screen;
    private ItemStack pricingItem = ItemStack.EMPTY;
    private EditBox price;
    private int left, top, width, height;

    ConfiguratorFooter(ConfiguratorScreen screen) { this.screen = screen; }

    void init(int left, int top, int width, int height, Font font) {
        this.left = left; this.top = top; this.width = width; this.height = height;
        screen.addControl(Button.builder(Component.translatable("screen.primordial.clear"),
                b -> PrimordialNetwork.CHANNEL.sendToServer(new C2SPlayerActionPacket(0)))
                .bounds(left + width - 32, top + 4, 28, 12).build());
        price = new EditBox(font, left + 30, top + 222, 70, 14, Component.translatable("screen.primordial.custom_price"));
        price.setMaxLength(com.primordial.value.ValueTable.MAX_DIGITS);
        price.setFilter(s -> s.matches("[0-9]*"));
        price.setTextColor(0xFFFFFF);
        screen.addControl(price);
        screen.addControl(Button.builder(Component.translatable("screen.primordial.set"), b -> setPrice())
                .bounds(left + 106, top + 221, 36, 16).build());
    }

    private void setPrice() {
        if (pricingItem.isEmpty()) return;
        try {
            java.math.BigInteger value = new java.math.BigInteger(price.getValue());
            if (value.signum() < 0) return;
            PrimordialNetwork.CHANNEL.sendToServer(new C2SPlayerActionPacket(
                    BuiltInRegistries.ITEM.getKey(pricingItem.getItem()).toString(), value, screen.getMenu().containerId));
        } catch (NumberFormatException ignored) {
            // 编辑中的空文本或单个小数点不构成价格，保留输入供玩家继续编辑。
        }
    }

    boolean click(double mouseX, double mouseY) {
        if (mouseX < left + 8 || mouseX >= left + 24 || mouseY < top + 222 || mouseY >= top + 238) return false;
        pricingItem = screen.getMenu().getCarried().copy();
        if (pricingItem.isEmpty()) price.setValue("");
        return true;
    }

    void background(GuiGraphics gg) {
        gg.fill(left + 2, top + 206, left + width - 2, top + 242, PrimordialGuiStyle.PANEL_BACKGROUND);
        gg.renderOutline(left + 2, top + 206, width - 4, 36, PrimordialGuiStyle.PANEL_BORDER);
        gg.fill(left + 7, top + 221, left + 25, top + 239, PrimordialGuiStyle.SLOT_FRAME);
        gg.fill(left + 8, top + 222, left + 24, top + 238, PrimordialGuiStyle.SLOT_EMPTY);
    }

    void render(GuiGraphics gg, Font font) {
        if (!pricingItem.isEmpty()) {
            gg.renderItem(pricingItem, left + 8, top + 222);
            gg.renderItemDecorations(font, pricingItem, left + 8, top + 222);
        }
    }

    /** 由 renderLabels 调用，坐标已经平移到 GUI 左上角。 */
    void labels(GuiGraphics gg, Font font) {
        String balance = Component.translatable("hud.primordial.balance", EmcFormatter.format(ClientBalanceState.getEmc())).getString();
        gg.drawString(font, balance, width - 6 - font.width(balance), height - 10, PrimordialGuiStyle.SELECTION, true);
        gg.drawString(font, Component.translatable("screen.primordial.custom_price"), 8, 208, PrimordialGuiStyle.PRIMARY_TEXT, false);
        if (!pricingItem.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(pricingItem.getItem()).toString();
            gg.drawString(font, font.plainSubstrByWidth(id, width - 156), 150, 208, PrimordialGuiStyle.SECONDARY_TEXT, false);
        }
    }
}
