/**
 * 【变更记录 #6】2026-09-16（介质预览切换修正）
 * - 删除：连续选择不同流体或气体时仍显示上一次样本纹理的缓存路径。
 * - 新增：按当前注册名比较预览状态，介质改变时重新构造只读 FluidSampleConfig。
 * - 修改：不改变价格输入、网络校验或样本保存协议，仅修正客户端视觉反馈。
 * - 用途：从背包或 JEI 连续选择不同介质时，价格编辑槽始终显示当前目标。
 */
/**
 * 【变更记录 #5】2026-09-16（流体、气体价格编辑与 JEI 拖放）
 * - 删除：价格编辑页只允许输入物品整数价格、样本槽不能接受 JEI 流体/气体的限制。
 * - 新增：JEI Ghost Ingredient 目标、流体/气体预览、按介质切换输入格式，以及向服务端提交介质价格的网络包。
 * - 修改：物品仍使用原有 BigInteger 价格协议；流体编辑框按 EMC/mB 输入，气体编辑框按整数 EMC/mB 输入。
 * - 用途：管理员可以从 JEI 直接拖入无桶流体或 Mekanism 气体并修改单价，普通玩家仍只能查看价格页。
 */
/**
 * 【文件职责】绘制自定义价格编辑界面并发送价格更新请求。
 * 【变更记录 #1】2026-09-14 12:45:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：物品幽灵槽、EMC 数值输入框和设置按钮。
 * - 修改：该界面仅由配置器潜行右键打开，机器配置页不再渲染价格输入。
 * - 用途：让玩家通过空气右键集中管理价值表，输入非法数值时保持界面不关闭。
 */
/**
 * 【变更记录 #2】2026-09-15 12:04:51（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：IPNIgnore 和 MouseTweaksIgnore 官方注解。
 * - 修改：仅让整理与鼠标搬运扩展跳过此价格编辑界面。
 * - 用途：配合 ConfiguratorSortingCompat 隐藏整理按钮，避免幽灵样本受到批量搬运影响。
 */
/**
 * 【变更记录 #3】2026-09-15 15:07:10（第 3 次备注 · 1.0.0-emc-service）
 * - 删除：double 解析和 20 字符输入上限。
 * - 新增：最多 4096 位整数输入、菜单 ID 随请求发送及管理员提示。
 * - 修改：价格文本直接转 BigInteger，空文本不发送，匹配服务器校验和协议 6。
 * - 用途：管理员可以设置超 long 物品价格，客户端界面不因浮点舍入提交错误 EMC。
 */
/**
 * 【变更记录 #4】2026-09-15 15:25:00（第 4 次备注 · 1.0.0-stage9-gui-style）
 * - 删除：本界面独立维护的背景、边框和警告颜色字面量。
 * - 新增：统一引用 PrimordialGuiStyle 调色板。
 * - 修改：只替换渲染颜色来源，保留管理员校验、幽灵槽和价格网络包行为。
 * - 用途：让价格编辑与配置器、玩家背包界面保持同一 16×16 槽位视觉风格。
 */
/**
 * 【变更记录 #7】2026-09-16 01:24:55（第 7 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：提交时统一要求非空 ItemStack 和 BigInteger，导致流体/气体请求提前返回、流体小数被拒绝的逻辑。
 * - 新增：按目标类型校验并发送价格、服务器当前报价、保存中/成功/失败反馈和输入长度约束。
 * - 修改：切换注册名会清除旧输入；返回报价仅在未编辑或完成保存时填入，权限与保存状态由服务器确认。
 * - 用途：JEI 与背包取样均能提交真实价格；界面为反馈换行预留空间，避免无响应和旧样本价格误提交。
 */
/**
 * 【变更记录 #8】2026-09-16 01:52:17（第 8 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：物品可提交超 long 独立价格及“两套价表”的界面语义。
 * - 新增：ProjectE 单件 long 价格校验和明确上限提示；气体 4096 位与流体分数输入保持。
 * - 修改：物品基础价格直接应用到 ProjectE，保存回显代表原生重算完成；玩家余额仍不受 long 限制。
 * - 用途：配置器作为 ProjectE 扩展编辑同一价表，不能默默截断大数或只改变附属价格。
 */
/**
 * 【变更记录 #9】2026-10-02 15:19:00（第 9 次备注 · 1.2.0-fluid-price-unit-tests）
 * - 删除：将流体输入描述为 EMC/mB 和乘 1000 持久化的旧注释。
 * - 新增：明确价格页与价表均按 EMC/1000mB 处理流体报价。
 * - 修改：仅修正文档，不改输入校验、网络提交或每 mB 结算逻辑。
 * - 用途：防止后续维护时重复乘以 1000，造成流体价格偏差。
 */
/** 【变更记录 #10】2026-10-08 03:32:46（1.2.0-media-price-modes）
 * 删除：无。新增：价格状态文本输入。修改：流体/气体不再把 inherit、disabled 等状态误判为数字。用途：管理员可从价格编辑页直接设置交易方向。 */
/**
 * 【变更记录 #11】2026-10-10 02:33:01（第 11 次备注 · 1.2.0-item-price-inherit）
 * - 删除：物品输入框仅接收数字、界面无法撤销人工价覆盖的限制。
 * - 新增：物品输入 inherit 时发送专用恢复默认请求；普通数字仍走原 ProjectE 保存协议。
 * - 修改：输入校验允许价格模式文本，物品分支只识别 inherit 或非负整数，非法文本不发包。
 * - 用途：管理员可在同一编辑器恢复 ProjectE 默认物品价，界面显示仍由服务端保存回包确认。
 */
/**
 * 【变更记录 #12】2026-10-10 03:15:39（第 12 次备注 · 1.2.0-price-mode-controls）
 * - 删除：当前状态直接渲染英文枚举并挤占价格行的界面行为。
 * - 新增：默认、禁用、仅买、仅卖按钮与分类型说明；模式独立显示为本地化文本，完整价格可悬停查看。
 * - 修改：物品默认按钮提交 inherit，禁用按钮提交零价；仅买/仅卖仅供流体和气体使用。提交共用现有权限、等待状态和服务端保存回包。
 * - 用途：管理员不必手输英文模式即可区分恢复默认与禁用；无样本、无权限或保存中时停用操作，菜单区域与 PriceEditorMenu 同步扩大 24 像素。
 */
/**
 * 【变更记录 #13】2026-10-10 03:21:28（第 13 次备注 · 1.2.0-price-mode-controls）
 * - 删除：新增按钮说明在每帧同步时重复创建 Tooltip 的路径。
 * - 新增：按选择类型配置按钮布局和说明的独立方法。
 * - 修改：类型/样本切换时才刷新说明和布局；每帧仅更新按钮可操作状态，保留待确认与权限控制。
 * - 用途：避免重建悬停说明缓存，保证定价器新增按钮的渲染开销保持有界。
 */
/**
 * 【变更记录 #14】2026-10-10 03:25:41（第 14 次备注 · 1.2.0-media-custom-restore）
 * - 删除：流体/气体切换默认或禁用后只能重输价格才能恢复人工双向交易的入口限制。
 * - 新增：介质自定模式按钮，提交 CUSTOM_VALUE 恢复已有人工单价并允许双向交易；物品不提供该模式按钮。
 * - 修改：介质五按钮使用 30 像素宽度、32 像素步距；物品两按钮维持宽按钮布局。按钮不读取输入框的零价或未保存值。
 * - 用途：完整提供五种介质状态的可视入口，避免禁用回显零价覆盖原人工价格；保存确认、审计、阶段与黑名单校验沿用服务端。
 */
package com.primordial.screen;

import com.primordial.menu.PriceEditorMenu;
import com.primordial.network.C2SPlayerActionPacket;
import com.primordial.network.C2SSetPriceTargetPacket;
import com.primordial.network.C2SSetMediaPricePacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CPriceEditorStatePacket;
import com.primordial.network.S2CPriceEditorStatePacket.Status;
import com.primordial.service.EmcAmount;
import com.primordial.value.ValueTable;
import com.primordial.value.PriceMode;
import com.primordial.data.FluidSampleConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.entity.player.Inventory;

@org.anti_ad.mc.ipn.api.IPNIgnore
@yalter.mousetweaks.api.MouseTweaksIgnore
public class PriceEditorScreen extends AbstractContainerScreen<PriceEditorMenu> {
    private EditBox value;
    private Button save;
    private final java.util.Map<PriceMode, Button> modeButtons = new java.util.EnumMap<>(PriceMode.class);
    private byte lastKind = -1;
    private ResourceLocation lastId;
    private S2CPriceEditorStatePacket lastState;
    private Component localStatus;
    private boolean inputEdited;
    private boolean pending;
    private FluidSampleConfig mediaPreview;
    public PriceEditorScreen(PriceEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 176; imageHeight = PriceEditorMenu.GUI_HEIGHT;
    }
    @Override protected void init() {
        super.init();
        value = new EditBox(font, leftPos + 8, topPos + 73, 100, 16, Component.translatable("screen.primordial.custom_price"));
        value.setMaxLength(ValueTable.MAX_DIGITS);
        value.setFilter(this::acceptPriceCharacters);
        value.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("screen.primordial.price_input_help")));
        value.setResponder(text -> inputEdited = true);
        addRenderableWidget(value);
        save = addRenderableWidget(Button.builder(Component.translatable("screen.primordial.set"), b -> submit())
                .bounds(leftPos + 112, topPos + 72, 56, 18).build());
        initModeButtons();
        lastKind = -1;
        lastState = null;
        pending = false;
        syncSelectionUi();
    }

    private boolean acceptPriceCharacters(String text) {
        return text.matches("[a-zA-Z0-9_./-]*");
    }

    private void initModeButtons() {
        modeButtons.clear();
        PriceMode[] modes = {PriceMode.INHERIT_NATIVE, PriceMode.CUSTOM_VALUE, PriceMode.DISABLED, PriceMode.BUY_ONLY, PriceMode.SELL_ONLY};
        for (int i = 0; i < modes.length; i++) {
            PriceMode mode = modes[i];
            String key = "screen.primordial.price_action_" + mode.name().toLowerCase(java.util.Locale.ROOT);
            Button button = addRenderableWidget(Button.builder(Component.translatable(key), b -> submitMode(mode))
                    .bounds(leftPos + 8 + i * 32, topPos + 94, 30, 18).build());
            modeButtons.put(mode, button);
        }
    }

    /** 模式按钮不改写未保存的输入文本；物品禁用沿用 ProjectE 零价，介质禁用保留人工单价。 */
    private void submitMode(PriceMode mode) {
        if (menu.selectedKind() == PriceEditorMenu.KIND_ITEM
                && mode != PriceMode.INHERIT_NATIVE && mode != PriceMode.DISABLED) return;
        submit(menu.selectedKind() == PriceEditorMenu.KIND_ITEM && mode == PriceMode.DISABLED
                ? "0" : mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void configureModeButtons(byte kind) {
        boolean media = kind != PriceEditorMenu.KIND_ITEM;
        for (var entry : modeButtons.entrySet()) {
            PriceMode mode = entry.getKey();
            Button button = entry.getValue();
            button.visible = media || mode == PriceMode.INHERIT_NATIVE || mode == PriceMode.DISABLED;
            if (mode == PriceMode.INHERIT_NATIVE || mode == PriceMode.DISABLED) {
                button.setWidth(media ? 30 : 78);
                button.setX(leftPos + (mode == PriceMode.INHERIT_NATIVE ? 8 : media ? 72 : 90));
            }
            String tooltip = "screen.primordial.price_action_" + mode.name().toLowerCase(java.util.Locale.ROOT)
                    + (media ? "_media" : "_item") + "_help";
            if (button.visible) button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(tooltip)));
        }
    }

    /** JEI 目标区域固定在价格样本槽，物品、流体和气体共用该区域。 */
    public Rect2i jeiPriceArea() {
        return new Rect2i(leftPos + 80, topPos + 25, 16, 16);
    }

    /** JEI 投放只更新本地幽灵预览，同时发送注册名让服务端菜单建立同一选择。 */
    public void acceptJeiIngredient(Object ingredient) {
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            menu.setItemSelection(stack);
            mediaPreview = null;
            sendTarget(PriceEditorMenu.KIND_ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()));
        } else if (ingredient instanceof FluidStack fluid && !fluid.isEmpty()) {
            menu.setFluidSelection(fluid);
            mediaPreview = new FluidSampleConfig();
            mediaPreview.filter = fluid.copy();
            sendTarget(PriceEditorMenu.KIND_FLUID, BuiltInRegistries.FLUID.getKey(fluid.getFluid()));
        } else if (com.primordial.compat.MekanismGasCompat.isGas(ingredient)) {
            var id = com.primordial.compat.MekanismGasCompat.gasId(ingredient);
            if (id == null) return;
            menu.setGasSelection(id);
            mediaPreview = new FluidSampleConfig();
            mediaPreview.gasFilter = com.primordial.compat.MekanismGasCompat.copyGas(ingredient, 1);
            sendTarget(PriceEditorMenu.KIND_GAS, id);
        }
        syncSelectionUi();
    }

    private void sendTarget(byte kind, net.minecraft.resources.ResourceLocation id) {
        if (id == null) return;
        PrimordialNetwork.CHANNEL.sendToServer(new C2SSetPriceTargetPacket(menu.containerId, kind, id.toString()));
    }

    private void syncSelectionUi() {
        if (value == null) return;
        byte kind = menu.selectedKind();
        if (kind != lastKind || !java.util.Objects.equals(lastId, menu.selectedId())) {
            lastKind = kind;
            lastId = menu.selectedId();
            value.setFilter(this::acceptPriceCharacters);
            value.setMaxLength(kind == PriceEditorMenu.KIND_FLUID
                    ? S2CPriceEditorStatePacket.MAX_PRICE_TEXT : ValueTable.MAX_DIGITS);
            value.setValue("");
            String help = kind == PriceEditorMenu.KIND_FLUID ? "price_input_help_fluid"
                    : kind == PriceEditorMenu.KIND_GAS ? "price_input_help_gas" : "price_input_help";
            value.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("screen.primordial." + help)));
            configureModeButtons(kind);
            inputEdited = false;
            pending = false;
            localStatus = null;
            lastState = null;
        }
        S2CPriceEditorStatePacket state = menu.priceState();
        if (state != null && state != lastState) {
            lastState = state;
            if (!inputEdited || state.status() == Status.SAVED) {
                value.setValue(state.price());
                inputEdited = false;
            }
            if (state.status() != Status.CURRENT || !pending) {
                pending = false;
                localStatus = null;
            }
        }
        if (save != null) save.active = menu.selectedId() != null && state != null && state.editable() && !pending;
        for (var entry : modeButtons.entrySet()) {
            PriceMode mode = entry.getKey();
            Button button = entry.getValue();
            button.active = button.visible && menu.selectedId() != null && state != null && state.editable() && !pending
                    && !mode.name().equals(state.mode());
        }
        value.setEditable(state != null && state.editable() && !pending);
        if (kind == PriceEditorMenu.KIND_FLUID) {
            FluidStack fluid = menu.selectedFluid();
            boolean changed = mediaPreview == null || mediaPreview.filter.isEmpty()
                    || mediaPreview.filter.getFluid() != fluid.getFluid();
            if (!fluid.isEmpty() && changed) {
                mediaPreview = new FluidSampleConfig();
                mediaPreview.filter = fluid;
            }
        } else if (kind == PriceEditorMenu.KIND_GAS && menu.selectedId() != null) {
            boolean changed = mediaPreview == null || mediaPreview.gasFilter == null
                    || !menu.selectedId().equals(com.primordial.compat.MekanismGasCompat.gasId(mediaPreview.gasFilter));
            Object stack = changed ? com.primordial.compat.MekanismGasCompat.preview(menu.selectedId()) : null;
            if (changed && com.primordial.compat.MekanismGasCompat.isGas(stack)) {
                mediaPreview = new FluidSampleConfig();
                mediaPreview.gasFilter = stack;
            }
        } else if (kind == PriceEditorMenu.KIND_ITEM) {
            mediaPreview = null;
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncSelectionUi();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.selectedKind() != PriceEditorMenu.KIND_ITEM
                && isHovering(80, 25, 16, 16, mouseX, mouseY) && menu.selectedId() != null)
            graphics.renderTooltip(font, Component.literal(menu.selectedId().toString()), mouseX, mouseY);
        if (menu.priceState() != null && isHovering(8, 47, imageWidth - 16, 9, mouseX, mouseY))
            graphics.renderTooltip(font, Component.translatable("screen.primordial.price_current", menu.priceState().price()), mouseX, mouseY);
    }

    /** 物品/气体按非负整数提交，流体按精确 EMC/1000mB 提交。 */
    private void submit() {
        submit(value.getValue());
    }

    private void submit(String text) {
        if (pending) return;
        ResourceLocation id = menu.selectedId();
        if (id == null) {
            localStatus = Component.translatable("screen.primordial.price_select");
            return;
        }
        S2CPriceEditorStatePacket state = menu.priceState();
        if (state == null || !state.editable()) {
            localStatus = Component.translatable(state == null
                    ? "screen.primordial.price_loading" : "screen.primordial.price_denied");
            return;
        }
        try {
            switch (menu.selectedKind()) {
                case PriceEditorMenu.KIND_ITEM -> {
                    if ("inherit".equalsIgnoreCase(text) || "inherit_native".equalsIgnoreCase(text)) {
                        PrimordialNetwork.CHANNEL.sendToServer(new C2SSetMediaPricePacket(
                                menu.containerId, menu.selectedKind(), id.toString(), "inherit"));
                    } else {
                        var amount = ValueTable.parseInteger(text);
                        if (!com.primordial.compat.ProjectEItemPrices.validItemPrice(amount))
                            throw new IllegalArgumentException("ProjectE item EMC exceeds long");
                        PrimordialNetwork.CHANNEL.sendToServer(new C2SPlayerActionPacket(id.toString(), amount, menu.containerId));
                    }
                }
                case PriceEditorMenu.KIND_FLUID -> {
                    // 报价文本必须可精确重读，避免超出价表可表示范围。
                    if (!isPriceMode(text)) EmcAmount.parse(text);
                    PrimordialNetwork.CHANNEL.sendToServer(new C2SSetMediaPricePacket(
                            menu.containerId, menu.selectedKind(), id.toString(), text));
                }
                case PriceEditorMenu.KIND_GAS -> {
                    if (!isPriceMode(text)) text = ValueTable.parseInteger(text).toString();
                    PrimordialNetwork.CHANNEL.sendToServer(new C2SSetMediaPricePacket(
                            menu.containerId, menu.selectedKind(), id.toString(), text));
                }
                default -> throw new IllegalArgumentException("Invalid price kind");
            }
            pending = true;
            localStatus = Component.translatable("screen.primordial.price_saving");
        } catch (IllegalArgumentException invalid) {
            localStatus = Component.translatable(menu.selectedKind() == PriceEditorMenu.KIND_FLUID
                    ? "screen.primordial.price_invalid_fluid" : menu.selectedKind() == PriceEditorMenu.KIND_ITEM
                    ? "screen.primordial.price_invalid_item" : "screen.primordial.price_invalid_integer");
        }
    }
    private static boolean isPriceMode(String text) {
        try { com.primordial.value.PriceMode.parse(text); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    @Override protected void renderBg(GuiGraphics gg, float partialTick, int mouseX, int mouseY) {
        gg.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PrimordialGuiStyle.SCREEN_BACKGROUND);
        gg.renderOutline(leftPos, topPos, imageWidth, imageHeight, PrimordialGuiStyle.PANEL_BORDER);
        for (var slot : menu.slots) { int x = leftPos + slot.x, y = topPos + slot.y; gg.fill(x - 1, y - 1, x + 17, y + 17, PrimordialGuiStyle.SLOT_BORDER); }
        if (mediaPreview != null) {
            com.primordial.client.FluidSampleRenderer.render(gg, mediaPreview, leftPos + 80, topPos + 25);
        } else if (menu.selectedKind() == PriceEditorMenu.KIND_GAS && menu.selectedId() != null) {
            gg.drawString(font, Component.literal("GAS"), leftPos + 81, topPos + 29,
                    PrimordialGuiStyle.PRIMARY_TEXT, false);
        }
    }
    @Override protected void renderLabels(GuiGraphics gg, int mouseX, int mouseY) {
        gg.drawString(font, Component.translatable("screen.primordial.custom_price"), 8, 7, PrimordialGuiStyle.PRIMARY_TEXT, false);
        String key = switch (menu.selectedKind()) {
            case PriceEditorMenu.KIND_FLUID -> "screen.primordial.price_fluid";
            case PriceEditorMenu.KIND_GAS -> "screen.primordial.price_gas";
            default -> "screen.primordial.price_item";
        };
        gg.drawString(font, Component.translatable(key), 8, 23, PrimordialGuiStyle.SECONDARY_TEXT, false);
        S2CPriceEditorStatePacket state = menu.priceState();
        String current = state == null || state.price().isEmpty() ? "—" : state.price();
        String label = Component.translatable("screen.primordial.price_current", current).getString();
        if (font.width(label) > imageWidth - 16)
            label = font.plainSubstrByWidth(label, imageWidth - 25) + "...";
        gg.drawString(font, label, 8, 47, PrimordialGuiStyle.SECONDARY_TEXT, false);
        String modeKey = "unknown";
        if (state != null && isPriceMode(state.mode()))
            modeKey = PriceMode.parse(state.mode()).name().toLowerCase(java.util.Locale.ROOT);
        gg.drawString(font, Component.translatable("screen.primordial.price_mode",
                Component.translatable("screen.primordial.price_mode_" + modeKey)),
                8, 59, PrimordialGuiStyle.SECONDARY_TEXT, false);
        Component feedback = localStatus;
        if (feedback == null) {
            String feedbackKey;
            if (state == null) feedbackKey = menu.selectedId() == null ? "price_select" : "price_loading";
            else if (!state.editable()) feedbackKey = "price_denied";
            else feedbackKey = switch (state.status()) {
                case SAVED -> "price_saved";
                case PERMISSION_DENIED -> "price_denied";
                case INVALID_TARGET -> "price_invalid_target";
                case INVALID_PRICE -> menu.selectedKind() == PriceEditorMenu.KIND_FLUID
                        ? "price_invalid_fluid" : menu.selectedKind() == PriceEditorMenu.KIND_ITEM
                        ? "price_invalid_item" : "price_invalid_integer";
                case SAVE_FAILED -> "price_failed";
                default -> menu.selectedId() == null ? "price_select"
                        : menu.selectedKind() == PriceEditorMenu.KIND_ITEM ? "price_scope" : "price_scope_media";
            };
            feedback = Component.translatable("screen.primordial." + feedbackKey);
        }
        gg.drawWordWrap(font, feedback, 8, 118, imageWidth - 16, PrimordialGuiStyle.SECONDARY_TEXT);
    }
}
