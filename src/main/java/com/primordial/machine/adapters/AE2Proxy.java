/**
 * 变更记录 #1（2026-09-14）：新增 Applied Energistics 2 主模组代理。
 * - 新增：AE2 方块实体能力读取和独立代理标识。
 * - 用途：为 AE2 及附属机器预留频道/方向专用策略，同时兼容标准 Forge capability。
 */
/**
 * 【变更记录 #4】2026-09-15 16:40:00（第 4 次备注 · 1.0.0-stage5-adapters）
 * - 删除：AE2 流体目标视图直接调用 GenericInternalInventory、忽略库存总开关和已有其它介质的路径。
 * - 新增：槽位边界、canInsert/canExtract、isAllowed 和同槽 AEKey 类型校验。
 * - 修改：模拟与执行均固定同一个 Generic 槽位，拒绝用流体写入物品槽或抽取非流体槽。
 * - 用途：保护 AE2 混合 GenericInternalInventory，避免配置器绑定一个槽后被网络库存重定向。
 */
/**
 * 【变更记录 #3】2026-09-14 14:00:00（第 3 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：无方向 FE 端口生成身份时跳过 AE2 面板查询。
 * - 修改：endpointKey 在 side=null 时不调用 IPartHost.getPart，避免内部能量接口预览抛出空指针。
 * - 用途：与 MachineTarget side=6 内部能量端口兼容，保持 AE2 外部面行为不变。
 */
/**
 * 【文件职责】适配 AE2 通用库存、接口和样板供应器返回缓存，以及线缆面板端点。
 * 【变更记录 #2】2026-09-14 00:55:10（第 2 次备注 · 1.0.0-stage5）
 * - 删除：删除未经核对的附属 modId 路由和无方向访问。
 * - 新增：GENERIC_INTERNAL_INV 访问、AEBaseBlockEntity/IPartHost 匹配、面板物品身份和按索引的流体插入/提取。
 * - 修改：物品沿用 AE2 GenericStackItemStorage；流体直接使用 GenericInternalInventory.insert/extract，保留 canInsert/canExtract。
 * - 用途：核对并编译 AE2 forge/v15.4.10；仅读公开缓存与外部 FE，不把整个 ME 网络或配置样板槽当作可取走的物品。
 */
package com.primordial.machine.adapters;

import com.primordial.machine.ModAdapterProxy;
import com.primordial.machine.MachineProxyRegistry;
import com.primordial.machine.SingleTankView;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.config.Actionable;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.AEFluidKey;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.helpers.externalstorage.GenericStackFluidStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import javax.annotation.Nullable;

/** AE2 15.4.10：适配接口库存、样板供应器返回缓存和线缆面板；不暴露整个 ME 网络为任意库存。 */
public final class AE2Proxy extends ModAdapterProxy {
    public static final AE2Proxy INSTANCE = new AE2Proxy();
    private AE2Proxy() { super("ae2"); }

    public static void register() {
        MachineProxyRegistry.registerModProxy("ae2", INSTANCE);
        MachineProxyRegistry.register("ae2:api", be -> be instanceof AEBaseBlockEntity || be instanceof IPartHost, INSTANCE);
    }

    @Nullable
    private GenericInternalInventory inventory(BlockEntity machine, @Nullable Direction side) {
        if (side == null || machine == null || machine.isRemoved()) return null;
        try {
            return machine.getCapability(Capabilities.GENERIC_INTERNAL_INV, side).resolve().orElse(null);
        } catch (RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    @Override
    public IItemHandler itemHandler(BlockEntity machine, @Nullable Direction side) {
        GenericInternalInventory inv = inventory(machine, side);
        return inv == null ? super.itemHandler(machine, side) : new GenericStackItemStorage(inv);
    }

    @Override
    public IFluidHandler fluidHandler(BlockEntity machine, @Nullable Direction side) {
        GenericInternalInventory inv = inventory(machine, side);
        return inv == null ? super.fluidHandler(machine, side) : new GenericStackFluidStorage(inv);
    }

    @Override
    public String endpointKey(BlockEntity machine, Direction side) {
        String key = super.endpointKey(machine, side);
        if (machine instanceof IPartHost host) {
            var part = side == null ? null : host.getPart(side);
            if (part == null) return key + "/empty_part";
            return key + "/part/" + BuiltInRegistries.ITEM.getKey(new ItemStack(part.getPartItem()).getItem());
        }
        return key;
    }

    @Override
    public IFluidHandler targetFluidHandler(BlockEntity machine, Direction side, IFluidHandler handler, int tank) {
        GenericInternalInventory inv = inventory(machine, side);
        if (inv == null) return super.targetFluidHandler(machine, side, handler, tank);
        if (tank < 0 || tank >= inv.size()) return null;
        return new SingleTankView(handler, tank) {
            @Override public int fill(FluidStack stack, FluidAction action) {
                if (stack == null || stack.isEmpty() || !inv.canInsert()) return 0;
                var key = AEFluidKey.of(stack);
                if (key == null || !inv.isAllowed(key)) return 0;
                var existing = inv.getKey(tank);
                if (existing != null && !existing.equals(key)) return 0;
                long amount = Math.min(Integer.MAX_VALUE, (long) stack.getAmount());
                return (int) Math.min(amount, Math.max(0L, inv.insert(tank, key, amount, Actionable.of(action))));
            }
            @Override public FluidStack drain(int amount, FluidAction action) {
                if (amount <= 0 || !inv.canExtract() || !(inv.getKey(tank) instanceof AEFluidKey key)) return FluidStack.EMPTY;
                long extracted = Math.min(Integer.MAX_VALUE, Math.max(0L,
                        inv.extract(tank, key, amount, Actionable.of(action))));
                return extracted == 0 ? FluidStack.EMPTY : key.toStack((int) extracted);
            }
        };
    }
}
