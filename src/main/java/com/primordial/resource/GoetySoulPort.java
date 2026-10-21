/**
 * 【文件职责】通过 Goety 原生方舟或图腾账户补充灵魂能量。
 * 【变更记录 #1】2026-10-14 17:12:00（第 1 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：原生账户选择、容量读取、确切数量写入及原生同步。
 * - 修改：兑换不套用击杀增益或施法折扣，实际增加多少才支付多少 EMC。
 * - 用途：保留 Goety 的图腾/方舟玩法；未持有有效账户不购买、不扣费。
 */
package com.primordial.resource;

import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class GoetySoulPort implements EmcResourcePort {
    private final ServerPlayer player;
    private final Object config;
    private final Method active, souls, setSouls, findTotem, totemSouls, totemMaximum, setTotem, sync;
    private GoetySoulPort(ServerPlayer player, Object config, Method... methods) {
        this.player = player; this.config = config;
        active=methods[0]; souls=methods[1]; setSouls=methods[2]; findTotem=methods[3];
        totemSouls=methods[4]; totemMaximum=methods[5]; setTotem=methods[6]; sync=methods[7];
    }
    static PlayerResourcePorts.Factory factory() throws ReflectiveOperationException {
        Class<?> helper = Class.forName("com.Polarice3.Goety.utils.SEHelper");
        Class<?> finder = Class.forName("com.Polarice3.Goety.utils.TotemFinder");
        Class<?> totem = Class.forName("com.Polarice3.Goety.api.items.magic.ITotem");
        Object config = Class.forName("com.Polarice3.Goety.config.MainConfig").getField("MaxArcaSouls").get(null);
        Method[] methods = {helper.getMethod("getSEActive", Player.class), helper.getMethod("getSESouls", Player.class),
            helper.getMethod("setSESouls", Player.class, int.class), finder.getMethod("FindTotem", Player.class),
            totem.getMethod("currentSouls", ItemStack.class), totem.getMethod("maximumSouls", ItemStack.class),
            totem.getMethod("setSoulsAmount", ItemStack.class, int.class), helper.getMethod("sendSEUpdatePacket", Player.class)};
        return player -> new GoetySoulPort(player, config, methods);
    }
    private boolean arca() { return Boolean.TRUE.equals(PlayerResourcePorts.invoke(active, null, player)); }
    private ItemStack totem() { return (ItemStack) PlayerResourcePorts.invoke(findTotem, null, player); }
    @Override public ResourceLocation unit() { return ResourceExchangeConversions.GOETY_SOULS; }
    @Override public long amount() {
        return Math.max(0, ((Number) (arca() ? PlayerResourcePorts.invoke(souls, null, player)
                : PlayerResourcePorts.invoke(totemSouls, null, totem()))).longValue());
    }
    @Override public long capacity() {
        if (arca()) return Math.max(0, ((Number) ((net.minecraftforge.common.ForgeConfigSpec.ConfigValue<?>) config).get()).longValue());
        ItemStack stack = totem();
        return stack.isEmpty() ? 0 : Math.max(0, ((Number) PlayerResourcePorts.invoke(totemMaximum, null, stack)).longValue());
    }
    @Override public boolean canReceive() { return capacity() > 0; }
    @Override public boolean canExtract() { return false; }
    @Override public long extract(long amount, boolean simulate) { return 0; }
    @Override public long receive(long requested, boolean simulate) {
        long before = amount(), accepted = Math.max(0, Math.min(requested, Math.min(Integer.MAX_VALUE, capacity()) - before));
        if (simulate || accepted == 0) return accepted;
        if (arca()) {
            PlayerResourcePorts.invoke(setSouls, null, player, (int) (before + accepted));
            try { PlayerResourcePorts.invoke(sync, null, player); }
            catch (RuntimeException failure) { PlayerResourcePorts.warn("goety-sync", failure); }
        } else {
            PlayerResourcePorts.invoke(setTotem, null, totem(), (int) (before + accepted));
            player.getInventory().setChanged();
            try { player.containerMenu.broadcastChanges(); }
            catch (RuntimeException failure) { PlayerResourcePorts.warn("goety-totem-sync", failure); }
        }
        return Math.max(0, Math.min(accepted, amount() - before));
    }
}
