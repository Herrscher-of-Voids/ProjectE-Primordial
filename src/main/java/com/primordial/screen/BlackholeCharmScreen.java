/**
 * 【文件职责】呈现护符物品、NBT与标签筛选编辑界面。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：类别切换、黑白名单、候选勾选、分页和增删按钮，操作即时保存。
 * - 修改：固定256×240布局，长注册名省略并提供完整悬停内容；玩家库存仅取样。
 * - 用途：IPN及MouseTweaks忽略此界面；动作经专用VarInt包避免高编号丢失。
 */
package com.primordial.screen;

import com.primordial.menu.BlackholeCharmMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.ArrayList;
import java.util.List;

/** 深色幽灵样本编辑器：左侧选 NBT/标签，右侧显示并删除当前黑/白名单，逐次自动保存。 */
@org.anti_ad.mc.ipn.api.IPNIgnore
@yalter.mousetweaks.api.MouseTweaksIgnore
public final class BlackholeCharmScreen extends AbstractContainerScreen<BlackholeCharmMenu> {
    private final List<Button> rows = new ArrayList<>();
    private Button mode;
    private int candidatePage;
    private int rulePage;
    private int revision = -1;
    private static final int PAGE_SIZE = 4;

    public BlackholeCharmScreen(BlackholeCharmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 256; imageHeight = 240;
    }
    @Override protected void init() {
        super.init(); rows.clear();
        for (int type = 0; type < 3; type++) {
            final int kind = type;
            button(8 + type * 81, 18, 78, 18, tr("type_" + type), b -> {
                candidatePage = 0; rulePage = 0; send(kind);
            });
        }
        button(38, 40, 88, 18, tr("add"), b -> send(4));
        mode = button(132, 40, 116, 18, tr("whitelist"), b -> { rulePage = 0; send(3); });
        for (int side = 0; side < 2; side++) for (int row = 0; row < PAGE_SIZE; row++) {
            final int column = side, line = row;
            rows.add(button(8 + side * 124, 78 + row * 12, 116, 11, Component.empty(), b -> {
                int index = (column == 0 ? candidatePage : rulePage) * PAGE_SIZE + line;
                send((column == 0 ? 2000 : 1000) + index);
            }));
        }
        button(8, 128, 28, 16, Component.literal("<"), b -> { candidatePage--; refresh(); });
        button(96, 128, 28, 16, Component.literal(">"), b -> { candidatePage++; refresh(); });
        button(132, 128, 28, 16, Component.literal("<"), b -> { rulePage--; refresh(); });
        button(220, 128, 28, 16, Component.literal(">"), b -> { rulePage++; refresh(); });
        refresh();
    }
    private Component tr(String key) { return Component.translatable("screen.primordial.charm." + key); }
    private Button button(int x, int y, int w, int h, Component label, Button.OnPress press) {
        return addRenderableWidget(Button.builder(label, press).bounds(leftPos + x, topPos + y, w, h).build());
    }
    private void send(int action) {
        com.primordial.network.PrimordialNetwork.CHANNEL.sendToServer(
                new com.primordial.network.C2SCharmFilterActionPacket(menu.containerId, menu.revision(), action));
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (revision != menu.revision()) refresh();
    }
    private void refresh() {
        revision = menu.revision();
        List<String> candidates = menu.candidates();
        candidatePage = Math.max(0, Math.min(candidatePage, Math.max(0, (candidates.size() - 1) / PAGE_SIZE)));
        rulePage = Math.max(0, Math.min(rulePage, Math.max(0, (menu.rules().size() - 1) / PAGE_SIZE)));
        mode.setMessage(tr(menu.blacklist() ? "blacklist" : "whitelist"));
        for (int side = 0; side < 2; side++) for (int row = 0; row < PAGE_SIZE; row++) {
            int index = (side == 0 ? candidatePage : rulePage) * PAGE_SIZE + row;
            Button button = rows.get(side * PAGE_SIZE + row);
            int size = side == 0 ? candidates.size() : menu.rules().size();
            button.visible = button.active = index < size;
            if (!button.visible) continue;
            String text;
            if (side == 0) text = (menu.selected(candidates.get(index)) ? "[x] " : "[ ] ") + candidates.get(index);
            else {
                Tag rule = menu.rules().get(index);
                text = "[-] " + (rule instanceof CompoundTag nbt ? nbt.getString("id") : rule.getAsString());
                button.setTooltip(Tooltip.create(Component.literal(rule.getAsString().substring(0, Math.min(1800, rule.getAsString().length())))));
            }
            if (side == 0) button.setTooltip(Tooltip.create(Component.literal(text)));
            button.setMessage(Component.literal(font.plainSubstrByWidth(text, 108)));
        }
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0080C12);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF3A5A7A);
        for (var slot : menu.slots) g.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF202830);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 5, 0x7FDFFF, false);
        g.drawString(font, font.plainSubstrByWidth(tr("sample_hint").getString(), 240), 8, 60, 0xAAAAAA, false);
        g.drawString(font, tr(menu.type() == 1 ? "nbt_hint" : "candidates"), 8, 69, 0x7FDFFF, false);
        g.drawString(font, tr("rules"), 132, 69, 0x7FDFFF, false);
        g.drawCenteredString(font, Integer.toString(candidatePage + 1), 66, 132, 0xAAAAAA);
        g.drawCenteredString(font, Integer.toString(rulePage + 1), 190, 132, 0xAAAAAA);
        g.drawString(font, font.plainSubstrByWidth(tr(menu.accepted() ? "saved" : "rejected").getString(), 240),
                8, 229, menu.accepted() ? 0xAAAAAA : 0xFFAA55, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g); super.render(g, mouseX, mouseY, partial); renderTooltip(g, mouseX, mouseY);
    }
}
