/**
 * 【文件职责】同步当前护符筛选状态。
 * 【变更记录 #1】2026-09-16 19:41:50（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：菜单ID、修订、模式、规则、样本及候选路径，限制32KiB规则与128候选。
 * - 修改：接收时验证数量和NBT大小，客户端只更新当前匹配菜单。
 * - 用途：避免样本与规则回显不同步，普通物品共享标签被裁剪也能显示服务端候选。
 */
package com.primordial.network;

import com.primordial.item.CharmFilterEditor;
import com.primordial.menu.BlackholeCharmMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.List;
import java.util.function.Supplier;

/** 筛选状态只由服务端发送，菜单身份防止迟到数据覆盖另一把护符。 */
public record S2CCharmFilterPacket(int containerId, int revision, int type, boolean blacklist, CompoundTag rules,
                                  net.minecraft.world.item.ItemStack sample, List<String> candidates, List<String> selected, boolean accepted) {
    public static void encode(S2CCharmFilterPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeVarInt(msg.revision); buf.writeByte(msg.type); buf.writeBoolean(msg.blacklist);
        buf.writeNbt(msg.rules); buf.writeItem(msg.sample);
        buf.writeVarInt(msg.candidates.size());
        for (String path : msg.candidates) buf.writeUtf(path, 256);
        buf.writeVarInt(msg.selected.size());
        for (String path : msg.selected) buf.writeUtf(path, 256);
        buf.writeBoolean(msg.accepted);
    }
    public static S2CCharmFilterPacket decode(FriendlyByteBuf buf) {
        int menu = buf.readVarInt(); int revision = buf.readVarInt(); int type = buf.readUnsignedByte(); boolean black = buf.readBoolean();
        CompoundTag rules = buf.readNbt(); var sample = buf.readItem();
        int candidatesCount = buf.readVarInt();
        if (candidatesCount < 0 || candidatesCount > 128 || sample.hasTag() && sample.getTag().sizeInBytes() > 8192)
            throw new io.netty.handler.codec.DecoderException("Invalid charm sample");
        var candidates = new java.util.ArrayList<String>(candidatesCount);
        for (int i = 0; i < candidatesCount; i++) candidates.add(buf.readUtf(256));
        int count = buf.readVarInt();
        if (type > 2 || count < 0 || count > 128 || rules == null || rules.sizeInBytes() > CharmFilterEditor.MAX_BYTES)
            throw new io.netty.handler.codec.DecoderException("Invalid charm filter state");
        var selected = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) selected.add(buf.readUtf(256));
        return new S2CCharmFilterPacket(menu, revision, type, black, rules, sample, List.copyOf(candidates), List.copyOf(selected), buf.readBoolean());
    }
    public static void handle(S2CCharmFilterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientHandler.accept(msg)); ctx.get().setPacketHandled(true);
    }
    private static final class ClientHandler {
        static void accept(S2CCharmFilterPacket msg) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof BlackholeCharmMenu menu) menu.accept(msg);
        }
    }
}
