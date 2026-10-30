/**
 * 【文件职责】隔离可选化学品注册表的反射入口，不把第三方类型带入公共知识 API。
 * 【变更记录 #1】2026-10-23 13:34:30（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：按实际安装状态载入兼容类，注册 ID 和标签成员查询均安全拒绝异常。
 * - 修改：无 Mekanism 时不解析其 API 类，也不把默认空注册项当作有效资源。
 * - 用途：管理员不能用任意字符串伪造知识；统一化学品黑名单仍可查询真实标签。
 */
package com.primordial.neoforge.knowledge;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

public final class NeoForgeMediaRegistry {
    private NeoForgeMediaRegistry() {}

    public static boolean validChemical(ResourceLocation id) { return invoke("valid", id, null); }

    /** 可选注册表只暴露不可变的真实 ID 快照，不把 Mekanism 类型带入目录或网络。 */
    public static Set<ResourceLocation> chemicalIds() {
        if (!ModList.get().isLoaded("mekanism")) return Set.of();
        try {
            Object raw = bridge("ids").invoke(null);
            if (!(raw instanceof Set<?> values)) return Set.of();
            java.util.HashSet<ResourceLocation> result = new java.util.HashSet<>();
            for (Object value : values) {
                if (!(value instanceof ResourceLocation id)) return Set.of();
                result.add(id);
            }
            return Set.copyOf(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { return Set.of(); }
    }
    public static Predicate<ResourceLocation> chemicalTags(ResourceLocation id) {
        return tag -> {
            if (!validChemical(id)) throw new IllegalStateException("Chemical registry unavailable");
            try { return (boolean) bridge("inTag", ResourceLocation.class, ResourceLocation.class).invoke(null, id, tag); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                throw new IllegalStateException("Chemical tag lookup unavailable", failure);
            }
        };
    }

    private static boolean invoke(String method, ResourceLocation id, ResourceLocation ignored) {
        if (id == null || !ModList.get().isLoaded("mekanism")) return false;
        try { return (boolean) bridge(method, ResourceLocation.class).invoke(null, id); }
        catch (ReflectiveOperationException | RuntimeException | LinkageError failure) { return false; }
    }

    private static Method bridge(String method, Class<?>... types) throws ReflectiveOperationException {
        return Class.forName("com.primordial.neoforge.compat.NeoForgeChemicalKnowledgeBridge").getMethod(method, types);
    }
}
