/**
 * 【文件职责】呈现加速模式、目标历史、几何设置及加速/休眠名单的原生管理界面。
 * 【变更记录 #1】2026-09-18 01:53:11（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：分页目标列表、模式及倍率选择、尺寸/偏移输入、AI 与休眠复选框、单项和批量管理。
 * - 修改：固定 230 像素高、宽度随窗口约束；长文本截断并提供提示，状态刷新保留未保存输入。
 * - 用途：无玩家库存槽，整理模组忽略；保存与启停由服务端确认，范围报价明确为上限。
 */
package com.primordial.screen;

import com.primordial.menu.AccelerationMenu;
import com.primordial.network.C2SAccelerationActionPacket;
import com.primordial.network.C2SAccelerationActionPacket.Action;
import com.primordial.network.PrimordialNetwork;
import com.primordial.service.*;
import com.primordial.util.EmcFormatter;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@org.anti_ad.mc.ipn.api.IPNIgnore
@yalter.mousetweaks.api.MouseTweaksIgnore
public final class AccelerationScreen extends AbstractContainerScreen<AccelerationMenu> {
    private static final int ROWS = 7;
    private final List<Button> rows = new ArrayList<>();
    private final List<EditBox> numbers = new ArrayList<>();
    private final List<Button> policies = new ArrayList<>();
    private Button modeButton, rateButton, save, start, pause, remove;
    private Checkbox idle, ai;
    private AccelerationSettings.Kind kind = AccelerationSettings.Kind.BLOCK;
    private int rate = 2, page, revision = -1, right, rightWidth, listWidth;
    private UUID formSelected;
    private boolean awaitingSave, received;

    public AccelerationScreen(AccelerationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 340; imageHeight = 230;
    }

    @Override protected void init() {
        imageWidth = Math.min(340, width - 8);
        super.init();
        rows.clear(); numbers.clear(); policies.clear();
        listWidth = Math.max(120, imageWidth * 2 / 5 - 8);
        right = listWidth + 20; rightWidth = imageWidth - right - 8;
        button(8, 26, listWidth, "+", tr("new"), b -> send(Action.SELECT, AccelerationBindingActions.SELECTION, false));
        modeButton = button(right, 26, rightWidth, "", tr("mode"), b -> {
            kind = AccelerationSettings.Kind.values()[(kind.ordinal() + 1) % 5]; updateControls();
        });
        rateButton = button(right, 48, rightWidth, "", tr("rate"), b -> {
            rate = EmcAccelerationService.nextMultiplier(rate); updateControls();
        });
        for (int row = 0; row < ROWS; row++) {
            final int line = row;
            rows.add(button(8, 50 + row * 18, listWidth, "", null, b -> {
                int index = page * ROWS + line;
                if (index < menu.targets().size()) send(Action.SELECT, AccelerationBindings.id(menu.targets().getCompound(index)), false);
            }));
        }
        button(8, 180, 22, "<", tr("previous"), b -> { page--; refresh(); });
        button(8 + listWidth - 22, 180, 22, ">", tr("next"), b -> { page++; refresh(); });
        int bulkWidth = (listWidth - 8) / 3;
        button(8, 200, bulkWidth, ">|", tr("start_all"), b -> send(Action.START_ALL, menu.selected(), false));
        button(12 + bulkWidth, 200, bulkWidth, "||", tr("pause_all"), b -> send(Action.PAUSE_ALL, menu.selected(), false));
        button(16 + bulkWidth * 2, 200, bulkWidth, "x", tr("clear_all"), b -> send(Action.CLEAR_ALL, menu.selected(), false));
        int cellWidth = (rightWidth - 8) / 3;
        for (int index = 0; index < 6; index++) {
            var box = new EditBox(font, leftPos + right + (index % 3) * (cellWidth + 4), topPos + (index < 3 ? 80 : 110),
                    cellWidth, 16, tr(index < 3 ? "size" : "offset"));
            box.setMaxLength(3); box.setFilter(value -> value.matches("-?[0-9]{0,2}"));
            box.setTooltip(Tooltip.create(tr(index < 3 ? "size_hint" : "offset_hint")));
            numbers.add(addRenderableWidget(box));
        }
        idle = addRenderableWidget(new Checkbox(leftPos + right, topPos + 133, rightWidth, 20, tr("idle"), true));
        ai = addRenderableWidget(new Checkbox(leftPos + right, topPos + 80, rightWidth, 20, tr("disable_ai"), false));
        idle.setTooltip(Tooltip.create(tr("idle_hint")));
        ai.setTooltip(Tooltip.create(tr("ai_hint")));
        for (int group = 0; group < 2; group++) {
            final boolean sleep = group == 1;
            int y = 155 + group * 20;
            policies.add(button(right, y, rightWidth - 48, "", tr(sleep ? "idle_list" : "area_list"), b ->
                    send(sleep ? Action.IDLE_POLICY : Action.AREA_POLICY, menu.selected(), !menu.selectedState().getBoolean(sleep ? "IdleWhitelist" : "AreaWhitelist"))));
            policies.add(button(right + rightWidth - 44, y, 20, "+", tr("mark_hint"), b ->
                    send(sleep ? Action.MARK_IDLE : Action.MARK_AREA, menu.selected(), false)));
            policies.add(button(right + rightWidth - 22, y, 22, "x", tr("clear_marks"), b ->
                    send(sleep ? Action.IDLE_CLEAR : Action.AREA_CLEAR, menu.selected(), menu.selectedState().getBoolean(sleep ? "IdleWhitelist" : "AreaWhitelist"))));
        }
        save = button(right, 198, rightWidth - 66, "", tr("save"), b -> send(Action.SAVE, menu.selected(), false));
        save.setMessage(tr("save"));
        start = button(right + rightWidth - 62, 198, 20, ">", tr("start"), b ->
                send(menu.selected().equals(AccelerationBindingActions.SELECTION) ? Action.WORLD : Action.START, menu.selected(), false));
        pause = button(right + rightWidth - 40, 198, 18, "||", tr("pause"), b -> send(Action.PAUSE, menu.selected(), false));
        remove = button(right + rightWidth - 20, 198, 20, "x", tr("remove"), b -> send(Action.REMOVE, menu.selected(), false));
        formSelected = null;
        refresh();
    }

    private Component tr(String key, Object... args) { return Component.translatable("screen.primordial.accelerator." + key, args); }
    private Button button(int x, int y, int w, String text, Component tooltip, Button.OnPress action) {
        var button = addRenderableWidget(Button.builder(Component.literal(text), action).bounds(leftPos + x, topPos + y, w, 16).build());
        if (tooltip != null) button.setTooltip(Tooltip.create(tooltip));
        return button;
    }
    private void send(Action action, UUID target, boolean flag) {
        AccelerationSettings settings = draft();
        if (settings == null || !settings.valid()) {
            if (action == Action.SAVE || action == Action.WORLD) return;
            settings = AccelerationSettings.read(menu.selectedState());
        }
        awaitingSave = action == Action.SAVE;
        PrimordialNetwork.CHANNEL.sendToServer(new C2SAccelerationActionPacket(menu.containerId, action, target, settings, flag, menu.hand()));
    }
    private AccelerationSettings draft() {
        try {
            int[] value = numbers.stream().mapToInt(box -> Integer.parseInt(box.getValue())).toArray();
            return new AccelerationSettings(kind, rate, value[0], value[1], value[2], value[3], value[4], value[5], idle.selected(), ai.selected());
        } catch (NumberFormatException invalid) { return null; }
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (revision != menu.revision()) refresh();
        updateControls();
    }
    private void refresh() {
        revision = menu.revision();
        if (!Objects.equals(formSelected, menu.selected()) || awaitingSave || !received && revision > 0) {
            var settings = AccelerationSettings.read(menu.selectedState());
            kind = settings.kind(); rate = settings.rate();
            int[] values = {settings.sizeX(), settings.sizeY(), settings.sizeZ(), settings.offsetX(), settings.offsetY(), settings.offsetZ()};
            for (int i = 0; i < values.length; i++) numbers.get(i).setValue(Integer.toString(values[i]));
            if (idle.selected() != settings.idleThrottle()) idle.onPress();
            if (ai.selected() != settings.disableAi()) ai.onPress();
            formSelected = menu.selected(); awaitingSave = false; received = revision > 0;
        }
        page = Math.max(0, Math.min(page, Math.max(0, (menu.targets().size() - 1) / ROWS)));
        for (int row = 0; row < ROWS; row++) {
            int index = page * ROWS + row;
            Button button = rows.get(row);
            button.visible = button.active = index < menu.targets().size();
            if (!button.visible) continue;
            CompoundTag entry = menu.targets().getCompound(index);
            String prefix = AccelerationBindings.id(entry).equals(menu.selected()) ? "> " : "";
            String text = prefix + Component.translatable(AccelerationSettings.read(entry).kind().translation()).getString()
                    + " x" + AccelerationBindings.rate(entry) + (AccelerationBindings.enabled(entry) ? " *" : "");
            button.setMessage(Component.literal(font.plainSubstrByWidth(text, listWidth - 8)));
            button.setTooltip(Tooltip.create(Component.literal(text + "\n" + entry.getString("TargetDimension") + "\n"
                    + AccelerationBindings.pos(entry).toShortString() + "\n" + entry.getString("EntityName"))));
        }
        updateControls();
    }

    private void updateControls() {
        boolean selection = menu.selected().equals(AccelerationBindingActions.SELECTION);
        boolean area = kind == AccelerationSettings.Kind.AREA;
        boolean entity = kind == AccelerationSettings.Kind.ENTITY;
        modeButton.setMessage(Component.translatable(kind.translation())); modeButton.active = selection;
        rateButton.setMessage(tr("multiplier", rate));
        for (EditBox box : numbers) { box.visible = area; box.active = area; }
        idle.visible = area; ai.visible = entity;
        for (Button button : policies) { button.visible = area; button.active = !selection; }
        for (int group = 0; group < 2; group++) {
            String prefix = group == 0 ? "Area" : "Idle";
            var label = tr((group == 0 ? "area_" : "idle_") + (menu.selectedState().getBoolean(prefix + "Whitelist") ? "allow" : "deny"),
                    menu.selectedState().getInt(prefix + "MarkCount"));
            policies.get(group * 3).setMessage(Component.literal(font.plainSubstrByWidth(label.getString(), rightWidth - 54)));
            policies.get(group * 3).setTooltip(Tooltip.create(label));
        }
        var current = draft();
        boolean valid = current != null && current.valid() && !kind.filter();
        save.active = valid;
        boolean dirty = !Objects.equals(current, AccelerationSettings.read(menu.selectedState()));
        start.active = valid && (selection ? kind.world() : !dirty);
        pause.active = remove.active = !selection;
    }

    @Override protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF016171A);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFFB6A06A);
        g.fill(leftPos + right - 7, topPos + 24, leftPos + right - 6, topPos + 218, 0xFF45464A);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 8, 0xEAD8A8, false);
        var status = tr(menu.accepted() ? "ticks_left" : "rejected", menu.state().getInt("TicksLeft"));
        g.drawString(font, font.plainSubstrByWidth(status.getString(), rightWidth), right, 8, menu.accepted() ? 0x80CDCA : 0xFF8888, false);
        if (kind == AccelerationSettings.Kind.AREA) {
            g.drawString(font, tr("size"), right, 70, 0xB8B8B8, false);
            g.drawString(font, tr("offset"), right, 100, 0xB8B8B8, false);
        }
        if (kind.world()) {
            g.drawString(font, font.plainSubstrByWidth(tr("day_time", menu.state().getLong("DayTime")).getString(), rightWidth), right, 80, 0xB8B8B8, false);
            String weather = menu.state().getString("Weather");
            g.drawString(font, tr("weather_" + (weather.isEmpty() ? "clear" : weather)), right, 100, 0xB8B8B8, false);
        }
        g.drawCenteredString(font, (page + 1) + "/" + Math.max(1, (menu.targets().size() + ROWS - 1) / ROWS), 8 + listWidth / 2, 184, 0xAAAAAA);
        String price = tr("quote", EmcFormatter.format(new BigInteger(menu.quote()))).getString();
        g.drawString(font, font.plainSubstrByWidth(price, imageWidth - 16), 8, 220, 0xEAD8A8, false);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (x >= leftPos + 8 && x < leftPos + 8 + listWidth && y >= topPos + 50 && y < topPos + 178) {
            page += delta < 0 ? 1 : -1; refresh(); return true;
        }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        renderBackground(g); super.render(g, x, y, partial); renderTooltip(g, x, y);
        if (x >= leftPos + 8 && x < leftPos + imageWidth - 8 && y >= topPos + 218 && y < topPos + 230)
            g.renderTooltip(font, tr("quote_detail", menu.quote()), x, y);
    }
}
