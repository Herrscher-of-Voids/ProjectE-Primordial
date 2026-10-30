/**
 * 【文件职责】NeoForge 平台注册表入口，统一注册本平台的方块、物品、实体类型和菜单类型。
 *
 * 【变更记录 #1】2026-10-22 11:18:30（第 1 次备注 · 2.0.0-neo-dev 黑洞系统迁移）
 * - 删除：无；保留既有 DeferredRegister 及其事件总线绑定。
 * - 新增：primordial:blackhole 方块、BlockItem 与 BlockEntityType 的延迟注册。
 * - 修改：注册器使用显式 DeferredHolder 类型；属性沿用 Forge 黑洞的强度、抗爆性与工具需求。
 * - 用途：在独立 NeoForge 工程建立首个真实方块实体闭环，不引用 Forge 类型或启动未移植服务。
 *
 * 【变更记录 #2】2026-10-22 22:47:07（第 2 次备注 · 2.0.0-neo-dev 转化接口基础迁移）
 * - 删除：无。
 * - 新增：primordial:transmutation_interface 方块、BlockItem 和 BlockEntityType 延迟注册。
 * - 修改：保留 Forge 接口的紫色金属外观、3/6 强度抗爆和正确工具需求。
 * - 用途：独立注册接口身份和新档生命周期，不创建普通库存或尚未迁移的网络交易。
 * 【变更记录 #3】2026-10-23 13:30:35（第 3 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：事件总线绑定前注册资源凭证与创造模式页。
 * - 修改：具体凭证表由独立 NeoForgeCertificates 管理，主注册器保持单一注册职责。
 * - 用途：注册与 Forge 一致的物品身份而不提前解析 DeferredHolder。
 * 【变更记录 #4】2026-10-23 17:05:23（第 4 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无；新增：定价器物品及无普通库存的菜单类型。
 * - 修改：菜单由服务端库存构造，取样与写价通过独立网络处理器复核。
 * - 用途：接通管理员工具身份和原生菜单生命周期，不引入可选 API。
 */
package com.primordial.neoforge;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.flag.FeatureFlags;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorItem;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorMenu;
import com.primordial.neoforge.machine.NeoForgeMachineConfiguratorMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import com.primordial.neoforge.blackhole.NeoForgeBlackholeBlock;
import com.primordial.neoforge.blackhole.NeoForgeBlackholeBlockEntity;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationInterfaceBlock;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationInterfaceBlockEntity;

public final class NeoForgeModRegistries {
    public static final DeferredRegister<net.minecraft.world.item.ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, PrimordialNeoForgeMod.MOD_ID);
    public static final DeferredHolder<net.minecraft.world.item.ArmorMaterial, net.minecraft.world.item.ArmorMaterial> BLACKHOLE_ARMOR_MATERIAL =
            ARMOR_MATERIALS.register("blackhole", () -> new net.minecraft.world.item.ArmorMaterial(
                    java.util.Map.of(net.minecraft.world.item.ArmorItem.Type.HELMET, 10,
                            net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, 16,
                            net.minecraft.world.item.ArmorItem.Type.LEGGINGS, 14,
                            net.minecraft.world.item.ArmorItem.Type.BOOTS, 10,
                            net.minecraft.world.item.ArmorItem.Type.BODY, 16),
                    30, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE,
                    () -> net.minecraft.world.item.crafting.Ingredient.EMPTY,
                    java.util.List.of(new net.minecraft.world.item.ArmorMaterial.Layer(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("primordial", "blackhole"))), 8, .25F));
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, PrimordialNeoForgeMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, PrimordialNeoForgeMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, PrimordialNeoForgeMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PrimordialNeoForgeMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, PrimordialNeoForgeMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PrimordialNeoForgeMod.MOD_ID);

    public static final DeferredHolder<Block, NeoForgeBlackholeBlock> BLACKHOLE = BLOCKS.register("blackhole",
            () -> new NeoForgeBlackholeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 600.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredHolder<Item, BlockItem> BLACKHOLE_ITEM = ITEMS.register("blackhole",
            () -> new BlockItem(BLACKHOLE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NeoForgeBlackholeBlockEntity>> BLACKHOLE_ENTITY =
            BLOCK_ENTITY_TYPES.register("blackhole", () -> BlockEntityType.Builder
                    .of(NeoForgeBlackholeBlockEntity::new, BLACKHOLE.get()).build(null));

    public static final DeferredHolder<Block, NeoForgeTransmutationInterfaceBlock> TRANSMUTATION_INTERFACE =
            BLOCKS.register("transmutation_interface", () -> new NeoForgeTransmutationInterfaceBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                            .strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));

    public static final DeferredHolder<Item, BlockItem> TRANSMUTATION_INTERFACE_ITEM =
            ITEMS.register("transmutation_interface", () -> new BlockItem(TRANSMUTATION_INTERFACE.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NeoForgeTransmutationInterfaceBlockEntity>>
            TRANSMUTATION_INTERFACE_ENTITY = BLOCK_ENTITY_TYPES.register("transmutation_interface",
                    () -> BlockEntityType.Builder.of(NeoForgeTransmutationInterfaceBlockEntity::new,
                            TRANSMUTATION_INTERFACE.get()).build(null));

    public static final DeferredHolder<Item, NeoForgePriceEditorItem> PRICE_EDITOR_ITEM =
            ITEMS.register("price_editor", () -> new NeoForgePriceEditorItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<MenuType<?>, MenuType<NeoForgePriceEditorMenu>> PRICE_EDITOR_MENU =
            MENU_TYPES.register("price_editor", () -> new MenuType<>(NeoForgePriceEditorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<Item, com.primordial.neoforge.machine.NeoForgeConfiguratorItem> CONFIGURATOR_ITEM =
            ITEMS.register("configurator", () -> new com.primordial.neoforge.machine.NeoForgeConfiguratorItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<MenuType<?>, MenuType<com.primordial.neoforge.machine.NeoForgePlayerInventoryMenu>> PLAYER_INVENTORY_MENU =
            MENU_TYPES.register("player_inventory", () -> new MenuType<>(com.primordial.neoforge.machine.NeoForgePlayerInventoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<NeoForgeMachineConfiguratorMenu>> MACHINE_CONFIGURATOR_MENU =
            MENU_TYPES.register("configurator", () -> IMenuTypeExtension.create(
                    (id, inventory, extra) -> new NeoForgeMachineConfiguratorMenu(id, inventory, extra.readUtf(256))));

    private NeoForgeModRegistries() {}

    public static final DeferredHolder<Item, com.primordial.neoforge.charm.NeoForgeBlackholeCharmItem> BLACKHOLE_CHARM_ITEM =
            ITEMS.register("blackhole_charm", () -> new com.primordial.neoforge.charm.NeoForgeBlackholeCharmItem(new Item.Properties()));

    public static final DeferredHolder<MenuType<?>, MenuType<com.primordial.neoforge.charm.NeoForgeBlackholeCharmMenu>> BLACKHOLE_CHARM_MENU =
            MENU_TYPES.register("blackhole_charm", () -> IMenuTypeExtension.create((id, inventory, extra) ->
                    new com.primordial.neoforge.charm.NeoForgeBlackholeCharmMenu(id, inventory,
                            extra.readEnum(net.minecraft.world.InteractionHand.class))));

    public static final DeferredHolder<Item, com.primordial.neoforge.acceleration.EmcAcceleratorItem> EMC_ACCELERATOR =
            ITEMS.register("emc_accelerator", () -> new com.primordial.neoforge.acceleration.EmcAcceleratorItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<MenuType<?>, MenuType<com.primordial.neoforge.acceleration.AccelerationMenu>> ACCELERATION_MENU =
            MENU_TYPES.register("acceleration", () -> IMenuTypeExtension.create((id, inventory, extra) ->
                    new com.primordial.neoforge.acceleration.AccelerationMenu(id, inventory, extra.readEnum(net.minecraft.world.InteractionHand.class))));
    public static final DeferredHolder<Block, com.primordial.neoforge.generator.NeoForgeEntityGeneratorBlock> ENTITY_GENERATOR =
            BLOCKS.register("entity_generator", () -> new com.primordial.neoforge.generator.NeoForgeEntityGeneratorBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3, 6).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final DeferredHolder<Item, BlockItem> ENTITY_GENERATOR_ITEM = ITEMS.register("entity_generator", () -> new BlockItem(ENTITY_GENERATOR.get(), new Item.Properties()));
    public static final DeferredHolder<MenuType<?>, MenuType<com.primordial.neoforge.generator.NeoForgeEntityGeneratorMenu>> ENTITY_GENERATOR_MENU =
            MENU_TYPES.register("entity_generator", () -> IMenuTypeExtension.create(com.primordial.neoforge.generator.NeoForgeEntityGeneratorMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<com.primordial.neoforge.equipment.NeoForgeBlackholeEquipmentMenu>> BLACKHOLE_EQUIPMENT_MENU =
            MENU_TYPES.register("blackhole_equipment", () -> IMenuTypeExtension.create((id, inventory, extra) ->
                    new com.primordial.neoforge.equipment.NeoForgeBlackholeEquipmentMenu(id, inventory, extra.readEnum(net.minecraft.world.InteractionHand.class))));
    public static final DeferredHolder<EntityType<?>, EntityType<com.primordial.neoforge.equipment.NeoForgeSingularityEntity>> SINGULARITY =
            ENTITY_TYPES.register("singularity", () -> EntityType.Builder.<com.primordial.neoforge.equipment.NeoForgeSingularityEntity>of(
                    com.primordial.neoforge.equipment.NeoForgeSingularityEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1, 1).clientTrackingRange(16).updateInterval(1).fireImmune().build("primordial:singularity"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.primordial.neoforge.equipment.NeoForgeSingularityDebrisEntity>> SINGULARITY_DEBRIS =
            ENTITY_TYPES.register("singularity_debris", () -> EntityType.Builder.<com.primordial.neoforge.equipment.NeoForgeSingularityDebrisEntity>of(
                    com.primordial.neoforge.equipment.NeoForgeSingularityDebrisEntity::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(.7F, .7F).clientTrackingRange(12).updateInterval(2).fireImmune().build("primordial:singularity_debris"));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem> BLACKHOLE_HELMET = ITEMS.register("blackhole_helmet",
            () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem(BLACKHOLE_ARMOR_MATERIAL, net.minecraft.world.item.ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem> BLACKHOLE_CHESTPLATE = ITEMS.register("blackhole_chestplate",
            () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem(BLACKHOLE_ARMOR_MATERIAL, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem> BLACKHOLE_LEGGINGS = ITEMS.register("blackhole_leggings",
            () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem(BLACKHOLE_ARMOR_MATERIAL, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem> BLACKHOLE_BOOTS = ITEMS.register("blackhole_boots",
            () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeArmorItem(BLACKHOLE_ARMOR_MATERIAL, net.minecraft.world.item.ArmorItem.Type.BOOTS, new Item.Properties().stacksTo(1).fireResistant()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Pick> BLACKHOLE_PICK = ITEMS.register("blackhole_pick", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Pick(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Axe> BLACKHOLE_AXE = ITEMS.register("blackhole_axe", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Axe(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Shovel> BLACKHOLE_SHOVEL = ITEMS.register("blackhole_shovel", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Shovel(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Hoe> BLACKHOLE_HOE = ITEMS.register("blackhole_hoe", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Hoe(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Shears> BLACKHOLE_SHEARS = ITEMS.register("blackhole_shears", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Shears(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Sword> BLACKHOLE_SWORD = ITEMS.register("blackhole_sword", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Sword(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Hammer> BLACKHOLE_HAMMER = ITEMS.register("blackhole_hammer", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Hammer(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Katar> BLACKHOLE_KATAR = ITEMS.register("blackhole_katar", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.Katar(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeTools.MorningStar> BLACKHOLE_MORNING_STAR = ITEMS.register("blackhole_morning_star", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeTools.MorningStar(new Item.Properties()));
    public static final DeferredHolder<Item, com.primordial.neoforge.equipment.NeoForgeBlackholeBowItem> BLACKHOLE_BOW = ITEMS.register("blackhole_bow", () -> new com.primordial.neoforge.equipment.NeoForgeBlackholeBowItem(new Item.Properties()));
    public static final java.util.Map<String, DeferredHolder<Item, ? extends Item>> BLACKHOLE_GEAR = java.util.Map.ofEntries(
            java.util.Map.entry("helmet", BLACKHOLE_HELMET),
            java.util.Map.entry("chestplate", BLACKHOLE_CHESTPLATE),
            java.util.Map.entry("leggings", BLACKHOLE_LEGGINGS),
            java.util.Map.entry("boots", BLACKHOLE_BOOTS),
            java.util.Map.entry("pick", BLACKHOLE_PICK),
            java.util.Map.entry("axe", BLACKHOLE_AXE),
            java.util.Map.entry("shovel", BLACKHOLE_SHOVEL),
            java.util.Map.entry("hoe", BLACKHOLE_HOE),
            java.util.Map.entry("shears", BLACKHOLE_SHEARS),
            java.util.Map.entry("sword", BLACKHOLE_SWORD),
            java.util.Map.entry("hammer", BLACKHOLE_HAMMER),
            java.util.Map.entry("katar", BLACKHOLE_KATAR),
            java.util.Map.entry("morning_star", BLACKHOLE_MORNING_STAR),
            java.util.Map.entry("bow", BLACKHOLE_BOW));

    public static final java.util.Map<String, DeferredHolder<Item, Item>> BLACKHOLE_MATERIALS;
    static {
        var materials = new java.util.LinkedHashMap<String, DeferredHolder<Item, Item>>();
        for (String id : new String[]{"primordial", "stellar", "intermediate", "supermassive", "ultramassive",
                "schwarzschild", "reissner_nordstrom", "kerr", "kerr_newman"})
            materials.put(id, ITEMS.register("blackhole_material_" + id, () -> new Item(new Item.Properties().fireResistant())));
        BLACKHOLE_MATERIALS = java.util.Collections.unmodifiableMap(materials);
    }

    public static void register(IEventBus modEventBus) {
        com.primordial.neoforge.registry.NeoForgeCertificates.register();
        ARMOR_MATERIALS.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
