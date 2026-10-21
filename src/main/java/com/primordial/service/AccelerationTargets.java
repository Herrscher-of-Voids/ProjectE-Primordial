/**
 * 【文件职责】把持久化绑定解析成当前已加载的方块、实体或全局时钟工作。
 * 【变更记录 #1】2026-09-18 01:30:39（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：实体 UUID 定位、游戏规则门禁、玩家/骑乘/多部件排除及实体 AI 临时开关。
 * - 修改：昼夜与天气使用主世界权威数据，游戏总时间不变；方块仍经原生目标检查。
 * - 用途：所有回调只在已付款的主线程执行，实体每次调用前复核；AI 状态即使异常也恢复。
 */
/**
 * 【变更记录 #2】2026-09-18 02:28:29（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：多部件实体仅排除子部件、未排除主体的遗漏。
 * - 新增：Forge isMultipartEntity 主体校验，与 PartEntity 子部件检查同时生效。
 * - 修改：主体在绑定和每次额外 tick 前均被拒绝，避免部件更新链被重复推进。
 * - 用途：兑现实体配置所述的多部件排除规则，不对未支持的复合实体扣款。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.item.EmcAcceleratorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.ServerLevelData;

final class AccelerationTargets {
    private AccelerationTargets() { }

    static ServerLevel level(MinecraftServer server, CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(EmcAcceleratorItem.DIMENSION_KEY));
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    static AccelerationWork read(MinecraftServer server, CompoundTag entry) {
        try {
            var kind = AccelerationSettings.kind(entry);
            if (kind == null || kind.filter() || kind == AccelerationSettings.Kind.AREA) return null;
            ServerLevel level = level(server, entry);
            if (level == null) return null;
            if (kind.world()) return world(server, level, kind);
            if (kind == AccelerationSettings.Kind.ENTITY) {
                if (!entry.hasUUID("EntityId")) return null;
                Entity entity = level.getEntity(entry.getUUID("EntityId"));
                if (!entityValid(level, entity)) return null;
                return new AccelerationWork(entity.getUUID(), level, entity.blockPosition(), () -> entityValid(level, entity),
                        () -> tickEntity(level, entity, entry.getBoolean("DisableEntityAi")), entity.getId());
            }
            AccelerationTarget target = block(level, AccelerationBindings.pos(entry));
            return target == null || !target.blockId().equals(entry.getString(EmcAcceleratorItem.BLOCK_KEY)) ? null : work(target);
        } catch (RuntimeException | LinkageError unavailable) { return null; }
    }

    static AccelerationTarget block(ServerLevel level, BlockPos pos) {
        try { return AccelerationTarget.read(level, pos); }
        catch (RuntimeException | LinkageError unavailable) { return null; }
    }

    static AccelerationWork work(AccelerationTarget target) {
        return new AccelerationWork(GlobalPos.of(target.level().dimension(), target.pos()), target.level(), target.pos(),
                target::valid, target::tick, -1);
    }

    static boolean entityValid(ServerLevel level, Entity entity) {
        return PrimordialServerConfig.accelerationEntitiesEnabled() && entity != null && !(entity instanceof Player)
                && !(entity instanceof net.minecraftforge.entity.PartEntity<?>) && !entity.isMultipartEntity()
                && !entity.isRemoved() && entity.isAlive()
                && entity.level() == level && !entity.isPassenger() && !entity.isVehicle() && entity.canUpdate()
                && level.hasChunkAt(entity.blockPosition()) && level.shouldTickBlocksAt(entity.blockPosition())
                && !PrimordialServerConfig.accelerationEntityBlocked(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    private static void tickEntity(ServerLevel level, Entity entity, boolean disableAi) {
        Mob mob = entity instanceof Mob value ? value : null;
        boolean previousAi = mob != null && mob.isNoAi();
        try {
            if (mob != null && disableAi) mob.setNoAi(true);
            // 先记次数再调用原生 tick，避免 tickNonPassenger 自动递归乘客或异常破坏世界 profiler 栈。
            entity.setOldPosAndRot();
            entity.tickCount++;
            entity.tick();
        } finally {
            if (mob != null && disableAi) mob.setNoAi(previousAi);
        }
    }

    private static AccelerationWork world(MinecraftServer server, ServerLevel level, AccelerationSettings.Kind kind) {
        if (level != server.overworld()) return null;
        if (kind == AccelerationSettings.Kind.DAYTIME) {
            java.util.function.BooleanSupplier valid = () -> PrimordialServerConfig.accelerationWorldEnabled()
                    && level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) && level.getDayTime() < Long.MAX_VALUE;
            return !valid.getAsBoolean() ? null : new AccelerationWork(kind, level, BlockPos.ZERO, valid,
                    () -> level.setDayTime(level.getDayTime() + 1), -2);
        }
        if (!(level.getLevelData() instanceof ServerLevelData data)) return null;
        java.util.function.BooleanSupplier valid = () -> PrimordialServerConfig.accelerationWorldEnabled()
                && level.dimensionType().hasSkyLight() && level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        return !valid.getAsBoolean() ? null : new AccelerationWork(kind, level, BlockPos.ZERO, valid,
                () -> AccelerationWeather.tick(data, level.getRandom()), -2);
    }
}
