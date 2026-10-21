/**
 * 【文件职责】验证黑洞护符物品、标签和精确 NBT 筛选规则的优先级与默认行为。
 *
 * 【变更记录 #1】2026-09-15 15:12:00（第 1 次备注 · 1.0.0-stage8-blackhole-charm）
 * - 删除：无；保留 JUnit 与 Minecraft 基础注册初始化。
 * - 新增：无规则放行、白名单收紧、黑名单优先和完整 NBT 匹配回归。
 * - 修改：测试只构造本地 ItemStack/NBT，不启动世界、不写玩家存档或调用网络。
 * - 用途：锁定护符筛选契约，防止后续 GUI 编辑或性能优化改变玩家明确设置的规则。
 */
package com.primordial.machine;

import com.primordial.item.BlackholeCharmFilter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlackholeCharmFilterTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void noRulesAllowAnyNonEmptyItem() {
        assertTrue(BlackholeCharmFilter.accepts(new ItemStack(Items.STICK), new ItemStack(Items.DIAMOND)));
        assertFalse(BlackholeCharmFilter.accepts(ItemStack.EMPTY, ItemStack.EMPTY));
    }

    @Test void whitelistRestrictsAndBlacklistWins() {
        ItemStack charm = new ItemStack(Items.AMETHYST_SHARD);
        ListTag white = new ListTag();
        white.add(StringTag.valueOf("minecraft:diamond"));
        charm.getOrCreateTag().put("WhitelistItems", white);
        assertTrue(BlackholeCharmFilter.accepts(charm, new ItemStack(Items.DIAMOND)));
        assertFalse(BlackholeCharmFilter.accepts(charm, new ItemStack(Items.IRON_INGOT)));

        ListTag black = new ListTag();
        black.add(StringTag.valueOf("minecraft:diamond"));
        charm.getOrCreateTag().put("BlacklistItems", black);
        assertFalse(BlackholeCharmFilter.accepts(charm, new ItemStack(Items.DIAMOND)));
    }

    @Test void nbtWhitelistRequiresExactTagAndBlacklistOverridesIt() {
        ItemStack candidate = new ItemStack(Items.DIAMOND);
        candidate.getOrCreateTag().putInt("CustomModelData", 7);
        ItemStack charm = new ItemStack(Items.AMETHYST_SHARD);
        CompoundTag rule = new CompoundTag();
        rule.putString("id", "minecraft:diamond");
        rule.put("tag", candidate.getTag().copy());
        ListTag white = new ListTag();
        white.add(rule);
        charm.getOrCreateTag().put("WhitelistNbt", white);
        assertTrue(BlackholeCharmFilter.accepts(charm, candidate));

        ItemStack other = candidate.copy();
        other.getOrCreateTag().putInt("CustomModelData", 8);
        assertFalse(BlackholeCharmFilter.accepts(charm, other));

        ListTag black = new ListTag();
        black.add(rule.copy());
        charm.getOrCreateTag().put("BlacklistNbt", black);
        assertFalse(BlackholeCharmFilter.accepts(charm, candidate));
    }

    @Test void oversizedFilterNbtIsBounded() {
        ItemStack charm = new ItemStack(Items.AMETHYST_SHARD);
        ListTag white = new ListTag();
        for (int i = 0; i < 10_000; i++) white.add(StringTag.valueOf("test:item_" + i));
        charm.getOrCreateTag().put("WhitelistItems", white);
        assertFalse(BlackholeCharmFilter.accepts(charm, new ItemStack(Items.DIAMOND)));
    }
}
