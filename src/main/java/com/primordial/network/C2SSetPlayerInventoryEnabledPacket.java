/**
 * 【文件职责】接收玩家背包配置页的批量暂停/启用请求，只修改规则的 enabled 标志。
 * 服务端验证当前菜单、槽位范围和发送者身份后写入 PlayerInventoryConfigSavedData；该包不携带物品内容，
 * 因此不能通过暂停操作移动、复制或替换玩家背包中的物品。
 *
 * 【变更记录 #1】2026-09-15 01:35:00（第 1 次备注 · 补充规则暂停修复）
 * - 删除：无；保留原有 C2SPlayerInventoryRulePacket 的创建和编辑流程。
 * - 新增：批量设置多个玩家背包规则为暂停或启用的网络包，槽位数量限制为 36。
 * - 修改：无；本包只改变持久化规则的 enabled 字段，过滤器、模式、数量和 tick 间隔保持不变。
 * - 用途：为补充物品提供明确的暂停入口，服务端拒绝越界槽位和未打开玩家背包配置页的请求。
 */
/**
 * 【变更记录 #2】2026-09-15 11:05:49（第 2 次备注 · 1.0.0-player-inventory-pause）
 * - 删除：解码时静默截断超长槽位列表的做法，避免把剩余槽位误读成 enabled。
 * - 新增：containerId 校验，拒绝已关闭菜单的延迟请求；数量和索引越界时拒绝整包。
 * - 修改：修改后立即广播菜单状态；暂停和恢复使用已有规则，槽位当前为空也能操作。
 * - 用途：确保玩家看到已暂停时服务端确已停止该规则，批量请求最多处理 36 个槽位。
 */
package com.primordial.network;

import java.util.Arrays;
import java.util.function.Supplier;

import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.menu.PlayerInventoryMenu;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** C→S 玩家背包规则开关包；enabled=false 表示暂停，enabled=true 表示恢复。 */
public record C2SSetPlayerInventoryEnabledPacket(int containerId, int[] slots, boolean enabled) {
    private static final int MAX_SLOTS = PlayerInventoryMenu.SLOT_COUNT;

    public C2SSetPlayerInventoryEnabledPacket {
        if (slots == null || slots.length > MAX_SLOTS) throw new IllegalArgumentException("Invalid slot count");
        slots = Arrays.copyOf(slots, slots.length);
        for (int slot : slots) {
            if (slot < 0 || slot >= MAX_SLOTS) throw new IllegalArgumentException("Invalid inventory slot");
        }
    }

    @Override public int[] slots() { return slots.clone(); }

    public static void encode(C2SSetPlayerInventoryEnabledPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeByte(msg.slots.length);
        for (int slot : msg.slots) buf.writeByte(slot);
        buf.writeBoolean(msg.enabled);
    }

    public static C2SSetPlayerInventoryEnabledPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int count = buf.readUnsignedByte();
        if (count > MAX_SLOTS) throw new DecoderException("Too many inventory slots");
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            slots[i] = buf.readUnsignedByte();
            if (slots[i] >= MAX_SLOTS) throw new DecoderException("Invalid inventory slot");
        }
        return new C2SSetPlayerInventoryEnabledPacket(containerId, slots, buf.readBoolean());
    }

    public static void handle(C2SSetPlayerInventoryEnabledPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof PlayerInventoryMenu menu)
                    || menu.containerId != msg.containerId) return;
            PlayerInventoryConfigSavedData data = PlayerInventoryConfigSavedData.get(player.server);
            for (int slot : msg.slots) {
                data.setEnabled(player.getUUID(), slot, msg.enabled);
            }
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
