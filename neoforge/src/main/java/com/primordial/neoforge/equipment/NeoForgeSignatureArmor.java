package com.primordial.neoforge.equipment;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeSignatureArmor {
    private static final String SAFE="BlackholeLastSafe",GRACE="BlackholeDeathGrace";
    private NeoForgeSignatureArmor() { }
    public static BigInteger level(ServerPlayer p,EquipmentSlot slot,Kind kind){return NeoForgeBlackholeModules.level(p.getItemBySlot(slot),kind);}
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!p.isAlive())return;
        BigInteger undying=level(p,EquipmentSlot.CHEST,Kind.UNDYING);
        if(undying.signum()>0&&p.getY()<p.level().getMinBuildHeight()-8&&NeoForgeBlackholeGear.payLevel(p,Kind.UNDYING,undying)){
            rescue(p);protect(p,undying);
        }
        if(p.level().getGameTime()%20!=0)return;
        if(p.onGround()&&p.getY()>=p.level().getMinBuildHeight()&&!p.isInLava()){
            CompoundTag safe=new CompoundTag();safe.putString("Dimension",p.level().dimension().location().toString());
            safe.putDouble("X",p.getX());safe.putDouble("Y",p.getY());safe.putDouble("Z",p.getZ());p.getPersistentData().put(SAFE,safe);
        }
        BigInteger insight=level(p,EquipmentSlot.HEAD,Kind.INSIGHT);if(insight.signum()==0)return;
        double radius=NeoForgeBlackholeScaling.radius(insight,16);
        java.util.List<LivingEntity> targets=new java.util.ArrayList<>();
        for(Entity entity:p.serverLevel().getAllEntities())if(entity instanceof LivingEntity living&&entity instanceof Enemy
                &&entity.isAlive()&&!p.isAlliedTo(entity)&&p.distanceToSqr(entity)<=radius*radius)targets.add(living);
        if(!targets.isEmpty()&&NeoForgeBlackholeGear.payLevel(p,Kind.INSIGHT,insight))for(LivingEntity target:targets)
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING,25,0,true,false));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fatal(LivingDeathEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        BigInteger level=level(p,EquipmentSlot.CHEST,Kind.UNDYING);
        if(NeoForgeBlackholeGear.payLevel(p,Kind.UNDYING,level)){event.setCanceled(true);p.setHealth(p.getMaxHealth());p.clearFire();protect(p,level);}
    }
    private static void protect(ServerPlayer p,BigInteger level){
        p.getPersistentData().putLong(GRACE,NeoForgeBlackholeScaling.deadline(p.level().getGameTime(),40,level));p.fallDistance=0;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void grace(LivingIncomingDamageEvent event){
        if(event.getEntity() instanceof ServerPlayer p&&level(p,EquipmentSlot.CHEST,Kind.UNDYING).signum()>0
                &&p.getPersistentData().getLong(GRACE)>p.level().getGameTime()&&!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))event.setCanceled(true);
    }
    private static void rescue(ServerPlayer p){
        CompoundTag safe=p.getPersistentData().getCompound(SAFE);ServerLevel world=p.serverLevel();
        Vec3 target=new Vec3(safe.getDouble("X"),safe.getDouble("Y"),safe.getDouble("Z"));
        BlockPos pos=BlockPos.containing(target);
        boolean usable=world.dimension().location().toString().equals(safe.getString("Dimension"))&&world.hasChunkAt(pos)
                &&world.getWorldBorder().isWithinBounds(pos)&&world.getBlockState(pos.below()).isSolid()
                &&world.noCollision(p,p.getBoundingBox().move(target.subtract(p.position())));
        if(!usable){world=p.server.overworld();pos=world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,world.getSharedSpawnPos()).above();target=Vec3.atBottomCenterOf(pos);}
        p.teleportTo(world,target.x,target.y,target.z,p.getYRot(),p.getXRot());p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
    }
}
