/**
 * 【文件职责】为定价器提供无可选 API 类型的化学品采样、名称及图标查询，隔离 Mekanism 类加载。
 * 【变更记录 #1】2026-10-23 16:57:42（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。
 * - 新增：物品容器与 JEI 对象的只读注册名采样，以及 Component、ResourceLocation、long、int 形式的显示数据。
 * - 修改：先确认 Mekanism 已安装才反射兼容实现；缺 API 或不兼容时返回空结果，仅记录一次降级原因。
 * - 用途：菜单、屏幕与 JEI 共用公共边界；不保存化学品对象，不消耗玩家物品或容器内容，也不赋予定价权限。
 */
package com.primordial.neoforge.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class NeoForgePriceEditorChemicalBridge {
    private static final String IMPLEMENTATION = "com.primordial.neoforge.compat.mekanism.NeoForgePriceEditorChemicalSamples";
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private NeoForgePriceEditorChemicalBridge() {}

    public static ResourceLocation sampleContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Object value = call("sampleContainer", ItemStack.class, stack);
        return value instanceof ResourceLocation id ? id : null;
    }

    public static ResourceLocation chemicalId(Object ingredient) {
        if (ingredient == null) return null;
        Object value = call("chemicalId", Object.class, ingredient);
        return value instanceof ResourceLocation id ? id : null;
    }

    public static long chemicalAmount(Object ingredient) {
        if (ingredient == null) return 0;
        Object value = call("chemicalAmount", Object.class, ingredient);
        return value instanceof Long amount && amount > 0 ? amount : 0;
    }

    public static boolean validChemical(ResourceLocation id) {
        return id != null && Boolean.TRUE.equals(call("validChemical", ResourceLocation.class, id));
    }

    public static Component chemicalName(ResourceLocation id) {
        if (id == null) return Component.empty();
        Object value = call("chemicalName", ResourceLocation.class, id);
        return value instanceof Component name ? name : Component.literal(id.toString());
    }

    /** 图标是方块图集中的 sprite ID；无有效化学品时返回 null，调用者应跳过化学品图标。 */
    public static ResourceLocation chemicalTexture(ResourceLocation id) {
        if (id == null) return null;
        Object value = call("chemicalTexture", ResourceLocation.class, id);
        return value instanceof ResourceLocation texture ? texture : null;
    }

    public static int chemicalTint(ResourceLocation id) {
        if (id == null) return 0xFFFFFF;
        Object value = call("chemicalTint", ResourceLocation.class, id);
        return value instanceof Integer tint ? tint : 0xFFFFFF;
    }

    private static Object call(String name, Class<?> parameter, Object argument) {
        if (!ModList.get().isLoaded("mekanism")) return null;
        try {
            Method method = METHODS.get(name);
            if (method == null) {
                method = Class.forName(IMPLEMENTATION).getMethod(name, parameter);
                METHODS.put(name, method);
            }
            return method.invoke(null, argument);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (WARNED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                    "[Primordial] Optional chemical price-editor sampling unavailable; real inventory retained", failure);
            return null;
        }
    }
}
