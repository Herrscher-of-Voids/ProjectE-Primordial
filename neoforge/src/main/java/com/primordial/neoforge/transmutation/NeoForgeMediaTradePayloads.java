/** Server-authoritative carried-media requests and bounded, exact transaction receipts. */
package com.primordial.neoforge.transmutation;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeMediaTradePayloads {
    private static final int MAX_EMC = 16384;
    private static final Map<ServerPlayer, Seen> SEEN = new WeakHashMap<>();
    private record Seen(WeakReference<AbstractContainerMenu> menu, long sequence) {}
    private NeoForgeMediaTradePayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Trade.TYPE, Trade.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Receipt receipt = tradeFor(player, payload);
                if (receipt != null) PacketDistributor.sendToPlayer(player, receipt);
            }
        }));
        registrar.playToClient(Receipt.TYPE, Receipt.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ClientHandler.accept(payload)));
    }

    public record Trade(int menuId, boolean buy, boolean chemical, String id, long sequence) implements CustomPacketPayload {
        public static final Type<Trade> TYPE = new Type<>(NeoForgeMediaTradePayloads.id("transmutation_media_trade"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Trade> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.menuId); buffer.writeBoolean(payload.buy);
                    buffer.writeBoolean(payload.chemical); buffer.writeUtf(payload.id, NeoForgeTransmutationPayloads.MAX_ID);
                    buffer.writeVarLong(payload.sequence); },
                buffer -> new Trade(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), readId(buffer), nonnegative(buffer)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Receipt(int menuId, boolean buy, boolean chemical, long sequence, NeoForgeMediaTradeResult result) implements CustomPacketPayload {
        public static final Type<Receipt> TYPE = new Type<>(id("transmutation_media_receipt"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Receipt> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.menuId); buffer.writeBoolean(payload.buy);
                    buffer.writeBoolean(payload.chemical); buffer.writeVarLong(payload.sequence);
                    buffer.writeEnum(payload.result.status()); buffer.writeUtf(payload.result.id(), NeoForgeTransmutationPayloads.MAX_ID);
                    buffer.writeVarLong(payload.result.units()); buffer.writeUtf(payload.result.emc(), MAX_EMC); },
                buffer -> new Receipt(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), nonnegative(buffer),
                        new NeoForgeMediaTradeResult(buffer.readEnum(NeoForgeMediaTradeResult.Status.class), readId(buffer),
                                nonnegative(buffer), buffer.readUtf(MAX_EMC))));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Replayed clicks, closed menus and unrelated menus cannot perform another transaction. */
    public static Receipt tradeFor(ServerPlayer player, Trade request) {
        if (!NeoForgeTransmutationMediaTrade.currentMenu(player, request.menuId()) || request.sequence() < 0
                || request.id() == null || request.id().length() > NeoForgeTransmutationPayloads.MAX_ID) return null;
        ResourceLocation selected = request.id().isEmpty() ? null : ResourceLocation.tryParse(request.id());
        if (!request.id().isEmpty() && selected == null) return null;
        Seen seen = SEEN.get(player);
        if (seen != null && seen.menu().get() == player.containerMenu && request.sequence() <= seen.sequence()) return null;
        SEEN.put(player, new Seen(new WeakReference<>(player.containerMenu), request.sequence()));
        return new Receipt(request.menuId(), request.buy(), request.chemical(), request.sequence(),
                NeoForgeTransmutationMediaTrade.trade(player, request.menuId(), request.buy(), request.chemical(), selected));
    }

    private static long nonnegative(RegistryFriendlyByteBuf buffer) {
        long value = buffer.readVarLong();
        if (value < 0) throw new DecoderException("Negative media transaction value");
        return value;
    }
    private static String readId(RegistryFriendlyByteBuf buffer) {
        String id = buffer.readUtf(NeoForgeTransmutationPayloads.MAX_ID);
        if (!id.isEmpty() && ResourceLocation.tryParse(id) == null) throw new DecoderException("Invalid media ID");
        return id;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }
    private static final class ClientHandler {
        private static void accept(Receipt receipt) { com.primordial.neoforge.client.NeoForgeTransmutationMediaClient.acceptTrade(receipt); }
    }
}
