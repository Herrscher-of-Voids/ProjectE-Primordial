/**
 * 【文件职责】保存玩家背包指定槽位的维持数量规则。
 * 【变更记录 #1】2026-09-14 13:40:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：客户端提交槽位、过滤物品、目标数量和启用状态。
 * - 修改：服务端只接受当前玩家实际背包槽位，并从该槽位复制过滤器，防止跨玩家配置。
 * - 用途：玩家背包配置页设置“维持某栏位数量”，补充任务严格写回同一槽位。
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
 * 【变更记录 #10】2026-09-15 11:08:00（第 10 次备注 · 1.0.0-player-inventory-pause）
 * - 删除：编辑暂停规则时无条件使用客户端 enabled=true 的路径。
 * - 新增：菜单 ID 校验，过期窗口发来的设置请求不生效。
 * - 修改：已有规则保留服务端启停状态；新规则按请求创建，恢复必须使用独立启用操作。
 * - 用途：避免暂停后调整数量或模式又意外恢复补充，变更后同步可见状态。
 */
package com.primordial.network;

import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.data.SampleMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SPlayerInventoryRulePacket(int containerId, int slot, int targetCount, int intervalTicks, SampleMode mode, boolean enabled, ItemStack filter) {
    public static void encode(C2SPlayerInventoryRulePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeByte(msg.slot); buf.writeVarInt(msg.targetCount); buf.writeVarInt(msg.intervalTicks); buf.writeByte(msg.mode.ordinal()); buf.writeBoolean(msg.enabled); buf.writeItem(msg.filter);
    }
    public static C2SPlayerInventoryRulePacket decode(FriendlyByteBuf buf) {
        return new C2SPlayerInventoryRulePacket(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(),
                SampleMode.VALUES[buf.readByte() & 1], buf.readBoolean(), buf.readItem());
    }
    public static void handle(C2SPlayerInventoryRulePacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof com.primordial.menu.PlayerInventoryMenu menu)
                    || menu.containerId != msg.containerId
                    || msg.slot < 0 || msg.slot >= 36 || msg.filter == null || msg.filter.isEmpty()
                    || msg.targetCount < 0 || msg.intervalTicks < 1 || msg.mode == null) return;
            var data = PlayerInventoryConfigSavedData.get(player.server);
            var existing = data.get(player.getUUID(), msg.slot);
            boolean enabled = existing == null ? msg.enabled : existing.enabled();
            data.set(player.getUUID(), msg.slot, msg.filter, msg.targetCount, msg.intervalTicks, msg.mode, enabled);
            menu.broadcastChanges();
        }); context.setPacketHandled(true);
    }
}
