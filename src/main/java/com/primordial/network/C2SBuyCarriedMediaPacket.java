/**
 * 【文件职责】请求为 ProjectE 转化桌光标上的介质容器补充同种资源。
 * 【变更记录 #1】2026-10-02 16:50:00（第 1 次备注 · 1.2.0-transmutation-media-output）
 * - 删除：无。
 * - 新增：仅传菜单 ID 的服务端购买请求。
 * - 修改：资源、容器容量、知识和余额全部由服务端读取。
 * - 用途：防止客户端伪造购买种类与数量。
 */
/**
 * 【变更记录 #2】2026-10-02 17:05:00（第 2 次备注 · 1.2.0-transmutation-media-selection）
 * - 删除：无。
 * - 新增：可选的流体/气体种类 ID，限制消息字符串长度。
 * - 修改：空 ID 沿用原有补充路径，非空 ID 仅作为服务端重新校验的选择请求。
 * - 用途：支持空容器选种，同时不信任客户端提交的价格、容量和知识。
 */
/**
 * 【变更记录 #3】2026-10-10 03:53:52（第 3 次备注 · 1.2.0-resource-system-one）
 * - 删除：购买请求没有服务端成交或失败回显的路径。
 * - 新增：匹配转化桌菜单的统一交易入口和结果反馈。
 * - 修改：非法 ID 明确拒绝，空选择自动识别已有介质或 FE 容器；价格和数量仍由服务端读取。
 * - 用途：玩家能够辨别未知资源、容量、余额或背包不足，不因客户端按钮变化误认交易成功。
 */
package com.primordial.network;

import com.primordial.service.TransmutationMediaTrade;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SBuyCarriedMediaPacket(int containerId, boolean gas, String selectedId) {
    public C2SBuyCarriedMediaPacket(int containerId) { this(containerId, false, ""); }
    public static void encode(C2SBuyCarriedMediaPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeBoolean(msg.gas);
        buf.writeUtf(msg.selectedId, 128);
    }
    public static C2SBuyCarriedMediaPacket decode(FriendlyByteBuf buf) {
        return new C2SBuyCarriedMediaPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(128));
    }
    public static void handle(C2SBuyCarriedMediaPacket msg, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            var player = context.get().getSender();
            if (player != null && player.containerMenu.containerId == msg.containerId
                    && player.containerMenu instanceof moze_intel.projecte.gameObjs.container.TransmutationContainer) {
                ResourceLocation id = msg.selectedId.isBlank() ? null : ResourceLocation.tryParse(msg.selectedId);
                var result = !msg.selectedId.isBlank() && id == null
                        ? com.primordial.service.ResourceTradeResult.failure(
                                com.primordial.service.ResourceTradeResult.Status.INVALID_RESOURCE)
                        : TransmutationMediaTrade.trade(player, true, msg.gas, id);
                S2CMediaTradeResultPacket.send(player, true, result);
            }
        });
        context.get().setPacketHandled(true);
    }
}
