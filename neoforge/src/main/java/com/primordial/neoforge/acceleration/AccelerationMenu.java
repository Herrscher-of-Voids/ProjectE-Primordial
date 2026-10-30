package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
import com.primordial.neoforge.acceleration.*;
import com.primordial.neoforge.acceleration.*;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AccelerationMenu extends AbstractContainerMenu {
    private final Player owner;
    private final InteractionHand hand;
    private final ItemStack tool;
    private CompoundTag state = new CompoundTag();
    private UUID selected = AccelerationBindingActions.SELECTION;
    private int revision, lastSync = Integer.MIN_VALUE;
    private boolean accepted = true;
    private String quote = "0";

    public AccelerationMenu(int id, Inventory inventory, InteractionHand hand) {
        super(com.primordial.neoforge.NeoForgeModRegistries.ACCELERATION_MENU.get(), id);
        this.owner = inventory.player; this.hand = hand; this.tool = owner.getItemInHand(hand);
        if (owner instanceof ServerPlayer) {
            try (var edit = AccelerationData.edit(tool)) { AccelerationBindingActions.prepare(tool); }
        }
    }

    @Override public boolean stillValid(Player player) {
        return player == owner && player.isAlive() && !player.isSpectator() && player.getItemInHand(hand) == tool
                && tool.getItem() instanceof EmcAcceleratorItem;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public void clicked(int slot, int button, ClickType type, Player player) { }
    @Override public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.inventoryMenu.sendAllDataToRemote();
    }

    public void apply(C2SAccelerationActionPacket request) {
        if (!(owner instanceof ServerPlayer player) || !stillValid(player)) return;
        var action = request.action();
        UUID id = request.target();
        accepted = true;
        switch (action) {
            case SELECT -> {
                if (id.equals(AccelerationBindingActions.SELECTION) || AccelerationBindings.find(tool, id) != null) selected = id;
                else accepted = false;
            }
            case SAVE -> accepted = AccelerationBindingActions.edit(tool, player, id, request.settings());
            case START -> accepted = AccelerationBindingActions.start(tool, player, hand, id);
            case PAUSE -> accepted = AccelerationBindingActions.pause(tool, player, id, false);
            case REMOVE -> {
                accepted = AccelerationBindingActions.pause(tool, player, id, true);
                if (accepted && id.equals(selected)) selected = AccelerationBindingActions.SELECTION;
            }
            case START_ALL -> AccelerationBindingActions.startAll(tool, player, hand);
            case PAUSE_ALL -> AccelerationBindingActions.pauseAll(tool, player);
            case CLEAR_ALL -> { EmcAccelerationService.clear(tool, player); selected = AccelerationBindingActions.SELECTION; }
            case WORLD -> {
                accepted = request.settings().kind().world() && AccelerationBindingActions.edit(tool, player, AccelerationBindingActions.SELECTION, request.settings());
                if (accepted) accepted = AccelerationBindingActions.world(tool, player, hand);
            }
            case AREA_POLICY, AREA_CLEAR, IDLE_POLICY, IDLE_CLEAR -> accepted = AccelerationBindingActions.policy(tool, player, id,
                    action == C2SAccelerationActionPacket.Action.IDLE_POLICY || action == C2SAccelerationActionPacket.Action.IDLE_CLEAR,
                    request.flag(), action == C2SAccelerationActionPacket.Action.AREA_CLEAR || action == C2SAccelerationActionPacket.Action.IDLE_CLEAR);
            case MARK_AREA, MARK_IDLE -> {
                accepted = AccelerationBindingActions.marking(tool, id, action == C2SAccelerationActionPacket.Action.MARK_IDLE);
                if (accepted) { owner.getInventory().setChanged(); player.closeContainer(); return; }
            }
            default -> accepted = false;
        }
        owner.getInventory().setChanged();
        send();
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer player && (lastSync == Integer.MIN_VALUE || player.server.getTickCount() - lastSync >= 10)) send();
    }

    private void send() {
        if (!(owner instanceof ServerPlayer player)) return;
        lastSync = player.server.getTickCount();
        CompoundTag snapshot = new CompoundTag();
        CompoundTag selection = new CompoundTag();
        AccelerationSettings.read(AccelerationData.get(tool)).write(selection);
        snapshot.put("Selection", selection);
        var targets = new ListTag();
        for (CompoundTag entry : AccelerationBindings.view(tool)) {
            CompoundTag summary = new CompoundTag();
            AccelerationSettings.read(entry).write(summary);
            summary.putUUID("BindingId", AccelerationBindings.id(entry));
            for (String key : new String[]{EmcAcceleratorItem.DIMENSION_KEY, EmcAcceleratorItem.X_KEY, EmcAcceleratorItem.Y_KEY,
                    EmcAcceleratorItem.Z_KEY, EmcAcceleratorItem.ENABLED_KEY, EmcAcceleratorItem.WARNING_PENDING_KEY,
                    "AreaWhitelist", "IdleWhitelist", "EntityName", "EntityType", EmcAcceleratorItem.BLOCK_KEY}) {
                Tag value = entry.get(key);
                if (value != null && value.sizeInBytes() <= 1024) summary.put(key, value.copy());
            }
            summary.putInt("AreaMarkCount", Math.min(3375, entry.getLongArray("AreaMarks").length));
            summary.putInt("IdleMarkCount", Math.min(3375, entry.getLongArray("IdleMarks").length));
            targets.add(summary);
        }
        snapshot.put("Targets", targets);
        snapshot.putString("Remaining", AccelerationRun.remaining(tool).toString());
        snapshot.putInt("TicksLeft", AccelerationRun.expired(tool, player.server) ? 0
                : (int) Math.max(0, Math.min(600, AccelerationData.get(tool).getLong("RunDeadline") - player.server.getTickCount())));
        snapshot.putLong("DayTime", player.server.overworld().getDayTime());
        snapshot.putString("Weather", player.server.overworld().getLevelData().isThundering() ? "thunder"
                : player.server.overworld().getLevelData().isRaining() ? "rain" : "clear");
        state = snapshot;
        var settings = AccelerationSettings.read(selectedState());
        int count = settings.kind() == AccelerationSettings.Kind.AREA ? settings.volume() : 1;
        quote = EmcAccelerationService.costPerSecond(settings.rate()).multiply(BigInteger.valueOf(count)).toString();
        if (snapshot.sizeInBytes() <= S2CAccelerationStatePacket.MAX_BYTES)
            PacketDistributor.sendToPlayer(player,
                    new S2CAccelerationStatePacket(containerId, ++revision, selected, snapshot, quote, accepted));
    }

    public void accept(S2CAccelerationStatePacket packet) {
        if (packet.containerId() != containerId || packet.revision() <= revision) return;
        state = packet.state(); selected = packet.selected(); revision = packet.revision(); quote = packet.quote(); accepted = packet.accepted();
    }
    public CompoundTag selectedState() {
        if (!selected.equals(AccelerationBindingActions.SELECTION)) {
            for (Tag tag : targets()) if (tag instanceof CompoundTag entry && AccelerationBindings.id(entry).equals(selected)) return entry;
        }
        return state.getCompound("Selection");
    }
    public ListTag targets() { return state.getList("Targets", Tag.TAG_COMPOUND); }
    public InteractionHand hand() { return hand; }
    public UUID selected() { return selected; }
    public int revision() { return revision; }
    public boolean accepted() { return accepted; }
    public String quote() { return quote; }
    public CompoundTag state() { return state; }
}
