/**
 * 【文件职责】在转化桌卖入本地容器时返还内容并按空容器结算。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：识别原版本地容器，返还内容；未知存储保留原物品，避免共享库存复制。
 * - 修改：沿用 ProjectE 知识、价格和同步，设置仅影响之后的学习。
 * - 用途：保留物品变体，阻止容器内容进入可重复生成的知识；不改变历史知识。
 */
/**
 * 【变更记录 #2】2026-10-02 19:35:00（第 2 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：非 CompoundTag 类型的 Items 列表会被误读为空库存的路径。
 * - 新增：验证非空列表元素类型，不认识的存储格式保留原物品。
 * - 修改：返还以直接子物品为单位，嵌套容器原样返还，不递归销毁。
 * - 用途：防止损坏或未知格式内容静默丢失。
 */
package com.primordial.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class TransmutationContainerReturn {
    private TransmutationContainerReturn() { }
    private static final Set<String> LOCAL = Set.of("chest", "trapped_chest", "barrel", "bundle", "shulker_box");
    public record Plan(ItemStack shell, List<ItemStack> contents) { }
    public static boolean localContainer(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("minecraft") && (LOCAL.contains(id.getPath()) || id.getPath().endsWith("_shulker_box"));
    }
    public static boolean handles(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return localContainer(stack) || stack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                || stack.getTag() != null && (stack.getTag().contains("BlockEntityTag") || stack.getTag().contains("Items"));
    }
    /** 未知存储实现保持原物品；不对可能指向共享库存的模组背包做快照复制。 */
    public static Plan prepare(ItemStack stack) {
        if (!localContainer(stack)) return null;
        CompoundTag source = stack.getTag();
        CompoundTag contentsTag = source == null ? new CompoundTag()
                : stack.is(net.minecraft.world.item.Items.BUNDLE) ? source : source.getCompound("BlockEntityTag");
        if (contentsTag.contains("LootTable") || contentsTag.contains("Items") && !contentsTag.contains("Items", Tag.TAG_LIST))
            return null;
        var entries = contentsTag.getList("Items", Tag.TAG_COMPOUND);
        if (contentsTag.get("Items") instanceof net.minecraft.nbt.ListTag raw
                && !raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) return null;
        if (entries.size() > 256 || !entries.isEmpty() && stack.getCount() != 1) return null;
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ItemStack item = ItemStack.of(entries.getCompound(i));
            if (item.isEmpty() || item.getCount() <= 0) return null;
            contents.add(item);
        }
        // 重建物品，不把 BlockEntityTag 或 ForgeCaps 带入知识与输出。
        ItemStack shell = TransmutationNbtPolicy.safeOutput(stack);
        return new Plan(shell, List.copyOf(contents));
    }
    public static boolean sell(TransmutationInventory inventory, ItemStack original, Runnable consume) {
        if (!(inventory.player instanceof ServerPlayer player)) return false;
        Plan plan = prepare(original);
        if (plan == null || EMCHelper.getEmcSellValue(plan.shell) <= 0) return false;
        long price = EMCHelper.getEmcSellValue(plan.shell);
        consume.run();
        for (ItemStack item : plan.contents) player.getInventory().placeItemBackInInventory(item.copy());
        inventory.handleKnowledge(plan.shell);
        inventory.addEmc(BigInteger.valueOf(price).multiply(BigInteger.valueOf(original.getCount())));
        return true;
    }
}
