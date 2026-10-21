/**
 * 【文件职责】接收配置器幽灵槽或 JEI 投放的物品过滤器。
 * 【变更记录 #1】2026-09-14 13:00:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：按菜单、section、槽位写入完整 ItemStack（保留 NBT）。
 * - 修改：服务端验证玩家仍拥有菜单编辑权，避免跨机器写入。
 * - 用途：为 JEI Ghost Ingredient 和客户端拖放提供统一的物品过滤器入口。
 */
/**
 * 【变更记录 #2】2026-09-15 12:04:51（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：仅保存配置而不更新服务端样本物品的分支。
 * - 新增：权威菜单回填和槽位广播。
 * - 修改：样本始终为带原 NBT 的一件物品，写入后槽显示与存档引用一致。
 * - 用途：解决 JEI 拖放后的显示/保存不同步，不消耗真实玩家物品。
 */
package com.primordial.network;

import com.primordial.data.MachineConfigSavedData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public record C2SSetItemFilterPacket(int containerId, int section, int slot, ItemStack stack) {
    public static void encode(C2SSetItemFilterPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.section); buf.writeByte(msg.slot); buf.writeItem(msg.stack);
    }
    public static C2SSetItemFilterPacket decode(FriendlyByteBuf buf) {
        return new C2SSetItemFilterPacket(buf.readVarInt(), buf.readByte(), buf.readByte(), buf.readItem());
    }
    public static void handle(C2SSetItemFilterPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof com.primordial.menu.ConfiguratorMenu menu)
                    || menu.containerId != msg.containerId || !menu.canEdit(player)
                    || msg.section < 0 || msg.section > 1 || msg.slot < 0 || msg.slot >= 9 || msg.stack.isEmpty()) return;
            var config = msg.section == 0 ? menu.config.itemIn[msg.slot] : menu.config.itemOut[msg.slot];
            config.filter = msg.stack.copyWithCount(1); config.enabled = true;
            menu.refillFromConfig();
            menu.broadcastChanges();
            MachineConfigSavedData.get(player.server).setDirty();
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CFullConfigSyncPacket(menu.machineKey, menu.config.save()));
        }); context.setPacketHandled(true);
    }
}
