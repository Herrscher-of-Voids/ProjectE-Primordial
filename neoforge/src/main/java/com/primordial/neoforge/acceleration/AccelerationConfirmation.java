package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.AccelerationConfig;
import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
import com.primordial.neoforge.acceleration.S2CAccelerationWarningPacket;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

final class AccelerationConfirmation {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static UUID session = UUID.randomUUID();

    private AccelerationConfirmation() { }

    static void start(ItemStack stack, ServerPlayer player, InteractionHand hand, List<CompoundTag> entries) {
        cancel(player.getUUID());
        entries.forEach(AccelerationBindings::pause);
        int maxRate = entries.stream().mapToInt(AccelerationBindings::rate).max().orElse(2);
        if (maxRate < EmcAccelerationService.warningMultiplier()) {
            enable(stack, entries, player);
            return;
        }
        entries.forEach(entry -> entry.putBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY, true));
        UUID token = UUID.randomUUID();
        BigInteger price = price(entries);
        PENDING.put(player.getUUID(), new Pending(token, stack, hand, entries.stream().map(CompoundTag::copy).toList(), price, player.server.getTickCount()));
        S2CAccelerationWarningPacket.send(player, token, maxRate, AccelerationBindings.pos(entries.get(0)), entries.size(), price.toString());
    }

    static void confirm(ServerPlayer player, UUID token, boolean accepted) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.token().equals(token)) return;
        PENDING.remove(player.getUUID());
        try (var edit = AccelerationData.edit(pending.stack())) {
            List<CompoundTag> entries = current(pending);
            boolean valid = accepted && player.server.getTickCount() - pending.created() <= 600
                    && player.getItemInHand(pending.hand()) == pending.stack() && player.isAlive() && !player.isSpectator()
                    && pending.stack().getItem() instanceof EmcAcceleratorItem && AccelerationConfig.accelerationEnabled()
                    && entries.equals(pending.snapshots()) && pending.price().equals(price(entries))
                    && entries.stream().allMatch(entry -> EmcAccelerationService.validBinding(player.server, entry));
            entries.forEach(AccelerationBindings::pause);
            if (valid) enable(pending.stack(), entries, player);
        }
    }

    private static List<CompoundTag> current(Pending pending) {
        var entries = AccelerationBindings.view(pending.stack());
        return pending.snapshots().stream().map(snapshot -> entries.stream()
                .filter(entry -> AccelerationBindings.id(entry).equals(AccelerationBindings.id(snapshot)))
                .findFirst().orElse(null)).filter(java.util.Objects::nonNull).toList();
    }

    private static void pausePending(Pending pending) {
        try (var edit = AccelerationData.edit(pending.stack())) {
            current(pending).forEach(AccelerationBindings::pause);
        }
    }

    static BigInteger price(List<CompoundTag> entries) {
        BigInteger total = BigInteger.ZERO;
        for (CompoundTag entry : entries) {
            int targets = AccelerationSettings.kind(entry) == AccelerationSettings.Kind.AREA ? AccelerationSettings.read(entry).volume() : 1;
            total = total.add(EmcAccelerationService.costPerSecond(AccelerationBindings.rate(entry)).multiply(BigInteger.valueOf(targets)));
        }
        return total;
    }

    private static void enable(ItemStack stack, List<CompoundTag> entries, ServerPlayer player) {
        AccelerationRun.begin(stack, player.server);
        for (CompoundTag entry : entries) {
            entry.putUUID("ConfirmedSession", session);
            entry.putUUID("ConfirmedOwner", player.getUUID());
            entry.putBoolean(EmcAcceleratorItem.ENABLED_KEY, true);
            entry.putBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY, false);
        }
        EmcAccelerationService.message(player, "message.primordial.acceleration_started_targets", entries.size(), price(entries).toString());
    }

    static boolean authorized(CompoundTag entry, UUID player) {
        return AccelerationBindings.rate(entry) < EmcAccelerationService.warningMultiplier()
                || entry.hasUUID("ConfirmedSession") && session.equals(entry.getUUID("ConfirmedSession"))
                && entry.hasUUID("ConfirmedOwner") && player.equals(entry.getUUID("ConfirmedOwner"));
    }

    static void cancel(UUID player) {
        Pending pending = PENDING.remove(player);
        if (pending != null) pausePending(pending);
    }

    static void expire(MinecraftServer server) {
        PENDING.entrySet().removeIf(entry -> {
            if (server.getTickCount() - entry.getValue().created() <= 600) return false;
            pausePending(entry.getValue());
            return true;
        });
    }

    static void reset() {
        PENDING.values().forEach(pending -> pausePending(pending));
        PENDING.clear();
        session = UUID.randomUUID();
    }

    private record Pending(UUID token, ItemStack stack, InteractionHand hand, List<CompoundTag> snapshots, BigInteger price, int created) { }
}
