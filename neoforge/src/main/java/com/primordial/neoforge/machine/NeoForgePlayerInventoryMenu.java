package com.primordial.neoforge.machine;

import com.primordial.neoforge.NeoForgeModRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The 1.20.1 player-slot layout, with authoritative rule status and no item movement. */
public final class NeoForgePlayerInventoryMenu extends AbstractContainerMenu {
    public static final int SLOT_COUNT = 36;
    public static final int UNCONFIGURED = 0, ENABLED = 1, PAUSED = 2;
    private final Player owner;
    private final ContainerData ruleStates;
    private NeoForgePlayerInventoryPayloads.ResourceState resourceSnapshot;
    private int lastResourceTick = Integer.MIN_VALUE;

    public NeoForgePlayerInventoryMenu(int id, Inventory inventory) {
        super(NeoForgeModRegistries.PLAYER_INVENTORY_MENU.get(), id);
        owner = inventory.player;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new ReadOnlyInventorySlot(inventory, 9 + row * 9 + col, 43 + col * 18, 24 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new ReadOnlyInventorySlot(inventory, col, 43 + col * 18, 82));
        ruleStates = createRuleStates(inventory);
        addDataSlots(ruleStates);
    }

    private static ContainerData createRuleStates(Inventory inventory) {
        if (!(inventory.player instanceof ServerPlayer player)) return new SimpleContainerData(SLOT_COUNT);
        var data = NeoForgePlayerInventorySavedData.get(player.server);
        var owner = player.getUUID();
        return new ContainerData() {
            @Override public int get(int index) {
                if (index < 0 || index >= SLOT_COUNT) return UNCONFIGURED;
                var rule = data.get(owner, inventoryIndex(index));
                return rule == null ? UNCONFIGURED : rule.enabled() ? ENABLED : PAUSED;
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return SLOT_COUNT; }
        };
    }

    public int ruleState(int menuSlot) {
        return menuSlot >= 0 && menuSlot < SLOT_COUNT ? ruleStates.get(menuSlot) : UNCONFIGURED;
    }

    public int resourceState(int index) {
        if (index < 0 || index >= 2 || resourceSnapshot == null) return UNCONFIGURED;
        return index == 0 ? resourceSnapshot.manaState() : resourceSnapshot.soulState();
    }
    public boolean resourceReady(int index) { return index >= 0 && index < 2 && resourceSnapshot != null; }
    public long resourceStored(int index) {
        return !resourceReady(index) ? 0 : index == 0 ? resourceSnapshot.mana() : resourceSnapshot.souls();
    }
    public long resourceCapacity(int index) {
        return !resourceReady(index) ? 0 : index == 0 ? resourceSnapshot.manaCapacity() : resourceSnapshot.soulCapacity();
    }
    public void acceptResourceState(NeoForgePlayerInventoryPayloads.ResourceState state) {
        if (state.containerId() == containerId) resourceSnapshot = state;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        int resource = button - 100;
        if (player != owner || !(player instanceof ServerPlayer serverPlayer) || !serverPlayer.server.isSameThread()
                || serverPlayer.containerMenu != this || !stillValid(player) || resource < 0 || resource >= 2) return false;
        var port = com.primordial.neoforge.resource.NeoForgePlayerResourcePorts.find(serverPlayer, resource);
        if (port == null || port.capacity() <= 0) return false;
        NeoForgePlayerInventorySavedData.get(serverPlayer.server).toggleResource(serverPlayer.getUUID(), resource);
        lastResourceTick = Integer.MIN_VALUE;
        broadcastChanges();
        return true;
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(owner instanceof ServerPlayer player) || player.containerMenu != this) return;
        int tick = player.server.getTickCount();
        if (resourceSnapshot != null && tick - (long) lastResourceTick < 10) return;
        lastResourceTick = tick;
        var current = NeoForgePlayerInventoryPayloads.resourceState(player, this);
        if (!current.equals(resourceSnapshot)) {
            resourceSnapshot = current;
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, current);
        }
    }

    public static int inventoryIndex(int menuSlot) {
        return menuSlot < 27 ? 9 + menuSlot : menuSlot - 27;
    }

    @Override public boolean stillValid(Player player) { return player == owner; }
    @Override public void clicked(int slotId, int button, ClickType type, Player player) { }
    @Override public boolean canDragTo(Slot slot) { return false; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return false; }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    private static final class ReadOnlyInventorySlot extends Slot {
        private ReadOnlyInventorySlot(Inventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
        @Override public void onTake(Player player, ItemStack stack) { }
    }
}
