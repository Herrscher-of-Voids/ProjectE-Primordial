package com.primordial.neoforge.equipment;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeBlackholeEquipmentEvents {
    private static final ResourceLocation REACH_ID=ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID,"blackhole_reach");
    private static final ResourceLocation SPEED_ID=ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID,"blackhole_speed");
    private static final ResourceLocation KB_ID=ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID,"blackhole_knockback");
    private static final ResourceLocation DAMAGE_ID=ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID,"blackhole_damage");
    private static final ResourceLocation ATTACK_SPEED_ID=ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID,"blackhole_attack_speed");
    private static final Map<UUID,Flight> FLIGHTS=new HashMap<>();
    private static final Map<UUID,Map<Holder<MobEffect>,MobEffectInstance>> EFFECTS=new HashMap<>();
    private static final class Flight {final float speed;boolean granted;Flight(float speed){this.speed=speed;}}
    private NeoForgeBlackholeEquipmentEvents() { }
    public static double scale(BigInteger n){return NeoForgeBlackholeModules.magnitude(n);}
    @SubscribeEvent public static void attributes(ItemAttributeModifierEvent event){
        if(!NeoForgeBlackholeGear.isGear(event.getItemStack())||NeoForgeBlackholeGear.isArmor(event.getItemStack()))return;
        var item=event.getItemStack().getItem();
        double damage=item instanceof NeoForgeBlackholeTools.Katar?240:item instanceof NeoForgeBlackholeTools.Sword?200:
            item instanceof NeoForgeBlackholeTools.MorningStar?160:item instanceof NeoForgeBlackholeTools.Hammer?140:
            item instanceof NeoForgeBlackholeTools.Axe?120:item instanceof NeoForgeBlackholeTools.Pick?80:64;
        event.removeAllModifiersFor(Attributes.ATTACK_DAMAGE);event.removeAllModifiersFor(Attributes.ATTACK_SPEED);
        event.addModifier(Attributes.ATTACK_DAMAGE,new AttributeModifier(DAMAGE_ID,damage-1,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
        event.addModifier(Attributes.ATTACK_SPEED,new AttributeModifier(ATTACK_SPEED_ID,-1.6,AttributeModifier.Operation.ADD_VALUE),EquipmentSlotGroup.MAINHAND);
    }
    @SubscribeEvent public static void breakSpeed(PlayerEvent.BreakSpeed event){
        ItemStack stack=event.getEntity().getMainHandItem();
        if(NeoForgeBlackholeGear.isGear(stack)&&!NeoForgeBlackholeGear.isArmor(stack)&&!(stack.getItem() instanceof NeoForgeBlackholeBowItem))event.setNewSpeed(event.getNewSpeed()*4);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        if(!p.isAlive()){revokeFlight(p);clearEffects(p);NeoForgeBlackholeWorldActions.cancel(p);return;}
        flight(p);
        NeoForgeBlackholeWorldActions.tick(p);
        if(p.serverLevel().getGameTime()%20!=0)return;
        double speed=NeoForgeBlackholeGear.armorPower(p,Kind.SWIFT), kb=NeoForgeBlackholeGear.armorPower(p,Kind.KNOCKBACK);
        attribute(p.getAttribute(Attributes.MOVEMENT_SPEED),SPEED_ID,NeoForgeBlackholeGear.payArmor(p,Kind.SWIFT)?0.2*speed:0,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        attribute(p.getAttribute(Attributes.KNOCKBACK_RESISTANCE),KB_ID,NeoForgeBlackholeGear.payArmor(p,Kind.KNOCKBACK)?0.1*kb:0,AttributeModifier.Operation.ADD_VALUE);
        ItemStack held=p.getMainHandItem();BigInteger reach=NeoForgeBlackholeModules.level(held,Kind.REACH);
        double extra=NeoForgeBlackholeGear.payLevel(p,Kind.REACH,reach)?2*scale(reach):0;
        attribute(p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE),REACH_ID,extra,AttributeModifier.Operation.ADD_VALUE);
        attribute(p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE),REACH_ID,extra,AttributeModifier.Operation.ADD_VALUE);
        effect(p,Kind.FIREWARD,MobEffects.FIRE_RESISTANCE);effect(p,Kind.NIGHT_VISION,MobEffects.NIGHT_VISION);effect(p,Kind.BREATH,MobEffects.WATER_BREATHING);
        double heal=NeoForgeBlackholeGear.armorPower(p,Kind.HEAL);
        if(heal>0&&p.getHealth()<p.getMaxHealth()&&NeoForgeBlackholeGear.payArmor(p,Kind.HEAL))p.heal((float)Math.min(p.getMaxHealth()-p.getHealth(),heal));
        double food=NeoForgeBlackholeGear.armorPower(p,Kind.FEED);
        if(food>0&&p.getFoodData().getFoodLevel()<20&&NeoForgeBlackholeGear.payArmor(p,Kind.FEED))p.getFoodData().eat((int)Math.min(20-p.getFoodData().getFoodLevel(),food),0.5F);
        BigInteger magnet=NeoForgeBlackholeModules.level(held,Kind.MAGNET);
        if(NeoForgeBlackholeGear.payLevel(p,Kind.MAGNET,magnet))NeoForgeBlackholeWorldActions.magnet(p,scale(magnet)*4);
    }
    private static void effect(ServerPlayer p,Kind kind,Holder<MobEffect> effect){
        var owned=EFFECTS.computeIfAbsent(p.getUUID(),id->new HashMap<>());
        MobEffectInstance current=p.getEffect(effect),previous=owned.get(effect);
        boolean ours=current!=null&&current==previous&&current.getAmplifier()==0&&current.isAmbient();
        if(current!=null&&!ours){owned.remove(effect);return;}
        double power=NeoForgeBlackholeGear.armorPower(p,kind);
        if(power>0&&ours&&current.getDuration()>220)return;
        if(power>0&&NeoForgeBlackholeGear.payArmor(p,kind)){
            int duration=(int)Math.min(Integer.MAX_VALUE,240*power);
            p.addEffect(new MobEffectInstance(effect,duration,0,true,false));owned.put(effect,p.getEffect(effect));
        }else if(ours){p.removeEffect(effect);owned.remove(effect);}
        if(owned.isEmpty())EFFECTS.remove(p.getUUID());
    }
    private static void clearEffects(ServerPlayer p){
        var owned=EFFECTS.remove(p.getUUID());if(owned==null)return;
        owned.forEach((effect,instance)->{if(p.getEffect(effect)==instance&&instance.getAmplifier()==0&&instance.isAmbient())p.removeEffect(effect);});
    }

    private static void attribute(AttributeInstance a,ResourceLocation id,double value,AttributeModifier.Operation operation){
        if(a==null)return;AttributeModifier old=a.getModifier(id);
        if(old!=null&&old.amount()==value)return;
        if(old!=null)a.removeModifier(id);
        if(value>0)a.addTransientModifier(new AttributeModifier(id,value,operation));
    }
    private static void flight(ServerPlayer p){
        double power=NeoForgeBlackholeGear.armorPower(p,Kind.FLIGHT);
        if(p.isSpectator()||!NeoForgeBlackholeGear.fullSet(p)||!NeoForgeBlackholeGear.payArmor(p,Kind.FLIGHT)){revokeFlight(p);return;}
        Flight state=FLIGHTS.computeIfAbsent(p.getUUID(),id->new Flight(p.getAbilities().getFlyingSpeed()));
        boolean changed=false;
        if(!p.getAbilities().mayfly){p.getAbilities().mayfly=true;state.granted=true;changed=true;}
        float speed=NeoForgeBlackholeScaling.finiteFloat(0.075*power);
        if(p.getAbilities().getFlyingSpeed()!=speed){p.getAbilities().setFlyingSpeed(speed);changed=true;}
        if(changed)p.onUpdateAbilities();
    }
    private static void revokeFlight(ServerPlayer p){
        Flight state=FLIGHTS.remove(p.getUUID());if(state==null)return;
        p.getAbilities().setFlyingSpeed(state.speed);
        if(state.granted&&!p.isCreative()&&!p.isSpectator()){p.getAbilities().mayfly=false;p.getAbilities().flying=false;}
        p.onUpdateAbilities();
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void hurt(LivingIncomingDamageEvent event){
        if(event.getAmount()<=0||event.getEntity().level().isClientSide)return;
        if(event.getEntity() instanceof ServerPlayer p){
            double guard=NeoForgeBlackholeGear.armorPower(p,Kind.GUARD);
            if(NeoForgeBlackholeGear.payArmor(p,Kind.GUARD))event.setAmount((float)(event.getAmount()/(1+0.15*guard)));
        }
        if(!NeoForgeBlackholeWorldActions.secondary()&&event.getSource().getDirectEntity() instanceof ServerPlayer p){
            BigInteger assault=NeoForgeBlackholeModules.level(p.getMainHandItem(),Kind.ASSAULT);
            if(NeoForgeBlackholeGear.payLevel(p,Kind.ASSAULT,assault))event.setAmount((float)Math.min(Float.MAX_VALUE,event.getAmount()+6*scale(assault)));
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent.Post event){
        if(event.getNewDamage()<=0||NeoForgeBlackholeWorldActions.secondary()||!(event.getSource().getDirectEntity() instanceof ServerPlayer p))return;
        ItemStack held=p.getMainHandItem();BigInteger vamp=NeoForgeBlackholeModules.level(held,Kind.VAMPIRISM);
        if(p.getHealth()<p.getMaxHealth()&&NeoForgeBlackholeGear.payLevel(p,Kind.VAMPIRISM,vamp))
            p.heal((float)Math.min(p.getMaxHealth()-p.getHealth(),event.getNewDamage()*0.05*scale(vamp)));
        BigInteger sweep=NeoForgeBlackholeModules.level(held,Kind.SWEEP);
        if(NeoForgeBlackholeGear.payLevel(p,Kind.SWEEP,sweep))NeoForgeBlackholeWorldActions.sweep(p,event.getEntity(),2*scale(sweep),event.getNewDamage());
    }
    @SubscribeEvent public static void fall(LivingFallEvent event){
        if(event.getEntity() instanceof ServerPlayer p){double power=NeoForgeBlackholeGear.armorPower(p,Kind.FALL);
            if(event.getDistance()>3&&NeoForgeBlackholeGear.payArmor(p,Kind.FALL))event.setDamageMultiplier((float)(event.getDamageMultiplier()/(1+power)));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p){revokeFlight(p);clearEffects(p);NeoForgeBlackholeWorldActions.cancel(p);}}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        ItemStack gear=event.getItemStack();if(!NeoForgeBlackholeGear.isGear(gear))return;
        event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.open"));
        event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.unbreakable"));
        event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.innate"));
        if(NeoForgeBlackholeModules.innate(gear,Kind.REAP)||NeoForgeBlackholeModules.innate(gear,Kind.DASH))
            event.getToolTip().add(Component.translatable("tooltip.primordial.equipment.signature_key"));
        for(Kind kind:Kind.values())if(NeoForgeBlackholeModules.purchased(gear,kind))event.getToolTip().add(Component.translatable(
            "tooltip.primordial.equipment.level",Component.translatable("feature.primordial."+kind.id),NeoForgeBlackholeModules.level(gear,kind).toString(),NeoForgeBlackholeModules.owned(gear,kind).toString()));
    }
}
