/**
 * 【文件职责】定价器目标/保存 C2S 与权威状态 S2C；公共协议不携带第三方对象或物品组件。
 * 【变更记录 #1】2026-10-23 16:56:25（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：有界字符串、菜单及样本代校验、每包服务端权限复核和实际成功/失败反馈。
 * - 修改：保存同时要求当前价格修订；目标切换和外部改价后旧请求不会覆盖新状态。
 * - 用途：包处理回到主线程；客户端状态分发隔离在内部类，专服注册不解析界面类型。
 * 【变更记录 #2】2026-10-23 17:11:28（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：失权时只发送即将关闭界面的短暂拒绝状态，导致反馈难以看到的路径。
 * - 新增：关闭失权菜单前发送服务端聊天拒绝消息。
 * - 修改：容器、样本与保存权限校验保持不变，拒绝请求仍不写入价格。
 * - 用途：管理员被撤销权限后仍能看到明确失败原因，不把关闭误认为保存成功。
 */
package com.primordial.neoforge.priceeditor;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.value.NeoForgePriceEditing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgePriceEditorPayloads {
    public static final int MAX_ID = 256, MAX_PRICE_TEXT = 4354;
    public enum Status { CURRENT, SAVED, PERMISSION_DENIED, INVALID_TARGET, INVALID_PRICE, SAVE_FAILED, UNAVAILABLE, STALE }
    private NeoForgePriceEditorPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Target.TYPE, Target.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handleTarget(player, payload);
        }));
        registrar.playToServer(Edit.TYPE, Edit.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) handleEdit(player, payload);
        }));
        registrar.playToClient(State.TYPE, State.STREAM_CODEC, NeoForgePriceEditorPayloads::handleState);
    }

    public record Target(int containerId, byte kind, String id, long version) implements CustomPacketPayload {
        public static final Type<Target> TYPE = new Type<>(NeoForgePriceEditorPayloads.id("price_editor_target"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.containerId); buffer.writeByte(payload.kind);
                    buffer.writeUtf(payload.id, MAX_ID); buffer.writeVarLong(payload.version); },
                buffer -> new Target(buffer.readVarInt(), buffer.readByte(), buffer.readUtf(MAX_ID), buffer.readVarLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Edit(int containerId, byte kind, String id, long version, long priceRevision, String value) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(NeoForgePriceEditorPayloads.id("price_editor_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.containerId); buffer.writeByte(payload.kind);
                    buffer.writeUtf(payload.id, MAX_ID); buffer.writeVarLong(payload.version);
                    buffer.writeVarLong(payload.priceRevision); buffer.writeUtf(payload.value, MAX_PRICE_TEXT); },
                buffer -> new Edit(buffer.readVarInt(), buffer.readByte(), buffer.readUtf(MAX_ID),
                        buffer.readVarLong(), buffer.readVarLong(), buffer.readUtf(MAX_PRICE_TEXT)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record State(int containerId, byte kind, String id, long version, String price, String mode,
                        boolean editable, boolean available, long priceRevision, Status status, String messageKey) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(NeoForgePriceEditorPayloads.id("price_editor_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.containerId); buffer.writeByte(payload.kind);
                    buffer.writeUtf(payload.id, MAX_ID); buffer.writeVarLong(payload.version); buffer.writeUtf(payload.price, MAX_PRICE_TEXT);
                    buffer.writeUtf(payload.mode, 32); buffer.writeBoolean(payload.editable); buffer.writeBoolean(payload.available);
                    buffer.writeVarLong(payload.priceRevision); buffer.writeEnum(payload.status); buffer.writeUtf(payload.messageKey, 128); },
                buffer -> new State(buffer.readVarInt(), buffer.readByte(), buffer.readUtf(MAX_ID), buffer.readVarLong(),
                        buffer.readUtf(MAX_PRICE_TEXT), buffer.readUtf(32), buffer.readBoolean(), buffer.readBoolean(),
                        buffer.readVarLong(), buffer.readEnum(Status.class), buffer.readUtf(128)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 独立公开入口便于真实 GameTest 复用同一服务端校验，不复制一套测试实现。 */
    public static void handleTarget(ServerPlayer player, Target target) {
        if (!player.server.isSameThread() || !(player.containerMenu instanceof NeoForgePriceEditorMenu menu)
                || menu.containerId != target.containerId()) return;
        if (!authorize(player, menu)) return;
        ResourceLocation id = parseId(target.id());
        if (target.id() == null || target.id().length() > MAX_ID) {
            menu.sendPriceState(player, Status.INVALID_TARGET, "screen.primordial.price_invalid_target");
            return;
        }
        if (!menu.acceptTarget(target.kind(), id, target.version())) {
            // 返回请求自己的身份和序号，只能终止对应本地预览，不伪造成功或修改真实样本。
            PacketDistributor.sendToPlayer(player, new State(menu.containerId, target.kind(),
                    target.id() == null ? "" : target.id(), target.version(), "", "", true, false,
                    NeoForgePriceEditing.revision(), Status.INVALID_TARGET, "screen.primordial.price_invalid_target"));
            return;
        }
        menu.sendPriceState(player, Status.CURRENT, "");
    }

    public static void handleEdit(ServerPlayer player, Edit edit) {
        if (!player.server.isSameThread() || !(player.containerMenu instanceof NeoForgePriceEditorMenu menu)
                || menu.containerId != edit.containerId()) return;
        if (!authorize(player, menu)) return;
        ResourceLocation id = parseId(edit.id());
        if (!menu.matchesSelection(edit.kind(), id, edit.version()) || !NeoForgePriceEditorMenu.validTarget(edit.kind(), id)) {
            menu.sendPriceState(player, Status.INVALID_TARGET, "screen.primordial.price_invalid_target");
            return;
        }
        if (edit.priceRevision() != NeoForgePriceEditing.revision()) {
            menu.sendPriceState(player, Status.STALE, "screen.primordial.price_changed");
            return;
        }
        if (edit.value() == null || edit.value().isBlank() || edit.value().length() > MAX_PRICE_TEXT) {
            menu.sendPriceState(player, Status.INVALID_PRICE, "primordial.price_editor.result.invalid");
            return;
        }
        var result = NeoForgePriceEditing.mutate(player.server, player, edit.kind(), id, edit.value());
        Status status = result.success() ? Status.SAVED : switch (result.messageKey()) {
            case "primordial.price_editor.result.denied" -> Status.PERMISSION_DENIED;
            case "primordial.price_editor.result.invalid" -> Status.INVALID_PRICE;
            case "primordial.price_editor.result.unavailable" -> Status.UNAVAILABLE;
            default -> Status.SAVE_FAILED;
        };
        menu.sendPriceState(player, status, result.messageKey());
        player.sendSystemMessage(Component.translatable(result.messageKey()));
    }

    private static boolean authorize(ServerPlayer player, NeoForgePriceEditorMenu menu) {
        if (NeoForgePriceEditorAccess.canEdit(player) && menu.stillValid(player)) return true;
        menu.sendPriceState(player, Status.PERMISSION_DENIED, "primordial.price_editor.result.denied");
        player.sendSystemMessage(Component.translatable("primordial.price_editor.result.denied"));
        player.closeContainer();
        return false;
    }

    private static ResourceLocation parseId(String text) {
        return text == null || text.isEmpty() || text.length() > MAX_ID ? null : ResourceLocation.tryParse(text);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }

    private static void handleState(State state, IPayloadContext context) { context.enqueueWork(() -> ClientHandler.accept(state)); }
    private static final class ClientHandler {
        private static void accept(State state) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoForgePriceEditorMenu menu) menu.acceptPriceState(state);
        }
    }
}
