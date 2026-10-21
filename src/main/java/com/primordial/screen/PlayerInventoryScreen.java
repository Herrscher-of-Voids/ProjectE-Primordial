/**
 * 【编码修复说明】PlayerInventoryScreen 的原备注因错误字符集转换被写成问号，已根据当前实现恢复。
 * - 删除：不可读的问号文本。
 * - 新增：职责、边界条件和失败处理说明。
 * - 修改：只修复注释内容，不改变字段、协议、算法或运行时行为。
 * - 用途：帮助维护者理解本段代码，并确保后续修改可追溯。
 */
/**
 * 【变更记录 #8】2026-09-14 16:45:00（第 8 次备注 · 1.0.0-stage5-feedback2-hotfix）
 * - 删除：运行时把旧 rate 字段当作固定模式速率的路径；旧字段仅保留给已有存档读取。
 * - 新增：固定模式使用 amount 数量字段，补充模式根据目标容量或缺口计算，不受固定数量影响。
 * - 修改：EnergyConfig、玩家背包规则、配置器界面和任务执行器统一采用“数量 + tick 间隔”语义，并继续限制非法值。
 * - 用途：避免高容量设备因为旧上限或错误速率解释而产生过量循环、配置丢失或跨槽位写入。
 */
/**
 * 【变更记录 #9】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #10】2026-09-15 10:59:49（第 10 次备注 · 补充规则暂停修复）
 * - 删除：无；保留原有玩家背包只读槽位和规则设置控件。
 * - 新增：暂停所选、启用所选按钮，支持一次操作多个已选槽位。
 * - 修改：界面高度和已选槽位提示位置适配新增按钮；开关请求只发送槽位索引，不携带或移动物品。
 * - 用途：玩家可在补充物品持续运行时立即暂停规则，之后按需恢复，不需要重新选择过滤物品。
 */
/**
 * 【变更记录 #11】2026-09-15 11:08:00（第 11 次备注 · 1.0.0-player-inventory-pause）
 * - 删除：只能点击开关却看不到服务端结果的反馈缺口。
 * - 新增：全部暂停按钮、各槽位状态标记和启用/暂停计数，支持选中空格子管理遗留规则。
 * - 修改：控件排布扩大为 246×222，显示固定模式数量和 tick 标签；编辑既有暂停规则不自动恢复。
 * - 用途：紧急停止持续补充，并从服务端菜单属性确认结果；恢复只需选槽位点击启用。
 */
/**
 * 【变更记录 #12】2026-09-15 12:04:51（第 12 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：取消所选绑定按钮，支持多选和空槽，显示删除规则的工具提示。
 * - 修改：服务端状态决定按钮是否可用；底行保留全部暂停并增加解绑；声明 IPN/Mouse Tweaks 忽略。
 * - 用途：永久删除补充规则且保留背包实物，暂停/恢复继续管理尚未删除的规则。
 */
/**
 * 【变更记录 #13】2026-09-15 15:25:00（第 13 次备注 · 1.0.0-stage9-gui-style）
 * - 删除：玩家背包界面独立维护的背景、槽位和文字颜色字面量。
 * - 新增：统一引用 PrimordialGuiStyle 调色板。
 * - 修改：只调整渲染颜色来源，保留槽位多选、暂停/恢复和 tick 参数交互。
 * - 用途：玩家背包配置与配置器、价格编辑共享同一套高对比度深色风格。
 */
/**
 * 【变更记录 #12】2026-10-14 17:12:00（第 12 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：背包配置下方的法力、灵魂自动补充按钮和中文用法提示。
 * - 修改：状态依服务端同步更新，缺少对应模组或 API 时按钮不可用。
 * - 用途：给玩家实际可操作的联动入口，避免新增单位仅能通过命令使用。
 */
package com.primordial.screen;

import com.primordial.data.SampleMode;
import com.primordial.menu.PlayerInventoryMenu;
import com.primordial.network.C2SPlayerActionPacket;
import com.primordial.network.C2SPlayerInventoryRulePacket;
import com.primordial.network.C2SSetPlayerInventoryEnabledPacket;
import com.primordial.network.PrimordialNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.IntStream;

@org.anti_ad.mc.ipn.api.IPNIgnore
@yalter.mousetweaks.api.MouseTweaksIgnore
public class PlayerInventoryScreen extends AbstractContainerScreen<PlayerInventoryMenu> {
    private final Set<Integer> selectedSlots = new LinkedHashSet<>();
    private EditBox amountBox;
    private EditBox intervalBox;
    private Button setButton;
    private Button pauseButton;
    private Button resumeButton;
    private Button pauseAllButton;
    private Button unbindButton;
    private SampleMode mode = SampleMode.KEEP_LEVEL;
    private final Button[] resourceButtons = new Button[2];

    public PlayerInventoryScreen(PlayerInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 246; imageHeight = 244;
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.charge_held"), b ->
                PrimordialNetwork.CHANNEL.sendToServer(new C2SPlayerActionPacket(2)))
                .bounds(leftPos + 8, topPos + 116, 230, 18).build());
        addRenderableWidget(Button.builder(modeText(), b -> {
            mode = mode == SampleMode.KEEP_LEVEL ? SampleMode.FIXED_RATE : SampleMode.KEEP_LEVEL;
            b.setMessage(modeText());
            updateModeWidgets();
        }).bounds(leftPos + 8, topPos + 151, 86, 18).build());
        amountBox = new EditBox(font, leftPos + 100, topPos + 152, 62, 16, Component.translatable("screen.primordial.amount"));
        amountBox.setMaxLength(10); amountBox.setFilter(s -> s.matches("\\d*")); addRenderableWidget(amountBox);
        amountBox.setValue("1");
        intervalBox = new EditBox(font, leftPos + 170, topPos + 152, 68, 16, Component.translatable("screen.primordial.interval_ticks"));
        intervalBox.setMaxLength(5); intervalBox.setFilter(s -> s.matches("\\d*")); addRenderableWidget(intervalBox);
        intervalBox.setValue("20");
        setButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.set"), b -> setRules())
                .bounds(leftPos + 8, topPos + 174, 74, 18).build());
        pauseButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.pause_selected"), b -> setSelectedEnabled(false))
                .bounds(leftPos + 86, topPos + 174, 74, 18).build());
        resumeButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.resume_selected"), b -> setSelectedEnabled(true))
                .bounds(leftPos + 164, topPos + 174, 74, 18).build());
        pauseAllButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.pause_all_inventory"), b ->
                PrimordialNetwork.CHANNEL.sendToServer(new C2SSetPlayerInventoryEnabledPacket(menu.containerId,
                        IntStream.range(0, PlayerInventoryMenu.SLOT_COUNT).toArray(), false)))
                .bounds(leftPos + 8, topPos + 198, 140, 18).build());
        unbindButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.unbind_selected"), b -> {
            int[] slots = selectedSlots.stream().mapToInt(PlayerInventoryMenu::inventoryIndex).toArray();
            if (slots.length > 0) PrimordialNetwork.CHANNEL.sendToServer(
                    new com.primordial.network.C2SClearPlayerInventoryRulesPacket(menu.containerId, slots));
        }).bounds(leftPos + 152, topPos + 198, 86, 18).build());
        unbindButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_unbind_hint")));
        setButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_set_hint")));
        pauseButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_pause_hint")));
        resumeButton.setTooltip(Tooltip.create(Component.translatable("screen.primordial.inventory_resume_hint")));
        updateModeWidgets();
        for (int index = 0; index < resourceButtons.length; index++) {
            final int resource = index;
            resourceButtons[index] = addRenderableWidget(Button.builder(resourceText(index), b -> {
                if (minecraft != null && minecraft.gameMode != null)
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 100 + resource);
            }).bounds(leftPos + 8 + index * 116, topPos + 222, 114, 18).build());
            resourceButtons[index].setTooltip(Tooltip.create(Component.translatable("screen.primordial.player_resource_hint")));
        }
    }

    private void updateModeWidgets() {
        boolean fixed = mode == SampleMode.FIXED_RATE;
        amountBox.visible = fixed; amountBox.active = fixed;
        intervalBox.visible = fixed; intervalBox.active = fixed;
    }

    private Component resourceText(int index) {
        String state = switch (menu.resourceState(index)) {
            case PlayerInventoryMenu.ENABLED -> "screen.primordial.on";
            case PlayerInventoryMenu.PAUSED -> "screen.primordial.off";
            default -> "screen.primordial.resource_unavailable";
        };
        return Component.translatable("screen.primordial.player_resource_toggle",
                Component.translatable(index == 0 ? "screen.primordial.iron_mana" : "screen.primordial.soul_energy"),
                Component.translatable(state));
    }

    private void setRules() {
        if (selectedSlots.isEmpty()) return;
        int amount = 0, interval = 1;
        if (mode == SampleMode.FIXED_RATE) {
            try { amount = Integer.parseInt(amountBox.getValue()); interval = Integer.parseInt(intervalBox.getValue()); }
            catch (NumberFormatException ignored) { return; }
            if (amount <= 0 || interval <= 0) return;
        } else amount = 1;
        for (int menuSlot : selectedSlots) {
            ItemStack stack = menu.getSlot(menuSlot).getItem();
            if (stack.isEmpty()) continue;
            PrimordialNetwork.CHANNEL.sendToServer(new C2SPlayerInventoryRulePacket(
                    menu.containerId, PlayerInventoryMenu.inventoryIndex(menuSlot), amount, interval, mode,
                    true, stack.copyWithCount(1)));
        }
    }

    /** 向服务端批量设置所选规则的 enabled 状态；空选择不发送网络包。 */
    private void setSelectedEnabled(boolean enabled) {
        if (selectedSlots.isEmpty()) return;
        int[] slots = selectedSlots.stream().mapToInt(PlayerInventoryMenu::inventoryIndex).toArray();
        PrimordialNetwork.CHANNEL.sendToServer(new C2SSetPlayerInventoryEnabledPacket(menu.containerId, slots, enabled));
    }

    private Component modeText() {
        return Component.translatable(mode == SampleMode.KEEP_LEVEL
                ? "screen.primordial.mode_keep" : "screen.primordial.mode_fixed");
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < PlayerInventoryMenu.SLOT_COUNT; i++) {
            var slot = menu.slots.get(i);
            if (mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                    && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16) {
                if (button == 0) {
                    if (!selectedSlots.add(i)) selectedSlots.remove(i);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        gg.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PrimordialGuiStyle.SCREEN_BACKGROUND);
        gg.renderOutline(leftPos, topPos, imageWidth, imageHeight, PrimordialGuiStyle.PANEL_BORDER);
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i); int x = leftPos + slot.x, y = topPos + slot.y;
            int color = selectedSlots.contains(i) ? PrimordialGuiStyle.SELECTION : PrimordialGuiStyle.SLOT_BORDER;
            gg.fill(x - 1, y - 1, x + 17, y + 17, color);
            gg.fill(x, y, x + 16, y + 16, PrimordialGuiStyle.SLOT_BACKGROUND);
        }
    }

    @Override protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
        gg.drawString(font, Component.translatable("screen.primordial.player_inventory"), 8, 6, PrimordialGuiStyle.PRIMARY_TEXT, false);
        gg.drawString(font, Component.translatable("screen.primordial.selected_slots", selectedSlots.size()), 8, 103, PrimordialGuiStyle.SECONDARY_TEXT, false);
        int enabled = 0, paused = 0;
        boolean canPause = false, canResume = false;
        for (int i = 0; i < PlayerInventoryMenu.SLOT_COUNT; i++) {
            int state = menu.ruleState(i);
            if (state == PlayerInventoryMenu.ENABLED) enabled++;
            if (state == PlayerInventoryMenu.PAUSED) paused++;
            if (selectedSlots.contains(i)) {
                canPause |= state == PlayerInventoryMenu.ENABLED;
                canResume |= state == PlayerInventoryMenu.PAUSED;
            }
            var slot = menu.slots.get(i);
            int color = state == PlayerInventoryMenu.ENABLED ? 0xFF66DD88
                    : state == PlayerInventoryMenu.PAUSED ? 0xFFFFBB55 : PrimordialGuiStyle.SLOT_BORDER;
            gg.fill(slot.x, slot.y + 16, slot.x + 16, slot.y + 17, color);
        }
        gg.drawString(font, Component.translatable("screen.primordial.inventory_rule_counts", enabled, paused),
                111, 103, PrimordialGuiStyle.SECONDARY_TEXT, false);
        if (mode == SampleMode.FIXED_RATE) {
            gg.drawString(font, Component.translatable("screen.primordial.amount"), 100, 140, PrimordialGuiStyle.SECONDARY_TEXT, false);
            gg.drawString(font, Component.translatable("screen.primordial.interval_ticks"), 170, 140, PrimordialGuiStyle.SECONDARY_TEXT, false);
        } else {
            gg.drawString(font, Component.translatable("screen.primordial.keep_unlimited"), 8, 140, PrimordialGuiStyle.SECONDARY_TEXT, false);
        }
        setButton.active = !selectedSlots.isEmpty();
        pauseButton.active = canPause;
        resumeButton.active = canResume;
        unbindButton.active = canPause || canResume;
        pauseAllButton.active = enabled > 0;
    }

    /** 槽位标记与提示使用服务端同步状态；格子为空时同样显示已配置规则的状态。 */
    @Override public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        for (int index = 0; index < resourceButtons.length; index++) if (resourceButtons[index] != null) {
            resourceButtons[index].setMessage(resourceText(index));
            resourceButtons[index].active = menu.resourceState(index) != PlayerInventoryMenu.UNCONFIGURED;
        }
        renderBackground(gg);
        super.render(gg, mouseX, mouseY, partialTick);
        renderTooltip(gg, mouseX, mouseY);
        if (hoveredSlot != null) {
            String key = switch (menu.ruleState(hoveredSlot.index)) {
                case PlayerInventoryMenu.ENABLED -> "screen.primordial.inventory_rule_enabled";
                case PlayerInventoryMenu.PAUSED -> "screen.primordial.inventory_rule_paused";
                default -> "screen.primordial.inventory_rule_none";
            };
            if (hoveredSlot.getItem().isEmpty()) {
                gg.renderTooltip(font, Component.translatable(key), mouseX, mouseY);
            } else {
                gg.drawString(font, Component.translatable(key), leftPos + 8, topPos + imageHeight + 3, 0xFFFFFFFF, false);
            }
        }
    }
}
