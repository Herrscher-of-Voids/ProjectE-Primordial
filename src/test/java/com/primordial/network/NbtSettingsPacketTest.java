/**
 * 【文件职责】验证 NBT 设置协议范围、菜单校验和玩家隔离。
 * 【变更记录 #1】2026-10-02 19:32:53（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：编解码、过期菜单、无发送者、非法位及只修改本人设置的测试。
 * - 修改：实际处理逻辑和存档运行，仅世界存储入口使用替身。
 * - 用途：避免客户端修改其他玩家或离开转化桌后仍写设置。
 */
package com.primordial.network;

import com.primordial.data.TransmutationNbtSettings;
import com.primordial.machine.AdapterTestBootstrap;
import io.netty.buffer.Unpooled;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NbtSettingsPacketTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @Test void payloadRoundTripsAndMalformedRepliesAreRejected() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int flags = -1; flags <= 7; flags++) {
                buffer.clear();
                var request = new C2SNbtSettingsPacket(21, flags);
                C2SNbtSettingsPacket.encode(request, buffer);
                assertEquals(request, C2SNbtSettingsPacket.decode(buffer));
            }
            buffer.clear();
            var reply = new S2CNbtSettingsPacket(21, 5);
            S2CNbtSettingsPacket.encode(reply, buffer);
            assertEquals(reply, S2CNbtSettingsPacket.decode(buffer));
            buffer.clear(); buffer.writeVarInt(21); buffer.writeByte(8);
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> S2CNbtSettingsPacket.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void invalidRequestsNeverAccessSavedData() {
        var player = mock(ServerPlayer.class);
        player.containerMenu = mock(TransmutationContainer.class);
        int id = player.containerMenu.containerId;
        try (var saved = mockStatic(TransmutationNbtSettings.class)) {
            assertNull(C2SNbtSettingsPacket.apply(null, new C2SNbtSettingsPacket(id, 0)));
            assertNull(C2SNbtSettingsPacket.apply(player, new C2SNbtSettingsPacket(id + 1, 0)));
            assertNull(C2SNbtSettingsPacket.apply(player, new C2SNbtSettingsPacket(id, 8)));
            assertNull(C2SNbtSettingsPacket.apply(player, new C2SNbtSettingsPacket(id, -2)));
            player.containerMenu = mock(AbstractContainerMenu.class);
            assertNull(C2SNbtSettingsPacket.apply(player, new C2SNbtSettingsPacket(player.containerMenu.containerId, 0)));
            saved.verifyNoInteractions();
        }
    }
    @Test void queriesAreReadOnlyAndUpdatesOnlyAffectSender() throws Exception {
        var server = mock(MinecraftServer.class);
        var player = mock(ServerPlayer.class);
        var field = ServerPlayer.class.getDeclaredField("server");
        field.setAccessible(true); field.set(player, server);
        var owner = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        player.containerMenu = mock(TransmutationContainer.class);
        int id = player.containerMenu.containerId;
        var data = new TransmutationNbtSettings();
        try (var saved = mockStatic(TransmutationNbtSettings.class)) {
            saved.when(() -> TransmutationNbtSettings.get(server)).thenReturn(data);
            assertEquals(7, C2SNbtSettingsPacket.apply(player, new C2SNbtSettingsPacket(id, -1)).flags());
            assertFalse(data.isDirty());
            assertEquals(3, C2SNbtSettingsPacket.apply(player, new C2SNbtSettingsPacket(id, 3)).flags());
            assertEquals(3, data.flags(owner));
            assertEquals(7, data.flags(UUID.randomUUID()));
        }
    }
}
