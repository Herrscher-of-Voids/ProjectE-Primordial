/**
 * 【变更记录 #1】2026-10-14 03:09:40（第 1 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：指数费用、几何级数批量升级、有限世界数值转换；超余额指数不展开，不设置技能等级封顶。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
/**
 * 【变更记录 #2】2026-10-14 03:42:00（第 2 次备注 · 1.2.0-linear-equipment-cost）
 * - 删除：使用费和升级费按 2 的幂次增长、几何级数批量结算的路径。
 * - 新增：使用费按“基础费用 × 启用等级”计算，批量购买按“基础价格 × 购买等级数”计算；保留等级对能力倍率的指数增长。
 * - 修改：大整数费用直接乘法结算，不再展开等级对应的 2 次幂；价格显示同步使用线性公式。
 * - 用途：避免高等级技能因指数费用无法使用，同时保留用户要求的技能效果指数成长。
 */
package com.primordial.equipment;
import java.math.BigInteger;
/** 等级保留为大整数；能力倍率仍指数增长，费用只做有界的大整数乘法。 */
public final class BlackholeScaling {
    private BlackholeScaling() { }
    public static BigInteger boundedPower(BigInteger level,BigInteger ceiling){
        if(level.signum()<=0||ceiling.signum()<=0)return BigInteger.ZERO;
        BigInteger exponent=level.subtract(BigInteger.ONE);
        if(exponent.compareTo(BigInteger.valueOf(ceiling.bitLength()))>=0)return ceiling;
        return BigInteger.ONE.shiftLeft(exponent.intValue()).min(ceiling);
    }
    public static double power(BigInteger level){
        if(level.signum()<=0)return 0;
        return Math.scalb(1.0,level.subtract(BigInteger.ONE).min(BigInteger.valueOf(1020)).intValue());
    }
    public static float finiteFloat(double value){return (float)Math.min(Float.MAX_VALUE,Math.max(0,value));}
    public static int radius(BigInteger level,int base){return boundedPower(level,BigInteger.valueOf(60000000)).multiply(BigInteger.valueOf(base)).min(BigInteger.valueOf(60000000)).intValue();}
    public static BigInteger usage(int base,BigInteger level,BigInteger budget){
        if(level.signum()<=0||budget.signum()<=0)return null;
        BigInteger cost=BigInteger.valueOf(base).multiply(level);
        return cost.compareTo(budget)<=0?cost:null;
    }
    public static BigInteger purchase(int base,BigInteger owned,BigInteger count,BigInteger budget){
        if(owned.signum()<0||count.signum()<=0||budget.signum()<=0)return null;
        BigInteger cost=BigInteger.valueOf(base).multiply(count);
        return cost.compareTo(budget)<=0?cost:null;
    }
    public static String purchaseText(int base,BigInteger owned,BigInteger count){
        if(count.signum()<=0)return "0";
        return BigInteger.valueOf(base).multiply(count).toString();
    }
    // 保留该工具供其它有限时长能力使用；其时长随等级线性增长并在引擎时钟上饱和。
    public static long deadline(long now,int baseTicks,BigInteger level){
        BigInteger limit=BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.valueOf(now));
        BigInteger ticks=BigInteger.valueOf(baseTicks).multiply(level).min(limit);
        return BigInteger.valueOf(now).add(ticks).longValue();
    }
}
