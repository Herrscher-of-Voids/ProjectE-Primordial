/**
 * 【变更记录 #5】2026-10-11 23:44:54（第 5 次备注 · 1.2.0-storage-interface-system）
 * - 删除：无；保留原物品注册对象及顺序。
 * - 新增：transmutation_interface 方块物品注册对象，绑定独立方块注册。
 * - 修改：无合成配方在本次创建，接口入口仅出现在创造标签中。
 * - 用途：让测试者可从创造栏放置 AE2/RS 转化接口，不改变现有工具获取方式。
 */
/**
 * 【文件职责】集中注册 Primordial 的独立物品，并为创造标签和 Curios 生命周期提供稳定 RegistryObject。
 *
 * 【变更记录 #1】2026-09-15 15:12:00（第 1 次备注 · 1.0.0-stage8-blackhole-charm）
 * - 删除：无；保留配置器和黑洞方块物品的注册顺序。
 * - 新增：blackhole_charm 护符物品注册，使用独立 Item 实现移动范围回收。
 * - 修改：无；黑洞方块仍由 BlockItem 注册，护符不会创建方块实体或库存。
 * - 用途：为 CuriosApi.registerCurio 提供可佩戴物品，并让资源模型、语言和创造标签共享同一注册名。
 */
/**
 * 【变更记录 #2】2026-09-16 13:42:00（第 2 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：emc_accelerator 单件工具物品的稳定注册入口。
 * - 修改：每个工具独立保存目标、倍率和启用状态，不可堆叠以免绑定歧义。
 * - 用途：EMC 加速服务只处理这种物品，配方、模型和创造标签使用相同注册名。
 */
/**
 * 【文件职责】集中登记可用物品。
 * 【变更记录 #3】2026-09-16 19:41:50（第 3 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：九种凭证的EnumMap注册，使用稳定的类别注册名。
 * - 修改：保留已有物品顺序，凭证使用ResourceCertificateItem统一行为。
 * - 用途：配方、知识映射、创造标签共用同一注册对象。
 */
/**
 * 【变更记录 #4】2026-10-10 02:49:08（第 4 次备注 · 1.2.0-dedicated-price-editor）
 * - 删除：无；保留配置器及其它工具的注册名和顺序。
 * - 新增：单件 PRICE_EDITOR 工具注册对象，绑定 PriceEditorItem 的服务端管理员校验逻辑。
 * - 修改：无；该物品不注册合成配方，获取入口由创造物品栏或管理员命令提供。
 * - 用途：使独立定价工具拥有稳定的 primordial:price_editor 物品身份，供语言、模型和创造栏引用。
 */
/**
 * 【变更记录 #6】2026-10-14 00:48:00（第 6 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增四件护甲、红物质九工具对应款、额外黑洞弓和七种实体模块。
 * - 修改：每件装备保留独立稳定注册名，模块由对应物品类持有类型。
 * - 用途：配方、创造栏、纹理及装备菜单共用同一物品身份。
 */
/**
 * 【变更记录 #7】2026-10-14 01:18:08（第 7 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：实体模块物品关联。
 * - 新增：装备内购买功能关联。
 * - 修改：删除七种 ModuleItem 注册与映射，装备注册名不变。
 * - 用途：功能由装备界面 EMC 解锁，避免继续提供旧实物安装入口。
 */
/**
 * 【变更记录 #8】2026-10-14 02:04:37（第 8 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：注册九种黑洞合成材料；科学分类只作身份区分，不虚构科学强弱阶级。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #9】2026-10-14 16:01:56（第 9 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：实体生成器方块物品注册名 primordial:entity_generator。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
package com.primordial.registry;

import com.primordial.PrimordialMod;
import com.primordial.item.ConfiguratorItem;
import com.primordial.item.BlackholeCharmItem;
import com.primordial.item.EmcAcceleratorItem;
import com.primordial.item.PriceEditorItem;
import com.primordial.equipment.BlackholeArmorItem;
import com.primordial.equipment.BlackholeBowItem;
import com.primordial.equipment.BlackholeModules;
import com.primordial.equipment.BlackholeTools;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PrimordialMod.MODID);

    public static final RegistryObject<Item> CONFIGURATOR = ITEMS.register("configurator",
            () -> new ConfiguratorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PRICE_EDITOR = ITEMS.register("price_editor",
            () -> new PriceEditorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> ENTITY_GENERATOR = ITEMS.register("entity_generator",
            () -> new BlockItem(ModBlocks.ENTITY_GENERATOR.get(), new Item.Properties()));

    public static final RegistryObject<Item> BLACKHOLE = ITEMS.register("blackhole",
            () -> new BlockItem(ModBlocks.BLACKHOLE.get(), new Item.Properties()));

    public static final RegistryObject<Item> TRANSMUTATION_INTERFACE = ITEMS.register("transmutation_interface",
            () -> new BlockItem(ModBlocks.TRANSMUTATION_INTERFACE.get(), new Item.Properties()));

    public static final RegistryObject<Item> BLACKHOLE_CHARM = ITEMS.register("blackhole_charm",
            () -> new BlackholeCharmItem(new Item.Properties()));

    public static final RegistryObject<Item> EMC_ACCELERATOR = ITEMS.register("emc_accelerator",
            () -> new EmcAcceleratorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> BLACKHOLE_HELMET = ITEMS.register("blackhole_helmet",
            () -> new BlackholeArmorItem(net.minecraft.world.item.ArmorItem.Type.HELMET, new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_CHESTPLATE = ITEMS.register("blackhole_chestplate",
            () -> new BlackholeArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_LEGGINGS = ITEMS.register("blackhole_leggings",
            () -> new BlackholeArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_BOOTS = ITEMS.register("blackhole_boots",
            () -> new BlackholeArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS, new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_PICK = ITEMS.register("blackhole_pick",
            () -> new BlackholeTools.Pick(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_AXE = ITEMS.register("blackhole_axe",
            () -> new BlackholeTools.Axe(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_SHOVEL = ITEMS.register("blackhole_shovel",
            () -> new BlackholeTools.Shovel(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_HOE = ITEMS.register("blackhole_hoe",
            () -> new BlackholeTools.Hoe(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_SHEARS = ITEMS.register("blackhole_shears",
            () -> new BlackholeTools.Shears(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_SWORD = ITEMS.register("blackhole_sword",
            () -> new BlackholeTools.Sword(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_HAMMER = ITEMS.register("blackhole_hammer",
            () -> new BlackholeTools.Hammer(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_KATAR = ITEMS.register("blackhole_katar",
            () -> new BlackholeTools.Katar(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_MORNING_STAR = ITEMS.register("blackhole_morning_star",
            () -> new BlackholeTools.MorningStar(new Item.Properties()));
    public static final RegistryObject<Item> BLACKHOLE_BOW = ITEMS.register("blackhole_bow",
            () -> new BlackholeBowItem(new Item.Properties()));

    public static final java.util.Map<String, RegistryObject<Item>> BLACKHOLE_GEAR = java.util.Map.ofEntries(
            java.util.Map.entry("helmet", BLACKHOLE_HELMET), java.util.Map.entry("chestplate", BLACKHOLE_CHESTPLATE),
            java.util.Map.entry("leggings", BLACKHOLE_LEGGINGS), java.util.Map.entry("boots", BLACKHOLE_BOOTS),
            java.util.Map.entry("pick", BLACKHOLE_PICK), java.util.Map.entry("axe", BLACKHOLE_AXE),
            java.util.Map.entry("shovel", BLACKHOLE_SHOVEL), java.util.Map.entry("hoe", BLACKHOLE_HOE),
            java.util.Map.entry("shears", BLACKHOLE_SHEARS), java.util.Map.entry("sword", BLACKHOLE_SWORD),
            java.util.Map.entry("hammer", BLACKHOLE_HAMMER), java.util.Map.entry("katar", BLACKHOLE_KATAR),
            java.util.Map.entry("morning_star", BLACKHOLE_MORNING_STAR), java.util.Map.entry("bow", BLACKHOLE_BOW));

    public static final java.util.Map<String,RegistryObject<Item>> BLACKHOLE_MATERIALS;
    static {
        var entries=new java.util.LinkedHashMap<String,RegistryObject<Item>>();
        for(String id:new String[]{"stellar","intermediate","supermassive","ultramassive","primordial","schwarzschild","kerr","reissner_nordstrom","kerr_newman"})
            entries.put(id,ITEMS.register("blackhole_material_"+id,()->new Item(new Item.Properties().fireResistant())));
        BLACKHOLE_MATERIALS=java.util.Collections.unmodifiableMap(entries);
    }

    public static final java.util.Map<com.primordial.service.ResourceKnowledge.Kind, RegistryObject<Item>> CERTIFICATES;
    static {
        var entries = new java.util.EnumMap<com.primordial.service.ResourceKnowledge.Kind, RegistryObject<Item>>(
                com.primordial.service.ResourceKnowledge.Kind.class);
        for (var kind : com.primordial.service.ResourceKnowledge.Kind.values())
            entries.put(kind, ITEMS.register(kind.itemId(), com.primordial.item.ResourceCertificateItem::new));
        CERTIFICATES = java.util.Collections.unmodifiableMap(entries);
    }
}
