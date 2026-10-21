/**
 * 【文件职责】服务端执行黑洞装备模块的防护、伤害、速度、抗火、修复、范围和飞行能力。
 * 【变更记录 #1】2026-10-14 00:59:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增每项能力的实际使用时扣费与余额不足停用逻辑。
 * - 修改：飞行仅全套护甲可启用，移除装备或断费时撤销本模组授予的飞行权限；切换游戏模式保留权限归属记录。
 * - 用途：所有模块效果由服务端主线程判定并复用 EmcService，避免客户端和多人免费生效。
 */
/**
 * 【变更记录 #2】2026-10-14 01:18:08（第 2 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：实体模块物品关联。
 * - 新增：装备内购买功能关联。
 * - 修改：事件使用已购且开启的功能；装备提示列出功能及开关状态。
 * - 用途：功能由装备界面 EMC 解锁，避免继续提供旧实物安装入口。
 */
/**
 * 【变更记录 #3】2026-10-14 01:43:26（第 3 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：基础攻击64–240、九工具挖速四倍、按等级生效的防护/迅捷/飞行/触及/回血/饱食/击退/掉落/磁吸/范围攻击/吸血与三项生存效果；无修补路径。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #4】2026-10-14 02:49:18（第 4 次备注 · 1.2.0-signature-final-validation）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：物品说明标注自带1级能力与主动技能键，等级显示改为包含基础能力的总等级。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #5】2026-10-14 03:10:28（第 5 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：四件护甲按各自指数倍率加算，飞行/迅捷/吸血/伤害/回复按指数成长；药水按指数服务周期扣费，关闭时清理自身效果。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;
import com.primordial.PrimordialMod;
import com.primordial.equipment.BlackholeModules.Kind;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=PrimordialMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackholeEquipmentEvents {
    private static final UUID REACH_ID=UUID.fromString("b3f2cce7-0c1b-4c16-8b06-7b99f0f88cb4");
    private static final UUID SPEED_ID=UUID.fromString("9f9d2741-556d-48bf-bb13-95f4e569ec6e");
    private static final UUID KB_ID=UUID.fromString("9b4712dc-113e-4c7c-80ab-1bc9e018bdcc");
    private static final UUID DAMAGE_ID=UUID.fromString("b9168c2c-0116-4bce-8ecf-6c901c0e5ee4");
    private static final UUID ATTACK_SPEED_ID=UUID.fromString("f43a4b70-6524-4e90-acd1-69028004b6a7");
    private static final Map<UUID,Flight> FLIGHTS=new HashMap<>();
    private static final Map<UUID,Map<MobEffect,MobEffectInstance>> EFFECTS=new HashMap<>();
    private static final class Flight {final float speed;boolean granted;Flight(float speed){this.speed=speed;}}
    private BlackholeEquipmentEvents() { }
    public static double scale(BigInteger n){return BlackholeModules.magnitude(n);}
    @SubscribeEvent public static void attributes(ItemAttributeModifierEvent event){
        if(event.getSlotType()!=EquipmentSlot.MAINHAND||!BlackholeGear.isGear(event.getItemStack())||BlackholeGear.isArmor(event.getItemStack()))return;
        var item=event.getItemStack().getItem();
        double damage=item instanceof BlackholeTools.Katar?240:item instanceof BlackholeTools.Sword?200:
            item instanceof BlackholeTools.MorningStar?160:item instanceof BlackholeTools.Hammer?140:
            item instanceof BlackholeTools.Axe?120:item instanceof BlackholeTools.Pick?80:64;
        event.removeAttribute(Attributes.ATTACK_DAMAGE);event.removeAttribute(Attributes.ATTACK_SPEED);
        event.addModifier(Attributes.ATTACK_DAMAGE,new AttributeModifier(DAMAGE_ID,"Blackhole base damage",damage-1,AttributeModifier.Operation.ADDITION));
        event.addModifier(Attributes.ATTACK_SPEED,new AttributeModifier(ATTACK_SPEED_ID,"Blackhole attack speed",-1.6,AttributeModifier.Operation.ADDITION));
    }
    @SubscribeEvent public static void breakSpeed(PlayerEvent.BreakSpeed event){
        ItemStack stack=event.getEntity().getMainHandItem();
        if(BlackholeGear.isGear(stack)&&!BlackholeGear.isArmor(stack)&&!(stack.getItem() instanceof BlackholeBowItem))event.setNewSpeed(event.getNewSpeed()*4);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        if(!p.isAlive()){revokeFlight(p);clearEffects(p);BlackholeWorldActions.cancel(p);return;}
        flight(p);
        BlackholeWorldActions.tick(p);
        if(p.serverLevel().getGameTime()%20!=0)return;
        double speed=BlackholeGear.armorPower(p,Kind.SWIFT), kb=BlackholeGear.armorPower(p,Kind.KNOCKBACK);
        attribute(p.getAttribute(Attributes.MOVEMENT_SPEED),SPEED_ID,BlackholeGear.payArmor(p,Kind.SWIFT)?0.2*speed:0,AttributeModifier.Operation.MULTIPLY_TOTAL);
        attribute(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE),KB_ID,BlackholeGear.payArmor(p,Kind.KNOCKBACK)?0.1*kb:0,AttributeModifier.Operation.ADDITION);
        ItemStack held=p.getMainHandItem();BigInteger reach=BlackholeModules.level(held,Kind.REACH);
        double extra=BlackholeGear.payLevel(p,Kind.REACH,reach)?2*scale(reach):0;
        attribute(p.getAttribute(ForgeMod.BLOCK_REACH.get()),REACH_ID,extra,AttributeModifier.Operation.ADDITION);
        attribute(p.getAttribute(ForgeMod.ENTITY_REACH.get()),REACH_ID,extra,AttributeModifier.Operation.ADDITION);
        effect(p,Kind.FIREWARD,MobEffects.FIRE_RESISTANCE);effect(p,Kind.NIGHT_VISION,MobEffects.NIGHT_VISION);effect(p,Kind.BREATH,MobEffects.WATER_BREATHING);
        double heal=BlackholeGear.armorPower(p,Kind.HEAL);
        if(heal>0&&p.getHealth()<p.getMaxHealth()&&BlackholeGear.payArmor(p,Kind.HEAL))p.heal((float)Math.min(p.getMaxHealth()-p.getHealth(),heal));
        double food=BlackholeGear.armorPower(p,Kind.FEED);
        if(food>0&&p.getFoodData().getFoodLevel()<20&&BlackholeGear.payArmor(p,Kind.FEED))p.getFoodData().eat((int)Math.min(20-p.getFoodData().getFoodLevel(),food),0.5F);
        BigInteger magnet=BlackholeModules.level(held,Kind.MAGNET);
        if(BlackholeGear.payLevel(p,Kind.MAGNET,magnet))BlackholeWorldActions.magnet(p,scale(magnet)*4);
    }
    private static void effect(ServerPlayer p,Kind kind,MobEffect effect){
        var owned=EFFECTS.computeIfAbsent(p.getUUID(),id->new HashMap<>());
        MobEffectInstance current=p.getEffect(effect),previous=owned.get(effect);
        boolean ours=current!=null&&current==previous&&current.getAmplifier()==0&&current.isAmbient();
        if(current!=null&&!ours){owned.remove(effect);return;}
        double power=BlackholeGear.armorPower(p,kind);
        if(power>0&&ours&&current.getDuration()>220)return;
        if(power>0&&BlackholeGear.payArmor(p,kind)){
            int duration=(int)Math.min(Integer.MAX_VALUE,240*power);
            p.addEffect(new MobEffectInstance(effect,duration,0,true,false));owned.put(effect,p.getEffect(effect));
        }else if(ours){p.removeEffect(effect);owned.remove(effect);}
        if(owned.isEmpty())EFFECTS.remove(p.getUUID());
    }
    private static void clearEffects(ServerPlayer p){
        var owned=EFFECTS.remove(p.getUUID());if(owned==null)return;
        owned.forEach((effect,instance)->{if(p.getEffect(effect)==instance&&instance.getAmplifier()==0&&instance.isAmbient())p.removeEffect(effect);});
    }

    private static void attribute(AttributeInstance a,UUID id,double value,AttributeModifier.Operation operation){
        if(a==null)return;AttributeModifier old=a.getModifier(id);
        if(old!=null&&old.getAmount()==value)return;
        if(old!=null)a.removeModifier(id);
        if(value>0)a.addTransientModifier(new AttributeModifier(id,"Blackhole feature",value,operation));
    }
    private static void flight(ServerPlayer p){
        double power=BlackholeGear.armorPower(p,Kind.FLIGHT);
        if(p.isSpectator()||!BlackholeGear.fullSet(p)||!BlackholeGear.payArmor(p,Kind.FLIGHT)){revokeFlight(p);return;}
        Flight state=FLIGHTS.computeIfAbsent(p.getUUID(),id->new Flight(p.getAbilities().getFlyingSpeed()));
        boolean changed=false;
        if(!p.getAbilities().mayfly){p.getAbilities().mayfly=true;state.granted=true;changed=true;}
        float speed=BlackholeScaling.finiteFloat(0.075*power);
        if(p.getAbilities().getFlyingSpeed()!=speed){p.getAbilities().setFlyingSpeed(speed);changed=true;}
        if(changed)p.onUpdateAbilities();
    }
    private static void revokeFlight(ServerPlayer p){
        Flight state=FLIGHTS.remove(p.getUUID());if(state==null)return;
        p.getAbilities().setFlyingSpeed(state.speed);
        if(state.granted&&!p.isCreative()&&!p.isSpectator()){p.getAbilities().mayfly=false;p.getAbilities().flying=false;}
        p.onUpdateAbilities();
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void hurt(LivingHurtEvent event){
        if(event.getAmount()<=0||event.getEntity().level().isClientSide)return;
        if(event.getEntity() instanceof ServerPlayer p){
            double guard=BlackholeGear.armorPower(p,Kind.GUARD);
            if(BlackholeGear.payArmor(p,Kind.GUARD))event.setAmount((float)(event.getAmount()/(1+0.15*guard)));
        }
        if(!BlackholeWorldActions.secondary()&&event.getSource().getDirectEntity() instanceof ServerPlayer p){
            BigInteger assault=BlackholeModules.level(p.getMainHandItem(),Kind.ASSAULT);
            if(BlackholeGear.payLevel(p,Kind.ASSAULT,assault))event.setAmount((float)Math.min(Float.MAX_VALUE,event.getAmount()+6*scale(assault)));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event){
        if(event.getAmount()<=0||BlackholeWorldActions.secondary()||!(event.getSource().getDirectEntity() instanceof ServerPlayer p))return;
        ItemStack held=p.getMainHandItem();BigInteger vamp=BlackholeModules.level(held,Kind.VAMPIRISM);
        if(p.getHealth()<p.getMaxHealth()&&BlackholeGear.payLevel(p,Kind.VAMPIRISM,vamp))
            p.heal((float)Math.min(p.getMaxHealth()-p.getHealth(),Math.min(event.getAmount(),event.getEntity().getHealth())*0.05*scale(vamp)));
        BigInteger sweep=BlackholeModules.level(held,Kind.SWEEP);
        if(BlackholeGear.payLevel(p,Kind.SWEEP,sweep))BlackholeWorldActions.sweep(p,event.getEntity(),2*scale(sweep),event.getAmount());
    }
    @SubscribeEvent public static void fall(LivingFallEvent event){
        if(event.getEntity() instanceof ServerPlayer p){double power=BlackholeGear.armorPower(p,Kind.FALL);
            if(event.getDistance()>3&&BlackholeGear.payArmor(p,Kind.FALL))event.setDamageMultiplier((float)(event.getDamageMultiplier()/(1+power)));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p){revokeFlight(p);clearEffects(p);BlackholeWorldActions.cancel(p);}}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        ItemStack gear=event.getItemStack();if(!BlackholeGear.isGear(gear))return;
        event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.open"));
        event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.unbreakable"));
        event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.innate"));
        if(BlackholeModules.innate(gear,Kind.REAP)||BlackholeModules.innate(gear,Kind.DASH))
            event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.signature_key"));
        for(Kind kind:Kind.values())if(BlackholeModules.purchased(gear,kind))event.getToolTip().add(Component.translatable(
            "tooltip.primordial.equipment.level",Component.translatable("feature.primordial."+kind.id),BlackholeModules.level(gear,kind).toString(),BlackholeModules.owned(gear,kind).toString()));
    }
}
