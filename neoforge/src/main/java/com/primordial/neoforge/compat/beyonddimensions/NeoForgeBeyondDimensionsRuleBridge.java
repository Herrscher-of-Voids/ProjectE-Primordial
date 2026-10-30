package com.primordial.neoforge.compat.beyonddimensions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** BD 0.8.2 plans rules against temporary purchasing power and commits only after exact EMC authorization. */
public final class NeoForgeBeyondDimensionsRuleBridge {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static Api api;

    private NeoForgeBeyondDimensionsRuleBridge() {}

    public static long extract(Object transaction, Object requested, long wanted, boolean simulate) {
        Api methods = api(transaction);
        Object storage = get(methods.storage, transaction);
        var funding = NeoForgeBeyondDimensionsStorageAdapter.ruleFunding(storage, requested);
        if (funding == null) return number(call(methods.extract, transaction, requested, wanted));
        Context previous = ACTIVE.get();
        Context context = new Context(storage, requested, funding.available());
        try {
            // Nested simulated rule queries must read the actual physical stock here.
            ACTIVE.remove();
            long physical = number(call(methods.stored, storage, requested));
            ACTIVE.set(context);
            long planned = number(call(methods.extract, transaction, requested, wanted));
            if (simulate || planned <= 0) return planned;
            Object delta = ((Map<?, ?>) get(methods.changes, transaction)).get(requested);
            long change = delta == null ? 0 : number(delta);
            long missing = Math.max(0, -change - physical);
            if (missing == 0) return planned;
            context.planning = false;
            Map<Object, Long> physicalSnapshot = new LinkedHashMap<>();
            for (var entry : ((Map<?, ?>) get(methods.changes, transaction)).entrySet()) {
                if (number(entry.getValue()) < 0) physicalSnapshot.put(entry.getKey(), number(call(methods.stored, storage, entry.getKey())));
            }
            long paid = NeoForgeBeyondDimensionsStorageAdapter.payRule(funding, storage, missing, authorized -> {
                if (authorized != missing) return 0;
                for (var entry : physicalSnapshot.entrySet())
                    if (number(call(methods.stored, storage, entry.getKey())) != entry.getValue()) return 0;
                context.prepaid = authorized;
                call(methods.commit, transaction);
                if (context.prepaid != 0) throw new IllegalStateException("BD did not consume its authorized virtual stock");
                return authorized;
            });
            if (paid == missing) return planned;
            // UnifiedStorage will still call commit after this redirect returns; rejected payment must leave it empty.
            ((Map<?, ?>) get(methods.changes, transaction)).clear();
            ((Collection<?>) get(methods.journal, transaction)).clear();
            ((Collection<?>) get(methods.effects, transaction)).clear();
            return 0;
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    public static long storedAmount(Object storage, Object requested, long actual) {
        Context context = ACTIVE.get();
        if (context == null || !context.planning || context.storage != storage || !context.key.equals(requested)) return actual;
        return context.available > Long.MAX_VALUE - actual ? Long.MAX_VALUE : actual + context.available;
    }

    static boolean committing(Object storage) {
        Context context = ACTIVE.get();
        return context != null && !context.planning && context.storage == storage && context.prepaid > 0;
    }

    static long consume(Object requested, long missing, boolean simulate) {
        Context context = ACTIVE.get();
        if (simulate || context == null || !context.key.equals(requested) || missing <= 0) return 0;
        long paid = Math.min(missing, context.prepaid);
        context.prepaid -= paid;
        return paid;
    }

    private static synchronized Api api(Object transaction) {
        if (api != null) return api;
        try {
            Class<?> type = transaction.getClass();
            Class<?> key = Class.forName("com.wintercogs.beyonddimensions.api.storage.key.IStackKey");
            Field storage = field(type, "storage");
            api = new Api(storage, field(type, "changes"), field(type, "journal"), field(type, "effects"),
                    method(type, "extract", key, long.class), method(type, "commit"),
                    method(storage.getType(), "storedAmount", key));
            return api;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("BD 0.8.2 rule transaction API changed", failure); }
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static Object get(Field field, Object target) {
        try { return field.get(target); }
        catch (IllegalAccessException failure) { throw new IllegalStateException("Read BD rule transaction", failure); }
    }

    private static long number(Object value) { return ((Number) value).longValue(); }

    private static Object call(Method method, Object target, Object... arguments) {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("BD rule transaction failed", cause);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Call BD rule transaction", failure); }
    }

    private static final class Context {
        final Object storage, key;
        final long available;
        boolean planning = true;
        long prepaid;
        Context(Object storage, Object key, long available) { this.storage = storage; this.key = key; this.available = available; }
    }

    private record Api(Field storage, Field changes, Field journal, Field effects, Method extract, Method commit, Method stored) {}
}
