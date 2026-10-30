/** Real, single-container media slots matching the Forge transmutation page. */
package com.primordial.neoforge.transmutation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class NeoForgeMediaContainerSlot extends Slot {
    private final NeoForgeMediaChargingMenu menu;
    private final Player player;
    private final boolean unlearn;

    public NeoForgeMediaContainerSlot(Container inventory, int index, int x, int y,
                                     NeoForgeMediaChargingMenu menu, Player player, boolean unlearn) {
        super(inventory, index, x, y);
        this.menu = menu;
        this.player = player;
        this.unlearn = unlearn;
    }
    @Override public boolean isActive() { return menu.primordial$mediaPage(); }
    @Override public boolean mayPickup(Player player) { return isActive() && super.mayPickup(player); }
    @Override public boolean mayPlace(ItemStack stack) {
        if (!isActive() || stack == null || stack.isEmpty()) return false;
        try { return unlearn ? !hasItem() : stack.getCapability(Capabilities.EnergyStorage.ITEM) != null; }
        catch (RuntimeException | LinkageError unavailable) { return false; }
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public void set(ItemStack stack) {
        super.set(stack);
        if (unlearn && isActive() && !stack.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            boolean forgotten = NeoForgeMediaSlotTransactions.forget(serverPlayer, stack, menu.primordial$chemicalPage());
            player.displayClientMessage(Component.translatable(forgotten
                    ? "message.primordial.media_forgotten" : "message.primordial.media_forget_failed"), true);
        }
    }
}
