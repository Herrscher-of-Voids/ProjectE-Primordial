/**
 * 【文件职责】读取护符样本可选择的NBT字段与转义路径。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：最多128字段、8级复合遍历及256字符路径。
 * - 修改：斜杠与波浪号转义避免字段名歧义；标签值按原始类型精确比较。
 * - 用途：允许选择独立字段而不要求整个物品NBT完全相同，不进行模糊文本匹配。
 */
package com.primordial.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.LinkedHashMap;
import java.util.Map;

/** 用转义路径选择 NBT 字段；值按原始 Tag 精确相等，数字类型不同也不视为相同。 */
public final class CharmNbtFields {
    public static final int MAX_FIELDS = 128;
    private CharmNbtFields() { }
    public static Map<String, Tag> fields(CompoundTag root) {
        var result = new LinkedHashMap<String, Tag>();
        if (root != null) collect(root, "", 0, result);
        return result;
    }
    private static void collect(CompoundTag root, String prefix, int depth, Map<String, Tag> result) {
        for (String key : root.getAllKeys().stream().sorted().toList()) {
            if (result.size() >= MAX_FIELDS) return;
            String path = prefix + "/" + key.replace("~", "~0").replace("/", "~1");
            if (path.length() > 256) continue;
            Tag value = root.get(key);
            result.put(path, value);
            if (value instanceof CompoundTag nested && depth < 7) collect(nested, path, depth + 1, result);
        }
    }
    public static Tag value(CompoundTag root, String path) {
        if (root == null || path == null || !path.startsWith("/") || path.length() > 256) return null;
        Tag value = root;
        for (String part : path.substring(1).split("/", -1)) {
            if (!(value instanceof CompoundTag compound)) return null;
            value = compound.get(part.replace("~1", "/").replace("~0", "~"));
        }
        return value;
    }
}
