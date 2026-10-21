/**
 * 【文件职责】供整合包脚本和附属模组调用的服务端介质知识接口；不安装额外策略、不改变默认玩法。
 * 【变更记录 #1】2026-10-13 20:00:00（第 1 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：无。新增：按玩家 UUID 查询、授予、撤销单种流体/气体知识及购买解锁查询。
 * - 修改：操作复用原知识存档；授予清除显式遗忘，撤销保留遗忘记录以覆盖类别凭证，真实交易仍检查价格、阶段与余额。
 * - 用途：包作者主动调用才改变知识；无网络授权入口。只接受服务端主线程和有效资源 ID，变更后刷新存储网络目录。
 */
package com.primordial.api;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.service.EmcService;
import com.primordial.service.ResourceKnowledge;
import com.primordial.service.TransmutationNetworkTrade;
import com.primordial.value.ValueManager;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.material.Fluids;

public final class MediaKnowledgeApi {
    public enum Media { FLUID, GAS }
    private MediaKnowledgeApi() { }

    public static boolean validResource(Media media, ResourceLocation id) {
        if (media == null || id == null) return false;
        if (media == Media.GAS) return MekanismGasCompat.validGas(id);
        return BuiltInRegistries.FLUID.containsKey(id) && BuiltInRegistries.FLUID.get(id) != Fluids.EMPTY
                && BuiltInRegistries.FLUID.get(id).defaultFluidState().isSource();
    }

    /** 查询该资源是否被明确学习；类别凭证的整类授权不计为逐项学习。 */
    public static boolean hasLearned(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return ready(server, player, media, id) && MediaKnowledgeSavedData.get(server).knows(player, media == Media.GAS, id);
    }

    /** 授予只写知识，不赠送实体资源或 EMC；幂等调用返回是否真的变更。 */
    public static boolean learn(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        if (!ready(server, player, media, id)) return false;
        boolean changed = MediaKnowledgeSavedData.get(server).learn(player, media == Media.GAS, id);
        if (changed) TransmutationNetworkTrade.invalidate(player);
        return changed;
    }

    /** 不要求当前满足购买阶段；作者可在阶段锁定时撤销，之后重新卖入或授予可恢复。 */
    public static boolean forget(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        if (!ready(server, player, media, id)) return false;
        boolean changed = MediaKnowledgeSavedData.get(server).forget(player, media == Media.GAS, id);
        if (changed) TransmutationNetworkTrade.invalidate(player);
        return changed;
    }

    /** 检查知识/作者阶段/价格规则是否已解锁；不是余额、容器容量检查或交易承诺。 */
    public static boolean isPurchaseUnlocked(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        if (!ready(server, player, media, id)) return false;
        boolean gas = media == Media.GAS;
        if (PrimordialServerConfig.mediaPurchaseDisabled(gas)
                || !ResourceKnowledge.allowsMedia(server, player, gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID, id)) return false;
        return gas ? ValueManager.canBuyGas(id) && !EmcService.gasPrice(id).isZero()
                : ValueManager.canBuyFluid(BuiltInRegistries.FLUID.get(id))
                        && !EmcService.fluidPrice(BuiltInRegistries.FLUID.get(id)).isZero();
    }

    private static boolean ready(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return server != null && player != null && server.isSameThread() && server.overworld() != null && validResource(media, id);
    }
}
