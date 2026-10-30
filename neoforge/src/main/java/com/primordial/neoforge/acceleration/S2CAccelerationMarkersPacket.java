package com.primordial.neoforge.acceleration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;


public record S2CAccelerationMarkersPacket(List<Marker> markers) implements CustomPacketPayload {
    public static final Type<S2CAccelerationMarkersPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("primordial", "acceleration_markers"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CAccelerationMarkersPacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> encode(msg, buf), S2CAccelerationMarkersPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final int MAX_MARKERS = 256;
    public record Marker(ResourceLocation dimension, BlockPos position, int multiplier, int entityId) {
        public Marker(ResourceLocation dimension, BlockPos position, int multiplier) { this(dimension, position, multiplier, -1); }
    }

    public static void send(net.minecraft.server.level.ServerPlayer player, List<Marker> markers) {
        PacketDistributor.sendToPlayer(player,
                new S2CAccelerationMarkersPacket(List.copyOf(markers)));
    }

    public static void encode(S2CAccelerationMarkersPacket message, FriendlyByteBuf buffer) {
        int count = Math.min(message.markers.size(), MAX_MARKERS);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Marker marker = message.markers.get(i);
            buffer.writeResourceLocation(marker.dimension());
            buffer.writeBlockPos(marker.position());
            buffer.writeVarInt(marker.multiplier());
            buffer.writeVarInt(marker.entityId());
        }
    }

    public static S2CAccelerationMarkersPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_MARKERS) throw new io.netty.handler.codec.DecoderException("Too many acceleration markers");
        var markers = new java.util.ArrayList<Marker>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation dimension = buffer.readResourceLocation();
            BlockPos position = buffer.readBlockPos();
            int multiplier = buffer.readVarInt();
            if (dimension == null || multiplier < 2 || multiplier > 1024 || (multiplier & (multiplier - 1)) != 0)
                throw new io.netty.handler.codec.DecoderException("Invalid acceleration marker");
            int entityId = buffer.readVarInt();
            if (entityId < -1) throw new io.netty.handler.codec.DecoderException("Invalid acceleration entity marker");
            markers.add(new Marker(dimension, position, multiplier, entityId));
        }
        return new S2CAccelerationMarkersPacket(List.copyOf(markers));
    }

    public static void handle(S2CAccelerationMarkersPacket message, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.accept(message));
    }

    private static final class ClientHandler {
        static void accept(S2CAccelerationMarkersPacket message) {
            com.primordial.neoforge.acceleration.ClientAccelerationMarkers.replace(message.markers());
        }
    }
}
