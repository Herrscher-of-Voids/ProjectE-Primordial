/**
 * 【文件职责】发布 JAR 在隔离 Forge 世界执行实体生成/吸收、EMC 精确差额与融合保存断言。
 * 【变更记录 #1】2026-10-14 16:08:40（第 1 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：无。新增：真实猪生成和失败不扣费，黑洞方块/技能回收，无掉落重复计价，同账户融合及不同账户隔离。
 * - 修改：临时假账户仅测试时加入在线索引，结束清理；不加载玩家存档。
 * - 用途：验证反混淆发行产物和 Forge 事件真实行为，测试模组不部署至实例。
 */
/**
 * 【变更记录 #2】2026-10-14 16:08:01（第 2 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：探针对模组子类继承 tick 的直接调用（独立 JAR 反混淆不会重写该引用）。
 * - 新增：真实默认生物属性报价断言；通过原版 Entity 声明调用生命周期。
 * - 修改：测试范围不变，避免测试工具自身的 NoSuchMethodError。
 * - 用途：验证发行代码而非探针映射错误。
 */
package com.primordial.smoke;
import com.primordial.service.*;
import com.primordial.equipment.SingularityEntity;
import com.primordial.registry.*;
import com.primordial.blockentity.BlackholeBlockEntity;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import static com.primordial.smoke.RuntimeSmoke.check;
final class EntityRuntimeSmoke {
    private static SingularityEntity hole(ServerLevel world,UUID owner,UUID id,BlockPos pos){
        var hole=new SingularityEntity(ModEntities.SINGULARITY.get(),world);CompoundTag tag=new CompoundTag();
        tag.putUUID("Owner",owner);tag.putString("Power","1");tag.putString("RemainingTicks","600");
        ((Entity)hole).load(tag);((Entity)hole).setUUID(id);((Entity)hole).setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5);world.addFreshEntity(hole);return hole;
    }
    static void run(MinecraftServer server) throws Exception {
        ServerLevel world=server.overworld();ServerPlayer owner=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        List<ServerPlayer> online=null;Map<UUID,ServerPlayer> ids=null;
        for(var field:net.minecraft.server.players.PlayerList.class.getDeclaredFields()){
            if(java.lang.reflect.Modifier.isStatic(field.getModifiers()))continue;field.setAccessible(true);
            if(field.getType()==List.class&&field.get(server.getPlayerList()) instanceof ArrayList<?>)online=(List<ServerPlayer>)field.get(server.getPlayerList());
            if(field.getType()==Map.class&&field.getGenericType() instanceof java.lang.reflect.ParameterizedType t&&t.getActualTypeArguments()[0]==UUID.class&&t.getActualTypeArguments()[1]==ServerPlayer.class)ids=(Map<UUID,ServerPlayer>)field.get(server.getPlayerList());
        }
        check(online!=null&&ids!=null,"Account fixture indexes");online.add(owner);ids.put(owner.getUUID(),owner);
        List<Entity> created=new ArrayList<>();
        try {
            for(int x=-1;x<=2;x++)for(int z=-1;z<=1;z++){world.getChunk(x,z);world.setChunkForced(x,z,true);}
            ((Entity)owner).setPos(0,120,0);EmcService.add(server,owner.getUUID(),BigInteger.valueOf(10_000_000));
            BlockPos generator=new BlockPos(4,110,4);world.setBlockAndUpdate(generator,ModBlocks.ENTITY_GENERATOR.get().defaultBlockState());
            var catalog=EntityEmc.catalog(world);check(catalog.stream().anyMatch(o->o.type()==EntityType.WITHER),"Vanilla boss catalog");
            check(catalog.stream().noneMatch(o->o.type()==EntityType.PLAYER||o.type()==EntityType.ARROW||o.type()==EntityType.ITEM||o.type()==ModEntities.SINGULARITY.get()),"Excluded entity catalog");
            var offer=catalog.stream().filter(o->o.type()==EntityType.PIG).findFirst().orElseThrow();
            check(offer.price().equals(BigInteger.valueOf(1280)),"Pig base-health price");
            check(catalog.stream().filter(o->o.type()==EntityType.ZOMBIE).findFirst().orElseThrow().price().equals(BigInteger.valueOf(5120)),"Zombie base-health/category price");
            check(catalog.stream().filter(o->o.type()==EntityType.WITHER).findFirst().orElseThrow().price().equals(BigInteger.valueOf(2457600)),"Boss category price");
            BigInteger before=EmcService.balance(server,owner.getUUID());check(EntityEmc.spawn(owner,generator,offer),"Pig purchase");
            check(EmcService.balance(server,owner.getUUID()).equals(before.subtract(offer.price())),"Exact entity purchase charge");
            var pigs=world.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,new AABB(generator.above()).inflate(2));check(pigs.size()==1,"One actual pig generated");created.addAll(pigs);
            before=EmcService.balance(server,owner.getUUID());check(!EntityEmc.spawn(owner,generator,offer),"Occupied output refuses spawn");check(EmcService.balance(server,owner.getUUID()).equals(before),"No fee for occupied output");
            BlockPos sink=new BlockPos(20,110,4);world.setBlockAndUpdate(sink,ModBlocks.BLACKHOLE.get().defaultBlockState());
            var block=(BlackholeBlockEntity)world.getBlockEntity(sink);block.setOwner(owner.getUUID());
            ((Entity)pigs.get(0)).setPos(sink.getX()+0.5,sink.getY()+1.1,sink.getZ()+0.5);
            BlackholeBlockEntity.serverTick(world,sink,world.getBlockState(sink),block);
            check(((Entity)pigs.get(0)).isRemoved(),"Block instant absorption");check(EmcService.balance(server,owner.getUUID()).equals(before.add(offer.price())),"Block credits same entity quote");
            BlackholeBlockEntity.serverTick(world,sink,world.getBlockState(sink),block);check(EmcService.balance(server,owner.getUUID()).equals(before.add(offer.price())),"No repeat credit");
            BlockPos center=new BlockPos(36,110,4);var first=hole(world,owner.getUUID(),new UUID(0,10),center);var second=hole(world,owner.getUUID(),new UUID(0,20),center.offset(2,0,0));created.add(first);created.add(second);
            var foreign=hole(world,UUID.randomUUID(),new UUID(0,30),center.offset(3,0,0));created.add(foreign);
            var zombie=EntityType.ZOMBIE.create(world);zombie.moveTo(center.getX()+0.5,center.getY()+0.2,center.getZ()+0.5,0,0);world.addFreshEntity(zombie);created.add(zombie);
            BlockPos terrain=center.offset(-1,-1,-1);world.setBlockAndUpdate(terrain,Blocks.DIAMOND_BLOCK.defaultBlockState());var diamond=EntityEmc.blockPrice(Blocks.DIAMOND_BLOCK.defaultBlockState(),world);check(!diamond.isZero(),"Real ProjectE diamond price");
            before=EmcService.balance(server,owner.getUUID());BigInteger zombiePrice=EntityEmc.price(zombie);
            ((Entity)first).tick();check(((Entity)second).isRemoved(),"Same owner black holes merge");check(!((Entity)foreign).isRemoved(),"Foreign black hole remains");
            check(first.remainingTicks().equals(BigInteger.valueOf(1199)),"Remaining time added once");check(first.absorptionSpeed()==2,"Absorption speed sum");check(Math.abs(first.radius()-SingularityEntity.mergedRadius(8,8))<0.0001,"Volume conserving radius");
            check(((Entity)zombie).isRemoved(),"Skill instant absorption");check(world.getBlockState(terrain).isAir(),"Terrain actually removed");
            check(EmcService.balance(server,owner.getUUID()).equals(before.add(zombiePrice).add(diamond.floor())),"Skill credits mob and original block exactly");
            check(world.getEntitiesOfClass(ItemEntity.class,new AABB(center).inflate(10)).isEmpty(),"No duplicate item loot");
            CompoundTag saved=((Entity)first).saveWithoutId(new CompoundTag());saved.putInt("Age",700);var restored=new SingularityEntity(ModEntities.SINGULARITY.get(),world);((Entity)restored).load(saved);
            check(restored.remainingTicks().equals(first.remainingTicks())&&restored.absorptionSpeed()==2&&restored.radius()==first.radius(),"Merged lifetime/range/speed survive reload beyond base lifetime");
            System.out.println("ENTITY_EMC_RUNTIME_CHECKED: exact spawn/sink/skill fees; no duplicate loot; same-owner fusion volume/time/speed; foreign ownership isolation; merged NBT reload");
        } finally {
            for(Entity entity:created)entity.discard();online.remove(owner);ids.remove(owner.getUUID());
        }
    }
}
