package com.primordial.neoforge.equipment;

import moze_intel.projecte.gameObjs.EnumMatterType;
import moze_intel.projecte.gameObjs.items.tools.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class NeoForgeBlackholeTools {
    private NeoForgeBlackholeTools() { }
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
