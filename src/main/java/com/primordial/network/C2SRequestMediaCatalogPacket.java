/**
 * 【文件职责】请求当前转化桌中某类介质的有界搜索页。
 * 【变更记录 #1】2026-10-02 17:18:00（第 1 次备注 · 1.2.0-media-catalog）
 * - 删除：无。
 * - 新增：菜单 ID、类别、最多 64 字符查询和页码的 C2S 消息。
 * - 修改：无；服务端核对当前 ProjectE 转化桌后执行目录查询。
 * - 用途：不向客户端下发整份玩家知识或全部资源注册表。
 */
package com.primordial.network;

import com.primordial.service.TransmutationMediaCatalog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public record C2SRequestMediaCatalogPacket(int containerId, boolean gas, String query, int page) {
    public static void encode(C2SRequestMediaCatalogPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.gas);
        buf.writeUtf(msg.query, 64);
        buf.writeVarInt(msg.page);
    }
    public static C2SRequestMediaCatalogPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestMediaCatalogPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(64), buf.readVarInt());
    }
    public static void handle(C2SRequestMediaCatalogPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || !(player.containerMenu instanceof moze_intel.projecte.gameObjs.container.TransmutationContainer)
                    || player.containerMenu.containerId != msg.containerId || msg.page < 0 || msg.page > 10000) return;
            var result = TransmutationMediaCatalog.search(player, msg.gas, msg.query, msg.page);
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CMediaCatalogPacket(msg.containerId, msg.gas, msg.query, msg.page,
                            result.ids(), result.total()));
        });
        context.setPacketHandled(true);
    }
}
