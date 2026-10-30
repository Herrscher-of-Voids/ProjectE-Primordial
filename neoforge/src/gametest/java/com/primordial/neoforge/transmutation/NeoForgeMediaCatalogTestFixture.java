package com.primordial.neoforge.transmutation;

import java.util.List;
import java.util.stream.IntStream;

import com.primordial.neoforge.PrimordialNeoForgeMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.WaterFluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** 仅 GameTest 注册 20 种真实源流体，验证目录的 16 项页面和 12 项重叠步长；不进入主 JAR。 */
@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NeoForgeMediaCatalogTestFixture {
    static final List<ResourceLocation> IDS = IntStream.range(0, 20).mapToObj(index ->
            ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "catalog_test_" + (index < 10 ? "0" : "") + index)).toList();
    static final ResourceLocation OVERSIZED_ID = ResourceLocation.fromNamespaceAndPath(
            PrimordialNeoForgeMod.MOD_ID, "catalog_oversized_" + "a".repeat(128));

    private NeoForgeMediaCatalogTestFixture() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.FLUID)) {
            for (ResourceLocation id : IDS) event.register(Registries.FLUID, id, WaterFluid.Source::new);
            event.register(Registries.FLUID, OVERSIZED_ID, WaterFluid.Source::new);
        }
    }
}
