package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.EmcAccelerationService;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SConfirmAccelerationPacket(UUID token, boolean accepted) implements CustomPacketPayload {
    public static final Type<C2SConfirmAccelerationPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("primordial", "acceleration_confirm"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SConfirmAccelerationPacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> encode(msg, buf), C2SConfirmAccelerationPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(C2SConfirmAccelerationPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.token); buf.writeBoolean(msg.accepted);
    }

    public static C2SConfirmAccelerationPacket decode(FriendlyByteBuf buf) {
        return new C2SConfirmAccelerationPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(C2SConfirmAccelerationPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null;
            if (player != null) EmcAccelerationService.confirm(player, msg.token, msg.accepted);
        });
    }
}
