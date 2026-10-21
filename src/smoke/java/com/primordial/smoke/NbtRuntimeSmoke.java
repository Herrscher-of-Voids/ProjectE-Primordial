/**
 * 【文件职责】在真实 Forge 类加载器验证 ProjectE NBT Mixin 和知识往返。
 * 【变更记录 #1】2026-10-02 19:32:17（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：真实 NBTManager、KnowledgeImpl、学习偏好及三个转化桌类的转换验证。
 * - 修改：仅测试探针运行，不随正式 JAR 发布。
 * - 用途：避免普通 JUnit 不应用 Mixin 导致错误报告集成成功。
 */
package com.primordial.smoke;

import com.primordial.data.TransmutationNbtSettings;
import com.primordial.service.TransmutationNbtPolicy;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.emc.nbt.NBTManager;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static com.primordial.smoke.RuntimeSmoke.check;

final class NbtRuntimeSmoke {
    static void run(MinecraftServer server) throws Exception {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(37);
        sword.setHoverName(net.minecraft.network.chat.Component.literal("NBT smoke sword"));
        CompoundTag enchant = new CompoundTag();
        enchant.putString("id", "minecraft:sharpness");
        enchant.putShort("lvl", (short) 3);
        ListTag enchants = new ListTag();
        enchants.add(enchant);
        sword.getOrCreateTag().put("Enchantments", enchants);
        ItemInfo info = ItemInfo.fromStack(sword);
        check(NBTManager.getPersistentInfo(info).equals(info), "NBTManager preserves safe full variant");
        var provider = (KnowledgeImpl.DefaultImpl)KnowledgeImpl.getDefault();
        check(provider.addKnowledge(info), "learn exact variant");
        check(provider.hasKnowledge(info), "variant remains known");
        check(!provider.hasKnowledge(new ItemStack(Items.DIAMOND_SWORD)), "variant does not grant unrelated undamaged sword");
        var loaded = (KnowledgeImpl.DefaultImpl)KnowledgeImpl.getDefault();
        loaded.deserializeNBT(provider.serializeNBT());
        check(loaded.hasKnowledge(info), "variant survives ProjectE serialization");
        check(loaded.removeKnowledge(info) && !loaded.hasKnowledge(info), "exact variant can be forgotten");
        var player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(server.overworld());
        TransmutationNbtSettings.get(server).set(player.getUUID(), 1);
        var learned = TransmutationNbtPolicy.learned(player, info);
        check(learned.getNBT().contains("display") && !learned.getNBT().contains("Damage")
                && !learned.getNBT().contains("Enchantments"), "per-player settings filter before learning");
        for (String name : new String[]{
                "moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory",
                "moze_intel.projecte.gameObjs.container.TransmutationContainer",
                "moze_intel.projecte.gameObjs.container.slots.transmutation.SlotConsume"}) {
            Class<?> type = Class.forName(name);
            check(java.util.Arrays.stream(type.getDeclaredMethods()).anyMatch(method -> method.getName().contains("primordial")),
                    "Mixin applied to " + name);
        }
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.getOrCreateTagElement("BlockEntityTag").put("Items", enchants);
        check(!NBTManager.getPersistentInfo(ItemInfo.fromStack(chest)).hasNBT(), "container storage excluded");
    }
}
