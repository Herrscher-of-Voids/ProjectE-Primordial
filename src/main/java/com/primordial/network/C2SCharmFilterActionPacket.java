/**
 * 【文件职责】向服务端提交护符编辑动作。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：不使用原版仅单字节的容器按钮动作编码。
 * - 新增：VarInt动作、菜单ID与状态修订校验。
 * - 修改：通过有界动作操作服务端已有样本；过期修订拒绝，避免快速点击误删其它条目。
 * - 用途：客户端不能写任意NBT，也不能编辑没有打开的菜单。
 */
package com.primordial.network;

import com.primordial.menu.BlackholeCharmMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** 原版容器按钮只传单字节，过滤器动作改用 VarInt，并校验服务器状态修订防止误删。 */
public record C2SCharmFilterActionPacket(int containerId, int revision, int action) {
    public static void encode(C2SCharmFilterActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.revision); buf.writeVarInt(msg.action);
    }
    public static C2SCharmFilterActionPacket decode(FriendlyByteBuf buf) {
        return new C2SCharmFilterActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
    public static void handle(C2SCharmFilterActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof BlackholeCharmMenu menu
                    && menu.containerId == msg.containerId && menu.revision() == msg.revision)
                menu.clickMenuButton(player, msg.action);
        });
        ctx.get().setPacketHandled(true);
    }
}
