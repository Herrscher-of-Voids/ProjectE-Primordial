/**
 * 【变更记录 #2】2026-09-14 13:50:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：玩家背包和自定义价格菜单的客户端 Screen 注册。
 * - 修改：保留机器配置器注册，三类菜单在客户端生命周期统一注册。
 * - 用途：服务端空气右键打开的菜单拥有对应客户端渲染器，避免菜单类型找不到界面。
 */
/**
 * 【文件职责】注册客户端界面与显示入口。
 * 【变更记录 #3】2026-09-16 19:41:50（第 3 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：BlackholeCharmScreen菜单映射。
 * - 修改：客户端生命周期注册，不把屏幕类型放入服务端菜单构造。
 * - 用途：玩家手持护符右键能显示完整筛选界面。
 */
/**
 * 【变更记录 #4】2026-09-18 01:50:43（第 4 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：加速器管理屏幕和默认 X 管理按键，可在原版控制设置重绑。
 * - 修改：所有新客户端类型仅在客户端生命周期加载。
 * - 用途：主手或副手持有加速器时打开统一管理入口，服务端仍验证实际物品。
 */
/**
 * 【变更记录 #5】2026-10-14 00:55:00（第 5 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增黑洞装备模块屏幕注册。
 * - 修改：客户端初始化追加菜单映射及可重绑的 H 模块配置键，不占 ProjectE 右键动作。
 * - 用途：手持装备按键打开可交互双槽安装界面。
 */
/**
 * 【变更记录 #6】2026-10-14 02:22:31（第 6 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：客户端注册立体黑洞及被卷入方块渲染器，服务端不引用渲染类型。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #7】2026-10-14 02:37:36（第 7 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：可改键B触发招牌主动能力，H继续配置等级。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #8】2026-10-14 03:11:54（第 8 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：拉弦三阶段模型按快速拉弓实际进度显示，避免已满蓄力仍显示半拉弦。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
/**
 * 【变更记录 #9】2026-10-14 16:01:56（第 9 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：客户端注册实体生成器搜索与单个兑换界面。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
package com.primordial.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.primordial.PrimordialMod;
import com.primordial.registry.ModMenus;
import com.primordial.screen.ConfiguratorScreen;
import com.primordial.screen.PlayerInventoryScreen;
import com.primordial.screen.PriceEditorScreen;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = PrimordialMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.primordial.toggle_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.primordial");

    public static final KeyMapping SIGNATURE=new KeyMapping("key.primordial.signature",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.primordial");
    private ClientSetup() {}
    public static final KeyMapping ACCELERATOR_MENU = new KeyMapping("key.primordial.accelerator_menu",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.primordial");
    public static final KeyMapping EQUIPMENT_MENU = new KeyMapping("key.primordial.equipment_menu",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.primordial");

    @SubscribeEvent
    public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(com.primordial.registry.ModEntities.SINGULARITY.get(),SingularityRenderer::new);
        event.registerEntityRenderer(com.primordial.registry.ModEntities.DEBRIS.get(),SingularityDebrisRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_HUD);
        event.register(ACCELERATOR_MENU);
        event.register(EQUIPMENT_MENU);
        event.register(SIGNATURE);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.CONFIGURATOR.get(), ConfiguratorScreen::new);
            MenuScreens.register(ModMenus.ENTITY_GENERATOR.get(), com.primordial.screen.EntityGeneratorScreen::new);
            MenuScreens.register(ModMenus.PLAYER_INVENTORY.get(), PlayerInventoryScreen::new);
            MenuScreens.register(ModMenus.PRICE_EDITOR.get(), PriceEditorScreen::new);
            MenuScreens.register(ModMenus.BLACKHOLE_CHARM.get(), com.primordial.screen.BlackholeCharmScreen::new);
            MenuScreens.register(ModMenus.ACCELERATION.get(), com.primordial.screen.AccelerationScreen::new);
            MenuScreens.register(ModMenus.BLACKHOLE_EQUIPMENT.get(), com.primordial.screen.BlackholeEquipmentScreen::new);
            net.minecraft.client.renderer.item.ItemProperties.register(com.primordial.registry.ModItems.BLACKHOLE_BOW.get(),
                    new net.minecraft.resources.ResourceLocation("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem()
                            && entity.getUseItem() == stack ? 1.0F : 0.0F);
            net.minecraft.client.renderer.item.ItemProperties.register(com.primordial.registry.ModItems.BLACKHOLE_BOW.get(),
                    new net.minecraft.resources.ResourceLocation("pull"),
                    (stack, level, entity, seed) -> entity == null || entity.getUseItem() != stack ? 0.0F
                            : com.primordial.equipment.BlackholeBowItem.drawProgress(stack,stack.getUseDuration() - entity.getUseItemRemainingTicks()));
        });
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        // 左上角余额 HUD：渲染在最上层，y 已下移避开顶格
        event.registerAboveAll("balance_hud", BalanceHudOverlay.HUD);
    }
}
