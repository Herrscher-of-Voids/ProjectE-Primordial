package com.primordial.neoforge.equipment;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeSignatureActions {
    private static final Map<UUID,Dash> DASHES=new HashMap<>();
    private NeoForgeSignatureActions() { }
    public static void activate(ServerPlayer p){
        if(!p.server.isSameThread()||!p.isAlive()||p.isSpectator()||p.isPassenger()||NeoForgeSignatureCombat.frozen(p))return;
        long now=p.level().getGameTime();
        if(p.getPersistentData().contains("BlackholeActionTick")&&now-p.getPersistentData().getLong("BlackholeActionTick")<5)return;
        p.getPersistentData().putLong("BlackholeActionTick",now);
        BigInteger reap=NeoForgeBlackholeModules.level(p.getMainHandItem(),Kind.REAP);
        if(reap.signum()>0){NeoForgeSignatureFarming.start(p,reap);return;}
        BigInteger level=NeoForgeSignatureArmor.level(p,EquipmentSlot.FEET,Kind.DASH);if(level.signum()==0||DASHES.containsKey(p.getUUID()))return;
        Vec3 direction=p.getLookAngle().normalize();
        Dash dash=new Dash(p.serverLevel(),p.getItemBySlot(EquipmentSlot.FEET),level,direction,NeoForgeBlackholeScaling.radius(level,8));
        if(!dash.clear(p,p.position().add(direction.scale(0.5)))||!NeoForgeBlackholeGear.payLevel(p,Kind.DASH,level))return;
        DASHES.put(p.getUUID(),dash);
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        Dash dash=DASHES.get(p.getUUID());if(dash!=null&&!dash.step(p))DASHES.remove(p.getUUID());NeoForgeSignatureFarming.tick(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){DASHES.remove(event.getEntity().getUUID());NeoForgeSignatureFarming.cancel(event.getEntity().getUUID());}
    private static final class Dash {
        final ServerLevel world;final ItemStack boots;final BigInteger level;final Vec3 direction;double remaining;
        Dash(ServerLevel world,ItemStack boots,BigInteger level,Vec3 direction,double distance){this.world=world;this.boots=boots;this.level=level;this.direction=direction;remaining=distance;}
        boolean clear(ServerPlayer p,Vec3 at){
            BlockPos pos=BlockPos.containing(at);AABB bounds=p.getBoundingBox().move(at.subtract(p.position()));
            return at.y>=world.getMinBuildHeight()&&at.y<world.getMaxBuildHeight()&&world.hasChunkAt(pos)&&world.hasChunkAt(pos.above(2))
                &&world.getWorldBorder().isWithinBounds(bounds)&&world.noCollision(p,bounds);
        }
        boolean step(ServerPlayer p){
            if(!p.isAlive()||p.serverLevel()!=world||p.getItemBySlot(EquipmentSlot.FEET)!=boots||!NeoForgeBlackholeModules.level(boots,Kind.DASH).equals(level)||NeoForgeSignatureCombat.frozen(p))return false;
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
