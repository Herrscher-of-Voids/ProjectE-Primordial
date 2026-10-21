/**
 * 【文件职责】验证介质遗忘消息的菜单边界、发送者身份及有界网络编解码。
 * 【变更记录 #1】2026-10-02 18:48:00（第 1 次备注 · 1.2.0-media-forgetting）
 * - 删除：无。
 * - 新增：过期菜单/非转化桌/无发送者拒绝，以及有效请求只作用于发送者的回归。
 * - 修改：仅服务及网络上下文使用替身，消息编解码和校验运行真实实现。
 * - 用途：防止客户端远程遗忘或篡改其他玩家知识。
 */
package com.primordial.network;

import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.service.ResourceKnowledge;
import io.netty.buffer.Unpooled;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgetMediaPacketTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void payloadRoundTripAndOversizedIdsRejected() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new C2SForgetMediaPacket(42, true, "mekanism:hydrogen");
            C2SForgetMediaPacket.encode(packet, buf);
            assertEquals(packet, C2SForgetMediaPacket.decode(buf));
            assertThrows(RuntimeException.class, () -> C2SForgetMediaPacket.encode(
                    new C2SForgetMediaPacket(42, false, "a".repeat(129)), buf));
        } finally { buf.release(); }
    }

    @Test void staleNonTransmutationAndMissingSenderCannotForget() {
        var player = mock(ServerPlayer.class);
        player.containerMenu = mock(TransmutationContainer.class);
        try (var knowledge = mockStatic(ResourceKnowledge.class)) {
            var context = context(player);
            C2SForgetMediaPacket.handle(new C2SForgetMediaPacket(player.containerMenu.containerId + 1,
                    false, "minecraft:water"), () -> context);
            player.containerMenu = mock(AbstractContainerMenu.class);
            C2SForgetMediaPacket.handle(new C2SForgetMediaPacket(player.containerMenu.containerId,
                    false, "minecraft:water"), () -> context);
            var missing = context(null);
            C2SForgetMediaPacket.handle(new C2SForgetMediaPacket(0, false, "minecraft:water"), () -> missing);
            knowledge.verifyNoInteractions();
        }
    }

    @Test void validRequestUsesSenderIdentityAndNeverTouchesCarriedItems() throws Exception {
        var player = mock(ServerPlayer.class);
        var server = mock(MinecraftServer.class);
        var owner = UUID.randomUUID();
        var field = ServerPlayer.class.getDeclaredField("server");
        field.setAccessible(true); field.set(player, server);
        when(player.getUUID()).thenReturn(owner);
        var menu = mock(TransmutationContainer.class);
        player.containerMenu = menu;
        var context = context(player);
        var id = new ResourceLocation("mekanism:hydrogen");
        try (var knowledge = mockStatic(ResourceKnowledge.class)) {
            knowledge.when(() -> ResourceKnowledge.forgetMedia(server, owner, ResourceKnowledge.Kind.GAS, id))
                    .thenReturn(true);
            C2SForgetMediaPacket.handle(new C2SForgetMediaPacket(menu.containerId, true, id.toString()), () -> context);
            knowledge.verify(() -> ResourceKnowledge.forgetMedia(server, owner, ResourceKnowledge.Kind.GAS, id));
            verifyNoInteractions(menu);
            verify(context).setPacketHandled(true);
        }
    }

    private static NetworkEvent.Context context(ServerPlayer player) {
        var context = mock(NetworkEvent.Context.class);
        when(context.getSender()).thenReturn(player);
        when(context.enqueueWork(any())).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        return context;
    }
}
