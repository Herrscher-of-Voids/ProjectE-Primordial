package com.primordial.neoforge.equipment;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeBlackholeEquipmentPayloads {
    private NeoForgeBlackholeEquipmentPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Open.TYPE, Open.STREAM_CODEC, (message, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) open(player, message.hand);
        }));
        registrar.playToServer(Action.TYPE, Action.STREAM_CODEC, (message, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handle(player, message);
        }));
        registrar.playToServer(Signature.TYPE, Signature.STREAM_CODEC, (message, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) NeoForgeSignatureActions.activate(player);
        }));
    }

    public record Open(InteractionHand hand) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("equipment_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (buf, message) -> buf.writeEnum(message.hand), buf -> new Open(buf.readEnum(InteractionHand.class)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Action(int containerId, String featureId, boolean purchase, String amount, String expectedOwned) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("equipment_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, message) -> {
                    buf.writeVarInt(message.containerId); buf.writeUtf(message.featureId, 64); buf.writeBoolean(message.purchase);
                    buf.writeUtf(message.amount, 2048); buf.writeUtf(message.expectedOwned, 2048);
                }, buf -> new Action(buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readUtf(2048), buf.readUtf(2048)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Signature() implements CustomPacketPayload {
        public static final Type<Signature> TYPE = new Type<>(id("equipment_signature"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Signature> STREAM_CODEC = StreamCodec.unit(new Signature());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void open(ServerPlayer player, InteractionHand hand) {
        if (!player.server.isSameThread() || hand == null || !player.isAlive() || player.isSpectator()
                || player.containerMenu != player.inventoryMenu || !NeoForgeBlackholeGear.isGear(player.getItemInHand(hand))) return;
        player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new NeoForgeBlackholeEquipmentMenu(id, inventory, hand),
                Component.translatable("screen.primordial.equipment.title")), buf -> buf.writeEnum(hand));
    }

    public static void handle(ServerPlayer player, Action action) {
        if (player.server.isSameThread() && player.containerMenu instanceof NeoForgeBlackholeEquipmentMenu menu
                && menu.containerId == action.containerId)
            menu.action(player, action.featureId, action.purchase, action.amount, action.expectedOwned);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }
}
