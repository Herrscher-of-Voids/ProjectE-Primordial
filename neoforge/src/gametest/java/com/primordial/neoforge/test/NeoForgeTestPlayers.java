package com.primordial.neoforge.test;

import java.math.BigInteger;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.service.EmcAmount;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real logged-in ProjectE accounts for generator and native kinetic transactions. */
public final class NeoForgeTestPlayers {
    private NeoForgeTestPlayers() {}

    public static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "kin-" + UUID.randomUUID().toString().substring(0, 7));
        var information = ClientInformation.createDefault();
        var player = server.getPlayerList().getPlayerForLogin(profile, information);
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player,
                    new CommonListenerCookie(profile, 0, information, false, ConnectionType.NEOFORGE));
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            if (knowledge == null) throw new IllegalStateException("ProjectE player knowledge is unavailable");
            knowledge.clearKnowledge();
            knowledge.setEmc(BigInteger.valueOf(1_000_000));
            return player;
        } catch (RuntimeException | Error failure) {
            try { if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    public static EmcAmount balance(ServerPlayer player) {
        var ledger = NeoForgeEmcSavedData.get(player.server);
        return EmcAmount.of(player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).getEmc())
                .add(ledger.pending(player.getUUID())).subtract(EmcAmount.of(ledger.debit(player.getUUID())));
    }

    public static void logout(ServerPlayer player) {
        try {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player)
                player.server.getPlayerList().remove(player);
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }
}
