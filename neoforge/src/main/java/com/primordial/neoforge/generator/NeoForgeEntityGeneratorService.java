package com.primordial.neoforge.generator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.blackhole.NeoForgeEntityRecovery;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcTransactions;
import com.primordial.service.EmcAmount;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/** The original generator buys one fresh entity using the same base price as blackhole recovery. */
public final class NeoForgeEntityGeneratorService {
    public static final int MAX_OFFERS = 4096;
    public record Offer(EntityType<?> type, BigInteger price) {}
    private NeoForgeEntityGeneratorService() {}

    public static List<Offer> catalog(ServerLevel world) {
        List<Offer> offers = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!type.canSummon() || !type.canSerialize()) continue;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if (!id.startsWith("minecraft:") && NeoForgeServerConfig.entityOverrides().stream()
                    .noneMatch(entry -> entry.startsWith(id + "="))) continue;
            try {
                Entity sample = type.create(world);
                if (sample == null) continue;
                BigInteger quote = NeoForgeEntityRecovery.price(sample);
                if (quote.signum() > 0) offers.add(new Offer(type, quote));
            } catch (RuntimeException | LinkageError incompatibleEntity) {
                // A mod's unavailable factory cannot prevent opening the remaining server-owned catalogue.
            }
        }
        offers.sort(Comparator.comparing(o -> BuiltInRegistries.ENTITY_TYPE.getKey(o.type()).toString()));
        return List.copyOf(offers.subList(0, Math.min(MAX_OFFERS, offers.size())));
    }

    public static boolean live(ServerPlayer player, BlockPos position) {
        return player != null && player.server.isSameThread() && player.isAlive() && !player.isRemoved()
                && player.server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) <= 64
                && player.serverLevel().hasChunkAt(position)
                && player.serverLevel().getBlockState(position).is(NeoForgeModRegistries.ENTITY_GENERATOR.get());
    }

    public static boolean spawn(ServerPlayer player, BlockPos generator, Offer offer) {
        if (offer == null || offer.type() == null || offer.price() == null || offer.price().signum() <= 0
                || !live(player, generator)) return false;
        ServerLevel world = player.serverLevel();
        var generatorState = world.getBlockState(generator);
        Entity entity;
        try {
            entity = offer.type().create(world);
            if (entity == null || !NeoForgeEntityRecovery.price(entity).equals(offer.price())) return false;
            Vec3 location = Vec3.atBottomCenterOf(generator.above());
            entity.moveTo(location.x, location.y, location.z, player.getYRot(), 0);
            if (entity instanceof Mob mob)
                mob.finalizeSpawn(world, world.getCurrentDifficultyAt(generator.above()), MobSpawnType.COMMAND, null);
            if (player.serverLevel() != world || !live(player, generator) || !world.getBlockState(generator).equals(generatorState)
                    || !NeoForgeEntityRecovery.price(entity).equals(offer.price())
                    || !world.getWorldBorder().isWithinBounds(entity.getBoundingBox()) || !world.noCollision(entity)
                    || !world.getEntities(entity, entity.getBoundingBox(), e -> !e.isSpectator()).isEmpty()) return false;
        } catch (RuntimeException | LinkageError invalidFactory) { return false; }
        long bought = NeoForgeEmcTransactions.purchase(player.server, player.getUUID(), EmcAmount.of(offer.price()), 1, units -> {
            if (player.serverLevel() != world || !live(player, generator) || !world.getBlockState(generator).equals(generatorState)
                    || !NeoForgeEntityRecovery.price(entity).equals(offer.price())
                    || !world.getWorldBorder().isWithinBounds(entity.getBoundingBox()) || !world.noCollision(entity)
                    || !world.getEntities(entity, entity.getBoundingBox(), e -> !e.isSpectator()).isEmpty()) return 0;
            try {
                if (world.addFreshEntity(entity) && !entity.isRemoved()) return 1;
                entity.discard();
                return 0;
            } catch (RuntimeException | LinkageError failure) {
                entity.discard();
                if (world.getEntity(entity.getUUID()) != null)
                    throw new IllegalStateException("Cannot determine generator entity delivery", failure);
                return 0;
            }
        });
        return bought == 1;
    }
}
