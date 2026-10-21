/**
 * 【文件职责】JUnit 未启动 Forge 注册事件时，提供气体注册名、delegate 与 NBT 反查。
 * 【变更记录 #1】2026-09-15 11:55:00（第 1 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：作用域受控的 MekanismAPI.gasRegistry 替身，GasStack 保持真实构造、数量和序列化。
 * - 修改：关闭时恢复静态 API，不改变生产 jar 或全局游戏注册表。
 * - 用途：测试气体合约和重载，避免把实际 GasStack 也 mock 掉而遗漏返回余量错误。
 */
/**
 * 【变更记录 #2】2026-09-15 12:08:58（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：Holder.Reference.get 的化学品 delegate 返回值。
 * - 修改：注册表同时提供 value/get，真实 GasStack 能初始化 EMPTY 并按注册名重载。
 * - 用途：准确模拟 Forge 气体注册表的查询契约，不 mock 气体数量和插入算法。
 */
package com.primordial.machine;

import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;

final class GasTestRegistry implements AutoCloseable {
    final Gas gas = new Gas(GasBuilder.builder());
    final ResourceLocation id = new ResourceLocation("mekanism", "antimatter");
    final MockedStatic<MekanismAPI> api;

    @SuppressWarnings("unchecked") GasTestRegistry() {
        IForgeRegistry<Gas> registry = mock(IForgeRegistry.class);
        var emptyId = new ResourceLocation("mekanism", "empty");
        when(registry.getKey(any(Gas.class))).thenAnswer(call -> call.getArgument(0) == gas ? id : emptyId);
        when(registry.getDelegateOrThrow(any(Gas.class))).thenAnswer(call -> {
            Holder.Reference<Gas> holder = mock(Holder.Reference.class);
            when(holder.value()).thenReturn(call.getArgument(0));
            when(holder.get()).thenReturn(call.getArgument(0));
            return holder;
        });
        when(registry.getValue(id)).thenReturn(gas);
        when(registry.containsKey(id)).thenReturn(true);
        api = mockStatic(MekanismAPI.class, CALLS_REAL_METHODS);
        api.when(MekanismAPI::gasRegistry).thenReturn(registry);
    }
    @Override public void close() { api.close(); }
}
