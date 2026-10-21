/**
 * 【文件职责】发现气动工艺的 Air/Pressure 储存并映射为 EMC 资源端口。
 * 变更记录 #1（2026-09-16）：新增可选反射适配；优先使用 getAir/getMaxAir 和 addAir/
 * removeAir，兼容部分版本将空气数量命名为 pressure 的情况。
 */
/**
 * 【变更记录 #2】2026-09-16（气动工艺内部空气句柄修复）
 * - 删除：只在方块实体查找 getPressure/addAir，导致实际 IAirHandlerMachine 储存量和容量缺失的路径。
 * - 新增：读取 AbstractAirHandlingBlockEntity 的 airHandler 字段，使用 IAirHandler 的 getAir/getVolume/addAir。
 * - 修改：抽取通过 addAir(-amount) 执行，保留气动工艺自身压力更新逻辑；未找到真实句柄时返回空端口。
 * - 用途：气动工艺压力设备可以在配置器列出真实空气端口，并由 EMC 供给或回收而不直接改写压力值。
 */
/**
 * 【变更记录 #3】2026-09-16（真实容量读取修复）
 * - 删除：空气句柄查找失败时直接回退到方块实体的 getDefaultVolume() 路径；该方法在部分气动设备中只是基础体积，
 *   不能代表已经应用升级后的 MachineAirHandler.getVolume()，会把 20000 的设备显示成 1000。
 * - 新增：按 airHandler 字段、字段类型和兼容 getter 多级查找真实 IAirHandler；字段名称被映射或访问权限变化时仍能找到句柄。
 * - 修改：只有找到真实空气句柄才创建气动工艺资源端口，避免错误的方块实体回退覆盖真实容量；容量继续由 getVolume() 动态读取。
 * - 用途：配置器端口列表、补充模式缺口计算和实际 EMC 注入均使用气动设备当前升级后的完整容量。
 */
/**
 * 【变更记录 #4】2026-09-16 13:26:00（第 4 次备注 · 1.0.0-acceleration-safety）
 * - 删除：把 getVolume 当容量、把继承的 maxPressure 固定 10 bar 当机器上限，以及失败时视为无限容量的逻辑。
 * - 新增：按 getVolume × getDangerPressure 计算安全空气容量，向下取整并裁剪到原生 int 上限。
 * - 修改：仅使用 IAirHandler 的 getAir/addAir，回收使用负增量；升级后动态读取实际容积。
 * - 用途：充气站容积 1000、等级上限 20 bar 时允许存储 20000 mL；读取失败不再过量充气导致爆炸。
 */
package com.primordial.resource.adapters;

import java.lang.reflect.Field;
import com.primordial.resource.ReflectiveResourceAdapter;
import com.primordial.resource.ResourceExchangeConversions;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PneumaticCraftResourceAdapter extends ReflectiveResourceAdapter {
    public PneumaticCraftResourceAdapter() {
        super(ResourceExchangeConversions.PNEUMATIC_PRESSURE,
                new String[]{"getAir"}, new String[]{},
                new String[]{"addAir"}, new String[]{"addAir"}, true);
    }
    @Override protected String id() { return "pneumaticcraft"; }
    @Override protected boolean matchesClass(String name) { return packagePrefix(name, "me.desht.pneumaticcraft."); }

    @Override
    public List<com.primordial.resource.EmcResourcePort> find(BlockEntity machine, Direction side) {
        if (machine == null || machine.isRemoved() || !matchesClass(machine.getClass().getName())) return List.of();
        Object handler = findAirHandler(machine);
        if (handler != null) {
            String suffix = side == null ? "internal" : side.getName().toLowerCase(Locale.ROOT);
            return createPort(handler, id() + "/" + machine.getClass().getName() + "/" + suffix + "/air_handler",
                    () -> maxAirCapacity(handler));
        }
        // 找不到真实句柄时返回空列表，不能把方块实体自身的 getDefaultVolume 当作空气容量。
        return List.of();
    }

    /**
     * 查找气动工艺真正的空气储存对象。
     *
     * PneumaticCraft 公开的 {@code airHandler} 是 AbstractAirHandlingBlockEntity 的受保护字段。
     * 不同构建映射可能改变字段可见性或名称，因此先尝试稳定名称，再扫描继承层级中类型/名称包含
     * AirHandler 的字段，最后兼容少数版本公开的无参 getter。候选对象必须同时具备 getAir、getVolume
     * 和 addAir 这三个真实空气 API，避免误把 BlockEntity 的压力或基础体积方法包装成端口。
     */
    private static Object findAirHandler(BlockEntity machine) {
        Object direct = readField(machine, "airHandler");
        if (usable(direct)) return direct;

        for (Class<?> type = machine.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName().toLowerCase(Locale.ROOT);
                String fieldType = field.getType().getName().toLowerCase(Locale.ROOT);
                if (!name.contains("airhandler") && !fieldType.contains("airhandler")) continue;
                try {
                    field.setAccessible(true);
                    Object candidate = field.get(machine);
                    if (usable(candidate)) return candidate;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 继续尝试继承层级中的其它候选字段；第三方私有字段不可访问时不影响其它设备。
                }
            }
        }

        for (String getter : new String[]{"getAirHandler", "getAirHandlerMachine", "airHandler"}) {
            Object candidate = invokeNoArg(machine, getter);
            if (usable(candidate)) return candidate;
        }
        return null;
    }

    /** 只接受可以读写真实空气数量并读取升级后容量的候选对象。 */
    private static boolean usable(Object candidate) {
        if (candidate == null) return false;
        Class<?> type = candidate.getClass();
        return hasNoArg(type, "getAir") && hasNoArg(type, "getVolume") && hasNumericWrite(type, "addAir");
    }

    private static boolean hasNoArg(Class<?> type, String name) {
        for (var method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) return true;
        }
        return false;
    }

    private static boolean hasNumericWrite(Class<?> type, String name) {
        for (var method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter == byte.class || parameter == short.class || parameter == int.class
                    || parameter == long.class || parameter == float.class || parameter == double.class
                    || Number.class.isAssignableFrom(parameter)) return true;
        }
        return false;
    }

    /**
     * PneumaticCraft 的 getVolume() 是基础体积，不是空气数量上限。
     * IAirHandler 的压力由 air / volume 得出，因此最大空气数量必须使用
     * volume × getDangerPressure；该值是 GUI 使用的安全工作压力，超过后机器可能爆炸。
     * 1.20.1 MachineAirHandler 继承的 maxPressure() 固定为 10，并不代表机器的压力等级。
     */
    static long maxAirCapacity(Object handler) {
        Object volumeValue = invokeNoArg(handler, "getVolume");
        Object pressureValue = invokeNoArg(handler, "getDangerPressure");
        if (!(volumeValue instanceof Number volume) || !(pressureValue instanceof Number pressure)) {
            return 0;
        }
        if (volume.doubleValue() <= 0 || pressure.doubleValue() <= 0) return 0;
        double result = volume.doubleValue() * pressure.doubleValue();
        if (!Double.isFinite(result) || result <= 0) return 0;
        return (long) Math.floor(Math.min(Integer.MAX_VALUE, result));
    }
}
