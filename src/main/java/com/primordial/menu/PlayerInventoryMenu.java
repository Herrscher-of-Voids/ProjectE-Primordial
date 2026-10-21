/**
 * 【编码修复说明】PlayerInventoryMenu 的原备注因错误字符集转换被写成问号，已根据当前实现恢复。
 * - 删除：不可读的问号文本。
 * - 新增：职责、边界条件和失败处理说明。
 * - 修改：只修复注释内容，不改变字段、协议、算法或运行时行为。
 * - 用途：帮助维护者理解本段代码，并确保后续修改可追溯。
 */
/**
 * 【变更记录 #7】2026-09-14 16:20:00（第 7 次备注 · 1.0.0-stage5-feedback2）
 * - 删除：旧的秒间隔解释、未限制的补充扫描和无法明确绑定目标的回退路径。
 * - 新增：以 intervalTicks 表示固定模式间隔，并让补充模式每 tick 检查；配置器、网络包和任务调度器共用该单位。
 * - 修改：NBT 读取兼容旧字段，旧秒数转换为 tick；目标失效时暂停当前规则，不改写其它槽位。
 * - 用途：保证 GUI、配置数据和服务端任务对时间单位与目标选择保持一致，避免 JEI、Mekanism 气体和机器端口出现错配。
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
 * 【文件职责】展示玩家背包的只读槽位，并通过菜单属性同步每条补充规则的启用状态。
 * 【变更记录 #10】2026-09-15 11:05:49（第 10 次备注 · 1.0.0-player-inventory-pause）
 * - 删除：无；保留禁止取出、放入和快速移动真实物品的限制。
 * - 新增：36 个菜单状态属性，区分未配置、已启用、已暂停；重新开菜单时从服务端存档读取。
 * - 修改：背包格子居中，为暂停按钮和规则说明留出空间；属性仅在变化时由原版菜单同步。
 * - 用途：PlayerInventoryScreen 显示服务端确认的状态，避免点过暂停却无法确认是否生效。
 */
/**
 * 【变更记录 #11】2026-09-15 12:04:51（第 11 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：Inventory Profiles Next 官方 IPNIgnore 注解。
 * - 修改：仅声明本菜单不接受 IPN 整理；槽位只读和服务器状态同步照常执行。
 * - 用途：阻止整理器将背包规则面板误认为普通库存，避免移动真实背包内容。
 */
/**
 * 【变更记录 #12】2026-10-14 17:12:00（第 12 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：两个玩家魔法补充开关的服务器菜单属性与按钮处理。
 * - 修改：只修改发起玩家自己的配置；客户端不能指定账户、价格或资源数量。
 * - 用途：可选模组不存在时禁用入口，菜单关闭后保留自动补充设置。
 */
package com.primordial.menu;

import com.primordial.data.PlayerInventoryConfigSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@org.anti_ad.mc.ipn.api.IPNIgnore
public class PlayerInventoryMenu extends AbstractContainerMenu {
    public static final int SLOT_COUNT = 36;
    public static final int UNCONFIGURED = 0;
    public static final int ENABLED = 1;
    public static final int PAUSED = 2;
    private final ContainerData ruleStates;

    public PlayerInventoryMenu(int id, Inventory inventory) {
        super(com.primordial.registry.ModMenus.PLAYER_INVENTORY.get(), id);
        addPlayerInventory(inventory);
        ruleStates = createRuleStates(inventory);
        addDataSlots(ruleStates);
    }

    public PlayerInventoryMenu(int id, Inventory inventory, FriendlyByteBuf ignored) {
        this(id, inventory);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new ReadOnlyInventorySlot(inventory, 9 + row * 9 + col, 43 + col * 18, 24 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new ReadOnlyInventorySlot(inventory, col, 43 + col * 18, 82));
    }

    /** 服务端只读取本玩家的 36 条规则；客户端使用菜单同步的整数，不持有 SavedData。 */
    private static ContainerData createRuleStates(Inventory inventory) {
        if (!(inventory.player instanceof ServerPlayer player)) return new SimpleContainerData(SLOT_COUNT + 2);
        var data = PlayerInventoryConfigSavedData.get(player.server);
        var owner = player.getUUID();
        return new ContainerData() {
            @Override public int get(int index) {
                if (index >= SLOT_COUNT) {
                    int resource = index - SLOT_COUNT;
                    var port = com.primordial.resource.PlayerResourcePorts.find(player, resource);
                    if (port == null) return UNCONFIGURED;
                    return data.resourceEnabled(owner, resource) ? ENABLED : PAUSED;
                }
                var rule = data.get(owner, inventoryIndex(index));
                return rule == null ? UNCONFIGURED : rule.enabled() ? ENABLED : PAUSED;
            }
            @Override public void set(int index, int value) { /* 服务端属性只读，开关修改须走权限校验包。 */ }
            @Override public int getCount() { return SLOT_COUNT + 2; }
        };
    }

    public int ruleState(int menuSlot) {
        return menuSlot >= 0 && menuSlot < SLOT_COUNT ? ruleStates.get(menuSlot) : UNCONFIGURED;
    }

    public int resourceState(int index) { return index >= 0 && index < 2 ? ruleStates.get(SLOT_COUNT + index) : UNCONFIGURED; }

    @Override public boolean clickMenuButton(Player player, int button) {
        int resource = button - 100;
        if (!(player instanceof ServerPlayer owner) || resource < 0 || resource >= 2
                || com.primordial.resource.PlayerResourcePorts.find(owner, resource) == null) return false;
        PlayerInventoryConfigSavedData.get(owner.server).toggleResource(owner.getUUID(), resource);
        broadcastChanges(); return true;
    }

    /** 将菜单槽位映射到玩家真实背包索引；映射只用于显示和规则编辑，不允许客户端直接搬动物品。 */
    public static int inventoryIndex(int menuSlot) {
        return menuSlot < 27 ? 9 + menuSlot : menuSlot - 27;
    }

    @Override public boolean stillValid(Player player) { return true; }

    /** 玩家背包菜单使用只读物品槽，配置按钮单独发送规则更新，避免拖拽、丢弃或 shift 点击改变背包内容。 */
    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId >= 0 && slotId < SLOT_COUNT) return;
        super.clicked(slotId, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static final class ReadOnlyInventorySlot extends Slot {
        private ReadOnlyInventorySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }
        @Override public void onTake(Player player, ItemStack stack) { }
    }
}
