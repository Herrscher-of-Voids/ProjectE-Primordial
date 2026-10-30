/** Bounded JEI media quotes and authenticated one-stack item purchases; no JEI/client classes on the server. */
package com.primordial.neoforge.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.primordial.service.EmcAmount;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeJeiPayloads {
    public static final int LIMIT = 128;
    private static final int MAX_PRICE = 4354;
    private static final Map<ServerPlayer, Integer> LAST_QUERY = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_PURCHASE = new WeakHashMap<>();
    private NeoForgeJeiPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(RequestQuotes.TYPE, RequestQuotes.STREAM_CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Quotes result = quotesFor(player, request);
                if (result != null) PacketDistributor.sendToPlayer(player, result);
            }
        }));
        registrar.playToClient(Quotes.TYPE, Quotes.STREAM_CODEC, (result, context) ->
                context.enqueueWork(() -> ClientHandler.accept(result)));
        registrar.playToServer(Purchase.TYPE, Purchase.STREAM_CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) purchaseFor(player, request);
        }));
    }

    public record Key(boolean chemical, ResourceLocation id) {
        public Key { if (id == null || id.toString().length() > 128) throw new IllegalArgumentException("Invalid JEI media ID"); }
        void write(RegistryFriendlyByteBuf buffer) { buffer.writeBoolean(chemical); buffer.writeUtf(id.toString(), 128); }
        static Key read(RegistryFriendlyByteBuf buffer) {
            boolean chemical = buffer.readBoolean();
            ResourceLocation id = ResourceLocation.tryParse(buffer.readUtf(128));
            if (id == null) throw new DecoderException("Invalid JEI media ID");
            return new Key(chemical, id);
        }
    }

    public record Entry(Key key, String price) {}

    public record RequestQuotes(long sequence, List<Key> keys) implements CustomPacketPayload {
        public RequestQuotes { keys = List.copyOf(keys); if (keys.size() > LIMIT) throw new IllegalArgumentException("Too many JEI quotes"); }
        public static final Type<RequestQuotes> TYPE = new Type<>(id("jei_media_quotes_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestQuotes> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarLong(value.sequence); buffer.writeVarInt(value.keys.size());
                    value.keys.forEach(key -> key.write(buffer)); },
                buffer -> { long sequence = readSequence(buffer); int count = readCount(buffer); List<Key> keys = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) keys.add(Key.read(buffer)); return new RequestQuotes(sequence, keys); });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Quotes(long sequence, List<Entry> entries) implements CustomPacketPayload {
        public Quotes { entries = List.copyOf(entries); if (entries.size() > LIMIT) throw new IllegalArgumentException("Too many JEI quotes"); }
        public static final Type<Quotes> TYPE = new Type<>(id("jei_media_quotes"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Quotes> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarLong(value.sequence); buffer.writeVarInt(value.entries.size());
                    for (Entry entry : value.entries) { entry.key.write(buffer); buffer.writeUtf(entry.price, MAX_PRICE); } },
                buffer -> { long sequence = readSequence(buffer); int count = readCount(buffer); List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) entries.add(new Entry(Key.read(buffer), buffer.readUtf(MAX_PRICE)));
                    return new Quotes(sequence, entries); });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Purchase(int menuId, long sequence, ItemStack sample) implements CustomPacketPayload {
        public Purchase { sample = sample == null ? ItemStack.EMPTY : sample.copyWithCount(1); }
        @Override public ItemStack sample() { return sample.copy(); }
        public static final Type<Purchase> TYPE = new Type<>(id("jei_item_purchase"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Purchase> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarInt(value.menuId); buffer.writeVarLong(value.sequence);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, value.sample); },
                buffer -> new Purchase(buffer.readVarInt(), readSequence(buffer), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Knowledge, stages, price direction and policy generations use the actual trading quote. */
    public static EmcAmount quote(ServerPlayer player, Key key) {
        if (player == null || key == null || !player.server.isSameThread()) return EmcAmount.ZERO;
        if (key.chemical) return NeoForgeResourceTrades.purchaseChemicalPrice(player.server, player.getUUID(), key.id,
                NeoForgeMediaRegistry.chemicalTags(key.id));
        return BuiltInRegistries.FLUID.containsKey(key.id)
                ? NeoForgeResourceTrades.purchaseFluidPrice(player.server, player.getUUID(),
                        new FluidStack(BuiltInRegistries.FLUID.get(key.id), 1)) : EmcAmount.ZERO;
    }

    public static Quotes quotesFor(ServerPlayer player, RequestQuotes request) {
        if (player == null || request == null || !player.server.isSameThread() || request.sequence < 0 || request.keys.size() > LIMIT) return null;
        Integer previous = LAST_QUERY.get(player);
        if (previous != null && player.tickCount - previous < 10) return null;
        LAST_QUERY.put(player, player.tickCount);
        return new Quotes(request.sequence, request.keys.stream().map(key -> new Entry(key, quote(player, key).toString())).toList());
    }

    /** At most a real item stack, delivered into the 36 ordinary inventory slots; partial delivery alone is billed. */
    public static long purchaseFor(ServerPlayer player, Purchase request) {
        if (player == null || request == null || !player.server.isSameThread() || player.containerMenu.containerId != request.menuId
                || !player.isAlive() || player.hasDisconnected() || player.server.getPlayerList().getPlayer(player.getUUID()) != player
                || !player.containerMenu.stillValid(player) || request.sequence < 0 || request.sample.isEmpty()) return 0;
        Long previous = LAST_PURCHASE.get(player);
        if (previous != null && request.sequence <= previous) return 0;
        LAST_PURCHASE.put(player, request.sequence);
        ItemStack sample = request.sample.copyWithCount(1);
        long inserted = NeoForgeResourceTrades.purchaseItem(player.server, player.getUUID(), sample, sample.getMaxStackSize(),
                units -> insert(player, sample.copyWithCount((int) units)));
        if (inserted > 0) { player.getInventory().setChanged(); player.containerMenu.broadcastChanges(); }
        return inserted;
    }

    private static long insert(ServerPlayer player, ItemStack output) {
        int before = output.getCount();
        // Merge first, then fill empties, without touching armor, offhand, crafting slots or the cursor.
        for (int pass = 0; pass < 2 && !output.isEmpty(); pass++) for (int slot = 0; slot < 36 && !output.isEmpty(); slot++) {
            ItemStack present = player.getInventory().getItem(slot);
            if (pass == 0 && !present.isEmpty() && ItemStack.isSameItemSameComponents(present, output)) {
                int amount = Math.min(output.getCount(), Math.max(0, present.getMaxStackSize() - present.getCount()));
                present.grow(amount); output.shrink(amount);
            } else if (pass == 1 && present.isEmpty()) {
                int amount = Math.min(output.getCount(), output.getMaxStackSize());
                player.getInventory().setItem(slot, output.copyWithCount(amount)); output.shrink(amount);
            }
        }
        return before - output.getCount();
    }

    private static int readCount(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > LIMIT) throw new DecoderException("Invalid JEI media quote count");
        return count;
    }
    private static long readSequence(RegistryFriendlyByteBuf buffer) {
        long value = buffer.readVarLong();
        if (value < 0) throw new DecoderException("Invalid JEI sequence");
        return value;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }
    private static final class ClientHandler {
        static void accept(Quotes result) { com.primordial.neoforge.client.NeoForgeJeiMediaQuotes.accept(result); }
    }
}
