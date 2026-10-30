package com.primordial.neoforge.resource;

import net.minecraft.resources.ResourceLocation;

/** A synchronous native resource endpoint. Simulation must not mutate its backing storage. */
public interface NeoForgeResourcePort {
    ResourceLocation unit();
    long amount();
    long capacity();
    boolean canReceive();
    boolean canExtract();
    long receive(long amount, boolean simulate);
    long extract(long amount, boolean simulate);
    default String key() { return unit().toString(); }
}
