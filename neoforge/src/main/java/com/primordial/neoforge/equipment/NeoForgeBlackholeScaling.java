package com.primordial.neoforge.equipment;
import java.math.BigInteger;
/** 等级保留为大整数；能力倍率仍指数增长，费用只做有界的大整数乘法。 */
public final class NeoForgeBlackholeScaling {
    private NeoForgeBlackholeScaling() { }
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
