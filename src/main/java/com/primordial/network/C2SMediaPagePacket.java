/**
 * 【文件职责】同步转化桌页签状态，绑定发送者当前菜单。
 * 【变更记录 #1】2026-10-12 23:39:00（第 1 次备注 · 1.2.0-media-real-slots）
 * - 删除：无。新增：菜单 ID、介质页和气体类别请求。
 * - 修改：只在服务端主线程更新真实槽状态；用途：隐藏页不能继续充电或接受容器。
 */
package com.primordial.network;

import com.primordial.service.MediaChargingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SMediaPagePacket(int menuId, boolean active, boolean gas) {
    public static void encode(C2SMediaPagePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.menuId); buf.writeBoolean(msg.active); buf.writeBoolean(msg.gas);
    }
    public static C2SMediaPagePacket decode(FriendlyByteBuf buf) {
        return new C2SMediaPagePacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }
    public static void handle(C2SMediaPagePacket msg, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null && player.containerMenu.containerId == msg.menuId
                    && player.containerMenu instanceof MediaChargingMenu menu) {
                menu.primordial$setMediaPage(msg.active, msg.gas);
                player.containerMenu.broadcastChanges();
            }
        });
        context.setPacketHandled(true);
    }
}
