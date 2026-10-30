/**
 * 【文件职责】只读核实可生成物品的完整状态与真实所有者知识，阻止库存、充能或未知附件被复制。
 * 【变更记录 #1】2026-10-23 11:45:23（第 1 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：无。
 * - 新增：ProjectE 持久形态、完整注册表序列化等价、原生物品/流体/能源容器非空检查和离线知识读取。
 * - 修改：保留已被 ProjectE 识别且无库存的安全组件，任何能力或序列化异常均拒绝生成，不改写样本。
 * - 用途：公开生成和模拟共用相同安全边界；可选模组只经 NeoForge 通用能力查询，不产生第三方类链接。
 * 【变更记录 #2】2026-10-23 11:49:01（第 2 次备注 · 2.0.0-neoforge-resource-trade）
 * - 删除：非空流体能力一概拒绝而误伤原版水桶的判定。
 * - 新增：能力内容对照同类型默认物品的逐槽、逐罐和能源比较。
 * - 修改：水桶等固有内容仍归原生物品价格，额外库存/填充/充能状态拒绝，不调用介质回收。
 * - 用途：同时保留合法固有容器语义与可变容器防复制边界。
 * 【变更记录 #3】2026-10-23 13:30:27（第 3 次备注 · 2.0.0-neoforge-knowledge-certificates）
 * - 删除：本策略直接查询 ProjectE 在线或离线知识的重复逻辑。
 * - 新增：复用 NeoForgeResourceKnowledge.allowsItem 的统一知识开关与原生知识授权。
 * - 修改：库存、充能、组件与完整序列化安全检查始终先执行；关闭知识要求只能跳过已学检查。
 * - 用途：普通物品生成与系统 07 知识 API 共用授权，不因配置关闭而放宽容器防复制规则。
 */
package com.primordial.neoforge.trade;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.capabilities.Capabilities;

final class NeoForgeTradeItemPolicy {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private NeoForgeTradeItemPolicy() {}

    static boolean allows(MinecraftServer server, UUID owner, ItemStack sample) {
        ItemInfo persistent = persistentOutput(server, sample);
        if (persistent == null) return false;
        try {
            return NeoForgeResourceKnowledge.allowsItem(server, owner, persistent.createStack());
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
            return false;
        }
    }

    @Nullable
    private static ItemInfo persistentOutput(MinecraftServer server, ItemStack sample) {
        try {
            ItemContainerContents container = sample.get(DataComponents.CONTAINER);
            BundleContents bundle = sample.get(DataComponents.BUNDLE_CONTENTS);
            if (container != null && container.nonEmptyStream().findAny().isPresent()
                    || bundle != null && !bundle.isEmpty() || sample.has(DataComponents.BLOCK_ENTITY_DATA)) return null;
            ItemStack base = new ItemStack(sample.getItem());
            var inventory = sample.getCapability(Capabilities.ItemHandler.ITEM);
            if (inventory != null) {
                var defaults = base.getCapability(Capabilities.ItemHandler.ITEM);
                if (defaults == null || inventory.getSlots() != defaults.getSlots()) return null;
                for (int slot = 0; slot < inventory.getSlots(); slot++)
                    if (!ItemStack.matches(inventory.getStackInSlot(slot), defaults.getStackInSlot(slot))) return null;
            }
            var fluids = sample.getCapability(Capabilities.FluidHandler.ITEM);
            if (fluids != null) {
                var defaults = base.getCapability(Capabilities.FluidHandler.ITEM);
                if (defaults == null || fluids.getTanks() != defaults.getTanks()) return null;
                for (int tank = 0; tank < fluids.getTanks(); tank++) {
                    var actual = fluids.getFluidInTank(tank);
                    var expected = defaults.getFluidInTank(tank);
                    if (actual.isEmpty() != expected.isEmpty() || !actual.isEmpty()
                            && (actual.getAmount() != expected.getAmount()
                            || !net.neoforged.neoforge.fluids.FluidStack.isSameFluidSameComponents(actual, expected))) return null;
                }
            }
            var energy = sample.getCapability(Capabilities.EnergyStorage.ITEM);
            if (energy != null) {
                var defaults = base.getCapability(Capabilities.EnergyStorage.ITEM);
                if (defaults == null || energy.getEnergyStored() < 0
                        || energy.getEnergyStored() != defaults.getEnergyStored()) return null;
            }
            ItemInfo normalized = IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(sample));
            if (normalized == null) return null;
            ItemStack output = normalized.createStack();
            // ItemInfo 只有组件，不含其它序列化附件；完整比较防止第三方附件从规范化边界旁路。
            return ItemStack.isSameItemSameComponents(sample, output)
                    && sample.save(server.registryAccess()).equals(output.save(server.registryAccess())) ? normalized : null;
        } catch (RuntimeException | LinkageError failure) {
            warn(failure);
            return null;
        }
    }

    private static void warn(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) LogUtils.getLogger().warn(
                "[Primordial] Item trade generation policy unavailable; transaction rejected", failure);
    }
}
