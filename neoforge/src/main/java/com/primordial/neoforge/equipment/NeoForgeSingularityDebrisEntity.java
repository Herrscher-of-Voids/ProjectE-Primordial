package com.primordial.neoforge.equipment;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class NeoForgeSingularityDebrisEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK=SynchedEntityData.defineId(NeoForgeSingularityDebrisEntity.class,EntityDataSerializers.BLOCK_STATE);
    private UUID hole;private Vec3 center=Vec3.ZERO;private int age;
    public NeoForgeSingularityDebrisEntity(EntityType<? extends NeoForgeSingularityDebrisEntity> type,Level level){super(type,level);noPhysics=true;}
    public void initialize(BlockState state,UUID hole,Vec3 center){entityData.set(BLOCK,state);this.hole=hole;this.center=center;}
    public BlockState block(){return entityData.get(BLOCK);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){builder.define(BLOCK,Blocks.STONE.defaultBlockState());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        hole=tag.hasUUID("Hole")?tag.getUUID("Hole"):null;age=Math.max(0,tag.getInt("Age"));
        center=new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
        if(!Double.isFinite(center.lengthSqr()))center=Vec3.ZERO;
        entityData.set(BLOCK,NbtUtils.readBlockState(level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag.getCompound("Block")));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        if(hole!=null)tag.putUUID("Hole",hole);tag.putInt("Age",age);tag.putDouble("X",center.x);tag.putDouble("Y",center.y);tag.putDouble("Z",center.z);tag.put("Block",NbtUtils.writeBlockState(block()));
    }
    @Override public void tick(){
        // Visual debris follows its own path; baseTick's fluid checks can load chunks even on its final tick.
        firstTick=false;checkBelowWorld();if(isRemoved())return;
        if(age<Integer.MAX_VALUE)age++;
        if(level().isClientSide)return;
        if(age>60||hole==null||!(((ServerLevel)level()).getEntity(hole) instanceof NeoForgeSingularityEntity parent)||parent.isRemoved()){discard();return;}
        Vec3 delta=center.subtract(position());double distance=delta.length();
        if(distance<0.65){discard();return;}
        Vec3 tangent=new Vec3(-delta.z,0,delta.x).normalize().scale(Math.min(0.3,distance*0.05));
        Vec3 movement=delta.normalize().scale(Math.min(distance,0.3+age*0.04)).add(tangent);
        setDeltaMovement(movement);setPos(position().add(movement));
    }
}
