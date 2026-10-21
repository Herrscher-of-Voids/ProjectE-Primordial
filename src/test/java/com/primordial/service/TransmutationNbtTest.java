/**
 * 【文件职责】验证 NBT 学习设置、安全字段及容器内容返还计划。
 * 【变更记录 #1】2026-10-02 19:32:00（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：三开关组合、知识样本稳定性、大小边界及原版容器内容原样返还测试。
 * - 修改：运行真实 ItemStack、NBT 和返还计划，不启动客户端。
 * - 用途：防止容器内容进入知识或无意丢失原输入。
 */
/**
 * 【变更记录 #2】2026-10-13 04:37:24（第 2 次备注 · 1.2.0-nbt-independent-options）
 * - 删除：测试将 Damage 与附魔正确性绑定到普通 NBT 总开关的旧断言。
 * - 新增：对 8 种 bit 组合分别核对普通安全字段、耐久、附魔，并继续检查输入未被改写。
 * - 修改：测试语义匹配三个独立服务器开关，既有容器返还和恶意 NBT 测试保持有效。
 * - 用途：覆盖界面开关、服务端知识规范化及安全边界之间的组合行为。
 */
package com.primordial.service;

import com.primordial.data.TransmutationNbtSettings;
import com.primordial.machine.AdapterTestBootstrap;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TransmutationNbtTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void independentSettingsPersistAndAreIsolatedByPlayer() {
        var data = new TransmutationNbtSettings();
        var player = UUID.randomUUID();
        assertEquals(7, data.flags(player));
        for (int flags = 0; flags <= 7; flags++) {
            data.set(player, flags);
            assertEquals(flags, TransmutationNbtSettings.load(data.save(new CompoundTag())).flags(player));
        }
        data.set(player, 1);
        assertEquals(7, data.flags(UUID.randomUUID()));
        data.setDirty(false);
        data.set(player, 1);
        assertFalse(data.isDirty());
        assertThrows(IllegalArgumentException.class, () -> data.set(player, 8));
    }

    @Test void masterDamageAndEnchantmentOptionsControlFutureSampleWithoutMutatingInput() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(37);
        sword.setHoverName(net.minecraft.network.chat.Component.literal("Named sword"));
        ListTag enchantments = new ListTag();
        CompoundTag enchantment = new CompoundTag();
        enchantment.putString("id", "minecraft:sharpness");
        enchantment.putShort("lvl", (short) 3);
        enchantments.add(enchantment);
        sword.getOrCreateTag().put("Enchantments", enchantments);
        var original = sword.save(new CompoundTag());
        for (int flags = 0; flags <= 7; flags++) {
            ItemInfo result = TransmutationNbtPolicy.normalize(ItemInfo.fromStack(sword), flags);
            CompoundTag tag = result.getNBT() == null ? new CompoundTag() : result.getNBT();
            assertEquals((flags & 1) != 0, tag.contains("display"));
            assertEquals((flags & 2) != 0, tag.contains("Damage"));
            assertEquals((flags & 4) != 0, tag.contains("Enchantments"));
            assertEquals(original, sword.save(new CompoundTag()));
        }
    }

    @Test void normalizationIsStableAcrossStackCreationAndDoesNotShareTags() {
        for (var item : new net.minecraft.world.item.Item[]{Items.DIAMOND_SWORD, Items.DIAMOND, Items.POTION}) {
            var info = ItemInfo.fromItem(item);
            assertEquals(info, TransmutationNbtPolicy.persistent(ItemInfo.fromStack(info.createStack())));
        }
        var named = new ItemStack(Items.DIAMOND);
        named.setHoverName(net.minecraft.network.chat.Component.literal("Original"));
        var info = TransmutationNbtPolicy.persistent(ItemInfo.fromStack(named));
        named.setHoverName(net.minecraft.network.chat.Component.literal("Changed"));
        assertEquals("Original", info.createStack().getHoverName().getString());
        assertEquals(info, TransmutationNbtPolicy.persistent(info));
    }

    @Test void unsafeUnknownAndOversizedFieldsNeverBecomeKnowledge() {
        var stack = new ItemStack(Items.CHEST);
        stack.getOrCreateTag().put("BlockEntityTag", new CompoundTag());
        stack.getOrCreateTag().putInt("Energy", 100000);
        stack.getOrCreateTag().putString("Owner", "owner");
        stack.getOrCreateTag().putString("unknown_mod_storage", "contents");
        assertFalse(TransmutationNbtPolicy.persistent(ItemInfo.fromStack(stack)).hasNBT());
        stack.getOrCreateTag().putString("display", "x".repeat(40000));
        assertFalse(TransmutationNbtPolicy.persistent(ItemInfo.fromStack(stack)).hasNBT());
        CompoundTag deeplyNested = new CompoundTag(), cursor = deeplyNested;
        for (int i = 0; i < 24; i++) { var next = new CompoundTag(); cursor.put("nested", next); cursor = next; }
        stack.getOrCreateTag().put("display", deeplyNested);
        assertFalse(TransmutationNbtPolicy.persistent(ItemInfo.fromStack(stack)).hasNBT());
    }

    @Test void shulkerContentsAndNestedContainerAreReturnedIntactAndShellContainsNoStorage() {
        var shulker = new ItemStack(Items.RED_SHULKER_BOX);
        shulker.setHoverName(net.minecraft.network.chat.Component.literal("Red storage"));
        var nested = new ItemStack(Items.BLUE_SHULKER_BOX);
        nested.getOrCreateTagElement("BlockEntityTag").putString("CustomName", "nested");
        var diamond = new ItemStack(Items.DIAMOND, 32);
        ListTag contents = new ListTag();
        contents.add(diamond.save(new CompoundTag()));
        contents.add(nested.save(new CompoundTag()));
        shulker.getOrCreateTagElement("BlockEntityTag").put("Items", contents);
        var original = shulker.save(new CompoundTag());
        var result = TransmutationContainerReturn.prepare(shulker);
        assertNotNull(result);
        assertEquals(2, result.contents().size());
        assertEquals(diamond.save(new CompoundTag()), result.contents().get(0).save(new CompoundTag()));
        assertEquals(nested.save(new CompoundTag()), result.contents().get(1).save(new CompoundTag()));
        assertFalse(result.shell().getOrCreateTag().contains("BlockEntityTag"));
        assertEquals("Red storage", result.shell().getHoverName().getString());
        assertEquals(original, shulker.save(new CompoundTag()));
    }

    @Test void chestsBarrelsAndBundlesUseTheirOwnLocalContentsAndDoNotFlattenNestedItems() {
        for (var item : new net.minecraft.world.item.Item[]{Items.CHEST, Items.TRAPPED_CHEST, Items.BARREL, Items.BUNDLE}) {
            var stack = new ItemStack(item);
            ListTag contents = new ListTag();
            contents.add(new ItemStack(Items.EMERALD, 7).save(new CompoundTag()));
            (item == Items.BUNDLE ? stack.getOrCreateTag() : stack.getOrCreateTagElement("BlockEntityTag")).put("Items", contents);
            var result = TransmutationContainerReturn.prepare(stack);
            assertNotNull(result);
            assertEquals(7, result.contents().get(0).getCount());
            assertFalse(result.shell().getOrCreateTag().contains("Items"));
            assertFalse(result.shell().getOrCreateTag().contains("BlockEntityTag"));
        }
    }

    @Test void malformedUnresolvedLootAndStackedFilledContainersAreRejectedWithoutMutation() {
        var stack = new ItemStack(Items.CHEST);
        var data = stack.getOrCreateTagElement("BlockEntityTag");
        data.putString("LootTable", "minecraft:chests/simple_dungeon");
        assertNull(TransmutationContainerReturn.prepare(stack));
        data.remove("LootTable");
        ListTag invalid = new ListTag();
        invalid.add(StringTag.valueOf("bad"));
        data.put("Items", invalid);
        assertNull(TransmutationContainerReturn.prepare(stack));
        ListTag contents = new ListTag();
        contents.add(new ItemStack(Items.DIAMOND).save(new CompoundTag()));
        data.put("Items", contents);
        stack.setCount(2);
        assertNull(TransmutationContainerReturn.prepare(stack));
        assertEquals(2, stack.getCount());
        assertEquals(1, data.getList("Items", 10).size());
        assertNull(TransmutationContainerReturn.prepare(new ItemStack(Items.ENDER_CHEST)));
    }
}
