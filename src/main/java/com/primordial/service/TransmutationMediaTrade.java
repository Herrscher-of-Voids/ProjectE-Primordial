/**
 * 【文件职责】从 ProjectE 转化桌光标上的单个容器卖入一种有价介质，并返还抽空后的原容器。
 * 【变更记录 #1】2026-10-02 16:24:00（第 1 次备注 · 1.2.0-transmutation-media-input）
 * - 删除：无。
 * - 新增：服务端容器校验、Forge 流体抽取和 Mekanism 可选气体抽取入口。
 * - 修改：成交数量只取真实抽取量；结算和逐种学习复用 EmcService。
 * - 用途：让转化桌直接卖入流体与气体，零价、空容器和堆叠容器均不成交。
 */
/**
 * 【变更记录 #2】2026-10-02 16:50:00（第 2 次备注 · 1.2.0-transmutation-media-output）
 * - 删除：无。
 * - 新增：对光标单个容器已存流体的按容量补充入口。
 * - 修改：模拟容量后预扣 EMC，按真实填充量退款；仍以服务端知识为门禁。
 * - 用途：完成已选流体的购买闭环，不允许空容器任意生成资源。
 */
/**
 * 【变更记录 #3】2026-10-02 17:05:00（第 3 次备注 · 1.2.0-transmutation-media-selection）
 * - 删除：无。
 * - 新增：由服务端按注册名选择已学习流体并模拟填充光标空容器。
 * - 修改：仍复用 purchaseFluid 的知识、价格、余额与实际交付结算。
 * - 用途：让玩家购买已学习流体时无需先持有同种流体；错误 ID 和不可填容器不会成交。
 */
/**
 * 【变更记录 #4】2026-10-10 03:53:52（第 4 次备注 · 1.2.0-resource-system-one）
 * - 删除：只允许单件容器和不反馈失败的交易分支，流体购买只按一个储罐估算容量。
 * - 新增：统一流体/气体/FE 交易结果、堆叠容器副本提交、完整流体容量模拟及 FE 容器双向交易。
 * - 修改：执行量裁剪到授权数并先提交容器，再按真实数量结算；副本能力异常不交付并退款，能源购买继续检查凭证与阶段。
 * - 用途：作为第 1 系统整体验收入口，避免背包不足、尾批、余额不足或能力异常时吞容器或错误计费。
 */
/**
 * 【变更记录 #5】2026-10-10 04:03:51（第 5 次备注 · 1.2.0-resource-system-one）
 * - 删除：自动识别无能力时覆盖有用空容器提示、满气罐继续回退 FE 的路径。
 * - 新增：自动识别保留实际已识别容器的失败状态。
 * - 修改：仅缺能力或空内容时尝试后续资源类别；真实成交或明确满容量不再转向其它资源。
 * - 用途：混合能力容器每次只处理一种资源，交易反馈与真实拒绝原因一致。
 */
/**
 * 【变更记录 #6】2026-10-13 01:53:18（第 6 次备注 · 1.2.0-media-feedback）
 * - 删除：Mekanism 流体单次自动化速率上限；模拟与执行共用 MANUAL 交互，副本提交和真实数量结算不变。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
/**
 * 【变更记录 #7】2026-10-13 19:40:05（第 7 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：手动流体交易在容器模拟和计费前检查 BUY/SELL，复用既有 DISABLED 结果，不增加玩家提示控件。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #8】2026-10-13 20:14:16（第 8 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
package com.primordial.service;

import com.primordial.PrimordialMod;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.energy.EnergyConversions;
import com.primordial.service.ResourceTradeResult.Status;
import com.primordial.value.ValueManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import java.util.function.LongUnaryOperator;

public final class TransmutationMediaTrade {
    private TransmutationMediaTrade() { }

    public static boolean sellCarried(ServerPlayer player) { return trade(player, false, false, null).traded(); }
    public static boolean buyCarried(ServerPlayer player) { return trade(player, true, false, null).traded(); }
    public static boolean buySelectedCarried(ServerPlayer player, boolean gas, ResourceLocation id) {
        return id != null && trade(player, true, gas, id).traded();
    }

    /** 所有资源只读取真实菜单与光标，修改独立的一件副本；成功交付后才提交实际结算数量。 */
    public static ResourceTradeResult trade(ServerPlayer player, boolean buy, boolean gas, ResourceLocation selected) {
        if (player == null || player.server == null || !player.server.isSameThread()
                || !(player.containerMenu instanceof moze_intel.projecte.gameObjs.container.TransmutationContainer))
            return ResourceTradeResult.failure(Status.INVALID_RESOURCE);
        if (player.containerMenu.getCarried().isEmpty()) return ResourceTradeResult.failure(Status.EMPTY);
        CarriedContainerTransaction transaction = new CarriedContainerTransaction(player);
        ItemStack copy = transaction.copy();
        ResourceTradeResult result;
        try {
            if (selected != null && EnergyConversions.FE.equals(selected) && !gas) {
                result = energy(player, transaction, buy);
            } else if (selected != null && gas) {
                result = MekanismGasCompat.tradeCarriedGas(player, copy, buy, selected, transaction::commit);
            } else {
                result = fluid(player, transaction, buy, selected);
                if (selected == null && (result.status() == Status.NO_CONTAINER || result.status() == Status.EMPTY)) {
                    ResourceTradeResult previous = result;
                    result = MekanismGasCompat.tradeCarriedGas(player, copy, buy, null, transaction::commit);
                    if (result.status() == Status.NO_CONTAINER) result = previous;
                    if (result.status() == Status.NO_CONTAINER || result.status() == Status.EMPTY) {
                        ResourceTradeResult energy = energy(player, transaction, buy);
                        if (energy.status() != Status.NO_CONTAINER) result = energy;
                    }
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn("[Primordial] carried resource trade failed for {}", player.getUUID(), failure);
            result = ResourceTradeResult.failure(Status.ERROR);
        }
        if (result.traded()) player.containerMenu.broadcastChanges();
        else if (transaction.failure() == Status.INVENTORY_FULL) result = ResourceTradeResult.failure(Status.INVENTORY_FULL);
        return result;
    }

    private static ResourceTradeResult fluid(ServerPlayer player, CarriedContainerTransaction transaction,
                                             boolean buy, ResourceLocation selected) {
        if (selected != null && (!BuiltInRegistries.FLUID.containsKey(selected)
                || !BuiltInRegistries.FLUID.get(selected).defaultFluidState().isSource()))
            return ResourceTradeResult.failure(Status.INVALID_RESOURCE);
        IFluidHandlerItem handler = transaction.copy().getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        if (handler == null) return ResourceTradeResult.failure(Status.NO_CONTAINER);
        FluidStack sample = selected == null ? FluidStack.EMPTY
                : new FluidStack(BuiltInRegistries.FLUID.get(selected), 1);
        if (selected == null) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                if (!handler.getFluidInTank(tank).isEmpty()) { sample = handler.getFluidInTank(tank).copy(); break; }
            }
        }
        if (sample.isEmpty()) return ResourceTradeResult.failure(Status.EMPTY);
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(sample.getFluid());
        var mode = ValueManager.fluidMode(id);
        if ((buy ? PrimordialServerConfig.mediaPurchaseDisabled(false) : PrimordialServerConfig.mediaSaleDisabled(false))
                || mode != null && !(buy ? mode.allowsBuy() : mode.allowsSell()))
            return ResourceTradeResult.failure(Status.DISABLED);
        EmcAmount price = EmcService.fluidPrice(sample.getFluid());
        if (price.isZero()) return ResourceTradeResult.failure(Status.UNPRICED);
        if (buy && !ResourceKnowledge.allowsMedia(player.server, player.getUUID(), ResourceKnowledge.Kind.FLUID, id))
            return ResourceTradeResult.failure(Status.UNKNOWN);
        FluidStack template = sample.copy();
        int request;
        if (buy) {
            long room = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stored = handler.getFluidInTank(tank);
                if (stored.isEmpty() || stored.isFluidEqual(template)) {
                    room = Math.min(Integer.MAX_VALUE, room + Math.max(0L,
                            (long) handler.getTankCapacity(tank) - stored.getAmount()));
                }
            }
            template.setAmount((int) room);
            request = Math.max(0, Math.min((int) room, manualFluid(handler, template, true, IFluidHandler.FluidAction.SIMULATE)));
        } else {
            template.setAmount(Integer.MAX_VALUE);
            request = manualFluid(handler, template, false, IFluidHandler.FluidAction.SIMULATE);
        }
        if (request <= 0) return ResourceTradeResult.failure(buy ? Status.FULL : Status.NO_TRANSFER);
        LongUnaryOperator move = units -> {
            FluidStack proposal = template.copy();
            proposal.setAmount((int) units);
            long actual = detachedMove(() -> {
                return manualFluid(handler, proposal, buy, IFluidHandler.FluidAction.EXECUTE);
            });
            actual = Math.max(0, Math.min(units, actual));
            return actual > 0 && transaction.commit(handler.getContainer()) ? actual : 0;
        };
        long actual = buy ? EmcService.purchaseFluid(player.server, player.getUUID(), sample.getFluid(), request, move)
                : EmcService.reclaimFluid(player.server, player.getUUID(), sample.getFluid(), request, move);
        return actual > 0 ? ResourceTradeResult.success(id.toString(), actual, price)
                : ResourceTradeResult.failure(buy ? Status.NO_FUNDS : Status.NO_TRANSFER);
    }

    /** 手动交互绕开 Mekanism 自动化速率，普通 Forge 容器沿用完整容量请求。 */
    public static int manualFluid(IFluidHandler handler, FluidStack sample, boolean buy, IFluidHandler.FluidAction action) {
        int manual = MekanismGasCompat.manualFluid(handler, sample, buy, action);
        if (manual >= 0) return manual;
        if (buy) return handler.fill(sample, action);
        FluidStack drained = handler.drain(sample, action);
        return drained.isFluidEqual(sample) ? drained.getAmount() : 0;
    }

    private static ResourceTradeResult energy(ServerPlayer player, CarriedContainerTransaction transaction, boolean buy) {
        var storage = transaction.copy().getCapability(ForgeCapabilities.ENERGY).orElse(null);
        if (storage == null) return ResourceTradeResult.failure(Status.NO_CONTAINER);
        if (PrimordialServerConfig.resourceBlocked(EnergyConversions.FE)) return ResourceTradeResult.failure(Status.DISABLED);
        if (buy && !ResourceKnowledge.allows(player.server, player.getUUID(), ResourceKnowledge.Kind.ENERGY))
            return ResourceTradeResult.failure(Status.UNKNOWN);
        EmcAmount price = EmcService.energyPrice(EnergyConversions.FE);
        if (price.isZero()) return ResourceTradeResult.failure(Status.UNPRICED);
        int requested = buy ? storage.canReceive() ? storage.receiveEnergy(Integer.MAX_VALUE, true) : 0
                : storage.canExtract() ? storage.extractEnergy(Integer.MAX_VALUE, true) : 0;
        if (requested <= 0) return ResourceTradeResult.failure(buy ? Status.FULL : Status.EMPTY);
        LongUnaryOperator move = units -> {
            long actual = detachedMove(() -> buy ? storage.receiveEnergy((int) units, false) : storage.extractEnergy((int) units, false));
            actual = Math.max(0, Math.min(units, actual));
            return actual > 0 && transaction.commit(transaction.copy()) ? actual : 0;
        };
        long actual = buy ? EmcService.purchase(player.server, player.getUUID(), price, requested, move)
                : EmcService.reclaim(player.server, player.getUUID(), price, requested, move);
        return actual > 0 ? ResourceTradeResult.success(EnergyConversions.FE.toString(), actual, price)
                : ResourceTradeResult.failure(buy ? Status.NO_FUNDS : Status.NO_TRANSFER);
    }

    /** 这里只执行副本能力；抛错时不交付容器、返回零让购买事务退款。提交容器的异常不在此捕获。 */
    public static long detachedMove(java.util.function.LongSupplier move) {
        try { return move.getAsLong(); }
        catch (RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn("[Primordial] detached container capability rejected resource transfer", failure);
            return 0;
        }
    }
}
