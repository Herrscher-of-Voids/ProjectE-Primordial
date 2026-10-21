/**
 * 【变更记录 #1】2026-10-14 02:37:36（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：B键服务端验证，优先锄的轮回作业，否则启动靴的视线冲刺；逐半格检测墙体，每刻限预算，不加载远方区块。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 03:10:28（第 2 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：冲刺距离按8/16/32/64增长，逐半格碰撞和每刻预算不变。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;
import com.primordial.PrimordialMod;
import com.primordial.equipment.BlackholeModules.Kind;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=PrimordialMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SignatureActions {
    private static final Map<UUID,Dash> DASHES=new HashMap<>();
    private SignatureActions() { }
    public static void activate(ServerPlayer p){
        if(!p.server.isSameThread()||!p.isAlive()||p.isSpectator()||p.isPassenger()||SignatureCombat.frozen(p))return;
        long now=p.level().getGameTime();
        if(p.getPersistentData().contains("BlackholeActionTick")&&now-p.getPersistentData().getLong("BlackholeActionTick")<5)return;
        p.getPersistentData().putLong("BlackholeActionTick",now);
        BigInteger reap=BlackholeModules.level(p.getMainHandItem(),Kind.REAP);
        if(reap.signum()>0){SignatureFarming.start(p,reap);return;}
        BigInteger level=SignatureArmor.level(p,EquipmentSlot.FEET,Kind.DASH);if(level.signum()==0||DASHES.containsKey(p.getUUID()))return;
        Vec3 direction=p.getLookAngle().normalize();
        Dash dash=new Dash(p.serverLevel(),p.getItemBySlot(EquipmentSlot.FEET),level,direction,BlackholeScaling.radius(level,8));
        if(!dash.clear(p,p.position().add(direction.scale(0.5)))||!BlackholeGear.payLevel(p,Kind.DASH,level))return;
        DASHES.put(p.getUUID(),dash);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer p))return;
        Dash dash=DASHES.get(p.getUUID());if(dash!=null&&!dash.step(p))DASHES.remove(p.getUUID());SignatureFarming.tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){DASHES.remove(event.getEntity().getUUID());SignatureFarming.cancel(event.getEntity().getUUID());}
    private static final class Dash {
        final ServerLevel world;final ItemStack boots;final BigInteger level;final Vec3 direction;double remaining;
        Dash(ServerLevel world,ItemStack boots,BigInteger level,Vec3 direction,double distance){this.world=world;this.boots=boots;this.level=level;this.direction=direction;remaining=distance;}
        boolean clear(ServerPlayer p,Vec3 at){
            BlockPos pos=BlockPos.containing(at);AABB bounds=p.getBoundingBox().move(at.subtract(p.position()));
            return at.y>=world.getMinBuildHeight()&&at.y<world.getMaxBuildHeight()&&world.hasChunkAt(pos)&&world.hasChunkAt(pos.above(2))
                &&world.getWorldBorder().isWithinBounds(bounds)&&world.noCollision(p,bounds);
        }
        boolean step(ServerPlayer p){
            if(!p.isAlive()||p.serverLevel()!=world||p.getItemBySlot(EquipmentSlot.FEET)!=boots||!BlackholeModules.level(boots,Kind.DASH).equals(level)||SignatureCombat.frozen(p))return false;
            Vec3 at=p.position();boolean stopped=false;
            // 每刻最多16格，逐半格检查，防止大等级穿墙或强行加载远方区块。
            for(int i=0;i<32&&remaining>0;i++){
                double step=Math.min(0.5,remaining);Vec3 next=at.add(direction.scale(step));
                if(!clear(p,next)){stopped=true;break;}at=next;remaining-=step;
            }
            p.teleportTo(world,at.x,at.y,at.z,p.getYRot(),p.getXRot());p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;
            return !stopped&&remaining>0;
        }
    }
}
