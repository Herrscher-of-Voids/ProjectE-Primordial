package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
import com.primordial.neoforge.acceleration.AccelerationMenu;
import com.primordial.neoforge.acceleration.AccelerationBindingActions;
import com.primordial.neoforge.acceleration.AccelerationSettings;
import com.primordial.neoforge.acceleration.EmcAccelerationService;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;


public record C2SAccelerationActionPacket(int containerId, Action action, UUID target,
                                         AccelerationSettings settings, boolean flag, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<C2SAccelerationActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("primordial", "acceleration_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SAccelerationActionPacket> STREAM_CODEC = StreamCodec.of((buf, msg) -> encode(msg, buf), C2SAccelerationActionPacket::decode);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public enum Action {
        OPEN, CYCLE, SELECT, SAVE, START, PAUSE, REMOVE, START_ALL, PAUSE_ALL, CLEAR_ALL, WORLD,
        AREA_POLICY, IDLE_POLICY, AREA_CLEAR, IDLE_CLEAR, MARK_AREA, MARK_IDLE
    }
    private static final Map<ServerPlayer, int[]> REQUESTS = new WeakHashMap<>();

    public static C2SAccelerationActionPacket open(InteractionHand hand) {
        return new C2SAccelerationActionPacket(-1, Action.OPEN, AccelerationBindingActions.SELECTION,
                AccelerationSettings.read(new net.minecraft.nbt.CompoundTag()), false, hand);
    }

    public static void encode(C2SAccelerationActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeEnum(packet.action); buffer.writeUUID(packet.target);
        var s = packet.settings;
        buffer.writeEnum(s.kind()); buffer.writeVarInt(s.rate());
        buffer.writeVarInt(s.sizeX()); buffer.writeVarInt(s.sizeY()); buffer.writeVarInt(s.sizeZ());
        buffer.writeVarInt(s.offsetX()); buffer.writeVarInt(s.offsetY()); buffer.writeVarInt(s.offsetZ());
        buffer.writeBoolean(s.idleThrottle()); buffer.writeBoolean(s.disableAi());
        buffer.writeBoolean(packet.flag); buffer.writeEnum(packet.hand);
    }

    public static C2SAccelerationActionPacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        Action action = buffer.readEnum(Action.class);
        UUID target = buffer.readUUID();
        var settings = new AccelerationSettings(buffer.readEnum(AccelerationSettings.Kind.class), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean());
        if (!settings.valid()) throw new io.netty.handler.codec.DecoderException("Invalid acceleration settings");
        return new C2SAccelerationActionPacket(container, action, target, settings, buffer.readBoolean(), buffer.readEnum(InteractionHand.class));
    }

    public static void handle(C2SAccelerationActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> apply(context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer ? serverPlayer : null, packet));
    }

    static void apply(ServerPlayer player, C2SAccelerationActionPacket packet) {
        if (player == null || !player.isAlive() || player.isSpectator() || !packet.settings.valid()) return;
        int[] limit = REQUESTS.computeIfAbsent(player, ignored -> new int[]{player.server.getTickCount(), 0});
        if (limit[0] != player.server.getTickCount()) { limit[0] = player.server.getTickCount(); limit[1] = 0; }
        if (++limit[1] > 8) return;
        var tool = player.getItemInHand(packet.hand);
        if (!(tool.getItem() instanceof EmcAcceleratorItem)) return;
        try (var edit = AccelerationData.edit(tool)) {
        if (packet.action == Action.OPEN || packet.action == Action.CYCLE) {
            if (player.containerMenu != player.inventoryMenu) return;
            if (packet.action == Action.OPEN) player.openMenu(
                    new SimpleMenuProvider((id, inv, owner) -> new AccelerationMenu(id, inv, packet.hand),
                            Component.translatable("screen.primordial.accelerator.title")), buffer -> buffer.writeEnum(packet.hand));
            else EmcAccelerationService.cycleMultiplier(tool, player, !packet.flag);
        } else if (player.containerMenu instanceof AccelerationMenu menu && menu.containerId == packet.containerId
                && menu.hand() == packet.hand && menu.stillValid(player)) menu.apply(packet);
        }
    }
}
