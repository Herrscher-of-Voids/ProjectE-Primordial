package com.primordial.neoforge.equipment;

import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import java.math.BigInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.stats.Stats;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;

public final class NeoForgeBlackholeBowItem extends BowItem {
    public static final String PIERCE_REMAINING="BlackholePierceRemaining";
    public NeoForgeBlackholeBowItem(Properties properties){super(properties.stacksTo(1).fireResistant());}
    @Override public boolean isDamageable(ItemStack stack){return false;}
    @Override public void setDamage(ItemStack stack,int damage) { }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack bow=player.getItemInHand(hand);
        boolean ammo=!player.getProjectile(bow).isEmpty()||NeoForgeBlackholeModules.installed(bow,Kind.INFINITY)
                ||EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.INFINITY),bow)>0;
        var result=EventHooks.onArrowNock(bow,level,player,hand,ammo);
        if(result!=null)return result;
        if(!ammo&&!player.getAbilities().instabuild)return InteractionResultHolder.fail(bow);
        player.startUsingItem(hand);return InteractionResultHolder.consume(bow);
    }
    public static float drawPower(int ticks,BigInteger level){
        double rate=level.signum()>0?2*NeoForgeBlackholeScaling.power(level):1;
        return getPowerForTime((int)Math.min(20,Math.max(0,ticks)*rate));
    }
    public static float drawProgress(ItemStack stack,int ticks){
        BigInteger level=NeoForgeBlackholeModules.level(stack,Kind.RAPID_DRAW);
        return (float)Math.min(1,Math.max(0,ticks)*(level.signum()>0?2*NeoForgeBlackholeScaling.power(level):1)/20.0);
    }
    // 沿用原版蓄力、弹药和附魔事件；所有付费效果、弹药扣除只在服务端真正射出时结算。
    @Override public void releaseUsing(ItemStack bow,Level world,LivingEntity entity,int remaining){
        if(!(entity instanceof ServerPlayer p))return;
        ItemStack ammo=p.getProjectile(bow);
        boolean vanillaFree=p.getAbilities().instabuild||EnchantmentHelper.getItemEnchantmentLevel(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.INFINITY),bow)>0;
        int ticks=EventHooks.onArrowLoose(bow,world,p,getUseDuration(bow,entity)-remaining,
                !ammo.isEmpty()||vanillaFree||NeoForgeBlackholeModules.installed(bow,Kind.INFINITY));
        BigInteger draw=NeoForgeBlackholeModules.level(bow,Kind.RAPID_DRAW);
        if(ticks<0||drawPower(ticks,draw)<0.1F)return;
        float power=getPowerForTime(ticks);
        BigInteger paid=BigInteger.ZERO;
        boolean empty=ammo.isEmpty();
        if(empty)ammo=new ItemStack(Items.ARROW);
        ArrowItem arrowItem=(ArrowItem)(ammo.getItem() instanceof ArrowItem?ammo.getItem():Items.ARROW);
        boolean free=p.getAbilities().instabuild||arrowItem.isInfinite(ammo,bow,p);
        BigInteger infinity=NeoForgeBlackholeModules.level(bow,Kind.INFINITY);
        if(!free&&ammo.is(Items.ARROW)&&infinity.signum()>0){
            BigInteger fee=NeoForgeBlackholeGear.charge(p,Kind.INFINITY,infinity);free=fee.signum()>0;if(free)paid=paid.add(fee);
        }
        if(empty&&!free)return;
        BigInteger drawFee=NeoForgeBlackholeGear.charge(p,Kind.RAPID_DRAW,draw);
        if(drawFee.signum()>0){paid=paid.add(drawFee);power=drawPower(ticks,draw);}
        if(power<0.1F){if(paid.signum()>0)NeoForgeBlackholeGear.refund(p,paid);return;}
        AbstractArrow arrow=customArrow(arrowItem.createArrow(world,ammo.copyWithCount(1),p,bow),ammo,bow);
        arrow.setBaseDamage(32);
        BigInteger assault=NeoForgeBlackholeModules.level(bow,Kind.ASSAULT);
        BigInteger assaultFee=NeoForgeBlackholeGear.charge(p,Kind.ASSAULT,assault);
        if(assaultFee.signum()>0){
            arrow.setBaseDamage(32+6*NeoForgeBlackholeModules.magnitude(assault));paid=paid.add(assaultFee);
        }
        BigInteger speed=NeoForgeBlackholeModules.level(bow,Kind.ARROW_SPEED);
        BigInteger speedFee=NeoForgeBlackholeGear.charge(p,Kind.ARROW_SPEED,speed);boolean fast=speedFee.signum()>0;
        double factor=fast?1.25*NeoForgeBlackholeModules.magnitude(speed):1;
        if(fast)paid=paid.add(speedFee);
        arrow.shootFromRotation(p,p.getXRot(),p.getYRot(),0,(float)Math.min(60000000,power*3*factor),1);
        arrow.setCritArrow(power==1);
        // 1.21 的力量、冲击、火矢由 firedFromWeapon 和原版附魔流水线结算。
        BigInteger pierce=NeoForgeBlackholeModules.level(bow,Kind.PIERCE);
        BigInteger pierceFee=NeoForgeBlackholeGear.charge(p,Kind.PIERCE,pierce);
        if(pierceFee.signum()>0){
            paid=paid.add(pierceFee);
            ((com.primordial.neoforge.mixin.NeoForgeBlackholeArrowAccess)arrow).primordial$setPierceLevel((byte)1);arrow.getPersistentData().putString(PIERCE_REMAINING,pierceFee.divide(BigInteger.valueOf(Kind.PIERCE.emc)).multiply(BigInteger.TWO).toString());
        }
        BigInteger singularity=NeoForgeBlackholeModules.level(bow,Kind.SINGULARITY);
        if(singularity.signum()>0)arrow.getPersistentData().putString("BlackholeImpactLevel",singularity.toString());
        if(free)arrow.pickup=AbstractArrow.Pickup.CREATIVE_ONLY;
        if(!world.addFreshEntity(arrow)){
            if(paid.signum()>0)NeoForgeBlackholeGear.refund(p,paid);
            return;
        }
        world.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,1,1/(world.random.nextFloat()*0.4F+1.2F)+power*0.5F);
        if(!free){ammo.shrink(EnchantmentHelper.processAmmoUse((ServerLevel)world,bow,ammo,1));if(ammo.isEmpty())p.getInventory().removeItem(ammo);}
        p.awardStat(Stats.ITEM_USED.get(this));
    }
}
