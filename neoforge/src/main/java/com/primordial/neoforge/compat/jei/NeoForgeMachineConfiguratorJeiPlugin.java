package com.primordial.neoforge.compat.jei;

import java.util.ArrayList;
import java.util.List;

import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.machine.NeoForgeMachineConfiguratorScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@JeiPlugin
public final class NeoForgeMachineConfiguratorJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "machine_configurator");
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(NeoForgeMachineConfiguratorScreen.class, new IGhostIngredientHandler<>() {
            @Override public <I> List<Target<I>> getTargetsTyped(NeoForgeMachineConfiguratorScreen screen,
                                                                 ITypedIngredient<I> ingredient, boolean doStart) {
                Object value = ingredient.getIngredient();
                List<Target<I>> targets = new ArrayList<>();
                if (value instanceof ItemStack item && !item.isEmpty()) {
                    for (Rect2i area : screen.itemSampleAreas()) targets.add(target(area,
                            accepted -> screen.acceptJeiItem(item.copyWithCount(1), area)));
                } else if (value instanceof FluidStack fluid && !fluid.isEmpty()) {
                    for (Rect2i area : screen.fluidSampleAreas()) targets.add(target(area,
                            accepted -> screen.acceptJeiFluid(fluid.copy(), area)));
                } else {
                    ResourceLocation chemical = NeoForgePriceEditorChemicalBridge.chemicalId(value);
                    if (chemical != null) for (Rect2i area : screen.fluidSampleAreas()) targets.add(target(area,
                            accepted -> screen.acceptJeiChemical(chemical, area)));
                }
                return targets;
            }

            private <I> Target<I> target(Rect2i area, java.util.function.Consumer<I> accept) {
                return new Target<>() {
                    @Override public Rect2i getArea() { return area; }
                    @Override public void accept(I ingredient) { accept.accept(ingredient); }
                };
            }

            @Override public void onComplete() { }
            @Override public boolean shouldHighlightTargets() { return true; }
        });
    }
}
