/**
 * 【文件职责】黑洞的 Mekanism IGasHandler 接收端，按实际接收量计 EMC 并返回未接收气体。
 * 【变更记录 #1】2026-09-15 11:45:00（第 1 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：单罐只进不出接口、模拟填充、无价格拒收和每 tick 独立气体预算。
 * - 修改：回收金额交给黑洞 pendingEmc 合并结算，不在管道回调里写玩家余额或创建队列节点。
 * - 用途：支持反物质等注册气体；缺 owner、客户端、无服务器或未知 EMC 时不消耗输入。
 */
/**
 * 【变更记录 #2】2026-09-15 14:58:00（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：接收端直接引用 ValueManager 的定价路径。
 * - 新增：EmcService 气体报价与精确队列金额。
 * - 修改：仅改变结算入口；long 接口数量先按预算裁剪，模拟仍不写入。
 * - 用途：反物质等气体与物品/流体/FE 共用价格与 BigInteger 结算规则。
 */
/**
 * 【变更记录 #3】2026-10-02 18:54:32（第 3 次备注 · 1.2.0-media-trade-switches）
 * - 删除：无。
 * - 新增：黑洞气体模拟与实际接收检查卖出开关。
 * - 修改：关闭时返回完整输入余量，不计费、不占预算。
 * - 用途：黑洞气体回收与转化桌遵守同一配置。
 */
/** 【变更记录 #4】2026-10-08 03:32:46（1.2.0-media-price-modes）
 * 删除：无。新增：黑洞气体接收检查仅卖状态。修改：模拟和实际插入均在计价前拒绝。用途：黑洞能力与普通气体回收遵循同一方向规则。 */
/**
 * 【变更记录 #5】2026-10-13 19:40:05（第 5 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：气体黑洞验证与模拟/实际插入共同检查 SELL，拒绝返回完整气体余量，不占预算或排队 EMC。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #6】2026-10-13 20:14:16（第 6 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
/**
 * 【变更记录 #7】2026-10-13 23:59:40（第 7 次备注 · 1.2.0-discovery-learning-system）
 * - 删除：黑洞气体只结算却遗漏学习的路径及未使用的 BigInteger 导入。
 * - 新增：真实接收后为所有者学习对应气体；非服务端主线程执行返回完整余量。
 * - 修改：学习与实际接受数量、pendingEmc 入队处于同一执行分支，模拟和拒收不学习。
 * - 用途：黑洞气体与流体、手动卖入一致，1 mB 即可学习，避免异步部分提交。
 */
package com.primordial.blockentity;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.service.EmcService;
import com.primordial.service.EmcAmount;
import com.primordial.service.ResourceKnowledge;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;

public final class MekanismGasSink implements IGasHandler {
    private final BlackholeBlockEntity be;
    public MekanismGasSink(BlackholeBlockEntity be) { this.be = be; }
    @Override public int getTanks() { return 1; }
    @Override public GasStack getChemicalInTank(int tank) { return GasStack.EMPTY; }
    @Override public long getTankCapacity(int tank) { return tank == 0 ? PrimordialServerConfig.blackholeMaxGasMbPerTick() : 0; }
    /** 黑洞没有可替换的库存；直接赋值不能绕过 insertChemical 的预算与结算。 */
    @Override public void setChemicalInTank(int tank, GasStack stack) { }
    @Override public GasStack extractChemical(int tank, long amount, Action action) { return GasStack.EMPTY; }

    @Override public boolean isValid(int tank, GasStack stack) {
        return !PrimordialServerConfig.mediaSaleDisabled(true)
                && tank == 0 && stack != null && !stack.isEmpty() && be.getOwner() != null
                && (com.primordial.value.ValueManager.gasMode(stack.getType().getRegistryName()) == null
                    || com.primordial.value.ValueManager.gasMode(stack.getType().getRegistryName()).allowsSell())
                && !EmcService.gasPrice(stack.getType().getRegistryName()).isZero();
    }

    /** 常数次计算处理 long 数量；模拟不扣预算、不计价入账、不修改传入 GasStack。 */
    @Override public GasStack insertChemical(int tank, GasStack stack, Action action) {
        if (stack == null || stack.isEmpty()) return GasStack.EMPTY;
        if (PrimordialServerConfig.mediaSaleDisabled(true)) return stack.copy();
        var mode = com.primordial.value.ValueManager.gasMode(stack.getType().getRegistryName());
        if (mode != null && !mode.allowsSell()) return stack.copy();
        if (tank != 0 || be.getOwner() == null || be.isRemoved() || be.getLevel() == null
                || be.getLevel().isClientSide || be.getLevel().getServer() == null) return stack.copy();
        var server = be.getLevel().getServer();
        if (action.execute() && !server.isSameThread()) return stack.copy();
        EmcAmount perMb = EmcService.gasPrice(stack.getType().getRegistryName());
        if (perMb.isZero()) return stack.copy();
        long accepted = Math.min(stack.getAmount(), be.remainingGasBudget());
        if (accepted <= 0) return stack.copy();
        if (action.execute()) {
            be.recordGasAccepted((int) accepted);
            be.queueEmc(perMb.multiply(accepted));
            ResourceKnowledge.learnMedia(server, be.getOwner(), ResourceKnowledge.Kind.GAS,
                    stack.getType().getRegistryName());
            be.puffParticles();
        }
        return accepted == stack.getAmount() ? GasStack.EMPTY : new GasStack(stack, stack.getAmount() - accepted);
    }
}
