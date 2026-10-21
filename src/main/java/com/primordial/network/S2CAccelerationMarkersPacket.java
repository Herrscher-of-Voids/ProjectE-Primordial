/**
 * 【文件职责】向附近玩家同步加速位置和倍率。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：每玩家最多256标记的有界快照，携带维度、坐标和二次幂倍率。
 * - 修改：逐玩家发送，解码拒绝异常数量与倍率，客户端类型隔离在内部接收器。
 * - 用途：专用服务器可注册数据包，不因显示代码链接客户端类。
 */
/**
 * 【变更记录 #2】2026-09-18 01:44:57（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：标记只能固定在方块坐标的限制。
 * - 新增：实体运行时编号，-1 继续表示方块，客户端按当前实体位置绘制倍率。
 * - 修改：编码/解码同步追加有界编号；协议版本随管理界面一起升级。
 * - 用途：移动实体不留下滞后的坐标标签，数量、距离及超时限制继续有效。
 */
package com.primordial.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.List;
import java.util.function.Supplier;

/** 服务端定期发送附近加速目标快照；快照有界，客户端只负责短期显示，不参与计费或执行。 */
public record S2CAccelerationMarkersPacket(List<Marker> markers) {
    public static final int MAX_MARKERS = 256;
    public record Marker(ResourceLocation dimension, BlockPos position, int multiplier, int entityId) {
        public Marker(ResourceLocation dimension, BlockPos position, int multiplier) { this(dimension, position, multiplier, -1); }
    }

    public static void send(net.minecraft.server.level.ServerPlayer player, List<Marker> markers) {
        PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CAccelerationMarkersPacket(List.copyOf(markers)));
    }

    public static void encode(S2CAccelerationMarkersPacket message, FriendlyByteBuf buffer) {
        int count = Math.min(message.markers.size(), MAX_MARKERS);
        buffer.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Marker marker = message.markers.get(i);
            buffer.writeResourceLocation(marker.dimension());
            buffer.writeBlockPos(marker.position());
            buffer.writeVarInt(marker.multiplier());
            buffer.writeVarInt(marker.entityId());
        }
    }

    public static S2CAccelerationMarkersPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_MARKERS) throw new io.netty.handler.codec.DecoderException("Too many acceleration markers");
        var markers = new java.util.ArrayList<Marker>(count);
        for (int i = 0; i < count; i++) {
            ResourceLocation dimension = buffer.readResourceLocation();
            BlockPos position = buffer.readBlockPos();
            int multiplier = buffer.readVarInt();
            if (dimension == null || multiplier < 2 || multiplier > 1024 || (multiplier & (multiplier - 1)) != 0)
                throw new io.netty.handler.codec.DecoderException("Invalid acceleration marker");
            int entityId = buffer.readVarInt();
            if (entityId < -1) throw new io.netty.handler.codec.DecoderException("Invalid acceleration entity marker");
            markers.add(new Marker(dimension, position, multiplier, entityId));
        }
        return new S2CAccelerationMarkersPacket(List.copyOf(markers));
    }

    public static void handle(S2CAccelerationMarkersPacket message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientHandler.accept(message));
        context.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        static void accept(S2CAccelerationMarkersPacket message) {
            com.primordial.client.ClientAccelerationMarkers.replace(message.markers());
        }
    }
}
