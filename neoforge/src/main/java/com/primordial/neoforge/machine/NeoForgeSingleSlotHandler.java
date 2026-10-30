package com.primordial.neoforge.machine;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** Exposes exactly one selected source slot as slot zero, without a set-stack bypass. */
public final class NeoForgeSingleSlotHandler implements IItemHandler {
    private final IItemHandler source;
    private final int target;

    public NeoForgeSingleSlotHandler(IItemHandler source, int target) {
        if (target < 0 || target >= source.getSlots()) throw new IllegalArgumentException("Invalid target slot");
        this.source = source;
        this.target = target;
    }

    private void check(int slot) {
        if (slot != 0) throw new IndexOutOfBoundsException("Single-slot view: " + slot);
    }

    @Override public int getSlots() { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { check(slot); return source.getStackInSlot(target); }
    @Override public int getSlotLimit(int slot) { check(slot); return source.getSlotLimit(target); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { check(slot); return source.isItemValid(target, stack); }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        check(slot);
        return source.insertItem(target, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        check(slot);
        return source.extractItem(target, amount, simulate);
    }
}
