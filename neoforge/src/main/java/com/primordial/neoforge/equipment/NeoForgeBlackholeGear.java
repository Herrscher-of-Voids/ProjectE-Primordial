package com.primordial.neoforge.equipment;

import com.primordial.neoforge.NeoForgeModRegistries;

import java.math.BigInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

public final class NeoForgeBlackholeGear {
    private NeoForgeBlackholeGear() { }

    public static boolean isGear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return NeoForgeModRegistries.BLACKHOLE_GEAR.values().stream().anyMatch(entry -> entry.get() == item);
    }

    public static boolean isArmor(ItemStack stack) {
        if (!isGear(stack)) return false;
        return stack.getItem() == NeoForgeModRegistries.BLACKHOLE_HELMET.get()
                || stack.getItem() == NeoForgeModRegistries.BLACKHOLE_CHESTPLATE.get()
                || stack.getItem() == NeoForgeModRegistries.BLACKHOLE_LEGGINGS.get()
                || stack.getItem() == NeoForgeModRegistries.BLACKHOLE_BOOTS.get();
    }

    public static boolean fullSet(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(NeoForgeModRegistries.BLACKHOLE_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(NeoForgeModRegistries.BLACKHOLE_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(NeoForgeModRegistries.BLACKHOLE_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(NeoForgeModRegistries.BLACKHOLE_BOOTS.get());
    }

    public static boolean pay(ServerPlayer player, int emc) {
        return pay(player, BigInteger.valueOf(emc));
    }
    public static boolean pay(ServerPlayer player, BigInteger emc) {
        return player != null && emc != null && emc.signum() > 0 && player.server.isSameThread()
                && com.primordial.neoforge.emc.NeoForgeEmcTransactions.purchase(player.server, player.getUUID(), com.primordial.service.EmcAmount.of(emc), 1, units -> 1)==1;
    }
    public static void refund(ServerPlayer player, BigInteger amount) {
        if (amount.signum()>0) com.primordial.neoforge.emc.NeoForgeEmcCredits.credit(player.server, player.getUUID(), com.primordial.service.EmcAmount.of(amount));
    }
    private static BigInteger balance(ServerPlayer player) {
        try {
            var data=com.primordial.neoforge.emc.NeoForgeEmcSavedData.get(player.server);
            if (!data.canCredit(player.getUUID())) return BigInteger.ZERO;
            return com.primordial.neoforge.emc.NeoForgeEmcCredits.onlineBalance(player).add(data.pending(player.getUUID()).floor()).subtract(data.debit(player.getUUID())).max(BigInteger.ZERO);
        } catch (RuntimeException | LinkageError unavailable) { return BigInteger.ZERO; }
    }
    public static BigInteger armorLevel(ServerPlayer player, NeoForgeBlackholeModules.Kind kind) {
        BigInteger result=BigInteger.ZERO;
        for(ItemStack stack:player.getArmorSlots())if(isArmor(stack))result=result.add(NeoForgeBlackholeModules.level(stack,kind));
        return result;
    }
    public static BigInteger available(ServerPlayer player){return balance(player);}
    public static BigInteger charge(ServerPlayer player,NeoForgeBlackholeModules.Kind kind,BigInteger level){
        if(player==null||level.signum()<=0||!player.server.isSameThread())return BigInteger.ZERO;
        BigInteger fee=NeoForgeBlackholeScaling.usage(kind.emc,level,available(player));
        return fee!=null&&pay(player,fee)?fee:BigInteger.ZERO;
    }
    public static double armorPower(ServerPlayer player,NeoForgeBlackholeModules.Kind kind){
        double power=0;for(ItemStack stack:player.getArmorSlots())if(isArmor(stack))power+=NeoForgeBlackholeScaling.power(NeoForgeBlackholeModules.level(stack,kind));return power;
    }
    public static boolean payArmor(ServerPlayer player,NeoForgeBlackholeModules.Kind kind){
        if(player==null||!player.server.isSameThread()||armorLevel(player,kind).signum()==0)return false;
        BigInteger balance=available(player),total=BigInteger.ZERO;
        for(ItemStack stack:player.getArmorSlots())if(isArmor(stack)){
            BigInteger level=NeoForgeBlackholeModules.level(stack,kind);if(level.signum()==0)continue;
            BigInteger fee=NeoForgeBlackholeScaling.usage(kind.emc,level,balance.subtract(total));
            if(fee==null)return false;total=total.add(fee);
        }
        return total.signum()>0&&pay(player,total);
    }
    public static boolean payLevel(ServerPlayer player, NeoForgeBlackholeModules.Kind kind, BigInteger level) {
        return charge(player,kind,level).signum()>0;
    }
}
