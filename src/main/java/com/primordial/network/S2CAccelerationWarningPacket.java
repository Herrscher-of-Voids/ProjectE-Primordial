/**
 * 【文件职责】把服务器发起的高倍率启动确认展示为原生确认弹窗。
 * 【变更记录 #1】2026-09-16 13:36:00（第 1 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无；替代聊天栏再次右键确认的交互。
 * - 新增：一次性令牌、倍率、位置及服务端报价，弹窗按钮延迟 20 客户端 tick 启用。
 * - 修改：取消只发送拒绝结果；关闭弹窗也不会启动，未响应令牌由服务端超时回收。
 * - 用途：256/512/1024 倍每次启用必须明确确认；客户端屏幕类型隔离于内部接收器。
 */
/**
 * 【变更记录 #2】2026-09-16 14:41:00（第 2 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：弹窗只描述一台机器的限制。
 * - 新增：本次启动目标数，配合最高倍率和合计价格明确展示批量启动费用。
 * - 修改：编码和解码同步追加目标数；旧的一次性令牌与 20 tick 确认延迟保持。
 * - 用途：恢复多个已暂停目标时，所有高倍率任务都包含在同一次明确确认中。
 */
/**
 * 【变更记录 #3】2026-09-18 01:59:51（第 3 次备注 · 1.0.0-acceleration-modes）
 * - 删除：从管理界面发起确认后无条件退出到游戏的行为。
 * - 新增：确认或取消后，在原容器仍有效时返回加速器管理屏幕。
 * - 修改：原一次性令牌和 20 tick 按钮延迟不变，普通物品右键仍返回游戏。
 * - 用途：管理多个高倍率目标时保留列表和选择，不绕过任何服务端确认校验。
 */
package com.primordial.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

public record S2CAccelerationWarningPacket(UUID token, int multiplier, BlockPos pos, int targets, String cost) {
    public static void send(net.minecraft.server.level.ServerPlayer player, UUID token, int multiplier, BlockPos pos, int targets, String cost) {
        PrimordialNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new S2CAccelerationWarningPacket(token, multiplier, pos, targets, cost));
    }

    public static void encode(S2CAccelerationWarningPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.token); buf.writeVarInt(msg.multiplier); buf.writeBlockPos(msg.pos); buf.writeVarInt(msg.targets); buf.writeUtf(msg.cost, 160);
    }

    public static S2CAccelerationWarningPacket decode(FriendlyByteBuf buf) {
        return new S2CAccelerationWarningPacket(buf.readUUID(), buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readUtf(160));
    }

    public static void handle(S2CAccelerationWarningPacket msg, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientHandler.open(msg));
        context.get().setPacketHandled(true);
    }

    private static final class ClientHandler {
        static void open(S2CAccelerationWarningPacket msg) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null) return;
            var previous = minecraft.screen;
            var container = minecraft.player.containerMenu;
            var screen = new net.minecraft.client.gui.screens.ConfirmScreen(accepted -> {
                PrimordialNetwork.CHANNEL.sendToServer(new C2SConfirmAccelerationPacket(msg.token, accepted));
                minecraft.setScreen(previous instanceof com.primordial.screen.AccelerationScreen
                        && minecraft.player != null && minecraft.player.containerMenu == container ? previous : null);
            }, Component.translatable("screen.primordial.acceleration_warning_title"),
                    Component.translatable("screen.primordial.acceleration_warning_body", msg.targets, msg.multiplier,
                            msg.pos.getX(), msg.pos.getY(), msg.pos.getZ(), msg.cost),
                    Component.translatable("screen.primordial.acceleration_confirm"), Component.translatable("gui.cancel"));
            minecraft.setScreen(screen);
            screen.setDelay(20);
        }
    }
}
