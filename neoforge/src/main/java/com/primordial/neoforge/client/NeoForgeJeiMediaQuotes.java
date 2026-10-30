/** Expiring, generation-matched quotes for visible JEI media; no client price or knowledge guesses. */
package com.primordial.neoforge.client;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.compat.jei.NeoForgeJeiPayloads;
import com.primordial.neoforge.compat.jei.NeoForgeJeiPayloads.Key;
import com.primordial.service.EmcAmount;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeJeiMediaQuotes {
    private static final Set<Key> VISIBLE = new LinkedHashSet<>();
    private static Map<Key, EmcAmount> prices = Map.of();
    private static Set<Key> requested = Set.of();
    private static long sequence, lastRequest, received;
    private NeoForgeJeiMediaQuotes() {}
    public static void begin() { VISIBLE.clear(); }
    public static EmcAmount price(Key key) {
        if (VISIBLE.size() < NeoForgeJeiPayloads.LIMIT) VISIBLE.add(key);
        return System.nanoTime() - received < 1_500_000_000L ? prices.getOrDefault(key, EmcAmount.ZERO) : EmcAmount.ZERO;
    }
    public static void flush() {
        long now = System.nanoTime();
        if (VISIBLE.isEmpty() || now - lastRequest < 1_000_000_000L) return;
        lastRequest = now;
        requested = Set.copyOf(VISIBLE);
        PacketDistributor.sendToServer(new NeoForgeJeiPayloads.RequestQuotes(++sequence, List.copyOf(VISIBLE)));
    }
    public static void accept(NeoForgeJeiPayloads.Quotes result) {
        if (result.sequence() != sequence) return;
        Map<Key, EmcAmount> next = new HashMap<>();
        for (var entry : result.entries()) if (requested.contains(entry.key())) {
            try { next.put(entry.key(), EmcAmount.parse(entry.price())); }
            catch (IllegalArgumentException ignored) { /* Malformed quotes remain hidden. */ }
        }
        prices = Map.copyOf(next);
        received = System.nanoTime();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ++sequence; VISIBLE.clear(); requested = Set.of(); prices = Map.of(); lastRequest = received = 0;
    }
}
