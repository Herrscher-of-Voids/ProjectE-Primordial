/**
 * 【文件职责】保存玩家背包指定槽位的物品维持规则，供服务端定时任务按槽位补充。
 * 【变更记录 #1】2026-09-14 12:50:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：按 UUID 保存槽位、过滤物品和目标数量；旧存档缺少规则时保持空配置。
 * - 修改：规则数据与机器配置分离，补充时只写入指定槽位，避免物品乱塞到其它栏位。
 * - 用途：玩家背包配置页和 PlayerInventoryTask 共用的持久化模型；所有数量仍受 int 上限约束。
 */
/**
 * 【变更记录 #8】2026-09-14 16:45:00（第 8 次备注 · 1.0.0-stage5-feedback2-hotfix）
 * - 删除：运行时把旧 rate 字段当作固定模式速率的路径；旧字段仅保留给已有存档读取。
 * - 新增：固定模式使用 amount 数量字段，补充模式根据目标容量或缺口计算，不受固定数量影响。
 * - 修改：EnergyConfig、玩家背包规则、配置器界面和任务执行器统一采用“数量 + tick 间隔”语义，并继续限制非法值。
 * - 用途：避免高容量设备因为旧上限或错误速率解释而产生过量循环、配置丢失或跨槽位写入。
 */
/**
 * 【变更记录 #9】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #10】2026-09-15 10:59:49（第 10 次备注 · 补充规则暂停修复）
 * - 删除：无；保留原有规则数据和 NBT 字段。
 * - 新增：setEnabled 方法，只更新指定槽位规则的启用状态并保留过滤器、模式、数量和 tick 间隔。
 * - 修改：不存在的规则不会因暂停请求被创建，启用状态变化后标记 SavedData 为脏数据以便持久化。
 * - 用途：玩家背包配置器可以安全暂停或恢复补充物品，PlayerInventoryTask 通过 enabled 判断立即停止执行。
 */
/**
 * 【变更记录 #11】2026-10-14 17:12:00（第 11 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：按个人 UUID 保存两个玩家魔法资源的自动补充开关。
 * - 修改：新存档字段与槽位规则独立，默认关闭，读取只接受已定义的资源索引。
 * - 用途：重登保持设置，暂停后不继续购买，不创建重复资源库存。
 */
package com.primordial.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

public class PlayerInventoryConfigSavedData extends SavedData {
    private static final String NAME = "primordial_player_inventory_configs";
    public record Rule(ItemStack filter, int targetCount, int intervalTicks, SampleMode mode, boolean enabled) {}
    private final Map<UUID, Map<Integer, Rule>> rules = new HashMap<>();
    private final Map<UUID, Integer> resourceFlags = new HashMap<>();

    public boolean resourceEnabled(UUID player, int index) {
        return index >= 0 && index < com.primordial.resource.PlayerResourcePorts.COUNT
                && (resourceFlags.getOrDefault(player, 0) & (1 << index)) != 0;
    }

    public void toggleResource(UUID player, int index) {
        if (index < 0 || index >= com.primordial.resource.PlayerResourcePorts.COUNT) return;
        int next = resourceFlags.getOrDefault(player, 0) ^ (1 << index);
        if (next == 0) resourceFlags.remove(player); else resourceFlags.put(player, next);
        setDirty();
    }

    public static PlayerInventoryConfigSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                PlayerInventoryConfigSavedData::load, PlayerInventoryConfigSavedData::new, NAME);
    }

    public Rule get(UUID player, int slot) {
        return rules.getOrDefault(player, Map.of()).get(slot);
    }

    public void set(UUID player, int slot, ItemStack filter, int targetCount, boolean enabled) {
        set(player, slot, filter, targetCount, 1, SampleMode.KEEP_LEVEL, enabled);
    }

    public void set(UUID player, int slot, ItemStack filter, int targetCount, int intervalTicks, SampleMode mode, boolean enabled) {
        if (slot < 0 || slot >= 36 || filter == null || filter.isEmpty()) return;
        rules.computeIfAbsent(player, ignored -> new HashMap<>()).put(slot,
                new Rule(filter.copyWithCount(1), ConfigLimits.amount(targetCount, 0),
                        ConfigLimits.intervalTicks(intervalTicks), mode == null ? SampleMode.KEEP_LEVEL : mode, enabled));
        setDirty();
    }

    public void clear(UUID player, int slot) {
        Map<Integer, Rule> user = rules.get(player);
        if (user != null && user.remove(slot) != null) { if (user.isEmpty()) rules.remove(player); setDirty(); }
    }

    /**
     * 只修改指定规则的启用状态，保留过滤器、模式、数量和间隔。
     * 规则不存在时不创建新规则，避免暂停按钮意外产生无法显示的配置。
     */
    public void setEnabled(UUID player, int slot, boolean enabled) {
        Map<Integer, Rule> user = rules.get(player);
        Rule current = user == null ? null : user.get(slot);
        if (current == null || current.enabled() == enabled) return;
        user.put(slot, new Rule(current.filter().copy(), current.targetCount(), current.intervalTicks(), current.mode(), enabled));
        setDirty();
    }

    public Map<Integer, Rule> rules(UUID player) { return Map.copyOf(rules.getOrDefault(player, Map.of())); }

    @Override public CompoundTag save(CompoundTag tag) {
        CompoundTag root = new CompoundTag();
        for (var user : rules.entrySet()) {
            ListTag list = new ListTag();
            for (var entry : user.getValue().entrySet()) {
                Rule rule = entry.getValue(); CompoundTag value = new CompoundTag();
                value.putInt("slot", entry.getKey()); value.putInt("count", rule.targetCount()); value.putInt("intervalTicks", rule.intervalTicks());
                value.putString("mode", rule.mode().name()); value.putBoolean("enabled", rule.enabled());
                value.put("item", rule.filter().save(new CompoundTag())); list.add(value);
            }
            root.put(user.getKey().toString(), list);
        }
        tag.put("rules", root);
        CompoundTag resources = new CompoundTag();
        resourceFlags.forEach((owner, flags) -> resources.putInt(owner.toString(), flags));
        tag.put("playerResources", resources); return tag;
    }

    public static PlayerInventoryConfigSavedData load(CompoundTag tag) {
        PlayerInventoryConfigSavedData data = new PlayerInventoryConfigSavedData();
        CompoundTag resources = tag.getCompound("playerResources");
        for (String id : resources.getAllKeys()) try {
            int flags = resources.getInt(id) & ((1 << com.primordial.resource.PlayerResourcePorts.COUNT) - 1);
            if (flags != 0) data.resourceFlags.put(UUID.fromString(id), flags);
        } catch (IllegalArgumentException ignored) { }
        CompoundTag root = tag.getCompound("rules");
        for (String id : root.getAllKeys()) try {
            UUID player = UUID.fromString(id); ListTag list = root.getList(id, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) { CompoundTag value = list.getCompound(i); int slot = value.getInt("slot");
                ItemStack filter = ItemStack.of(value.getCompound("item"));
                if (slot >= 0 && slot < 36 && !filter.isEmpty()) {
                    SampleMode mode; try { mode = SampleMode.valueOf(value.getString("mode")); } catch (IllegalArgumentException ex) { mode = SampleMode.KEEP_LEVEL; }
                    int interval = value.contains("intervalTicks") ? value.getInt("intervalTicks") : 1;
                    data.set(player, slot, filter, value.getInt("count"), interval, mode, value.getBoolean("enabled"));
                } }
        } catch (IllegalArgumentException ignored) { }
        data.setDirty(false); return data;
    }
}
