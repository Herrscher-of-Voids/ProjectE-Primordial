/**
 * 【文件职责】返回有界 JEI 介质报价，零价代表不可兑换。
 * 【变更记录 #1】2026-10-09 01:36:12（第 1 次备注 · 1.2.0-jei-media-knowledge）
 * - 删除：无。新增：请求编号、介质标识和精确价格回包。
 * - 修改：客户端接收隔离到嵌套类。用途：专用服务器安全加载，旧请求不能覆盖新报价。
 */
package com.primordial.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

public record S2CJeiMediaQuotesPacket(int request, List<Entry> entries) {
    public record Entry(C2SJeiMediaQuotesPacket.Key key, String price) { }
    public static void encode(S2CJeiMediaQuotesPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.request); buf.writeVarInt(msg.entries.size());
        for (Entry entry : msg.entries) { entry.key.write(buf); buf.writeUtf(entry.price, 4354); }
    }
    public static S2CJeiMediaQuotesPacket decode(FriendlyByteBuf buf) {
        int request = buf.readVarInt(), count = C2SJeiMediaQuotesPacket.readCount(buf);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(C2SJeiMediaQuotesPacket.Key.read(buf), buf.readUtf(4354)));
        return new S2CJeiMediaQuotesPacket(request, List.copyOf(entries));
    }
    public static void handle(S2CJeiMediaQuotesPacket msg, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientHandler.accept(msg));
        supplier.get().setPacketHandled(true);
    }
    private static final class ClientHandler {
        static void accept(S2CJeiMediaQuotesPacket msg) { com.primordial.client.JeiMediaQuotes.accept(msg); }
    }
}
