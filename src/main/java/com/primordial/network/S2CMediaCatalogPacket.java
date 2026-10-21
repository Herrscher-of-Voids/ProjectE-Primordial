/**
 * 【文件职责】把当前转化桌的少量可购买介质 ID 返回客户端目录控件。
 * 【变更记录 #1】2026-10-02 17:18:00（第 1 次备注 · 1.2.0-media-catalog）
 * - 删除：无。
 * - 新增：最多 5 个 ID、总数和查询标识的 S2C 回包。
 * - 修改：客户端接收端隔离在嵌套类，专用服务器不解析 Minecraft 客户端类型。
 * - 用途：过期菜单、旧搜索词和旧页码结果不会覆盖当前选择。
 */
/**
 * 【变更记录 #2】2026-10-02 18:00:13（第 2 次备注 · 1.2.0-media-tab）
 * - 删除：五项回包限制。
 * - 新增：27 项上限校验。
 * - 修改：目录分页容量与独立介质页网格一致。
 * - 用途：保持网络边界有界，同时容纳整页图标。
 */
/**
 * 【变更记录 #3】2026-10-12 14:35:41（第 3 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：无。
 * - 新增：无。
 * - 修改：S2C 目录页解码上限从 27 调整为 16，与服务端目录页和 ProjectE 原生知识槽数量一致。
 * - 用途：继续拒绝超出界面的伪造大回包，并保证客户端分页边界与服务器协议一致。
 */
/**
 * 【变更记录 #4】2026-10-12 14:52:00（第 4 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：无。
 * - 新增：无。
 * - 修改：目录回包解码上限采用 16 项槽位容量；页面偏移另按 12 项步进，模拟 ProjectE 原版的 4 项分页重叠。
 * - 用途：限制不可信目录消息的有界长度，维持客户端/服务端与原版分页协议一致。
 */
/**
 * 【变更记录 #5】2026-10-12 15:03:11（第 5 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：无。
 * - 新增：无。
 * - 修改：保留 16 项解码上限；虽原版每次页码步进 12 项，实际知识槽仍有 16 个，后 4 项在相邻页重叠。
 * - 用途：使网络解码边界匹配服务端 PAGE_SIZE，而不把翻页步长误当页容量。
 */
package com.primordial.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record S2CMediaCatalogPacket(int containerId, boolean gas, String query, int page,
        List<String> ids, int total) {
    public static void encode(S2CMediaCatalogPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.gas);
        buf.writeUtf(msg.query, 64);
        buf.writeVarInt(msg.page);
        buf.writeVarInt(msg.ids.size());
        for (String id : msg.ids) buf.writeUtf(id, 128);
        buf.writeVarInt(msg.total);
    }
    public static S2CMediaCatalogPacket decode(FriendlyByteBuf buf) {
        int container = buf.readVarInt();
        boolean gas = buf.readBoolean();
        String query = buf.readUtf(64);
        int page = buf.readVarInt();
        int count = buf.readVarInt();
        if (count < 0 || count > 16) throw new io.netty.handler.codec.DecoderException("Invalid media catalog page size");
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buf.readUtf(128));
        return new S2CMediaCatalogPacket(container, gas, query, page, List.copyOf(ids), buf.readVarInt());
    }
    public static void handle(S2CMediaCatalogPacket msg, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientHandler.accept(msg));
        supplier.get().setPacketHandled(true);
    }
    private static final class ClientHandler {
        private static void accept(S2CMediaCatalogPacket msg) {
            com.primordial.client.TransmutationMediaButton.acceptCatalog(msg);
        }
    }
}
