/**
 * 【文件职责】为纯 JUnit 测试准备 Minecraft 基础注册及 capability 类型身份。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增测试环境桥接。
 * - 新增：在无 Forge 启动转换器的测试 JVM 中按泛型类型模拟 CapabilityManager 的 token 身份。
 * - 修改：仅启动身份被模拟；适配器和依赖的库存/储罐传输代码仍为真实实现。
 * - 用途：测试结果覆盖组件行为，不等同于完整 Forge 世界加载、游戏界面或所有附属兼容性验证。
 */
/**
 * 【变更记录 #2】2026-09-15 15:49:31（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：无。
 * - 新增：ProjectE 知识 capability 的测试类型身份初始化。
 * - 修改：仍只替代 Forge 启动转换器，账户余额可直接执行同版 ProjectE 实现。
 * - 用途：验证原生大整数提交、离线队列合并和网络失败隔离。
 */
/**
 * 【变更记录 #3】2026-09-16 14:49:28（第 3 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：仅同包测试可调用的访问限制。
 * - 新增：为加速服务测试开放原有 initialize 入口，复用 ProjectE capability 身份初始化。
 * - 修改：启动方式与替身范围不变，不重复实现测试专用 Forge 类型注册。
 * - 用途：纯 JUnit 可执行真实余额消费链，避免缺少启动转换器导致 PЕCapabilities 初始化失败。
 */
package com.primordial.machine;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import java.lang.reflect.ParameterizedType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.Mockito.*;

/** JUnit 不运行 Forge 启动转换器；仅模拟 capability token 的类型身份，库存与传输实现仍使用真实依赖。 */
public final class AdapterTestBootstrap {
    private static boolean initialized;
    private AdapterTestBootstrap() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized void initialize() {
        if (initialized) return;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Map<String, Capability<?>> tokens = new ConcurrentHashMap<>();
        try (var capabilities = mockStatic(CapabilityManager.class)) {
            capabilities.when(() -> CapabilityManager.get(any(CapabilityToken.class))).thenAnswer(call -> {
                CapabilityToken<?> token = call.getArgument(0);
                String type = ((ParameterizedType) token.getClass().getGenericSuperclass()).getActualTypeArguments()[0].getTypeName();
                return tokens.computeIfAbsent(type, key -> mock(Capability.class));
            });
            Class.forName("net.minecraftforge.common.capabilities.ForgeCapabilities");
            Class.forName("appeng.capabilities.Capabilities");
            Class.forName("mekanism.common.capabilities.Capabilities");
            Class.forName("moze_intel.projecte.api.capabilities.PECapabilities");
        } catch (ClassNotFoundException missingDependency) {
            throw new AssertionError("Adapter test dependency missing", missingDependency);
        }
        initialized = true;
    }
}
