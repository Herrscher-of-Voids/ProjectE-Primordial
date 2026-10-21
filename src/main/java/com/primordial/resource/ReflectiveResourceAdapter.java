/**
 * 【文件职责】为不稳定的第三方资源 API 提供统一的反射适配骨架。
 *
 * 变更记录 #1（2026-09-16）：新增按方块实体类名匹配、候选方法列表和端口身份生成逻辑。
 * 适配器只返回同时具备数量、容量和增减方法的端口；只读数据不会被误当作可交易储存。
 */
/**
 * 【变更记录 #2】2026-09-16（嵌套资源容器支持）
 * - 新增：createPort 与 invokeNoArg 受保护工具，允许模组把资源保存在方块实体返回的容器对象中。
 * - 修改：所有适配器仍在端口创建时验证读写能力；找不到完整 API 时返回空列表并继续使用其它代理。
 * - 用途：兼容自然灵气祭坛的 getAuraContainer 以及同类不把储存方法直接暴露在方块实体上的实现。
 */
/**
 * 【变更记录 #3】2026-09-16 13:42:00（第 3 次备注 · 1.0.0-acceleration-safety）
 * - 删除：无。
 * - 新增：允许 createPort 接收 LongSupplier 读取动态组合容量。
 * - 修改：气动机器的容积和安全压力由专用适配器组合，基础端口继续按实际增量结算。
 * - 用途：避免把某个原生 getter 一律解释成储存上限，同时保留其它资源适配器的既有读取规则。
 */
package com.primordial.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** 可由各模组适配器继承的反射资源发现器。 */
public abstract class ReflectiveResourceAdapter implements ResourcePortProvider {
    private final ResourceLocation unit;
    private final String[] amountNames;
    private final String[] capacityNames;
    private final String[] receiveNames;
    private final String[] extractNames;
    private final boolean extractWithNegativeAmount;

    protected ReflectiveResourceAdapter(ResourceLocation unit, String[] amountNames, String[] capacityNames,
            String[] receiveNames, String[] extractNames) {
        this(unit, amountNames, capacityNames, receiveNames, extractNames, false);
    }

    protected ReflectiveResourceAdapter(ResourceLocation unit, String[] amountNames, String[] capacityNames,
            String[] receiveNames, String[] extractNames, boolean extractWithNegativeAmount) {
        this.unit = unit;
        this.amountNames = amountNames;
        this.capacityNames = capacityNames;
        this.receiveNames = receiveNames;
        this.extractNames = extractNames;
        this.extractWithNegativeAmount = extractWithNegativeAmount;
    }

    protected abstract String id();
    protected abstract boolean matchesClass(String className);

    public Predicate<BlockEntity> matcher() { return machine -> machine != null && matchesClass(machine.getClass().getName()); }

    @Override public List<EmcResourcePort> find(BlockEntity machine, Direction side) {
        if (machine == null || machine.isRemoved() || !matchesClass(machine.getClass().getName())) return List.of();
        String suffix = side == null ? "internal" : side.getName().toLowerCase(Locale.ROOT);
        return createPort(machine, id() + "/" + machine.getClass().getName() + "/" + suffix + "/" + unit);
    }

    /**
     * 为指定对象创建资源端口。对象可以是方块实体本身，也可以是其公开返回的资源容器；
     * key 必须由调用方保持稳定，服务端会在每次任务执行时再次按 key 验证目标。
     */
    protected final List<EmcResourcePort> createPort(Object target, String key) {
        return createPort(target, key, null);
    }

    /** 创建端口并允许适配器按原生 API 组合计算容量，例如气动工艺的体积乘最大压力。 */
    protected final List<EmcResourcePort> createPort(Object target, String key, LongSupplier capacitySupplier) {
        if (target == null || key == null || key.isEmpty()) return List.of();
        ReflectiveResourcePort port = new ReflectiveResourcePort(target, unit, key,
                amountNames, capacityNames, receiveNames, extractNames, extractWithNegativeAmount, capacitySupplier);
        return port.canReceive() || port.canExtract() ? List.of(port) : List.of();
    }

    /** 只调用公开无参方法，防止适配器为探测而修改第三方方块状态。 */
    protected static Object invokeNoArg(Object target, String name) {
        if (target == null || name == null || name.isEmpty()) return null;
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 0) continue;
                return method.invoke(target);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 第三方 API 缺失或签名改变时交给其它候选适配器处理。
        }
        return null;
    }

    /** 沿继承层级读取已声明字段；仅用于第三方公开资源对象的安全探测，不修改字段内容。 */
    protected static Object readField(Object target, String name) {
        if (target == null || name == null || name.isEmpty()) return null;
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                try { field.setAccessible(true); } catch (RuntimeException ignored) { return null; }
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    protected static boolean packagePrefix(String className, String... prefixes) {
        for (String prefix : prefixes) if (className.startsWith(prefix)) return true;
        return false;
    }
}
