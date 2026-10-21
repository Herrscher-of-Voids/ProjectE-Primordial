/**
 * 【变更记录 #1】2026-10-14 01:48:06（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：范围平面挖掘、同矿连锁、磁吸和范围攻击分批执行；只处理已加载区块，沿用破坏保护与真实成功扣费；高额掉落持久化输出。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 02:34:14（第 2 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：斧按原木连锁伐木；铲固定水平平整；锤和钉头锤按体积采掘，专属持续伤害不递归触发模块。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #3】2026-10-14 03:10:28（第 3 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：范围半径与矿脉数量按指数增长；每块使用费翻倍，失败返还实际收费值。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;
import com.primordial.PrimordialMod;
import com.primordial.equipment.BlackholeModules.Kind;
import com.primordial.service.EmcService;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=PrimordialMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackholeWorldActions {
    private static final Map<UUID,Mining> MINING=new HashMap<>();
    private static final Map<UUID,Combat> COMBAT=new HashMap<>();
    private static final ThreadLocal<Boolean> SECONDARY=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> BREAKING=ThreadLocal.withInitial(()->false);
    private static final String OUTPUT="BlackholePendingDrops";
    private BlackholeWorldActions() { }
    public static boolean secondary(){return SECONDARY.get();}
    public static boolean secondaryDamage(ServerPlayer owner,LivingEntity target,float damage){
        boolean old=SECONDARY.get();SECONDARY.set(true);
        try{return target.hurt(owner.damageSources().indirectMagic(owner,owner),damage);}finally{SECONDARY.set(old);}
    }
    public static boolean allowSkillBreak(ServerPlayer p,BlockPos pos){
        boolean old=BREAKING.get();BREAKING.set(true);
        try{return net.minecraftforge.common.ForgeHooks.onBlockBreakEvent(p.serverLevel(),p.gameMode.getGameModeForPlayer(),p,pos)>=0;}
        finally{BREAKING.set(old);}
    }
    public static void cancel(ServerPlayer p){MINING.remove(p.getUUID());COMBAT.remove(p.getUUID());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event){
        if(event.isCanceled()||BREAKING.get()||!(event.getPlayer() instanceof ServerPlayer p)||p.isCreative())return;
        ItemStack tool=p.getMainHandItem();BigInteger vein=BlackholeModules.level(tool,Kind.VEIN),area=BlackholeModules.level(tool,Kind.AREA);
        boolean useVein=vein.signum()>0&&(event.getState().is(Tags.Blocks.ORES)||((tool.getItem() instanceof BlackholeTools.Axe||tool.getItem() instanceof BlackholeTools.Katar)&&event.getState().is(net.minecraft.tags.BlockTags.LOGS)));
        if(!useVein&&area.signum()==0)return;
        MINING.put(p.getUUID(),new Mining(p,tool,event.getPos(),event.getState(),useVein?Kind.VEIN:Kind.AREA,useVein?vein:area));
    }
    public static void tick(ServerPlayer p){
        Mining job=MINING.get(p.getUUID());if(job!=null&&!job.step(p))MINING.remove(p.getUUID());
        Combat combat=COMBAT.get(p.getUUID());if(combat!=null&&!combat.step(p))COMBAT.remove(p.getUUID());
        flush(p);
    }
    private static final class Mining {
        final ServerLevel world;final ItemStack tool;final BlockPos origin;final BlockState initial;final Kind kind;final BigInteger level;
        final BlackholeBlockCursor cursor;boolean started;BigInteger left;
        final ArrayDeque<BlockPos> frontier=new ArrayDeque<>();final Set<BlockPos> seen=new HashSet<>();
        Mining(ServerPlayer p,ItemStack tool,BlockPos pos,BlockState state,Kind kind,BigInteger level){
            this.world=p.serverLevel();this.tool=tool;this.origin=pos.immutable();this.initial=state;this.kind=kind;this.level=level;
            Direction.Axis axis=Direction.getNearest(p.getLookAngle().x,p.getLookAngle().y,p.getLookAngle().z).getAxis();
            // 限于 Minecraft 的有效世界坐标；总范围不受每刻工作预算限制。
            int radius=BlackholeScaling.radius(level,1);
            if(tool.getItem() instanceof BlackholeTools.Shovel)axis=Direction.Axis.Y;
            cursor=new BlackholeBlockCursor(radius,axis,tool.getItem() instanceof BlackholeTools.Hammer||tool.getItem() instanceof BlackholeTools.MorningStar);
            left=BlackholeScaling.boundedPower(level,BigInteger.valueOf(Long.MAX_VALUE)).multiply(BigInteger.valueOf(64));
            seen.add(origin);neighbors(origin);
        }
        void neighbors(BlockPos pos){for(Direction direction:Direction.values()){
            BlockPos next=pos.relative(direction);if(world.hasChunkAt(next)&&world.getBlockState(next).is(initial.getBlock())&&seen.add(next))frontier.add(next);
        }}
        BlockPos next(){
            if(kind==Kind.VEIN)return frontier.poll();
            BlockPos offset=cursor.next();if(offset==null)return null;
            long x=(long)origin.getX()+offset.getX(),y=(long)origin.getY()+offset.getY(),z=(long)origin.getZ()+offset.getZ();
            if(x < -30000000||x>=30000000||z < -30000000||z>=30000000||y<world.getMinBuildHeight()||y>=world.getMaxBuildHeight())return origin;
            return new BlockPos((int)x,(int)y,(int)z);
        }
        boolean step(ServerPlayer p){
            if(p.serverLevel()!=world||p.getMainHandItem()!=tool||!p.isAlive()||!BlackholeModules.level(tool,kind).equals(level))return false;
            if(!started){started=true;if(world.getBlockState(origin).equals(initial))return false;}
            int broken=0;
            for(int scanned=0;scanned<256&&broken<32;scanned++){
                BlockPos pos=next();if(pos==null||left.signum()<=0&&kind==Kind.VEIN)return false;
                if(pos.equals(origin)||!world.hasChunkAt(pos)||!world.getWorldBorder().isWithinBounds(pos))continue;
                BlockState state=world.getBlockState(pos);
                if(state.isAir()||state.getDestroySpeed(world,pos)<0||state.hasBlockEntity()||!state.canHarvestBlock(world,pos,p))continue;
                if(kind==Kind.VEIN&&!state.is(initial.getBlock()))continue;
                BigInteger cost=BlackholeGear.charge(p,kind,level);if(cost.signum()==0)return false;
                boolean removed=false;BREAKING.set(true);
                try{removed=p.gameMode.destroyBlock(pos)&&!world.getBlockState(pos).equals(state);}finally{BREAKING.remove();if(!removed)EmcService.add(p.server,p.getUUID(),cost);}
                if(removed){broken++;left=left.subtract(BigInteger.ONE);if(kind==Kind.VEIN)neighbors(pos);}
            }
            return true;
        }
    }
    public static void magnet(ServerPlayer p,double radius){
        double distance=radius*radius;
        for(Entity e:p.serverLevel().getAllEntities())if(e instanceof ItemEntity item&&item.isAlive()&&!item.hasPickUpDelay()
                &&item.distanceToSqr(p)<=distance){
            CompoundTag data=new CompoundTag();item.addAdditionalSaveData(data);
            if(data.hasUUID("Owner")&&!data.getUUID("Owner").equals(p.getUUID()))continue;
            item.teleportTo(p.getX(),p.getY()+0.3,p.getZ());
        }
    }
    public static void sweep(ServerPlayer p,LivingEntity struck,double radius,float damage){
        ArrayDeque<UUID> targets=new ArrayDeque<>();
        for(Entity e:p.serverLevel().getAllEntities())if(e instanceof LivingEntity living&&living!=p&&living!=struck
                &&living.isAlive()&&!p.isAlliedTo(living)&&living.distanceToSqr(p)<=radius*radius
                &&(!(living instanceof Player other)||p.canHarmPlayer(other)))targets.add(living.getUUID());
        COMBAT.put(p.getUUID(),new Combat(p.serverLevel(),p.getMainHandItem(),targets,damage));
    }
    private record Combat(ServerLevel world,ItemStack tool,ArrayDeque<UUID> targets,float damage){
        boolean step(ServerPlayer p){
            if(p.serverLevel()!=world||p.getMainHandItem()!=tool||!BlackholeModules.installed(tool,Kind.SWEEP))return false;
            for(int i=0;i<32&&!targets.isEmpty();i++){
                Entity entity=world.getEntity(targets.poll());
                if(entity instanceof LivingEntity target&&target.isAlive()&&!p.isAlliedTo(target)&&(!(target instanceof Player other)||p.canHarmPlayer(other))){
                    SECONDARY.set(true);try{target.hurt(p.damageSources().playerAttack(p),damage);}finally{SECONDARY.remove();}
                }
            }
            return !targets.isEmpty();
        }
    }
    // 大数掉落存入玩家持久化队列，每刻最多输出八组；不能因背包满或退出而丢弃余量。
    public static void output(ServerPlayer p,ItemStack sample,BigInteger count){
        if(count.signum()<=0)return;
        CompoundTag persisted=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag queue=persisted.getList(OUTPUT,Tag.TAG_COMPOUND);
        for(int i=0;i<queue.size();i++){
            CompoundTag entry=queue.getCompound(i);ItemStack saved=ItemStack.of(entry.getCompound("Item"));
            if(ItemStack.isSameItemSameTags(saved,sample)){entry.putString("Count",pending(entry.getString("Count")).add(count).toString());persisted.put(OUTPUT,queue);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);return;}
        }
        CompoundTag entry=new CompoundTag();entry.put("Item",sample.copyWithCount(1).save(new CompoundTag()));entry.putString("Count",count.toString());queue.add(entry);
        persisted.put(OUTPUT,queue);p.getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);
    }
    private static BigInteger pending(String text){
        if(text.isEmpty()||text.length()>8192||!text.matches("[0-9]+"))return BigInteger.ZERO;
        return new BigInteger(text);
    }
    private static void flush(ServerPlayer p){
        CompoundTag persisted=p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);ListTag queue=persisted.getList(OUTPUT,Tag.TAG_COMPOUND);
        for(int i=0;i<8&&!queue.isEmpty();i++){
            CompoundTag entry=queue.getCompound(0);ItemStack stack=ItemStack.of(entry.getCompound("Item"));
            BigInteger count=pending(entry.getString("Count"));
            if(stack.isEmpty()||count==null||count.signum()==0){queue.remove(0);continue;}
            int batch=count.min(BigInteger.valueOf(stack.getMaxStackSize())).intValue();stack.setCount(batch);
            p.getInventory().add(stack);
            count=count.subtract(BigInteger.valueOf(batch-stack.getCount()));
            if(count.signum()==0)queue.remove(0);else entry.putString("Count",count.toString());
            if(!stack.isEmpty())break; // 背包满则保留余量，不生成无限掉落实体。
        }
        if(!persisted.contains(OUTPUT))return;
        if(queue.isEmpty())persisted.remove(OUTPUT);else persisted.put(OUTPUT,queue);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG,persisted);
    }
}
