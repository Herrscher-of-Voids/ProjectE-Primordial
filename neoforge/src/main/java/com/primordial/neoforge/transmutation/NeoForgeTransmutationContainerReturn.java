/** Return contained items and settle only a sanitized, empty storage item in ProjectE's transmutation table. */
package com.primordial.neoforge.transmutation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class NeoForgeTransmutationContainerReturn {
    private static final Set<String> LOCAL = Set.of("chest", "trapped_chest", "barrel", "bundle", "shulker_box");
    private static final int MAX_CONTENTS = 256;
    private static final ThreadLocal<Boolean> SETTLING = ThreadLocal.withInitial(() -> false);

    public record Plan(ItemStack shell, List<ItemStack> contents, long shellValue) {
        public Plan { contents = List.copyOf(contents); }
    }

    private NeoForgeTransmutationContainerReturn() {}

    public static boolean handles(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (localContainer(stack)
                || stack.has(net.minecraft.core.component.DataComponents.CONTAINER)
                || stack.has(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS)
                || stack.has(net.minecraft.core.component.DataComponents.CONTAINER_LOOT)
                || stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                || stack.getCapability(Capabilities.ItemHandler.ITEM) != null);
    }

    public static Plan prepare(ItemStack stack, net.minecraft.core.HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()
                || stack.has(net.minecraft.core.component.DataComponents.CONTAINER_LOOT)) return null;
        if (!localContainer(stack)) {
            NeoForgeContainerItemStorage storage = NeoForgeContainerItemStorage.read(stack.copy());
            return storage == null ? null : plan(stack, storage.contents());
        }

        boolean bundleItem = isBundle(stack);
        boolean hasContainer = stack.has(net.minecraft.core.component.DataComponents.CONTAINER);
        boolean hasBundle = stack.has(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        boolean hasBlockData = stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (bundleItem ? hasContainer || hasBlockData : hasBundle || hasContainer && hasBlockData) return null;

        List<List<ItemStack>> sources = new ArrayList<>(3);
        ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        BundleContents bundle = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        if (!bundleItem && hasContainer && container != null) {
            List<ItemStack> copied = copy(container.nonEmptyItemsCopy());
            if (copied == null) return null;
            sources.add(copied);
        }
        if (bundleItem && hasBundle && bundle != null) {
            List<ItemStack> copied = copy(bundle.itemsCopy());
            if (copied == null) return null;
            sources.add(copied);
        }

        var blockData = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (!bundleItem && hasBlockData && blockData != null) {
            CompoundTag tag = blockData.copyTag();
            if (tag.contains("LootTable") || tag.contains("Items") && !tag.contains("Items", Tag.TAG_LIST)) return null;
            if (tag.get("Items") instanceof ListTag entries) {
                if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) return null;
                List<ItemStack> parsed = new ArrayList<>(entries.size());
                Set<Integer> slots = new HashSet<>();
                for (Tag entry : entries) {
                    if (!(entry instanceof CompoundTag compound)) return null;
                    if (!compound.contains("Slot", Tag.TAG_BYTE)) return null;
                    int slot = compound.getByte("Slot") & 0xFF;
                    if (slot >= 27 || !slots.add(slot)) return null;
                    ItemStack content = ItemStack.parseOptional(registries, compound);
                    if (content.isEmpty() || content.getCount() <= 0) return null;
                    parsed.add(content);
                }
                sources.add(parsed);
            }
        }

        List<List<ItemStack>> nonEmpty = sources.stream().filter(source -> !source.isEmpty()).toList();
        if (nonEmpty.size() > 1) return null;
        List<ItemStack> contents = nonEmpty.isEmpty() ? List.of() : nonEmpty.getFirst();
        if (contents.size() > MAX_CONTENTS || !contents.isEmpty() && stack.getCount() != 1) return null;

        ItemStack shell = stack.copy();
        shell.remove(net.minecraft.core.component.DataComponents.CONTAINER);
        shell.remove(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        shell.remove(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        shell.remove(net.minecraft.core.component.DataComponents.CONTAINER_LOOT);
        return plan(shell, contents);
    }

    private static Plan plan(ItemStack shell, List<ItemStack> contents) {
        ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(shell));
        if (persistent == null) return null;
        ItemStack safeShell = persistent.createStack();
        safeShell.remove(net.minecraft.core.component.DataComponents.CONTAINER);
        safeShell.remove(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        safeShell.remove(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        safeShell.remove(net.minecraft.core.component.DataComponents.CONTAINER_LOOT);
        safeShell.setCount(shell.getCount());
        long value = IEMCProxy.INSTANCE.getSellValue(persistent);
        return value <= 0 ? null : new Plan(safeShell, contents, value);
    }

    public static boolean sell(TransmutationInventory inventory, ItemStack original, Runnable consume) {
        if (!(inventory.player instanceof ServerPlayer player) || SETTLING.get()) return false;
        SETTLING.set(true);
        try {
            int count = original.getCount();
            var provider = inventory.provider;
            BigInteger beforeEmc = provider.getEmc();
            Set<ItemInfo> beforeKnowledge = Set.copyOf(provider.getKnowledge());
            boolean beforeFull = provider.hasFullKnowledge();
            Plan plan;
            NeoForgeContainerItemStorage storage = null;
            ReturnInventory returned;
            try {
                plan = prepare(original, player.registryAccess());
                if (plan == null) return false;
                returned = ReturnInventory.prepare(player, original, plan.contents());
                if (returned == null) return false;
                if (!localContainer(original)) {
                    storage = NeoForgeContainerItemStorage.read(original);
                    if (storage == null || !storage.sameContents(plan.contents()) || !storage.drain()) {
                        original.setCount(count);
                        return false;
                    }
                    if (original.getCount() != count) {
                        storage.restore();
                        original.setCount(count);
                        return false;
                    }
                }
            } catch (RuntimeException | LinkageError invalid) {
                original.setCount(count);
                return false;
            }
            try {
                consume.run();
                if (!returned.consumed(player)) throw new IllegalStateException("Container source changed during settlement");
                inventory.handleKnowledge(ItemInfo.fromStack(plan.shell()));
                inventory.addEmc(BigInteger.valueOf(plan.shellValue()).multiply(BigInteger.valueOf(count)));
                returned.publish(player, returned.output);
                return true;
            } catch (RuntimeException | LinkageError failed) {
                try {
                    if (storage != null) storage.restore();
                } finally {
                    original.setCount(count);
                    returned.publish(player, returned.before);
                    try {
                        for (ItemInfo item : Set.copyOf(provider.getKnowledge())) if (!beforeKnowledge.contains(item)) provider.removeKnowledge(item);
                        for (ItemInfo item : beforeKnowledge) if (!provider.hasKnowledge(item)) provider.addKnowledge(item);
                        provider.setFullKnowledge(beforeFull);
                    } finally {
                        provider.setEmc(beforeEmc);
                        provider.sync(player);
                    }
                }
                return false;
            }
        } finally {
            SETTLING.remove();
        }
    }

    private record ReturnInventory(List<ItemStack> before, List<ItemStack> consumed, List<ItemStack> output) {
        static ReturnInventory prepare(ServerPlayer player, ItemStack source, List<ItemStack> contents) {
            var inventory = player.getInventory();
            List<ItemStack> before = new ArrayList<>(inventory.getContainerSize());
            List<ItemStack> consumed = new ArrayList<>(inventory.getContainerSize());
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack item = inventory.getItem(i);
                before.add(item.copy());
                consumed.add(item == source ? ItemStack.EMPTY : item.copy());
            }
            List<ItemStack> output = consumed.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            for (ItemStack item : contents) {
                int remaining = item.getCount();
                for (int pass = 0; pass < 2 && remaining > 0; pass++) for (int slot = 0; slot < inventory.items.size() && remaining > 0; slot++) {
                    ItemStack stored = output.get(slot);
                    if (pass == 0 ? stored.isEmpty() || !ItemStack.isSameItemSameComponents(stored, item) : !stored.isEmpty()) continue;
                    int maximum = Math.min(inventory.getMaxStackSize(), item.getMaxStackSize());
                    int moved = Math.min(remaining, Math.max(0, maximum - stored.getCount()));
                    if (moved > 0) {
                        output.set(slot, item.copyWithCount(stored.getCount() + moved));
                        remaining -= moved;
                    }
                }
                if (remaining != 0) return null;
            }
            return new ReturnInventory(before, consumed, output);
        }

        boolean consumed(ServerPlayer player) {
            for (int i = 0; i < consumed.size(); i++) if (!ItemStack.matches(player.getInventory().getItem(i), consumed.get(i))) return false;
            return true;
        }

        void publish(ServerPlayer player, List<ItemStack> items) {
            for (int i = 0; i < items.size(); i++) player.getInventory().setItem(i, items.get(i).copy());
            player.getInventory().setChanged();
        }
    }

    private static boolean localContainer(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("minecraft")
                && (LOCAL.contains(id.getPath()) || id.getPath().endsWith("_shulker_box"));
    }

    private static boolean isBundle(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("minecraft") && id.getPath().equals("bundle");
    }

    private static List<ItemStack> copy(Iterable<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) return null;
            result.add(stack.copy());
            if (result.size() > MAX_CONTENTS) return null;
        }
        return result;
    }
}
