/**
 * 【文件职责】注册原生转化桌的有界介质目录与玩家遗忘请求；身份和知识只在服务端裁决。
 * 【变更记录 #1】2026-10-23 20:54:00（第 1 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 删除：无。新增：目录请求/回包、遗忘请求/回执及菜单、页码、长度和请求代数边界。
 * - 修改：不接收客户端知识、价格或其它玩家身份；客户端处理放在隔离内部类。
 * - 用途：旧菜单或旧搜索回包不能覆盖当前目录，专服无需解析 Minecraft 客户端类型。
 */
package com.primordial.neoforge.transmutation;

import java.util.ArrayList;
import java.util.List;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import io.netty.handler.codec.DecoderException;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeTransmutationPayloads {
    public static final int MAX_ID = NeoForgeTransmutationMediaCatalog.MAX_ID;
    private NeoForgeTransmutationPayloads() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(PageState.TYPE, PageState.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) setPageFor(player, payload);
        }));
        registrar.playToServer(RequestCatalog.TYPE, RequestCatalog.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Catalog result = catalogFor(player, payload);
                if (result != null) PacketDistributor.sendToPlayer(player, result);
            }
        }));
        registrar.playToServer(Forget.TYPE, Forget.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ForgetResult result = forgetFor(player, payload);
                if (result != null) {
                    PacketDistributor.sendToPlayer(player, result);
                    player.displayClientMessage(Component.translatable(result.forgotten()
                            ? "message.primordial.media_forgotten" : "message.primordial.media_forget_failed"), true);
                }
            }
        }));
        registrar.playToClient(Catalog.TYPE, Catalog.STREAM_CODEC, NeoForgeTransmutationPayloads::handleCatalog);
        registrar.playToClient(ForgetResult.TYPE, ForgetResult.STREAM_CODEC, NeoForgeTransmutationPayloads::handleForgetResult);
    }

    public record PageState(int menuId, boolean active, boolean chemical) implements CustomPacketPayload {
        public static final Type<PageState> TYPE = new Type<>(id("transmutation_media_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PageState> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeVarInt(value.menuId); buffer.writeBoolean(value.active); buffer.writeBoolean(value.chemical); },
                buffer -> new PageState(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static boolean setPageFor(ServerPlayer player, PageState request) {
        if (request == null || !NeoForgeTransmutationMediaTrade.currentMenu(player, request.menuId())) return false;
        ((NeoForgeMediaChargingMenu) player.containerMenu).primordial$setMediaPage(request.active(), request.chemical());
        player.containerMenu.broadcastChanges();
        return true;
    }

    public record RequestCatalog(int menuId, boolean chemical, String query, int page, long sequence) implements CustomPacketPayload {
        public static final Type<RequestCatalog> TYPE = new Type<>(id("transmutation_media_catalog_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestCatalog> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.menuId); buffer.writeBoolean(payload.chemical);
                    buffer.writeUtf(payload.query, NeoForgeTransmutationMediaCatalog.MAX_QUERY); buffer.writeVarInt(payload.page);
                    buffer.writeVarLong(payload.sequence); },
                buffer -> new RequestCatalog(buffer.readVarInt(), buffer.readBoolean(),
                        buffer.readUtf(NeoForgeTransmutationMediaCatalog.MAX_QUERY), readPage(buffer), readSequence(buffer)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Catalog(int menuId, boolean chemical, String query, int page, long sequence,
                          List<String> ids, int total) implements CustomPacketPayload {
        public Catalog {
            ids = List.copyOf(ids);
            if (ids.size() > NeoForgeTransmutationMediaCatalog.PAGE_SIZE || total < ids.size())
                throw new IllegalArgumentException("Invalid media catalog size");
        }
        public static final Type<Catalog> TYPE = new Type<>(id("transmutation_media_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.menuId); buffer.writeBoolean(payload.chemical);
                    buffer.writeUtf(payload.query, NeoForgeTransmutationMediaCatalog.MAX_QUERY); buffer.writeVarInt(payload.page);
                    buffer.writeVarLong(payload.sequence); buffer.writeVarInt(payload.ids.size());
                    for (String entry : payload.ids) buffer.writeUtf(entry, MAX_ID);
                    buffer.writeVarInt(payload.total); },
                buffer -> {
                    int menuId = buffer.readVarInt();
                    boolean chemical = buffer.readBoolean();
                    String query = buffer.readUtf(NeoForgeTransmutationMediaCatalog.MAX_QUERY);
                    int page = readPage(buffer);
                    long sequence = readSequence(buffer);
                    int count = buffer.readVarInt();
                    if (count < 0 || count > NeoForgeTransmutationMediaCatalog.PAGE_SIZE)
                        throw new DecoderException("Invalid media catalog page size");
                    List<String> ids = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) ids.add(readId(buffer));
                    int total = buffer.readVarInt();
                    if (total < count) throw new DecoderException("Invalid media catalog total");
                    return new Catalog(menuId, chemical, query, page, sequence, ids, total);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Forget(int menuId, boolean chemical, String id) implements CustomPacketPayload {
        public static final Type<Forget> TYPE = new Type<>(NeoForgeTransmutationPayloads.id("transmutation_media_forget"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Forget> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.menuId); buffer.writeBoolean(payload.chemical); buffer.writeUtf(payload.id, MAX_ID); },
                buffer -> new Forget(buffer.readVarInt(), buffer.readBoolean(), readId(buffer)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ForgetResult(int menuId, boolean chemical, String id, boolean forgotten) implements CustomPacketPayload {
        public static final Type<ForgetResult> TYPE = new Type<>(NeoForgeTransmutationPayloads.id("transmutation_media_forget_result"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ForgetResult> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> { buffer.writeVarInt(payload.menuId); buffer.writeBoolean(payload.chemical);
                    buffer.writeUtf(payload.id, MAX_ID); buffer.writeBoolean(payload.forgotten); },
                buffer -> new ForgetResult(buffer.readVarInt(), buffer.readBoolean(), readId(buffer), buffer.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Shared by the actual handler and GameTests; a stale or unrelated menu never reaches the catalog. */
    public static Catalog catalogFor(ServerPlayer player, RequestCatalog request) {
        if (!currentMenu(player, request.menuId()) || request.query() == null
                || request.query().length() > NeoForgeTransmutationMediaCatalog.MAX_QUERY
                || request.page() < 0 || request.page() > NeoForgeTransmutationMediaCatalog.MAX_PAGE || request.sequence() < 0) return null;
        var result = NeoForgeTransmutationMediaCatalog.search(player, request.chemical(), request.query(), request.page());
        return new Catalog(request.menuId(), request.chemical(), request.query(), request.page(), request.sequence(), result.ids(), result.total());
    }

    /** Client selection is only an ID hint; authority remains the sender's own current server knowledge. */
    public static ForgetResult forgetFor(ServerPlayer player, Forget request) {
        if (!currentMenu(player, request.menuId()) || request.id() == null || request.id().length() > MAX_ID) return null;
        ResourceLocation id = ResourceLocation.tryParse(request.id());
        if (id == null) return null;
        boolean forgotten = NeoForgeResourceKnowledge.forgetMedia(player.server, player.getUUID(),
                request.chemical() ? NeoForgeResourceKnowledge.Kind.GAS : NeoForgeResourceKnowledge.Kind.FLUID, id);
        return new ForgetResult(request.menuId(), request.chemical(), request.id(), forgotten);
    }

    private static boolean currentMenu(ServerPlayer player, int menuId) {
        return player != null && player.server.isSameThread() && player.containerMenu instanceof TransmutationContainer
                && player.containerMenu.containerId == menuId && player.containerMenu.stillValid(player);
    }

    private static int readPage(RegistryFriendlyByteBuf buffer) {
        int page = buffer.readVarInt();
        if (page < 0 || page > NeoForgeTransmutationMediaCatalog.MAX_PAGE) throw new DecoderException("Invalid media catalog page");
        return page;
    }
    private static long readSequence(RegistryFriendlyByteBuf buffer) {
        long sequence = buffer.readVarLong();
        if (sequence < 0) throw new DecoderException("Invalid media catalog sequence");
        return sequence;
    }
    private static String readId(RegistryFriendlyByteBuf buffer) {
        String id = buffer.readUtf(MAX_ID);
        if (ResourceLocation.tryParse(id) == null) throw new DecoderException("Invalid media resource ID");
        return id;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, path); }
    private static void handleCatalog(Catalog payload, IPayloadContext context) { context.enqueueWork(() -> ClientHandler.catalog(payload)); }
    private static void handleForgetResult(ForgetResult payload, IPayloadContext context) { context.enqueueWork(() -> ClientHandler.forgotten(payload)); }
    private static final class ClientHandler {
        private static void catalog(Catalog payload) { com.primordial.neoforge.client.NeoForgeTransmutationMediaClient.acceptCatalog(payload); }
        private static void forgotten(ForgetResult payload) { com.primordial.neoforge.client.NeoForgeTransmutationMediaClient.acceptForgetResult(payload); }
    }
}
