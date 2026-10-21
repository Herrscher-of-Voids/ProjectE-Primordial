/**
 * 【文件职责】转化桌使用 Mekanism 手动储罐交互，绕开自动化速率而保留容量与介质校验。
 * 【变更记录 #1】2026-10-13 01:56:00（第 1 次备注 · 1.2.0-media-feedback）
 * - 删除：无。新增：流体/气体储罐 MANUAL 插入和抽取。
 * - 修改：仅在已安装 Mekanism 的兼容边界调用；不直接改写 NBT 或储量。
 * - 用途：一次买满/卖空，实际交付仍由原交易事务结算，创造罐原有行为保留。
 */
package com.primordial.compat;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.fluid.IMekanismFluidHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

final class MekanismManualMedia {
    private MekanismManualMedia() { }

    static long gas(IGasHandler handler, int index, GasStack sample, long amount, boolean buy, Action action) {
        IGasTank tank = handler instanceof IGasTank direct && index == 0 ? direct
                : handler instanceof IGasHandler.IMekanismGasHandler typed ? typed.getChemicalTank(index, null) : null;
        if (tank == null) {
            if (buy) return amount - handler.insertChemical(index, new GasStack(sample, amount), action).getAmount();
            GasStack extracted = handler.extractChemical(index, amount, action);
            return extracted.isTypeEqual(sample) ? extracted.getAmount() : 0;
        }
        if (buy) return amount - tank.insert(new GasStack(sample, amount), action, AutomationType.MANUAL).getAmount();
        if (!tank.getStack().isTypeEqual(sample)) return 0;
        GasStack extracted = tank.extract(amount, action, AutomationType.MANUAL);
        return extracted.isTypeEqual(sample) ? extracted.getAmount() : 0;
    }

    /** -1 表示普通 Forge 容器，由调用者保留其能力契约。 */
    static int fluid(IFluidHandler handler, FluidStack sample, boolean buy, IFluidHandler.FluidAction action) {
        java.util.List<IExtendedFluidTank> tanks;
        if (handler instanceof IMekanismFluidHandler typed) tanks = typed.getFluidTanks(null);
        else if (handler instanceof IExtendedFluidTank direct) tanks = java.util.List.of(direct);
        else return -1;
        int remaining = sample.getAmount();
        for (IExtendedFluidTank tank : tanks) {
            if (remaining == 0) break;
            int moved;
            if (buy) {
                var proposal = sample.copy(); proposal.setAmount(remaining);
                moved = remaining - tank.insert(proposal, Action.fromFluidAction(action), AutomationType.MANUAL).getAmount();
            } else {
                if (!tank.getFluid().isFluidEqual(sample)) continue;
                var extracted = tank.extract(remaining, Action.fromFluidAction(action), AutomationType.MANUAL);
                moved = extracted.isFluidEqual(sample) ? extracted.getAmount() : 0;
            }
            remaining -= Math.max(0, Math.min(remaining, moved));
        }
        return sample.getAmount() - remaining;
    }
}
