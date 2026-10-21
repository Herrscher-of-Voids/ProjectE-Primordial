/**
 * 【文件职责】黑洞弓保留原版箭矢消耗，使用 EMC 为实际射出的箭追加伤害。
 * 【变更记录 #1】2026-10-14 00:48:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增弓箭射出时 16 EMC 的强化伤害结算。
 * - 修改：余额不足时仍可用普通弓攻击，不能获得免费强化；客户端不写余额。
 * - 用途：作为红物质九工具之外的第十件工具，射击无需新增弹药类型。
 */
/**
 * 【变更记录 #2】2026-10-14 01:59:18（第 2 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：黑洞弓取消耐久，基础箭伤提高至 32；射出时按启用等级结算强袭、箭速、穿透，无限普通箭不会复制药水箭。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #3】2026-10-14 03:11:54（第 3 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：新增快速拉弓1级2倍速度，逐级翻倍并实际射出才扣费；所有弓模块记录指数实付费，生成失败完整退款；穿透目标数同步指数。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;

import com.primordial.equipment.BlackholeModules.Kind;
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
import net.minecraftforge.event.ForgeEventFactory;

public final class BlackholeBowItem extends BowItem {
    public static final String PIERCE_REMAINING="BlackholePierceRemaining";
    public BlackholeBowItem(Properties properties){super(properties.stacksTo(1).fireResistant());}
    @Override public boolean isDamageable(ItemStack stack){return false;}
    @Override public void setDamage(ItemStack stack,int damage) { }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        ItemStack bow=player.getItemInHand(hand);
        boolean ammo=!player.getProjectile(bow).isEmpty()||BlackholeModules.installed(bow,Kind.INFINITY)
                ||EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS,bow)>0;
        var result=ForgeEventFactory.onArrowNock(bow,level,player,hand,ammo);
        if(result!=null)return result;
        if(!ammo&&!player.getAbilities().instabuild)return InteractionResultHolder.fail(bow);
        player.startUsingItem(hand);return InteractionResultHolder.consume(bow);
    }
    public static float drawPower(int ticks,BigInteger level){
        double rate=level.signum()>0?2*BlackholeScaling.power(level):1;
        return getPowerForTime((int)Math.min(20,Math.max(0,ticks)*rate));
    }
    public static float drawProgress(ItemStack stack,int ticks){
        BigInteger level=BlackholeModules.level(stack,Kind.RAPID_DRAW);
        return (float)Math.min(1,Math.max(0,ticks)*(level.signum()>0?2*BlackholeScaling.power(level):1)/20.0);
    }
    // 沿用原版蓄力、弹药和附魔事件；所有付费效果、弹药扣除只在服务端真正射出时结算。
    @Override public void releaseUsing(ItemStack bow,Level world,LivingEntity entity,int remaining){
        if(!(entity instanceof ServerPlayer p))return;
        ItemStack ammo=p.getProjectile(bow);
        boolean vanillaFree=p.getAbilities().instabuild||EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS,bow)>0;
        int ticks=ForgeEventFactory.onArrowLoose(bow,world,p,getUseDuration(bow)-remaining,
                !ammo.isEmpty()||vanillaFree||BlackholeModules.installed(bow,Kind.INFINITY));
        BigInteger draw=BlackholeModules.level(bow,Kind.RAPID_DRAW);
        if(ticks<0||drawPower(ticks,draw)<0.1F)return;
        float power=getPowerForTime(ticks);
        BigInteger paid=BigInteger.ZERO;
        boolean empty=ammo.isEmpty();
        if(empty)ammo=new ItemStack(Items.ARROW);
        ArrowItem arrowItem=(ArrowItem)(ammo.getItem() instanceof ArrowItem?ammo.getItem():Items.ARROW);
        boolean free=p.getAbilities().instabuild||arrowItem.isInfinite(ammo,bow,p);
        BigInteger infinity=BlackholeModules.level(bow,Kind.INFINITY);
        if(!free&&ammo.is(Items.ARROW)&&infinity.signum()>0){
            BigInteger fee=BlackholeGear.charge(p,Kind.INFINITY,infinity);free=fee.signum()>0;if(free)paid=paid.add(fee);
        }
        if(empty&&!free)return;
        BigInteger drawFee=BlackholeGear.charge(p,Kind.RAPID_DRAW,draw);
        if(drawFee.signum()>0){paid=paid.add(drawFee);power=drawPower(ticks,draw);}
        if(power<0.1F){if(paid.signum()>0)com.primordial.service.EmcService.add(p.server,p.getUUID(),paid);return;}
        AbstractArrow arrow=arrowItem.createArrow(world,ammo,p);
        arrow.setBaseDamage(32);
        BigInteger assault=BlackholeModules.level(bow,Kind.ASSAULT);
        BigInteger assaultFee=BlackholeGear.charge(p,Kind.ASSAULT,assault);
        if(assaultFee.signum()>0){
            arrow.setBaseDamage(32+6*BlackholeModules.magnitude(assault));paid=paid.add(assaultFee);
        }
        BigInteger speed=BlackholeModules.level(bow,Kind.ARROW_SPEED);
        BigInteger speedFee=BlackholeGear.charge(p,Kind.ARROW_SPEED,speed);boolean fast=speedFee.signum()>0;
        double factor=fast?1.25*BlackholeModules.magnitude(speed):1;
        if(fast)paid=paid.add(speedFee);
        arrow.shootFromRotation(p,p.getXRot(),p.getYRot(),0,(float)Math.min(60000000,power*3*factor),1);
        arrow.setCritArrow(power==1);
        int damage=EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS,bow);
        if(damage>0)arrow.setBaseDamage(arrow.getBaseDamage()+damage*0.5+0.5);
        arrow.setKnockback(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS,bow));
        if(EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS,bow)>0)arrow.setSecondsOnFire(100);
        BigInteger pierce=BlackholeModules.level(bow,Kind.PIERCE);
        BigInteger pierceFee=BlackholeGear.charge(p,Kind.PIERCE,pierce);
        if(pierceFee.signum()>0){
            paid=paid.add(pierceFee);
            arrow.setPierceLevel((byte)1);arrow.getPersistentData().putString(PIERCE_REMAINING,pierceFee.divide(BigInteger.valueOf(Kind.PIERCE.emc)).multiply(BigInteger.TWO).toString());
        }
        BigInteger singularity=BlackholeModules.level(bow,Kind.SINGULARITY);
        if(singularity.signum()>0)arrow.getPersistentData().putString(SingularityEntity.ARROW_LEVEL,singularity.toString());
        if(free)arrow.pickup=AbstractArrow.Pickup.CREATIVE_ONLY;
        if(!world.addFreshEntity(arrow)){
            if(paid.signum()>0)com.primordial.service.EmcService.add(p.server,p.getUUID(),paid);
            return;
        }
        world.playSound(null,p.getX(),p.getY(),p.getZ(),SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,1,1/(world.random.nextFloat()*0.4F+1.2F)+power*0.5F);
        if(!free){ammo.shrink(1);if(ammo.isEmpty())p.getInventory().removeItem(ammo);}
        p.awardStat(Stats.ITEM_USED.get(this));
    }
}
