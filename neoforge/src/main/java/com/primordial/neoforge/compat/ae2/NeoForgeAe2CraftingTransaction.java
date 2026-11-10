package com.primordial.neoforge.compat.ae2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.crafting.inv.ListCraftingInventory;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.trade.NeoForgeResourceTrades.PurchaseReceipt;
import net.minecraft.server.MinecraftServer;

/** Exact refunds only for generated resources in one synchronous AE crafting submission. */
public final class NeoForgeAe2CraftingTransaction implements AutoCloseable {
    private static final ThreadLocal<NeoForgeAe2CraftingTransaction> CURRENT = new ThreadLocal<>();
    private final NeoForgeAe2CraftingTransaction previous;
    private final IActionSource source;
    private final List<Generated> generated = new ArrayList<>();
    private final KeyCounter returned = new KeyCounter();

    private NeoForgeAe2CraftingTransaction(IActionSource source) {
        this.source = source;
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static NeoForgeAe2CraftingTransaction begin(IActionSource source) {
        return new NeoForgeAe2CraftingTransaction(source);
    }

    public static void record(MinecraftServer server, UUID owner, AEKey key, IActionSource source,
                              PurchaseReceipt receipt) {
        var scope = CURRENT.get();
        if (scope != null && scope.source == source && receipt.units() > 0)
            scope.generated.add(new Generated(server, owner, key, receipt));
    }

    /** Withhold our generated portion from physical storage before refunding its original exact price. */
    public static long returnInitialItems(MEStorage inventory, AEKey key, long amount,
                                          Actionable mode, IActionSource source) {
        var scope = CURRENT.get();
        long refunded = 0;
        if (scope != null && scope.source == source && mode == Actionable.MODULATE && amount > 0) {
            for (var entry : scope.generated) {
                if (!entry.key.equals(key) || entry.remaining == 0
                        || !NeoForgeEmcSavedData.get(entry.server).canCredit(entry.owner)) continue;
                long units = Math.min(amount - refunded, entry.remaining);
                entry.remaining -= units;
                NeoForgeEmcCredits.credit(entry.server, entry.owner, entry.receipt.unitPrice().multiply(units));
                scope.returned.add(key, units);
                refunded += units;
                if (refunded == amount) break;
            }
        }
        long stored = refunded < amount ? inventory.insert(key, amount - refunded, mode, source) : 0;
        if (scope != null && scope.source == source && mode == Actionable.MODULATE && stored > 0)
            scope.returned.add(key, stored);
        return refunded + stored;
    }

    /** AE ignores a rejected rollback insertion; keep those resources in its persisted CPU inventory. */
    public static void clearReturnedItems(ListCraftingInventory inventory) {
        var scope = CURRENT.get();
        if (scope == null) inventory.clear();
        else scope.consumeReturned(inventory);
    }

    /** Also runs if a later third-party rollback throws, so confirmed refunds cannot leave CPU duplicates. */
    public void consumeReturned(ListCraftingInventory inventory) {
        for (var entry : returned)
            inventory.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
        inventory.list.removeZeros();
        inventory.list.removeEmptySubmaps();
        returned.clear();
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }

    private static final class Generated {
        final MinecraftServer server;
        final UUID owner;
        final AEKey key;
        final PurchaseReceipt receipt;
        long remaining;

        Generated(MinecraftServer server, UUID owner, AEKey key, PurchaseReceipt receipt) {
            this.server = server;
            this.owner = owner;
            this.key = key;
            this.receipt = receipt;
            remaining = receipt.units();
        }
    }
}
