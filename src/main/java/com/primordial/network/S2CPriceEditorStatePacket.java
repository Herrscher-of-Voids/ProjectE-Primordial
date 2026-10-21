/**
 * 【文件职责】把服务器确认的价格、编辑权限和提交结果返回当前价格菜单。
 * 【变更记录 #1】2026-09-16 01:24:55（第 1 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：无；补齐原价格请求只有聊天消息、页面无法确认保存结果的反馈链。
 * - 新增：有界的菜单 ID、介质类型、注册名、精确价格和结果状态；客户端丢弃旧菜单或旧样本的回包。
 * - 修改：界面只把服务端确认的价格视为已生效；保存失败不展示用户输入的新价格。
 * - 用途：供 PriceEditorMenu 选择同步和两个价格提交处理器共用，避免客户端本地价表与服务器结算不一致。
 */
/** 【变更记录 #2】2026-10-08 03:45:00（1.2.0-media-price-modes）
 * 删除：无。新增：服务端回传介质价格模式。修改：编码协议在价格后增加有界 mode 字段，旧构造器保持测试与调用兼容。用途：价格编辑器明确显示当前交易方向状态。 */
package com.primordial.network;

import com.primordial.menu.PriceEditorMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record S2CPriceEditorStatePacket(int containerId, byte kind, String id, String price,
                                      String mode, boolean editable, Status status) {
    public static final int MAX_PRICE_TEXT = 4354;

    public enum Status {
        CURRENT, SAVED, PERMISSION_DENIED, INVALID_TARGET, INVALID_PRICE, SAVE_FAILED
    }

    public S2CPriceEditorStatePacket(int containerId, byte kind, String id, String price,
                                     boolean editable, Status status) {
        this(containerId, kind, id, price, "", editable, status);
    }

    public static void encode(S2CPriceEditorStatePacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.containerId);
        buffer.writeByte(message.kind);
        buffer.writeUtf(message.id, 256);
        buffer.writeUtf(message.price, MAX_PRICE_TEXT);
        buffer.writeUtf(message.mode == null ? "" : message.mode, 32);
        buffer.writeBoolean(message.editable);
        buffer.writeEnum(message.status);
    }

    public static S2CPriceEditorStatePacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        byte kind = buffer.readByte();
        if (kind < PriceEditorMenu.KIND_ITEM || kind > PriceEditorMenu.KIND_GAS)
            throw new io.netty.handler.codec.DecoderException("Invalid price editor kind");
        return new S2CPriceEditorStatePacket(container, kind, buffer.readUtf(256),
                buffer.readUtf(MAX_PRICE_TEXT), buffer.readUtf(32), buffer.readBoolean(), buffer.readEnum(Status.class));
    }

    public static void handle(S2CPriceEditorStatePacket message, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientHandler.accept(message));
        context.get().setPacketHandled(true);
    }

    /** 只在客户端分发时解析 Minecraft 类，专用服务器注册网络包时不会加载客户端屏幕。 */
    private static final class ClientHandler {
        private static void accept(S2CPriceEditorStatePacket message) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof PriceEditorMenu menu)
                menu.acceptPriceState(message);
        }
    }
}
