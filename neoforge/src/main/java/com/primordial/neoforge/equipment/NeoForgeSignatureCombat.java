package com.primordial.neoforge.equipment;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeSignatureCombat {
    private static final String STASIS="BlackholeStasis",REND="BlackholeRend";
    private NeoForgeSignatureCombat() { }
    private static boolean allowed(ServerPlayer owner,LivingEntity target){return owner!=target&&!owner.isAlliedTo(target)
        &&(!(target instanceof Player p)||!p.isCreative()&&!p.isSpectator()&&owner.canHarmPlayer(p))
        &&(!(target instanceof TamableAnimal pet)||!pet.isOwnedBy(owner));}
    @SubscribeEvent(priority=EventPriority.LOW) public static void execute(LivingIncomingDamageEvent event){
        if(NeoForgeBlackholeWorldActions.secondary()||event.getAmount()<=0||!(event.getSource().getDirectEntity() instanceof ServerPlayer p)||!allowed(p,event.getEntity()))return;
        BigInteger level=NeoForgeBlackholeModules.level(p.getMainHandItem(),Kind.EXECUTE);
        if(event.getEntity().getHealth()<=event.getEntity().getMaxHealth()*0.25F&&NeoForgeBlackholeGear.payLevel(p,Kind.EXECUTE,level))
            event.setAmount((float)Math.min(Float.MAX_VALUE,event.getAmount()+event.getEntity().getMaxHealth()*0.25*NeoForgeBlackholeModules.magnitude(level)));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent.Post event){
        if(NeoForgeBlackholeWorldActions.secondary()||event.getNewDamage()<=0)return;
        if(event.getEntity() instanceof ServerPlayer defender&&event.getSource().getEntity() instanceof LivingEntity attacker&&allowed(defender,attacker)){
            BigInteger level=NeoForgeSignatureArmor.level(defender,EquipmentSlot.LEGS,Kind.STASIS);
            if(NeoForgeBlackholeGear.payLevel(defender,Kind.STASIS,level)){
                CompoundTag tag=new CompoundTag();tag.putString("Dimension",attacker.level().dimension().location().toString());
                tag.putLong("Until",NeoForgeBlackholeScaling.deadline(attacker.level().getGameTime(),40,level));
                tag.putDouble("X",attacker.getX());tag.putDouble("Y",attacker.getY());tag.putDouble("Z",attacker.getZ());attacker.getPersistentData().put(STASIS,tag);
            }
        }
        if(!(event.getSource().getDirectEntity() instanceof ServerPlayer p)||!allowed(p,event.getEntity()))return;
        LivingEntity target=event.getEntity();BigInteger dispel=NeoForgeBlackholeModules.level(p.getMainHandItem(),Kind.DISPEL);
        if(dispel.signum()>0){
            var effects=target.getActiveEffects().stream().filter(effect->effect.getEffect().value().isBeneficial()).toList();
            int count=NeoForgeBlackholeScaling.boundedPower(dispel,BigInteger.valueOf(effects.size())).intValue();
            if(count>0&&NeoForgeBlackholeGear.payLevel(p,Kind.DISPEL,dispel))for(int i=0;i<count;i++)target.removeEffect(effects.get(i).getEffect());
        }
        BigInteger rend=NeoForgeBlackholeModules.level(p.getMainHandItem(),Kind.REND);
        if(NeoForgeBlackholeGear.payLevel(p,Kind.REND,rend)){
            CompoundTag tag=new CompoundTag();tag.putUUID("Owner",p.getUUID());tag.putString("Power",rend.toString());
            tag.putLong("Until",NeoForgeBlackholeScaling.deadline(target.level().getGameTime(),100,rend));target.getPersistentData().put(REND,tag);
        }
    }
    public static boolean frozen(LivingEntity entity){
        CompoundTag tag=entity.getPersistentData().getCompound(STASIS);
        boolean active=!(entity instanceof Player p&&(p.isCreative()||p.isSpectator()))&&tag.getLong("Until")>entity.level().getGameTime()&&tag.getString("Dimension").equals(entity.level().dimension().location().toString());
        if(!active)entity.getPersistentData().remove(STASIS);return active;
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event){
        if(!(event.getEntity() instanceof LivingEntity target)||!(target.level() instanceof ServerLevel world)||!target.isAlive())return;
        CompoundTag rend=target.getPersistentData().getCompound(REND);
        if(rend.getLong("Until")<=world.getGameTime())target.getPersistentData().remove(REND);
        else if(world.getGameTime()%20==0&&rend.hasUUID("Owner")){
            ServerPlayer owner=world.getServer().getPlayerList().getPlayer(rend.getUUID("Owner"));BigInteger level=NeoForgeBlackholeModules.parse(rend.getString("Power"));
            if(owner!=null&&owner.level()==world&&level!=null&&allowed(owner,target))NeoForgeBlackholeWorldActions.secondaryDamage(owner,target,(float)Math.min(Float.MAX_VALUE,2*NeoForgeBlackholeModules.magnitude(level)));
        }
        if(frozen(target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))){
            CompoundTag tag=target.getPersistentData().getCompound(STASIS);target.setDeltaMovement(Vec3.ZERO);
            if(target instanceof ServerPlayer player)player.connection.teleport(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"),player.getYRot(),player.getXRot());
            else{target.setPos(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));event.setCanceled(true);}
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event){if(!event.getEntity().level().isClientSide&&frozen(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void rightBlock(PlayerInteractEvent.RightClickBlock event){cancelInteraction(event,event);}
    @SubscribeEvent public static void rightItem(PlayerInteractEvent.RightClickItem event){cancelInteraction(event,event);}
    @SubscribeEvent public static void entityInteract(PlayerInteractEvent.EntityInteract event){cancelInteraction(event,event);}
    @SubscribeEvent public static void entityInteractAt(PlayerInteractEvent.EntityInteractSpecific event){cancelInteraction(event,event);}
    @SubscribeEvent public static void leftBlock(PlayerInteractEvent.LeftClickBlock event){cancelInteraction(event,event);}
    private static void cancelInteraction(PlayerInteractEvent event,ICancellableEvent cancellable){if(!event.getEntity().level().isClientSide&&frozen(event.getEntity()))cancellable.setCanceled(true);}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event){if(frozen(event.getPlayer()))event.setCanceled(true);}
}
