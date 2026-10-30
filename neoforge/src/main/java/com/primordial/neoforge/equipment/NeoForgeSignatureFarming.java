package com.primordial.neoforge.equipment;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

public final class NeoForgeSignatureFarming {
    private static final Map<UUID,Job> JOBS=new HashMap<>();
    private NeoForgeSignatureFarming() { }
    public static void cancel(UUID owner){JOBS.remove(owner);}
    public static void start(ServerPlayer p,BigInteger level){
        HitResult hit=p.pick(8,0,false);if(!(hit instanceof BlockHitResult block))return;
        BlockPos center=block.getBlockPos();if(!(p.level().getBlockState(center).getBlock() instanceof CropBlock))center=center.above();
        JOBS.put(p.getUUID(),new Job(p.serverLevel(),p.getMainHandItem(),level,center));
    }
    public static void tick(ServerPlayer p){Job job=JOBS.get(p.getUUID());if(job!=null&&!job.step(p))JOBS.remove(p.getUUID());}
    private static final class Job {
        final ServerLevel world;final ItemStack tool;final BigInteger level;final BlockPos center;final NeoForgeBlackholeBlockCursor cursor;
        Job(ServerLevel world,ItemStack tool,BigInteger level,BlockPos center){this.world=world;this.tool=tool;this.level=level;this.center=center;
            cursor=new NeoForgeBlackholeBlockCursor(NeoForgeBlackholeScaling.radius(level,2),Direction.Axis.Y,false);}
        boolean step(ServerPlayer p){
            if(!p.isAlive()||p.serverLevel()!=world||p.getMainHandItem()!=tool||!NeoForgeBlackholeModules.level(tool,Kind.REAP).equals(level)||NeoForgeSignatureCombat.frozen(p))return false;
            int changed=0;
            for(int scan=0;scan<256&&changed<8;scan++){
                BlockPos offset=cursor.next();if(offset==null)return false;BlockPos pos=center.offset(offset);
                if(!world.hasChunkAt(pos)||!world.getWorldBorder().isWithinBounds(pos))continue;
                var state=world.getBlockState(pos);if(!(state.getBlock() instanceof CropBlock crop)||state.hasBlockEntity())continue;
                if(p.blockActionRestricted(world,pos,p.gameMode.getGameModeForPlayer())||world.getServer().isUnderSpawnProtection(world,pos,p)||!NeoForgeBlackholeWorldActions.allowSkillBreak(p,pos))continue;
                List<ItemStack> drops=new ArrayList<>();
                if(crop.isMaxAge(state)){
                    ItemStack seed=crop.getCloneItemStack(world,pos,state);
                    drops=Block.getDrops(state,world,pos,null,p,tool);
                    boolean replanted=false;
                    for(ItemStack drop:drops)if(!drop.isEmpty()&&drop.is(seed.getItem())){drop.shrink(1);replanted=true;break;}
                    if(!replanted)continue; // 收成中没有种子时不凭空补种。
                }
                BigInteger fee=NeoForgeBlackholeGear.charge(p,Kind.REAP,level);if(fee.signum()==0)return false;
                boolean mature=crop.isMaxAge(state);
                if(!world.setBlock(pos,crop.getStateForAge(mature?0:crop.getMaxAge()),3)){
                    NeoForgeBlackholeGear.refund(p,fee);continue;
                }
                if(mature)for(ItemStack drop:drops)if(!drop.isEmpty())NeoForgeBlackholeWorldActions.output(p,drop,BigInteger.valueOf(drop.getCount()));
                changed++;
            }
            return true;
        }
    }
}
