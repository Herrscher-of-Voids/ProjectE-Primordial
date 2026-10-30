/**
 * 【文件职责】验证转化桌目录协议边界、延迟回包隔离和玩家遗忘的真实服务端菜单门禁。
 * 【变更记录 #1】2026-10-23 20:54:00（第 1 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 删除：无。新增：真实玩家请求、错误/关闭菜单、无效资源、超大回包和旧代缓存测试。
 * - 修改：复用生产处理入口，不伪造另一套知识或目录实现。
 * - 用途：覆盖网络在知识服务之外的边界；测试源集不进入生产 JAR。
 */
package com.primordial.neoforge.transmutation;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationPayloadGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private NeoForgeTransmutationPayloadGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void protocolRoundTripsAndRejectsUnboundedPagesBeforeAllocation(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            var request = new NeoForgeTransmutationPayloads.RequestCatalog(90, false, " WATER ", 10000, Long.MAX_VALUE);
            NeoForgeTransmutationPayloads.RequestCatalog.STREAM_CODEC.encode(buffer, request);
            check(request.equals(NeoForgeTransmutationPayloads.RequestCatalog.STREAM_CODEC.decode(buffer)), "Catalog request must retain the exact query and request generation");
            var catalog = new NeoForgeTransmutationPayloads.Catalog(90, false, " WATER ", 2, 18, List.of(WATER.toString()), 13);
            NeoForgeTransmutationPayloads.Catalog.STREAM_CODEC.encode(buffer, catalog);
            check(catalog.equals(NeoForgeTransmutationPayloads.Catalog.STREAM_CODEC.decode(buffer)), "Catalog response must retain bounded IDs, total and request identity");
            var forget = new NeoForgeTransmutationPayloads.Forget(90, false, WATER.toString());
            NeoForgeTransmutationPayloads.Forget.STREAM_CODEC.encode(buffer, forget);
            check(forget.equals(NeoForgeTransmutationPayloads.Forget.STREAM_CODEC.decode(buffer)), "Forget request must round trip");
            var result = new NeoForgeTransmutationPayloads.ForgetResult(90, false, WATER.toString(), true);
            NeoForgeTransmutationPayloads.ForgetResult.STREAM_CODEC.encode(buffer, result);
            check(result.equals(NeoForgeTransmutationPayloads.ForgetResult.STREAM_CODEC.decode(buffer)), "Forget result must round trip");
            buffer.clear();
            buffer.writeVarInt(90); buffer.writeBoolean(false); buffer.writeUtf(""); buffer.writeVarInt(0); buffer.writeVarLong(1);
            buffer.writeVarInt(Integer.MAX_VALUE);
            expectDecoder(() -> NeoForgeTransmutationPayloads.Catalog.STREAM_CODEC.decode(buffer), "An oversized catalog count must reject before allocating its advertised list");
            buffer.clear();
            buffer.writeVarInt(90); buffer.writeBoolean(false); buffer.writeUtf(""); buffer.writeVarInt(-1); buffer.writeVarLong(1);
            expectDecoder(() -> NeoForgeTransmutationPayloads.RequestCatalog.STREAM_CODEC.decode(buffer), "Negative pages must be rejected by the network codec");
            buffer.clear();
            buffer.writeVarInt(90); buffer.writeBoolean(false); buffer.writeUtf("bad id");
            expectDecoder(() -> NeoForgeTransmutationPayloads.Forget.STREAM_CODEC.decode(buffer), "Malformed IDs must be rejected by the network codec");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void catalogCacheRejectsOldSearchesPagesMenusAndGenerations(GameTestHelper helper) {
        var state = new NeoForgeTransmutationMediaState();
        var request = new NeoForgeTransmutationPayloads.RequestCatalog(91, false, "water", 0, 7);
        state.begin(request);
        for (var stale : List.of(
                new NeoForgeTransmutationPayloads.Catalog(90, false, "water", 0, 7, List.of(), 0),
                new NeoForgeTransmutationPayloads.Catalog(91, true, "water", 0, 7, List.of(), 0),
                new NeoForgeTransmutationPayloads.Catalog(91, false, "lava", 0, 7, List.of(), 0),
                new NeoForgeTransmutationPayloads.Catalog(91, false, "water", 1, 7, List.of(), 0),
                new NeoForgeTransmutationPayloads.Catalog(91, false, "water", 0, 6, List.of(), 0)))
            check(!state.accept(stale) && state.catalog() == null, "Only the current menu, category, query, page and generation may populate the cache");
        var current = new NeoForgeTransmutationPayloads.Catalog(91, false, "water", 0, 7, List.of(WATER.toString()), 1);
        check(state.accept(current) && state.catalog().equals(current), "Current response must be accepted");
        state.begin(new NeoForgeTransmutationPayloads.RequestCatalog(91, false, "water", 0, 8));
        check(state.catalog() == null && !state.accept(current), "Requesting the same search again must clear the view and reject the older generation");
        state.clear();
        check(state.request() == null && !state.accept(current), "Closing the screen must reject late responses");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void serverMenuAndSenderGateCatalogAndForgetWithoutTouchingOtherOwners(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        UUID otherOwner = UUID.randomUUID();
        try {
            check(NeoForgeResourceKnowledge.learnMedia(server, player.getUUID(), Kind.FLUID, WATER)
                    && NeoForgeResourceKnowledge.learnMedia(server, otherOwner, Kind.FLUID, WATER), "Independent owners must learn real water knowledge");
            var request = new NeoForgeTransmutationPayloads.RequestCatalog(92, false, "water", 0, 1);
            check(NeoForgeTransmutationPayloads.catalogFor(player, request) == null
                    && NeoForgeTransmutationPayloads.forgetFor(player, new NeoForgeTransmutationPayloads.Forget(92, false, WATER.toString())) == null,
                    "The inventory menu must not query or mutate transmutation knowledge");
            var menu = new TransmutationContainer(92, player.getInventory());
            player.containerMenu = menu;
            check(NeoForgeTransmutationPayloads.catalogFor(player, request) != null, "The sender's actual transmutation menu must produce a response");
            check(NeoForgeTransmutationPayloads.catalogFor(player, new NeoForgeTransmutationPayloads.RequestCatalog(93, false, "", 0, 2)) == null
                    && NeoForgeTransmutationPayloads.catalogFor(player, new NeoForgeTransmutationPayloads.RequestCatalog(92, false, "x".repeat(65), 0, 2)) == null
                    && NeoForgeTransmutationPayloads.catalogFor(player, new NeoForgeTransmutationPayloads.RequestCatalog(92, false, "", -1, 2)) == null,
                    "Wrong menu and invalid direct service requests must not reach the catalog");
            check(NeoForgeTransmutationPayloads.forgetFor(player, new NeoForgeTransmutationPayloads.Forget(93, false, WATER.toString())) == null
                    && NeoForgeTransmutationPayloads.forgetFor(player, new NeoForgeTransmutationPayloads.Forget(92, false, "bad id")) == null
                    && NeoForgeResourceKnowledge.allowsMedia(server, player.getUUID(), Kind.FLUID, WATER),
                    "Stale menu and invalid ID must leave the sender's knowledge intact");
            var forgotten = NeoForgeTransmutationPayloads.forgetFor(player, new NeoForgeTransmutationPayloads.Forget(92, false, WATER.toString()));
            check(forgotten != null && forgotten.forgotten() && !NeoForgeResourceKnowledge.allowsMedia(server, player.getUUID(), Kind.FLUID, WATER)
                    && NeoForgeResourceKnowledge.allowsMedia(server, otherOwner, Kind.FLUID, WATER), "Forgetting may mutate only the authenticated sender");
            check(!NeoForgeTransmutationPayloads.forgetFor(player, new NeoForgeTransmutationPayloads.Forget(92, false, WATER.toString())).forgotten(),
                    "Repeated forget must report failure rather than pretend to forget already absent knowledge");
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
            check(NeoForgeTransmutationPayloads.catalogFor(player, request) == null, "A closed menu must reject delayed requests");
        } finally { logout(server, player); }
        helper.succeed();
    }

    private static void expectDecoder(Runnable action, String message) {
        try { action.run(); } catch (DecoderException expected) { return; }
        throw new GameTestAssertException(message);
    }
    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "media-" + UUID.randomUUID().toString().substring(0, 6));
        var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            try { logout(server, player); } finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }
    private static void logout(MinecraftServer server, ServerPlayer player) {
        try {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel) channel.finishAndReleaseAll();
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
