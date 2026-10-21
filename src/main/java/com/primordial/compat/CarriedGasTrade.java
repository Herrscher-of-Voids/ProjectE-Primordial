/**
 * 【文件职责】在 Mekanism 已安装后交易单个气体容器副本。
 * 【变更记录 #1】2026-10-10 03:53:52（第 1 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：独立的气体容器买卖实现、实际抽取/插入量结算和容器提交回调；抽空前复制气体身份。
 * - 修改：复用统一 EMC 结算及服务端主线程，不接受客户端价格或实际数量。
 * - 用途：完整提供资源系统的真实交易与失败保护；可选 API 保持在兼容层。
 */
/**
 * 【变更记录 #2】2026-10-10 04:03:51（第 2 次备注 · 1.2.0-resource-system-one）
 * - 删除：自动识别时无气体能力的 FE 容器被气体交易开关阻断的路径。
 * - 新增：先识别容器能力再检查气体类别开关。
 * - 修改：无气体内容返回 EMPTY，已确定的气体满罐仍不进入 FE 交易回退。
 * - 用途：确保液体、气体交易开关独立于能源资源，空容器失败提示也保持准确。
 */
/**
 * 【变更记录 #3】2026-10-10 04:16:28（第 3 次备注 · 1.2.0-resource-system-one）
 * - 删除：自动补充已满气罐时把失败状态保留为 EMPTY 的路径。
 * - 新增：识别可交易气体后明确标记满罐状态。
 * - 修改：满气罐返回 FULL，不再被自动识别误认为空内容后转向 FE。
 * - 用途：混合能力容器每次只交易一种资源，失败反馈与实际容量一致。
 */
/**
 * 【变更记录 #4】2026-10-13 01:53:18（第 4 次备注 · 1.2.0-media-feedback）
 * - 删除：Mekanism 气体模拟/执行单次自动化速率上限；手动交易按储罐容量与余额决定数量。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
/**
 * 【变更记录 #5】2026-10-13 19:40:05（第 5 次备注 · 1.2.0-resource-gates-system）
 * - 删除：此入口绕过独立资源操作门禁的旧路径。
 * - 新增：统一 MediaAccessPolicy 校验。
 * - 修改：按气体罐资源 ID 检查 BUY/SELL，受限气体跳过并继续查其它罐，复用原禁用结果。
 * - 用途：资源配置在界面、自动化与结算端一致生效，保留原价格、阶段和知识存档。
 */
/**
 * 【变更记录 #6】2026-10-13 20:14:16（第 6 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：无额外限制；删除新加的独立操作策略调用及关联包装，恢复原知识、价格和 EMC 结算路径。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
package com.primordial.compat;

import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.service.ResourceKnowledge;
import com.primordial.service.ResourceTradeResult;
import com.primordial.service.ResourceTradeResult.Status;
import com.primordial.value.ValueManager;
import com.primordial.config.PrimordialServerConfig;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.function.Predicate;

/** 只由已检查 loaded() 的兼容入口调用，通用菜单和网络包不链接 Mekanism 类型。 */
final class CarriedGasTrade {
    private CarriedGasTrade() { }

    static ResourceTradeResult trade(ServerPlayer player, ItemStack copy, boolean buy,
                                    ResourceLocation selected, Predicate<ItemStack> commit) {
        if (selected != null && !MekanismGasCompat.validGas(selected))
            return ResourceTradeResult.failure(Status.INVALID_RESOURCE);
        var handler = copy.getCapability(Capabilities.GAS_HANDLER).orElse(null);
        if (handler == null) return ResourceTradeResult.failure(Status.NO_CONTAINER);
        if (buy ? PrimordialServerConfig.mediaPurchaseDisabled(true) : PrimordialServerConfig.mediaSaleDisabled(true))
            return ResourceTradeResult.failure(Status.DISABLED);

        Status failure = selected == null ? Status.EMPTY : Status.FULL;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            GasStack stored = handler.getChemicalInTank(tank).copy();
            if (selected == null && stored.isEmpty()) continue;
            ResourceLocation id = selected == null ? stored.getTypeRegistryName() : selected;
            var mode = ValueManager.gasMode(id);
            if (mode != null && !(buy ? mode.allowsBuy() : mode.allowsSell())) { failure = Status.DISABLED; continue; }
            EmcAmount price = EmcService.gasPrice(id);
            if (price.isZero()) { failure = Status.UNPRICED; continue; }
            if (buy && !ResourceKnowledge.allowsMedia(player.server, player.getUUID(), ResourceKnowledge.Kind.GAS, id)) {
                failure = Status.UNKNOWN; continue;
            }
            GasStack sample = selected == null ? stored : (GasStack) MekanismGasCompat.preview(id);
            if (!stored.isEmpty() && !stored.isTypeEqual(sample)) continue;
            if (buy) failure = Status.FULL;
            final int index = tank;
            long request = buy ? Math.max(0, handler.getTankCapacity(tank) - stored.getAmount()) : stored.getAmount();
            if (request <= 0) continue;
            request = Math.max(0, Math.min(request,
                    MekanismManualMedia.gas(handler, index, sample, request, buy, Action.SIMULATE)));
            if (request <= 0) continue;
            java.util.function.LongUnaryOperator move = units -> {
                long actual = com.primordial.service.TransmutationMediaTrade.detachedMove(() -> {
                    return Math.max(0, Math.min(units,
                            MekanismManualMedia.gas(handler, index, sample, units, buy, Action.EXECUTE)));
                });
                return actual > 0 && commit.test(copy) ? actual : 0;
            };
            long actual = buy ? EmcService.purchaseGas(player.server, player.getUUID(), id, request, move)
                    : EmcService.reclaimGas(player.server, player.getUUID(), id, request, move);
            if (actual > 0) return ResourceTradeResult.success(id.toString(), actual, price);
            // execute 已改变副本；不得用这个副本重试其它储罐。
            return ResourceTradeResult.failure(buy ? Status.NO_FUNDS : Status.NO_TRANSFER);
        }
        return ResourceTradeResult.failure(failure);
    }
}

