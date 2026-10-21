/**
 * 【变更记录 #1】2026-10-14 02:35:54（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：冠付费标记附近敌人；铠付费抵挡致命伤、短暂无敌并救回虚空到最后安全位置，强制击杀不拦截。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 03:10:28（第 2 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：洞察半径与不灭保护时间均逐级翻倍，时钟溢出饱和而不回绕。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;
import com.primordial.PrimordialMod;
import com.primordial.equipment.BlackholeModules.Kind;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=PrimordialMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SignatureArmor {
    private static final String SAFE="BlackholeLastSafe",GRACE="BlackholeDeathGrace";
    private SignatureArmor() { }
    public static BigInteger level(ServerPlayer p,EquipmentSlot slot,Kind kind){return BlackholeModules.level(p.getItemBySlot(slot),kind);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p)||!p.isAlive())return;
        BigInteger undying=level(p,EquipmentSlot.CHEST,Kind.UNDYING);
        if(undying.signum()>0&&p.getY()<p.level().getMinBuildHeight()-8&&BlackholeGear.payLevel(p,Kind.UNDYING,undying)){
            rescue(p);protect(p,undying);
        }
        if(p.level().getGameTime()%20!=0)return;
        if(p.onGround()&&p.getY()>=p.level().getMinBuildHeight()&&!p.isInLava()){
            CompoundTag safe=new CompoundTag();safe.putString("Dimension",p.level().dimension().location().toString());
            safe.putDouble("X",p.getX());safe.putDouble("Y",p.getY());safe.putDouble("Z",p.getZ());p.getPersistentData().put(SAFE,safe);
        }
        BigInteger insight=level(p,EquipmentSlot.HEAD,Kind.INSIGHT);if(insight.signum()==0)return;
        double radius=BlackholeScaling.radius(insight,16);
        java.util.List<LivingEntity> targets=new java.util.ArrayList<>();
        for(Entity entity:p.serverLevel().getAllEntities())if(entity instanceof LivingEntity living&&entity instanceof Enemy
                &&entity.isAlive()&&!p.isAlliedTo(entity)&&p.distanceToSqr(entity)<=radius*radius)targets.add(living);
        if(!targets.isEmpty()&&BlackholeGear.payLevel(p,Kind.INSIGHT,insight))for(LivingEntity target:targets)
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING,25,0,true,false));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fatal(LivingDeathEvent event){
        if(!(event.getEntity() instanceof ServerPlayer p)||event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY))return;
        BigInteger level=level(p,EquipmentSlot.CHEST,Kind.UNDYING);
        if(BlackholeGear.payLevel(p,Kind.UNDYING,level)){event.setCanceled(true);p.setHealth(p.getMaxHealth());p.clearFire();protect(p,level);}
    }
    private static void protect(ServerPlayer p,BigInteger level){
        p.getPersistentData().putLong(GRACE,BlackholeScaling.deadline(p.level().getGameTime(),40,level));p.fallDistance=0;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void grace(LivingAttackEvent event){
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
