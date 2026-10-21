/**
 * 【文件职责】管理单个时间加速器的有界目标列表，每个目标独立保存倍率、位置和启停状态。
 * 【变更记录 #1】2026-09-16 14:36:38（第 1 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：无；由本类接管旧物品根节点中的单目标数据。
 * - 新增：多目标 NBT、只读提示入口、逐项暂停和旧绑定迁入；硬上限 256 防止异常 NBT 无界遍历。
 * - 修改：服务器首次编辑迁入旧目标；高倍率旧授权失效，需要再次确认。
 * - 用途：服务、确认流程和物品提示共用数据规则；读取客户端提示不会改写 ItemStack。
 */
/**
 * 【变更记录 #2】2026-09-18 01:36:30（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：不同目标种类只按坐标匹配的假设。
 * - 新增：服务端分配稳定绑定 UUID，界面操作按 UUID 定位，避免删除后索引错位。
 * - 修改：只读入口仍不写 NBT；旧绑定首次编辑补齐身份，方块匹配排除范围与时钟条目。
 * - 用途：管理界面、范围缓存和筛选编辑共享同一身份，旧单点数据仍按原有规则处理。
 */
package com.primordial.service;

import com.primordial.item.EmcAcceleratorItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class AccelerationBindings {
    public static final String TARGETS_KEY = "AccelerationTargets";
    public static final int HARD_LIMIT = 256;

    private AccelerationBindings() { }

    /** 只读入口保留旧单目标的显示，返回列表中原有的复合标签，不复制大型 NBT。 */
    public static List<CompoundTag> view(ItemStack stack) {
        if (!stack.hasTag()) return List.of();
        CompoundTag root = stack.getTag();
        if (!root.contains(TARGETS_KEY, Tag.TAG_LIST)) {
            return root.contains(EmcAcceleratorItem.DIMENSION_KEY) ? List.of(root) : List.of();
        }
        ListTag tags = root.getList(TARGETS_KEY, Tag.TAG_COMPOUND);
        var result = new ArrayList<CompoundTag>(Math.min(tags.size(), HARD_LIMIT));
        for (int i = 0; i < tags.size() && i < HARD_LIMIT; i++) result.add(tags.getCompound(i));
        return result;
    }

    static ListTag editable(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(TARGETS_KEY, Tag.TAG_LIST)) {
            var entries = new ListTag();
            if (root.contains(EmcAcceleratorItem.DIMENSION_KEY)) {
                var previous = new CompoundTag();
                for (String key : new String[]{EmcAcceleratorItem.DIMENSION_KEY, EmcAcceleratorItem.X_KEY,
                        EmcAcceleratorItem.Y_KEY, EmcAcceleratorItem.Z_KEY, EmcAcceleratorItem.BLOCK_KEY,
                        EmcAcceleratorItem.MULTIPLIER_KEY, EmcAcceleratorItem.ENABLED_KEY}) {
                    if (root.contains(key)) previous.put(key, root.get(key).copy());
                    if (!key.equals(EmcAcceleratorItem.MULTIPLIER_KEY)) root.remove(key);
                }
                if (rate(previous) >= EmcAccelerationService.warningMultiplier()) pause(previous);
                entries.add(previous);
            }
            root.remove(EmcAcceleratorItem.WARNING_PENDING_KEY);
            root.remove("ConfirmedSession");
            root.remove("ConfirmedOwner");
            root.put(TARGETS_KEY, entries);
        }
        ListTag entries = root.getList(TARGETS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size() && i < HARD_LIMIT; i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("BindingId")) entry.putUUID("BindingId", java.util.UUID.randomUUID());
        }
        return entries;
    }

    public static int rate(CompoundTag entry) {
        int rate = entry.getInt(EmcAcceleratorItem.MULTIPLIER_KEY);
        return EmcAccelerationService.isMultiplier(rate) ? rate : EmcAccelerationService.minMultiplier();
    }

    public static boolean enabled(CompoundTag entry) { return entry.getBoolean(EmcAcceleratorItem.ENABLED_KEY); }
    public static boolean pending(CompoundTag entry) { return entry.getBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY); }

    public static BlockPos pos(CompoundTag entry) {
        return new BlockPos(entry.getInt(EmcAcceleratorItem.X_KEY), entry.getInt(EmcAcceleratorItem.Y_KEY), entry.getInt(EmcAcceleratorItem.Z_KEY));
    }

    static boolean matches(CompoundTag entry, ServerLevel level, BlockPos pos) {
        return AccelerationSettings.kind(entry) == AccelerationSettings.Kind.BLOCK
                && entry.getString(EmcAcceleratorItem.DIMENSION_KEY).equals(level.dimension().location().toString()) && pos(entry).equals(pos);
    }

    public static java.util.UUID id(CompoundTag entry) {
        return entry.hasUUID("BindingId") ? entry.getUUID("BindingId") : new java.util.UUID(0, 0);
    }

    public static CompoundTag find(ItemStack tool, java.util.UUID id) {
        return view(tool).stream().filter(entry -> id(entry).equals(id)).findFirst().orElse(null);
    }

    static void pause(CompoundTag entry) {
        entry.putBoolean(EmcAcceleratorItem.ENABLED_KEY, false);
        entry.putBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY, false);
        entry.remove("ConfirmedSession");
        entry.remove("ConfirmedOwner");
    }

    static void pauseAll(ItemStack stack) { view(stack).forEach(AccelerationBindings::pause); }
}
