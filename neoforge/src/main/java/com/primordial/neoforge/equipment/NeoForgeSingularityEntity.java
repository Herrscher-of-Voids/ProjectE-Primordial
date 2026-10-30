package com.primordial.neoforge.equipment;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.blackhole.NeoForgeEntityRecovery;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.emc.NeoForgeEmcNbt;
import com.primordial.neoforge.emc.NeoForgeEmcRecoveryData;
import com.primordial.service.EmcAmount;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeSingularityEntity extends Entity {
    public static final String ARROW_LEVEL="BlackholeImpactLevel";
    private static final EntityDataAccessor<Float> RADIUS=SynchedEntityData.defineId(NeoForgeSingularityEntity.class,EntityDataSerializers.FLOAT);
    public static final int OPEN_TICKS=12,CLOSE_TICKS=20,LIFETIME_TICKS=600;
    private static final double MAX_PULL_SPEED=2.5,MAX_PULL_ACCELERATION=1.25;
    private static final EntityDataAccessor<Integer> OPEN_AGE=SynchedEntityData.defineId(NeoForgeSingularityEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> END_TICKS=SynchedEntityData.defineId(NeoForgeSingularityEntity.class,EntityDataSerializers.INT);
    private boolean closing;
    private UUID owner;private BigInteger power=BigInteger.ONE,remainingTicks=BigInteger.valueOf(LIFETIME_TICKS);private int age;
    private NeoForgeBlackholeBlockCursor terrain;
    private double fusionMass=1,mergedForce;
    public BigInteger remainingTicks(){return remainingTicks;}
    public double absorptionSpeed(){return fusionMass;}
    public UUID owner(){return owner;}
    public static float mergedRadius(double a,double b){return (float)Math.min(60000000,Math.cbrt(a*a*a+b*b*b));}
    /** 同账户中心进入任一吸收范围才融合，UUID 较小者保留；先移除供体避免同刻再次结算。 */
    private void mergeNearby(ServerLevel world){
        List<NeoForgeSingularityEntity> candidates=new ArrayList<>();
        for(Entity entity:world.getAllEntities()){
            if(!(entity instanceof NeoForgeSingularityEntity other)||other==this||other.isRemoved()||other.closing||!Objects.equals(owner,other.owner)
                    ||getUUID().compareTo(other.getUUID())>=0||other.remainingTicks.signum()<=0)continue;
            if(position().distanceTo(other.position())>Math.max(radius(),other.radius()))continue;
            candidates.add(other);if(candidates.size()>=64)break;
        }
        for(NeoForgeSingularityEntity other:candidates){
            if(other.isRemoved())continue;
            float combined=mergedRadius(radius(),other.radius());
            double force=Math.min(60000000,pullForce()+other.pullForce());
            BigInteger life=remainingTicks.add(other.remainingTicks);double mass=Math.min(1e12,fusionMass+other.fusionMass);
            if(!discardEntity(other))continue;remainingTicks=life;fusionMass=mass;mergedForce=force;entityData.set(RADIUS,combined);terrain=null;
        }
    }
    private double pullForce(){return mergedForce>0?mergedForce:Math.min(60000000,0.16*NeoForgeBlackholeModules.magnitude(power));}
    public NeoForgeSingularityEntity(EntityType<? extends NeoForgeSingularityEntity> type,Level level){super(type,level);noPhysics=true;}
    public static float radius(BigInteger level){return (float)NeoForgeBlackholeScaling.radius(level,8);}
    public float radius(){return entityData.get(RADIUS);}
    public int age(){return age;}
    public float openingAge(float partial){return Math.min(OPEN_TICKS,entityData.get(OPEN_AGE)+partial);}
    public float endingTicks(float partial){int left=entityData.get(END_TICKS);return left>CLOSE_TICKS?CLOSE_TICKS:Math.max(0,left-partial);}
    private void syncAnimation(){
        entityData.set(OPEN_AGE,Math.min(OPEN_TICKS,age));
        entityData.set(END_TICKS,remainingTicks.max(BigInteger.ZERO).min(BigInteger.valueOf(CLOSE_TICKS+1)).intValue());
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(RADIUS,8F);builder.define(OPEN_AGE,0);builder.define(END_TICKS,CLOSE_TICKS+1);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;age=Math.max(0,tag.getInt("Age"));closing=tag.getBoolean("Closing");
        String life=tag.getString("RemainingTicks");
        try{remainingTicks=life.isEmpty()?BigInteger.valueOf(Math.max(0,LIFETIME_TICKS-age)):life.length()<=8192?new BigInteger(life):BigInteger.ZERO;}catch(NumberFormatException bad){remainingTicks=BigInteger.ZERO;}
        double mass=tag.getDouble("FusionMass");fusionMass=Double.isFinite(mass)&&mass>=1?Math.min(1e12,mass):1;
        double force=tag.getDouble("MergedForce");mergedForce=Double.isFinite(force)&&force>0?Math.min(60000000,force):0;
        if(!closing&&fusionMass<=1)remainingTicks=remainingTicks.min(BigInteger.valueOf(Math.max(0,LIFETIME_TICKS-age)));
        BigInteger saved=NeoForgeBlackholeModules.parse(tag.getString("Power"));power=saved==null||saved.signum()==0?BigInteger.ONE:saved;float savedRadius=tag.getFloat("MergedRadius");entityData.set(RADIUS,fusionMass>1&&Float.isFinite(savedRadius)&&savedRadius>0?Math.min(60000000,savedRadius):radius(power));syncAnimation();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){if(owner!=null)tag.putUUID("Owner",owner);tag.putInt("Age",age);tag.putString("Power",power.toString());tag.putString("RemainingTicks",remainingTicks.toString());tag.putBoolean("Closing",closing);tag.putDouble("FusionMass",fusionMass);tag.putDouble("MergedForce",mergedForce);tag.putFloat("MergedRadius",radius());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void impact(ProjectileImpactEvent event){
        if(event.isCanceled()||!(event.getProjectile() instanceof AbstractArrow arrow)||!(arrow.level() instanceof ServerLevel world)
                ||event.getRayTraceResult().getType()==HitResult.Type.MISS||!(arrow.getOwner() instanceof ServerPlayer p))return;
        String saved=arrow.getPersistentData().getString(ARROW_LEVEL);BigInteger level=NeoForgeBlackholeModules.parse(saved);
        if(level==null||level.signum()==0)return;
        arrow.getPersistentData().remove(ARROW_LEVEL); // 穿透箭也只允许首次碰撞生成一个黑洞。
        if(!p.isAlive()||p.isSpectator()||p.serverLevel()!=world||p.server.getPlayerList().getPlayer(p.getUUID())!=p)return;
        BigInteger fee=NeoForgeBlackholeGear.charge(p,Kind.SINGULARITY,level);if(fee.signum()==0)return;
        NeoForgeSingularityEntity hole=create(world,p.getUUID(),level,event.getRayTraceResult().getLocation());
        try{if(world.addFreshEntity(hole)&&!hole.isRemoved())return;}
        catch(RuntimeException failure){hole.discard();if(world.getEntity(hole.getUUID())!=null)throw failure;}
        NeoForgeBlackholeGear.refund(p,fee);
    }
    /** Creates the original 600-tick effect; charging remains the caller's responsibility. */
    public static NeoForgeSingularityEntity create(ServerLevel world,UUID owner,BigInteger power,Vec3 position){
        if(owner==null||power==null||power.signum()<=0||!Double.isFinite(position.lengthSqr()))throw new IllegalArgumentException("Invalid singularity");
        NeoForgeSingularityEntity hole=new NeoForgeSingularityEntity(NeoForgeModRegistries.SINGULARITY.get(),world);
        hole.owner=owner;hole.power=power;hole.remainingTicks=BigInteger.valueOf(LIFETIME_TICKS);
        hole.entityData.set(RADIUS,radius(power));hole.setPos(position);hole.syncAnimation();return hole;
    }
    private static boolean discardEntity(Entity entity){
        try{entity.discard();}
        catch(RuntimeException|LinkageError failure){if(!entity.isRemoved())throw failure;}
        return entity.isRemoved();
    }
    private void creditOrPreserve(ServerLevel world,EmcAmount amount){
        try{NeoForgeEmcCredits.credit(world.getServer(),owner,amount);}
        catch(RuntimeException|LinkageError unavailable){
            CompoundTag snapshot=new CompoundTag();snapshot.putUUID("owner",owner);snapshot.put("queued_credit",NeoForgeEmcNbt.write(amount));
            snapshot.putString("dimension",world.dimension().location().toString());snapshot.putInt("x",getBlockX());snapshot.putInt("y",getBlockY());snapshot.putInt("z",getBlockZ());
            snapshot.putString("recovery_reason","singularity_removed_before_credit");NeoForgeEmcRecoveryData.preserve(world.getServer(),snapshot);
        }
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
        // This stationary effect has no fluid/portal physics; Entity.baseTick can synchronously load its eye chunk.
        firstTick=false;checkBelowWorld();if(isRemoved())return;
        if(age<Integer.MAX_VALUE)age++;
        if(level().isClientSide)return;
        ServerLevel world=(ServerLevel)level();ServerPlayer player=owner==null?null:world.getServer().getPlayerList().getPlayer(owner);
        // 所有者离开时停止作用，留下最多1秒的纯视觉收尾；正常寿命的末20刻直接用于收缩，不额外收费或延寿。
        if(!closing&&(player==null||!player.isAlive()||player.level()!=world)){
            closing=true;remainingTicks=remainingTicks.min(BigInteger.valueOf(CLOSE_TICKS));syncAnimation();return;
        }
        if(!closing)mergeNearby(world);
        remainingTicks=remainingTicks.subtract(BigInteger.ONE);syncAnimation();
        if(remainingTicks.signum()<=0){discard();return;}
        if(closing||!player.isAlive()||player.level()!=world||world.getServer().getPlayerList().getPlayer(owner)!=player)return;
        double r=radius(),force=pullForce();
        if(!NeoForgeEmcSavedData.get(world.getServer()).canCredit(owner))return;
        EmcAmount earned=EmcAmount.ZERO;
        try{
        List<Entity> victims=new ArrayList<>();
        // 只遍历本维度已加载实体；即便高等级范围很大，也不会请求加载区块。
        for(Entity entity:world.getAllEntities()){
            if(entity==this||entity==player||entity instanceof NeoForgeSingularityEntity||entity instanceof NeoForgeSingularityDebrisEntity
                    ||!entity.isAlive()||entity.isSpectator()||entity instanceof Player
                    ||entity instanceof TamableAnimal pet&&pet.isOwnedBy(player)||player.isAlliedTo(entity))continue;
            Vec3 delta=position().subtract(entity.position().add(0,entity.getBbHeight()*0.4,0));double distance=delta.length();
            if(distance>r)continue;
            entity.setDeltaMovement(boundedPullVelocity(entity.getDeltaMovement(),delta,force,fusionMass));entity.hurtMarked=true;
            if(distance<1.6&&victims.size()<64)victims.add(entity);
        }
        for(Entity entity:victims){
            if(!player.isAlive()||player.level()!=world||world.getServer().getPlayerList().getPlayer(owner)!=player)break;
            if(entity.isRemoved()||!entity.isAlive())continue;
                if(entity instanceof ItemEntity item){
                    EmcAmount value=NeoForgeRecoveryPrices.item(item.getItem()).multiply(item.getItem().getCount());
                    if(!value.isZero()&&discardEntity(item))earned=earned.add(value);
                }else{BigInteger value=NeoForgeEntityRecovery.price(entity);if(value.signum()>0&&discardEntity(entity))earned=earned.add(EmcAmount.of(value));}
        }
        if(terrain==null)terrain=new NeoForgeBlackholeBlockCursor((int)r,net.minecraft.core.Direction.Axis.Y,true);
        int removed=0,blockBudget=(int)Math.min(256,8*fusionMass),scanBudget=(int)Math.min(4096,128*fusionMass);
        // 由内向外常量内存遍历；高等级不会把随机采样散到未加载远方，范围/寿命增长仍受每刻工作预算约束。
        for(int i=0;i<scanBudget&&removed<blockBudget;i++){
            if(!player.isAlive()||player.level()!=world||world.getServer().getPlayerList().getPlayer(owner)!=player)break;
            BlockPos offset=terrain.next();if(offset==null)break;
            if(offset.distSqr(BlockPos.ZERO)>r*r)continue;
            long x=(long)getBlockX()+offset.getX(),y=(long)getBlockY()+offset.getY(),z=(long)getBlockZ()+offset.getZ();
            if(x< -30000000||x>=30000000||z< -30000000||z>=30000000||y<world.getMinBuildHeight()||y>=world.getMaxBuildHeight())continue;
            BlockPos pos=new BlockPos((int)x,(int)y,(int)z);
            var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
            if(chunk==null||!world.getWorldBorder().isWithinBounds(pos))continue;
            var state=chunk.getBlockState(pos);
            if(state.isAir()||state.hasBlockEntity()||!state.getFluidState().isEmpty()||!readyForBlockUpdates(world,pos)||state.getDestroySpeed(world,pos)<0
                    ||player.blockActionRestricted(world,pos,player.gameMode.getGameModeForPlayer())
                    ||world.getServer().isUnderSpawnProtection(world,pos,player)||!NeoForgeBlackholeWorldActions.allowSkillBreak(player,pos)
                    ||world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=chunk||!chunk.getBlockState(pos).equals(state)
                    ||!readyForBlockUpdates(world,pos)||!player.isAlive()||player.serverLevel()!=world
                    ||world.getServer().getPlayerList().getPlayer(owner)!=player)continue;
            EmcAmount blockValue=NeoForgeRecoveryPrices.item(new net.minecraft.world.item.ItemStack(state.getBlock().asItem()));
            boolean consumed;
            try{consumed=world.removeBlock(pos,false);}
            catch(RuntimeException|LinkageError failure){if(!chunk.getBlockState(pos).isAir())throw failure;consumed=true;}
            if(!consumed)continue;
            earned=earned.add(blockValue);
            NeoForgeSingularityDebrisEntity debris=new NeoForgeSingularityDebrisEntity(NeoForgeModRegistries.SINGULARITY_DEBRIS.get(),world);
            debris.initialize(state,getUUID(),position());debris.setPos(Vec3.atCenterOf(pos));world.addFreshEntity(debris);removed++;
        }
        }finally{if(!earned.isZero())creditOrPreserve(world,earned);}
    }
    private static boolean readyForBlockUpdates(ServerLevel world,BlockPos pos){
        if(!readyChunks(world,pos))return false;
        var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        var state=chunk.getBlockState(pos);
        // Tripwire hooks scan 41 blocks even through air; support removal can detach a neighboring hook too.
        if(!readyHookUpdates(world,pos,state))return false;
        for(Direction direction:Direction.Plane.HORIZONTAL){
            if(state.getBlock() instanceof TripWireBlock&&!readyTripwireLine(world,pos,direction))return false;
            BlockPos neighbor=pos.relative(direction);
            var adjacent=world.getChunkSource().getChunkNow(neighbor.getX()>>4,neighbor.getZ()>>4).getBlockState(neighbor);
            if(adjacent.getBlock() instanceof TripWireHookBlock&&adjacent.getValue(TripWireHookBlock.FACING)==direction
                    &&!readyHookUpdates(world,neighbor,adjacent))return false;
        }
        return true;
    }
    private static boolean readyHookUpdates(ServerLevel world,BlockPos pos,BlockState state){
        return !(state.getBlock() instanceof TripWireHookBlock)
                ||!state.getValue(TripWireHookBlock.ATTACHED)&&!state.getValue(TripWireHookBlock.POWERED)
                ||readyTripwireLine(world,pos,state.getValue(TripWireHookBlock.FACING));
    }
    private static boolean readyTripwireLine(ServerLevel world,BlockPos pos,Direction direction){
        for(int distance=16;distance<42;distance+=16)if(!readyChunks(world,pos.relative(direction,distance)))return false;
        return readyChunks(world,pos.relative(direction,41));
    }
    private static boolean readyChunks(ServerLevel world,BlockPos pos){
        // hasChunkAt only checks tickets, not completed FULL chunks; normal neighbor updates must also stay loaded.
        for(int x=(pos.getX()>>4)-1;x<=(pos.getX()>>4)+1;x++)
            for(int z=(pos.getZ()>>4)-1;z<=(pos.getZ()>>4)+1;z++)
                if(world.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
}
