package com.primordial.neoforge.resource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/** Iron's Spells 3.16 uses event-aware MagicData writes and a native NeoForge mana payload. */
final class NeoForgeIronManaPort implements NeoForgeResourcePort {
    private static final ResourceLocation UNIT = ResourceLocation.parse("irons_spellbooks:mana");
    private final ServerPlayer player;
    private final Object data;
    private final Method get, read, write;
    private final Constructor<?> packet;
    private NeoForgeIronManaPort(ServerPlayer player, Object data, Method get, Method read, Method write, Constructor<?> packet) {
        this.player = player; this.data = data; this.get = get; this.read = read; this.write = write; this.packet = packet;
    }
    static NeoForgeResourcePort create(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> magic = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
        Method get = magic.getMethod("getPlayerMagicData", LivingEntity.class);
        Object data = NeoForgePlayerResourcePorts.invoke(get, null, player);
        return data == null ? null : new NeoForgeIronManaPort(player, data, get, magic.getMethod("getMana"),
                magic.getMethod("setMana", float.class),
                Class.forName("io.redspace.ironsspellbooks.network.SyncManaPacket").getConstructor(magic));
    }
    private double current() {
        double amount = ((Number) NeoForgePlayerResourcePorts.invoke(read, data)).doubleValue();
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalStateException("Invalid native mana balance");
        return amount;
    }
    private double maximum() {
        var holder = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("irons_spellbooks:max_mana")).orElse(null);
        if (holder == null || player.getAttribute(holder) == null) return 0;
        double maximum = player.getAttributeValue(holder);
        return Double.isFinite(maximum) ? Math.max(0, Math.min(Integer.MAX_VALUE, maximum)) : 0;
    }
    public ResourceLocation unit() { return UNIT; }
    public String key() { return "player/" + player.getUUID() + "/iron_mana"; }
    public long amount() { return (long) current(); }
    public long capacity() { return (long) maximum(); }
    public boolean canReceive() { return maximum() > 0; }
    public boolean canExtract() { return false; }
    public long extract(long requested, boolean simulate) { return 0; }
    public long receive(long requested, boolean simulate) {
        if (requested <= 0 || !player.isAlive() || player.isRemoved()
                || NeoForgePlayerResourcePorts.invoke(get, null, player) != data) return 0;
        double before = current();
        long bounded = Math.max(0, Math.min(requested, (long) Math.max(0, maximum() - before)));
        if (simulate || bounded == 0) return bounded;
        float target = (float) (before + bounded);
        if (target > before + bounded) target = Math.nextDown(target);
        if (target <= before) return 0;
        NeoForgePlayerResourcePorts.invoke(write, data, target);
        double actual = current() - before;
        if (actual < 0 || actual > bounded) throw new IllegalStateException("Mana event changed outside authorized transfer");
        try { PacketDistributor.sendToPlayer(player, (CustomPacketPayload) packet.newInstance(data)); }
        catch (ReflectiveOperationException | RuntimeException failedSync) { NeoForgePlayerResourcePorts.warn("iron_mana_sync", failedSync); }
        // Native event hooks may grant a fractional amount; the long-valued port bills its smallest whole unit.
        return (long) Math.ceil(actual);
    }
}
