package com.primordial.neoforge.trade;

import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import moze_intel.projecte.api.capabilities.item.IItemEmcHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ContainerSaleTestFixture {
    static final ResourceLocation STORAGE = id("container_sale_storage");
    static final ResourceLocation FAILING = id("container_sale_failing");
    static final ResourceLocation RESTORE_LINKAGE = id("container_sale_restore_linkage");
    static final ResourceLocation EXTERNAL_EMC = id("container_sale_external_emc");
    static Runnable onExtract;
    static long externalEmc;
    static int externalEmcInsertions;

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }

    @SubscribeEvent public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) for (ResourceLocation id : List.of(STORAGE, FAILING, RESTORE_LINKAGE, EXTERNAL_EMC))
            event.register(Registries.ITEM, id, () -> new Item(new Item.Properties()));
    }

    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        for (ResourceLocation id : List.of(STORAGE, FAILING, RESTORE_LINKAGE)) event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, context) -> new ComponentItemHandler(stack, DataComponents.CONTAINER, 2) {
                    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        ItemStack result = super.extractItem(slot, amount, simulate);
                        if (!simulate && !result.isEmpty()) {
                            if (onExtract != null) onExtract.run();
                            if ((id.equals(FAILING) || id.equals(RESTORE_LINKAGE)) && slot == 1)
                                throw new IllegalStateException("Intentional container extraction failure");
                        }
                        return result;
                    }
                    @Override public void setStackInSlot(int slot, ItemStack item) {
                        super.setStackInSlot(slot, item);
                        if (id.equals(RESTORE_LINKAGE) && slot == 0 && !item.isEmpty())
                            throw new NoClassDefFoundError("Intentional failure after restoring the first storage slot");
                    }
                }, BuiltInRegistries.ITEM.get(id));
        event.registerItem(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY, (stack, context) -> new IItemEmcHolder() {
            @Override public long insertEmc(ItemStack item, long amount, EmcAction action) {
                if (action.execute()) { externalEmcInsertions++; externalEmc += amount; }
                return amount;
            }
            @Override public long extractEmc(ItemStack item, long amount, EmcAction action) {
                long extracted = Math.min(amount, externalEmc);
                if (action.execute()) externalEmc -= extracted;
                return extracted;
            }
            @Override public long getStoredEmc(ItemStack item) { return externalEmc; }
            @Override public long getMaximumEmc(ItemStack item) { return Long.MAX_VALUE; }
        }, BuiltInRegistries.ITEM.get(EXTERNAL_EMC));
    }

    static ItemStack stack(ResourceLocation id) { return new ItemStack(BuiltInRegistries.ITEM.get(id)); }
}
