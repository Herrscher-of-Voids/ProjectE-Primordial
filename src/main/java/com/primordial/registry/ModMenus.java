/**
 * 【变更记录 #2】2026-09-14 13:50:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：玩家背包配置和自定义价格编辑两种 MenuType。
 * - 修改：机器配置器注册顺序保持不变，新增类型使用 Forge 菜单字节缓冲工厂。
 * - 用途：ConfiguratorItem 的三种右键场景分别进入独立菜单，减少界面状态耦合。
 */
/**
 * 【文件职责】注册公共菜单类型。
 * 【变更记录 #3】2026-09-16 19:41:50（第 3 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：blackhole_charm菜单类型及打开数据中的手信息。
 * - 修改：客户端和服务端构造同一布局，样本状态后续由权威包同步。
 * - 用途：服务端打开的护符界面有稳定MenuType身份。
 */
/**
 * 【变更记录 #4】2026-09-18 01:50:43（第 4 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：acceleration 无库存管理菜单，打开数据只携带主手或副手。
 * - 修改：所有目标及价格后续使用服务端权威摘要同步。
 * - 用途：专用服务器和客户端构造相同菜单类型，避免将客户端屏幕类引入注册路径。
 */
/**
 * 【变更记录 #5】2026-10-14 00:51:00（第 5 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增黑洞装备实体模块菜单类型及开屏手信息。
 * - 修改：原菜单 ID 与注册顺序保留，新类型追加在末尾。
 * - 用途：专用服务器和客户端使用同一双槽布局。
 */
/**
 * 【变更记录 #6】2026-10-14 16:01:56（第 6 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：实体生成器菜单，打开时只传位置和服务端报价。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
package com.primordial.registry;

import com.primordial.PrimordialMod;
import com.primordial.menu.ConfiguratorMenu;
import com.primordial.menu.PlayerInventoryMenu;
import com.primordial.menu.PriceEditorMenu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, PrimordialMod.MODID);

    public static final RegistryObject<MenuType<com.primordial.menu.EntityGeneratorMenu>> ENTITY_GENERATOR =
            MENUS.register("entity_generator", () -> IForgeMenuType.create(com.primordial.menu.EntityGeneratorMenu::new));
    public static final RegistryObject<MenuType<ConfiguratorMenu>> CONFIGURATOR =
            MENUS.register("configurator", () -> IForgeMenuType.create(ConfiguratorMenu::new));
    public static final RegistryObject<MenuType<PlayerInventoryMenu>> PLAYER_INVENTORY =
            MENUS.register("player_inventory", () -> IForgeMenuType.create(PlayerInventoryMenu::new));
    public static final RegistryObject<MenuType<PriceEditorMenu>> PRICE_EDITOR =
            MENUS.register("price_editor", () -> IForgeMenuType.create((id, inv, buf) -> new PriceEditorMenu(id, inv)));
    public static final RegistryObject<MenuType<com.primordial.menu.BlackholeCharmMenu>> BLACKHOLE_CHARM =
            MENUS.register("blackhole_charm", () -> IForgeMenuType.create((id, inv, buf) ->
                    new com.primordial.menu.BlackholeCharmMenu(id, inv, buf.readEnum(net.minecraft.world.InteractionHand.class))));
    public static final RegistryObject<MenuType<com.primordial.menu.AccelerationMenu>> ACCELERATION =
            MENUS.register("acceleration", () -> IForgeMenuType.create((id, inv, buf) ->
                    new com.primordial.menu.AccelerationMenu(id, inv, buf.readEnum(net.minecraft.world.InteractionHand.class))));
    public static final RegistryObject<MenuType<com.primordial.menu.BlackholeEquipmentMenu>> BLACKHOLE_EQUIPMENT =
            MENUS.register("blackhole_equipment", () -> IForgeMenuType.create((id, inv, buf) ->
                    new com.primordial.menu.BlackholeEquipmentMenu(id, inv,
                            buf.readEnum(net.minecraft.world.InteractionHand.class))));
}
