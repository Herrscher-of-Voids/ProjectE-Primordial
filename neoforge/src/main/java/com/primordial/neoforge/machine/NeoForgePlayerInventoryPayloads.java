package com.primordial.neoforge.machine;

import com.primordial.data.SampleMode;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Menu-bound requests only edit the sender's inventory rules; samples never become real items. */
public final class NeoForgePlayerInventoryPayloads {
    private NeoForgePlayerInventoryPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ResourceState.TYPE, ResourceState.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientHandler.accept(payload)));
        registrar.playToServer(Edit.TYPE, Edit.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handleEdit(player, payload);
        }));
        registrar.playToServer(SetEnabled.TYPE, SetEnabled.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handleSetEnabled(player, payload);
        }));
        registrar.playToServer(Clear.TYPE, Clear.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handleClear(player, payload);
        }));
        registrar.playToServer(ChargeHeld.TYPE, ChargeHeld.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handleChargeHeld(player, payload);
        }));
    }

    public record Edit(int containerId, int slot, int amount, int intervalTicks, SampleMode mode,
                       boolean enabled, ItemStack filter) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(id("player_inventory_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.slot); buf.writeVarInt(msg.amount);
                    buf.writeVarInt(msg.intervalTicks); buf.writeEnum(msg.mode); buf.writeBoolean(msg.enabled);
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.filter); },
                buf -> new Edit(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readEnum(SampleMode.class), buf.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetEnabled(int containerId, int[] slots, boolean enabled) implements CustomPacketPayload {
        public SetEnabled { slots = slots.clone(); }
        @Override public int[] slots() { return slots.clone(); }
        public static final Type<SetEnabled> TYPE = new Type<>(id("player_inventory_enabled"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetEnabled> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); writeSlots(buf, msg.slots); buf.writeBoolean(msg.enabled); },
                buf -> new SetEnabled(buf.readVarInt(), readSlots(buf), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Clear(int containerId, int[] slots) implements CustomPacketPayload {
        public Clear { slots = slots.clone(); }
        @Override public int[] slots() { return slots.clone(); }
        public static final Type<Clear> TYPE = new Type<>(id("player_inventory_clear"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Clear> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> { buf.writeVarInt(msg.containerId); writeSlots(buf, msg.slots); },
                buf -> new Clear(buf.readVarInt(), readSlots(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ChargeHeld(int containerId) implements CustomPacketPayload {
        public static final Type<ChargeHeld> TYPE = new Type<>(id("player_inventory_charge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChargeHeld> STREAM_CODEC = StreamCodec.of(
                (buf, msg) -> buf.writeVarInt(msg.containerId), buf -> new ChargeHeld(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ResourceState(int containerId, int manaState, long mana, long manaCapacity,
                                int soulState, long souls, long soulCapacity) implements CustomPacketPayload {
        public static final Type<ResourceState> TYPE = new Type<>(id("player_resource_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResourceState> STREAM_CODEC = StreamCodec.of(
                (buf, state) -> {
                    buf.writeVarInt(state.containerId); buf.writeByte(state.manaState); buf.writeVarLong(state.mana);
                    buf.writeVarLong(state.manaCapacity); buf.writeByte(state.soulState);
                    buf.writeVarLong(state.souls); buf.writeVarLong(state.soulCapacity);
                }, buf -> new ResourceState(buf.readVarInt(), buf.readUnsignedByte(), buf.readVarLong(), buf.readVarLong(),
                        buf.readUnsignedByte(), buf.readVarLong(), buf.readVarLong()));
        public ResourceState {
            if (manaState < 0 || manaState > 2 || soulState < 0 || soulState > 2
                    || mana < 0 || manaCapacity < 0 || souls < 0 || soulCapacity < 0)
                throw new DecoderException("Invalid player resource snapshot");
        }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static ResourceState resourceState(ServerPlayer player, NeoForgePlayerInventoryMenu menu) {
        int[] states = new int[2]; long[] amounts = new long[2], capacities = new long[2];
        var data = NeoForgePlayerInventorySavedData.get(player.server);
        if (player.containerMenu == menu && menu.stillValid(player)) for (int index = 0; index < 2; index++) try {
            var port = com.primordial.neoforge.resource.NeoForgePlayerResourcePorts.find(player, index);
            if (port == null) continue;
            amounts[index] = Math.max(0, port.amount()); capacities[index] = Math.max(0, port.capacity());
            if (capacities[index] > 0) states[index] = data.resourceEnabled(player.getUUID(), index)
                    ? NeoForgePlayerInventoryMenu.ENABLED : NeoForgePlayerInventoryMenu.PAUSED;
        } catch (RuntimeException | LinkageError unavailable) { states[index] = 0; amounts[index] = capacities[index] = 0; }
        return new ResourceState(menu.containerId, states[0], amounts[0], capacities[0], states[1], amounts[1], capacities[1]);
    }

    private static final class ClientHandler {
        static void accept(ResourceState state) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoForgePlayerInventoryMenu menu
                    && menu.containerId == state.containerId()) menu.acceptResourceState(state);
        }
    }

    public static void handleEdit(ServerPlayer player, Edit edit) {
        var menu = menu(player, edit.containerId());
        if (menu == null || edit.slot() < 0 || edit.slot() >= 36 || edit.amount() < 0
                || edit.intervalTicks() < 1 || edit.mode() == null || edit.filter() == null || edit.filter().isEmpty()) return;
        ItemStack actual = player.getInventory().getItem(edit.slot());
        if (actual.isEmpty() || !ItemStack.isSameItemSameComponents(actual, edit.filter())) return;
        var data = NeoForgePlayerInventorySavedData.get(player.server);
        var previous = data.get(player.getUUID(), edit.slot());
        data.set(player.getUUID(), edit.slot(), actual, edit.amount(), edit.intervalTicks(), edit.mode(),
                previous == null ? edit.enabled() : previous.enabled());
        menu.broadcastChanges();
    }

    public static void handleSetEnabled(ServerPlayer player, SetEnabled edit) {
        var menu = menu(player, edit.containerId());
        if (menu == null || !validSlots(edit.slots)) return;
        var data = NeoForgePlayerInventorySavedData.get(player.server);
        for (int slot : edit.slots) data.setEnabled(player.getUUID(), slot, edit.enabled());
        menu.broadcastChanges();
    }

    public static void handleClear(ServerPlayer player, Clear edit) {
        var menu = menu(player, edit.containerId());
        if (menu == null || !validSlots(edit.slots)) return;
        var data = NeoForgePlayerInventorySavedData.get(player.server);
        for (int slot : edit.slots) data.clear(player.getUUID(), slot);
        menu.broadcastChanges();
    }

    public static void handleChargeHeld(ServerPlayer player, ChargeHeld edit) {
        var menu = menu(player, edit.containerId());
        if (menu == null) return;
        ItemStack expected = player.getMainHandItem().copy();
        if (expected.isEmpty()) return;
        int selected = player.getInventory().selected;
        int accepted;
        try {
            var probe = expected.copy().getCapability(Capabilities.EnergyStorage.ITEM);
            if (probe == null || !probe.canReceive()) return;
            accepted = Math.max(0, probe.receiveEnergy(Integer.MAX_VALUE, true));
        } catch (RuntimeException | LinkageError unavailable) { return; }
        // A broken optional capability can only mutate a detached copy before the paid commit.
        long actual = NeoForgeResourceTrades.purchaseEnergy(player.server, player.getUUID(), accepted,
                units -> {
                    ItemStack working = expected.copy();
                    int received;
                    try {
                        var storage = working.getCapability(Capabilities.EnergyStorage.ITEM);
                        if (storage == null || !storage.canReceive()) return 0;
                        received = storage.receiveEnergy((int) units, false);
                    } catch (RuntimeException | LinkageError unavailable) { return 0; }
                    if (received <= 0 || received > units || working.getItem() != expected.getItem()
                            || working.getCount() != expected.getCount() || menu(player, edit.containerId()) != menu
                            || player.getInventory().selected != selected
                            || !ItemStack.matches(player.getMainHandItem(), expected)) return 0;
                    player.getInventory().setItem(selected, working);
                    return received;
                });
        if (actual > 0) { player.getInventory().setChanged(); menu.broadcastChanges(); }
    }

    private static NeoForgePlayerInventoryMenu menu(ServerPlayer player, int id) {
        return player.server.isSameThread() && player.containerMenu instanceof NeoForgePlayerInventoryMenu menu
                && menu.containerId == id && menu.stillValid(player) ? menu : null;
    }

    private static boolean validSlots(int[] slots) {
        if (slots == null || slots.length > 36) return false;
        for (int slot : slots) if (slot < 0 || slot >= 36) return false;
        return true;
    }

    private static void writeSlots(RegistryFriendlyByteBuf buf, int[] slots) {
        if (!validSlots(slots)) throw new IllegalArgumentException("Invalid inventory selection");
        buf.writeByte(slots.length);
        for (int slot : slots) buf.writeByte(slot);
    }

    private static int[] readSlots(RegistryFriendlyByteBuf buf) {
        int count = buf.readUnsignedByte();
        if (count > 36) throw new DecoderException("Too many inventory slots");
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = buf.readUnsignedByte();
            if (slots[i] >= 36) throw new DecoderException("Invalid inventory slot");
        }
        return slots;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path);
    }
}
