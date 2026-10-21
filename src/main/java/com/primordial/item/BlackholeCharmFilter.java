/**
 * 【文件职责】读取黑洞饰品 ItemStack 中的物品、物品标签和精确 NBT 筛选规则。
 *
 * 【变更记录 #1】2026-09-15 15:12:00（第 1 次备注 · 1.0.0-stage8-blackhole-charm）
 * - 删除：黑洞饰品只能按 EMC 价格接收、无法区分物品的路径。
 * - 新增：物品 ID、物品标签和完整 NBT 的白名单/黑名单匹配；任一黑名单优先拒绝，白名单存在时必须命中。
 * - 修改：所有规则从饰品自身 NBT 读取，格式错误按未命中处理，不访问文件或全局可变状态。
 * - 用途：为后续 GUI 编辑提供稳定的运行时契约；无任何规则时默认允许全部有价物品，避免改变基础黑洞吸收行为。
 */
/**
 * 【变更记录 #2】2026-09-15 15:35:00（第 2 次备注 · 1.0.0-stage10-performance）
 * - 删除：筛选列表按 NBT 实际长度无上限遍历的路径。
 * - 新增：每类筛选最多检查 256 项，超出部分延后到 GUI 清理或下次编辑。
 * - 修改：匹配仍保持黑名单优先和精确 NBT 语义，异常列表不会放大每 tick CPU。
 * - 用途：防止恶意或误导入的超大饰品 NBT 在每次掉落物扫描中造成 TPS 抖动。
 */
/**
 * 【文件职责】判断掉落物是否通过护符的六类名单。
 * 【变更记录 #3】2026-09-16 19:41:50（第 3 次备注 · 1.0.0-review-ready）
 * - 删除：无；完整NBT旧格式继续读取。
 * - 新增：fields形式的选择字段匹配，路径缺失或类型不同均拒绝命中。
 * - 修改：黑名单仍优先，各类别白名单只在存在时生效。
 * - 用途：编辑后的规则可直接影响回收，保留无规则默认允许和有界遍历。
 */
package com.primordial.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

/** 黑名单拒绝优先于白名单；每个类别的白名单只有在该类别存在规则时才收紧匹配。 */
public final class BlackholeCharmFilter {
    private static final int MAX_FILTER_ENTRIES = 256;
    private static final String WHITELIST_ITEMS = "WhitelistItems";
    private static final String BLACKLIST_ITEMS = "BlacklistItems";
    private static final String WHITELIST_TAGS = "WhitelistTags";
    private static final String BLACKLIST_TAGS = "BlacklistTags";
    private static final String WHITELIST_NBT = "WhitelistNbt";
    private static final String BLACKLIST_NBT = "BlacklistNbt";

    private BlackholeCharmFilter() {}

    /** 判断一个掉落物是否通过饰品当前的所有筛选规则。 */
    public static boolean accepts(ItemStack charm, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        CompoundTag root = charm == null ? null : charm.getTag();
        if (root == null) return true;
        String itemId = BuiltInRegistries.ITEM.getKey(candidate.getItem()).toString();

        if (containsString(root, BLACKLIST_ITEMS, itemId)) return false;
        if (hasStrings(root, WHITELIST_ITEMS) && !containsString(root, WHITELIST_ITEMS, itemId)) return false;
        if (matchesItemTag(root, BLACKLIST_TAGS, candidate)) return false;
        if (hasStrings(root, WHITELIST_TAGS) && !matchesItemTag(root, WHITELIST_TAGS, candidate)) return false;
        if (matchesNbt(root, BLACKLIST_NBT, candidate)) return false;
        return !hasNbtRules(root, WHITELIST_NBT) || matchesNbt(root, WHITELIST_NBT, candidate);
    }

    private static boolean matchesItemTag(CompoundTag root, String key, ItemStack stack) {
        if (!root.contains(key, Tag.TAG_LIST)) return false;
        ListTag list = root.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < Math.min(list.size(), MAX_FILTER_ENTRIES); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null && stack.is(TagKey.create(Registries.ITEM, id))) return true;
        }
        return false;
    }

    /** NBT 规则项格式为 {"id":"namespace:item","tag":{...}}；tag 必须完整相等。 */
    private static boolean matchesNbt(CompoundTag root, String key, ItemStack stack) {
        if (!root.contains(key, Tag.TAG_LIST)) return false;
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CompoundTag actual = stack.getTag();
        for (int i = 0; i < Math.min(list.size(), MAX_FILTER_ENTRIES); i++) {
            CompoundTag rule = list.getCompound(i);
            String id = rule.getString("id");
            if (!id.isEmpty() && !itemId.equals(id)) continue;
            if (rule.contains("fields", Tag.TAG_COMPOUND)) {
                CompoundTag fields = rule.getCompound("fields");
                if (!fields.isEmpty() && fields.size() <= CharmNbtFields.MAX_FIELDS
                        && fields.getAllKeys().stream().allMatch(path -> fields.get(path).equals(CharmNbtFields.value(actual, path))))
                    return true;
                continue;
            }
            CompoundTag expected = rule.contains("tag", Tag.TAG_COMPOUND) ? rule.getCompound("tag")
                    : rule.contains("nbt", Tag.TAG_COMPOUND) ? rule.getCompound("nbt") : null;
            if (expected != null && expected.equals(actual == null ? new CompoundTag() : actual)) return true;
        }
        return false;
    }

    private static boolean hasNbtRules(CompoundTag root, String key) {
        return root.contains(key, Tag.TAG_LIST) && !root.getList(key, Tag.TAG_COMPOUND).isEmpty();
    }

    private static boolean hasStrings(CompoundTag root, String key) {
        return root.contains(key, Tag.TAG_LIST) && !root.getList(key, Tag.TAG_STRING).isEmpty();
    }

    private static boolean containsString(CompoundTag root, String key, String value) {
        if (!root.contains(key, Tag.TAG_LIST)) return false;
        ListTag list = root.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < Math.min(list.size(), MAX_FILTER_ENTRIES); i++)
            if (value.equals(list.getString(i))) return true;
        return false;
    }
}
