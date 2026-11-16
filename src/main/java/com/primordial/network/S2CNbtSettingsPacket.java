package com.primordial.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CNbtSettingsPacket(int containerId, int flags) {
    public static void encode(S2CNbtSettingsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.flags);
    }
    public static S2CNbtSettingsPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt(), flags = buf.readUnsignedByte();
        if (flags > 15) throw new io.netty.handler.codec.DecoderException("Invalid NBT settings");
        return new S2CNbtSettingsPacket(id, flags);
    }
    public static void handle(S2CNbtSettingsPacket msg, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientHandler.accept(msg));
        supplier.get().setPacketHandled(true);
    }
    private static final class ClientHandler {
        static void accept(S2CNbtSettingsPacket msg) { com.primordial.client.TransmutationNbtButtons.accept(msg); }
    }
}
