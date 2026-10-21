/**
 * 【文件职责】将明确标注单位的原生能量报价统一为 FE/EMC；不直接操作第三方机器。
 * 【变更记录 #1】2026-09-15 14:08:10（第 1 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：FE/RF 等价、MEK J 与 AE 原生配置读取、精确比例覆盖和可注册单位提供器。
 * - 修改：未知单位返回无价；已暴露 FE 的机器只用 FE 报价，避免二次换算。
 * - 用途：后续专用能源/配方适配器共享换算，不把应力或热量猜成电能；可选 API 隔离在内部类。
 */
/**
 * 【变更记录 #2】2026-09-15 15:04:02（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：每次 FE 报价构造新分数及两个可选模组共用链接类的方式。
 * - 新增：比例快照缓存、无效配置防御、原生 API 异常一次告警和独立延迟加载类。
 * - 修改：按实际 10.4.16.80 API 取 1 FE 对应 J 再求倒数，AE2 按 AE/FE 比率精确相除。
 * - 用途：缺少 Mekanism/AE2 时 FE 仍可运行；FE/RF 不允许二次比例覆盖，热量/应力须显式注册报价。
 */
package com.primordial.energy;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.service.EmcAmount;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class EnergyConversions {
    public static final ResourceLocation FE = new ResourceLocation("forge", "fe");
    public static final ResourceLocation RF = new ResourceLocation("cofh", "rf");
    private static volatile BigInteger cachedFeRatio = BigInteger.valueOf(1000);
    private static volatile EmcAmount cachedFePrice = new EmcAmount(BigInteger.ONE, cachedFeRatio);
    private static volatile java.util.List<? extends String> cachedEntries = java.util.List.of();
    private static volatile Map<ResourceLocation, EmcAmount> overrides = Map.of();
    private static final java.util.Set<ResourceLocation> WARNED = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, Supplier<EmcAmount>> PROVIDERS = new ConcurrentHashMap<>();
    private EnergyConversions() {}

    public static void register(ResourceLocation unit, Supplier<EmcAmount> fePerUnit) {
        PROVIDERS.put(unit, fePerUnit);
    }

    /** 精确报价只读当前服务端设置，修改配置不改变已经积累的 EMC 找零。 */
    public static EmcAmount emcPerUnit(ResourceLocation unit) {
        if (FE.equals(unit) || RF.equals(unit)) {
            BigInteger ratio = PrimordialServerConfig.fePerEmc();
            synchronized (EnergyConversions.class) {
                if (!ratio.equals(cachedFeRatio)) {
                    cachedFePrice = new EmcAmount(BigInteger.ONE, ratio);
                    cachedFeRatio = ratio;
                }
                return cachedFePrice;
            }
        }
        EmcAmount fe = fePerUnit(unit);
        return new EmcAmount(fe.numerator(), fe.denominator().multiply(PrimordialServerConfig.fePerEmc()));
    }

    public static EmcAmount fePerUnit(ResourceLocation unit) {
        if (unit == null) return EmcAmount.ZERO;
        if (FE.equals(unit) || RF.equals(unit)) return EmcAmount.ONE;
        var entries = PrimordialServerConfig.nativeFeRatios();
        if (!entries.equals(cachedEntries)) {
            synchronized (EnergyConversions.class) {
                Map<ResourceLocation, EmcAmount> next = new java.util.HashMap<>();
                for (String entry : entries) if (validEntry(entry)) {
                    int equals = entry.indexOf('=');
                    next.put(new ResourceLocation(entry.substring(0, equals)), parseRatio(entry.substring(equals + 1)));
                }
                overrides = Map.copyOf(next);
                cachedEntries = java.util.List.copyOf(entries);
            }
        }
        EmcAmount configured = overrides.get(unit);
        if (configured != null) return configured;
        try {
        Supplier<EmcAmount> provider = PROVIDERS.get(unit);
        if (provider != null) return java.util.Objects.requireNonNullElse(provider.get(), EmcAmount.ZERO);
        ModList mods = ModList.get();
        if (mods != null) {
            if (unit.toString().equals("mekanism:joule") && mods.isLoaded("mekanism")) return MekanismUnits.joule();
            if (unit.toString().equals("ae2:ae") && mods.isLoaded("ae2")) return AeUnits.ae();
        }
        } catch (RuntimeException | LinkageError unavailable) {
            if (WARNED.add(unit)) com.primordial.PrimordialMod.LOGGER.warn("[Primordial] Native energy conversion unavailable for {}", unit, unavailable);
        }
        return EmcAmount.ZERO;
    }

    public static boolean validEntry(Object value) {
        if (!(value instanceof String text) || text.length() > 512) return false;
        String[] pair = text.split("=", -1);
        if (pair.length != 2 || ResourceLocation.tryParse(pair[0]) == null) return false;
        if (FE.toString().equals(pair[0]) || RF.toString().equals(pair[0])) return false;
        try { return !parseRatio(pair[1]).isZero(); } catch (RuntimeException invalid) { return false; }
    }

    private static EmcAmount parseRatio(String text) {
        String[] parts = text.split("/", -1);
        if (parts.length != 2 || !parts[0].matches("[0-9]{1,128}") || !parts[1].matches("[0-9]{1,128}"))
            throw new IllegalArgumentException("Expected positive numerator/denominator");
        return new EmcAmount(new BigInteger(parts[0]), new BigInteger(parts[1]));
    }

    private static EmcAmount decimal(String text) {
        BigDecimal value = new BigDecimal(text);
        return value.scale() >= 0 ? new EmcAmount(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
                : EmcAmount.of(value.toBigIntegerExact());
    }

    /** 依赖存在时才链接 API；这里只读取模组转换参数，EMC 算术始终使用整数分数。 */
    private static final class MekanismUnits {
        static EmcAmount joule() {
            EmcAmount joulesPerFe = decimal(mekanism.common.util.UnitDisplayUtils.EnergyUnit.FORGE_ENERGY
                    .convertFrom(mekanism.api.math.FloatingLong.ONE).toString());
            return joulesPerFe.isZero() ? EmcAmount.ZERO : new EmcAmount(joulesPerFe.denominator(), joulesPerFe.numerator());
        }
    }
    private static final class AeUnits {
        static EmcAmount ae() {
            EmcAmount ae = decimal(Double.toString(appeng.api.config.PowerUnits.AE.conversionRatio));
            EmcAmount fe = decimal(Double.toString(appeng.api.config.PowerUnits.FE.conversionRatio));
            return fe.isZero() ? EmcAmount.ZERO : new EmcAmount(ae.numerator().multiply(fe.denominator()), ae.denominator().multiply(fe.numerator()));
        }
    }
}
