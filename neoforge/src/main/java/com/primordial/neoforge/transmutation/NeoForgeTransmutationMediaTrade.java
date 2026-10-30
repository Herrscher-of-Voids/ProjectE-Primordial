/**
 * 【文件职责】在真实原生转化桌内购买/回收光标容器介质，按交付副本后的实际量复用精确 EMC 结算。
 * 【变更记录 #1】2026-10-23（2.0.0-neoforge-carried-media）
 * - 新增：源流体与可选统一化学品、模拟副本、实际执行副本、拆堆返还及同玩家重入隔离。
 * - 修改：购买仍复核知识/阶段/价格；回收成功才学习，不信任客户端数量、价格或容器数据。
 * 【变更记录 #2】2026-10-23（2.0.0-neoforge-carried-energy）
 * - 新增：forge:fe 显式能源容器交易、无流体能力时的 FE 识别，以及能源凭证/阶段和原生能力方向校验。
 * - 修改：FE 与介质共用独立模拟副本、单件交付、实际量结算和失败退款；FE 不受流体买卖开关控制。
 */
package com.primordial.neoforge.transmutation;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongUnaryOperator;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.compat.NeoForgeCarriedMekanismBridge;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import com.primordial.neoforge.transmutation.NeoForgeMediaTradeResult.Status;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

public final class NeoForgeTransmutationMediaTrade {
    private static final Set<ServerPlayer> ACTIVE = ConcurrentHashMap.newKeySet();
    private NeoForgeTransmutationMediaTrade() {}

    public static boolean currentMenu(ServerPlayer player, int menuId) {
        return player != null && player.server.isSameThread() && player.isAlive()
                && player.containerMenu instanceof TransmutationContainer menu
                && menu.containerId == menuId && menu.stillValid(player);
    }

    public static NeoForgeMediaTradeResult trade(ServerPlayer player, int menuId, boolean buy,
                                                 boolean chemical, ResourceLocation selected) {
        if (!currentMenu(player, menuId)) return NeoForgeMediaTradeResult.failure(Status.INVALID_RESOURCE);
        if (player.containerMenu.getCarried().isEmpty()) return NeoForgeMediaTradeResult.failure(Status.EMPTY);
        if (!ACTIVE.add(player)) return NeoForgeMediaTradeResult.failure(Status.NO_TRANSFER);
        try {
            if (selected != null && !validResource(chemical, selected))
                return NeoForgeMediaTradeResult.failure(Status.INVALID_RESOURCE);
            NeoForgeCarriedContainerTransaction transaction = new NeoForgeCarriedContainerTransaction(player);
            NeoForgeMediaContainer probe = open(transaction.simulationCopy(), chemical, selected);
            if (probe == null) return NeoForgeMediaTradeResult.failure(Status.NO_CONTAINER);
            ResourceLocation id = probe.id();
            if (id == null) return NeoForgeMediaTradeResult.failure(Status.EMPTY);
            boolean energy = !chemical && Kind.ENERGY.unit().equals(id);
            if (id.toString().length() > NeoForgeTransmutationPayloads.MAX_ID
                    || !validResource(chemical, id))
                return NeoForgeMediaTradeResult.failure(Status.INVALID_RESOURCE);
            if (!energy && !(chemical ? buy ? NeoForgeServerConfig.gasPurchaseEnabled() : NeoForgeServerConfig.gasSaleEnabled()
                    : buy ? NeoForgeServerConfig.fluidPurchaseEnabled() : NeoForgeServerConfig.fluidSaleEnabled()))
                return NeoForgeMediaTradeResult.failure(Status.DISABLED);
            if (buy && !(energy ? NeoForgeResourceKnowledge.allows(player.server, player.getUUID(), Kind.ENERGY)
                    : NeoForgeResourceKnowledge.allowsMedia(player.server, player.getUUID(), chemical ? Kind.GAS : Kind.FLUID, id)))
                return NeoForgeMediaTradeResult.failure(Status.UNKNOWN);
            FluidStack sample = probe.fluid().copy();
            if (!chemical && !energy && buy && !FluidStack.isSameFluidSameComponents(sample, new FluidStack(sample.getFluid(), 1)))
                return NeoForgeMediaTradeResult.failure(Status.INVALID_RESOURCE);
            long maximum = chemical ? Long.MAX_VALUE : Integer.MAX_VALUE;
            long requested = probe.move(maximum, buy, true);
            if (requested < 0 || requested > maximum) return NeoForgeMediaTradeResult.failure(Status.ERROR);
            if (requested == 0) return NeoForgeMediaTradeResult.failure(buy ? Status.FULL : Status.NO_TRANSFER);
            NeoForgeMediaContainer working = open(transaction.copy(), chemical, id);
            if (working instanceof FluidContainer fluid) working = new FluidContainer(fluid.handler(), sample);
            if (working == null || !id.equals(working.id()) || !transaction.unchanged())
                return NeoForgeMediaTradeResult.failure(Status.NO_TRANSFER);
            EmcAmount price = energy ? NeoForgeRecoveryPrices.energy() : chemical
                    ? buy ? NeoForgeRecoveryPrices.chemicalPurchase(id, NeoForgeMediaRegistry.chemicalTags(id))
                          : NeoForgeRecoveryPrices.chemical(id, NeoForgeMediaRegistry.chemicalTags(id))
                    : buy ? NeoForgeRecoveryPrices.fluidPurchase(sample) : NeoForgeRecoveryPrices.fluid(sample);
            if (price.isZero()) return NeoForgeMediaTradeResult.failure(Status.UNPRICED);
            NeoForgeMediaContainer executing = working;
            boolean[] capabilityFailed = {false};
            LongUnaryOperator transfer = authorized -> {
                // Only detached capability work is caught as zero delivery. Never refund an
                // uncertain exception after the real container has already been committed.
                long actual;
                ItemStack result;
                try {
                    actual = executing.move(authorized, buy, false);
                    if (actual < 0 || actual > authorized) { capabilityFailed[0] = true; return 0; }
                    if (actual == 0) return 0;
                    result = executing.result();
                } catch (RuntimeException | LinkageError failedCopy) {
                    LogUtils.getLogger().warn("[Primordial] Detached media capability failed; original container retained", failedCopy);
                    capabilityFailed[0] = true;
                    return 0;
                }
                return transaction.commit(result) ? actual : 0;
            };
            long actual = energy
                    ? buy ? NeoForgeResourceTrades.purchaseEnergy(player.server, player.getUUID(), requested, transfer)
                          : NeoForgeResourceTrades.reclaimEnergy(player.server, player.getUUID(), requested, transfer)
                    : chemical
                    ? buy ? NeoForgeResourceTrades.purchaseChemical(player.server, player.getUUID(), id,
                                NeoForgeMediaRegistry.chemicalTags(id), requested, transfer)
                          : NeoForgeResourceTrades.reclaimChemical(player.server, player.getUUID(), id,
                                NeoForgeMediaRegistry.chemicalTags(id), requested, transfer)
                    : buy ? NeoForgeResourceTrades.purchaseFluid(player.server, player.getUUID(), sample, requested, transfer)
                          : NeoForgeResourceTrades.reclaimFluid(player.server, player.getUUID(), sample, requested, transfer);
            if (actual > 0) {
                player.containerMenu.broadcastChanges();
                return NeoForgeMediaTradeResult.success(id.toString(), actual, price);
            }
            if (capabilityFailed[0]) return NeoForgeMediaTradeResult.failure(Status.ERROR);
            if (transaction.failure() == Status.INVENTORY_FULL) return NeoForgeMediaTradeResult.failure(Status.INVENTORY_FULL);
            return NeoForgeMediaTradeResult.failure(buy ? Status.NO_FUNDS : Status.NO_TRANSFER);
        } catch (RuntimeException | LinkageError failure) {
            LogUtils.getLogger().warn("[Primordial] Carried media trade rejected for {}", player.getUUID(), failure);
            return NeoForgeMediaTradeResult.failure(Status.ERROR);
        } finally { ACTIVE.remove(player); }
    }

    private static boolean validResource(boolean chemical, ResourceLocation id) {
        return !chemical && Kind.ENERGY.unit().equals(id)
                || NeoForgeResourceKnowledge.validMedia(chemical ? Kind.GAS : Kind.FLUID, id);
    }

    private static NeoForgeMediaContainer open(ItemStack copy, boolean chemical, ResourceLocation selected) {
        if (chemical) return NeoForgeCarriedMekanismBridge.open(copy, selected);
        if (Kind.ENERGY.unit().equals(selected)) return energy(copy);
        IFluidHandlerItem handler = copy.getCapability(Capabilities.FluidHandler.ITEM);
        // A real fluid port, including an empty or full one, retains priority. An
        // explicit fluid selection never silently buys another resource instead.
        if (handler == null) return selected == null ? energy(copy) : null;
        FluidStack sample = selected == null ? FluidStack.EMPTY : new FluidStack(BuiltInRegistries.FLUID.get(selected), 1);
        if (selected == null) for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stored = handler.getFluidInTank(tank);
            if (!stored.isEmpty()) { sample = stored.copyWithAmount(1); break; }
        }
        return new FluidContainer(handler, sample);
    }

    private static NeoForgeMediaContainer energy(ItemStack copy) {
        IEnergyStorage storage = copy.getCapability(Capabilities.EnergyStorage.ITEM);
        return storage == null ? null : new EnergyContainer(copy, storage);
    }

    private record EnergyContainer(ItemStack result, IEnergyStorage storage) implements NeoForgeMediaContainer {
        @Override public ResourceLocation id() { return Kind.ENERGY.unit(); }
        @Override public long move(long maximum, boolean buy, boolean simulate) {
            int limit = (int) Math.min(Integer.MAX_VALUE, maximum);
            return buy ? storage.canReceive() ? storage.receiveEnergy(limit, simulate) : 0
                    : storage.canExtract() ? storage.extractEnergy(limit, simulate) : 0;
        }
    }

    private record FluidContainer(IFluidHandlerItem handler, FluidStack fluid) implements NeoForgeMediaContainer {
        @Override public ResourceLocation id() { return fluid.isEmpty() ? null : BuiltInRegistries.FLUID.getKey(fluid.getFluid()); }
        @Override public ItemStack result() { return handler.getContainer(); }
        @Override public long move(long maximum, boolean buy, boolean simulate) {
            FluidStack proposal = fluid.copyWithAmount((int) Math.min(Integer.MAX_VALUE, maximum));
            var action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
            int manual = NeoForgeCarriedMekanismBridge.manualFluid(handler, proposal, buy, action);
            if (manual >= 0) return manual;
            if (buy) return handler.fill(proposal, action);
            FluidStack removed = handler.drain(proposal, action);
            return FluidStack.isSameFluidSameComponents(removed, proposal) ? removed.getAmount() : 0;
        }
    }
}
