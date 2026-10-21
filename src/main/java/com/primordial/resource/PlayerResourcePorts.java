/**
 * 【文件职责】发现玩家身上的铁魔法法力和诡厄巫法灵魂账户，不缓存玩家对象。
 * 【变更记录 #1】2026-10-14 17:12:00（第 1 次备注 · 1.2.0-player-magic-resources）
 * - 删除：无。新增：可选原生 API 的延迟解析、精确缺口补充和客户端资源同步。
 * - 修改：只提供购买端口，模拟不写入；灵魂账户遵循方舟优先、否则图腾的原生选择。
 * - 用途：EMC 交易与玩家真实资源共用库存，缺少联动模组时不加载其类型。
 */
package com.primordial.resource;

import com.primordial.PrimordialMod;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

public final class PlayerResourcePorts {
    public static final int COUNT = 2;
    private static final String[] MODS = {"irons_spellbooks", "goety"};
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final Factory[] FACTORIES = new Factory[COUNT];
    private static final boolean[] RESOLVED = new boolean[COUNT];
    private PlayerResourcePorts() { }

    @FunctionalInterface interface Factory { EmcResourcePort create(ServerPlayer player) throws ReflectiveOperationException; }

    public static EmcResourcePort find(ServerPlayer player, int index) {
        if (player == null || index < 0 || index >= COUNT || !ModList.get().isLoaded(MODS[index])) return null;
        try {
            Factory factory = resolve(index);
            return factory == null ? null : factory.create(player);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(MODS[index], failure); return null;
        }
    }

    private static synchronized Factory resolve(int index) throws ReflectiveOperationException {
        if (RESOLVED[index]) return FACTORIES[index];
        RESOLVED[index] = true;
        FACTORIES[index] = index == 0 ? IronManaPort.factory() : GoetySoulPort.factory();
        return FACTORIES[index];
    }

    static Object invoke(Method method, Object instance, Object... arguments) {
        try { return method.invoke(instance, arguments); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native resource API failed: " + method, failure); }
    }

    static void warn(String key, Throwable failure) {
        if (WARNED.add(key)) PrimordialMod.LOGGER.warn("[Primordial] {} player resource API unavailable", key, failure);
    }

}
