/**
 * 【文件职责】维护转化桌介质目录、选择、分页与菜单绑定的交易反馈。
 * 【变更记录 #1】2026-10-10 03:58:22（第 1 次备注 · 1.2.0-resource-system-one）
 * - 删除：无；原 CatalogView 行为从 TransmutationMediaButton 拆出。
 * - 新增：真实成交/失败结果、提交中按钮停用和背包不足提示。
 * - 修改：遗忘按钮缩为 65 像素，余下底栏用于简短结果，悬停显示完整金额和资源 ID。
 * - 用途：让第 1 系统可整体验收，控制文件长度；客户端反馈不替代服务端授权。
 */
/**
 * 【变更记录 #2】2026-10-10 03:58:22（第 2 次备注 · 1.2.0-resource-system-one）
 * - 删除：目录只渲染流体/气体、FE 单位没有图标的限制。
 * - 新增：FE 红石图标、能源说明和独立黑名单按钮检查。
 * - 修改：目录资源 forge:fe 通过已有选择按钮提交；反馈金额按 FE 单位显示。
 * - 用途：玩家在同一个资源页可以发现 FE，并沿用已有凭证知识及容器容量校验。
 */
/**
 * 【变更记录 #3】2026-10-10 04:03:51（第 3 次备注 · 1.2.0-resource-system-one）
 * - 删除：FE 选择可点击介质遗忘按钮、输入新搜索后旧目录仍接受的路径。
 * - 新增：FE 禁用逐种介质遗忘，结果回包与当前输入再次匹配。
 * - 修改：价格/知识仍以服务端为准；输入超过 64 字符按查询协议规范化比较。
 * - 用途：保持能源凭证与介质遗忘语义独立，避免旧搜索结果误导整体验收。
 */
/**
 * 【变更记录 #4】2026-10-12 14:17:29（第 4 次备注 · 1.2.0-transmutation-media-theme）
 * - 删除：无；保留原有交易状态、目录选择和分页反馈。
 * - 新增：无。
 * - 修改：介质页状态摘要文字从白色改为深紫灰，以适配 ProjectE 浅色转化桌背景并保持可读性。
 * - 用途：使交易结果与加载状态继续清晰显示在炼金主题面板上，不改变反馈内容或交易流程。
 */
/**
 * 【变更记录 #5】2026-10-12 14:35:41（第 5 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：下方 9×3 结果按钮、独立购买/卖入/遗忘/搜索按钮及矩形目录布局。
 * - 新增：依 ProjectE 原生 8 个充能槽、16 个知识槽、火焰卖入槽和遗忘槽处理鼠标操作；知识格点击直接提交对应介质购买。
 * - 修改：搜索框移动到原版顶部坐标，分页箭头移至原版箭头位置；目录单页固定 16 项，服务器结果仍按当前菜单、类别和查询校验。
 * - 用途：让流体/气体介质页复用等价交换槽位语义；FE 充能请求走服务端现有购买事务，实际接收量决定 EMC 扣款。
 */
/**
 * 【变更记录 #6】2026-10-12 14:52:00（第 6 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：重造的搜索框与翻页按钮、媒体页状态占位文本及 16 项分页。
 * - 新增：直接使用 ProjectE 原生搜索框和箭头控件；按原版每页 12 项显示知识目标，交易反馈改用原生操作栏消息。
 * - 修改：查询文本实时提交服务端目录；右键知识图标仅选中以供遗忘，左键购买；隐藏的 ProjectE 槽位仍由自定义处理器执行流体/气体/FE 事务。
 * - 用途：保留原版 GUI 搜索、分页、EMC 标签的外观与行为，减少自制控件造成的视觉偏差。
 */
/**
 * 【变更记录 #7】2026-10-12 15:03:11（第 7 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：自制搜索框/箭头及介质页交易状态占位；不再用完整菜单槽位处理 27 格列表。
 * - 新增：复用原版输入框和前后箭头，目录回包按 16 个知识槽显示且按 12 项步长翻页；流体、气体分类独立为 F/G 页签。
 * - 修改：搜索实时向服务端请求并重置页码；槽位右键仅选择知识，左键右侧槽购买，中心槽处理卖入/遗忘，左侧 8 槽购买 FE。
 * - 用途：复现 ProjectE 1.20.1 的控件行为和输出位布局；交易操作只发送受服务端校验的协议消息。
 */
/**
 * 【变更记录 #8】2026-10-12 15:07:00（第 8 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：无。
 * - 新增：无。
 * - 修改：切回物品页时将复用搜索框当前文本同步给 ProjectE TransmutationInventory.filter 并重置原版页码。
 * - 用途：避免介质页搜索后返回原版物品页时输入框和物品过滤结果不一致。
 */
/**
 * 【变更记录 #9】2026-10-12 23:38:49（第 9 次备注 · 1.2.0-media-real-slots）
 * - 删除：旧介质页不完整的交互路径。
 * - 新增：左侧与遗忘区域不再吞掉原生槽点击；右键清空搜索、停用箭头不处理点击、切回物品页恢复箭头。
 * - 修改：对照 ProjectE 真实槽位及原生控件处理。
 * - 用途：介质页状态、容器安全与显示一致，保留全部历史。
 */
/**
 * 【变更记录 #10】2026-10-13 01:53:18（第 10 次备注 · 1.2.0-media-feedback）
 * - 删除：字母页签、全部自定义介质槽说明与选择紫框；页签改为物品/流体/气体/合成，重绘知识槽原生背景。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
/**
 * 【变更记录 #11】2026-10-13 02:08:37（第 11 次备注 · 1.2.0-media-feedback-2）
 * - 删除：买入/卖入前的待处理 actionbar 消息，以及交易成功后展示单位、数量和 EMC、失败时展示状态的 actionbar 消息。
 * - 新增：鼠标悬停于有效流体或气体知识图标时，调用 FluidSampleRenderer.name 并使用 Minecraft 原生 renderTooltip 显示介质名称。
 * - 修改：交易结果回包只在卖出成功后清空搜索并刷新目录；买入结果和失败结果不弹出未请求的提示。页签与页面按钮仍不注册 tooltip。
 * - 用途：让玩家可辨认目录中的介质，同时保持交易界面安静；服务端交易、计费和目录刷新逻辑不变。
 */
/**
 * 【变更记录 #12】2026-10-13 02:18:07（第 12 次备注 · 1.2.0-media-tab-tooltips）
 * - 删除：无。
 * - 新增：物品、流体、气体和合成四个选页标签分别绑定已有中英文翻译提示；流体与气体页签共用“流体与气体页”说明。
 * - 修改：只恢复四个选页按钮的悬停提示；交易槽、介质知识格以外的页面控件仍不附加 tooltip，介质图标名称提示保持原生样式。
 * - 用途：让玩家悬停标签时识别目标页面，同时维持页面内交易操作无额外提示的要求；只影响客户端界面。
 */
package com.primordial.client;

import com.primordial.PrimordialMod;
import com.primordial.network.C2SSellCarriedMediaPacket;
import com.primordial.network.C2SBuyCarriedMediaPacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.network.C2SRequestMediaCatalogPacket;
import com.primordial.network.S2CMediaCatalogPacket;
import com.primordial.data.FluidSampleConfig;
import java.lang.ref.WeakReference;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

final class TransmutationMediaCatalogView {
    final moze_intel.projecte.gameObjs.gui.GUITransmutation screen;
    final EditBox queryBox;
    private final boolean[] gasMode;
    private static final int PAGE_SIZE = 16;
    private static final int PAGE_STRIDE = 12;
    private final String[] resultIds = new String[PAGE_SIZE];
    private final List<AbstractWidget> nativeWidgets;
    final int left;
    final int top;
    private final Button itemTab;
    private final Button mediaTab;
    private final Button craftingTab;
    private final Button gasTab;
    private final Button previous;
    private final Button next;
    private int page;
    private String requestedQuery = "";
    private boolean requestedGas;
    int activePage;
    String selectedId = "";
    private net.minecraft.world.item.ItemStack forgetSample = net.minecraft.world.item.ItemStack.EMPTY;

    TransmutationMediaCatalogView(moze_intel.projecte.gameObjs.gui.GUITransmutation screen, EditBox queryBox,
            boolean[] gasMode, List<AbstractWidget> nativeWidgets, int left, int top) {
        this.screen = screen;
        this.queryBox = queryBox;
        this.gasMode = gasMode;
        this.nativeWidgets = nativeWidgets;
        this.left = left;
        this.top = top;
        previous = nativeWidgets.stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(widget -> widget.getX() == left + 125 && widget.getY() == top + 100)
                .findFirst().orElseThrow(() -> new IllegalStateException("ProjectE previous page button not found"));
        next = nativeWidgets.stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(widget -> widget.getX() == left + 193 && widget.getY() == top + 100)
                .findFirst().orElseThrow(() -> new IllegalStateException("ProjectE next page button not found"));
        itemTab = Button.builder(Component.literal("物品"), ignored -> showPage(0))
                .pos(left - 28, top + 5).size(26, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.primordial.transmutation.item_tab"))).build();
        mediaTab = Button.builder(Component.literal("流体"), ignored -> showMediaMode(false))
                .pos(left - 28, top + 27).size(26, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.primordial.transmutation.media_tab"))).build();
        gasTab = Button.builder(Component.literal("气体"), ignored -> showMediaMode(true))
                .pos(left - 28, top + 49).size(26, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.primordial.transmutation.media_tab"))).build();
        craftingTab = Button.builder(Component.literal("合成"), ignored -> showPage(2))
                .pos(left - 28, top + 71).size(26, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("screen.primordial.transmutation.crafting_tab"))).build();
    }

    void add(ScreenEvent.Init.Post event) {
        event.addListener(itemTab);
        event.addListener(mediaTab);
        event.addListener(gasTab);
        event.addListener(craftingTab);
    }

    void showPage(int nextPage) {
        activePage = nextPage;
        clearSelection();
        screen.setFocused(null);
        for (AbstractWidget widget : nativeWidgets)
            widget.visible = nextPage == 0 || (nextPage == 1 && isNativeSearchOrPager(widget));
        itemTab.active = nextPage != 0;
        mediaTab.active = nextPage != 1 || gasMode[0];
        gasTab.active = nextPage != 1 || !gasMode[0];
        craftingTab.active = nextPage != 2;
        configureSearchResponder(nextPage == 1);
        ((com.primordial.service.MediaChargingMenu) screen.getMenu()).primordial$setMediaPage(nextPage == 1, gasMode[0]);
        PrimordialNetwork.CHANNEL.sendToServer(new com.primordial.network.C2SMediaPagePacket(
                screen.getMenu().containerId, nextPage == 1, gasMode[0]));
        if (nextPage == 0) { previous.active = true; next.active = true; }
        if (nextPage == 1) {
            previous.active = false;
            next.active = false;
        }
        if (nextPage == 1) request(0);
    }

    void showMediaMode(boolean gas) {
        gasMode[0] = gas;
        showPage(1);
    }

    boolean gasModeActive() { return gasMode[0]; }

    private boolean isNativeSearchOrPager(AbstractWidget widget) {
        if (widget == queryBox) return true;
        int x = widget.getX() - left, y = widget.getY() - top;
        return y == 100 && (x == 125 || x == 193);
    }

    private void configureSearchResponder(boolean media) {
        if (media) {
            queryBox.setResponder(ignored -> {
                clearSelection();
                request(0);
            });
        } else {
            queryBox.setResponder(value -> {
                var inventory = screen.getMenu().transmutationInventory;
                inventory.filter = value.toLowerCase(java.util.Locale.ROOT);
                inventory.searchpage = 0;
                inventory.updateClientTargets();
            });
            var inventory = screen.getMenu().transmutationInventory;
            inventory.filter = queryBox.getValue().toLowerCase(java.util.Locale.ROOT);
            inventory.searchpage = 0;
            inventory.updateClientTargets();
        }
    }

    void clickMediaWidget(double x, double y, int button) {
        if (queryBox.isMouseOver(x, y)) {
            if (button == 1) queryBox.setValue("");
            else queryBox.mouseClicked(x, y, button);
            screen.setFocused(queryBox);
            return;
        }
        if (button == 1) {
            int result = findSlot(x, y, 11, 16);
            if (result >= 0 && result < PAGE_SIZE && resultIds[result] != null && !resultIds[result].isEmpty())
                selectedId = resultIds[result];
            return;
        }
        if (button != 0) return;
        if (previous.active && isOverPageButton(x, y, 125)) {
            request(page - 1);
            return;
        }
        if (next.active && isOverPageButton(x, y, 193)) {
            request(page + 1);
            return;
        }
        if (x < left || x >= left + 228 || y < top || y >= top + 115) return;
        if (isOverSlot(x, y, 9)) {
            PrimordialNetwork.CHANNEL.sendToServer(new C2SSellCarriedMediaPacket(screen.getMenu().containerId));
            return;
        }
        int result = findSlot(x, y, 11, 16);
        if (result >= 0 && result < PAGE_SIZE) {
            String id = resultIds[result];
            if (id == null || id.isEmpty()) return;
            selectedId = id;
            PrimordialNetwork.CHANNEL.sendToServer(new C2SBuyCarriedMediaPacket(
                    screen.getMenu().containerId, gasMode[0], id));
        }
    }

    boolean nativeMediaSlot(double x, double y) {
        return findSlot(x, y, 73, 9) >= 0;
    }

    private boolean isOverPageButton(double mouseX, double mouseY, int x) {
        return mouseX >= left + x && mouseX < left + x + 14
                && mouseY >= top + 100 && mouseY < top + 114;
    }

    private int findSlot(double x, double y, int start, int count) {
        for (int i = 0; i < count; i++) if (isOverSlot(x, y, start + i)) return i;
        return -1;
    }

    private boolean isOverSlot(double x, double y, int index) {
        if (index < 0 || index >= screen.getMenu().slots.size()) return false;
        Slot slot = screen.getMenu().slots.get(index);
        return x >= left + slot.x && x < left + slot.x + 16
                && y >= top + slot.y && y < top + slot.y + 16;
    }

    void request(int targetPage) {
        if (targetPage < 0 || targetPage > 10000) return;
        clearSelection();
        java.util.Arrays.fill(resultIds, "");
        page = targetPage;
        requestedQuery = queryBox.getValue().trim();
        if (requestedQuery.length() > 64) requestedQuery = requestedQuery.substring(0, 64);
        requestedGas = gasMode[0];
        previous.active = false;
        next.active = false;
        PrimordialNetwork.CHANNEL.sendToServer(new C2SRequestMediaCatalogPacket(
                        screen.getMenu().containerId, requestedGas, requestedQuery, page));
    }

    private String normalizedQuery() {
        String query = queryBox.getValue().trim();
        return query.length() > 64 ? query.substring(0, 64) : query;
    }

    void clearSelection() {
        selectedId = "";
    }

    void accept(S2CMediaCatalogPacket packet) {
        if (activePage != 1 || Minecraft.getInstance().screen != screen
                || packet.containerId() != screen.getMenu().containerId
                || packet.gas() != requestedGas || packet.page() != page
                || !packet.query().equals(requestedQuery)
                || !packet.query().equals(normalizedQuery())) return;
        for (int i = 0; i < resultIds.length; i++) {
            String id = i < packet.ids().size() ? packet.ids().get(i) : "";
            resultIds[i] = id;
        }
        previous.active = page > 0;
        next.active = ((long) page + 1) * PAGE_STRIDE < packet.total();
    }

    void acceptTradeResult(com.primordial.network.S2CMediaTradeResultPacket packet) {
        if (Minecraft.getInstance().screen != screen || packet.containerId() != screen.getMenu().containerId) return;
        if (packet.result().traded() && !packet.buy()) {
            queryBox.setValue("");
            request(0);
        }
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activePage != 1) return;
        var currentForgetSample = screen.getMenu().getSlot(81).getItem();
        if (!net.minecraft.world.item.ItemStack.matches(currentForgetSample, forgetSample)) {
            forgetSample = currentForgetSample.copy();
            if (!currentForgetSample.isEmpty()) request(0);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 199);
        for (int i = 0; i < 8; i++) {
            Slot slot = screen.getMenu().slots.get(i);
            int x = slot.x - 1, y = slot.y - 1;
            var texture = new ResourceLocation("projecte", "textures/gui/transmute.png");
            graphics.blit(texture, left + x, top + y, x, y, 18, 1);
            graphics.blit(texture, left + x, top + y + 17, x, y + 17, 18, 1);
            graphics.blit(texture, left + x, top + y + 1, x, y + 1, 1, 16);
            graphics.blit(texture, left + x + 17, top + y + 1, x + 17, y + 1, 1, 16);
        }
        for (int i = 8; i < 27; i++) {
            if (i == 9 || i == 10) continue;
            Slot slot = screen.getMenu().slots.get(i);
            graphics.blit(new ResourceLocation("projecte", "textures/gui/transmute.png"),
                    left + slot.x - 1, top + slot.y - 1, slot.x - 1, slot.y - 1, 18, 18);
        }
        graphics.pose().popPose();
        for (int i = 0; i < resultIds.length; i++) {
            ResourceLocation id = ResourceLocation.tryParse(resultIds[i]);
            if (id == null) continue;
            Slot slot = screen.getMenu().slots.get(11 + i);
            int x = left + slot.x, y = top + slot.y;
            FluidSampleConfig sample = new FluidSampleConfig();
            if (requestedGas) sample.gasFilter = com.primordial.compat.MekanismGasCompat.preview(id);
            else if (BuiltInRegistries.FLUID.containsKey(id))
                sample.filter = new FluidStack(BuiltInRegistries.FLUID.get(id), 1000);
            FluidSampleRenderer.render(graphics, sample, x, y);
            Component name = FluidSampleRenderer.name(sample);
            if (!name.getString().isEmpty() && mouseX >= x && mouseX < x + 16
                    && mouseY >= y && mouseY < y + 16) {
                graphics.renderTooltip(Minecraft.getInstance().font, name, mouseX, mouseY);
            }
        }
    }
}
