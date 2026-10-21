/**
 * 【变更记录 #8】2026-10-11 23:44:54（第 8 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；原模组注册、ProjectE/Curios 生命周期保留。
 * - 新增：AE2 capability attachment 事件通过 ModList + 反射接入；common setup 注册隔离的存储适配器。
 * - 修改：AE2 缺失或 API 链接错误只禁用该适配，不阻断无 AE2 环境启动；RS 由自身隔离启动器检查。
 * - 用途：建立 optional API 类加载边界，防止纯 Forge/ProjectE 实例解析第三方类型。
 */
/**
 * 【文件职责】
 * - 注册 Primordial 模组的方块、物品、菜单、方块实体、创造标签、网络和客户端配置。
 * - 作为 Forge 生命周期入口，负责把公共初始化安排到对应事件阶段。
 *
 * 【变更记录 #1】2026-09-13 22:52:14（第 1 次备注·EMC 显示改造）
 * - 删除：无；保留原有注册流程，避免影响现有方块、菜单和网络初始化。
 * - 新增：客户端 EMC 显示配置注册入口，配置由 PrimordialClientConfig 提供。
 * - 修改：构造函数在注册 DeferredRegister 前登记 ModConfig.Type.CLIENT 配置。
 * - 用途：让玩家可以在 primordial-client.toml 中选择紧凑、工程或科学计数法显示 EMC；配置只影响客户端显示，不参与服务端余额计算。
 */
/**
 * 【文件职责】注册模组内容、网络和客户端配置并安排公共初始化。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：无。
 * - 新增：ModAdapterBootstrap 的 common setup 调用。
 * - 修改：在 enqueueWork 中先注册网络，再注册可选机器适配器。
 * - 用途：确保配置器/服务器任务运行前注册表可用，避免适配器只存在文件中却从未启用。
 */
/**
 * 变更记录 #3（2026-09-14）：登记服务端 ProjectE 学习策略配置。
 * - 新增：PrimordialServerConfig 及 ModConfig.Type.SERVER 注册。
 * - 目的：让服务器管理员独立控制回收物品是否自动写入 ProjectE 知识。
 */
/**
 * 【变更记录 #4】2026-09-15 12:04:51（第 4 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：Inventory Sorter 的 containerblacklist 官方 IMC，按三个菜单注册 ID 登记。
 * - 修改：在 InterModEnqueueEvent 发送 ResourceLocation，防止快捷整理操作幽灵菜单。
 * - 用途：配置器、背包配置和价格编辑共用整理排除；未安装整理模组时不需要额外类。
 */
/**
 * 【变更记录 #5】2026-09-15 15:12:00（第 5 次备注 · 1.0.0-stage8-blackhole-charm）
 * - 删除：无；保留网络、适配器和配置注册顺序。
 * - 新增：公共初始化阶段把 blackhole_charm 注册为 Curios 护符物品。
 * - 修改：Curios 绑定安排在 DeferredRegister 完成后执行，避免 RegistryObject 尚未解析。
 * - 用途：让护符在 charm 槽位获得 ICurioItem 能力；Curios 为强制依赖，缺失时由 Forge 依赖检查阻止错误启动。
 */
/**
 * 【文件职责】注册模组内容和公共生命周期入口。
 * 【变更记录 #6】2026-09-16 19:41:50（第 6 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：启动级知识配置、Forge凭证配方条件和护符菜单整理黑名单。
 * - 修改：公共初始化在配方加载前注册条件序列化器。
 * - 用途：保证开关及菜单注册实际生效，不引入新的强制可选依赖。
 */
/**
 * 【文件职责】注册公共配置加载与模组内容。
 * 【变更记录 #7】2026-09-16 19:46:19（第 7 次备注 · 1.0.0-review-ready）
 * - 删除：依赖配置类注解自动发现加载回调的路径。
 * - 新增：在模组事件总线明确添加PrimordialKnowledgeConfig.onLoad。
 * - 修改：配置注册后、游戏内容加载前挂接监听器，原有初始化顺序保持。
 * - 用途：启动快照按实际COMMON配置生成，测试与服务器使用同一个入口。
 */
/**
 * 【变更记录 #9】2026-10-13 03:02:30（第 9 次备注 · 1.2.0-quest-system）
 * - 删除：无。新增：QuestGateConfig 独立 SERVER 配置注册为 primordial-quests.toml。
 * - 修改：只注册本模组配置，不在启动类引用 FTB Quests 类型。
 * - 用途：任务条件受服务器配置权威控制，Configured 自动提供翻译后的编辑入口。
 * 【变更记录 #10】2026-10-13（第 10 次备注 · FTB 阶段奖励）
 * - 删除：QuestGateConfig 的任务 ID 映射配置注册。
 * - 修改：FTB 类型只由可选 Mixin 懒加载，主生命周期不链接 FTB Quests。
 * - 用途：由 FTB 任务奖励直接发放现有阶段，不新增启动依赖。
 * 【变更记录 #11】2026-10-13 03:56:00（第 11 次备注 · 1.2.0-ftb-stage-rewards）
 * - 删除：无；保留配置注册和公共初始化顺序。
 * - 新增：无；FTB 接入放在独立可选 Mixin，不在模组入口引用其 API。
 * - 修改：确认主生命周期仅注册原有 SERVER 阶段配置，FTB 存在与否不改变公共启动路径。
 * - 用途：让阶段奖励可选加载，避免在未安装 FTB Quests 的整合包触发第三方类解析。
 */
/**
 * 【变更记录 #12】2026-10-13 23:32:45（第 12 次备注 · 1.2.0-blacklist-system）
 * - 删除：无。新增：模组配置事件与 Forge 标签更新事件的黑名单缓存失效监听。
 * - 修改：加载、重载、卸载与切换世界时重新建立规则；用途：不沿用旧服务器配置或旧标签成员判断。
 */
/**
 * 【变更记录 #13】2026-10-14 00:51:00（第 13 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增模块菜单到 Inventory Sorter 容器排除列表。
 * - 修改：只追加新菜单注册名，保持现有容器和网络启动顺序。
 * - 用途：避免第三方整理快捷键移动正在编辑的实体模块。
 */
/**
 * 【变更记录 #14】2026-10-14 02:21:23（第 14 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：注册黑洞技能及方块碎片实体。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial;

import com.mojang.logging.LogUtils;
import com.primordial.config.PrimordialClientConfig;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.network.PrimordialNetwork;
import com.primordial.registry.ModBlockEntities;
import com.primordial.registry.ModBlocks;
import com.primordial.registry.ModCreativeTabs;
import com.primordial.registry.ModItems;
import com.primordial.registry.ModMenus;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(PrimordialMod.MODID)
public class PrimordialMod {
    public static final String MODID = "primordial";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PrimordialMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON, com.primordial.config.PrimordialKnowledgeConfig.SPEC, "primordial-common.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                ModConfig.Type.CLIENT, PrimordialClientConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                ModConfig.Type.SERVER, PrimordialServerConfig.SPEC);
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(com.primordial.config.PrimordialKnowledgeConfig::onLoad);
        bus.addListener(com.primordial.config.ResourceBlacklistRules::onConfigChange);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(com.primordial.config.ResourceBlacklistRules::onTagsUpdated);
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        com.primordial.registry.ModEntities.ENTITIES.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModMenus.MENUS.register(bus);
        ModCreativeTabs.TABS.register(bus);
        bus.addListener(this::onCommonSetup);
        bus.addListener(this::onInterModEnqueue);
        LOGGER.info("[Primordial] mod loading");
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PrimordialNetwork.register();
            net.minecraftforge.common.crafting.CraftingHelper.register(com.primordial.recipe.KnowledgeRequiredCondition.SERIALIZER);
            com.primordial.machine.ModAdapterBootstrap.registerAll();
            com.primordial.network.StorageNetworkAdapterBootstrap.registerAll();
            net.minecraft.world.item.Item charm = ModItems.BLACKHOLE_CHARM.get();
            top.theillusivec4.curios.api.CuriosApi.registerCurio(charm,
                    (top.theillusivec4.curios.api.type.capability.ICurioItem) charm);
        });
    }

    /** Inventory Sorter 1.20.1 的官方容器黑名单接收菜单注册 ID；阻止整理快捷键操作幽灵库存。 */
    private void onInterModEnqueue(net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent event) {
        for (String name : new String[]{"configurator", "player_inventory", "price_editor", "blackhole_charm", "blackhole_equipment"}) {
            net.minecraftforge.fml.InterModComms.sendTo("inventorysorter", "containerblacklist",
                    () -> new net.minecraft.resources.ResourceLocation(MODID, name));
        }
    }
}
