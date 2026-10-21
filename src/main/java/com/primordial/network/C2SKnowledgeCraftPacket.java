/**
 * 【文件职责】按服务器配方与玩家知识执行 JEI 合成材料兑换。
 * 【变更记录 #1】2026-10-07 00:44:19（第 1 次备注 · 1.2.0-knowledge-crafting）
 * - 删除：无。
 * - 新增：菜单 ID、配方 ID 和批量标记请求，最多规划 64 套材料。
 * - 修改：无既有消息；客户端不提交物品、NBT、价格或玩家身份。
 * - 用途：服务端验证知识、阶段、NBT、余额与完整配方，规划成功后一次扣款并同步槽位。
 *
 * 【变更记录 #2】2026-10-11 23:18:39（第 2 次备注 · 1.2.0-crafting-jei-system）
 * - 删除：网络处理器内部的最大批量规划循环。
 * - 新增：调用专用最大批量规划入口；普通传输仍只规划一套。
 * - 修改：配方、知识、真实输入输出和 EMC 的服务端校验及单次扣款顺序保持不变。
 * - 用途：减少网络触发的重复配方计算，同时保留服务器权威执行。
 */
package com.primordial.network;

import com.primordial.compat.ProjectECompat;
import com.primordial.service.*;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.network.NetworkEvent;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

public record C2SKnowledgeCraftPacket(int containerId, ResourceLocation recipeId, boolean maximum) {
    public static void encode(C2SKnowledgeCraftPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeResourceLocation(msg.recipeId); buf.writeBoolean(msg.maximum);
    }
    public static C2SKnowledgeCraftPacket decode(FriendlyByteBuf buf) {
        return new C2SKnowledgeCraftPacket(buf.readVarInt(), buf.readResourceLocation(), buf.readBoolean());
    }
    public static void handle(C2SKnowledgeCraftPacket msg, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || !(player.containerMenu instanceof TransmutationContainer menu)
                    || menu.containerId != msg.containerId || menu.slots.size() < 73
                    || !menu.stillValid(player) || !menu.getCarried().isEmpty()) return;
            var found = player.level().getRecipeManager().byKey(msg.recipeId);
            if (found.isEmpty() || !(found.get() instanceof CraftingRecipe recipe) || recipe.isSpecial()
                    || !recipe.canCraftInDimensions(3, 3) || recipe.getIngredients().isEmpty()) return;
            List<Ingredient> ingredients = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            for (int i = 0; i < recipe.getIngredients().size(); i++) {
                int index = recipe instanceof ShapedRecipe ? (i / width) * 3 + i % width : i;
                if (index >= 9) return;
                ingredients.set(index, recipe.getIngredients().get(i));
            }
            List<Slot> inventory = new ArrayList<>();
            for (int i = 27; i < 63; i++) if (menu.getSlot(i).mayPickup(player)) inventory.add(menu.getSlot(i));
            List<ItemStack> oldGrid = new ArrayList<>();
            for (int i = 63; i < 72; i++) oldGrid.add(menu.getSlot(i).getItem().copy());
            List<ItemStack> known = new ArrayList<>();
            player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).ifPresent(cap -> {
                for (var info : cap.getKnowledge()) {
                    ItemStack sample = info.createStack();
                    if (ingredients.stream().anyMatch(ingredient -> !ingredient.isEmpty() && ingredient.test(sample)))
                        known.add(sample);
                }
            });
            Map<moze_intel.projecte.api.ItemInfo, BigInteger> prices = new HashMap<>();
            java.util.function.Function<ItemStack, BigInteger> quote = sample -> prices.computeIfAbsent(
                    moze_intel.projecte.api.ItemInfo.fromStack(sample), ignored ->
                    ProjectECompat.hasKnowledge(player.server, player.getUUID(), sample)
                    && EmcService.hasKnowledge(player.server, player.getUUID(), sample)
                            ? EmcService.itemValue(sample, player.server) : BigInteger.ZERO);
            var snapshot = inventory.stream().map(slot -> slot.getItem().copy()).toList();
            BigInteger balance = EmcService.balance(player.server, player.getUUID());
            CraftingTransferPlan.Plan plan = msg.maximum
                    ? CraftingTransferPlan.createMaximum(ingredients, snapshot, oldGrid, known, quote, balance)
                    : CraftingTransferPlan.create(ingredients, snapshot, oldGrid, known, quote, balance, 1);
            if (plan == null) {
                player.displayClientMessage(Component.translatable("message.primordial.crafting_transfer_failed"), true);
                return;
            }
            // 用真实配方再次验证规划结果；自定义配方的动态限制不能只凭 Ingredient 推断。
            var probe = new TransientCraftingContainer(menu, 3, 3);
            for (int i = 0; i < 9; i++) probe.setItem(i, plan.grid().get(i).copy());
            if (!recipe.matches(probe, player.level())) return;
            ItemStack output = recipe.assemble(probe, player.level().registryAccess());
            if (output.isEmpty() || !output.isItemEnabled(player.level().enabledFeatures())) return;
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack next = plan.inventory().get(i);
                if (!next.isEmpty() && (!inventory.get(i).mayPlace(next)
                        || next.getCount() > inventory.get(i).getMaxStackSize(next))) return;
            }
            if (!EmcService.spend(player.server, player.getUUID(), plan.cost())) return;
            for (int i = 0; i < inventory.size(); i++) inventory.get(i).set(plan.inventory().get(i));
            for (int i = 0; i < 9; i++) menu.getSlot(63 + i).set(plan.grid().get(i));
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
