/** Server-authoritative JEI recipe transfer: reuse physical inputs, buy only authorized deficits. */
package com.primordial.neoforge.crafting;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcTransactions;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationTable;
import com.primordial.service.EmcAmount;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public record NeoForgeKnowledgeCraftPayload(int menuId, ResourceLocation recipeId, boolean maximum)
        implements CustomPacketPayload {
    public static final Type<NeoForgeKnowledgeCraftPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "knowledge_craft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NeoForgeKnowledgeCraftPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> { buf.writeVarInt(value.menuId); buf.writeResourceLocation(value.recipeId); buf.writeBoolean(value.maximum); },
            buf -> new NeoForgeKnowledgeCraftPayload(buf.readVarInt(), buf.readResourceLocation(), buf.readBoolean()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(TYPE, STREAM_CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && !transfer(player, request))
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.primordial.crafting_transfer_failed"), true);
        }));
    }

    /** Shared with native-menu GameTests. Clients submit no materials, price, output or identity. */
    public static boolean transfer(ServerPlayer player, NeoForgeKnowledgeCraftPayload request) {
        if (player == null || request == null || request.recipeId == null || !player.server.isSameThread()
                || player.server.getPlayerList().getPlayer(player.getUUID()) != player || !player.isAlive() || player.hasDisconnected()
                || !(player.containerMenu instanceof TransmutationContainer menu) || menu.containerId != request.menuId
                || menu.slots.size() != NeoForgeTransmutationTable.TOTAL_SLOT_COUNT
                || !menu.stillValid(player) || !menu.getCarried().isEmpty()) return false;
        var holder = player.server.getRecipeManager().byKey(request.recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe) || recipe.isSpecial()
                || !recipe.canCraftInDimensions(3, 3) || recipe.getIngredients().isEmpty()
                || player.level().getGameRules().getBoolean(GameRules.RULE_LIMITED_CRAFTING)
                && !player.getRecipeBook().contains(holder)) return false;
        List<Ingredient> ingredients = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        for (int i = 0; i < recipe.getIngredients().size(); i++) {
            int index = recipe instanceof ShapedRecipe ? i / width * 3 + i % width : i;
            if (index >= 9) return false;
            ingredients.set(index, recipe.getIngredients().get(i));
        }
        List<Slot> inventory = new ArrayList<>();
        for (int i = 27; i < NeoForgeTransmutationTable.NATIVE_SLOT_COUNT; i++)
            if (menu.getSlot(i).mayPickup(player)) inventory.add(menu.getSlot(i));
        List<ItemStack> oldGrid = new ArrayList<>();
        for (int i = 0; i < 9; i++) oldGrid.add(menu.getSlot(63 + i).getItem().copy());
        List<ItemStack> known = new ArrayList<>();
        var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        if (knowledge != null) for (ItemInfo info : knowledge.getKnowledge()) {
            ItemStack sample = info.createStack();
            if (ingredients.stream().anyMatch(ingredient -> !ingredient.isEmpty() && ingredient.test(sample))) known.add(sample);
        }
        Map<ItemInfo, EmcAmount> prices = new HashMap<>();
        java.util.function.Function<ItemStack, BigInteger> quote = sample -> prices.computeIfAbsent(
                ItemInfo.fromStack(sample), ignored -> NeoForgeResourceTrades.purchaseItemPrice(
                        player.server, player.getUUID(), sample)).ceil();
        List<ItemStack> before = inventory.stream().map(slot -> slot.getItem().copy()).toList();
        BigInteger budget = NeoForgeEmcTransactions.balance(player.server, player.getUUID()).floor();
        var plan = request.maximum
                ? NeoForgeCraftingTransferPlan.createMaximum(ingredients, before, oldGrid, known, quote, budget)
                : NeoForgeCraftingTransferPlan.create(ingredients, before, oldGrid, known, quote, budget, 1);
        if (plan == null) return false;
        // Dynamic recipes must accept the complete component-preserving grid before any payment.
        var probe = plan.grid().stream().map(ItemStack::copy).toList();
        var input = CraftingInput.of(3, 3, probe);
        if (!recipe.matches(input, player.level())) return false;
        ItemStack output = recipe.assemble(input, player.registryAccess());
        if (output.isEmpty() || !output.isItemEnabled(player.level().enabledFeatures())) return false;
        for (int i = 0; i < 9; i++) if (!ItemStack.matches(probe.get(i), plan.grid().get(i))) return false;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack next = plan.inventory().get(i);
            if (!next.isEmpty() && (!inventory.get(i).mayPlace(next)
                    || next.getCount() > inventory.get(i).getMaxStackSize(next))) return false;
        }
        java.util.function.BooleanSupplier unchanged = () -> {
            if (player.containerMenu != menu || !menu.getCarried().isEmpty() || !menu.stillValid(player)
                    || player.server.getPlayerList().getPlayer(player.getUUID()) != player || !player.isAlive() || player.hasDisconnected()
                    || player.server.getRecipeManager().byKey(request.recipeId).orElse(null) != holder) return false;
            for (int i = 0; i < inventory.size(); i++)
                if (!ItemStack.matches(before.get(i), inventory.get(i).getItem())) return false;
            for (int i = 0; i < 9; i++) if (!ItemStack.matches(oldGrid.get(i), menu.getSlot(63 + i).getItem())) return false;
            for (var entry : prices.entrySet())
                if (!entry.getValue().equals(NeoForgeResourceTrades.purchaseItemPrice(
                        player.server, player.getUUID(), entry.getKey().createStack()))) return false;
            return true;
        };
        if (!unchanged.getAsBoolean()) return false;
        Runnable commit = () -> {
            for (int i = 0; i < inventory.size(); i++) inventory.get(i).set(plan.inventory().get(i));
            for (int i = 0; i < 9; i++) menu.getSlot(63 + i).set(plan.grid().get(i));
            menu.broadcastChanges();
        };
        if (plan.cost().signum() == 0) { commit.run(); return true; }
        return NeoForgeEmcTransactions.purchase(player.server, player.getUUID(), EmcAmount.of(plan.cost()), 1,
                units -> { if (!unchanged.getAsBoolean()) return 0; commit.run(); return 1; }) == 1;
    }
}
