/**
 * 【文件职责】适配 Ender IO 1.20.1 的机器和导管端点，复用其公开 Forge capability 访问。
 * - 核心输入：Ender IO 方块实体、外部方向以及物品/流体/能源 capability。
 * - 核心输出：包含机器实现类的稳定端点身份和精确单槽/单罐视图。
 * - 调用链：ModAdapterBootstrap 注册 → MachineProxyRegistry 按 modId 或包名命中 → 任务执行器通过目标身份访问。
 *
 * 【变更记录 #1】2026-09-15 22:09:32（第 1 次备注 · 1.0.0-stage5-adapters2）
 * - 删除：无；不直接依赖 Ender IO 内部机器类，避免 beta 版本类名变化破坏主模组加载。
 * - 新增：enderio modId 和 com.enderio 包名匹配，使用 Forge capability 的侧面权限作为唯一读写边界。
 * - 修改：物品槽按 handler 身份固定，流体仅允许单罐视图，能源不重复转换并保留模拟/执行语义。
 * - 用途：兼容 Ender IO 机器、导管和附属设备，同时保证配置器绑定不会因导管聚合能力而跨槽写入。
 */
package com.primordial.machine.adapters;

import com.primordial.machine.MachineProxyRegistry;

/** Ender IO 专用 Forge capability 适配器。 */
public final class EnderIOProxy extends ForgeModAdapterProxy {
    public static final EnderIOProxy INSTANCE = new EnderIOProxy();

    private EnderIOProxy() {
        super("enderio");
    }

    public static void register() {
        MachineProxyRegistry.registerModProxy("enderio", INSTANCE);
        MachineProxyRegistry.register("enderio:api",
                be -> classInPackage(be, "com.enderio."), INSTANCE);
    }
}
