/**
 * 【文件职责】将菜单绑定的真实资源交易结果反馈给客户端。
 * 【变更记录 #1】2026-10-10 03:53:52（第 1 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：有界状态、注册名、实际数量、精确 EMC 和买卖方向回包。
 * - 修改：客户端只在匹配转化桌显示结果，不作为知识、余额或成交授权。
 * - 用途：明确容器容量、知识、价格和背包不足的失败原因，专用服务端不链接客户端类。
 */
package com.primordial.network;

import com.primordial.service.ResourceTradeResult;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Supplier;

public record S2CMediaTradeResultPacket(int containerId, boolean buy, ResourceTradeResult result) {
    public static final int MAX_VALUE_LENGTH = 16384;
    public static void encode(S2CMediaTradeResultPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.buy);
        buf.writeEnum(msg.result.status());
        buf.writeUtf(msg.result.unit(), 128);
        buf.writeVarLong(msg.result.units());
        buf.writeUtf(msg.result.emc(), MAX_VALUE_LENGTH);
    }
    public static S2CMediaTradeResultPacket decode(FriendlyByteBuf buf) {
        int menu = buf.readVarInt();
        boolean buy = buf.readBoolean();
        var status = buf.readEnum(ResourceTradeResult.Status.class);
        String id = buf.readUtf(128);
        long units = buf.readVarLong();
        String value = buf.readUtf(MAX_VALUE_LENGTH);
        if (units < 0) throw new io.netty.handler.codec.DecoderException("Negative resource trade quantity");
        return new S2CMediaTradeResultPacket(menu, buy, new ResourceTradeResult(status, id, units, value));
    }
    public static void send(ServerPlayer player, boolean buy, ResourceTradeResult result) {
        PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CMediaTradeResultPacket(player.containerMenu.containerId, buy, result));
    }
    public static void handle(S2CMediaTradeResultPacket msg, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientHandler.accept(msg));
        supplier.get().setPacketHandled(true);
    }
    private static final class ClientHandler {
        static void accept(S2CMediaTradeResultPacket packet) {
            com.primordial.client.TransmutationMediaButton.acceptTradeResult(packet);
        }
    }
}
