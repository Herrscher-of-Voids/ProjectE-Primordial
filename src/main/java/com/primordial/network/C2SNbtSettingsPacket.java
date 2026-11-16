package com.primordial.network;

import com.primordial.data.TransmutationNbtSettings;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public record C2SNbtSettingsPacket(int containerId, int flags) {
    public static void encode(C2SNbtSettingsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.flags);
    }
    public static C2SNbtSettingsPacket decode(FriendlyByteBuf buf) {
        return new C2SNbtSettingsPacket(buf.readVarInt(), buf.readByte());
    }
    public static void handle(C2SNbtSettingsPacket msg, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            var reply = apply(player, msg);
            if (reply != null) PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply);
        });
        context.setPacketHandled(true);
    }

    static S2CNbtSettingsPacket apply(net.minecraft.server.level.ServerPlayer player, C2SNbtSettingsPacket msg) {
        if (player == null || !(player.containerMenu instanceof TransmutationContainer)
                || player.containerMenu.containerId != msg.containerId || msg.flags < -1 || msg.flags > 15) return null;
        var data = TransmutationNbtSettings.get(player.server);
        if (msg.flags >= 0) data.set(player.getUUID(), msg.flags);
        return new S2CNbtSettingsPacket(msg.containerId, data.flags(player.getUUID()));
    }
}
