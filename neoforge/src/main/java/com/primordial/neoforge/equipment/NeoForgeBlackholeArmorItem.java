package com.primordial.neoforge.equipment;

import moze_intel.projecte.gameObjs.items.armor.PEArmor;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;

public final class NeoForgeBlackholeArmorItem extends PEArmor {
    public NeoForgeBlackholeArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Properties properties) {
        super(material,type,properties.stacksTo(1).fireResistant());
    }
    @Override public boolean isDamageable(ItemStack stack){return false;}
    @Override public void setDamage(ItemStack stack,int damage){ }
    @Override public float getFullSetBaseReduction(){return 0.99F;}
    @Override public float getMaxDamageAbsorb(ArmorItem.Type type,DamageSource source){
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)?0:1000000F;
    }
}
