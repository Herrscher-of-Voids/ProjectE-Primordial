/**
 * 【文件职责】转化桌 NBT 设置的客户端按钮。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：三个标志位与菜单绑定同步；总开关关闭时禁用两个子选项。
 * - 修改：使用服务端回复显示真实设置，不接受过期菜单或非法标志位。
 * - 用途：设置只改变后续学习，界面按钮位于转化桌外侧，不覆盖或透传底下槽位。
 */
/**
 * 【变更记录 #2】2026-10-02 19:37:19（第 2 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：独立 ScreenEvent 订阅与不确定的按钮初始化顺序。
 * - 新增：供介质页初始化完成后调用的 install 入口。
 * - 修改：设置按钮不会被误收集为原版控件，也不会随页签切换错误显示子选项。
 * - 用途：保持设置展开状态，防止三个开关受原版/介质页显隐逻辑干扰。
 */
/**
 * 【变更记录 #3】2026-10-03 03:34:00（第 3 次备注 · 1.2.0-transmutation-crafting）
 * - 删除：设置按钮占用第三页签的原位置。
 * - 新增：第三页签下方的设置按钮空间。
 * - 修改：设置入口下移 22 像素，所有开关随入口整体下移。
 * - 用途：合成页与 NBT 设置均可独立操作，不发生按钮重叠。
 */
/**
 * 【变更记录 #4】2026-10-12 23:38:49（第 4 次备注 · 1.2.0-media-real-slots）
 * - 删除：旧介质页不完整的交互路径。
 * - 新增：设置入口及下属按钮整体下移 22 像素，避开合成页签。
 * - 修改：对照 ProjectE 真实槽位及原生控件处理。
 * - 用途：介质页状态、容器安全与显示一致，保留全部历史。
 */
/**
 * 【变更记录 #5】2026-10-13 01:53:18（第 5 次备注 · 1.2.0-media-feedback）
 * - 删除：设置及子开关英文代号、全部按钮悬停说明；设置和保/损/附开关用中文。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
/**
 * 【变更记录 #6】2026-10-13 02:29:36（第 6 次备注 · 1.2.0-nbt-option-tooltips）
 * - 删除：无。
 * - 新增：为“保留安全 NBT”“保留耐久”“保留附魔”三个选项绑定已有中英文 tooltip 翻译。
 * - 修改：仅在三个选项按钮上显示用法、开关效果及依赖说明；设置展开按钮不增加提示，服务端 flags 与网络同步不变。
 * - 用途：玩家可以悬停查看 NBT 学习策略，避免误以为开关会追溯修改已学知识；只影响转化桌客户端显示。
 */
/**
 * 【变更记录 #7】2026-10-13 04:37:24（第 7 次备注 · 1.2.0-nbt-independent-options）
 * - 删除：普通 NBT 关闭时将耐久和附魔按钮禁用的父子开关逻辑。
 * - 新增：服务端返回当前 flags 后，三个选项均可独立切换；未同步时仍全部禁用。
 * - 修改：保留现有菜单 ID 校验、服务端状态回传和页面生命周期检查。
 * - 用途：使按钮交互与服务端字段白名单逻辑一致，避免客户端造成状态假象。
 */
package com.primordial.client;

import com.primordial.network.C2SNbtSettingsPacket;
import com.primordial.network.S2CNbtSettingsPacket;
import com.primordial.network.PrimordialNetwork;
import java.lang.ref.WeakReference;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;

public final class TransmutationNbtButtons {
    private static WeakReference<View> current = new WeakReference<>(null);
    private TransmutationNbtButtons() { }
    public static void install(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof GUITransmutation screen)) return;
        var view = new View(screen);
        current = new WeakReference<>(view);
        event.addListener(view.settings);
        for (var button : view.options) event.addListener(button);
        PrimordialNetwork.CHANNEL.sendToServer(new C2SNbtSettingsPacket(screen.getMenu().containerId, -1));
    }
    public static void accept(S2CNbtSettingsPacket packet) {
        var view = current.get();
        if (view == null || Minecraft.getInstance().screen != view.screen
                || view.screen.getMenu().containerId != packet.containerId()) return;
        view.flags = packet.flags();
        view.ready = true;
        view.refresh();
    }
    private static final class View {
        final GUITransmutation screen;
        final Button settings;
        final Button[] options = new Button[3];
        int flags = 7;
        boolean expanded, ready;
        View(GUITransmutation screen) {
            this.screen = screen;
            int x = screen.getGuiLeft() - 28, y = screen.getGuiTop() + 93;
            settings = Button.builder(Component.literal("设置"), button -> {
                expanded = !expanded; refresh();
            }).pos(x, y).size(26, 20).build();
            String[] tooltipKeys = {
                    "screen.primordial.nbt.retain",
                    "screen.primordial.nbt.damage",
                    "screen.primordial.nbt.enchant"
            };
            for (int i = 0; i < 3; i++) {
                int bit = 1 << i;
                options[i] = Button.builder(Component.empty(), button -> {
                    ready = false;
                    refresh();
                    PrimordialNetwork.CHANNEL.sendToServer(new C2SNbtSettingsPacket(screen.getMenu().containerId, flags ^ bit));
                }).pos(x, y + 22 * (i + 1)).size(26, 20)
                        .tooltip(Tooltip.create(Component.translatable(tooltipKeys[i]))).build();
            }
            refresh();
        }
        void refresh() {
            String[] symbols = {"保", "损", "附"};
            for (int i = 0; i < 3; i++) {
                options[i].visible = expanded;
                options[i].active = ready;
                options[i].setMessage(Component.literal(symbols[i] + ((flags & (1 << i)) != 0 ? "+" : "-")));
            }
        }
    }
}
