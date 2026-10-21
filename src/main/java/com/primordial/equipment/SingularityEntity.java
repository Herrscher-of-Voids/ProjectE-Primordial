/**
 * 【变更记录 #1】2026-10-14 02:21:23（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：弓首次碰撞按等级付费生成5秒黑洞；吸附已加载实体，逐刻卷起最多8个可破坏方块，保留出生点/领地事件/容器保护，不使用粒子。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 02:49:18（第 2 次备注 · 1.2.0-signature-final-validation）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：黑洞核心伤害使用独立技能伤害上下文，避免再次触发手持武器模块和额外扣费。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #3】2026-10-14 03:11:54（第 3 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：黑洞半径8/16/32、寿命5/10/20秒、引力和核心伤害指数增长；大整数剩余寿命保存重载，移除随寿命增长的访问集合。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
/**
 * 【变更记录 #4】2026-10-14 03:32:08（第 4 次备注 · 1.2.0-singularity-animation）
 * - 删除：黑洞瞬间完整出现、寿命结束直接消失的视觉路径。
 * - 新增：12刻展开、寿命最后20刻坍缩的有界同步状态；中途进入范围和存档重载读取当前阶段，所有者失效时停止破坏并收尾。
 * - 修改：服务端同步出生进度与末段寿命，客户端插值三维模型缩放和透明度。
 * - 用途：黑洞展开、坍缩均随实际寿命；保留指数寿命与原EMC结算。
 */
/**
 * 【变更记录 #5】2026-10-14 03:42:00（第 5 次备注 · 1.2.0-linear-equipment-cost）
 * - 删除：黑洞寿命由已付 EMC 倍率推导、随技能等级翻倍的路径。
 * - 新增：所有新生成黑洞固定 600 游戏刻（30 秒）寿命；技能等级只影响半径、引力和伤害，费用由统一线性结算器负责。
 * - 修改：存档读取将未进入收尾阶段的寿命限制在本次 30 秒生命周期内，兼容旧存档且不延长已过去的时间。
 * - 用途：让黑洞持续时间稳定可预期，避免高等级费用和寿命同时失控；保留 20 刻的消失动画收尾。
 */
/**
 * 【变更记录 #6】2026-10-14 05:11:45（第 6 次备注 · 1.2.0-singularity-momentum-crash）
 * - 删除：黑洞引力把指数强度直接叠加到实体速度的无界路径。
 * - 新增：引力加速度与单刻位移边界；旧存档中异常高速掉落物加载时恢复安全速度。
 * - 修改：保留等级对作用范围和核心伤害的成长，引力接近中心时不再把实体甩出范围。
 * - 用途：防止实体移动查询溢出并修复已受影响的末地存档。
 */
/**
 * 【变更记录 #7】2026-10-14 16:01:56（第 7 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：中心无掉落秒杀并回收实体/物品；成功卷起方块按原方块回收价入账；同账户融合剩余寿命与速度相加、半径体积相加并持久化，安全位移边界保留。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
/**
 * 【变更记录 #8】2026-10-14 16:08:40（第 8 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：在已加载实体迭代器内部直接移除供体和回收目标。
 * - 新增：每刻有限候选列表，遍历完成后移除并结算。
 * - 修改：融合速度影响拉力与方块吞噬预算，位移仍受引擎安全边界保护。
 * - 用途：避免移除回调修改世界实体集合导致迭代失效，防止重复吸收。
 */
package com.primordial.equipment;
import com.primordial.PrimordialMod;
import com.primordial.equipment.BlackholeModules.Kind;
import com.primordial.registry.ModEntities;
import com.primordial.service.EmcService;
import com.primordial.service.EntityEmc;
import com.primordial.service.EmcAmount;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

@Mod.EventBusSubscriber(modid=PrimordialMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SingularityEntity extends Entity {
    public static final String ARROW_LEVEL="BlackholeImpactLevel";
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(SingularityEntity.class,EntityDataSerializers.FLOAT);
    public static final int OPEN_TICKS=12,CLOSE_TICKS=20,LIFETIME_TICKS=600;
    private static final double MAX_PULL_SPEED=2.5,MAX_PULL_ACCELERATION=1.25;
    private static final EntityDataAccessor<Integer> OPEN_AGE=SynchedEntityData.defineId(SingularityEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> END_TICKS=SynchedEntityData.defineId(SingularityEntity.class,EntityDataSerializers.INT);
    private boolean closing;
    private UUID owner;private BigInteger power=BigInteger.ONE,remainingTicks=BigInteger.valueOf(LIFETIME_TICKS);private int age;
    private BlackholeBlockCursor terrain;
    private double fusionMass=1,mergedForce;
    public BigInteger remainingTicks(){return remainingTicks;}
    public double absorptionSpeed(){return fusionMass;}
    public UUID owner(){return owner;}
    public static float mergedRadius(double a,double b){return (float)Math.min(60000000,Math.cbrt(a*a*a+b*b*b));}
    /** 同账户中心进入任一吸收范围才融合，UUID 较小者保留；先移除供体避免同刻再次结算。 */
    private void mergeNearby(ServerLevel world){
        List<SingularityEntity> candidates=new ArrayList<>();
        for(Entity entity:world.getAllEntities()){
            if(!(entity instanceof SingularityEntity other)||other==this||other.isRemoved()||other.closing||!Objects.equals(owner,other.owner)
                    ||getUUID().compareTo(other.getUUID())>=0||other.remainingTicks.signum()<=0)continue;
            if(position().distanceTo(other.position())>Math.max(radius(),other.radius()))continue;
            candidates.add(other);if(candidates.size()>=64)break;
        }
        for(SingularityEntity other:candidates){
            if(other.isRemoved())continue;
            float combined=mergedRadius(radius(),other.radius());
            double force=Math.min(60000000,pullForce()+other.pullForce());
            BigInteger life=remainingTicks.add(other.remainingTicks);double mass=Math.min(1e12,fusionMass+other.fusionMass);
            other.discard();remainingTicks=life;fusionMass=mass;mergedForce=force;entityData.set(RADIUS,combined);terrain=null;
        }
    }
    private double pullForce(){return mergedForce>0?mergedForce:Math.min(60000000,0.16*BlackholeModules.magnitude(power));}
    public SingularityEntity(EntityType<? extends SingularityEntity> type,Level level){super(type,level);noPhysics=true;}
    public static float radius(BigInteger level){return (float)BlackholeScaling.radius(level,8);}
    public float radius(){return entityData.get(RADIUS);}
    public int age(){return age;}
    public float openingAge(float partial){return Math.min(OPEN_TICKS,entityData.get(OPEN_AGE)+partial);}
    public float endingTicks(float partial){int left=entityData.get(END_TICKS);return left>CLOSE_TICKS?CLOSE_TICKS:Math.max(0,left-partial);}
    private void syncAnimation(){
        entityData.set(OPEN_AGE,Math.min(OPEN_TICKS,age));
        entityData.set(END_TICKS,remainingTicks.max(BigInteger.ZERO).min(BigInteger.valueOf(CLOSE_TICKS+1)).intValue());
    }
    @Override protected void defineSynchedData(){entityData.define(RADIUS,8F);entityData.define(OPEN_AGE,0);entityData.define(END_TICKS,CLOSE_TICKS+1);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;age=Math.max(0,tag.getInt("Age"));closing=tag.getBoolean("Closing");
        String life=tag.getString("RemainingTicks");
        try{remainingTicks=life.isEmpty()?BigInteger.valueOf(Math.max(0,LIFETIME_TICKS-age)):life.length()<=8192?new BigInteger(life):BigInteger.ZERO;}catch(NumberFormatException bad){remainingTicks=BigInteger.ZERO;}
        double mass=tag.getDouble("FusionMass");fusionMass=Double.isFinite(mass)&&mass>=1?Math.min(1e12,mass):1;
        double force=tag.getDouble("MergedForce");mergedForce=Double.isFinite(force)&&force>0?Math.min(60000000,force):0;
        if(!closing&&fusionMass<=1)remainingTicks=remainingTicks.min(BigInteger.valueOf(Math.max(0,LIFETIME_TICKS-age)));
        BigInteger saved=BlackholeModules.parse(tag.getString("Power"));power=saved==null||saved.signum()==0?BigInteger.ONE:saved;float savedRadius=tag.getFloat("MergedRadius");entityData.set(RADIUS,fusionMass>1&&Float.isFinite(savedRadius)&&savedRadius>0?Math.min(60000000,savedRadius):radius(power));syncAnimation();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){if(owner!=null)tag.putUUID("Owner",owner);tag.putInt("Age",age);tag.putString("Power",power.toString());tag.putString("RemainingTicks",remainingTicks.toString());tag.putBoolean("Closing",closing);tag.putDouble("FusionMass",fusionMass);tag.putDouble("MergedForce",mergedForce);tag.putFloat("MergedRadius",radius());}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void impact(ProjectileImpactEvent event){
        if(event.isCanceled()||!(event.getProjectile() instanceof AbstractArrow arrow)||!(arrow.level() instanceof ServerLevel world)
                ||event.getRayTraceResult().getType()==HitResult.Type.MISS||!(arrow.getOwner() instanceof ServerPlayer p))return;
        String saved=arrow.getPersistentData().getString(ARROW_LEVEL);BigInteger level=BlackholeModules.parse(saved);
        if(level==null||level.signum()==0)return;
        arrow.getPersistentData().remove(ARROW_LEVEL); // 穿透箭也只允许首次碰撞生成一个黑洞。
        if(!p.isAlive())return;
        BigInteger fee=BlackholeGear.charge(p,Kind.SINGULARITY,level);if(fee.signum()==0)return;
        SingularityEntity hole=new SingularityEntity(ModEntities.SINGULARITY.get(),world);
        hole.owner=p.getUUID();hole.power=level;hole.remainingTicks=BigInteger.valueOf(LIFETIME_TICKS);hole.entityData.set(RADIUS,radius(level));hole.setPos(event.getRayTraceResult().getLocation());hole.syncAnimation();
        if(!world.addFreshEntity(hole))EmcService.add(p.server,p.getUUID(),fee);
    }
    @SubscribeEvent public static void restoreItemMomentum(EntityJoinLevelEvent event){
        if(event.getLevel().isClientSide()||!(event.getEntity() instanceof ItemEntity item))return;
        Vec3 motion=item.getDeltaMovement();double speedSquared=motion.lengthSqr();
        if(!Double.isFinite(speedSquared))item.setDeltaMovement(Vec3.ZERO);
        else if(speedSquared>64*64)item.setDeltaMovement(motion.normalize().scale(MAX_PULL_SPEED));
    }
    public static Vec3 boundedPullVelocity(Vec3 motion,Vec3 toward,double force){
        return boundedPullVelocity(motion,toward,force,1);
    }
    public static Vec3 boundedPullVelocity(Vec3 motion,Vec3 toward,double force,double mass){
        double speed=Math.min(8,MAX_PULL_SPEED*Math.sqrt(Math.max(1,mass))),accel=Math.min(4,MAX_PULL_ACCELERATION*Math.sqrt(Math.max(1,mass)));
        double distance=toward.length();
        if(!Double.isFinite(distance)||distance<0.01)return Vec3.ZERO;
        double speedSquared=motion.lengthSqr();
        if(!Double.isFinite(speedSquared))motion=Vec3.ZERO;
        else if(speedSquared>speed*speed)motion=motion.normalize().scale(speed);
        double acceleration=Math.min(accel,force*distance/(distance+1));
        Vec3 pulled=motion.scale(0.85).add(toward.scale(acceleration/distance));
        double maxSpeed=Math.min(speed,distance);
        return pulled.lengthSqr()>maxSpeed*maxSpeed?pulled.normalize().scale(maxSpeed):pulled;
    }
    @Override public void tick(){
        super.tick();if(age<Integer.MAX_VALUE)age++;
        if(level().isClientSide)return;
        ServerLevel world=(ServerLevel)level();ServerPlayer player=owner==null?null:world.getServer().getPlayerList().getPlayer(owner);
        // 所有者离开时停止作用，留下最多1秒的纯视觉收尾；正常寿命的末20刻直接用于收缩，不额外收费或延寿。
        if(!closing&&(player==null||!player.isAlive()||player.level()!=world)){
            closing=true;remainingTicks=remainingTicks.min(BigInteger.valueOf(CLOSE_TICKS));syncAnimation();return;
        }
        if(!closing)mergeNearby(world);
        remainingTicks=remainingTicks.subtract(BigInteger.ONE);syncAnimation();
        if(remainingTicks.signum()<=0){discard();return;}
        if(closing)return;
        double r=radius(),force=pullForce();
        EmcAmount earned=EmcAmount.ZERO;int converted=0;
        List<Entity> victims=new ArrayList<>();
        // 只遍历本维度已加载实体；即便高等级范围很大，也不会请求加载区块。
        for(Entity entity:world.getAllEntities()){
            if(entity==this||entity==player||entity instanceof SingularityEntity||entity instanceof SingularityDebrisEntity
                    ||!entity.isAlive()||entity.isSpectator()||entity instanceof Player
                    ||entity instanceof TamableAnimal pet&&pet.isOwnedBy(player)||player.isAlliedTo(entity))continue;
            Vec3 delta=position().subtract(entity.position().add(0,entity.getBbHeight()*0.4,0));double distance=delta.length();
            if(distance>r)continue;
            entity.setDeltaMovement(boundedPullVelocity(entity.getDeltaMovement(),delta,force,fusionMass));entity.hurtMarked=true;
            if(distance<1.6&&victims.size()<64)victims.add(entity);
        }
        for(Entity entity:victims){
            if(entity.isRemoved()||!entity.isAlive())continue;
                if(entity instanceof ItemEntity item){
                    EmcAmount value=EmcService.itemRecoveryPrice(item.getItem(),world.getServer()).multiply(item.getItem().getCount());
                    if(!value.isZero()){item.discard();earned=earned.add(value);converted++;}
                }else{BigInteger value=EntityEmc.absorb(entity);if(value.signum()>0){earned=earned.add(EmcAmount.of(value));converted++;}}
        }
        if(terrain==null)terrain=new BlackholeBlockCursor((int)r,net.minecraft.core.Direction.Axis.Y,true);
        int removed=0,blockBudget=(int)Math.min(256,8*fusionMass),scanBudget=(int)Math.min(4096,128*fusionMass);
        // 由内向外常量内存遍历；高等级不会把随机采样散到未加载远方，范围/寿命增长仍受每刻工作预算约束。
        for(int i=0;i<scanBudget&&removed<blockBudget;i++){
            BlockPos offset=terrain.next();if(offset==null)break;
            if(offset.distSqr(BlockPos.ZERO)>r*r)continue;
            long x=(long)getBlockX()+offset.getX(),y=(long)getBlockY()+offset.getY(),z=(long)getBlockZ()+offset.getZ();
            if(x< -30000000||x>=30000000||z< -30000000||z>=30000000||y<world.getMinBuildHeight()||y>=world.getMaxBuildHeight())continue;
            BlockPos pos=new BlockPos((int)x,(int)y,(int)z);
            if(!world.hasChunkAt(pos)||!world.getWorldBorder().isWithinBounds(pos))continue;
            var state=world.getBlockState(pos);
            if(state.isAir()||state.hasBlockEntity()||!state.getFluidState().isEmpty()||state.getDestroySpeed(world,pos)<0
                    ||player.blockActionRestricted(world,pos,player.gameMode.getGameModeForPlayer())
                    ||world.getServer().isUnderSpawnProtection(world,pos,player)||!BlackholeWorldActions.allowSkillBreak(player,pos))continue;
            EmcAmount blockValue=EntityEmc.blockPrice(state,world);
            if(!world.removeBlock(pos,false))continue;
            earned=earned.add(blockValue);
            SingularityDebrisEntity debris=new SingularityDebrisEntity(ModEntities.DEBRIS.get(),world);
            debris.initialize(state,getUUID(),position());debris.setPos(Vec3.atCenterOf(pos));world.addFreshEntity(debris);removed++;
        }
        if(!earned.isZero())EmcService.credit(world.getServer(),owner,earned);
    }
}
