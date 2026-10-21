/**
 * 【文件职责】缓存当前可见 JEI 介质的服务端报价。
 * 【变更记录 #1】2026-10-09 01:36:12（第 1 次备注 · 1.2.0-jei-media-knowledge）
 * - 删除：无。新增：每秒批量查询、过期隐藏和登出清理。
 * - 修改：仅接收匹配请求的价格，缓存最多 128 项。用途：未学习、等待回包或过期时均不显示数量。
 */
package com.primordial.client;

import com.primordial.network.C2SJeiMediaQuotesPacket;
import com.primordial.network.C2SJeiMediaQuotesPacket.Key;
import com.primordial.network.S2CJeiMediaQuotesPacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.service.EmcAmount;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid = "primordial", value = Dist.CLIENT)
public final class JeiMediaQuotes {
    private static final Set<Key> visible = new LinkedHashSet<>();
    private static Map<Key, EmcAmount> prices = Map.of();
    private static Set<Key> requested = Set.of();
    private static int request;
    private static long lastRequest, received;
    private JeiMediaQuotes() { }
    public static void begin() { visible.clear(); }
    public static EmcAmount price(Key key) {
        if (visible.size() < C2SJeiMediaQuotesPacket.LIMIT) visible.add(key);
        return System.nanoTime() - received < 1_500_000_000L ? prices.getOrDefault(key, EmcAmount.ZERO) : EmcAmount.ZERO;
    }
    public static void flush() {
        long now = System.nanoTime();
        if (visible.isEmpty() || now - lastRequest < 1_000_000_000L) return;
        lastRequest = now; requested = Set.copyOf(visible);
        PrimordialNetwork.CHANNEL.sendToServer(new C2SJeiMediaQuotesPacket(++request, List.copyOf(visible)));
    }
    public static void accept(S2CJeiMediaQuotesPacket msg) {
        if (msg.request() != request) return;
        Map<Key, EmcAmount> next = new HashMap<>();
        for (var entry : msg.entries()) if (requested.contains(entry.key())) {
            try { next.put(entry.key(), EmcAmount.parse(entry.price())); }
            catch (IllegalArgumentException ignored) { /* 损坏报价不参与显示。 */ }
        }
        prices = Map.copyOf(next); received = System.nanoTime();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ++request; visible.clear(); requested = Set.of(); prices = Map.of(); lastRequest = received = 0;
    }
}
