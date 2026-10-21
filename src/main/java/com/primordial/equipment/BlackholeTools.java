/**
 * 【文件职责】复用 ProjectE 1.20.1 红物质工具动作，实现对应的九种黑洞工具。
 * 【变更记录 #1】2026-10-14 00:48:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增镐、斧、铲、锄、剪刀、剑、锤、卡塔尔、晨星的注册实现。
 * - 修改：原生红物质动作保留；镐斧铲提高基础挖掘效率，锤/卡塔尔/晨星提高蓄能上限。
 * - 用途：玩家可用同版 ProjectE 熟悉的交互操作，附加模块能力由本模组服务端扣费。
 */
/**
 * 【变更记录 #2】2026-10-14 01:43:26（第 2 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：所有九工具抗火且继承 ProjectE 的无耐久；统一挖掘/攻击提升由装备事件执行，避免只强化镐斧铲。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.equipment;

import moze_intel.projecte.gameObjs.EnumMatterType;
import moze_intel.projecte.gameObjs.items.tools.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class BlackholeTools {
    private BlackholeTools() { }
    public static final class Pick extends PEPickaxe {
        public Pick(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 4, properties.stacksTo(1).fireResistant()); }
    }
    public static final class Axe extends PEAxe {
        public Axe(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 4, properties.stacksTo(1).fireResistant()); }
    }
    public static final class Shovel extends PEShovel {
        public Shovel(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 4, properties.stacksTo(1).fireResistant()); }
    }
    public static final class Hoe extends PEHoe {
        public Hoe(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 4, properties.stacksTo(1).fireResistant()); }
    }
    public static final class Shears extends PEShears {
        public Shears(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 4, properties.stacksTo(1).fireResistant()); }
    }
    public static final class Sword extends RedMatterSword {
        public Sword(Item.Properties properties) { super(properties.stacksTo(1).fireResistant()); }
    }
    public static final class Hammer extends PEHammer {
        public Hammer(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 4, properties.stacksTo(1).fireResistant()); }
    }
    public static final class Katar extends PEKatar {
        public Katar(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 5, properties.stacksTo(1).fireResistant()); }
    }
    public static final class MorningStar extends PEMorningStar {
        public MorningStar(Item.Properties properties) { super(EnumMatterType.RED_MATTER, 5, properties.stacksTo(1).fireResistant()); }
    }
}
