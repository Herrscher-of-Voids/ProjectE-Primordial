/**
 * 【文件职责】实体生成与黑洞吸收共用的实体报价和事务服务；仅服务端修改实体与余额。
 * 【变更记录 #1】2026-10-14 15:59:10（第 1 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：无。
 * - 新增：生命值/类别默认报价、配置覆盖、无掉落吸收及失败退款生成；玩家、物品、经验、投射物和本模组黑洞不能兑换。
 * - 修改：同一类型生成与回收共用价格；生成使用新实体，不接收客户端 NBT，模组实体需显式配置才出现在生成目录。
 * - 用途：避免击杀掉落与生物价值双重入账；价格为零禁用，碰撞/生成失败不扣 EMC。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.equipment.SingularityEntity;
import com.primordial.equipment.SingularityDebrisEntity;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class EntityEmc {
    private EntityEmc() { }
    public record Offer(EntityType<?> type, BigInteger price) { }
    /** 白名单覆盖可以为模组实体定价，0 明确禁止；非法配置由 Forge 校验拒绝。 */
    public static BigInteger override(EntityType<?> type) {
        String key=BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
        for(String entry:PrimordialServerConfig.entityEmcOverrides()) {
            int split=entry.indexOf('=');
            if(split>0&&entry.substring(0,split).equals(key))return new BigInteger(entry.substring(split+1));
        }
        return null;
    }
    public static boolean eligible(Entity entity) {
        return entity!=null&&entity.getType().canSummon()&&entity.getType().canSerialize()
                &&!(entity instanceof Player||entity instanceof ItemEntity||entity instanceof ExperienceOrb
                ||entity instanceof Projectile||entity instanceof SingularityEntity||entity instanceof SingularityDebrisEntity);
    }
    public static BigInteger price(Entity entity) {
        if(!eligible(entity))return BigInteger.ZERO;
        BigInteger override=override(entity.getType());
        if(override!=null)return override;
        double health=8;
        if(entity instanceof LivingEntity living) {
            // 用注册类型的基础生命值，不允许药水、装备或自定义当前生命值抬高回收报价。
            @SuppressWarnings("unchecked") EntityType<? extends LivingEntity> type=(EntityType<? extends LivingEntity>)entity.getType();
            health=DefaultAttributes.hasSupplier(type)?DefaultAttributes.getSupplier(type).getValue(Attributes.MAX_HEALTH):living.getMaxHealth();
        }
        long multiplier=entity.getType().getCategory()==MobCategory.MONSTER?2:1;
        ResourceLocation id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if(id.equals(new ResourceLocation("minecraft","wither"))||id.equals(new ResourceLocation("minecraft","ender_dragon")))multiplier=64;
        return BigInteger.valueOf((long)Math.ceil(Math.max(1,Math.min(1_000_000,health))))
                .multiply(BigInteger.valueOf(128*multiplier));
    }
    /** 吸入核心时直接移除，不触发战利品/经验路径；调用方将返回值聚合后一次性入账。 */
    public static BigInteger absorb(Entity entity) {
        if(entity.level().isClientSide||!entity.isAlive()||entity.isRemoved())return BigInteger.ZERO;
        BigInteger quote=price(entity);
        if(quote.signum()<=0)return BigInteger.ZERO;
        entity.discard();
        return entity.isRemoved()?quote:BigInteger.ZERO;
    }
    public static EmcAmount blockPrice(BlockState state,ServerLevel world) {
        return EmcService.itemRecoveryPrice(new net.minecraft.world.item.ItemStack(state.getBlock().asItem()),world.getServer());
    }
    /** 默认开放原版可召唤实体；显式覆盖价格可让整合包作者开放模组实体。 */
    public static List<Offer> catalog(ServerLevel world) {
        List<Offer> offers=new ArrayList<>();
        for(EntityType<?> type:BuiltInRegistries.ENTITY_TYPE) {
            if(!type.canSummon()||!type.canSerialize())continue;
            if(!BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")&&override(type)==null)continue;
            Entity sample=type.create(world);
            if(sample==null)continue;
            BigInteger quote=price(sample);
            if(quote.signum()>0)offers.add(new Offer(type,quote));
        }
        offers.sort(Comparator.comparing(o->BuiltInRegistries.ENTITY_TYPE.getKey(o.type()).toString()));
        return List.copyOf(offers);
    }
    /** 预创建、初始化、碰撞检查后扣费；Forge 拒绝加入时退款，失败不会留一个免费实体。 */
    public static boolean spawn(ServerPlayer player,BlockPos generator,Offer offer) {
        ServerLevel world=player.serverLevel();
        Entity entity=offer.type().create(world);
        if(entity==null||!eligible(entity)||!price(entity).equals(offer.price())||offer.price().signum()<=0)return false;
        Vec3 location=Vec3.atBottomCenterOf(generator.above());
        entity.moveTo(location.x,location.y,location.z,player.getYRot(),0);
        if(entity instanceof Mob mob)mob.finalizeSpawn(world,world.getCurrentDifficultyAt(generator.above()),MobSpawnType.COMMAND,null,null);
        if(!world.getWorldBorder().isWithinBounds(entity.getBoundingBox())||!world.noCollision(entity)
                ||!world.getEntities(entity,entity.getBoundingBox(),e->!e.isSpectator()).isEmpty())return false;
        if(!EmcService.spend(player.server,player.getUUID(),offer.price()))return false;
        try {
            if(world.addFreshEntity(entity))return true;
        } catch(RuntimeException failure) {
            entity.discard();
            com.primordial.PrimordialMod.LOGGER.warn("Entity generator rejected {}",BuiltInRegistries.ENTITY_TYPE.getKey(offer.type()),failure);
        }
        EmcService.add(player.server,player.getUUID(),offer.price());
        return false;
    }
}
