/** Safe return path for vanilla local storage items inserted into ProjectE's transmutation table. */
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
        if (stack == null || stack.isEmpty() || !localContainer(stack)
                || stack.has(net.minecraft.core.component.DataComponents.CONTAINER_LOOT)) return null;

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
        ItemInfo persistent = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(shell));
        if (persistent == null) return null;
        ItemStack safeShell = persistent.createStack();
        safeShell.remove(net.minecraft.core.component.DataComponents.CONTAINER);
        safeShell.remove(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        safeShell.remove(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        safeShell.remove(net.minecraft.core.component.DataComponents.CONTAINER_LOOT);
        safeShell.setCount(stack.getCount());
        long value = IEMCProxy.INSTANCE.getSellValue(persistent);
        return value <= 0 ? null : new Plan(safeShell, contents, value);
    }

    public static boolean sell(TransmutationInventory inventory, ItemStack original, Runnable consume) {
        if (!(inventory.player instanceof ServerPlayer player)) return false;
        Plan plan;
        try {
            plan = prepare(original, player.registryAccess());
        } catch (RuntimeException | LinkageError invalid) {
            return false;
        }
        if (plan == null) return false;
        consume.run();
        for (ItemStack item : plan.contents()) player.getInventory().placeItemBackInInventory(item.copy());
        inventory.handleKnowledge(ItemInfo.fromStack(plan.shell()));
        inventory.addEmc(BigInteger.valueOf(plan.shellValue()).multiply(BigInteger.valueOf(original.getCount())));
        return true;
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
