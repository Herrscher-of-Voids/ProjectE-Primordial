/**
 * 【文件职责】在同一配置菜单内展示方向按钮、分页端口图标和绑定操作。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增独立视图控制器。
 * - 新增：六面、27 槽分页、刷新/解绑/返回、实际内容及容量提示。
 * - 修改：按 section/sample/side 接受响应；不显示内部 Java 类与 key，空身份按钮不可绑定。
 * - 用途：旧页面或失败回包不会转移物品；服务器确认身份后持久化，编号代表公开自动化端口而非原 GUI 索引。
 */
/**
 * 【变更记录 #2】2026-09-14 12:15:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：能量页增加“内部”方向按钮，对应 side=6 的无方向 FE capability。
 * - 修改：方向文本在内部端口不再调用 Direction.from3DDataValue，避免非法枚举异常。
 * - 用途：玩家可以在同一端口选择界面绑定龙之研究能量塔等 null side 能源。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
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
 * 【变更记录 #10】2026-09-15 12:04:51（第 10 次备注 · 1.0.0-configurator-fixes）
 * - 删除：气体切换按钮必须匹配 Mekanism 代理 ID 的条件。
 * - 新增：根据已保存气体过滤器默认打开气体端口页。
 * - 修改：将 27 个端口移至 y=68、行距 32，避免与切换按钮及分页说明重叠。
 * - 用途：新气体样本未绑定时也能直接选择气罐；第三方气体设备可切换查询。
 */
/**
 * 【变更记录 #11】2026-09-15 12:08:58（第 11 次备注 · 1.0.0-configurator-fixes）
 * - 删除：接受上一次流体/气体模式的延迟端口响应。
 * - 新增：响应 gas 标识校验。
 * - 修改：快速切换介质时仅接收当前模式回包；最终端口区 y=68，行距32，高度30。
 * - 用途：避免气体页显示流体端口，且三排端口、气体切换与分页提示互不重叠。
 */
/**
 * 【变更记录 #12】2026-09-15 14:29:35（第 12 次备注 · 1.0.0-configurator-layout）
 * - 删除：流体/气体切换按钮在方向按钮下方的旧布局，以及说明文字横跨右侧操作按钮的绘制区域。
 * - 新增：把介质切换按钮放到选择面板右上角；说明文字限制在左下区域并由字体自动换行。
 * - 修改：适配器标题裁剪到右上切换按钮左侧，端口、分页和绑定操作继续使用原有坐标。
 * - 用途：避免标题、介质切换、绑定/解绑按钮与端口提示互相覆盖，保证不同语言文本在固定面板尺寸内可读。
 */
/**
 * 【变更记录 #13】2026-09-15 15:25:00（第 13 次备注 · 1.0.0-stage9-gui-style）
 * - 删除：端口选择页独立维护的标题、提示和选中颜色字面量。
 * - 新增：统一引用 PrimordialGuiStyle 的基础文本和高亮色。
 * - 修改：保留介质按钮右上布局、端口分页和多选绑定逻辑，仅统一渲染颜色来源。
 * - 用途：让端口选择页与配置器、玩家背包和价格编辑界面使用同一套深色 EMC 风格。
 */
/**
 * 【变更记录 #14】2026-09-16（独立资源默认内部端口）
 * - 新增：首次打开魔力、气压、灵气、魔源、转速或应力选择页时默认查询“内部”方向。
 * - 修改：已绑定目标继续沿用原方向，物品/流体/能量页的默认方向保持不变。
 * - 用途：这些资源通常由方块实体内部容器提供，玩家无需先猜测六个外部方向即可看到端口。
 */
/**
 * 【变更记录 #15】2026-10-02 21:50:00（第 15 次备注 · 1.2.0-relative-sides）
 * - 删除：按世界方向排列的六个纯文字按钮。
 * - 新增：机器面贴图展开图、参照说明和相对侧面名称。
 * - 修改：27 端口改为右侧六列布局；未绑定默认前面，已绑定仍沿用绝对侧面。
 * - 用途：让选择、提示和配置摘要共用机器朝向；存档与网络协议保持不变。
 */
/**
 * 【变更记录 #16】2026-10-02 22:18:22（第 16 次备注 · 1.2.0-neighbor-side-icons）
 * - 删除：把目标机器自身状态传入六面图标的逻辑。
 * - 新增：按机器坐标偏移一个实际侧面，提供相邻方块状态读取器。
 * - 修改：同维度、已加载检查共用于机器与邻居；相对方向和实际端口绑定保持不变。
 * - 用途：显示该方向隔壁放置的方块，空气与不可读取状态交由按钮分别提示。
 */
/**
 * 【变更记录 #17】2026-10-13 22:57:15（第 17 次备注 · 1.2.0-relative-target-system）
 * - 删除：新绑定只保存世界方向、机器旋转后仍操作旧面的路径；所有历史备注保留。
 * - 新增/修改：已存相对目标按机器现朝向显示；打开时选中真实连接面，旋转时保持相对面并清空陈旧选择重查；待加载不能绑定，旧目标标为固定面。
 * - 用途：第 9 系统相对面绑定与显示、网络、存档、执行保持一致；已有绝对目标重新绑定才转为相对，避免误改输入输出。
 */
package com.primordial.screen;

import com.primordial.data.MachineTarget;
import com.primordial.machine.MachinePortPage;
import com.primordial.network.C2SMachineTargetPacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CMachinePortsPacket;
import com.primordial.resource.ResourceExchangeConversions;
import com.primordial.util.RelativeMachineSides;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 配置器内部的端口选择页；保持同一个服务端菜单，按需查询选中方向的 27 个真实端口。 */
final class MachineTargetPicker {
    private final ConfiguratorScreen screen;
    private int section, sample, side, page, left, top;
    private boolean gasMode;
    private MachinePortPage snapshot;
    private boolean loading;
    private RelativeMachineSides sides = new RelativeMachineSides(Direction.NORTH, true);
    private static final int PORT_COLUMNS = 6, PORT_STEP = 28, PORT_SIZE = 26;
    /** 当前页已选择的端口；绑定按钮会把它们作为一个样本的多个目标提交。 */
    private final Map<Integer, String> selected = new LinkedHashMap<>();

    MachineTargetPicker(ConfiguratorScreen screen) { this.screen = screen; }

    void open(int section, int sample, MachineTarget current) {
        open(section, sample, current == null ? List.of() : List.of(current));
    }

    void open(int section, int sample, java.util.Collection<MachineTarget> currentTargets) {
        sides = RelativeMachineSides.from(machineState());
        this.section = section;
        this.sample = sample;
        this.gasMode = section >= 2 && section < 4 && currentTargets != null
                && currentTargets.stream().anyMatch(t -> t != null && t.bound() && t.key().contains("/gas/"));
        if (section >= 2 && section < 4) {
            var filter = screen.getMenu().clientFluidSample(sample + (section == 3 ? 9 : 0));
            if (filter != null && filter.hasGasFilter()) gasMode = true;
        }
        MachineTarget first = currentTargets == null || currentTargets.isEmpty() ? MachineTarget.UNBOUND
                : currentTargets.iterator().next().resolve(machineState());
        side = first.bound() ? first.side()
                : section >= 6 ? MachineTarget.INTERNAL_SIDE : sides.front().get3DDataValue();
        page = first.bound() ? first.index() / MachinePortPage.PAGE_SIZE : 0;
        selected.clear();
        if (currentTargets != null) for (MachineTarget target : currentTargets) {
            if (target == null || !target.bound()) continue;
            var resolved = target.resolve(machineState());
            if (resolved.side() == side) selected.put(resolved.index(), resolved.key());
        }
        screen.getMenu().pickingTarget = true;
        request();
    }

    private void request() {
        loading = true;
        snapshot = null;
        screen.rebuildTargetWidgets();
        PrimordialNetwork.CHANNEL.sendToServer(new C2SMachineTargetPacket(screen.getMenu().containerId,
                section, sample, side, page, false, "", false, gasMode, sides.front().get3DDataValue()));
    }

    void accept(S2CMachinePortsPacket msg) {
        if (!screen.getMenu().pickingTarget || msg.section() != section || msg.sample() != sample
                || msg.side() != side || msg.gas() != gasMode
                || msg.referenceFront() != sides.front().get3DDataValue()
                || !sides.equals(RelativeMachineSides.from(machineState()))) return;
        snapshot = msg.page();
        page = snapshot.page();
        loading = false;
        screen.getMenu().proxyId = snapshot.proxyId();
        screen.rebuildTargetWidgets();
    }

    void init(int left, int top) {
        this.left = left;
        this.top = top;
        for (var face : RelativeMachineSides.Face.values()) {
            Direction direction = sides.direction(face);
            int id = direction.get3DDataValue();
            var neighborPos = screen.getMenu().pos.relative(direction);
            Button button = new MachineSideButton(left + 8 + face.column * 31, top + 28 + face.row * 35,
                    directionText(id), () -> stateAt(neighborPos), direction, id == side, b -> {
                if (side == id) return;
                side = id; page = 0; selected.clear(); request();
            });
            screen.addTargetButton(button);
        }
        if (section >= 4) {
            Button internal = Button.builder(directionText(MachineTarget.INTERNAL_SIDE), b -> {
                side = MachineTarget.INTERNAL_SIDE; page = 0; selected.clear(); request();
            }).bounds(left + 8, top + 140, 60, 18).build();
            internal.active = side != MachineTarget.INTERNAL_SIDE;
            screen.addTargetButton(internal);
        }
        if (section >= 2 && section < 4 && com.primordial.compat.MekanismGasCompat.loaded()) {
            addButton(gasMode ? "screen.primordial.fluid_mode" : "screen.primordial.gas_mode", 244, 8, 66,
                    b -> { gasMode = !gasMode; page = 0; selected.clear(); request(); }, true);
        }
        if (snapshot != null) {
            for (int i = 0; i < snapshot.ports().size(); i++) {
                var port = snapshot.ports().get(i);
                Button button = Button.builder(Component.empty(), b -> toggle(port.index(), port.key()))
                        .bounds(portX(i), portY(i), PORT_SIZE, PORT_SIZE).build();
                button.active = port.bindable();
                screen.addTargetButton(button);
            }
        }
        addButton("screen.primordial.previous", 8, 182, 58, b -> { page--; request(); }, !loading && page > 0);
        addButton("screen.primordial.next", 72, 182, 58, b -> { page++; request(); },
                !loading && snapshot != null && (long) (page + 1) * MachinePortPage.PAGE_SIZE < snapshot.total());
        addButton("screen.primordial.refresh", 136, 182, 58, b -> request(), true);
        addButton("screen.primordial.bind_selected", 200, 182, 110, b -> bindSelected(), !loading && !selected.isEmpty());
        addButton("screen.primordial.unbind", 200, 202, 110, b -> bind(-1, ""), true);
        addButton("screen.primordial.back", 230, 220, 80, b -> screen.closeTargetPicker(), true);
    }

    private void addButton(String key, int x, int y, int w, Button.OnPress action, boolean active) {
        Button button = Button.builder(Component.translatable(key), action).bounds(left + x, top + y, w, 18).build();
        button.active = active;
        screen.addTargetButton(button);
    }

    private void bind(int index, String key) {
        PrimordialNetwork.CHANNEL.sendToServer(new C2SMachineTargetPacket(screen.getMenu().containerId,
                section, sample, side, index, true, key, false, gasMode, sides.front().get3DDataValue()));
        screen.closeTargetPicker();
    }

    private void toggle(int index, String key) {
        if (selected.containsKey(index)) selected.remove(index);
        else if (!key.isEmpty()) selected.put(index, key);
        screen.rebuildTargetWidgets();
    }

    /** 首个包替换旧目标，后续包追加；每次都由服务端重新校验 key。 */
    private void bindSelected() {
        if (loading || selected.isEmpty() || refreshOrientation()) return;
        boolean append = false;
        for (Map.Entry<Integer, String> entry : selected.entrySet()) {
            PrimordialNetwork.CHANNEL.sendToServer(new C2SMachineTargetPacket(screen.getMenu().containerId,
                    section, sample, side, entry.getKey(), true, entry.getValue(), append, gasMode, sides.front().get3DDataValue()));
            append = true;
        }
        screen.closeTargetPicker();
    }

    void render(GuiGraphics gg, Font font, int mouseX, int mouseY) {
        refreshOrientation();
        Component title = Component.translatable("screen.primordial.select_target");
        if (section >= 6) {
            var units = ResourceExchangeConversions.supportedUnits();
            if (sample >= 0 && sample < units.size()) title = Component.translatable(
                    section == 6 ? "screen.primordial.resource_input_title" : "screen.primordial.resource_output_title",
                    Component.translatable("screen.primordial.resource." + units.get(sample).getPath()));
        }
        gg.drawString(font, title, left + 8, top + 10, PrimordialGuiStyle.PRIMARY_TEXT, false);
        String adapter = snapshot == null ? screen.getMenu().proxyId : snapshot.proxyId();
        gg.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.primordial.adapter." + adapter).getString(), 90),
                left + 150, top + 10, 0xFFDDCC88, false);
        if (loading || snapshot == null || snapshot.total() == 0) {
            gg.drawString(font, Component.translatable(loading ? "screen.primordial.loading" : "screen.primordial.no_ports"),
                    left + 144, top + 36, PrimordialGuiStyle.SECONDARY_TEXT, false);
        } else {
            for (int i = 0; i < snapshot.ports().size(); i++) {
                var port = snapshot.ports().get(i);
                int x = portX(i), y = portY(i);
                ItemStack icon = port.item();
                if (!port.fluid().isEmpty()) icon = new ItemStack(port.fluid().getFluid().getBucket());
                if (!icon.isEmpty()) gg.renderItem(icon, x + 5, y + 1);
                else if (port.resource()) {
                    String shortName = resourceShortName(port.resourceUnit());
                    gg.drawString(font, font.plainSubstrByWidth(shortName, 24), x + 1, y + 4, 0xFFB9EEFF, false);
                } else if (section >= 4) gg.drawString(font, "FE", x + 8, y + 4, 0xFFFFFF88, false);
                else if (port.gas()) gg.drawString(font, "GAS", x + 2, y + 4, 0xFF88DDFF, false);
                String number = Integer.toString(port.index() + 1);
                gg.drawString(font, number, x + 13 - font.width(number) / 2, y + 17, port.bindable() ? 0xFFFFFFFF : 0xFFCC7777, false);
                if (selected.containsKey(port.index())) gg.renderOutline(x, y, PORT_SIZE, PORT_SIZE, 0xFF55FF55);
            }
            gg.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.primordial.port_page", page + 1,
                    (snapshot.total() - 1) / MachinePortPage.PAGE_SIZE + 1, snapshot.total()).getString(), 168),
                    left + 144, top + 170, PrimordialGuiStyle.SECONDARY_TEXT, false);
        }
        gg.drawString(font, Component.translatable("screen.primordial.side.selected", directionText(side)),
                left + 8, top + 164, PrimordialGuiStyle.PRIMARY_TEXT, false);
        // 提示的是玩家需要理解的端口编号，不把 Java 类或绑定 key 显示到产品界面。
        gg.drawWordWrap(font, Component.translatable("screen.primordial.port_hint"), left + 8, top + 204, 180, PrimordialGuiStyle.SECONDARY_TEXT);
        if (mouseX >= left + 8 && mouseX < left + 132 && mouseY >= top + 164 && mouseY < top + 179) {
            gg.renderComponentTooltip(font, List.of(Component.translatable("screen.primordial.side.perspective"),
                    Component.translatable(sides.fallback() ? "screen.primordial.side.reference_fallback" : "screen.primordial.side.reference",
                            Component.translatable("screen.primordial.side." + sides.front().getName())),
                    Component.translatable("screen.primordial.side.rotation_hint")), mouseX, mouseY);
        }
        if (snapshot != null) renderHovered(gg, font, mouseX, mouseY);
    }

    private void renderHovered(GuiGraphics gg, Font font, int mouseX, int mouseY) {
        for (int i = 0; i < snapshot.ports().size(); i++) {
            int x = portX(i), y = portY(i);
            if (mouseX < x || mouseX >= x + PORT_SIZE || mouseY < y || mouseY >= y + PORT_SIZE) continue;
            var port = snapshot.ports().get(i);
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("screen.primordial.target_label", directionText(side), port.index() + 1));
            if (!port.item().isEmpty()) tooltip.add(port.item().getHoverName());
            if (!port.fluid().isEmpty()) tooltip.add(port.fluid().getDisplayName());
            if (port.resource()) tooltip.add(Component.translatable("screen.primordial.resource_port", port.resourceUnit()));
            tooltip.add(Component.translatable("screen.primordial.port_amount", port.amount(), port.capacity()));
            if (!port.bindable()) tooltip.add(Component.translatable("screen.primordial.port_unsupported"));
            gg.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }
    }

    private int portX(int index) { return left + 144 + index % PORT_COLUMNS * PORT_STEP; }
    private int portY(int index) { return top + 28 + index / PORT_COLUMNS * PORT_STEP; }

    private BlockState machineState() {
        return stateAt(screen.getMenu().pos);
    }

    /** 机器在菜单打开时旋转：保留相对面，清除旧端口选择并重新向服务器查询。 */
    private boolean refreshOrientation() {
        var current = RelativeMachineSides.from(machineState());
        if (current.equals(sides)) return false;
        if (side != MachineTarget.INTERNAL_SIDE)
            side = current.direction(sides.face(Direction.from3DDataValue(side))).get3DDataValue();
        sides = current;
        page = 0;
        selected.clear();
        request();
        return true;
    }

    private BlockState stateAt(net.minecraft.core.BlockPos pos) {
        var level = Minecraft.getInstance().level;
        var menu = screen.getMenu();
        return level != null && level.dimension().equals(menu.dim) && level.hasChunkAt(pos)
                ? level.getBlockState(pos) : null;
    }

    Component directionText(int side) {
        if (side == MachineTarget.INTERNAL_SIDE) return Component.translatable("screen.primordial.side.internal");
        return Component.translatable("screen.primordial.side." + sides.face(Direction.from3DDataValue(side)).key);
    }

    Component targetText(MachineTarget target) {
        sides = RelativeMachineSides.from(machineState());
        if (!target.bound()) return Component.translatable("screen.primordial.target_unbound");
        var text = Component.translatable("screen.primordial.target_label",
                directionText(target.resolve(machineState()).side()), target.index() + 1);
        if (target.relativeFace() == null && target.side() != MachineTarget.INTERNAL_SIDE)
            text.append(Component.translatable("screen.primordial.side.legacy"));
        return text;
    }

    Component targetText(java.util.Collection<MachineTarget> targets) {
        if (targets == null || targets.isEmpty()) return Component.translatable("screen.primordial.target_unbound");
        MachineTarget first = targets.iterator().next();
        return targets.size() == 1 ? targetText(first)
                : Component.translatable("screen.primordial.target_multiple", targets.size());
    }

    /** 端口小格只有 30 像素宽，使用稳定缩写确保六类资源都能辨认。 */
    private static String resourceShortName(String unit) {
        if (unit == null) return "RES";
        return switch (unit) {
            case "botania:mana" -> "MANA";
            case "pneumaticcraft:pressure" -> "AIR";
            case "naturesaura:aura" -> "AURA";
            case "ars_nouveau:source" -> "SRC";
            case "create:rotation_speed" -> "RPM";
            case "create:stress" -> "STR";
            default -> unit.substring(unit.indexOf(':') + 1, Math.min(unit.length(), unit.indexOf(':') + 6));
        };
    }
}
