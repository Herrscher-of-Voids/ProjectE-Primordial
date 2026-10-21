/**
 * 【变更记录 #1】2026-10-14 02:35:54（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：护腿受击封锁攻击者移动与交互；剑低血量斩杀加成；剪刀清除有益效果；拳剑5秒持续撕裂，保护友方和禁用PvP目标。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #2】2026-10-14 02:49:18（第 2 次备注 · 1.2.0-signature-final-validation）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：切换创造或旁观后同时解除行动与交互封锁。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #3】2026-10-14 03:10:28（第 3 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：封锁时间、驱散数量、斩杀伤害、撕裂伤害和持续时间逐级翻倍，按启用等级收费。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;
import com.primordial.PrimordialMod;
import com.primordial.equipment.BlackholeModules.Kind;
import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=PrimordialMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SignatureCombat {
    private static final String STASIS="BlackholeStasis",REND="BlackholeRend";
    private SignatureCombat() { }
    private static boolean allowed(ServerPlayer owner,LivingEntity target){return owner!=target&&!owner.isAlliedTo(target)
        &&(!(target instanceof Player p)||!p.isCreative()&&!p.isSpectator()&&owner.canHarmPlayer(p))
        &&(!(target instanceof TamableAnimal pet)||!pet.isOwnedBy(owner));}
    @SubscribeEvent(priority=EventPriority.LOW) public static void execute(LivingHurtEvent event){
        if(BlackholeWorldActions.secondary()||event.getAmount()<=0||!(event.getSource().getDirectEntity() instanceof ServerPlayer p)||!allowed(p,event.getEntity()))return;
        BigInteger level=BlackholeModules.level(p.getMainHandItem(),Kind.EXECUTE);
        if(event.getEntity().getHealth()<=event.getEntity().getMaxHealth()*0.25F&&BlackholeGear.payLevel(p,Kind.EXECUTE,level))
            event.setAmount((float)Math.min(Float.MAX_VALUE,event.getAmount()+event.getEntity().getMaxHealth()*0.25*BlackholeModules.magnitude(level)));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent event){
        if(BlackholeWorldActions.secondary()||event.getAmount()<=0)return;
        if(event.getEntity() instanceof ServerPlayer defender&&event.getSource().getEntity() instanceof LivingEntity attacker&&allowed(defender,attacker)){
            BigInteger level=SignatureArmor.level(defender,EquipmentSlot.LEGS,Kind.STASIS);
            if(BlackholeGear.payLevel(defender,Kind.STASIS,level)){
                CompoundTag tag=new CompoundTag();tag.putString("Dimension",attacker.level().dimension().location().toString());
                tag.putLong("Until",BlackholeScaling.deadline(attacker.level().getGameTime(),40,level));
                tag.putDouble("X",attacker.getX());tag.putDouble("Y",attacker.getY());tag.putDouble("Z",attacker.getZ());attacker.getPersistentData().put(STASIS,tag);
            }
        }
        if(!(event.getSource().getDirectEntity() instanceof ServerPlayer p)||!allowed(p,event.getEntity()))return;
        LivingEntity target=event.getEntity();BigInteger dispel=BlackholeModules.level(p.getMainHandItem(),Kind.DISPEL);
        if(dispel.signum()>0){
            var effects=target.getActiveEffects().stream().filter(effect->effect.getEffect().isBeneficial()).toList();
            int count=BlackholeScaling.boundedPower(dispel,BigInteger.valueOf(effects.size())).intValue();
            if(count>0&&BlackholeGear.payLevel(p,Kind.DISPEL,dispel))for(int i=0;i<count;i++)target.removeEffect(effects.get(i).getEffect());
        }
        BigInteger rend=BlackholeModules.level(p.getMainHandItem(),Kind.REND);
        if(BlackholeGear.payLevel(p,Kind.REND,rend)){
            CompoundTag tag=new CompoundTag();tag.putUUID("Owner",p.getUUID());tag.putString("Power",rend.toString());
            tag.putLong("Until",BlackholeScaling.deadline(target.level().getGameTime(),100,rend));target.getPersistentData().put(REND,tag);
        }
    }
    public static boolean frozen(LivingEntity entity){
        CompoundTag tag=entity.getPersistentData().getCompound(STASIS);
        boolean active=!(entity instanceof Player p&&(p.isCreative()||p.isSpectator()))&&tag.getLong("Until")>entity.level().getGameTime()&&tag.getString("Dimension").equals(entity.level().dimension().location().toString());
        if(!active)entity.getPersistentData().remove(STASIS);return active;
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        LivingEntity target=event.getEntity();if(!(target.level() instanceof ServerLevel world)||!target.isAlive())return;
        CompoundTag rend=target.getPersistentData().getCompound(REND);
        if(rend.getLong("Until")<=world.getGameTime())target.getPersistentData().remove(REND);
        else if(world.getGameTime()%20==0&&rend.hasUUID("Owner")){
            ServerPlayer owner=world.getServer().getPlayerList().getPlayer(rend.getUUID("Owner"));BigInteger level=BlackholeModules.parse(rend.getString("Power"));
            if(owner!=null&&owner.level()==world&&level!=null&&allowed(owner,target))BlackholeWorldActions.secondaryDamage(owner,target,(float)Math.min(Float.MAX_VALUE,2*BlackholeModules.magnitude(level)));
        }
        if(frozen(target)&&!(target instanceof Player player&&(player.isCreative()||player.isSpectator()))){
            CompoundTag tag=target.getPersistentData().getCompound(STASIS);target.setDeltaMovement(Vec3.ZERO);
            if(target instanceof ServerPlayer player)player.connection.teleport(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"),player.getYRot(),player.getXRot());
            else{target.setPos(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z"));event.setCanceled(true);}
        }
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event){if(!event.getEntity().level().isClientSide&&frozen(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void interact(PlayerInteractEvent event){if(event.isCancelable()&&!event.getEntity().level().isClientSide&&frozen(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event){if(frozen(event.getPlayer()))event.setCanceled(true);}
}
