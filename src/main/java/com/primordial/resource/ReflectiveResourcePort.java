/**
 * 【文件职责】以受限反射封装不同模组资源储存的读写方法。
 *
 * 变更记录 #1（2026-09-16）：新增可选 API 隔离实现。只调用无参数数量/容量方法和单个
 * 数值参数的增减方法；找不到完整读写链时端口不会暴露。方法查找按类、方法名和参数类型
 * 缓存，避免服务器 tick 期间重复反射扫描。
 */
/**
 * 【变更记录 #2】2026-09-16 13:26:00（第 2 次备注 · 1.0.0-acceleration-safety）
 * - 删除：自定义容量读取异常时返回无限容量的回退。
 * - 新增：容量供应器异常返回零的关闭保护。
 * - 修改：真实存量仍可回收，但读取失败不接受额外注入。
 * - 用途：气动工艺等具备过量风险的资源接口不得因反射失败被灌满原生数值上限。
 */
package com.primordial.resource;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import net.minecraft.resources.ResourceLocation;

/** 适配常见“get + add/remove”资源接口的安全端口。 */
final class ReflectiveResourcePort implements EmcResourcePort {
    private static final Map<Lookup, Method> METHODS = new ConcurrentHashMap<>();
    private final Object target;
    private final ResourceLocation unit;
    private final String key;
    private final String[] amountNames;
    private final String[] capacityNames;
    private final String[] receiveNames;
    private final String[] extractNames;
    private final boolean extractWithNegativeAmount;
    private final LongSupplier capacitySupplier;

    ReflectiveResourcePort(Object target, ResourceLocation unit, String key,
            String[] amountNames, String[] capacityNames, String[] receiveNames, String[] extractNames) {
        this(target, unit, key, amountNames, capacityNames, receiveNames, extractNames, false);
    }

    ReflectiveResourcePort(Object target, ResourceLocation unit, String key,
            String[] amountNames, String[] capacityNames, String[] receiveNames, String[] extractNames,
            boolean extractWithNegativeAmount) {
        this(target, unit, key, amountNames, capacityNames, receiveNames, extractNames,
                extractWithNegativeAmount, null);
    }

    ReflectiveResourcePort(Object target, ResourceLocation unit, String key,
            String[] amountNames, String[] capacityNames, String[] receiveNames, String[] extractNames,
            boolean extractWithNegativeAmount, LongSupplier capacitySupplier) {
        this.target = target;
        this.unit = unit;
        this.key = key;
        this.amountNames = amountNames;
        this.capacityNames = capacityNames;
        this.receiveNames = receiveNames;
        this.extractNames = extractNames;
        this.extractWithNegativeAmount = extractWithNegativeAmount;
        this.capacitySupplier = capacitySupplier;
    }

    @Override public ResourceLocation unit() { return unit; }
    @Override public String key() { return key; }

    @Override public long amount() { return readNoArg(amountNames, 0, true); }

    @Override public long capacity() {
        long value = capacitySupplier == null ? readNoArg(capacityNames, Long.MAX_VALUE, false)
                : safeCapacity(capacitySupplier);
        return value < 0 ? Long.MAX_VALUE : Math.max(value, amount());
    }

    private static long safeCapacity(LongSupplier supplier) {
        try {
            return Math.max(0L, supplier.getAsLong());
        } catch (RuntimeException failure) {
            return 0;
        }
    }

    @Override public boolean canReceive() {
        return hasNumericMethod(receiveNames) && amount() < capacity();
    }

    @Override public boolean canExtract() {
        return hasNumericMethod(extractNames) && amount() > 0;
    }

    @Override public long receive(long requested, boolean simulate) {
        if (requested <= 0 || !hasNumericMethod(receiveNames)) return 0;
        long before = amount();
        long available = Math.max(0, capacity() - before);
        long ask = Math.min(requested, available);
        if (ask <= 0) return 0;
        if (simulate) return ask;
        return invokeDelta(receiveNames, ask, before, true);
    }

    @Override public long extract(long requested, boolean simulate) {
        if (requested <= 0 || !hasNumericMethod(extractNames)) return 0;
        long before = amount();
        long ask = Math.min(requested, before);
        if (ask <= 0) return 0;
        if (simulate) return ask;
        return invokeDelta(extractNames, ask, before, false);
    }

    private long invokeDelta(String[] names, long ask, long before, boolean receiving) {
        for (String name : names) {
            Method method = method(target.getClass(), name, true);
            if (method == null) continue;
            try {
                long signedAsk = !receiving && extractWithNegativeAmount ? -ask : ask;
                Object[] arguments = method.getParameterCount() == 2
                        ? new Object[]{convert(signedAsk, method.getParameterTypes()[0]), false}
                        : new Object[]{convert(signedAsk, method.getParameterTypes()[0])};
                Object result = method.invoke(target, arguments);
                if (result instanceof Number number) {
                    long reported = Math.max(0, number.longValue());
                    // 大多数 API 返回已移动量；如果返回剩余量，则以状态差为准，避免重复结算。
                    long after = amount();
                    long delta = receiving ? Math.max(0, after - before) : Math.max(0, before - after);
                    return Math.min(ask, delta > 0 ? delta : reported);
                }
                long after = amount();
                return Math.min(ask, receiving ? Math.max(0, after - before) : Math.max(0, before - after));
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 当前候选方法签名不兼容时继续尝试下一个公开 API 名称。
            }
        }
        return 0;
    }

    private long readNoArg(String[] names, long fallback, boolean clamp) {
        for (String name : names) {
            Method method = method(target.getClass(), name, false);
            if (method == null) continue;
            try {
                Object value = method.invoke(target);
                if (value instanceof Number number) {
                    double d = number.doubleValue();
                    if (!Double.isFinite(d)) continue;
                    long result = d <= 0 ? 0 : d >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) Math.floor(d);
                    return clamp ? Math.max(0, result) : result;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return fallback;
    }

    private boolean hasNumericMethod(String[] names) {
        for (String name : names) if (method(target.getClass(), name, true) != null) return true;
        return false;
    }

    private static Object convert(long value, Class<?> type) {
        if (type == byte.class || type == Byte.class) return (byte) Math.min(Byte.MAX_VALUE, value);
        if (type == short.class || type == Short.class) return (short) Math.min(Short.MAX_VALUE, value);
        if (type == int.class || type == Integer.class) return (int) Math.min(Integer.MAX_VALUE, value);
        if (type == float.class || type == Float.class) return (float) value;
        if (type == double.class || type == Double.class) return (double) value;
        return value;
    }

    private static Method method(Class<?> type, String name, boolean numericArgument) {
        if (name == null || name.isEmpty()) return null;
        Lookup lookup = new Lookup(type, name, numericArgument);
        return METHODS.computeIfAbsent(lookup, key -> {
            for (Method candidate : key.type().getMethods()) {
                if (!candidate.getName().equals(key.name()) || !Modifier.isPublic(candidate.getModifiers())) continue;
                Class<?>[] parameters = candidate.getParameterTypes();
                if (numericArgument) {
                    boolean oneNumeric = parameters.length == 1 && numeric(parameters[0]);
                    boolean numericWithSimulation = parameters.length == 2 && numeric(parameters[0])
                            && (parameters[1] == boolean.class || parameters[1] == Boolean.class);
                    if (!oneNumeric && !numericWithSimulation) continue;
                } else if (parameters.length != 0) continue;
                try { candidate.setAccessible(true); } catch (RuntimeException ignored) { }
                return candidate;
            }
            return null;
        });
    }

    private static boolean numeric(Class<?> type) {
        return type == byte.class || type == Byte.class || type == short.class || type == Short.class
                || type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == float.class || type == Float.class || type == double.class || type == Double.class;
    }

    private record Lookup(Class<?> type, String name, boolean numericArgument) { }
}
