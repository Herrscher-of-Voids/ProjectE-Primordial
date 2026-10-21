/**
 * 【文件职责】
 * - 绘制配置器 GUI，处理物品、流体、能量分页及参数编辑交互。
 * - 负责显示客户端同步的机器配置、玩家 EMC 余额和自定义价格控件。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：底部余额区域对 PrimordialPoolSavedData.format(EMC) 的直接调用。
 * - 新增：使用 EmcFormatter 读取 BigInteger 客户端余额并格式化。
 * - 修改：只调整余额展示入口，样品槽、网络编辑和机器参数布局保持原有行为。
 * - 用途：让配置器余额显示与左上角 HUD 共用显示模式；格式化不执行网络操作，避免界面渲染线程修改状态。
 */
/**
 * 【文件职责】绘制原配置器各介质参数并接入机器端口选择页。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：删除内联价格/余额页脚实现，迁移到 ConfiguratorFooter。
 * - 新增：样本及供能/回收的目标按钮、目标回包处理、选择页 Esc 返回和物品提示。
 * - 修改：选择页保持原菜单并隐藏原库存；按 machineKey 接受配置同步，端口按钮显示明确方向/槽号或未绑定。
 * - 用途：目标选择由 MachineTargetPicker 负责，原生 GUI 外观复刻不在本修订中冒充已完成；所有新界面文案来自语言文件。
 */
/**
 * 【变更记录 #3】2026-09-14 13:50:00（第 3 次备注 · 1.0.0-stage5-feedback）
 * - 删除：机器页的 ConfiguratorFooter 价格/余额控件，价格编辑已移至空气潜行右键页面。
 * - 新增：JEI ItemStack、FluidStack、GasStack Ghost Ingredient 目标区域与多目标按钮文字。
 * - 修改：补充模式才启用检查间隔输入，并显示明确的“检查间隔（秒）”文案。
 * - 用途：机器配置只负责样本和端口，玩家价格与背包配置通过独立菜单完成。
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
 * - 删除：Rect2i.equals 对象身份定位、遍历隐藏页同坐标槽位和气体缩写文字绘制。
 * - 新增：当前样本取消绑定按钮、IPN/Mouse Tweaks 忽略标记、原生流体/气体图标及名称。
 * - 修改：按活动槽坐标投放 JEI ingredient，使用服务端过滤数据绘制；解绑只清目标列表。
 * - 用途：修复 JEI 不发包、流体气体拖放失效及无桶样本不可见，保留左键编辑/右键清除。
 */
/**
 * 【变更记录 #11】2026-09-15 12:08:58（第 11 次备注 · 1.0.0-configurator-fixes）
 * - 删除：重建控件后保留旧输入框/按钮引用的状态。
 * - 新增：初始化时清空动态控件引用。
 * - 修改：固定/补充模式切换后，onConfigSync 根据实际控件是否存在决定重建，解绑按钮状态跟随服务端目标。
 * - 用途：防止切换气体和其它页时已移除的旧控件导致反复重建或按钮不可用。
 */
/**
 * 【变更记录 #13】2026-09-15 15:25:00（第 13 次备注 · 1.0.0-stage9-gui-style）
 * - 删除：配置器局部定义的槽位、面板和文字颜色常量。
 * - 新增：统一引用 PrimordialGuiStyle 调色板，保持 16×16 槽位视觉与其它菜单一致。
 * - 修改：仅替换渲染颜色来源，保留目标选择、JEI 投放、参数同步和 EMC 显示行为。
 * - 用途：后续 GUI 扩展可以共享同一套深色边框和高亮色，避免不同页面出现风格漂移。
 */
/**
 * 【变更记录 #14】2026-09-16（资源端口配置页）
 * - 新增：第四个“独立资源”标签，列出魔力、气压、灵气、魔源、转速和应力。
 * - 修改：资源规则沿用数量、tick 间隔、补充/固定、启停和目标绑定控件；输入/输出方向单独切换。
 * - 用途：让资源端口在配置器中可见、可编辑并与 MachineConfig 的 ri/ro 字段保持同步。
 */
/**
 * 【变更记录 #15】2026-09-16（资源页视觉布局修复）
 * - 删除：资源页仍绘制玩家背包槽位、底部说明位于参数面板外侧的布局。
 * - 新增：资源参数面板扩展到底部说明区域，说明文字在固定宽度内自动换行。
 * - 修改：隐藏无关槽位并重新安排资源标题、数量、模式、端口和解绑控件，避免文字与图标重叠。
 * - 用途：保证独立资源页在中文字体和不同窗口缩放下保持水平、清晰且可点击。
 */
/**
 * 【变更记录 #16】2026-09-16 20:02:43（第 16 次备注 · 1.0.0-create-kinetics）
 * - 删除：无。
 * - 新增：转速/应力按钮悬停说明，明确单 tick 付费、持续供给和支持的接入位置。
 * - 修改：说明通过中英文语言键显示在自动换行提示中，不挤占现有控件空间。
 * - 用途：避免把动力误当储罐，固定模式持续运转需间隔 1 tick，转速和应力各自绑定。
 */
/**
 * 【变更记录 #17】2026-10-02 21:50:00（第 17 次备注 · 1.2.0-relative-sides）
 * - 删除：目标按钮直接使用世界侧面名称的静态调用。
 * - 新增：无；复用 MachineTargetPicker 的相对方向解析。
 * - 修改：物品、流体、能量和独立资源绑定摘要统一按目标机器朝向命名。
 * - 用途：让配置摘要与面贴图选择页一致，保留原菜单槽位隔离和全部服务端绑定逻辑。
 */
package com.primordial.screen;

import com.primordial.data.EnergyConfig;
import com.primordial.data.FluidSampleConfig;
import com.primordial.data.ItemSampleConfig;
import com.primordial.data.ResourceSampleConfig;
import com.primordial.data.SampleMode;
import com.primordial.menu.ConfiguratorMenu;
import com.primordial.network.C2SUpdateConfigPacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.util.EmcFormatter;
import com.primordial.resource.ResourceExchangeConversions;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 配置器 GUI：4 tab（物品/流体/能量/独立资源）+ 幽灵样本槽 + 参数编辑。
 * 纯代码绘制，无背景贴图。
 */
@org.anti_ad.mc.ipn.api.IPNIgnore
@yalter.mousetweaks.api.MouseTweaksIgnore
public class ConfiguratorScreen extends AbstractContainerScreen<ConfiguratorMenu> {
    private final MachineTargetPicker targetPicker = new MachineTargetPicker(this);
    private Button targetButton;
    private Button unbindButton;
    private Button supplyPortButton;
    private Button recoverPortButton;
    private static final int IMAGE_WIDTH = 320;
    private static final int IMAGE_HEIGHT = 250;

    private static final int SLOT_BG = PrimordialGuiStyle.SLOT_EMPTY;
    private static final int SLOT_BORDER = PrimordialGuiStyle.SLOT_FRAME;
    private static final int SEL_COLOR = PrimordialGuiStyle.SELECTION;
    private static final int PANEL_BG = PrimordialGuiStyle.PANEL_BACKGROUND;
    private static final int PANEL_BORDER = PrimordialGuiStyle.PANEL_BORDER;

    // 选中：selZone 0=输入 1=输出；selIndex 0-8；-1 = 未选中
    private int selZone = -1;
    private int selIndex = -1;

    private EditBox sampleThresholdBox;
    private EditBox sampleIntervalBox;
    private Button sampleModeBtn;
    private Button sampleEnabledBtn;

    private EditBox supplyTargetBox;
    private EditBox supplyIntervalBox;
    private EditBox recoverTargetBox;
    private EditBox recoverIntervalBox;

    private Button masterInputBtn;
    private Button masterOutputBtn;
    /** 独立资源页的输入/输出方向切换按钮。 */
    private Button resourceDirectionBtn;


    private boolean updatingUI = false;

    public ConfiguratorScreen(ConfiguratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = IMAGE_WIDTH;
        this.imageHeight = IMAGE_HEIGHT;
        this.titleLabelY = -9999;
        this.inventoryLabelY = -9999;
    }

    @Override
    protected void init() {
        super.init();
        sampleThresholdBox = sampleIntervalBox = null;
        supplyTargetBox = supplyIntervalBox = recoverTargetBox = recoverIntervalBox = null;
        sampleModeBtn = sampleEnabledBtn = targetButton = unbindButton = null;
        supplyPortButton = recoverPortButton = masterInputBtn = masterOutputBtn = resourceDirectionBtn = null;
        if (menu.pickingTarget) {
            targetPicker.init(leftPos, topPos);
            return;
        }
        for (int i = 0; i < 4; i++) {
            int tab = i;
            addRenderableWidget(Button.builder(
                     Component.translatable("screen.primordial.tab_" + i), b -> switchTab(tab))
                    .bounds(leftPos + 4 + i * 62, topPos + 5, 58, 15)
                    .build());
        }
        if (menu.activeTab < 2) {
            initMasterButtons();
            initSamplePanel();
        } else if (menu.activeTab == 2) {
            initEnergyPanel();
        } else {
            initResourcePanel();
        }
    }

    <T extends AbstractWidget> T addControl(T widget) { return addRenderableWidget(widget); }

    /** 输入/输出大开关（始终显示，无需选中样本） */
    private void initMasterButtons() {
        int inWhich = menu.activeTab == 0 ? 0 : 2;
        int outWhich = menu.activeTab == 0 ? 1 : 3;
        // 与两行样本槽的 y=36 / y=72 对齐，放在样本栏右侧
        masterInputBtn = addRenderableWidget(masterButton(inWhich, leftPos + 180, topPos + 36));
        masterOutputBtn = addRenderableWidget(masterButton(outWhich, leftPos + 180, topPos + 72));
    }

    private Button masterButton(int which, int x, int y) {
        return Button.builder(masterLabel(which), b -> {
            boolean on = menu.clientMaster(which);
            // 资源规则占用 section 6/7；介质总开关使用独立的 8-11，避免协议编号冲突。
            PrimordialNetwork.CHANNEL.sendToServer(
                    new C2SUpdateConfigPacket(menu.machineKey, 8 + which, 0, 0, on ? 0 : 1));
            b.setMessage(masterLabel(which, !on));
        }).bounds(x, y, 38, 16).build();
    }

    private Component masterLabel(int which) {
        return masterLabel(which, menu.clientMaster(which));
    }

    private Component masterLabel(int which, boolean on) {
        // 总开关只显示状态本身，不附带“开关”等其他字符
        return enabledText(on);
    }

    private void switchTab(int tab) {
        if (menu.activeTab == tab) return;
        menu.activeTab = tab;
        // 资源页始终选中第一个资源，物品/流体页仍等待玩家点击样本槽。
        selZone = tab == 3 ? 0 : -1;
        selIndex = tab == 3 ? 0 : -1;
        rebuildWidgets();
    }

    // ---------------- 物品/流体页 ----------------

    private void initSamplePanel() {
        if (selZone < 0 || selIndex < 0) return;
        if (menu.activeTab == 0 ? currentSample() == null : currentFluidSample() == null) return;
        if (currentMode() == SampleMode.FIXED_RATE) {
            addLabel(leftPos + 6, topPos + 94, "screen.primordial.amount");
            sampleThresholdBox = addNumberBox(leftPos + 6, topPos + 110, 54, 1, v -> sendSampleEdit(2, v));
            addLabel(leftPos + 66, topPos + 94, "screen.primordial.interval_ticks");
            sampleIntervalBox = addNumberBox(leftPos + 66, topPos + 110, 54, 1, v -> sendSampleEdit(3, v));
        } else {
            addLabel(leftPos + 6, topPos + 101, "screen.primordial.keep_unlimited");
        }
        sampleModeBtn = addRenderableWidget(Button.builder(currentModeText(), b -> {
            int next = currentMode().ordinal() ^ 1;
            sendSampleEdit(0, next);
            b.setMessage(modeText(SampleMode.VALUES[next]));
            rebuildWidgets();
        }).bounds(leftPos + 140, topPos + 109, 60, 16).build());
        if (selZone == 0) {
            sampleEnabledBtn = addRenderableWidget(Button.builder(currentEnabledText(), b -> {
                boolean on = currentEnabled();
                sendSampleEdit(4, on ? 0 : 1);
                b.setMessage(enabledText(!on));
            }).bounds(leftPos + 204, topPos + 109, 46, 16).build());
        }
        setSampleBoxes();
        targetButton = addRenderableWidget(Button.builder(targetPicker.targetText(currentTargets()),
                b -> targetPicker.open(menu.activeTab * 2 + selZone, selIndex, currentTargets()))
                .bounds(leftPos + 182, topPos + 142, 128, 20).build());
        targetButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.primordial.select_target")));
        unbindButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.unbind"), b ->
                PrimordialNetwork.CHANNEL.sendToServer(new com.primordial.network.C2SMachineTargetPacket(
                        menu.containerId, menu.activeTab * 2 + selZone, selIndex, 0, -1, true, "", false,
                        menu.activeTab == 1 && currentFluidSample().hasGasFilter())))
                .bounds(leftPos + 182, topPos + 166, 128, 18).build());
        unbindButton.active = !currentTargets().isEmpty();
    }

    private void setSampleBoxes() {
        updatingUI = true;
        if (sampleThresholdBox != null) sampleThresholdBox.setValue(String.valueOf(currentThreshold()));
        if (sampleIntervalBox != null) sampleIntervalBox.setValue(String.valueOf(currentInterval()));
        updatingUI = false;
        if (sampleModeBtn != null) sampleModeBtn.setMessage(currentModeText());
        if (sampleEnabledBtn != null) sampleEnabledBtn.setMessage(currentEnabledText());
        if (targetButton != null) targetButton.setMessage(targetPicker.targetText(currentTargets()));
        if (unbindButton != null) unbindButton.active = !currentTargets().isEmpty();
    }

    private com.primordial.data.MachineTarget currentTarget() {
        return menu.activeTab == 0 ? currentSample().target : currentFluidSample().target;
    }

    private java.util.List<com.primordial.data.MachineTarget> currentTargets() {
        if (menu.activeTab == 0) return currentSample() == null ? java.util.List.of() : currentSample().effectiveTargets();
        if (menu.activeTab == 1) return currentFluidSample() == null ? java.util.List.of() : currentFluidSample().effectiveTargets();
        if (menu.activeTab == 3) {
            ResourceSampleConfig sample = menu.clientResourceSample(selZone == 0, selIndex);
            return sample == null ? java.util.List.of() : sample.effectiveTargets();
        }
        return java.util.List.of();
    }

    void addTargetButton(Button button) { addRenderableWidget(button); }
    void rebuildTargetWidgets() { rebuildWidgets(); }
    void closeTargetPicker() { menu.pickingTarget = false; rebuildWidgets(); }
    public void acceptPorts(com.primordial.network.S2CMachinePortsPacket packet) { targetPicker.accept(packet); }

    /** JEI Ghost Ingredient 目标区域；只暴露当前 tab 可用的 18 个幽灵槽。 */
    public java.util.List<Rect2i> jeiSampleAreas(boolean fluid) {
        java.util.List<Rect2i> result = new java.util.ArrayList<>();
        if (menu.pickingTarget || (fluid && menu.activeTab != 1) || (!fluid && menu.activeTab != 0)) return result;
        int start = fluid ? ConfiguratorMenu.FLUID_IN_START : ConfiguratorMenu.ITEM_IN_START;
        for (int i = 0; i < 18; i++) {
            int idx = start + i; Slot slot = menu.slots.get(idx);
            if (slot.isActive()) result.add(new Rect2i(leftPos + slot.x, topPos + slot.y, 16, 16));
        }
        return result;
    }

    /** JEI 目标接受回调通过屏幕坐标定位槽位，再发送权威 C2S 过滤器包。 */
    public void acceptJeiIngredient(Object ingredient, Rect2i area) {
        for (int i = 0; i < ConfiguratorMenu.SAMPLE_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            // Rect2i 没有按坐标实现 equals；同时必须排除同坐标的隐藏页槽位。
            if (!slot.isActive() || leftPos + slot.x != area.getX() || topPos + slot.y != area.getY()) continue;
            int section = i < 18 ? (i < 9 ? 0 : 1) : (i < 27 ? 2 : 3);
            int index = i % 9;
            if (ingredient instanceof net.minecraft.world.item.ItemStack stack && section < 2)
                PrimordialNetwork.CHANNEL.sendToServer(new com.primordial.network.C2SSetItemFilterPacket(menu.containerId, section, index, stack));
            else if (ingredient instanceof net.minecraftforge.fluids.FluidStack fluid && section >= 2)
                PrimordialNetwork.CHANNEL.sendToServer(new com.primordial.network.C2SSetFluidFilterPacket(menu.containerId, section, index, fluid));
            else if (section >= 2 && com.primordial.compat.MekanismGasCompat.isGas(ingredient))
                com.primordial.compat.MekanismGasCompat.sendFilter(menu.containerId, section, index, ingredient);
            return;
        }
    }

    private boolean hasSelection() {
        if (selZone < 0 || selIndex < 0) return false;
        if (menu.activeTab == 0) return currentSample() != null;
        if (menu.activeTab == 1) return currentFluidSample() != null;
        return menu.activeTab == 3 && menu.clientResourceSample(selZone == 0, selIndex) != null;
    }

    private ItemSampleConfig currentSample() {
        if (selZone < 0 || selIndex < 0) return null;
        return menu.clientItemSample(selZone == 0 ? selIndex : selIndex + 9);
    }

    private FluidSampleConfig currentFluidSample() {
        if (selZone < 0 || selIndex < 0) return null;
        return menu.clientFluidSample(selZone == 0 ? selIndex : selIndex + 9);
    }

    private SampleMode currentMode() {
        if (menu.activeTab == 0) return java.util.Objects.requireNonNull(currentSample()).mode;
        if (menu.activeTab == 1) return java.util.Objects.requireNonNull(currentFluidSample()).mode;
        return java.util.Objects.requireNonNull(menu.clientResourceSample(selZone == 0, selIndex)).mode;
    }

    private int currentRate() {
        if (menu.activeTab == 0) return currentSample().rate;
        if (menu.activeTab == 1) return currentFluidSample().rate;
        return 0;
    }

    private int currentThreshold() {
        if (menu.activeTab == 0) return currentSample().threshold;
        if (menu.activeTab == 1) return currentFluidSample().threshold;
        return menu.clientResourceSample(selZone == 0, selIndex).amount;
    }

    private int currentInterval() {
        if (menu.activeTab == 0) return currentSample().intervalTicks;
        if (menu.activeTab == 1) return currentFluidSample().intervalTicks;
        return menu.clientResourceSample(selZone == 0, selIndex).intervalTicks;
    }

    private Component currentModeText() {
        return modeText(currentMode());
    }

    private boolean currentEnabled() {
        if (menu.activeTab == 0) return currentSample().enabled;
        if (menu.activeTab == 1) return currentFluidSample().enabled;
        return menu.clientResourceSample(selZone == 0, selIndex).enabled;
    }

    private Component currentEnabledText() {
        return enabledText(currentEnabled());
    }

    private void sendSampleEdit(int field, int value) {
        int section;
        if (menu.activeTab == 0) section = selZone == 0 ? 0 : 1;
        else if (menu.activeTab == 1) section = selZone == 0 ? 2 : 3;
        else if (menu.activeTab == 3) section = selZone == 0 ? 6 : 7;
        else return;
        PrimordialNetwork.CHANNEL.sendToServer(
                new C2SUpdateConfigPacket(menu.machineKey, section, selIndex, field, value));
    }

    // ---------------- 独立资源页 ----------------

    /**
     * 独立资源页不使用幽灵物品槽，而是直接列出六个稳定资源单位。点击单位后编辑输入
     * 或输出规则；方向按钮只切换当前编辑的规则，不会修改另一方向的配置。
     */
    private void initResourcePanel() {
        if (selZone < 0) selZone = 0;
        if (selIndex < 0 || selIndex >= ResourceExchangeConversions.supportedUnits().size()) selIndex = 0;
        var units = ResourceExchangeConversions.supportedUnits();
        for (int i = 0; i < units.size(); i++) {
            final int index = i;
            int col = i % 3;
            int row = i / 3;
            ResourceSampleConfig sample = menu.clientResourceSample(selZone == 0, i);
            String label = resourceName(units.get(i)).getString();
            if (sample != null && sample.enabled) label = "● " + label;
            Button unitButton = addRenderableWidget(Button.builder(Component.literal(label), b -> {
                selIndex = index;
                rebuildWidgets();
            }).bounds(leftPos + 6 + col * 104, topPos + 29 + row * 27, 100, 22).build());
            unitButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    i >= 4 ? Component.translatable("screen.primordial.create_resource_hint")
                            : Component.literal(units.get(i).toString())));
        }
        resourceDirectionBtn = addRenderableWidget(Button.builder(
                Component.translatable(selZone == 0 ? "screen.primordial.input" : "screen.primordial.output"), b -> {
                    selZone = selZone == 0 ? 1 : 0;
                    rebuildWidgets();
                }).bounds(leftPos + 214, topPos + 84, 96, 18).build());
        initResourceSamplePanel();
    }

    private void initResourceSamplePanel() {
        ResourceSampleConfig sample = menu.clientResourceSample(selZone == 0, selIndex);
        if (sample == null) return;
        addLabel(leftPos + 6, topPos + 95, resourceName(sample.unit));
        if (sample.mode == SampleMode.FIXED_RATE) {
            addLabel(leftPos + 6, topPos + 110, "screen.primordial.amount");
            sampleThresholdBox = addNumberBox(leftPos + 6, topPos + 126, 54, 1, v -> sendSampleEdit(2, v));
            addLabel(leftPos + 66, topPos + 110, "screen.primordial.interval_ticks");
            sampleIntervalBox = addNumberBox(leftPos + 66, topPos + 126, 54, 1, v -> sendSampleEdit(3, v));
        } else {
            addLabel(leftPos + 6, topPos + 126, "screen.primordial.keep_unlimited");
        }
        sampleModeBtn = addRenderableWidget(Button.builder(modeText(sample.mode), b -> {
            int next = sample.mode.ordinal() ^ 1;
            sendSampleEdit(0, next);
            rebuildWidgets();
        }).bounds(leftPos + 140, topPos + 125, 60, 16).build());
        sampleEnabledBtn = addRenderableWidget(Button.builder(enabledText(sample.enabled), b -> {
            sendSampleEdit(4, sample.enabled ? 0 : 1);
            b.setMessage(enabledText(!sample.enabled));
        }).bounds(leftPos + 204, topPos + 125, 46, 16).build());
        targetButton = addRenderableWidget(Button.builder(targetPicker.targetText(sample.effectiveTargets()),
                b -> targetPicker.open(selZone == 0 ? 6 : 7, selIndex, sample.effectiveTargets()))
                .bounds(leftPos + 182, topPos + 150, 128, 18).build());
        targetButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.primordial.select_target")));
        unbindButton = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.unbind"), b ->
                PrimordialNetwork.CHANNEL.sendToServer(new com.primordial.network.C2SMachineTargetPacket(
                        menu.containerId, selZone == 0 ? 6 : 7, selIndex, 0, -1, true, "", false, false)))
                .bounds(leftPos + 182, topPos + 171, 128, 18).build());
        unbindButton.active = !sample.effectiveTargets().isEmpty();
        setSampleBoxes();
    }

    private static Component resourceName(net.minecraft.resources.ResourceLocation unit) {
        String path = unit == null ? "unknown" : unit.getPath();
        return Component.translatable("screen.primordial.resource." + path);
    }

    // ---------------- 能量页 ----------------

    private void initEnergyPanel() {
        EnergyConfig e = menu.clientEnergy();
        if (e == null) return;
        if (e.supplyMode == SampleMode.FIXED_RATE) {
            addLabel(leftPos + 6, topPos + 28, "screen.primordial.amount_fe");
            supplyTargetBox = addNumberBox(leftPos + 6, topPos + 44, 66, 1, v -> sendEnergyEdit(true, 2, v));
            addLabel(leftPos + 78, topPos + 28, "screen.primordial.interval_ticks");
            supplyIntervalBox = addNumberBox(leftPos + 78, topPos + 44, 54, 1, v -> sendEnergyEdit(true, 3, v));
        } else addLabel(leftPos + 6, topPos + 45, "screen.primordial.keep_unlimited");
        addRenderableWidget(energyModeBtn(true, leftPos + 176, topPos + 42));
        addRenderableWidget(energyEnabledBtn(true, leftPos + 240, topPos + 42));
        if (e.recoverMode == SampleMode.FIXED_RATE) {
            addLabel(leftPos + 6, topPos + 82, "screen.primordial.amount_fe");
            recoverTargetBox = addNumberBox(leftPos + 6, topPos + 98, 66, 1, v -> sendEnergyEdit(false, 2, v));
            addLabel(leftPos + 78, topPos + 82, "screen.primordial.interval_ticks");
            recoverIntervalBox = addNumberBox(leftPos + 78, topPos + 98, 54, 1, v -> sendEnergyEdit(false, 3, v));
        } else addLabel(leftPos + 6, topPos + 99, "screen.primordial.keep_unlimited");
        addRenderableWidget(energyModeBtn(false, leftPos + 176, topPos + 96));
        addRenderableWidget(energyEnabledBtn(false, leftPos + 240, topPos + 96));
        setEnergyBoxes(e);
        supplyPortButton = addRenderableWidget(Button.builder(targetPicker.targetText(e.supplyPort),
                b -> targetPicker.open(4, 0, menu.clientEnergy().supplyPort)).bounds(leftPos + 182, topPos + 144, 128, 18).build());
        recoverPortButton = addRenderableWidget(Button.builder(targetPicker.targetText(e.recoverPort),
                b -> targetPicker.open(5, 0, menu.clientEnergy().recoverPort)).bounds(leftPos + 182, topPos + 184, 128, 18).build());
        addLabel(leftPos + 182, topPos + 133, "screen.primordial.supply_port");
        addLabel(leftPos + 182, topPos + 173, "screen.primordial.recover_port");
    }

    private Button energyModeBtn(boolean supply, int x, int y) {
        EnergyConfig e = menu.clientEnergy();
        SampleMode mode = e == null ? SampleMode.FIXED_RATE : (supply ? e.supplyMode : e.recoverMode);
        return Button.builder(modeText(mode), b -> {
            EnergyConfig cur = menu.clientEnergy();
            if (cur == null) return;
            SampleMode m = supply ? cur.supplyMode : cur.recoverMode;
            int next = m.ordinal() ^ 1;
            sendEnergyEdit(supply, 0, next);
            b.setMessage(modeText(SampleMode.VALUES[next]));
        }).bounds(x, y, 56, 16).build();
    }

    private Button energyEnabledBtn(boolean supply, int x, int y) {
        EnergyConfig e = menu.clientEnergy();
        boolean enabled = e != null && (supply ? e.supplyEnabled : e.recoverEnabled);
        return Button.builder(enabledText(enabled), b -> {
            EnergyConfig cur = menu.clientEnergy();
            if (cur == null) return;
            boolean on = supply ? cur.supplyEnabled : cur.recoverEnabled;
            sendEnergyEdit(supply, 4, on ? 0 : 1);
            b.setMessage(enabledText(!on));
        }).bounds(x, y, 22, 16).build();
    }

    private void sendEnergyEdit(boolean supply, int field, int value) {
        PrimordialNetwork.CHANNEL.sendToServer(
                new C2SUpdateConfigPacket(menu.machineKey, supply ? 4 : 5, 0, field, value));
    }

    private void setEnergyBoxes(EnergyConfig e) {
        updatingUI = true;
        if (supplyTargetBox != null) supplyTargetBox.setValue(String.valueOf(e.supplyAmount));
        if (supplyIntervalBox != null) supplyIntervalBox.setValue(String.valueOf(e.supplyIntervalTicks));
        if (recoverTargetBox != null) recoverTargetBox.setValue(String.valueOf(e.recoverAmount));
        if (recoverIntervalBox != null) recoverIntervalBox.setValue(String.valueOf(e.recoverIntervalTicks));
        updatingUI = false;
    }

    private EditBox addNumberBox(int x, int y, int w, int min, java.util.function.IntConsumer send) {
        EditBox box = new EditBox(this.font, x, y, w, 14, CommonComponents.EMPTY);
        box.setMaxLength(10);
        box.setFilter(s -> s.matches("\\d*"));
        box.setTextColor(0xFFFFFF);
        box.setResponder(text -> {
            if (updatingUI) return;
            try {
                int v = Integer.parseInt(text);
                if (v >= min) send.accept(v);
            } catch (NumberFormatException ignored) {
            }
        });
        return addRenderableWidget(box);
    }

    private void addLabel(int x, int y, String key) {
        addLabel(x, y, Component.translatable(key));
    }

    /** 添加一个不接收鼠标事件的静态标签；独立资源名称已经是 Component，避免重复翻译。 */
    private void addLabel(int x, int y, Component text) {
        addRenderableWidget(new AbstractWidget(x, y, 0, 0, CommonComponents.EMPTY) {
            @Override
            protected void renderWidget(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
                gg.drawString(font, text, getX(), getY(), PrimordialGuiStyle.SECONDARY_TEXT, false);
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput out) {
            }
        });
    }

    private static Component modeText(SampleMode mode) {
        return Component.translatable(mode == SampleMode.FIXED_RATE
                ? "screen.primordial.mode_fixed" : "screen.primordial.mode_keep");
    }

    private static Component enabledText(boolean enabled) {
        return Component.translatable(enabled ? "screen.primordial.on" : "screen.primordial.off");
    }

    /** 服务端全量同步回包后刷新 UI 值 */
    public void onConfigSync(String machineKey) {
        if (!menu.machineKey.equals(machineKey) || menu.pickingTarget) return;
        EnergyConfig syncedEnergy = menu.clientEnergy();
        boolean energyLayoutChanged = menu.activeTab == 2 && syncedEnergy != null
                && ((supplyTargetBox != null) != (syncedEnergy.supplyMode == SampleMode.FIXED_RATE)
                || (recoverTargetBox != null) != (syncedEnergy.recoverMode == SampleMode.FIXED_RATE));
        boolean sampleLayoutChanged = hasSelection() && ((sampleThresholdBox != null)
                != (currentMode() == SampleMode.FIXED_RATE));
        if (energyLayoutChanged || sampleLayoutChanged || (hasSelection() && targetButton == null)) {
            rebuildWidgets();
            return;
        }
        if (menu.activeTab < 2) {
            int inWhich = menu.activeTab == 0 ? 0 : 2;
            int outWhich = menu.activeTab == 0 ? 1 : 3;
            if (masterInputBtn != null) masterInputBtn.setMessage(masterLabel(inWhich));
            if (masterOutputBtn != null) masterOutputBtn.setMessage(masterLabel(outWhich));
            if (hasSelection()) setSampleBoxes();
        }
        if (menu.activeTab == 2) {
            EnergyConfig e = menu.clientEnergy();
            if (e != null) setEnergyBoxes(e);
            if (e != null && supplyPortButton != null) supplyPortButton.setMessage(targetPicker.targetText(e.supplyPort));
            if (e != null && recoverPortButton != null) recoverPortButton.setMessage(targetPicker.targetText(e.recoverPort));
        }
        if (menu.activeTab == 3 && hasSelection()) {
            setSampleBoxes();
            if (resourceDirectionBtn != null) resourceDirectionBtn.setMessage(Component.translatable(
                    selZone == 0 ? "screen.primordial.input" : "screen.primordial.output"));
        }
    }

    // ---------------- 交互 ----------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.pickingTarget) return super.mouseClicked(mouseX, mouseY, button);
        handleSlotSelect(mouseX, mouseY);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (menu.pickingTarget && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closeTargetPicker();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void handleSlotSelect(double mouseX, double mouseY) {
        for (int i = 0; i < ConfiguratorMenu.SAMPLE_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.isActive()) continue;
            double sx = leftPos + slot.x;
            double sy = topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                int idx = i % 9;
                int zone = (i % 18 < 9) ? 0 : 1;
                if (selZone != zone || selIndex != idx) {
                    selZone = zone;
                    selIndex = idx;
                    rebuildWidgets();
                }
                return;
            }
        }
    }

    // ---------------- 渲染 ----------------

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        renderBackground(gg);
        super.render(gg, mouseX, mouseY, partialTick);
        if (menu.pickingTarget) {
            targetPicker.render(gg, font, mouseX, mouseY);
            return;
        }
        // 无桶流体和气体直接使用过滤数据绘制，不能把空的显示 ItemStack 当作未保存。
        Component chemicalTooltip = null;
        if (menu.activeTab == 1) {
            for (int i = 0; i < 18; i++) {
                var sample = menu.clientFluidSample(i);
                if (sample == null) continue;
                int slotIndex = ConfiguratorMenu.FLUID_IN_START + i;
                Slot slot = menu.slots.get(slotIndex);
                com.primordial.client.FluidSampleRenderer.render(gg, sample, leftPos + slot.x, topPos + slot.y);
                if (mouseX >= leftPos + slot.x && mouseX < leftPos + slot.x + 16
                        && mouseY >= topPos + slot.y && mouseY < topPos + slot.y + 16
                        && (sample.hasGasFilter() || !sample.filter.isEmpty())) {
                    chemicalTooltip = com.primordial.client.FluidSampleRenderer.name(sample);
                }
            }
        }
        if (chemicalTooltip != null) gg.renderTooltip(font, chemicalTooltip, mouseX, mouseY);
        else renderTooltip(gg, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        gg.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PrimordialGuiStyle.SCREEN_BACKGROUND);
        gg.renderOutline(leftPos, topPos, imageWidth, imageHeight, PANEL_BORDER);
        if (menu.pickingTarget) return;

        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            gg.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
            gg.fill(x, y, x + 16, y + 16, SLOT_BG);
        }

        if (selZone >= 0 && menu.activeTab < 2) {
            int slotIdx;
            if (menu.activeTab == 0) {
                slotIdx = selZone == 0 ? selIndex : selIndex + 9;
            } else {
                slotIdx = selZone == 0 ? selIndex + 18 : selIndex + 27;
            }
            Slot slot = menu.slots.get(slotIdx);
            if (slot.isActive()) {
                int x = leftPos + slot.x;
                int y = topPos + slot.y;
                gg.fill(x - 1, y - 1, x + 17, y, SEL_COLOR);
                gg.fill(x - 1, y + 16, x + 17, y + 17, SEL_COLOR);
                gg.fill(x - 1, y - 1, x, y + 17, SEL_COLOR);
                gg.fill(x + 16, y - 1, x + 17, y + 17, SEL_COLOR);
            }
        }

        if (menu.activeTab < 2) {
            if (selZone >= 0) {
                panel(gg, leftPos + 2, topPos + 90, imageWidth - 4, 36);
            }
        } else if (menu.activeTab == 2) {
            panel(gg, leftPos + 2, topPos + 24, imageWidth - 4, 44);
            panel(gg, leftPos + 2, topPos + 72, imageWidth - 4, 44);
        } else {
            // 资源页上方是六个单位选择按钮，下方是当前方向的参数和端口绑定面板。
            // 资源页没有玩家背包，面板向下延伸容纳标题和两行说明，避免说明文字压住操作按钮。
            panel(gg, leftPos + 2, topPos + 88, imageWidth - 4, 156);
            int col = Math.max(0, selIndex) % 3;
            int row = Math.max(0, selIndex) / 3;
            int x = leftPos + 6 + col * 104;
            int y = topPos + 29 + row * 27;
            gg.renderOutline(x - 1, y - 1, 102, 24, SEL_COLOR);
        }
    }

    private void panel(GuiGraphics gg, int x, int y, int w, int h) {
        gg.fill(x, y, x + w, y + h, PANEL_BG);
        gg.renderOutline(x, y, w, h, PANEL_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
        if (menu.pickingTarget) return;
        if (menu.activeTab < 2) {
            gg.drawString(font, Component.translatable("screen.primordial.input"),
                    8, 27, PrimordialGuiStyle.PRIMARY_TEXT, false);
            gg.drawString(font, Component.translatable("screen.primordial.output"),
                    8, 63, 0xFFFFD37F, false);
            if (selZone < 0) {
                gg.drawString(font, Component.translatable("screen.primordial.select_hint"),
                        8, 100, 0xFF6688AA, false);
            }
        } else if (menu.activeTab == 2) {
            gg.drawString(font, Component.translatable("screen.primordial.machine_energy",
                menu.machineEnergyFE, menu.machineEnergyMax), 8, 120, PrimordialGuiStyle.PRIMARY_TEXT, false);
        } else {
            gg.drawString(font, Component.translatable("screen.primordial.resource_title"),
                    8, 204, PrimordialGuiStyle.PRIMARY_TEXT, false);
            // 说明区域限定在左侧 176 像素，右侧不与端口按钮产生视觉竞争，并自动按字体换行。
            gg.drawWordWrap(font, Component.translatable("screen.primordial.resource_hint"),
                    8, 218, 176, PrimordialGuiStyle.SECONDARY_TEXT);
        }
    }
}

