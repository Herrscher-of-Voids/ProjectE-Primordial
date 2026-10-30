/** JEI recipe discovery and transfer support for the crafting page of ProjectE's transmutation table. */
package com.primordial.neoforge.compat.jei;

import java.util.List;
import java.util.Optional;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.client.NeoForgeTransmutationPages;
import com.primordial.neoforge.client.NeoForgeJeiEmcCounts;
import com.primordial.neoforge.crafting.NeoForgeKnowledgeCraftPayload;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationTable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.gui.GUITransmutation;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

@JeiPlugin
public final class NeoForgeTransmutationJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "transmutation");
    }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        NeoForgeJeiEmcCounts.setIngredientOverlay(runtime.getIngredientListOverlay());
    }

    @Override public void onRuntimeUnavailable() { NeoForgeJeiEmcCounts.setIngredientOverlay(null); }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GUITransmutation.class, new IGuiContainerHandler<>() {
            @Override public java.util.Collection<IGuiClickableArea> getGuiClickableAreas(
                    GUITransmutation screen, double mouseX, double mouseY) {
                if (!NeoForgeTransmutationPages.craftingPage(screen)) return List.of();
                return List.of(IGuiClickableArea.createBasic(107, 50, 16, 16, RecipeTypes.CRAFTING));
            }
        });
    }

    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new IRecipeTransferHandler<TransmutationContainer, RecipeHolder<CraftingRecipe>>() {
                    @Override public Class<? extends TransmutationContainer> getContainerClass() {
                        return TransmutationContainer.class;
                    }

                    @Override public Optional<MenuType<TransmutationContainer>> getMenuType() {
                        return Optional.of(PEContainerTypes.TRANSMUTATION_CONTAINER.get());
                    }

                    @Override public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
                        return RecipeTypes.CRAFTING;
                    }

                    @Override public IRecipeTransferError transferRecipe(TransmutationContainer menu,
                            RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots, Player player,
                            boolean maximum, boolean doTransfer) {
                        // JEI displays its recipe screen while transferring into the parent's open menu.
                        if (player.containerMenu != menu
                                || menu.slots.size() != NeoForgeTransmutationTable.TOTAL_SLOT_COUNT || recipe.value().isSpecial()
                                || !recipe.value().canCraftInDimensions(3, 3))
                            return registration.getTransferHelper().createInternalError();
                        if (!menu.getCarried().isEmpty()) return registration.getTransferHelper().createUserErrorWithTooltip(
                                Component.translatable("message.primordial.crafting_empty_cursor"));
                        if (doTransfer) {
                            PacketDistributor.sendToServer(new NeoForgeKnowledgeCraftPayload(menu.containerId, recipe.id(), maximum));
                            NeoForgeTransmutationPages.showCraftingPage(menu);
                        }
                        return null;
                    }
                }, RecipeTypes.CRAFTING);
    }
}
