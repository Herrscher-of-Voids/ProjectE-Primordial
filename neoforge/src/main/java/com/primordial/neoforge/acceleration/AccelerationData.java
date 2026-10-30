package com.primordial.neoforge.acceleration;

import java.util.IdentityHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Scoped copy-on-write preserves ItemStack component immutability and nested NBT edits. */
public final class AccelerationData {
    private static final ThreadLocal<IdentityHashMap<ItemStack, Scope>> EDITS = ThreadLocal.withInitial(IdentityHashMap::new);
    private AccelerationData() {}
    public static boolean has(ItemStack stack) {
        return EDITS.get().containsKey(stack) || stack.has(DataComponents.CUSTOM_DATA);
    }
    public static CompoundTag get(ItemStack stack) {
        Scope scope = EDITS.get().get(stack);
        return scope == null ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag() : scope.tag;
    }
    public static Scope edit(ItemStack stack) {
        var scopes = EDITS.get();
        Scope scope = scopes.get(stack);
        if (scope != null) { scope.depth++; return scope; }
        scope = new Scope(stack, get(stack));
        scopes.put(stack, scope);
        return scope;
    }
    public static final class Scope implements AutoCloseable {
        private final ItemStack stack;
        private final CompoundTag tag;
        private int depth = 1;
        private Scope(ItemStack stack, CompoundTag tag) { this.stack = stack; this.tag = tag; }
        @Override public void close() {
            if (--depth != 0) return;
            EDITS.get().remove(stack);
            if (!tag.equals(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag())) {
                if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
                else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            if (EDITS.get().isEmpty()) EDITS.remove();
        }
    }
}
