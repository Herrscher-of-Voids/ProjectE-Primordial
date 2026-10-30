package com.primordial.neoforge.transmutation;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import io.netty.handler.codec.DecoderException;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The client submits only the current menu and three learning preference bits. */
public final class NeoForgeTransmutationNbtPayloads {
    private NeoForgeTransmutationNbtPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Settings.TYPE, Settings.STREAM_CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                State state = apply(player, request);
                if (state != null) PacketDistributor.sendToPlayer(player, state);
            }
        }));
        registrar.playToClient(State.TYPE, State.STREAM_CODEC,
                (state, context) -> context.enqueueWork(() -> ClientHandler.accept(state)));
    }

    public record Settings(int menuId, int flags) implements CustomPacketPayload {
        public static final Type<Settings> TYPE = new Type<>(id("transmutation_nbt_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Settings> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarInt(value.menuId); buffer.writeByte(value.flags); },
                buffer -> new Settings(buffer.readVarInt(), readFlags(buffer, true)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(int menuId, int flags) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(id("transmutation_nbt_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarInt(value.menuId); buffer.writeByte(value.flags); },
                buffer -> new State(buffer.readVarInt(), readFlags(buffer, false)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static State apply(ServerPlayer player, Settings request) {
        if (player == null || !player.server.isSameThread() || !(player.containerMenu instanceof TransmutationContainer)
                || player.containerMenu.containerId != request.menuId() || !player.containerMenu.stillValid(player)
                || request.flags() < -1 || request.flags() > 7) return null;
        var data = NeoForgeTransmutationNbtSettings.get(player.server);
        if (request.flags() >= 0) data.set(player.getUUID(), request.flags());
        return new State(request.menuId(), data.flags(player.getUUID()));
    }

    private static int readFlags(RegistryFriendlyByteBuf buffer, boolean queryAllowed) {
        int flags = buffer.readByte();
        if (flags < (queryAllowed ? -1 : 0) || flags > 7) throw new DecoderException("Invalid transmutation NBT settings");
        return flags;
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }

    private static final class ClientHandler {
        static void accept(State state) { com.primordial.neoforge.client.NeoForgeTransmutationNbtButtons.accept(state); }
    }
}
