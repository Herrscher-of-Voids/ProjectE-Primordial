package com.primordial.neoforge.acceleration;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

final class AccelerationBudget {
    private final LongSupplier clock;
    private final long start;
    private final long nanos;
    private final int targetLimit;
    private final Set<Object> targets = new HashSet<>();
    private int ticksLeft;
    private int scansLeft = com.primordial.neoforge.acceleration.AccelerationConfig.accelerationMaxScans();

    AccelerationBudget(int targetLimit, int tickLimit, long nanos, LongSupplier clock) {
        this.targetLimit = Math.max(0, targetLimit);
        this.ticksLeft = Math.max(0, tickLimit);
        this.nanos = Math.max(0, nanos);
        this.clock = clock;
        this.start = clock.getAsLong();
    }

    boolean available() { return ticksLeft > 0 && clock.getAsLong() - start < nanos; }
    boolean acceptsTarget() { return targets.size() < targetLimit && available(); }
    boolean claim(Object target) { return acceptsTarget() && targets.add(target); }
    boolean scan() {
        if (scansLeft <= 0 || !available()) return false;
        scansLeft--;
        return true;
    }


    Result run(int requested, BooleanSupplier valid, Runnable tick) {
        int attempted = 0;
        for (int i = 0; i < requested && available(); i++) {
            long before = clock.getAsLong();
            try {
                if (!valid.getAsBoolean()) return new Result(attempted, true, false, null);
                if (!available()) break;
                ticksLeft--;
                attempted++;
                tick.run();
            } catch (RuntimeException | LinkageError failure) {
                return new Result(attempted, false, false, failure);
            }
            if (clock.getAsLong() - before >= nanos) return new Result(attempted, false, true, null);
        }
        return new Result(attempted, false, false, null);
    }

    record Result(int attempted, boolean invalid, boolean slow, Throwable failure) { }
}
