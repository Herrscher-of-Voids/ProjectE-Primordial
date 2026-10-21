/**
 * 【变更记录 #1】2026-10-09 01:36:12（第 1 次备注 · 1.2.0-jei-media-knowledge）
 * 删除：无。新增：未学习、允许、撤销、禁用与精确报价回归。修改：无。
 * 用途：真实查询入口不能以非零价格代替知识；回包和缓存不泄漏旧授权。
 */
package com.primordial.network;

import com.primordial.client.EmcCountText;
import com.primordial.client.JeiMediaQuotes;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.service.*;
import com.primordial.value.ValueManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JeiMediaQuotesTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @Test void unknownFluidAndGasNeverShowTheAffordableCount() {
        check(false); check(true);
    }
    private void check(boolean gas) {
        var player = mock(ServerPlayer.class);
        var owner = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        var key = new C2SJeiMediaQuotesPacket.Key(gas, new ResourceLocation(gas ? "mekanism:hydrogen" : "minecraft:water"));
        var kind = gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID;
        var price = EmcAmount.parse("1/1000");
        try (var config = mockStatic(PrimordialServerConfig.class);
             var knowledge = mockStatic(ResourceKnowledge.class);
             var values = mockStatic(ValueManager.class);
             var prices = mockStatic(EmcService.class);
             var gases = mockStatic(MekanismGasCompat.class)) {
            values.when(() -> ValueManager.canBuyFluid(Fluids.WATER)).thenReturn(true);
            values.when(() -> ValueManager.canBuyGas(key.id())).thenReturn(true);
            gases.when(() -> MekanismGasCompat.validGas(key.id())).thenReturn(true);
            prices.when(() -> EmcService.fluidPrice(Fluids.WATER)).thenReturn(price);
            prices.when(() -> EmcService.gasPrice(key.id())).thenReturn(price);
            assertTrue(C2SJeiMediaQuotesPacket.quote(player, key).isZero());
            assertEquals("", EmcCountText.label(true, BigInteger.valueOf(5700), C2SJeiMediaQuotesPacket.quote(player, key)));
            prices.verifyNoInteractions();
            knowledge.when(() -> ResourceKnowledge.allowsMedia(player.server, owner, kind, key.id())).thenReturn(true);
            assertEquals(price, C2SJeiMediaQuotesPacket.quote(player, key));
            assertEquals("5.7K", EmcCountText.label(true, BigInteger.valueOf(5700), C2SJeiMediaQuotesPacket.quote(player, key).multiply(1000)));
            knowledge.when(() -> ResourceKnowledge.allowsMedia(player.server, owner, kind, key.id())).thenReturn(false);
            assertTrue(C2SJeiMediaQuotesPacket.quote(player, key).isZero());
            knowledge.when(() -> ResourceKnowledge.allowsMedia(player.server, owner, kind, key.id())).thenReturn(true);
            config.when(() -> PrimordialServerConfig.mediaPurchaseDisabled(gas)).thenReturn(true);
            assertTrue(C2SJeiMediaQuotesPacket.quote(player, key).isZero());
            config.when(() -> PrimordialServerConfig.mediaPurchaseDisabled(gas)).thenReturn(false);
            values.when(() -> ValueManager.canBuyFluid(Fluids.WATER)).thenReturn(false);
            values.when(() -> ValueManager.canBuyGas(key.id())).thenReturn(false);
            assertTrue(C2SJeiMediaQuotesPacket.quote(player, key).isZero());
        }
    }
    @Test void packetsRoundTripExactPricesAndRejectOversizedQueries() {
        var key = new C2SJeiMediaQuotesPacket.Key(false, new ResourceLocation("minecraft:water"));
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var request = new C2SJeiMediaQuotesPacket(17, List.of(key));
            C2SJeiMediaQuotesPacket.encode(request, buffer);
            assertEquals(request, C2SJeiMediaQuotesPacket.decode(buffer));
            buffer.clear();
            var reply = new S2CJeiMediaQuotesPacket(17, List.of(new S2CJeiMediaQuotesPacket.Entry(key, "1/3000")));
            S2CJeiMediaQuotesPacket.encode(reply, buffer);
            assertEquals(reply, S2CJeiMediaQuotesPacket.decode(buffer));
            buffer.clear(); buffer.writeVarInt(17); buffer.writeVarInt(129);
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> C2SJeiMediaQuotesPacket.decode(buffer));
        } finally { buffer.release(); }
    }
    @Test void noReplyAndStaleReplyStayHidden() {
        JeiMediaQuotes.logout(null);
        var key = new C2SJeiMediaQuotesPacket.Key(false, new ResourceLocation("minecraft:water"));
        assertTrue(JeiMediaQuotes.price(key).isZero());
        JeiMediaQuotes.accept(new S2CJeiMediaQuotesPacket(-1, List.of(new S2CJeiMediaQuotesPacket.Entry(key, "1"))));
        assertTrue(JeiMediaQuotes.price(key).isZero());
        JeiMediaQuotes.logout(null);
        assertTrue(JeiMediaQuotes.price(key).isZero());
    }
}
