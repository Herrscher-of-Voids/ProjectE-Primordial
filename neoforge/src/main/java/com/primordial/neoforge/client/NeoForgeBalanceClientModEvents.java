/**
 * 【文件职责】在客户端 mod 总线注册余额 HUD 层与 K 键映射；渲染只读取客户端已同步余额。
 * 【变更记录 #1】2026-10-22 11:36:36（第 1 次备注 · 2.0.0-neo-dev 客户端生命周期）
 * - 删除：使用默认事件总线订阅客户端注册事件的隐式选择。
 * - 新增：显式限定 Dist.CLIENT 与 mod 总线。
 * - 修改：HUD/按键注册保留原处理器，通过正确总线在客户端初始化期间触发。
 * - 用途：保证普通客户端能注册 HUD 与按键，专用服务器不加载该客户端订阅器。
 * 【变更记录 #2】2026-10-22 21:44:20（第 2 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：模组列表中的 NeoForge 原生配置入口。
 * - 修改：仅客户端注册配置界面，不给专用服务器加载 GUI 类。
 * - 用途：回收参数可从配置菜单查看和调整。
 * 【变更记录 #3】2026-10-23 17:05:23（第 3 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无；新增：定价器原生菜单与屏幕绑定。
 * - 修改：在客户端初始化主线程注册屏幕；服务端仍仅加载菜单和包处理器。
 * - 用途：使物品打开的菜单具有受权限约束的取样与定价界面。
 */
package com.primordial.neoforge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NeoForgeBalanceClientModEvents {
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.primordial.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.primordial");
    private static final ResourceLocation HUD_LAYER = ResourceLocation.fromNamespaceAndPath(
            PrimordialNeoForgeMod.MOD_ID, "balance_hud");

    private NeoForgeBalanceClientModEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ModList.get().getModContainerById(PrimordialNeoForgeMod.MOD_ID).orElseThrow()
                .registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory) ConfigurationScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.primordial.neoforge.NeoForgeModRegistries.ACCELERATION_MENU.get(), com.primordial.neoforge.acceleration.AccelerationScreen::new);
        event.register(com.primordial.neoforge.NeoForgeModRegistries.ENTITY_GENERATOR_MENU.get(), com.primordial.neoforge.generator.NeoForgeEntityGeneratorScreen::new);
        event.register(com.primordial.neoforge.NeoForgeModRegistries.BLACKHOLE_EQUIPMENT_MENU.get(), com.primordial.neoforge.equipment.NeoForgeBlackholeEquipmentScreen::new);
        event.register(com.primordial.neoforge.NeoForgeModRegistries.BLACKHOLE_CHARM_MENU.get(),
                com.primordial.neoforge.charm.NeoForgeBlackholeCharmScreen::new);
        event.register(com.primordial.neoforge.NeoForgeModRegistries.PRICE_EDITOR_MENU.get(),
                com.primordial.neoforge.priceeditor.NeoForgePriceEditorScreen::new);
        event.register(com.primordial.neoforge.NeoForgeModRegistries.PLAYER_INVENTORY_MENU.get(),
                com.primordial.neoforge.machine.NeoForgePlayerInventoryScreen::new);
        event.register(com.primordial.neoforge.NeoForgeModRegistries.MACHINE_CONFIGURATOR_MENU.get(),
                com.primordial.neoforge.machine.NeoForgeMachineConfiguratorScreen::new);
    }

    @SubscribeEvent
    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.primordial.neoforge.NeoForgeModRegistries.SINGULARITY.get(), NeoForgeSingularityRenderer::new);
        event.registerEntityRenderer(com.primordial.neoforge.NeoForgeModRegistries.SINGULARITY_DEBRIS.get(), NeoForgeSingularityDebrisRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HUD);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, HUD_LAYER, NeoForgeBalanceClientModEvents::renderHud);
    }

    private static void renderHud(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientBalanceState.hudVisible() || minecraft.player == null || minecraft.options.hideGui) {
            return;
        }
        Component text = Component.translatable("hud.primordial.balance",
                NeoForgeEmcFormatter.format(ClientBalanceState.emc()));
        graphics.drawString(minecraft.font, text, 4, 18, 0xFF55FFFF, true);
    }
}
