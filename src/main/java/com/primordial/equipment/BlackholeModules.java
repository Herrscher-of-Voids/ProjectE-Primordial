/**
 * 【文件职责】黑洞装备的实体模块、安装规则和有界 NBT 存储。
 * 【变更记录 #1】2026-10-14 00:45:03（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增七类模块、双槽安装、同种与互斥冲突检查、仅接受注册模块的 NBT 读取。
 * - 修改：模块清单直接保存在装备物品上，未安装不触发额外能力。
 * - 用途：护甲和十件工具共享菜单及服务端能力检查；畸形或旧版模块数据不产生效果。
 */
/**
 * 【变更记录 #2】2026-10-14 01:17:24（第 2 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：实体模块安装流程。
 * - 新增：装备内 EMC 解锁、开关与服务端验证。
 * - 修改：购买状态和启用状态分别存入 NBT；兼容已安装旧模块，保留两项同时启用及飞行/迅捷互斥。
 * - 用途：落实用户修正，功能属于装备且不再作为物品流通。
 */
/**
 * 【变更记录 #3】2026-10-14 01:38:39（第 3 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：23 种功能、可重复购买的大整数等级、独立启用数值和旧单级数据迁移；移除修补及双槽/重复/飞行迅捷互斥限制。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #4】2026-10-14 02:34:14（第 4 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：用户确认的每件装备招牌能力内置1级，界面可关闭/升级；扩展到32种适用能力，保留通用模块购买。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #5】2026-10-14 03:09:40（第 5 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：拳剑自带攻击强化1级、快速拉弓新功能、批量升级按所购每级的指数价格求和。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.equipment;

import java.math.BigInteger;
import java.util.EnumSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class BlackholeModules {
    public static final String KEY = "BlackholeFeatures";
    public enum Scope { ARMOR, MINING, WEAPON, BOW, HAND, SIGNATURE }
    public enum Kind {
        GUARD("guard",16,65536,Scope.ARMOR), SWIFT("swift",4,65536,Scope.ARMOR),
        FLIGHT("flight",1,262144,Scope.ARMOR), REACH("reach",5,131072,Scope.HAND),
        FIREWARD("fireward",6,65536,Scope.ARMOR), ASSAULT("assault",12,65536,Scope.WEAPON),
        NIGHT_VISION("night_vision",2,65536,Scope.ARMOR), BREATH("breath",2,65536,Scope.ARMOR),
        HEAL("heal",16,131072,Scope.ARMOR), FEED("feed",8,65536,Scope.ARMOR),
        KNOCKBACK("knockback",4,65536,Scope.ARMOR), FALL("fall",8,65536,Scope.ARMOR),
        AREA("area",8,131072,Scope.MINING), VEIN("vein",8,131072,Scope.MINING),
        SMELT("smelt",8,65536,Scope.MINING), SILK("silk",8,131072,Scope.MINING),
        FORTUNE("fortune",16,131072,Scope.MINING), MAGNET("magnet",4,65536,Scope.HAND),
        SWEEP("sweep",12,131072,Scope.WEAPON), VAMPIRISM("vampirism",16,131072,Scope.WEAPON),
        INFINITY("infinity",8,262144,Scope.BOW), ARROW_SPEED("arrow_speed",8,65536,Scope.BOW),
        RAPID_DRAW("rapid_draw",8,65536,Scope.BOW), PIERCE("pierce",12,131072,Scope.BOW), SINGULARITY("singularity",1024,262144,Scope.BOW),
        INSIGHT("insight",8,131072,Scope.SIGNATURE), UNDYING("undying",4096,262144,Scope.SIGNATURE),
        STASIS("stasis",16,131072,Scope.SIGNATURE), DASH("dash",256,131072,Scope.SIGNATURE),
        EXECUTE("execute",64,131072,Scope.SIGNATURE), REAP("reap",16,131072,Scope.SIGNATURE),
        DISPEL("dispel",32,131072,Scope.SIGNATURE), REND("rend",24,131072,Scope.SIGNATURE);
        public final String id; public final int emc, price; public final Scope scope;
        Kind(String id,int emc,int price,Scope scope) { this.id=id;this.emc=emc;this.price=price;this.scope=scope; }
        public static Kind byId(String id) { for (Kind k:values()) if(k.id.equals(id)) return k; return null; }
    }
    public enum Result { OK, INVALID, NO_EMC, STALE, ABOVE_OWNED }
    private BlackholeModules() { }
    public static boolean supports(ItemStack gear,Kind kind) {
        if (kind==null || !BlackholeGear.isGear(gear)) return false;
        boolean armor=BlackholeGear.isArmor(gear), bow=gear.getItem() instanceof BlackholeBowItem;
        return switch(kind.scope) {
            case SIGNATURE -> innate(gear,kind);
            case ARMOR -> armor;
            case BOW -> bow;
            case HAND -> !armor;
            case WEAPON -> !armor && (!bow || kind==Kind.ASSAULT);
            case MINING -> !armor && !bow && !(gear.getItem() instanceof BlackholeTools.Sword);
        };
    }
    // 招牌能力随装备内置 1 级；可关闭，额外等级仍按同一购买/启用协议处理。
    public static boolean innate(ItemStack gear,Kind kind){
        return switch(kind){
            case INSIGHT->type(gear,"helmet");case UNDYING->type(gear,"chestplate");case STASIS->type(gear,"leggings");case DASH->type(gear,"boots");
            case ASSAULT->type(gear,"katar");case EXECUTE->type(gear,"sword");case REAP->type(gear,"hoe");case DISPEL->type(gear,"shears");case REND->type(gear,"katar");
            case AREA->type(gear,"pick")||type(gear,"shovel")||type(gear,"hammer")||type(gear,"morning_star");
            case VEIN->type(gear,"pick")||type(gear,"axe");case SINGULARITY->type(gear,"bow");default->false;
        };
    }
    private static boolean type(ItemStack gear,String name){
        var item=com.primordial.registry.ModItems.BLACKHOLE_GEAR.get(name);return item.isPresent()&&gear.is(item.get());
    }
    // 与 EMC 协议同样限制输入长度，避免畸形网络/NBT 消耗内存；不设置游戏等级上限。
    public static BigInteger parse(String text) {
        if(text==null || text.isEmpty() || text.length()>2048 || !text.matches("[0-9]+")) return null;
        return new BigInteger(text);
    }
    public static BigInteger owned(ItemStack gear,Kind kind) {
        if(!supports(gear,kind))return BigInteger.ZERO;
        BigInteger base=innate(gear,kind)?BigInteger.ONE:BigInteger.ZERO;
        if(!gear.hasTag())return base;
        CompoundTag root=gear.getTag().getCompound(KEY);
        if(root.contains("Levels",Tag.TAG_COMPOUND)) return number(root.getCompound("Levels").getString(kind.id)).max(base);
        if(root.getCompound("Purchased").getBoolean(kind.id)) return BigInteger.ONE;
        ListTag old=gear.getTag().getList("BlackholeModules",Tag.TAG_COMPOUND);
        for(int i=0;i<Math.min(2,old.size());i++) {
            CompoundTag entry=old.getCompound(i);
            if(entry.getByte("Count")>0 && entry.getString("id").equals("primordial:blackhole_module_"+kind.id)) return BigInteger.ONE;
        }
        return base;
    }
    public static BigInteger level(ItemStack gear,Kind kind) {
        BigInteger bought=owned(gear,kind);
        if(bought.signum()==0) return bought;
        if(!gear.hasTag())return innate(gear,kind)?BigInteger.ONE:BigInteger.ZERO;
        CompoundTag root=gear.getTag().getCompound(KEY);
        if(root.contains("Levels",Tag.TAG_COMPOUND)){
            CompoundTag active=root.getCompound("Active");
            return !active.contains(kind.id)&&innate(gear,kind)?BigInteger.ONE:number(active.getString(kind.id)).min(bought);
        }
        if(innate(gear,kind)&&!root.getCompound("Purchased").getBoolean(kind.id))return BigInteger.ONE;
        return root.contains("Purchased",Tag.TAG_COMPOUND) && !root.getCompound("Enabled").getBoolean(kind.id) ? BigInteger.ZERO:bought;
    }
    private static BigInteger number(String text) { BigInteger value=parse(text);return value==null?BigInteger.ZERO:value; }
    public static boolean purchased(ItemStack gear,Kind kind) {return owned(gear,kind).signum()>0;}
    public static boolean installed(ItemStack gear,Kind kind) {return level(gear,kind).signum()>0;}
    public static EnumSet<Kind> active(ItemStack gear) {EnumSet<Kind> set=EnumSet.noneOf(Kind.class);for(Kind k:Kind.values())if(installed(gear,k))set.add(k);return set;}
    private static CompoundTag state(ItemStack gear) {
        CompoundTag root=new CompoundTag(), bought=new CompoundTag(), active=new CompoundTag();
        for(Kind k:Kind.values()) {
            BigInteger b=owned(gear,k), a=level(gear,k);
            if(b.signum()>0)bought.putString(k.id,b.toString());
            if(b.signum()>0)active.putString(k.id,a.toString());
        }
        root.put("Levels",bought);root.put("Active",active);return root;
    }
    private static void save(ItemStack gear,CompoundTag root) {gear.getOrCreateTag().put(KEY,root);gear.getTag().remove("BlackholeModules");}
    public static String priceText(Kind kind,BigInteger before,BigInteger count) {return BlackholeScaling.purchaseText(kind.price,before,count);}
    // expected 防止重复网络包将同一批购买再扣一次；每批扣款与装备写入在同一主线程完成。
    public static Result buy(ServerPlayer player,ItemStack gear,Kind kind,BigInteger count,BigInteger expected) {
        if(player==null||!player.server.isSameThread()||!supports(gear,kind)||count==null||count.signum()<=0)return Result.INVALID;
        BigInteger before=owned(gear,kind), after=before.add(count);
        if(!before.equals(expected))return Result.STALE;
        if(after.toString().length()>2048)return Result.INVALID;
        CompoundTag next=state(gear);
        BigInteger fee=BlackholeScaling.purchase(kind.price,before,count,BlackholeGear.available(player));
        if(fee==null||!BlackholeGear.pay(player,fee))return Result.NO_EMC;
        next.getCompound("Levels").putString(kind.id,after.toString());
        // 购买不会擅自提高现有启用强度；首次购买默认启用 1 级。
        if(before.signum()==0)next.getCompound("Active").putString(kind.id,"1");
        save(gear,next);return Result.OK;
    }
    public static Result setLevel(ItemStack gear,Kind kind,BigInteger level) {
        if(!supports(gear,kind)||level==null||level.signum()<0)return Result.INVALID;
        if(level.compareTo(owned(gear,kind))>0)return Result.ABOVE_OWNED;
        CompoundTag next=state(gear);next.getCompound("Active").putString(kind.id,level.toString());
        save(gear,next);return Result.OK;
    }
    public static double magnitude(BigInteger level) {return BlackholeScaling.power(level);}
}
