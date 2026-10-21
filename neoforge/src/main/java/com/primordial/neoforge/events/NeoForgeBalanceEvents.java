package com.primordial.neoforge.events;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.network.SyncBalancePayload;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.ITransmutationProxy;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeBalanceEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, BigInteger> LAST_SENT = new HashMap<>();

    private NeoForgeBalanceEvents() {}

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        syncBalance(player, true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // ProjectE 1.21.1 does not emit a balance-change event from setEmc, so check at the Forge sync cadence.
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            syncBalance(player, false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    private static void syncBalance(ServerPlayer player, boolean force) {
        try {
            IKnowledgeProvider knowledge = ITransmutationProxy.INSTANCE.getKnowledgeProviderFor(player.getUUID());
            BigInteger emc = knowledge == null ? BigInteger.ZERO : knowledge.getEmc();
            if (force || !emc.equals(LAST_SENT.get(player.getUUID()))) {
                PacketDistributor.sendToPlayer(player, new SyncBalancePayload(emc));
                LAST_SENT.put(player.getUUID(), emc);
            }
        } catch (RuntimeException failure) {
            LOGGER.error("Could not sync ProjectE EMC for player {}", player.getGameProfile().getName(), failure);
        }
    }
}
