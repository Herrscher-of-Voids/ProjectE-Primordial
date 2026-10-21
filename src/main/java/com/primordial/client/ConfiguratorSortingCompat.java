/**
 * 【文件职责】在三种配置器屏幕内移除已知整理/转移按钮，保留 JEI 与正常编辑控件。
 * 【变更记录 #1】2026-09-15 11:40:46（第 1 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：Inventory Tweaks、Quark、Crafting Tweaks 控件类识别和 Forge 初始化/绘制前钩子。
 * - 修改：初始化完成后移除监听器；绘制前停用后添加的按钮，防止重建界面后重新出现。
 * - 用途：配合屏幕和菜单的 IPNIgnore、MouseTweaksIgnore 注解保护幽灵槽；仅扫描当前配置器的少量控件。
 */
/**
 * 【文件职责】抑制配置菜单中的背包整理控件。
 * 【变更记录 #2】2026-09-16 19:41:50（第 2 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：将黑洞护符筛选屏幕列入现有保护范围。
 * - 修改：继续使用现有整理控件识别与官方忽略注解。
 * - 用途：幽灵样本界面不显示受支持整理模组的按钮，不改变普通背包。
 */
/**
 * 【变更记录 #3】2026-09-18 01:59:51（第 3 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：将无库存加速器管理屏幕纳入已有整理按钮保护范围。
 * - 修改：仍按已知控件类识别，不影响加速模式、名单或目标按钮。
 * - 用途：避免整理模组在纯管理界面叠加无用按钮。
 */
package com.primordial.client;

import com.primordial.PrimordialMod;
import com.primordial.screen.ConfiguratorScreen;
import com.primordial.screen.PlayerInventoryScreen;
import com.primordial.screen.PriceEditorScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

@Mod.EventBusSubscriber(modid = PrimordialMod.MODID, value = Dist.CLIENT)
public final class ConfiguratorSortingCompat {
    private ConfiguratorSortingCompat() { }

    private static boolean protectedScreen(Screen screen) {
        return screen instanceof ConfiguratorScreen || screen instanceof PlayerInventoryScreen || screen instanceof PriceEditorScreen
                || screen instanceof com.primordial.screen.BlackholeCharmScreen
                || screen instanceof com.primordial.screen.AccelerationScreen;
    }

    /** 按已审计的控件类与父类识别，不能用按钮文字或笼统外部包名误删其它模组功能。 */
    public static boolean sortingWidget(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            String name = c.getName();
            if (name.startsWith("invtweaks.gui.InvTweaksButton")
                    || name.equals("org.violetmoon.quark.content.management.client.screen.widgets.MiniInventoryButton")
                    || name.equals("vazkii.quark.content.management.client.screen.widgets.MiniInventoryButton")
                    || name.equals("net.blay09.mods.craftingtweaks.client.GuiTweakButton")) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void initialized(ScreenEvent.Init.Post event) {
        if (!protectedScreen(event.getScreen())) return;
        for (var child : List.copyOf(event.getListenersList())) {
            if (sortingWidget(child.getClass())) event.removeListener(child);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (!protectedScreen(event.getScreen())) return;
        for (var child : event.getScreen().children()) {
            if (child instanceof AbstractWidget widget && sortingWidget(child.getClass())) {
                widget.visible = false;
                widget.active = false;
            }
        }
    }
}
