/**
 * 【文件职责】转化桌 NBT 设置的网络请求或回复。
 * 【变更记录 #1】2026-10-02 19:24:27（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：三个标志位与菜单绑定同步；总开关关闭时禁用两个子选项。
 * - 修改：使用服务端回复显示真实设置，不接受过期菜单或非法标志位。
 * - 用途：设置只改变后续学习，界面按钮位于转化桌外侧，不覆盖或透传底下槽位。
 */
/**
 * 【变更记录 #2】2026-10-02 19:32:53（第 2 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：处理器内混合菜单验证和发送回复的结构。
 * - 新增：可独立验证的 apply 入口，返回权威状态或空结果。
 * - 修改：网络处理器仅发送验证成功后的回复。
 * - 用途：测试真实发送者隔离与过期菜单拒绝，不依赖网络连接替身。
 */
package com.primordial.network;

import com.primordial.data.TransmutationNbtSettings;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public record C2SNbtSettingsPacket(int containerId, int flags) {
    public static void encode(C2SNbtSettingsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.flags);
    }
    public static C2SNbtSettingsPacket decode(FriendlyByteBuf buf) {
        return new C2SNbtSettingsPacket(buf.readVarInt(), buf.readByte());
    }
    public static void handle(C2SNbtSettingsPacket msg, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            var reply = apply(player, msg);
            if (reply != null) PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), reply);
        });
        context.setPacketHandled(true);
    }

    static S2CNbtSettingsPacket apply(net.minecraft.server.level.ServerPlayer player, C2SNbtSettingsPacket msg) {
        if (player == null || !(player.containerMenu instanceof TransmutationContainer)
                || player.containerMenu.containerId != msg.containerId || msg.flags < -1 || msg.flags > 7) return null;
        var data = TransmutationNbtSettings.get(player.server);
        if (msg.flags >= 0) data.set(player.getUUID(), msg.flags);
        return new S2CNbtSettingsPacket(msg.containerId, data.flags(player.getUUID()));
    }
}
