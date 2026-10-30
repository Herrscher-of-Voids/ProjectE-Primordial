/**
 * 【文件职责】黑洞正上方实体的服务端报价与无掉落吸收；实体生成及购买另按对应系统迁移。
 * 【变更记录 #1】2026-10-22 21:38:58（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：Forge 基础生命/类别倍率、0 禁用覆盖和每刻最多 64 实体回收。
 * - 修改：排除玩家、物品、经验、投射物与原初奇点；先报价再移除，确认移除才排入收益。
 * - 用途：保留既有黑洞实体回收，不因移植基础资源端口而静默删除这项功能。
 * 【变更记录 #2】2026-10-22 21:53:08（第 2 次备注 · 2.0.0-neo-dev 实体移除重入保护）
 * - 删除：实体移除回调后继续依赖原方块实体入账的路径。
 * - 新增：消费前账本预检、旧归属捕获、移除异常隔离及无法入账时的完整收益恢复记录。
 * - 修改：确认实体已移除才向捕获的 UUID 入账；回调拆除或换主不改变本次收益归属。
 * - 用途：防止 EntityLeaveLevelEvent 同步重入造成已销毁实体无收益或错付；失效后停止继续吸收。
 */
package com.primordial.neoforge.blackhole;

import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcNbt;
import com.primordial.neoforge.emc.NeoForgeEmcRecoveryData;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.service.EmcAmount;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;

public final class NeoForgeEntityRecovery {
    private static final AtomicBoolean REMOVAL_WARNED = new AtomicBoolean();
    private static final AtomicBoolean RECOVERY_WARNED = new AtomicBoolean();
    private static final AtomicBoolean PRESERVE_WARNED = new AtomicBoolean();

    private NeoForgeEntityRecovery() {}

    public static BigInteger price(Entity entity) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!entity.getType().canSummon() || !entity.getType().canSerialize()
                || entity instanceof Player || entity instanceof ItemEntity || entity instanceof ExperienceOrb
                || entity instanceof Projectile
                || id.getNamespace().equals("primordial") && id.getPath().startsWith("singularity")) return BigInteger.ZERO;
        for (String entry : NeoForgeServerConfig.entityOverrides()) {
            int split = entry.indexOf('=');
            if (split > 0 && entry.substring(0, split).equals(id.toString())) return new BigInteger(entry.substring(split + 1));
        }
        double health = 8;
        if (entity instanceof LivingEntity living) {
            @SuppressWarnings("unchecked") EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) entity.getType();
            health = DefaultAttributes.hasSupplier(type)
                    ? DefaultAttributes.getSupplier(type).getValue(Attributes.MAX_HEALTH) : living.getMaxHealth();
        }
        long multiplier = entity.getType().getCategory() == MobCategory.MONSTER ? 2 : 1;
        if (entity.getType() == EntityType.WITHER || entity.getType() == EntityType.ENDER_DRAGON) multiplier = 64;
        return BigInteger.valueOf((long) Math.ceil(Math.max(1, Math.min(1_000_000, health))))
                .multiply(BigInteger.valueOf(128 * multiplier));
    }

    static void absorbAbove(NeoForgeBlackholeBlockEntity block, ServerLevel level) {
        int absorbed = 0;
        for (Entity entity : level.getEntities((Entity) null, new AABB(block.getBlockPos()).inflate(1.5))) {
            if (absorbed >= 64 || !block.resourceReady()) break;
            if (!entity.isAlive() || entity.isRemoved() || !entity.blockPosition().equals(block.getBlockPos().above())) continue;
            BigInteger quote = price(entity);
            if (quote.signum() <= 0) continue;
            UUID owner = block.owner();
            if (owner == null || !NeoForgeEmcSavedData.get(level.getServer()).canCredit(owner)) break;
            try {
                entity.discard();
            } catch (RuntimeException | LinkageError failure) {
                if (REMOVAL_WARNED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                        "[Primordial] Entity recovery removal callback failed at {}; removed={}",
                        block.getBlockPos(), entity.isRemoved(), failure);
            }
            if (entity.isRemoved()) {
                absorbed++;
                if (!creditRemoved(block, level, entity, owner, quote)) break;
            }
            if (!block.resourceReady()) break;
        }
    }

    /** discard 会同步执行实体离开事件；只使用消费前捕获的归属和独立世界账本。 */
    private static boolean creditRemoved(NeoForgeBlackholeBlockEntity block, ServerLevel level,
            Entity entity, UUID owner, BigInteger quote) {
        EmcAmount amount = EmcAmount.of(quote);
        try {
            NeoForgeEmcCredits.credit(level.getServer(), owner, amount);
            return true;
        } catch (RuntimeException | LinkageError unavailable) {
            CompoundTag snapshot = new CompoundTag();
            snapshot.putUUID("owner", owner);
            snapshot.put("queued_credit", NeoForgeEmcNbt.write(amount));
            snapshot.putString("dimension", level.dimension().location().toString());
            snapshot.putInt("x", block.getBlockPos().getX());
            snapshot.putInt("y", block.getBlockPos().getY());
            snapshot.putInt("z", block.getBlockPos().getZ());
            snapshot.putString("entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            snapshot.putString("recovery_reason", "entity_removed_before_credit");
            try {
                NeoForgeEmcRecoveryData.preserve(level.getServer(), snapshot);
                if (RECOVERY_WARNED.compareAndSet(false, true)) LogUtils.getLogger().error(
                        "[Primordial] Removed entity credit for {} at {} preserved in EMC recovery data", owner, block.getBlockPos(), unavailable);
            } catch (RuntimeException | LinkageError preserveFailure) {
                if (PRESERVE_WARNED.compareAndSet(false, true)) LogUtils.getLogger().error(
                        "[Primordial] Cannot preserve removed entity credit snapshot {}", snapshot, preserveFailure);
            }
            return false;
        }
    }
}
