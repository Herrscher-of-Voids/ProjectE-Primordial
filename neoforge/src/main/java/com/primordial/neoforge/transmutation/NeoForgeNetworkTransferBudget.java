/**
 * Per-owner, per-server-tick limits shared by every virtual storage interface.
 */
package com.primordial.neoforge.transmutation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;

public final class NeoForgeNetworkTransferBudget {
    public static final long ITEMS_PER_TICK = 4096;
    public static final long FLUID_MB_PER_TICK = 1_000_000;
    private static final Map<MinecraftServer, Map<UUID, TickBudget>> BUDGETS = new java.util.WeakHashMap<>();

    private NeoForgeNetworkTransferBudget() {}

    public static synchronized long remainingItems(MinecraftServer server, UUID owner) {
        if (!valid(server, owner)) return 0;
        return current(server, owner).items.remaining();
    }

    public static synchronized Reservation reserveItems(MinecraftServer server, UUID owner, long requested) {
        if (!valid(server, owner) || requested <= 0) return new Reservation(null, 0);
        return reserve(current(server, owner).items, requested);
    }

    public static synchronized long remainingFluid(MinecraftServer server, UUID owner) {
        if (!valid(server, owner)) return 0;
        return current(server, owner).fluids.remaining();
    }

    public static synchronized Reservation reserveFluid(MinecraftServer server, UUID owner, long requested) {
        if (!valid(server, owner) || requested <= 0) return new Reservation(null, 0);
        return reserve(current(server, owner).fluids, requested);
    }

    private static Reservation reserve(Counter budget, long requested) {
        long reserved = Math.min(requested, budget.remaining());
        budget.reserved += reserved;
        return new Reservation(budget, reserved);
    }

    private static boolean valid(MinecraftServer server, UUID owner) {
        if (owner == null || server == null) return false;
        NeoForgeEmcCredits.requireServerThread(server);
        return server.overworld() != null;
    }

    private static TickBudget current(MinecraftServer server, UUID owner) {
        long tick = server.overworld().getGameTime();
        Map<UUID, TickBudget> owners = BUDGETS.computeIfAbsent(server, ignored -> new HashMap<>());
        TickBudget budget = owners.get(owner);
        if (budget == null || budget.tick != tick) {
            budget = new TickBudget(tick);
            owners.put(owner, budget);
        }
        return budget;
    }

    private static synchronized void finish(Counter budget, long reserved, long moved) {
        if (moved < 0 || moved > reserved) throw new IllegalArgumentException("Invalid network transfer amount");
        budget.reserved = Math.max(0, budget.reserved - reserved);
        budget.used = Math.min(budget.limit, budget.used + moved);
    }

    private static final class TickBudget {
        final long tick;
        final Counter items = new Counter(ITEMS_PER_TICK);
        final Counter fluids = new Counter(FLUID_MB_PER_TICK);
        TickBudget(long tick) { this.tick = tick; }
    }

    private static final class Counter {
        final long limit;
        long used;
        long reserved;
        Counter(long limit) { this.limit = limit; }
        long remaining() { return Math.max(0, limit - used - reserved); }
    }

    public static final class Reservation implements AutoCloseable {
        private final Counter budget;
        private final long amount;
        private boolean completed;

        private Reservation(Counter budget, long amount) {
            this.budget = budget;
            this.amount = amount;
        }

        public long amount() { return amount; }

        public void complete(long moved) {
            if (completed) throw new IllegalStateException("Network transfer reservation already completed");
            completed = true;
            if (budget != null) finish(budget, amount, moved);
            else if (moved != 0) throw new IllegalArgumentException("Invalid empty network transfer reservation");
        }

        @Override public void close() {
            if (!completed) complete(0);
        }
    }
}
