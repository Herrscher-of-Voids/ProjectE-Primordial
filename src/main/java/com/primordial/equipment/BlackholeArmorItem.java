/**
 * 【文件职责】基于 ProjectE 红物质护甲提供黑洞套装的额外防护与专用外观。
 * 【变更记录 #1】2026-10-14 00:45:03（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增比红物质全套略高的基础减伤和独立黑洞护甲纹理。
 * - 修改：仍继承 ProjectE 1.20.1 的盔甲判定和槽位语义；额外模块效果独立计费。
 * - 用途：四件套保留等价交换防护行为，造型与模块由本模组维护。
 */
/**
 * 【变更记录 #2】2026-10-14 01:38:39（第 2 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：独立高阶护甲材质：头10胸16腿14靴10、每件韧性8、99%全套基础减伤及高额吸收；无耐久，不处理虚空/强制击杀。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.equipment;

import moze_intel.projecte.gameObjs.items.armor.PEArmor;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class BlackholeArmorItem extends PEArmor {
    public enum Material implements ArmorMaterial {
        INSTANCE;
        public int getDurabilityForType(ArmorItem.Type type){return 0;}
        public int getDefenseForType(ArmorItem.Type type){return switch(type){case HELMET->10;case CHESTPLATE->16;case LEGGINGS->14;case BOOTS->10;};}
        public int getEnchantmentValue(){return 30;}
        public SoundEvent getEquipSound(){return SoundEvents.ARMOR_EQUIP_NETHERITE;}
        public Ingredient getRepairIngredient(){return Ingredient.EMPTY;}
        public String getName(){return "primordial:blackhole";}
        public float getToughness(){return 8;}
        public float getKnockbackResistance(){return 0.25F;}
    }
    public BlackholeArmorItem(ArmorItem.Type type,Properties properties){super(Material.INSTANCE,type,properties.stacksTo(1).fireResistant());}
    @Override public float getFullSetBaseReduction(){return 0.99F;}
    @Override public float getMaxDamageAbsorb(ArmorItem.Type type,DamageSource source){
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)?0:1000000F;
    }
    @Override public boolean isDamageable(ItemStack stack){return false;}
    @Override public void setDamage(ItemStack stack,int damage){ }
    @Override public String getArmorTexture(ItemStack stack,net.minecraft.world.entity.Entity entity,EquipmentSlot slot,String type){
        return "primordial:textures/models/armor/blackhole_layer_"+(slot==EquipmentSlot.LEGS?2:1)+".png";
    }
}
