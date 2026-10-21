/**
 * 【变更记录 #3】2026-09-15 16:40:00（第 3 次备注 · 1.0.0-stage5-adapters）
 * - 删除：直接遍历 Titanium MultiTankComponent 的 HashSet 并把结果顺序当作永久索引的路径。
 * - 新增：按组件名称排序的稳定储罐快照和负索引拒绝。
 * - 修改：每次读取仍重新验证面连接与组件结构，排序只稳定显示/绑定顺序，不缓存方块实体。
 * - 用途：工业先锋机器重载或组件集合重排后，已绑定的输入/输出罐不会随机漂移。
 */
/**
 * 变更记录 #1（2026-09-14）：新增 Industrial Foregoing 主模组代理。
 * - 新增：工业先锋方块实体能力读取和独立代理标识。
 * - 用途：为 Industrial Foregoing 及附属机器提供统一的物品、流体和 FE 访问入口。
 */
/**
 * 【文件职责】适配工业先锋及共享 Titanium ActiveTile 的库存组件、储罐和 FE。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：删除猜测的附属 ID 与无方向访问。
 * - 新增：组件名/组件内索引身份，按 FacingUtil 与 IFacingComponent 过滤储罐，精确物品/流体视图。
 * - 修改：端口重排后身份不一致即暂停；指定物品槽使用组件内索引检查输入/输出过滤。
 * - 用途：核对工业先锋 1369806de4ceee84a86fcc0e57cc8f4724ad716f、Titanium bbf607e563e86d9f33959ed26bb102fdfd936a17，测试 Titanium 3.8.34；不调用 fillForced/drainForced 或直接修改 NBT。
 */
package com.primordial.machine.adapters;

import com.primordial.machine.ModAdapterProxy;
import com.primordial.machine.MachineProxyRegistry;
import com.hrznstudio.titanium.block.tile.ActiveTile;
import com.hrznstudio.titanium.component.fluid.FluidTankComponent;
import com.hrznstudio.titanium.component.inventory.MultiInventoryComponent.MultiInvCapabilityHandler;
import com.hrznstudio.titanium.component.sideness.IFacingComponent;
import com.hrznstudio.titanium.util.FacingUtil;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import java.util.ArrayList;
import java.util.List;

/** Titanium 3.8.34 组件适配：按组件名识别物品槽和流体罐，保留侧面、输入输出过滤和内容变更回调。 */
public final class IndustrialForegoingProxy extends ModAdapterProxy {
    public static final IndustrialForegoingProxy INSTANCE = new IndustrialForegoingProxy();
    private IndustrialForegoingProxy() { super("industrial_foregoing"); }

    public static void register() {
        MachineProxyRegistry.registerModProxy("industrialforegoing", INSTANCE);
        MachineProxyRegistry.register("industrial_foregoing:titanium", be -> be instanceof ActiveTile<?>, INSTANCE);
    }

    @Override
    public String itemSlotKey(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        if (handler instanceof MultiInvCapabilityHandler<?> multi) return componentSlotKey(machine, side, multi, slot);
        return super.itemSlotKey(machine, side, handler, slot);
    }

    private <T extends com.hrznstudio.titanium.component.IComponentHarness> String componentSlotKey(
            BlockEntity machine, Direction side, MultiInvCapabilityHandler<T> handler, int slot) {
        var component = handler.getFromSlot(slot);
        return component == null ? "" : endpointKey(machine, side) + "/component/" + component.getName()
                + "/" + handler.getRelativeSlot(component, slot);
    }

    @Override
    public IItemHandler targetItemHandler(BlockEntity machine, Direction side, IItemHandler handler, int slot) {
        if (handler instanceof MultiInvCapabilityHandler<?> multi) return componentView(multi, slot);
        return super.targetItemHandler(machine, side, handler, slot);
    }

    private <T extends com.hrznstudio.titanium.component.IComponentHarness> IItemHandler componentView(
            MultiInvCapabilityHandler<T> handler, int slot) {
        var component = handler.getFromSlot(slot);
        return component == null ? null : new TitaniumItemSlot(component, handler.getRelativeSlot(component, slot));
    }

    /** 与 Titanium MultiTankComponent 的侧面过滤相同；只枚举公开组件，不读取私有字段或使用 fillForced。 */
    private List<FluidTankComponent<?>> visibleTanks(BlockEntity machine, Direction side) {
        if (!(machine instanceof ActiveTile<?> active) || active.getMultiTankComponent() == null) return List.of();
        var relative = FacingUtil.getFacingRelative(active.getFacingDirection(), side);
        List<FluidTankComponent<?>> result = new ArrayList<>();
        for (FluidTankComponent<?> tank : active.getMultiTankComponent().getTanks()) {
            if (tank instanceof IFacingComponent facing) {
                var mode = facing.getFacingModes().get(relative);
                if (mode == null || !mode.allowsConnection()) continue;
            }
            result.add(tank);
        }
        result.sort(java.util.Comparator.comparing(FluidTankComponent::getName));
        return result;
    }

    @Override
    public String fluidTankKey(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        if (machine instanceof ActiveTile<?>) {
            var tanks = visibleTanks(machine, side);
            return tanks.size() != handler.getTanks() || tank < 0 || tank >= tanks.size() ? ""
                    : endpointKey(machine, side) + "/tank/" + tanks.get(tank).getName();
        }
        return super.fluidTankKey(machine, side, handler, tank);
    }

    @Override
    public IFluidHandler targetFluidHandler(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        if (machine instanceof ActiveTile<?>) {
            var tanks = visibleTanks(machine, side);
            // 面禁用/组件结构异常时不退回聚合 fill，以免转向另一储罐。
            return tanks.size() != handler.getTanks() || tank < 0 || tank >= tanks.size() ? null : tanks.get(tank);
        }
        return super.targetFluidHandler(machine, side, handler, tank);
    }
}
