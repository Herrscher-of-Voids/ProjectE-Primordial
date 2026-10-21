/**
 * 【文件职责】请求把 ProjectE 转化桌光标上的介质容器卖入 EMC。
 * 【变更记录 #1】2026-10-02 16:24:00（第 1 次备注 · 1.2.0-transmutation-media-input）
 * - 删除：无。
 * - 新增：仅传菜单 ID 的有向消息。
 * - 修改：所有容器、介质、价格与数量由服务端当前菜单重新读取。
 * - 用途：防止客户端伪造资源或借其它菜单提交交易。
 */
/**
 * 【变更记录 #2】2026-10-10 03:53:52（第 2 次备注 · 1.2.0-resource-system-one）
 * - 删除：卖入请求没有任何结果回显的路径。
 * - 新增：菜单身份校验及真实成交数量、精确金额和失败原因回包。
 * - 修改：流体/气体/FE 自动识别统一走副本容器事务，不接受客户端抽取量。
 * - 用途：卖入学习和真实信用入账可由玩家整体验收，失败不会假报成功。
 */
package com.primordial.network;

import com.primordial.service.TransmutationMediaTrade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SSellCarriedMediaPacket(int containerId) {
    public static void encode(C2SSellCarriedMediaPacket msg, FriendlyByteBuf buf) { buf.writeVarInt(msg.containerId); }
    public static C2SSellCarriedMediaPacket decode(FriendlyByteBuf buf) {
        return new C2SSellCarriedMediaPacket(buf.readVarInt());
    }
    public static void handle(C2SSellCarriedMediaPacket msg, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player != null && player.containerMenu.containerId == msg.containerId
                    && player.containerMenu instanceof moze_intel.projecte.gameObjs.container.TransmutationContainer) {
                var result = TransmutationMediaTrade.trade(player, false, false, null);
                S2CMediaTradeResultPacket.send(player, false, result);
            }
        });
        context.get().setPacketHandled(true);
    }
}
