package com.primordial.neoforge.acceleration;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record S2CAccelerationWarningPacket(UUID token, int multiplier, BlockPos pos, int targets, String cost) implements CustomPacketPayload {
    public static final Type<S2CAccelerationWarningPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("primordial", "acceleration_warning"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CAccelerationWarningPacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> encode(msg, buf), S2CAccelerationWarningPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void send(net.minecraft.server.level.ServerPlayer player, UUID token, int multiplier, BlockPos pos, int targets, String cost) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new S2CAccelerationWarningPacket(token, multiplier, pos, targets, cost));
    }

    public static void encode(S2CAccelerationWarningPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.token); buf.writeVarInt(msg.multiplier); buf.writeBlockPos(msg.pos); buf.writeVarInt(msg.targets); buf.writeUtf(msg.cost, 160);
    }

    public static S2CAccelerationWarningPacket decode(FriendlyByteBuf buf) {
        return new S2CAccelerationWarningPacket(buf.readUUID(), buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readUtf(160));
    }

    public static void handle(S2CAccelerationWarningPacket msg, IPayloadContext context) {
        context.enqueueWork(() -> ClientHandler.open(msg));
    }

    private static final class ClientHandler {
        static void open(S2CAccelerationWarningPacket msg) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null) return;
            var previous = minecraft.screen;
            var container = minecraft.player.containerMenu;
            var screen = new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(new C2SConfirmAccelerationPacket(msg.token, accepted));
                minecraft.setScreen(previous instanceof com.primordial.neoforge.acceleration.AccelerationScreen
                        && minecraft.player != null && minecraft.player.containerMenu == container ? previous : null);
            }, Component.translatable("screen.primordial.acceleration_warning_title"),
                    Component.translatable("screen.primordial.acceleration_warning_body", msg.targets, msg.multiplier,
                            msg.pos.getX(), msg.pos.getY(), msg.pos.getZ(), msg.cost),
                    Component.translatable("screen.primordial.acceleration_confirm"), Component.translatable("gui.cancel"));
            minecraft.setScreen(screen);
            screen.setDelay(20);
        }
    }
}
