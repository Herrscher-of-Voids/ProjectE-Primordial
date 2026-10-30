package com.primordial.neoforge.charm;

import java.util.ArrayList;
import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeBlackholeCharmPayloads {
    private NeoForgeBlackholeCharmPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Action.TYPE, Action.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handle(player, payload);
        }));
        registrar.playToClient(State.TYPE, State.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> Client.accept(payload)));
    }

    public record Action(int containerId, int revision, int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(id("charm_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.revision); buf.writeVarInt(msg.action); },
                buf -> new Action(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void handle(ServerPlayer player, Action action) {
        if (player.server.isSameThread() && player.containerMenu instanceof NeoForgeBlackholeCharmMenu menu
                && menu.containerId == action.containerId && menu.revision() == action.revision && menu.stillValid(player))
            menu.clickMenuButton(player, action.action);
    }

    public record State(int containerId, int revision, int filterType, boolean blacklist, CompoundTag rules,
                        ItemStack sample, List<String> candidates, List<String> selected, boolean accepted) implements CustomPacketPayload {
        public State { rules = rules.copy(); sample = sample.copy(); candidates = List.copyOf(candidates); selected = List.copyOf(selected); }
        public static final Type<State> TYPE = new Type<>(id("charm_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> {
                    buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.revision); buf.writeByte(msg.filterType); buf.writeBoolean(msg.blacklist);
                    buf.writeNbt(msg.rules); ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.sample);
                    writeList(buf, msg.candidates); writeList(buf, msg.selected); buf.writeBoolean(msg.accepted);
                }, buf -> {
                    int menu = buf.readVarInt(), revision = buf.readVarInt(), type = buf.readUnsignedByte();
                    boolean black = buf.readBoolean();
                    CompoundTag rules = buf.readNbt();
                    ItemStack sample = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    if (type > 2 || rules == null || !NeoForgeCharmFilters.bounded(rules)
                            || !sample.isEmpty() && sample.save(buf.registryAccess()).sizeInBytes() > 8192)
                        throw new DecoderException("Invalid charm filter state");
                    return new State(menu, revision, type, black, rules, sample, readList(buf), readList(buf), buf.readBoolean());
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static void writeList(RegistryFriendlyByteBuf buf, List<String> values) {
        if (values.size() > 128) throw new IllegalArgumentException("Too many charm fields");
        buf.writeVarInt(values.size()); for (String value : values) buf.writeUtf(value, 256);
    }
    private static List<String> readList(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > 128) throw new DecoderException("Too many charm fields");
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(buf.readUtf(256));
        return values;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }
    private static final class Client {
        static void accept(State state) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoForgeBlackholeCharmMenu menu) menu.accept(state);
        }
    }
}
