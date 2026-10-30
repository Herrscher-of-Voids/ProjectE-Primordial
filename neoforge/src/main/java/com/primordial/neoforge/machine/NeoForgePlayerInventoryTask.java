package com.primordial.neoforge.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.primordial.data.SampleMode;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class NeoForgePlayerInventoryTask {
    private static final Map<UUID, long[]> LAST_RUN = new HashMap<>();
    private static final java.util.Set<ServerPlayer> ACTIVE = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static final Map<ServerPlayer, Integer> RESOURCE_TICKS = new java.util.IdentityHashMap<>();

    private NeoForgePlayerInventoryTask() {}

    public static void run(ServerPlayer player) {
        if (player == null || !player.getServer().isSameThread() || !player.isAlive() || player.isRemoved() || !ACTIVE.add(player)) return;
        try { runCurrent(player); }
        finally { ACTIVE.remove(player); }
    }

    private static void runCurrent(ServerPlayer player) {
        runResources(player);
        var rules = NeoForgePlayerInventorySavedData.get(player.getServer()).rules(player.getUUID());
        if (rules.isEmpty()) {
            LAST_RUN.remove(player.getUUID());
            return;
        }
        long gameTime = player.level().getGameTime();
        long[] timers = LAST_RUN.computeIfAbsent(player.getUUID(), ignored -> new long[36]);
        for (var entry : rules.entrySet()) {
            int slot = entry.getKey();
            var rule = entry.getValue();
            if (!rule.enabled() || rule.targetCount() <= 0) continue;
            if (rule.mode() == SampleMode.FIXED_RATE && !intervalDue(timers, slot, rule.intervalTicks(), gameTime)) continue;
            ItemStack filter = rule.filter();
            ItemStack current = player.getInventory().getItem(slot);
            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, filter)) continue;
            if (!NeoForgeResourceKnowledge.allowsItem(player.getServer(), player.getUUID(), filter)) continue;

            int have = current.isEmpty() ? 0 : current.getCount();
            int room = Math.max(0, filter.getMaxStackSize() - have);
            int requested = rule.mode() == SampleMode.KEEP_LEVEL ? room : Math.min(rule.targetCount(), room);
            if (requested <= 0) continue;
            ItemStack expected = current.copy();
            NeoForgeResourceTrades.purchaseItem(player.getServer(), player.getUUID(), filter, requested, units -> {
                ItemStack actual = player.getInventory().getItem(slot);
                if (!ItemStack.matches(actual, expected)) return 0;
                player.getInventory().setItem(slot, expected.isEmpty()
                        ? filter.copyWithCount((int) units) : expected.copyWithCount(have + (int) units));
                player.getInventory().setChanged();
                return units;
            });
        }
    }

    public static void forget(UUID owner) {
        LAST_RUN.remove(owner);
        RESOURCE_TICKS.keySet().removeIf(player -> player.getUUID().equals(owner));
    }

    private static void runResources(ServerPlayer player) {
        Integer last = RESOURCE_TICKS.put(player, player.server.getTickCount());
        if (last != null && last == player.server.getTickCount()) return;
        var data = NeoForgePlayerInventorySavedData.get(player.server);
        for (int index = 0; index < com.primordial.neoforge.resource.NeoForgePlayerResourcePorts.COUNT; index++) {
            if (!data.resourceEnabled(player.getUUID(), index)) continue;
            final int resource = index;
            try {
                var port = com.primordial.neoforge.resource.NeoForgePlayerResourcePorts.find(player, index);
                if (port == null || !port.canReceive()) continue;
                long requested = port.receive(Long.MAX_VALUE, true);
                if (requested <= 0 || !data.resourceEnabled(player.getUUID(), index)) continue;
                NeoForgeResourceTrades.purchaseResource(player.server, player.getUUID(), port.unit(), requested, units -> {
                    if (!player.isAlive() || player.isRemoved() || !data.resourceEnabled(player.getUUID(), resource)
                            || !port.canReceive()) return 0;
                    // Native ports recheck the original MagicData / selected Arca or Totem identity before every write.
                    long accepted = port.receive(units, true);
                    if (accepted < 0 || accepted > units) throw new IllegalStateException("Invalid player resource simulation");
                    return !data.resourceEnabled(player.getUUID(), resource) ? 0 : port.receive(accepted, false);
                });
            } catch (RuntimeException | LinkageError failure) {
                LogUtils.getLogger().debug("[Primordial] Player resource {} unavailable for {}", index, player.getUUID(), failure);
            }
        }
    }

    private static boolean intervalDue(long[] timers, int slot, int intervalTicks, long gameTime) {
        long next = timers[slot] + Math.max(1, intervalTicks);
        if (gameTime < next) return false;
        timers[slot] = gameTime;
        return true;
    }
}
