/**
 * 【文件职责】在 ProjectE 原生转化桌旁提供光标容器介质卖入按钮。
 * 【变更记录 #1】2026-10-02 16:24:00（第 1 次备注 · 1.2.0-transmutation-media-input）
 * - 删除：无。
 * - 新增：菜单初始化时添加有提示的卖入按钮。
 * - 修改：点击只提交菜单 ID，服务端负责决定是否成交。
 * - 用途：不替换 ProjectE 界面即可提供流体与气体学习入口。
 */
/**
 * 【变更记录 #2】2026-10-02 16:50:00（第 2 次备注 · 1.2.0-transmutation-media-output）
 * - 删除：无。
 * - 新增：有提示的介质补充按钮。
 * - 修改：买入和卖入各自仅提交当前菜单 ID。
 * - 用途：在 ProjectE 原生界面内操作已选介质的买卖。
 */
/**
 * 【变更记录 #3】2026-10-02 17:05:00（第 3 次备注 · 1.2.0-transmutation-media-selection）
 * - 删除：无。
 * - 新增：注册名输入框与流体/气体切换按钮。
 * - 修改：输入框留空时向上箭头仍补充现有介质；有值时请求服务端购买所选种类。
 * - 用途：让空容器可购买已学习资源，同时保留原有快捷补充操作。
 */
/**
 * 【变更记录 #4】2026-10-02 17:08:00（第 4 次备注 · 1.2.0-transmutation-media-selection-ui）
 * - 删除：无。
 * - 新增：模式按钮的互斥选中状态。
 * - 修改：当前模式按钮显示为不可再次点击，切换后立即更新双方状态。
 * - 用途：让玩家在购买前明确知道资源 ID 将按流体还是气体解析。
 */
/**
 * 【变更记录 #5】2026-10-02 17:18:00（第 5 次备注 · 1.2.0-media-catalog）
 * - 删除：无。
 * - 新增：搜索按钮、五项临时结果和分页控件；回包只更新当前屏幕的匹配查询。
 * - 修改：选择目录结果后填入原有注册名框，再使用既有购买按钮。
 * - 用途：不用手记 ID 也能浏览和购买已学习介质；列表关闭时不占用转化桌槽位。
 */
/**
 * 【变更记录 #6】2026-10-02 17:40:00（第 6 次备注 · 1.2.0-media-icons）
 * - 删除：目录结果按钮中的注册名文字。
 * - 新增：流体和气体原生材质图标、悬停名称与打开及卖入后的目录刷新。
 * - 修改：五项结果改为横向图标格，点击仍选择对应资源供购买。
 * - 用途：已学习介质像普通物品一样以贴图呈现，注册名只保留在输入框内部供交易使用。
 */
/**
 * 【变更记录 #7】2026-10-02 18:00:13（第 7 次备注 · 1.2.0-media-tab）
 * - 删除：原版物品页上方和槽位中的介质控件布局。
 * - 新增：转化桌内独立介质页、9×3 图标目录和页签切换。
 * - 修改：隐藏原版控件并覆盖原版上半部，只在介质页绘制介质目录；背包保持可用。
 * - 用途：使物品与流体/气体交易分区，避免目录遮挡原版转化桌操作。
 */
/**
 * 【变更记录 #8】2026-10-02 18:12:34（第 8 次备注 · 1.2.0-media-tab-input）
 * - 删除：只在介质页空白处拦截点击的漏洞。
 * - 新增：介质页上半部点击统一转交专属控件，原版槽位完全收不到该区域的点击。
 * - 修改：叠层绘制高度提高，遮蔽原版物品悬停提示；切页清除旧焦点。
 * - 用途：让介质页成为实际可交互的独立页面，避免误选或误交易原版物品。
 */
/**
 * 【变更记录 #9】2026-10-02 18:20:45（第 9 次备注 · 1.2.0-media-page-isolation）
 * - 删除：渲染末尾的高层覆盖底板和重复控件绘制。
 * - 新增：页签状态查询、隐藏槽位判定、介质页背景和背包提示渲染入口。
 * - 修改：客户端 Mixin 在原版绘制和命中之前切换路径；保留玩家背包与光标物品。
 * - 用途：实际停止隐藏物品槽的显示、悬停和交互，切回物品页恢复原版行为。
 */
/**
 * 【变更记录 #10】2026-10-02 18:48:00（第 10 次备注 · 1.2.0-media-forgetting）
 * - 删除：底栏静态标题占位。
 * - 新增：底栏遗忘所选资源按钮及说明；仅选择图标后启用。
 * - 修改：提交遗忘后清除选择并按顺序请求第一页，切换类别或搜索时禁用遗忘按钮。
 * - 用途：不占用背包或原版物品页，服务端负责实际知识修改。
 */
/**
 * 【变更记录 #11】2026-10-02 18:54:32（第 11 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：根据 Forge 同步的服务端开关更新交易按钮提示及禁用状态。
 * - 修改：明确选种被禁止时禁用购买；自动识别容器操作仅双类别均关闭才整体禁用。
 * - 用途：知识目录与遗忘继续可用，并说明哪个类别的交易被服务器关闭。
 */
/**
 * 【变更记录 #12】2026-10-02 19:37:19（第 12 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：介质页初始化完成后安装独立 NBT 设置按钮。
 * - 修改：显式初始化顺序，NBT 按钮不归入原版控件显隐列表。
 * - 用途：切换物品/介质页不会错误展开或隐藏 NBT 设置选项。
 */
/**
 * 【变更记录 #13】2026-10-03 03:34:00（第 13 次备注 · 1.2.0-transmutation-crafting）
 * - 删除：两页专属的布尔页签假设。
 * - 新增：独立合成页和九格材料、结果槽的实际显隐隔离。
 * - 修改：介质页点击拦截只作用于介质页；合成页允许原版槽位交互。
 * - 用途：三页共享背包，合成材料不与 ProjectE 买卖槽或介质控件重叠交互。
 */
/**
 * 【变更记录 #14】2026-10-07 00:44:19（第 14 次备注 · 1.2.0-knowledge-crafting）
 * - 删除：无。
 * - 新增：JEI 请求配料后按菜单身份切换到合成页。
 * - 修改：复用现有 showPage 进行槽位隔离与按钮刷新。
 * - 用途：从任意转化页查看 JEI 配方后，返回时可看到合成网格。
 */
/**
 * 【变更记录 #15】2026-10-10 03:58:22（第 15 次备注 · 1.2.0-resource-system-one）
 * - 删除：主事件类内过长的目录视图实现及卖入后无条件刷新。
 * - 新增：独立 TransmutationMediaCatalogView 和菜单绑定的交易反馈入口。
 * - 修改：买卖提交后等待服务器结果；成功卖入才刷新知识目录，物品/合成页隔离沿用原接口。
 * - 用途：把第 1 系统目录与反馈合为可维护组件，玩家能完整审核真实成交与失败情况。
 */
/**
 * 【变更记录 #16】2026-10-12 14:17:29（第 16 次备注 · 1.2.0-transmutation-media-theme）
 * - 删除：介质页整屏灰底和逐槽手绘灰框；仅替换背景绘制，不移除目录、交易控件或玩家物品槽。
 * - 新增：介质页复用 ProjectE 原生转化桌背景，并以浅灰底和淡紫 EMC 炼金印记覆盖隐藏知识槽区域。
 * - 修改：介质页外观从通用灰色面板改为 ProjectE 的炼金纹样与槽位材质；交易反馈文字改为深紫灰以保持浅底对比度。
 * - 用途：让流体/气体/FE 目录融入等价交换视觉，同时维持原有搜索、选择、分页、交易和背包交互。
 */
/**
 * 【变更记录 #17】2026-10-12 14:35:41（第 17 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：介质页底部覆盖遮罩和手绘 EMC 圆印，以及顶部独立买入/卖入按钮。
 * - 新增：保留 ProjectE 原生左右炼金印记；搜索框移至原版搜索区域，交易动作由目录视图按原生槽位坐标接管。
 * - 修改：介质页背景只绘制原版纹样；页面交易改由左侧星槽充 FE、右侧知识槽购买、火焰槽卖入和遗忘槽删除知识。
 * - 用途：使介质页外观和操作对应等价交换原生槽位；服务器仍负责 EMC 扣款、容器能力及知识合法性校验。
 */
/**
 * 【变更记录 #18】2026-10-12 15:03:11（第 18 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：为介质页另建的搜索框与流体/气体按钮，以及通过背景 Mixin 替代原版搜索/标签显示的路径。
 * - 新增：复用 GUITransmutation 创建的 EditBox、原生箭头 Button 和原版背景渲染；流体/气体分类用相邻左侧页签。
 * - 修改：背景仅在合成页取消原生渲染；介质页恢复 ProjectE 原生搜索框和 EMC/学习标签，目录控件不覆盖其表现。
 * - 用途：对齐当前测试实例 ProjectE 1.20.1-PE1.0.1 实际 GUI 生命周期，避免出现重复或错位控件。
 */
/**
 * 【变更记录 #19】2026-10-12 23:38:49（第 19 次备注 · 1.2.0-media-real-slots）
 * - 删除：旧介质页不完整的交互路径。
 * - 新增：介质页显示真实追加槽，拿放容器走原版菜单交互；合成页隐藏充电槽。
 * - 修改：对照 ProjectE 真实槽位及原生控件处理。
 * - 用途：介质页状态、容器安全与显示一致，保留全部历史。
 */
/**
 * 【变更记录 #20】2026-10-13 01:53:18（第 20 次备注 · 1.2.0-media-feedback）
 * - 删除：原生按钮遗留的 tooltip，保留搜索及正常槽交互。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
package com.primordial.client;

import com.primordial.PrimordialMod;
import com.primordial.network.S2CMediaCatalogPacket;
import com.primordial.data.FluidSampleConfig;
import java.lang.ref.WeakReference;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = PrimordialMod.MODID, value = Dist.CLIENT)
public final class TransmutationMediaButton {
    private static WeakReference<TransmutationMediaCatalogView> currentCatalog = new WeakReference<>(null);
    private TransmutationMediaButton() { }

    public static boolean mediaPageActive(Object screen) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        return view != null && view.screen == screen && view.activePage == 1;
    }

    public static boolean secondaryPageActive(Object screen) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        return view != null && view.screen == screen && view.activePage != 0;
    }

    public static boolean craftingPageActive(Object screen) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        return view != null && view.screen == screen && view.activePage == 2;
    }

    public static void showCraftingPage(moze_intel.projecte.gameObjs.container.TransmutationContainer menu) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view != null && view.screen.getMenu() == menu) view.showPage(2);
    }

    public static boolean hiddenSlot(Object screen, Slot slot) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view == null || view.screen != screen || slot == null) return false;
        int index = view.screen.getMenu().slots.indexOf(slot);
        if (index < 0) return false;
        if (view.activePage == 0) return index >= 63;
        if (view.activePage == 1) return index < 27 || index >= 63 && index < 73;
        return index < 27 || index >= 73;
    }

    public static void renderMediaBackground(GuiGraphics graphics) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view == null) return;
        int left = view.left, top = view.top;
        if (view.activePage == 1) {
            graphics.blit(new ResourceLocation("projecte", "textures/gui/transmute.png"), left, top,
                    0, 0, view.screen.getXSize(), view.screen.getYSize());
            return;
        }
        graphics.fill(left, top, left + view.screen.getXSize(), top + view.screen.getYSize(), 0xFF282B2E);
        graphics.fill(left + 2, top + 2, left + view.screen.getXSize() - 2,
                top + view.screen.getYSize() - 2, 0xFFBFC1C1);
        for (Slot slot : view.screen.getMenu().slots) {
            if (hiddenSlot(view.screen, slot)) continue;
            int x = left + slot.x, y = top + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFFEEEEEE);
            graphics.fill(x, y, x + 16, y + 16, 0xFF888888);
        }
        if (view.activePage == 2) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("screen.primordial.transmutation.crafting_title"),
                    left + 32, top + 14, 0x303030, false);
            graphics.fill(left + 107, top + 50, left + 119, top + 60, 0xFF777777);
            graphics.drawString(Minecraft.getInstance().font, "→", left + 108, top + 51, 0xFFFFFF, false);
        }
    }

    public static void renderInventoryTooltip(GuiGraphics graphics, int x, int y) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view == null || !view.screen.getMenu().getCarried().isEmpty()) return;
        Slot slot = view.screen.getSlotUnderMouse();
        if (slot != null && !hiddenSlot(view.screen, slot) && slot.hasItem())
            graphics.renderTooltip(Minecraft.getInstance().font, slot.getItem(), x, y);
    }

    public static void acceptCatalog(S2CMediaCatalogPacket packet) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view != null) view.accept(packet);
    }

    public static void acceptTradeResult(com.primordial.network.S2CMediaTradeResultPacket packet) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view != null) view.acceptTradeResult(packet);
    }

    @SubscribeEvent
    public static void initialized(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof moze_intel.projecte.gameObjs.gui.GUITransmutation screen)) return;
        TransmutationMediaCatalogView previousView = currentCatalog.get();
        int restoredPage = previousView != null && previousView.screen == screen ? previousView.activePage : 0;
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        List<AbstractWidget> nativeWidgets = new ArrayList<>();
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget) nativeWidgets.add(widget);
        }
        EditBox resourceId = nativeWidgets.stream().filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).filter(widget -> widget.getX() == left + 88 && widget.getY() == top + 8)
                .findFirst().orElseThrow(() -> new IllegalStateException("ProjectE transmutation search box not found"));
        for (AbstractWidget widget : nativeWidgets) widget.setTooltip(null);
        resourceId.setMaxLength(64);
        boolean[] gasMode = {previousView != null && previousView.screen == screen && previousView.gasModeActive()};
        TransmutationMediaCatalogView view = new TransmutationMediaCatalogView(screen, resourceId, gasMode, nativeWidgets,
                left, top);
        currentCatalog = new WeakReference<>(view);
        view.add(event);
        view.showPage(restoredPage);
        TransmutationNbtButtons.install(event);
    }

    @SubscribeEvent
    public static void renderCatalog(ScreenEvent.Render.Post event) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view != null && event.getScreen() == view.screen) view.render(event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void blockNativeMediaClicks(ScreenEvent.MouseButtonPressed.Pre event) {
        TransmutationMediaCatalogView view = currentCatalog.get();
        if (view != null && event.getScreen() == view.screen && view.activePage == 1
                && event.getMouseX() >= view.left && event.getMouseX() < view.left + 228
                && event.getMouseY() >= view.top && event.getMouseY() < view.top + 115) {
            if (view.nativeMediaSlot(event.getMouseX(), event.getMouseY())) return;
            view.clickMediaWidget(event.getMouseX(), event.getMouseY(), event.getButton());
            event.setCanceled(true);
        }
    }

}
