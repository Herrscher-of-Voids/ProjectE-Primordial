/**
 * 【变更记录 #1】2026-10-14 02:37:36（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：锄主动轮回：幼苗催熟，成熟作物按实际掉落收割并消耗一份种子补种；逐格扣费，保护取消和失败不扣费。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 03:10:28（第 2 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：作物范围半径2/4/8/16与每株费用同时翻倍，失败返还准确实付值。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;
import com.primordial.equipment.BlackholeModules.Kind;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

public final class SignatureFarming {
    private static final Map<UUID,Job> JOBS=new HashMap<>();
    private SignatureFarming() { }
    public static void cancel(UUID owner){JOBS.remove(owner);}
    public static void start(ServerPlayer p,BigInteger level){
        HitResult hit=p.pick(8,0,false);if(!(hit instanceof BlockHitResult block))return;
        BlockPos center=block.getBlockPos();if(!(p.level().getBlockState(center).getBlock() instanceof CropBlock))center=center.above();
        JOBS.put(p.getUUID(),new Job(p.serverLevel(),p.getMainHandItem(),level,center));
    }
    public static void tick(ServerPlayer p){Job job=JOBS.get(p.getUUID());if(job!=null&&!job.step(p))JOBS.remove(p.getUUID());}
    private static final class Job {
        final ServerLevel world;final ItemStack tool;final BigInteger level;final BlockPos center;final BlackholeBlockCursor cursor;
        Job(ServerLevel world,ItemStack tool,BigInteger level,BlockPos center){this.world=world;this.tool=tool;this.level=level;this.center=center;
            cursor=new BlackholeBlockCursor(BlackholeScaling.radius(level,2),Direction.Axis.Y,false);}
        boolean step(ServerPlayer p){
            if(!p.isAlive()||p.serverLevel()!=world||p.getMainHandItem()!=tool||!BlackholeModules.level(tool,Kind.REAP).equals(level)||SignatureCombat.frozen(p))return false;
            int changed=0;
            for(int scan=0;scan<256&&changed<8;scan++){
                BlockPos offset=cursor.next();if(offset==null)return false;BlockPos pos=center.offset(offset);
                if(!world.hasChunkAt(pos)||!world.getWorldBorder().isWithinBounds(pos))continue;
                var state=world.getBlockState(pos);if(!(state.getBlock() instanceof CropBlock crop)||state.hasBlockEntity())continue;
                if(p.blockActionRestricted(world,pos,p.gameMode.getGameModeForPlayer())||world.getServer().isUnderSpawnProtection(world,pos,p)||!BlackholeWorldActions.allowSkillBreak(p,pos))continue;
                List<ItemStack> drops=new ArrayList<>();
                if(crop.isMaxAge(state)){
                    ItemStack seed=crop.getCloneItemStack(world,pos,state);
                    drops=Block.getDrops(state,world,pos,null,p,tool);
                    boolean replanted=false;
                    for(ItemStack drop:drops)if(!drop.isEmpty()&&drop.is(seed.getItem())){drop.shrink(1);replanted=true;break;}
                    if(!replanted)continue; // 收成中没有种子时不凭空补种。
                }
                BigInteger fee=BlackholeGear.charge(p,Kind.REAP,level);if(fee.signum()==0)return false;
                boolean mature=crop.isMaxAge(state);
                if(!world.setBlock(pos,crop.getStateForAge(mature?0:crop.getMaxAge()),3)){
                    com.primordial.service.EmcService.add(p.server,p.getUUID(),fee);continue;
                }
                if(mature)for(ItemStack drop:drops)if(!drop.isEmpty())BlackholeWorldActions.output(p,drop,BigInteger.valueOf(drop.getCount()));
                changed++;
            }
            return true;
        }
    }
}
