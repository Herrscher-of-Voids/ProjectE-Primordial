package com.primordial.neoforge.acceleration;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CAccelerationStatePacket(int containerId, int revision, UUID selected, CompoundTag state,
                                        String quote, boolean accepted) implements CustomPacketPayload {
    public static final Type<S2CAccelerationStatePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("primordial", "acceleration_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CAccelerationStatePacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> encode(msg, buf), S2CAccelerationStatePacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static final int MAX_BYTES = 1048576;
    public static void encode(S2CAccelerationStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeVarInt(packet.revision); buffer.writeUUID(packet.selected);
        buffer.writeNbt(packet.state); buffer.writeUtf(packet.quote, 160); buffer.writeBoolean(packet.accepted);
    }
    public static S2CAccelerationStatePacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt(), revision = buffer.readVarInt();
        UUID selected = buffer.readUUID();
        var decoded = buffer.readNbt(NbtAccounter.create(MAX_BYTES));
        CompoundTag state = decoded instanceof CompoundTag compound ? compound : null;
        String quote = buffer.readUtf(160);
        if (state == null || state.sizeInBytes() > MAX_BYTES || state.getList("Targets", Tag.TAG_COMPOUND).size() > 256
                || !quote.matches("[0-9]{1,160}")) throw new io.netty.handler.codec.DecoderException("Invalid acceleration state");
        return new S2CAccelerationStatePacket(container, revision, selected, state, quote, buffer.readBoolean());
    }
    public static void handle(S2CAccelerationStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.accept(packet));
    }
    private static final class ClientHandler {
        static void accept(S2CAccelerationStatePacket packet) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof com.primordial.neoforge.acceleration.AccelerationMenu menu) menu.accept(packet);
        }
    }
}
