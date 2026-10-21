/**
 * 【文件职责】黑洞装备的物品身份、模块启用条件和统一 EMC 付款。
 * 【变更记录 #1】2026-10-14 00:45:03（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增装备注册身份检查、全套判定和服务端主线程原子扣费入口。
 * - 修改：模块每次使用从玩家个人 ProjectE 余额支付，失败立即拒绝额外效果。
 * - 用途：装备与原有转化交易共用 EmcService；只识别本模组的黑洞装备。
 */
/**
 * 【变更记录 #2】2026-10-14 01:38:39（第 2 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：任意大整数购买和使用费，护甲同类等级跨四件累加；沿用统一 EMC 服务。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #3】2026-10-14 03:09:40（第 3 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：使用费按单件启用等级指数结算；四件护甲各自计算倍率后相加，一次结算避免把四件等级误合为一个指数。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;

import com.primordial.registry.ModItems;
import com.primordial.service.EmcService;
import java.math.BigInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

public final class BlackholeGear {
    private BlackholeGear() { }

    public static boolean isGear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return ModItems.BLACKHOLE_GEAR.values().stream().anyMatch(entry -> entry.isPresent() && entry.get() == item);
    }

    public static boolean isArmor(ItemStack stack) {
        if (!isGear(stack)) return false;
        return stack.getItem() == ModItems.BLACKHOLE_HELMET.get()
                || stack.getItem() == ModItems.BLACKHOLE_CHESTPLATE.get()
                || stack.getItem() == ModItems.BLACKHOLE_LEGGINGS.get()
                || stack.getItem() == ModItems.BLACKHOLE_BOOTS.get();
    }

    public static boolean fullSet(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.BLACKHOLE_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.BLACKHOLE_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.BLACKHOLE_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.BLACKHOLE_BOOTS.get());
    }

    public static boolean pay(ServerPlayer player, int emc) {
        return pay(player, BigInteger.valueOf(emc));
    }
    public static boolean pay(ServerPlayer player, BigInteger emc) {
        return player != null && emc != null && emc.signum() > 0 && player.server.isSameThread()
                && EmcService.spend(player.server, player.getUUID(), emc);
    }
    public static BigInteger armorLevel(ServerPlayer player, BlackholeModules.Kind kind) {
        BigInteger result=BigInteger.ZERO;
        for(ItemStack stack:player.getArmorSlots())if(isArmor(stack))result=result.add(BlackholeModules.level(stack,kind));
        return result;
    }
    public static BigInteger available(ServerPlayer player){return EmcService.balance(player.server,player.getUUID());}
    public static BigInteger charge(ServerPlayer player,BlackholeModules.Kind kind,BigInteger level){
        if(player==null||level.signum()<=0||!player.server.isSameThread())return BigInteger.ZERO;
        BigInteger fee=BlackholeScaling.usage(kind.emc,level,available(player));
        return fee!=null&&pay(player,fee)?fee:BigInteger.ZERO;
    }
    public static double armorPower(ServerPlayer player,BlackholeModules.Kind kind){
        double power=0;for(ItemStack stack:player.getArmorSlots())if(isArmor(stack))power+=BlackholeScaling.power(BlackholeModules.level(stack,kind));return power;
    }
    public static boolean payArmor(ServerPlayer player,BlackholeModules.Kind kind){
        if(player==null||!player.server.isSameThread()||armorLevel(player,kind).signum()==0)return false;
        BigInteger balance=available(player),total=BigInteger.ZERO;
        for(ItemStack stack:player.getArmorSlots())if(isArmor(stack)){
            BigInteger level=BlackholeModules.level(stack,kind);if(level.signum()==0)continue;
            BigInteger fee=BlackholeScaling.usage(kind.emc,level,balance.subtract(total));
            if(fee==null)return false;total=total.add(fee);
        }
        return total.signum()>0&&pay(player,total);
    }
    public static boolean payLevel(ServerPlayer player, BlackholeModules.Kind kind, BigInteger level) {
        return charge(player,kind,level).signum()>0;
    }
}
