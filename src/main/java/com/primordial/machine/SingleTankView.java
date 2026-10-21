/**
 * 【文件职责】提供指定罐的容量、内容读取和完整 NBT 提取过滤。
 * 【变更记录 #1】2026-09-14 00:55:10（第 1 次备注 · 1.0.0-stage5）
 * - 删除：无；新增原生按罐包装基类。
 * - 新增：单罐边界检查与提取前 FluidStack.isFluidEqual 检查。
 * - 修改：子类负责原生索引的 fill/drain，基类禁止先提取后丢弃不匹配流体。
 * - 用途：供 Mekanism/AE2 适配器使用，避免流体自动分配到其它罐。
 */
package com.primordial.machine;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** 单储罐视图；子类必须实现按原生罐索引写入/提取，不能调用多罐聚合 fill/drain。 */
public abstract class SingleTankView implements IFluidHandler {
    protected final IFluidHandler source;
    protected final int tank;

    protected SingleTankView(IFluidHandler source, int tank) {
        if (tank < 0 || tank >= source.getTanks()) throw new IllegalArgumentException("Invalid target tank");
        this.source = source;
        this.tank = tank;
    }

    private void check(int index) {
        if (index != 0) throw new IndexOutOfBoundsException("Single-tank view: " + index);
    }

    @Override public final int getTanks() { return 1; }
    @Override public final FluidStack getFluidInTank(int index) { check(index); return source.getFluidInTank(tank); }
    @Override public final int getTankCapacity(int index) { check(index); return source.getTankCapacity(tank); }
    @Override public final boolean isFluidValid(int index, FluidStack stack) {
        check(index);
        return source.isFluidValid(tank, stack);
    }

    @Override public final FluidStack drain(FluidStack resource, FluidAction action) {
        // 完整 NBT 匹配在提取前进行，不能先提取再因过滤失败丢弃流体。
        if (resource.isEmpty() || !source.getFluidInTank(tank).isFluidEqual(resource)) return FluidStack.EMPTY;
        return drain(resource.getAmount(), action);
    }
}
