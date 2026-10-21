/**
 * 【文件职责】为 ProjectE 转化桌增加服务端权威的 3x3 合成网格。
 * 【变更记录 #1】2026-10-03 03:34:00（第 1 次备注 · 1.2.0-transmutation-crafting）
 * - 删除：无。
 * - 新增：九个合成输入槽、原版结果槽、配方重算及关闭返还。
 * - 修改：材料和结果槽 Shift 点击只转移到玩家背包，不经过 ProjectE 的卖入分支。
 * - 用途：复用原版配方、剩余物和成就处理，避免材料被误售或退出时丢失。
 */
/**
 * 【变更记录 #2】2026-10-03 03:41:05（第 2 次备注 · 1.2.0-transmutation-crafting-runtime）
 * - 删除：对继承的 addSlot 方法的多余 Shadow 声明。
 * - 新增：关闭菜单注入同时匹配开发环境 removed 和 Forge 发布版 m_6877_。
 * - 修改：合成槽直接调用父类菜单的 addSlot，避免 Shadow 名称在发布版失配。
 * - 用途：确保发布版 ProjectE 菜单成功应用合成 Mixin，生成 63-72 号真实槽位并在关闭时返还材料。
 *
 * 【变更记录 #3】2026-10-11 23:25:00（第 3 次备注 · 1.2.0-crafting-jei-system）
 * - 删除：结果槽 Shift 取出允许部分移动后消耗整份原料的路径。
 * - 新增：按背包 27-62 号实际槽权限和 NBT 只读确认整份产物能放下。
 * - 修改：空间不足直接返回空，不执行移动、结果槽回调或原料消耗。
 * - 用途：合成结果完整交付，背包满或只能容纳部分时材料保持原样。
 */
/**
 * 【变更记录 #4】2026-10-12 23:38:04（第 4 次备注 · 1.2.0-media-real-slots）
 * - 删除：旧介质页不完整的交互路径。
 * - 新增：追加 73-80 号 FE 充电槽和 81 号保留容器的遗忘槽；每 tick 按实际接收量扣款，切页停充、关闭返还。
 * - 修改：对照 ProjectE 真实槽位及原生控件处理。
 * - 用途：介质页状态、容器安全与显示一致，保留全部历史。
 */
package com.primordial.mixin;

import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
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
public abstract class TransmutationCraftingMixin extends moze_intel.projecte.gameObjs.container.PEHandContainer implements com.primordial.service.MediaChargingMenu {
    @Shadow @Final public TransmutationInventory transmutationInventory;
    @Unique private TransientCraftingContainer primordial$craft;
    @Unique private ResultContainer primordial$result;
    @Unique private boolean primordial$closing;
    @Unique private net.minecraft.world.SimpleContainer primordial$mediaSlots;
    @Unique private boolean primordial$mediaPage;
    @Unique private boolean primordial$gasPage;
    @Unique private long primordial$chargedTick = Long.MIN_VALUE;

    @Override public boolean primordial$mediaPage() { return primordial$mediaPage; }
    @Override public boolean primordial$gasPage() { return primordial$gasPage; }
    @Override public void primordial$setMediaPage(boolean active, boolean gas) {
        primordial$mediaPage = active;
        primordial$gasPage = gas;
    }

    @Override public void broadcastChanges() {
        if (!primordial$closing && primordial$mediaPage && primordial$mediaSlots != null
                && transmutationInventory.player instanceof ServerPlayer player
                && primordial$chargedTick != player.level().getGameTime()) {
            primordial$chargedTick = player.level().getGameTime();
            for (int i = 0; i < 8; i++)
                com.primordial.service.MediaSlotTransactions.charge(player, slots.get(FIRST_SLOT + i));
        }
        super.broadcastChanges();
    }

    @Inject(method = "clickPostValidate", at = @At("HEAD"), cancellable = true)
    private void primordial$extraSlotValidation(int index, int button,
            net.minecraft.world.inventory.ClickType type, Player player, CallbackInfo ci) {
        // 新增槽不属于 ProjectE 的 CombinedInvWrapper，不能传给其原生输出同步路径。
        if (primordial$mediaPage && index >= 0 && index < 27) {
            ci.cancel();
            return;
        }
        if (index >= 63) {
            if (index < FIRST_SLOT || primordial$mediaPage)
                super.clickPostValidate(index, button, type, player);
            ci.cancel();
        }
    }

    protected TransmutationCraftingMixin() { super(null, 0, null, null, 0); }

    @Inject(method = "initSlots", at = @At("TAIL"))
    private void primordial$addCraftingSlots(CallbackInfo ci) {
        var menu = (TransmutationContainer) (Object) this;
        primordial$craft = new TransientCraftingContainer(menu, 3, 3);
        primordial$result = new ResultContainer();
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(primordial$craft, i, 31 + (i % 3) * 18, 28 + (i / 3) * 18));
        }
        addSlot(new ResultSlot(transmutationInventory.player, primordial$craft, primordial$result, 0, 124, 46));
        primordial$mediaSlots = new net.minecraft.world.SimpleContainer(9);
        var mediaMenu = (com.primordial.service.MediaChargingMenu) (Object) this;
        for (int i = 0; i < 8; i++) {
            Slot nativeSlot = slots.get(i);
            addSlot(new com.primordial.service.MediaContainerSlot(primordial$mediaSlots, i,
                    nativeSlot.x, nativeSlot.y, mediaMenu, transmutationInventory.player, false));
        }
        addSlot(new com.primordial.service.MediaContainerSlot(primordial$mediaSlots, 8,
                89, 97, mediaMenu, transmutationInventory.player, true));
    }

    @Override
    public void slotsChanged(Container changed) {
        super.slotsChanged(changed);
        if (primordial$closing || changed != primordial$craft || transmutationInventory.player.level().isClientSide) return;
        Player player = transmutationInventory.player;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack output = ItemStack.EMPTY;
        var recipe = player.level().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, primordial$craft, player.level());
        if (recipe.isPresent()) {
            CraftingRecipe crafting = recipe.get();
            if (primordial$result.setRecipeUsed(player.level(), serverPlayer, crafting)) {
                ItemStack assembled = crafting.assemble(primordial$craft, player.level().registryAccess());
                if (assembled.isItemEnabled(player.level().enabledFeatures())) output = assembled;
            }
        }
        primordial$result.setItem(0, output);
        broadcastChanges();
    }

    @Inject(method = {"quickMoveStack", "m_7648_"}, at = @At("HEAD"), cancellable = true)
    private void primordial$moveCrafting(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index >= FIRST_SLOT && index <= FORGET_SLOT) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            if (!stack.isEmpty() && moveItemStackTo(stack, 27, 63, true)) {
                slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
                slot.onTake(player, stack);
                cir.setReturnValue(original);
            } else cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (primordial$mediaPage && index >= 27 && index < 63) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        if (index < 63 || index > 72) return;
        if (index == 72) {
            Slot resultSlot = ((TransmutationContainer) (Object) this).getSlot(index);
            ItemStack result = resultSlot.getItem();
            if (result.isEmpty()) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            ItemStack originalResult = result.copy();
            if (!com.primordial.service.CraftingOutputCapacity.fits(originalResult, slots.subList(27, 63))) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            if (!moveItemStackTo(result, 27, 63, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            resultSlot.onQuickCraft(result, originalResult);
            resultSlot.onTake(player, result);
            cir.setReturnValue(originalResult);
            return;
        }
        var menu = (TransmutationContainer) (Object) this;
        Slot slot = menu.getSlot(index);
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, 27, 63, true)) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }
        slot.setByPlayer(stack.isEmpty() ? ItemStack.EMPTY : stack);
        slot.onTake(player, stack);
        cir.setReturnValue(original);
    }

    @Inject(method = {"removed", "m_6877_"}, at = @At("TAIL"))
    private void primordial$returnCrafting(Player player, CallbackInfo ci) {
        if (player.level().isClientSide || primordial$craft == null) return;
        primordial$closing = true;
        for (int i = 0; i < primordial$craft.getContainerSize(); i++) {
            ItemStack stack = primordial$craft.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            if (!player.isAlive() || (player instanceof ServerPlayer server && server.hasDisconnected())) {
                player.drop(stack, false);
            } else {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        primordial$result.clearContent();
        for (int i = 0; i < primordial$mediaSlots.getContainerSize(); i++) {
            ItemStack stack = primordial$mediaSlots.removeItemNoUpdate(i);
            if (stack.isEmpty()) continue;
            if (!player.isAlive() || player instanceof ServerPlayer server && server.hasDisconnected())
                player.drop(stack, false);
            else player.getInventory().placeItemBackInInventory(stack);
        }
    }
}
