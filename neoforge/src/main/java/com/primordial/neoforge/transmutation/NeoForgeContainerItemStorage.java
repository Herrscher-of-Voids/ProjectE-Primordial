package com.primordial.neoforge.transmutation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Reads storage without copying its identity, then drains the live inventory before returning its items. */
final class NeoForgeContainerItemStorage {
    private static final int MAX_SLOTS = 4096;
    private static final int MAX_ITEMS = 256;
    private static final int MAX_EXTRACTIONS = 4096;

    private record Inventory(IItemHandlerModifiable handler, List<ItemStack> slots) {}
    private final List<Inventory> inventories;
    private final List<ItemStack> contents;

    private NeoForgeContainerItemStorage(List<Inventory> inventories, List<ItemStack> contents) {
        this.inventories = inventories;
        this.contents = contents;
    }

    static NeoForgeContainerItemStorage read(ItemStack stack) {
        try {
            List<? extends IItemHandler> handlers;
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("sophisticatedbackpacks")) {
                // Linked endpoints are access keys, not ownership of the remote storage or its upgrades.
                if (has(stack, "sophisticatedcore", "linked_storage_endpoint")
                        || has(stack, "sophisticatedbackpacks", "loot_table")
                        || has(stack, "sophisticatedbackpacks", "template_name")) return null;
                Class<?> type = Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
                Object wrapper = type.getMethod("fromStack", ItemStack.class).invoke(null, stack);
                handlers = List.of((IItemHandler) type.getMethod("getInventoryHandler").invoke(wrapper),
                        (IItemHandler) type.getMethod("getUpgradeHandler").invoke(wrapper));
            } else {
                IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
                if (handler == null) return null;
                handlers = List.of(handler);
            }
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
            return new NeoForgeContainerItemStorage(inventories, List.copyOf(contents));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
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
                            || !ItemStack.isSameItemSameComponents(expected, removed))
                        throw new IllegalStateException("Storage did not extract its advertised contents");
                    remaining -= removed.getCount();
                    ItemStack actual = inventory.handler.getStackInSlot(slot);
                    if (actual.getCount() != remaining || remaining > 0 && !ItemStack.isSameItemSameComponents(expected, actual))
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
            throw new IllegalStateException("Storage could not fully restore a rejected transmutation", failure);
        }
    }

    private static boolean has(ItemStack stack, String namespace, String path) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath(namespace, path));
        return type != null && stack.has(type);
    }
}
