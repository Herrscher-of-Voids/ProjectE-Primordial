/**
 * 【文件职责】黑洞的 64 堆物品缓冲、真实容量模拟与注册表感知存档；只在服务端主线程使用。
 * 【变更记录 #1】2026-10-22 21:38:58（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：按组件合并的有限缓冲、余量返回、完整 NBT 往返和损坏原文保留。
 * - 修改：模拟与执行共用容量计算，既不修改输入对象，也不丢弃超限或无法解析的存档。
 * - 用途：等待 tick 时按真实售价清算；价格变零的物品保留，拆除时退回世界。
 */
package com.primordial.neoforge.blackhole;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

final class NeoForgeBlackholeStorage {
    private static final int CAPACITY = 64;
    private final List<ItemStack> stacks = new ArrayList<>();
    private Tag invalid;

    boolean usable() { return invalid == null; }
    boolean isEmpty() { return stacks.isEmpty() && invalid == null; }
    List<ItemStack> stacks() { return stacks; }

    int insert(ItemStack input, int budget, boolean simulate) {
        if (!usable() || input.isEmpty() || budget <= 0 || input.getMaxStackSize() <= 0) return 0;
        int left = Math.min(input.getCount(), budget);
        int requested = left;
        for (ItemStack existing : stacks) {
            if (!ItemStack.isSameItemSameComponents(existing, input)) continue;
            int moved = Math.min(left, Math.max(0, existing.getMaxStackSize() - existing.getCount()));
            if (!simulate) existing.grow(moved);
            left -= moved;
            if (left == 0) break;
        }
        int freeSlots = CAPACITY - stacks.size();
        while (left > 0 && freeSlots-- > 0) {
            int moved = Math.min(left, input.getMaxStackSize());
            if (!simulate) stacks.add(input.copyWithCount(moved));
            left -= moved;
        }
        return requested - left;
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (invalid != null) {
            tag.put("buffer", invalid.copy());
        } else if (!stacks.isEmpty()) {
            ListTag encoded = new ListTag();
            for (ItemStack stack : stacks) encoded.add(stack.save(registries));
            tag.put("buffer", encoded);
        }
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        stacks.clear();
        invalid = null;
        if (!tag.contains("buffer")) return;
        Tag raw = tag.get("buffer");
        try {
            if (!(raw instanceof ListTag list) || list.size() > CAPACITY
                    || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Invalid blackhole buffer");
            }
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = ItemStack.parse(registries, list.getCompound(i)).orElseThrow();
                if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) {
                    throw new IllegalArgumentException("Invalid buffered stack");
                }
                stacks.add(stack);
            }
        } catch (RuntimeException failure) {
            stacks.clear();
            invalid = raw.copy();
        }
    }
}
