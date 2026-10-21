/**
 * 【文件职责】同步加速器菜单的只读目标摘要与服务器报价。
 * 【变更记录 #1】2026-09-18 01:47:24（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：容器编号、修订号、所选绑定、受限 NBT 摘要、报价与操作结果。
 * - 修改：只允许服务端发送；解码限制 1 MiB、256 个目标及 160 位报价，客户端类隔离。
 * - 用途：状态更新不会依赖玩家库存可以交互，旧窗口回包不能改写当前界面。
 */
package com.primordial.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record S2CAccelerationStatePacket(int containerId, int revision, UUID selected, CompoundTag state,
                                        String quote, boolean accepted) {
    public static final int MAX_BYTES = 1048576;
    public static void encode(S2CAccelerationStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeVarInt(packet.revision); buffer.writeUUID(packet.selected);
        buffer.writeNbt(packet.state); buffer.writeUtf(packet.quote, 160); buffer.writeBoolean(packet.accepted);
    }
    public static S2CAccelerationStatePacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt(), revision = buffer.readVarInt();
        UUID selected = buffer.readUUID();
        CompoundTag state = buffer.readNbt(new NbtAccounter(MAX_BYTES));
        String quote = buffer.readUtf(160);
        if (state == null || state.sizeInBytes() > MAX_BYTES || state.getList("Targets", Tag.TAG_COMPOUND).size() > 256
                || !quote.matches("[0-9]{1,160}")) throw new io.netty.handler.codec.DecoderException("Invalid acceleration state");
        return new S2CAccelerationStatePacket(container, revision, selected, state, quote, buffer.readBoolean());
    }
    public static void handle(S2CAccelerationStatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientHandler.accept(packet));
        context.get().setPacketHandled(true);
    }
    private static final class ClientHandler {
        static void accept(S2CAccelerationStatePacket packet) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof com.primordial.menu.AccelerationMenu menu) menu.accept(packet);
        }
    }
}
