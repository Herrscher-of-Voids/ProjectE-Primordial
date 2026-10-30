/**
 * 【文件职责】在 NeoForge 1.21.1 ProjectE 原生转化桌追加服务端权威的 3x3 合成网格与结果槽。
 *
 * 【变更记录 #1】2026-10-23 19:39:28（第 1 次备注 · 2.0.0-neoforge-transmutation-table）
 * - 删除：无。
 * - 新增：原生 TransmutationContainer 的九个 CraftingContainer 输入槽、ResultSlot、配方重算、Shift 取出和关闭返还。
 * - 修改：沿用 ProjectE 的 0–62 原生槽、知识/EMC 逻辑和 GUI；配方查询使用 1.21.1 CraftingInput/RecipeHolder，不复制 ProjectE 菜单。
 * - 用途：先完成转化桌系统的最小可用容器闭环，后续 JEI 配方展示、媒体页和网络购买在同一原生菜单上逐步接入。
 *
 * 【变更记录 #2】2026-10-23（第 2 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 删除：结果槽 Shift 提取只能放下部分产物时仍消耗整份材料的路径。
 * - 新增：按玩家真实槽位、数据组件和堆叠上限只读检查完整产物容量。
 * - 修改：空间不足时保持背包、合成输入和结果原样；空间足够才执行原版转移与消耗回调。
 * - 用途：保留 Forge 版已有的完整交付保证，避免 NeoForge 迁移造成合成产物丢失。
 *
 * 【变更记录 #3】2026-10-23（第 3 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 删除：真实玩家配方刷新跳过 doLimitedCrafting 的路径。
 * - 新增：ServerPlayer 使用原版带世界与配方书校验的 setRecipeUsed 重载。
 * - 修改：无配方或未解锁时清除先前配方身份；非 ServerPlayer 的服务端测试玩家保留配方计算。
 * - 用途：追加网格遵守原版有限合成规则，并继续支持轻量 GameTest 玩家。
 */
package com.primordial.neoforge.mixin;

import java.util.Optional;

import com.primordial.neoforge.transmutation.NeoForgeTransmutationTable;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationContainerReturn;
import com.primordial.neoforge.transmutation.NeoForgeMediaChargingMenu;
import com.primordial.neoforge.transmutation.NeoForgeMediaContainerSlot;
import com.primordial.neoforge.transmutation.NeoForgeMediaSlotTransactions;

import moze_intel.projecte.gameObjs.container.PEHandContainer;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransmutationContainer.class, remap = false)
public abstract class NeoForgeTransmutationCraftingMixin extends PEHandContainer implements NeoForgeMediaChargingMenu {
    @Shadow @Final public TransmutationInventory transmutationInventory;
    @Unique private TransientCraftingContainer primordial$craft;
    @Unique private ResultContainer primordial$result;
    @Unique private boolean primordial$closing;
    @Unique private SimpleContainer primordial$mediaSlots;
    @Unique private boolean primordial$mediaPage, primordial$chemicalPage;
    @Unique private int primordial$lastChargeTick = Integer.MIN_VALUE;

    @Override public boolean primordial$mediaPage() { return primordial$mediaPage; }
    @Override public boolean primordial$chemicalPage() { return primordial$chemicalPage; }
    @Override public void primordial$setMediaPage(boolean active, boolean chemical) {
        primordial$mediaPage = active;
        primordial$chemicalPage = chemical;
    }

    @Override public void broadcastChanges() {
        if (!primordial$closing && primordial$mediaPage && primordial$mediaSlots != null
                && transmutationInventory.player instanceof ServerPlayer player
                && player.tickCount != primordial$lastChargeTick) {
            primordial$lastChargeTick = player.tickCount;
            for (int index = NeoForgeTransmutationTable.MEDIA_FIRST_SLOT; index < NeoForgeTransmutationTable.FORGET_SLOT; index++)
                NeoForgeMediaSlotTransactions.charge(player, slots.get(index));
        }
        super.broadcastChanges();
    }

    protected NeoForgeTransmutationCraftingMixin() {
        super(null, 0, null, null, 0);
    }

    @Inject(method = "initSlots", at = @At("TAIL"))
    private void primordial$addCraftingSlots(CallbackInfo callback) {
        TransmutationContainer menu = (TransmutationContainer) (Object) this;
        primordial$craft = new TransientCraftingContainer(menu, 3, 3);
        primordial$result = new ResultContainer();
        for (int i = 0; i < NeoForgeTransmutationTable.CRAFTING_SLOT_COUNT; i++) {
            addSlot(new Slot(primordial$craft, i, 31 + (i % 3) * 18, 28 + (i / 3) * 18));
        }
        addSlot(new ResultSlot(transmutationInventory.player, primordial$craft, primordial$result, 0, 124, 46));
        primordial$mediaSlots = new SimpleContainer(NeoForgeTransmutationTable.MEDIA_SLOT_COUNT);
        for (int index = 0; index < 8; index++) {
            Slot nativeSlot = slots.get(index);
            addSlot(new NeoForgeMediaContainerSlot(primordial$mediaSlots, index, nativeSlot.x, nativeSlot.y,
                    this, transmutationInventory.player, false));
        }
        addSlot(new NeoForgeMediaContainerSlot(primordial$mediaSlots, 8, 89, 97,
                this, transmutationInventory.player, true));
    }

    /** The target version returns RecipeHolder from RecipeManager; retain its recipe identity for achievements. */
    @Override
    public void slotsChanged(Container changed) {
        super.slotsChanged(changed);
        if (primordial$closing || changed != primordial$craft || primordial$craft == null
                || primordial$result == null || transmutationInventory.player.level().isClientSide) return;
        Player player = transmutationInventory.player;
        primordial$refreshResult(player);
    }

    @Unique
    private void primordial$refreshResult(Player player) {
        ItemStack output = ItemStack.EMPTY;
        var input = NeoForgeTransmutationTable.input(primordial$craft);
        Optional<RecipeHolder<CraftingRecipe>> recipe = player.level().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, player.level());
        primordial$result.setRecipeUsed(null);
        if (recipe.isPresent()) {
            RecipeHolder<CraftingRecipe> holder = recipe.get();
            boolean allowed = player instanceof ServerPlayer serverPlayer
                    ? primordial$result.setRecipeUsed(player.level(), serverPlayer, holder) : true;
            if (allowed) {
                if (!(player instanceof ServerPlayer)) primordial$result.setRecipeUsed(holder);
                ItemStack assembled = holder.value().assemble(input, player.level().registryAccess());
                if (assembled.isItemEnabled(player.level().enabledFeatures())) output = assembled;
            }
        }
        primordial$result.setItem(0, output);
        broadcastChanges();
    }

    /** Let vanilla menu click handling process the added generic slots, then skip ProjectE's native-only branch. */
    @Inject(method = "clickPostValidate", at = @At("HEAD"), cancellable = true)
    private void primordial$validateCraftingSlots(int slot, int button, ClickType type, Player player,
            CallbackInfo callback) {
        if (primordial$mediaPage && slot >= 0 && slot < 27) {
            callback.cancel();
            return;
        }
        if (NeoForgeTransmutationTable.isMediaSlot(slot)) {
            if (primordial$mediaPage) super.clickPostValidate(slot, button, type, player);
            callback.cancel();
            return;
        }
        if (NeoForgeTransmutationTable.isCraftingSlot(slot) || NeoForgeTransmutationTable.isResultSlot(slot)) {
            super.clickPostValidate(slot, button, type, player);
            callback.cancel();
        }
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void primordial$returnLocalContainerContents(Player player, int index,
            CallbackInfoReturnable<ItemStack> callback) {
        TransmutationContainer menu = (TransmutationContainer) (Object) this;
        if (primordial$mediaPage && index >= 27 && index < NeoForgeTransmutationTable.NATIVE_SLOT_COUNT) {
            callback.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (NeoForgeTransmutationTable.isMediaSlot(index)) {
            Slot slot = menu.getSlot(index);
            ItemStack stack = slot.getItem();
            ItemStack before = stack.copy();
            if (primordial$mediaPage && !stack.isEmpty()
                    && moveItemStackTo(stack, 27, NeoForgeTransmutationTable.NATIVE_SLOT_COUNT, true)) {
                slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
                slot.onTake(player, stack);
                callback.setReturnValue(before);
            } else callback.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (index < 27 || index >= NeoForgeTransmutationTable.NATIVE_SLOT_COUNT || index >= menu.slots.size()) return;
        Slot source = menu.getSlot(index);
        ItemStack stack = source.getItem();
        if (!NeoForgeTransmutationContainerReturn.handles(stack)) return;
        if (transmutationInventory.isServer() && !NeoForgeTransmutationContainerReturn.sell(
                transmutationInventory, stack, () -> source.set(ItemStack.EMPTY))) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.primordial.container_return_unsupported"), true);
        }
        callback.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void primordial$quickMoveCrafting(Player player, int index, CallbackInfoReturnable<ItemStack> callback) {
        if (!NeoForgeTransmutationTable.isCraftingSlot(index) && !NeoForgeTransmutationTable.isResultSlot(index)) return;
        Slot slot = ((TransmutationContainer) (Object) this).getSlot(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            callback.setReturnValue(ItemStack.EMPTY);
            return;
        }
        ItemStack original = stack.copy();
        if (NeoForgeTransmutationTable.isResultSlot(index) && !primordial$fitsCraftingOutput(original)) {
            callback.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (!moveItemStackTo(stack, 27, NeoForgeTransmutationTable.NATIVE_SLOT_COUNT, true)) {
            callback.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (NeoForgeTransmutationTable.isResultSlot(index)) {
            slot.onQuickCraft(stack, original);
            slot.onTake(player, stack);
        } else {
            slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
            slot.onTake(player, stack);
        }
        callback.setReturnValue(original);
    }

    /** A partial merge must not consume a recipe; inspect exactly the slots used by the real move. */
    @Unique
    private boolean primordial$fitsCraftingOutput(ItemStack output) {
        int remaining = output.getCount();
        if (output.isStackable()) {
            for (int index = NeoForgeTransmutationTable.NATIVE_SLOT_COUNT - 1; index >= 27; index--) {
                Slot target = slots.get(index);
                ItemStack existing = target.getItem();
                if (!target.mayPlace(output) || existing.isEmpty()
                        || !ItemStack.isSameItemSameComponents(existing, output)) continue;
                int limit = Math.min(output.getMaxStackSize(), target.getMaxStackSize(output));
                remaining -= Math.max(0, limit - existing.getCount());
                if (remaining <= 0) return true;
            }
        }
        // AbstractContainerMenu moves into at most one empty slot after merging existing stacks.
        for (int index = NeoForgeTransmutationTable.NATIVE_SLOT_COUNT - 1; index >= 27; index--) {
            Slot target = slots.get(index);
            if (!target.mayPlace(output) || !target.getItem().isEmpty()) continue;
            int limit = Math.min(output.getMaxStackSize(), target.getMaxStackSize(output));
            return remaining <= limit;
        }
        return false;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        int menuIndex = slot == null ? -1 : slots.indexOf(slot);
        if (primordial$mediaPage && (menuIndex >= 0 && menuIndex < 27
                || NeoForgeTransmutationTable.isCraftingSlot(menuIndex) || NeoForgeTransmutationTable.isResultSlot(menuIndex))) return false;
        if (!primordial$mediaPage && NeoForgeTransmutationTable.isMediaSlot(menuIndex)) return false;
        return super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        int menuIndex = slot == null ? -1 : slots.indexOf(slot);
        if (NeoForgeTransmutationTable.isMediaSlot(menuIndex)) return primordial$mediaPage && slot.isActive();
        if (NeoForgeTransmutationTable.isCraftingSlot(menuIndex)
                || NeoForgeTransmutationTable.isResultSlot(menuIndex)) return false;
        return super.canDragTo(slot);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void primordial$returnCrafting(Player player, CallbackInfo callback) {
        if (player.level().isClientSide || primordial$craft == null || primordial$result == null) return;
        primordial$closing = true;
        for (int i = 0; i < primordial$craft.getContainerSize(); i++) {
            ItemStack stack = primordial$craft.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            if (!player.isAlive() || player instanceof ServerPlayer server && server.hasDisconnected())
                player.drop(stack, false);
            else player.getInventory().placeItemBackInInventory(stack);
        }
        primordial$result.clearContent();
        if (primordial$mediaSlots != null) for (int i = 0; i < primordial$mediaSlots.getContainerSize(); i++) {
            ItemStack stack = primordial$mediaSlots.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            if (!player.isAlive() || player instanceof ServerPlayer server && server.hasDisconnected())
                player.drop(stack, false);
            else player.getInventory().placeItemBackInInventory(stack);
        }
    }
}
