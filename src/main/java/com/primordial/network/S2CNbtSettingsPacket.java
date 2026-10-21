/**
 * 【文件职责】转化桌 NBT 设置的网络请求或回复。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：三个标志位与菜单绑定同步；总开关关闭时禁用两个子选项。
 * - 修改：使用服务端回复显示真实设置，不接受过期菜单或非法标志位。
 * - 用途：设置只改变后续学习，界面按钮位于转化桌外侧，不覆盖或透传底下槽位。
 */
package com.primordial.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CNbtSettingsPacket(int containerId, int flags) {
    public static void encode(S2CNbtSettingsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.flags);
    }
    public static S2CNbtSettingsPacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt(), flags = buf.readUnsignedByte();
        if (flags > 7) throw new io.netty.handler.codec.DecoderException("Invalid NBT settings");
        return new S2CNbtSettingsPacket(id, flags);
    }
    public static void handle(S2CNbtSettingsPacket msg, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> ClientHandler.accept(msg));
        supplier.get().setPacketHandled(true);
    }
    private static final class ClientHandler {
        static void accept(S2CNbtSettingsPacket msg) { com.primordial.client.TransmutationNbtButtons.accept(msg); }
    }
}
