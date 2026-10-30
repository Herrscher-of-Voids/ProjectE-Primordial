package com.primordial.neoforge.resource;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Goety 3.2: active Arca first, otherwise its native TotemFinder account; purchase only. */
final class NeoForgeGoetySoulPort implements NeoForgeResourcePort {
    private final ServerPlayer player;
    private final boolean arca;
    private final ItemStack totem;
    private final Method active, souls, setSouls, finder, currentTotem, maxTotem, setTotem, sync;
    private final ModConfigSpec.ConfigValue<?> maxArca;
    private NeoForgeGoetySoulPort(ServerPlayer player, ModConfigSpec.ConfigValue<?> config, Method... methods) {
        this.player = player; maxArca = config;
        active = methods[0]; souls = methods[1]; setSouls = methods[2]; finder = methods[3];
        currentTotem = methods[4]; maxTotem = methods[5]; setTotem = methods[6]; sync = methods[7];
        arca = active(); totem = arca ? ItemStack.EMPTY : findTotem();
    }
    static NeoForgeResourcePort create(ServerPlayer player) throws ReflectiveOperationException {
        Class<?> helper = Class.forName("com.Polarice3.Goety.utils.SEHelper");
        Class<?> finder = Class.forName("com.Polarice3.Goety.utils.TotemFinder");
        Class<?> totem = Class.forName("com.Polarice3.Goety.api.items.magic.ITotem");
        var config = (ModConfigSpec.ConfigValue<?>) Class.forName("com.Polarice3.Goety.config.MainConfig").getField("MaxArcaSouls").get(null);
        var port = new NeoForgeGoetySoulPort(player, config, helper.getMethod("getSEActive", Player.class),
                helper.getMethod("getSESouls", Player.class), helper.getMethod("setSESouls", Player.class, int.class),
                finder.getMethod("FindTotem", Player.class), totem.getMethod("currentSouls", ItemStack.class),
                totem.getMethod("maximumSouls", ItemStack.class), totem.getMethod("setSoulsamount", ItemStack.class, int.class),
                helper.getMethod("sendSEUpdatePacket", Player.class));
        return !port.arca && (port.totem.isEmpty() || !totem.isInstance(port.totem.getItem())) ? null : port;
    }
    private boolean active() { return Boolean.TRUE.equals(NeoForgePlayerResourcePorts.invoke(active, null, player)); }
    private ItemStack findTotem() { return (ItemStack) NeoForgePlayerResourcePorts.invoke(finder, null, player); }
    private boolean current() { return !player.isRemoved() && player.isAlive() && active() == arca && (arca || findTotem() == totem); }
    public ResourceLocation unit() { return ResourceLocation.parse("goety:soul_energy"); }
    public String key() { return "player/" + player.getUUID() + "/goety/" + (arca ? "arca" : "totem"); }
    public long amount() {
        if (!current()) return 0;
        return Math.max(0, ((Number) (arca ? NeoForgePlayerResourcePorts.invoke(souls, null, player)
                : NeoForgePlayerResourcePorts.invoke(currentTotem, null, totem))).longValue());
    }
    public long capacity() {
        if (!current()) return 0;
        return Math.max(0, Math.min(Integer.MAX_VALUE, ((Number) (arca ? maxArca.get()
                : NeoForgePlayerResourcePorts.invoke(maxTotem, null, totem))).longValue()));
    }
    public boolean canReceive() { return current() && capacity() > 0; }
    public boolean canExtract() { return false; }
    public long extract(long requested, boolean simulate) { return 0; }
    public long receive(long requested, boolean simulate) {
        if (!current() || requested <= 0) return 0;
        long before = amount(), bounded = Math.max(0, Math.min(requested, capacity() - before));
        if (simulate || bounded == 0 || !current()) return bounded;
        NeoForgePlayerResourcePorts.invoke(arca ? setSouls : setTotem, null, arca ? player : totem, (int) (before + bounded));
        long actual = amount() - before;
        if (actual < 0 || actual > bounded) throw new IllegalStateException("Soul account changed outside authorized transfer");
        try {
            if (arca) NeoForgePlayerResourcePorts.invoke(sync, null, player);
            else { player.getInventory().setChanged(); player.containerMenu.broadcastChanges(); }
        } catch (RuntimeException failedSync) { NeoForgePlayerResourcePorts.warn("goety_sync", failedSync); }
        return actual;
    }
}
