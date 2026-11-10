package com.primordial.neoforge.trade;

import java.util.List;

import com.primordial.neoforge.PrimordialNeoForgeMod;
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
    static Runnable onExtract;

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }

    @SubscribeEvent public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) for (ResourceLocation id : List.of(STORAGE, FAILING))
            event.register(Registries.ITEM, id, () -> new Item(new Item.Properties()));
    }

    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        for (ResourceLocation id : List.of(STORAGE, FAILING)) event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, context) -> new ComponentItemHandler(stack, DataComponents.CONTAINER, 2) {
                    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                        ItemStack result = super.extractItem(slot, amount, simulate);
                        if (!simulate && !result.isEmpty()) {
                            if (onExtract != null) onExtract.run();
                            if (id.equals(FAILING) && slot == 1) throw new IllegalStateException("Intentional container extraction failure");
                        }
                        return result;
                    }
                }, BuiltInRegistries.ITEM.get(id));
    }

    static ItemStack stack(ResourceLocation id) { return new ItemStack(BuiltInRegistries.ITEM.get(id)); }
}
