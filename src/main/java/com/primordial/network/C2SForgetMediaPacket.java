/**
 * 【文件职责】在服务端验证当前转化桌并遗忘发送者所选单种介质。
 * 【变更记录 #1】2026-10-02 18:48:00（第 1 次备注 · 1.2.0-media-forgetting）
 * - 删除：无。
 * - 新增：有界资源 ID、类别及菜单 ID 请求，失败不改变知识并提供提示。
 * - 修改：玩家身份取网络发送者，所有变更在服务端队列执行。
 * - 用途：客户端不能指定其它玩家或在过期菜单远程删改知识，遗忘不消耗容器及 EMC。
 */
package com.primordial.network;

import com.primordial.service.ResourceKnowledge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SForgetMediaPacket(int containerId, boolean gas, String id) {
    public static void encode(C2SForgetMediaPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.gas);
        buf.writeUtf(msg.id, 128);
    }

    public static C2SForgetMediaPacket decode(FriendlyByteBuf buf) {
        return new C2SForgetMediaPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(128));
    }

    public static void handle(C2SForgetMediaPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || !(player.containerMenu instanceof moze_intel.projecte.gameObjs.container.TransmutationContainer)
                    || player.containerMenu.containerId != msg.containerId) return;
            ResourceLocation id = ResourceLocation.tryParse(msg.id);
            boolean forgotten = ResourceKnowledge.forgetMedia(player.server, player.getUUID(),
                    msg.gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID, id);
            player.displayClientMessage(Component.translatable(forgotten
                    ? "message.primordial.media_forgotten" : "message.primordial.media_forget_failed"), true);
        });
        context.setPacketHandled(true);
    }
}
