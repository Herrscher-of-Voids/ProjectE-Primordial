/**
 * 【文件职责】将高倍率警告的确认/取消结果提交服务端，不允许客户端指定目标、价格或倍率。
 * 【变更记录 #1】2026-09-16 13:36:00（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无；新增严格限定方向的回应包。
 * - 新增：固定长度 UUID 令牌和布尔结果，服务端线程交给 EmcAccelerationService.confirm 验证。
 * - 修改：无待确认请求、旧令牌、换手/换物品或目标失效时不启动。
 * - 用途：弹窗只表达玩家决定，EMC 与世界操作仍由服务器控制。
 */
package com.primordial.network;

import com.primordial.service.EmcAccelerationService;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record C2SConfirmAccelerationPacket(UUID token, boolean accepted) {
    public static void encode(C2SConfirmAccelerationPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.token); buf.writeBoolean(msg.accepted);
    }

    public static C2SConfirmAccelerationPacket decode(FriendlyByteBuf buf) {
        return new C2SConfirmAccelerationPacket(buf.readUUID(), buf.readBoolean());
    }

    public static void handle(C2SConfirmAccelerationPacket msg, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player != null) EmcAccelerationService.confirm(player, msg.token, msg.accepted);
        });
        context.get().setPacketHandled(true);
    }
}
