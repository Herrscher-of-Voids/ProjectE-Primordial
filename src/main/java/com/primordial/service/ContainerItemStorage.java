package com.primordial.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraft.core.registries.BuiltInRegistries;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/** Reads storage without copying its identity, then drains the live inventory before returning its items. */
final class ContainerItemStorage {
    private static final int MAX_SLOTS = 4096;
    private static final int MAX_ITEMS = 256;
    private static final int MAX_EXTRACTIONS = 4096;

    private record Inventory(IItemHandlerModifiable handler, List<ItemStack> slots) {}
    private final List<Inventory> inventories;
    private final List<ItemStack> contents;

    private ContainerItemStorage(List<Inventory> inventories, List<ItemStack> contents) {
        this.inventories = inventories;
        this.contents = contents;
    }

    static ContainerItemStorage read(ItemStack stack) {
        try {
            List<? extends IItemHandler> handlers;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("sophisticatedbackpacks")) {
                var tag = stack.getTag();
                if (tag != null && (tag.contains("lootTableName") || tag.contains("templateName")
                        || tag.contains("linkedStorageEndpoint"))) return null;
                Class<?> capabilityType = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper");
                Capability<?> capability = (Capability<?>) capabilityType.getMethod("getCapabilityInstance").invoke(null);
                Object wrapper = stack.getCapability(capability).orElse(null);
                if (wrapper == null) return null;
                Class<?> type = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
                handlers = List.of((IItemHandler) type.getMethod("getInventoryHandler").invoke(wrapper),
                        (IItemHandler) type.getMethod("getUpgradeHandler").invoke(wrapper));
            } else {
                IItemHandler handler = stack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
                if (handler == null) return null;
                handlers = List.of(handler);
            }
            return snapshot(stack, handlers);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            return null;
        }
    }

    static ContainerItemStorage snapshot(ItemStack stack, List<? extends IItemHandler> handlers) {
        try {
            List<Inventory> inventories = new ArrayList<>();
            List<ItemStack> contents = new ArrayList<>();
            int slots = 0;
            for (IItemHandler handler : handlers) {
                if (!(handler instanceof IItemHandlerModifiable writable)) return null;
                int size = handler.getSlots();
                if (size < 0 || size > MAX_SLOTS - slots) return null;
                slots += size;
                List<ItemStack> snapshot = new ArrayList<>(size);
                for (int slot = 0; slot < size; slot++) {
                    ItemStack item = handler.getStackInSlot(slot);
                    if (item == null || !item.isEmpty() && item.getCount() <= 0) return null;
                    snapshot.add(item.copy());
                    if (!item.isEmpty()) contents.add(item.copy());
                    if (contents.size() > MAX_ITEMS) return null;
                }
                inventories.add(new Inventory(writable, snapshot));
            }
            if (!contents.isEmpty() && stack.getCount() != 1) return null;
            return new ContainerItemStorage(inventories, List.copyOf(contents));
        } catch (RuntimeException | LinkageError unsupported) {
            return null;
        }
    }

    List<ItemStack> contents() { return contents; }

    boolean sameContents(List<ItemStack> expected) {
        if (contents.size() != expected.size()) return false;
        for (int i = 0; i < contents.size(); i++) if (!ItemStack.matches(contents.get(i), expected.get(i))) return false;
        return true;
    }

    boolean drain() {
        try {
            for (Inventory inventory : inventories) {
                if (inventory.handler.getSlots() != inventory.slots.size()) return false;
                for (int slot = 0; slot < inventory.slots.size(); slot++)
                    if (!ItemStack.matches(inventory.handler.getStackInSlot(slot), inventory.slots.get(slot))) return false;
            }
            int operations = 0;
            for (Inventory inventory : inventories) for (int slot = 0; slot < inventory.slots.size(); slot++) {
                ItemStack expected = inventory.slots.get(slot);
                int remaining = expected.getCount();
                while (remaining > 0) {
                    if (++operations > MAX_EXTRACTIONS) throw new IllegalStateException("Storage extraction limit exceeded");
                    ItemStack removed = inventory.handler.extractItem(slot, remaining, false);
                    if (removed == null || removed.isEmpty() || removed.getCount() > remaining
                            || !ItemStack.isSameItemSameTags(expected, removed))
                        throw new IllegalStateException("Storage did not extract its advertised contents");
                    remaining -= removed.getCount();
                    ItemStack actual = inventory.handler.getStackInSlot(slot);
                    if (actual.getCount() != remaining || remaining > 0 && !ItemStack.isSameItemSameTags(expected, actual))
                        throw new IllegalStateException("Storage extraction did not remove exactly the returned items");
                }
            }
            for (Inventory inventory : inventories) {
                if (inventory.handler.getSlots() != inventory.slots.size()) throw new IllegalStateException("Storage layout changed");
                for (int slot = 0; slot < inventory.slots.size(); slot++)
                    if (!inventory.handler.getStackInSlot(slot).isEmpty()) throw new IllegalStateException("Storage refilled during extraction");
            }
            return true;
        } catch (RuntimeException | LinkageError failed) {
            restore();
            return false;
        }
    }

    void restore() {
        // Restore upgrades first: slot counts and stack limits can depend on them.
        Throwable failure = null;
        for (int i = inventories.size() - 1; i >= 0; i--) {
            Inventory inventory = inventories.get(i);
            for (int slot = 0; slot < inventory.slots.size(); slot++) {
                try {
                    inventory.handler.setStackInSlot(slot, inventory.slots.get(slot).copy());
                    if (!ItemStack.matches(inventory.handler.getStackInSlot(slot), inventory.slots.get(slot)))
                        throw new IllegalStateException("Storage refused to restore a rejected transmutation");
                } catch (RuntimeException | LinkageError invalid) {
                    if (failure == null) failure = invalid;
                    else failure.addSuppressed(invalid);
                }
            }
        }
        if (failure != null) {
            com.mojang.logging.LogUtils.getLogger().error("[Primordial] Item handler failed to restore rejected container contents", failure);
            throw new IllegalStateException("Could not restore container storage", failure);
        }
    }

}
