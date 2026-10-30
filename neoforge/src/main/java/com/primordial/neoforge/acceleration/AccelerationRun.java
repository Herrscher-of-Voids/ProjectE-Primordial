package com.primordial.neoforge.acceleration;

import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

public final class AccelerationRun {
    public static final int DURATION_TICKS = 600;
    public static final BigInteger MAX_SPEND = BigInteger.valueOf(2_100_000_000L);
    private static UUID session = UUID.randomUUID();
    private AccelerationRun() { }

    static void begin(ItemStack stack, MinecraftServer server) {
        if (AccelerationBindings.view(stack).stream().anyMatch(AccelerationBindings::enabled) && !expired(stack, server)) return;
        if (expired(stack, server)) AccelerationBindings.pauseAll(stack);
        CompoundTag root = AccelerationData.get(stack);
        root.putUUID("RunSession", session);
        root.putLong("RunDeadline", (long) server.getTickCount() + DURATION_TICKS);
        root.putString("RunSpent", "0");
    }

    public static boolean expired(ItemStack stack, MinecraftServer server) {
        CompoundTag root = AccelerationData.get(stack);
        return root == null || !root.hasUUID("RunSession") || !session.equals(root.getUUID("RunSession"))
                || server.getTickCount() >= root.getLong("RunDeadline");
    }

    public static BigInteger remaining(ItemStack stack) {
        try {
            CompoundTag root = AccelerationData.get(stack);
            if (root == null) return BigInteger.ZERO;
            String amount = root.getString("RunSpent");
            if (!amount.matches("[0-9]{1,10}")) return BigInteger.ZERO;
            return MAX_SPEND.subtract(new BigInteger(amount)).max(BigInteger.ZERO);
        } catch (NumberFormatException invalid) { return BigInteger.ZERO; }
    }

    static void charge(ItemStack stack, BigInteger amount) {
        AccelerationData.get(stack).putString("RunSpent", MAX_SPEND.subtract(remaining(stack)).add(amount).toString());
    }

    static void reset() { session = UUID.randomUUID(); }
}
