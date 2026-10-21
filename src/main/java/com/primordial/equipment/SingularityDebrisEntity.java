/**
 * 【变更记录 #1】2026-10-14 02:21:23（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：被卷起方块保存原方块状态并用方块模型渲染，沿旋转轨迹进入中心后吞噬；不重新放置、不复制掉落。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.equipment;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class SingularityDebrisEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK=SynchedEntityData.defineId(SingularityDebrisEntity.class,EntityDataSerializers.BLOCK_STATE);
    private UUID hole;private Vec3 center=Vec3.ZERO;private int age;
    public SingularityDebrisEntity(EntityType<? extends SingularityDebrisEntity> type,Level level){super(type,level);noPhysics=true;}
    public void initialize(BlockState state,UUID hole,Vec3 center){entityData.set(BLOCK,state);this.hole=hole;this.center=center;}
    public BlockState block(){return entityData.get(BLOCK);}
    @Override protected void defineSynchedData(){entityData.define(BLOCK,Blocks.STONE.defaultBlockState());}
    @Override protected void readAdditionalSaveData(CompoundTag tag){
        hole=tag.hasUUID("Hole")?tag.getUUID("Hole"):null;age=tag.getInt("Age");
        center=new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));
        entityData.set(BLOCK,NbtUtils.readBlockState(level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),tag.getCompound("Block")));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag){
        if(hole!=null)tag.putUUID("Hole",hole);tag.putInt("Age",age);tag.putDouble("X",center.x);tag.putDouble("Y",center.y);tag.putDouble("Z",center.z);tag.put("Block",NbtUtils.writeBlockState(block()));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void tick(){
        super.tick();age++;if(level().isClientSide)return;
        if(age>60||hole==null||((ServerLevel)level()).getEntity(hole)==null){discard();return;}
        Vec3 delta=center.subtract(position());double distance=delta.length();
        if(distance<0.65){discard();return;}
        Vec3 tangent=new Vec3(-delta.z,0,delta.x).normalize().scale(Math.min(0.3,distance*0.05));
        Vec3 movement=delta.normalize().scale(Math.min(distance,0.3+age*0.04)).add(tangent);
        setDeltaMovement(movement);setPos(position().add(movement));
    }
}
