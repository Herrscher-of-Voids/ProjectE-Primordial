/** Server-authoritative carried-media outcome; amounts are actual delivered/removed units. */
package com.primordial.neoforge.transmutation;

import com.primordial.service.EmcAmount;

public record NeoForgeMediaTradeResult(Status status, String id, long units, String emc) {
    public enum Status { SUCCESS, EMPTY, INVALID_RESOURCE, DISABLED, UNKNOWN, UNPRICED,
        NO_CONTAINER, FULL, NO_FUNDS, INVENTORY_FULL, NO_TRANSFER, ERROR }
    public boolean traded() { return status == Status.SUCCESS && units > 0; }
    public static NeoForgeMediaTradeResult failure(Status status) {
        return new NeoForgeMediaTradeResult(status, "", 0, "0");
    }
    public static NeoForgeMediaTradeResult success(String id, long units, EmcAmount price) {
        return new NeoForgeMediaTradeResult(Status.SUCCESS, id, units, price.multiply(units).toString());
    }
}
