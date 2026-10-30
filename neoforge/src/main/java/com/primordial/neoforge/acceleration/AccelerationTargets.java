package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.AccelerationConfig;
import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
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
        return AccelerationConfig.accelerationEntitiesEnabled() && entity != null && !(entity instanceof Player)
                && !(entity instanceof net.neoforged.neoforge.entity.PartEntity<?>) && !entity.isMultipartEntity()
                && !entity.isRemoved() && entity.isAlive()
                && (!(entity instanceof net.minecraft.world.entity.LivingEntity living)
                    || !com.primordial.neoforge.equipment.NeoForgeSignatureCombat.frozen(living))
                && entity.level() == level && !entity.isPassenger() && !entity.isVehicle()
                && level.hasChunkAt(entity.blockPosition()) && level.shouldTickBlocksAt(entity.blockPosition())
                && !AccelerationConfig.accelerationEntityBlocked(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    private static void tickEntity(ServerLevel level, Entity entity, boolean disableAi) {
        Mob mob = entity instanceof Mob value ? value : null;
        boolean previousAi = mob != null && mob.isNoAi();
        try {
            if (mob != null && disableAi) mob.setNoAi(true);
            // 先记次数再调用原生 tick，避免 tickNonPassenger 自动递归乘客或异常破坏世界 profiler 栈。
            entity.setOldPosAndRot();
            entity.tickCount++;
            if (!net.neoforged.neoforge.event.EventHooks.fireEntityTickPre(entity).isCanceled()) {
                entity.tick();
                net.neoforged.neoforge.event.EventHooks.fireEntityTickPost(entity);
            }
        } finally {
            if (mob != null && disableAi) mob.setNoAi(previousAi);
        }
    }

    private static AccelerationWork world(MinecraftServer server, ServerLevel level, AccelerationSettings.Kind kind) {
        if (level != server.overworld()) return null;
        if (kind == AccelerationSettings.Kind.DAYTIME) {
            java.util.function.BooleanSupplier valid = () -> AccelerationConfig.accelerationWorldEnabled()
                    && level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) && level.getDayTime() < Long.MAX_VALUE;
            return !valid.getAsBoolean() ? null : new AccelerationWork(kind, level, BlockPos.ZERO, valid,
                    () -> level.setDayTime(level.getDayTime() + 1), -2);
        }
        if (!(level.getLevelData() instanceof ServerLevelData data)) return null;
        java.util.function.BooleanSupplier valid = () -> AccelerationConfig.accelerationWorldEnabled()
                && level.dimensionType().hasSkyLight() && level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        return !valid.getAsBoolean() ? null : new AccelerationWork(kind, level, BlockPos.ZERO, valid,
                () -> AccelerationWeather.tick(data, level.getRandom()), -2);
    }
}
