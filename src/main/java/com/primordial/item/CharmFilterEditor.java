/**
 * 【文件职责】校验并持久化护符物品、标签和NBT规则。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：添加、去重、删除与六个名单列表快照；每类256规则，总计32KiB限制。
 * - 修改：NBT未选字段时保存完整标签；已选字段保存路径及精确值；标签只能来自样本。
 * - 用途：GUI无法提交任意物品NBT覆盖工具，持久规则直接供回收匹配使用。
 */
package com.primordial.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** 仅接受服务端库存样本，集中限制规则数量与 NBT 体积；菜单和匹配器共享持久化格式。 */
public final class CharmFilterEditor {
    public static final int MAX_BYTES = 32768;
    public static final int MAX_RULES = 256;
    private CharmFilterEditor() { }
    public static String key(int type, boolean blacklist) {
        return (blacklist ? "Blacklist" : "Whitelist") + switch (type) {
            case 1 -> "Nbt"; case 2 -> "Tags"; default -> "Items";
        };
    }
    public static CompoundTag snapshot(ItemStack charm) {
        var result = new CompoundTag();
        CompoundTag root = charm.getTag();
        if (root != null) for (int type = 0; type < 3; type++) for (boolean black : new boolean[]{false, true}) {
            String key = key(type, black);
            if (root.contains(key, Tag.TAG_LIST)) result.put(key, root.getList(key, type == 1 ? Tag.TAG_COMPOUND : Tag.TAG_STRING).copy());
        }
        return result;
    }
    public static ListTag rules(CompoundTag root, int type, boolean blacklist) {
        return root.getList(key(type, blacklist), type == 1 ? Tag.TAG_COMPOUND : Tag.TAG_STRING);
    }
    /** NBT 未选字段时保存完整精确匹配；已选字段只比较这些路径，值仍须精确相等。 */
    public static boolean add(ItemStack charm, ItemStack sample, int type, boolean blacklist, List<String> selected) {
        if (sample.isEmpty() || type < 0 || type > 2) return false;
        CompoundTag root = snapshot(charm);
        ListTag list = rules(root, type, blacklist);
        if (list.size() >= MAX_RULES) return false;
        String id = BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
        if (type == 0) append(list, StringTag.valueOf(id));
        else if (type == 1) {
            var rule = new CompoundTag(); rule.putString("id", id);
            if (selected.isEmpty()) rule.put("tag", sample.hasTag() ? sample.getTag().copy() : new CompoundTag());
            else {
                var fields = new CompoundTag();
                var allowed = CharmNbtFields.fields(sample.getTag());
                for (String path : selected) if (allowed.containsKey(path)) fields.put(path, allowed.get(path).copy());
                if (fields.isEmpty()) return false;
                rule.put("fields", fields);
            }
            append(list, rule);
        } else {
            var tags = sample.getTags().map(tag -> tag.location().toString()).toList();
            for (String tag : selected) if (tags.contains(tag) && list.size() < MAX_RULES) append(list, StringTag.valueOf(tag));
            if (selected.isEmpty()) return false;
        }
        root.put(key(type, blacklist), list);
        if (root.sizeInBytes() > MAX_BYTES) return false;
        for (String key : root.getAllKeys()) charm.getOrCreateTag().put(key, root.get(key));
        return true;
    }
    private static void append(ListTag rules, Tag rule) { if (!rules.contains(rule)) rules.add(rule); }
    public static boolean remove(ItemStack charm, int type, boolean blacklist, int index) {
        if (type < 0 || type > 2) return false;
        ListTag list = rules(charm.getOrCreateTag(), type, blacklist);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        return true;
    }
}
