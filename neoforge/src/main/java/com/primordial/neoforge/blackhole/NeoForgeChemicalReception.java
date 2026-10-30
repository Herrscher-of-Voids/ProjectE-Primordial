/**
 * 【文件职责】只用注册 ID 处理统一化学品报价、每 tick 预算和实际回收后的收益/知识。
 * 【变更记录 #1】2026-10-22 22:20:00（第 1 次备注 · 2.0.0-neo-dev 统一化学品回收）
 * - 删除：无。
 * - 新增：long 请求安全裁剪、模拟无副作用、报价重入保护与重载版本检查。
 * - 修改：每 mB 整数 EMC 沿用 gas_* 字段，统一化学品与物品/流体知识分开。
 * - 用途：可选适配器仅负责原生栈和能力，公共生命周期不解析 Mekanism 类。
 */
package com.primordial.neoforge.blackhole;

import java.util.Objects;
import java.util.function.Predicate;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import net.minecraft.resources.ResourceLocation;

final class NeoForgeChemicalReception {
    private final NeoForgeBlackholeBlockEntity block;
    private long budgetTick = Long.MIN_VALUE;
    private int received;
    private boolean quoting;
    private long version;

    NeoForgeChemicalReception(NeoForgeBlackholeBlockEntity block) { this.block = block; }
    boolean busy() { return quoting; }
    void reset() { version++; budgetTick = Long.MIN_VALUE; received = 0; }

    boolean valid(ResourceLocation id, Predicate<ResourceLocation> inTag) {
        return id != null && block.canRecycleResources() && !quote(id, inTag).isZero() && block.canRecycleResources();
    }

    private EmcAmount quote(ResourceLocation id, Predicate<ResourceLocation> inTag) {
        if (quoting) return EmcAmount.ZERO;
        var owner = block.owner();
        long before = version;
        quoting = true;
        EmcAmount price;
        try { price = NeoForgeRecoveryPrices.chemical(id, inTag); }
        finally { quoting = false; }
        return before == version && Objects.equals(owner, block.owner()) ? price : EmcAmount.ZERO;
    }

    long receive(ResourceLocation id, long requested, boolean simulate, Predicate<ResourceLocation> inTag) {
        if (id == null || requested <= 0 || !block.canRecycleResources()) return 0;
        EmcAmount price = quote(id, inTag);
        if (price.isZero() || !block.canRecycleResources()) return 0;
        long now = block.getLevel().getGameTime();
        int used = budgetTick == now ? received : 0;
        long accepted = Math.min(requested, Math.max(0, NeoForgeServerConfig.maxGas() - used));
        if (accepted == 0 || simulate) return accepted;
        // 先确认精确收益，再扣公共预算并学习；调用方仍持有原始栈，由适配器返回剩余量。
        block.queueCredit(price.multiply(accepted));
        budgetTick = now;
        received = used + (int) accepted;
        NeoForgeRecoveryKnowledge.recordChemical(block.getLevel().getServer(), block.owner(), id);
        block.recoveryParticles();
        return accepted;
    }
}
