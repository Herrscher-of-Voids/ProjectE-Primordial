package com.primordial.neoforge.resource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Botania Revived 1.0.3 public mana API; the official 1.21 port is not released yet. */
public final class NeoForgeBotaniaResourceAdapter {
    public static final ResourceLocation UNIT = ResourceLocation.parse("botania:mana");
    private static final ResourceLocation CREATIVE_POOL = ResourceLocation.parse("botania:creative_pool");

    private NeoForgeBotaniaResourceAdapter() {}

    @SuppressWarnings("unchecked")
    public static void register() {
        try {
            Class<?> receiver = Class.forName("velrondevs.botania.api.mana.ManaReceiver");
            Class<?> pool = Class.forName("velrondevs.botania.api.mana.ManaPool");
            Class<?> collector = Class.forName("velrondevs.botania.api.mana.ManaCollector");
            var capability = (BlockCapability<Object, Direction>) Class.forName("velrondevs.botania.api.BotaniaCapabilities")
                    .getField("MANA_RECEIVER").get(null);
            Method amount = receiver.getMethod("getCurrentMana");
            Method receive = receiver.getMethod("receiveMana", int.class);
            Method poolCapacity = pool.getMethod("getMaxMana");
            Method collectorCapacity = collector.getMethod("getMaxMana");
            NeoForgeResourcePortRegistry.register("botania", machine ->
                    machine.getClass().getName().startsWith("velrondevs.botania.")
                            && (pool.isInstance(machine) || collector.isInstance(machine)), (machine, side) -> {
                if (!NeoForgeResourcePortAccess.live(machine)
                        || CREATIVE_POOL.equals(BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock()))) return List.of();
                Method capacity = pool.isInstance(machine) ? poolCapacity : collectorCapacity;
                // A newly placed pool has capacity -1 until Botania's own first tick initializes it.
                if (number(capacity, machine) <= 0 || endpoint(machine, side, capability) != machine) return List.of();
                return List.of(new NeoForgeResourcePort() {
                    public ResourceLocation unit() { return UNIT; }
                    public String key() { return "botania/mana/" + machine.getClass().getName(); }
                    public long amount() { return Math.max(0, number(amount, machine)); }
                    public long capacity() { return Math.max(0, number(capacity, machine)); }
                    public boolean canReceive() { return available() && amount() < capacity(); }
                    public boolean canExtract() { return available() && amount() > 0; }
                    public long receive(long requested, boolean simulate) { return move(requested, simulate, true); }
                    public long extract(long requested, boolean simulate) { return move(requested, simulate, false); }
                    private boolean available() { return endpoint(machine, side, capability) == machine; }
                    private long move(long requested, boolean simulate, boolean incoming) {
                        if (requested <= 0 || !available()) return 0;
                        long before = amount();
                        int bounded = (int) Math.min(Integer.MAX_VALUE, Math.min(requested,
                                incoming ? Math.max(0, capacity() - before) : before));
                        if (simulate || bounded == 0) return bounded;
                        // ManaReceiver's signed void method reports no count; credit only its actual storage delta.
                        invoke(receive, machine, incoming ? bounded : -bounded);
                        long delta = incoming ? amount() - before : before - amount();
                        if (delta < 0 || delta > bounded) throw new IllegalStateException("Botania mana changed outside authorized transfer");
                        return delta;
                    }
                });
            });
        } catch (ClassNotFoundException unavailable) {
            // Other Botania ports need their own verified API rather than guessed package fallbacks.
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            com.mojang.logging.LogUtils.getLogger().warn("[Primordial] Botania Revived mana API is unavailable", unavailable);
        }
    }

    private static Object endpoint(BlockEntity machine, Direction side, BlockCapability<Object, Direction> capability) {
        return NeoForgeResourcePortAccess.live(machine)
                ? machine.getLevel().getCapability(capability, machine.getBlockPos(), machine.getBlockState(), machine, side) : null;
    }

    private static long number(Method method, Object receiver) { return ((Number) invoke(method, receiver)).longValue(); }

    private static Object invoke(Method method, Object receiver, Object... arguments) {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Botania mana invocation failed", cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Botania mana API changed", failure); }
    }
}
