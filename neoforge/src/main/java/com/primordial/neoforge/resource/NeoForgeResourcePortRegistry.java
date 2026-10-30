package com.primordial.neoforge.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Public optional integration boundary; no third-party API occurs in its signatures. */
public final class NeoForgeResourcePortRegistry {
    @FunctionalInterface public interface Provider { List<NeoForgeResourcePort> find(BlockEntity machine, Direction side); }
    private record Entry(String id, Predicate<BlockEntity> matcher, Provider provider) {}
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static boolean initialized;
    private NeoForgeResourcePortRegistry() {}
    public static synchronized void register(String id, Predicate<BlockEntity> matcher, Provider provider) {
        if (id == null || matcher == null || provider == null) throw new NullPointerException("resource adapter");
        if (ENTRIES.stream().noneMatch(entry -> entry.id.equals(id))) ENTRIES.add(new Entry(id, matcher, provider));
    }
    public static synchronized void ensureAdapters() {
        if (initialized) return;
        initialized = true;
        NeoForgeNativeResourceAdapters.register();
    }
    public static List<NeoForgeResourcePort> find(BlockEntity machine, Direction side) {
        if (machine == null || machine.isRemoved()) return List.of();
        ensureAdapters();
        List<NeoForgeResourcePort> result = new ArrayList<>();
        for (Entry entry : ENTRIES) try {
            if (!entry.matcher.test(machine)) continue;
            List<NeoForgeResourcePort> ports = entry.provider.find(machine, side);
            if (ports != null) for (NeoForgeResourcePort port : ports) {
                if (result.size() >= 512) return List.of();
                if (port != null && port.unit() != null) result.add(port);
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Discovery is read-only; an unavailable optional provider does not hide other providers.
        }
        return List.copyOf(result);
    }
    public static List<String> registeredIds() { ensureAdapters(); return ENTRIES.stream().map(Entry::id).toList(); }
}
