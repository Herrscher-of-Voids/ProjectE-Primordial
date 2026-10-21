/**
 * 【文件职责】接入转化桌 NBT 安全保留、学习及输出检查。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：普通安全 NBT、耐久与附魔保留入口，服务端权威检查并隔离容器状态。
 * - 修改：沿用 ProjectE 知识、价格和同步，设置仅影响之后的学习。
 * - 用途：保留物品变体，阻止容器内容进入可重复生成的知识；不改变历史知识。
 */
/**
 * 【变更记录 #2】2026-10-02 19:29:06（第 2 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：不存在的 ItemInfo.createStack(int) 调用。
 * - 新增：重建安全物品后恢复数量。
 * - 修改：按 ProjectE 1.20.1 的无参数 API 创建输出。
 * - 用途：兼容实际依赖且不携带旧 capability 状态。
 */
/**
 * 【变更记录 #3】2026-10-02 19:35:00（第 3 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：零损耗知识与无 Damage 标签知识被当成不同物品的歧义。
 * - 新增：自动补充对存储/能源容器完整序列化状态的安全检查。
 * - 修改：零 Damage 归一化为缺省；有额外存储状态的样本不得通过基础物品知识生成。
 * - 用途：防止转化桌清理容器后，机器仍用带内容的样本复制库存或充能状态。
 */
/**
 * 【变更记录 #4】2026-10-13 04:37:24（第 4 次备注 · 1.2.0-nbt-independent-options）
 * - 删除：Damage 与附魔列表必须依赖普通安全 NBT bit 1 才会被保留的条件。
 * - 新增：bit 1、bit 2、bit 4 分别控制普通安全字段、耐久和附魔组合。
 * - 修改：后两类字段单独进入规范化知识键；仍执行字段类型、深度、节点数和总字节上限过滤。
 * - 用途：实现三个独立选项，同时拒绝容器内容及未知模组状态参与知识与产物生成。
 */
package com.primordial.service;

import com.primordial.data.TransmutationNbtSettings;
import moze_intel.projecte.api.ItemInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.Set;

public final class TransmutationNbtPolicy {
    private TransmutationNbtPolicy() { }
    // 只复制已知物品属性，不复制容器、能源、坐标、所有权或未知模组运行状态。
    private static final Set<String> SAFE = Set.of("display", "HideFlags", "CustomModelData", "RepairCost",
            "Unbreakable", "CanDestroy", "CanPlaceOn", "Potion", "CustomPotionEffects", "CustomPotionColor",
            "Trim", "Fireworks", "Explosion", "AttributeModifiers");
    public static ItemInfo learned(ServerPlayer player, ItemInfo info) {
        return normalize(info, TransmutationNbtSettings.get(player.server).flags(player.getUUID()));
    }
    public static ItemInfo persistent(ItemInfo info) { return normalize(info, 7); }
    public static boolean canGenerate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        boolean container = TransmutationContainerReturn.handles(stack)
                || stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).isPresent()
                || stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
        CompoundTag serialized = stack.copyWithCount(1).save(new CompoundTag());
        if (!container && !serialized.contains("ForgeCaps")) return true;
        return serialized.equals(safeOutput(stack).copyWithCount(1).save(new CompoundTag()));
    }
    public static ItemStack safeOutput(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack output = persistent(ItemInfo.fromStack(stack)).createStack();
        output.setCount(stack.getCount());
        return output;
    }
    public static ItemInfo normalize(ItemInfo info, int flags) {
        if (!info.hasNBT()) return info;
        CompoundTag source = info.getNBT(), result = new CompoundTag();
        if ((flags & 1) != 0) {
            for (String key : SAFE) {
                Tag value = source.get(key);
                if (value != null && bounded(value, 0, new int[]{4096})) result.put(key, value.copy());
            }
        }
        if ((flags & 2) != 0 && source.contains("Damage", 99)) {
            ItemStack sample = new ItemStack(info.getItem());
            if (sample.isDamageableItem() && source.getInt("Damage") > 0)
                result.putInt("Damage", Math.min(sample.getMaxDamage(), source.getInt("Damage")));
        }
        if ((flags & 4) != 0) {
            for (String key : new String[]{"Enchantments", "StoredEnchantments"}) {
                Tag value = source.get(key);
                if (value instanceof net.minecraft.nbt.ListTag && bounded(value, 0, new int[]{4096}))
                    result.put(key, value.copy());
            }
        }
        if (result.sizeInBytes() > 32768) result = new CompoundTag();
        return ItemInfo.fromItem(info.getItem(), result.isEmpty() ? null : result);
    }
    private static boolean bounded(Tag tag, int depth, int[] remaining) {
        if (depth > 16 || --remaining[0] < 0) return false;
        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) if (!bounded(compound.get(key), depth + 1, remaining)) return false;
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (Tag child : list) if (!bounded(child, depth + 1, remaining)) return false;
        }
        return tag.sizeInBytes() <= 32768;
    }
}
