/**
 * 【文件职责】通过 Iron's Spells 原生 MagicData 补充玩家法力。
 * 【变更记录 #1】2026-10-14 17:12:00（第 1 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：最大法力属性、事件感知写入、实际增量计费和原生法力同步。
 * - 修改：无资源回收入口；满额、模拟或被原生事件拒绝时不产生收费。
 * - 用途：尊重装备提供的法力容量，复用现有 EMC 分数结算。
 */
package com.primordial.resource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

final class IronManaPort implements EmcResourcePort {
    private final ServerPlayer player;
    private final Object data;
    private final Method read, write, sync;
    private final Constructor<?> packet;

    private IronManaPort(ServerPlayer player, Object data, Method read, Method write, Method sync, Constructor<?> packet) {
        this.player = player; this.data = data; this.read = read; this.write = write; this.sync = sync; this.packet = packet;
    }

    static PlayerResourcePorts.Factory factory() throws ReflectiveOperationException {
        Class<?> magic = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
        Method get = magic.getMethod("getPlayerMagicData", LivingEntity.class);
        Method read = magic.getMethod("getMana"), write = magic.getMethod("setMana", float.class);
        Method sync = Class.forName("io.redspace.ironsspellbooks.setup.Messages").getMethod("sendToPlayer", Object.class, ServerPlayer.class);
        Class<?> packetType;
        try { packetType = Class.forName("io.redspace.ironsspellbooks.network.SyncManaPacket"); }
        catch (ClassNotFoundException legacy) { packetType = Class.forName("io.redspace.ironsspellbooks.network.ClientboundSyncMana"); }
        Constructor<?> packet = packetType.getConstructor(magic);
        return player -> new IronManaPort(player, PlayerResourcePorts.invoke(get, null, player), read, write, sync, packet);
    }

    private double current() { return Math.max(0, ((Number) PlayerResourcePorts.invoke(read, data)).doubleValue()); }
    private double maximum() {
        var attribute = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation("irons_spellbooks", "max_mana"));
        return attribute == null || player.getAttribute(attribute) == null ? 0 : Math.max(0, player.getAttributeValue(attribute));
    }
    @Override public ResourceLocation unit() { return ResourceExchangeConversions.IRON_MANA; }
    @Override public long amount() { return (long) current(); }
    @Override public long capacity() { return (long) Math.min(Integer.MAX_VALUE, maximum()); }
    @Override public boolean canReceive() { return data != null && capacity() > 0; }
    @Override public boolean canExtract() { return false; }
    @Override public long extract(long amount, boolean simulate) { return 0; }
    @Override public long receive(long requested, boolean simulate) {
        double before = current();
        long accepted = Math.max(0, Math.min(requested, (long) Math.max(0, maximum() - before)));
        if (simulate || accepted == 0) return accepted;
        PlayerResourcePorts.invoke(write, data, (float) (before + accepted));
        long actual = Math.max(0, Math.min(accepted, (long) (current() - before)));
        try { sync.invoke(null, packet.newInstance(data), player); }
        catch (ReflectiveOperationException | RuntimeException failure) { PlayerResourcePorts.warn("irons_spellbooks-sync", failure); }
        return actual;
    }
}
