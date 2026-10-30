/**
 * 【文件职责】为 NeoForge 目标版公开 ProjectE 原生转化桌扩展的槽位边界与配方输入工具。
 *
 * 【变更记录 #1】2026-10-23 19:39:28（第 1 次备注 · 2.0.0-neoforge-transmutation-table）
 * - 删除：无。
 * - 新增：原生转化桌槽位边界、3x3 合成结果索引和 CraftingInput 构造入口。
 * - 修改：不重复注册 ProjectE 的 MenuType 或 Screen；后续扩展通过本 API 和 Mixin 复用原生菜单。
 * - 用途：把 NeoForge 1.21.1 的原生 0–62 槽与本模组追加槽位契约固定下来，避免配方、JEI 和容器交互各自猜测索引。
 */
package com.primordial.neoforge.transmutation;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/** Shared slot contract for the ProjectE 1.1.0 native transmutation menu. */
public final class NeoForgeTransmutationTable {
    /** ProjectE's native transmutation menu currently exposes slots 0 through 62. */
    public static final int NATIVE_SLOT_COUNT = 63;
    public static final int CRAFTING_FIRST_SLOT = NATIVE_SLOT_COUNT;
    public static final int CRAFTING_SLOT_COUNT = 9;
    public static final int RESULT_SLOT = CRAFTING_FIRST_SLOT + CRAFTING_SLOT_COUNT;
    public static final int MEDIA_FIRST_SLOT = RESULT_SLOT + 1;
    public static final int MEDIA_SLOT_COUNT = 9;
    public static final int FORGET_SLOT = MEDIA_FIRST_SLOT + MEDIA_SLOT_COUNT - 1;
    public static final int TOTAL_SLOT_COUNT = FORGET_SLOT + 1;

    private NeoForgeTransmutationTable() {}

    public static boolean isCraftingSlot(int slot) {
        return slot >= CRAFTING_FIRST_SLOT && slot < RESULT_SLOT;
    }

    public static boolean isResultSlot(int slot) {
        return slot == RESULT_SLOT;
    }

    public static boolean isMediaSlot(int slot) {
        return slot >= MEDIA_FIRST_SLOT && slot < TOTAL_SLOT_COUNT;
    }

    /** Uses the target-version CraftingContainer default conversion, preserving components. */
    public static CraftingInput input(CraftingContainer container) {
        return container.asCraftInput();
    }

    /** Defensive copy used by tests and future recipe/JEI integrations. */
    public static List<ItemStack> copyInputs(CraftingContainer container) {
        List<ItemStack> copy = new ArrayList<>(container.getItems().size());
        for (ItemStack stack : container.getItems()) copy.add(stack.copy());
        return List.copyOf(copy);
    }
}
