/**
 * 【文件职责】在转化桌卖入本地容器时返还内容并按空容器结算。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：识别原版本地容器，返还内容；未知存储保留原物品，避免共享库存复制。
 * - 修改：沿用 ProjectE 知识、价格和同步，设置仅影响之后的学习。
 * - 用途：保留物品变体，阻止容器内容进入可重复生成的知识；不改变历史知识。
 */
/**
 * 【变更记录 #2】2026-10-02 19:35:00（第 2 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：非 CompoundTag 类型的 Items 列表会被误读为空库存的路径。
 * - 新增：验证非空列表元素类型，不认识的存储格式保留原物品。
 * - 修改：返还以直接子物品为单位，嵌套容器原样返还，不递归销毁。
 * - 用途：防止损坏或未知格式内容静默丢失。
 */
package com.primordial.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.capability.EmcHolderItemCapabilityWrapper;
import moze_intel.projecte.gameObjs.items.KleinStar;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandlerModifiable;

public final class TransmutationContainerReturn {
    private TransmutationContainerReturn() { }
    private static final Set<String> LOCAL = Set.of("chest", "trapped_chest", "barrel", "bundle", "shulker_box");
    private static final ThreadLocal<Boolean> SETTLING = ThreadLocal.withInitial(() -> false);
    public record Plan(ItemStack shell, List<ItemStack> contents) {
        public Plan { contents = List.copyOf(contents); }
    }
    public static boolean localContainer(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("minecraft") && (LOCAL.contains(id.getPath()) || id.getPath().endsWith("_shulker_box"));
    }
    public static boolean handles(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return localContainer(stack) || stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                || stack.getTag() != null && (stack.getTag().contains("BlockEntityTag") || stack.getTag().contains("Items"));
    }
    /** 可写存储在成交时清空实际库存；未知实现保留原物品。 */
    public static Plan prepare(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (!localContainer(stack)) {
            ContainerItemStorage storage = ContainerItemStorage.read(stack.copy());
            return storage == null ? null : new Plan(TransmutationNbtPolicy.safeOutput(stack), storage.contents());
        }
        CompoundTag source = stack.getTag();
        CompoundTag contentsTag = source == null ? new CompoundTag()
                : stack.is(net.minecraft.world.item.Items.BUNDLE) ? source : source.getCompound("BlockEntityTag");
        if (contentsTag.contains("LootTable") || contentsTag.contains("Items") && !contentsTag.contains("Items", Tag.TAG_LIST))
            return null;
        var entries = contentsTag.getList("Items", Tag.TAG_COMPOUND);
        if (contentsTag.get("Items") instanceof net.minecraft.nbt.ListTag raw
                && !raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) return null;
        if (entries.size() > 256 || !entries.isEmpty() && stack.getCount() != 1) return null;
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ItemStack item = ItemStack.of(entries.getCompound(i));
            if (item.isEmpty() || item.getCount() <= 0) return null;
            contents.add(item);
        }
        // 重建物品，不把 BlockEntityTag 或 ForgeCaps 带入知识与输出。
        ItemStack shell = TransmutationNbtPolicy.safeOutput(stack);
        return new Plan(shell, List.copyOf(contents));
    }
    public static boolean sell(TransmutationInventory inventory, ItemStack original, Runnable consume) {
        if (!(inventory.player instanceof ServerPlayer player) || SETTLING.get()) return false;
        SETTLING.set(true);
        try {
            int count = original.getCount();
            var provider = inventory.provider;
            if (provider == null) return false;
            if (!(provider.getInputAndLocks() instanceof IItemHandlerModifiable inputs)) return false;
            // External EMC holders can store charge outside the stack, where NBT rollback cannot undo it.
            for (int slot = 0; slot < Math.min(8, inputs.getSlots()); slot++) {
                ItemStack input = inputs.getStackInSlot(slot);
                if (input.isEmpty()) continue;
                var holder = input.getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY).orElse(null);
                if (holder != null && (input.getItem().getClass() != KleinStar.class
                        || holder.getClass() != EmcHolderItemCapabilityWrapper.class)) return false;
            }
            List<ItemStack> beforeInputs = new ArrayList<>(inputs.getSlots());
            for (int slot = 0; slot < inputs.getSlots(); slot++) beforeInputs.add(inputs.getStackInSlot(slot).copy());
            BigInteger beforeEmc = provider.getEmc();
            Set<ItemInfo> beforeKnowledge = Set.copyOf(provider.getKnowledge());
            boolean beforeFull = provider.hasFullKnowledge();
            Plan plan;
            long price;
            ContainerItemStorage storage = null;
            ReturnInventory returned;
            try {
                plan = prepare(original);
                if (plan == null || (price = EMCHelper.getEmcSellValue(plan.shell())) <= 0) return false;
                returned = ReturnInventory.prepare(player, original, plan.contents());
                if (returned == null) return false;
                if (!localContainer(original)) {
                    storage = ContainerItemStorage.read(original);
                    if (storage == null || !storage.sameContents(plan.contents()) || !storage.drain()) {
                        original.setCount(count);
                        return false;
                    }
                    if (original.getCount() != count) {
                        storage.restore();
                        original.setCount(count);
                        return false;
                    }
                }
            } catch (RuntimeException | LinkageError invalid) {
                original.setCount(count);
                return false;
            }
            try {
                consume.run();
                if (!returned.consumed(player)) throw new IllegalStateException("Container source changed during settlement");
                inventory.handleKnowledge(plan.shell());
                inventory.addEmc(BigInteger.valueOf(price).multiply(BigInteger.valueOf(count)));
                returned.publish(player, returned.output);
                return true;
            } catch (RuntimeException | LinkageError failed) {
                try {
                    try {
                        if (storage != null) storage.restore();
                    } finally {
                        original.setCount(count);
                        try {
                            returned.publish(player, returned.before);
                        } finally {
                            try {
                                // ProjectE charges EMC holders in these slots before updating the account balance.
                                for (int slot = 0; slot < beforeInputs.size(); slot++) {
                                    try {
                                        inputs.setStackInSlot(slot, beforeInputs.get(slot).copy());
                                    } catch (RuntimeException | LinkageError invalid) {
                                        com.mojang.logging.LogUtils.getLogger().error("[Primordial] Could not restore transmutation input slot {}", slot, invalid);
                                    }
                                }
                                for (ItemInfo item : Set.copyOf(provider.getKnowledge())) if (!beforeKnowledge.contains(item)) provider.removeKnowledge(item);
                                for (ItemInfo item : beforeKnowledge) if (!provider.hasKnowledge(item)) provider.addKnowledge(item);
                                provider.setFullKnowledge(beforeFull);
                            } finally {
                                try {
                                    provider.setEmc(beforeEmc);
                                } finally {
                                    provider.sync(player);
                                }
                            }
                        }
                    }
                } catch (RuntimeException | LinkageError rollbackFailure) {
                    com.mojang.logging.LogUtils.getLogger().error("[Primordial] Could not fully roll back rejected container sale", rollbackFailure);
                }
                return false;
            }
        } catch (RuntimeException | LinkageError invalid) {
            return false;
        } finally {
            SETTLING.remove();
        }
    }

    private record ReturnInventory(List<ItemStack> before, List<ItemStack> consumed, List<ItemStack> output) {
        static ReturnInventory prepare(ServerPlayer player, ItemStack source, List<ItemStack> contents) {
            var inventory = player.getInventory();
            List<ItemStack> before = new ArrayList<>(inventory.getContainerSize());
            List<ItemStack> consumed = new ArrayList<>(inventory.getContainerSize());
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack item = inventory.getItem(i);
                before.add(item.copy());
                consumed.add(item == source ? ItemStack.EMPTY : item.copy());
            }
            List<ItemStack> output = consumed.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            for (ItemStack item : contents) {
                int remaining = item.getCount();
                for (int pass = 0; pass < 2 && remaining > 0; pass++) for (int slot = 0; slot < inventory.items.size() && remaining > 0; slot++) {
                    ItemStack stored = output.get(slot);
                    if (pass == 0 ? stored.isEmpty() || !ItemStack.isSameItemSameTags(stored, item) : !stored.isEmpty()) continue;
                    int maximum = Math.min(inventory.getMaxStackSize(), item.getMaxStackSize());
                    int moved = Math.min(remaining, Math.max(0, maximum - stored.getCount()));
                    if (moved > 0) {
                        ItemStack merged = item.copy();
                        merged.setCount(stored.getCount() + moved);
                        output.set(slot, merged);
                        remaining -= moved;
                    }
                }
                if (remaining != 0) return null;
            }
            return new ReturnInventory(before, consumed, output);
        }

        boolean consumed(ServerPlayer player) {
            for (int i = 0; i < consumed.size(); i++) if (!ItemStack.matches(player.getInventory().getItem(i), consumed.get(i))) return false;
            return true;
        }

        void publish(ServerPlayer player, List<ItemStack> items) {
            for (int i = 0; i < items.size(); i++) player.getInventory().setItem(i, items.get(i).copy());
            player.getInventory().setChanged();
        }
    }

}
