/**
 * 【文件职责】适配 GregTech CEu Modern 1.20.1 的 MetaMachine 方块实体和附属机器能力。
 * - 核心输入：GregTech 方块实体、外部方向及 Forge 物品/流体/能源 capability。
 * - 核心输出：按机器实现类固定端点身份，并把选定槽/罐限制成唯一访问视图。
 * - 调用链：ModAdapterBootstrap 注册 → MachineProxyRegistry 按 gtceu 或包名命中 → MachineTargetAccess 复核 → TaskExecutor 执行。
 *
 * 【变更记录 #1】2026-09-15 22:09:32（第 1 次备注 · 1.0.0-stage5-adapters2）
 * - 删除：无；不猜测 GregTech 内部配方逻辑，也不直接修改 MetaMachine 的私有状态。
 * - 新增：gtceu modId 和 com.gregtechceu.gtceu 包名匹配，读取公开 Forge capability 并记录机器类身份。
 * - 修改：多储罐保持安全拒绝，物品/能源使用指定方向和原能力的模拟/执行方法，避免跨端口写入。
 * - 用途：为 GregTech Modern 的机器、仓室、仓口及附属设备提供统一的配置器端点，后续可在不改协议的情况下扩展原生配方 API。
 */
package com.primordial.machine.adapters;

import com.primordial.machine.MachineProxyRegistry;

/** GregTech CEu Modern 专用 Forge capability 适配器。 */
public final class GregTechModernProxy extends ForgeModAdapterProxy {
    public static final GregTechModernProxy INSTANCE = new GregTechModernProxy();

    private GregTechModernProxy() {
        super("gtceu");
    }

    public static void register() {
        MachineProxyRegistry.registerModProxy("gtceu", INSTANCE);
        MachineProxyRegistry.register("gtceu:api",
                be -> classInPackage(be, "com.gregtechceu.gtceu."), INSTANCE);
    }
}
