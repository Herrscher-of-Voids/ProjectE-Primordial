/**
 * 【文件职责】供整合包和附属模组按 UUID 查询、授予或撤销服务端介质知识。
 * 【变更记录 #1】2026-10-23 13:34:30（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：与 Forge 同语义的 FLUID/GAS API；目标 GAS 对应全部统一化学品。
 * - 修改：只接受主线程、真实资源和健康账本；学习清遗忘，作者撤销不要求当前购买授权。
 * - 用途：授予只写知识，不赠 EMC 或实体资源；购买解锁查询不保证余额、容量或交付。
 */
package com.primordial.neoforge.api;

import java.util.UUID;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledgeSavedData;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MediaKnowledgeApi {
    public enum Media { FLUID, GAS }
    private MediaKnowledgeApi() {}

    public static boolean validResource(Media media, ResourceLocation id) {
        return media != null && NeoForgeResourceKnowledge.validMedia(kind(media), id);
    }

    /** 仅报告逐种学习，类别凭证授予的整类授权不混入逐种表。 */
    public static boolean hasLearned(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        if (!ready(server, player, media, id)) return false;
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        return media == Media.GAS ? data.knowsChemical(player, id) : data.knowsFluid(player, id);
    }

    public static boolean learn(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return ready(server, player, media, id)
                && NeoForgeRecoveryKnowledgeSavedData.get(server).learn(player, media == Media.GAS, id);
    }

    /** 信任的作者入口可以撤销尚未解锁的资源；玩家自身入口使用 forgetMedia 的授权检查。 */
    public static boolean forget(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        return ready(server, player, media, id)
                && NeoForgeRecoveryKnowledgeSavedData.get(server).forget(player, media == Media.GAS, id);
    }

    public static boolean isPurchaseUnlocked(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        if (!ready(server, player, media, id)
                || !NeoForgeResourceKnowledge.allowsMedia(server, player, kind(media), id)) return false;
        return media == Media.GAS
                ? !NeoForgeRecoveryPrices.chemicalPurchase(id, NeoForgeMediaRegistry.chemicalTags(id)).isZero()
                : !NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(BuiltInRegistries.FLUID.get(id), 1)).isZero();
    }

    private static Kind kind(Media media) { return media == Media.GAS ? Kind.GAS : Kind.FLUID; }

    private static boolean ready(MinecraftServer server, UUID player, Media media, ResourceLocation id) {
        if (server == null || player == null || !server.isSameThread() || server.overworld() == null
                || !validResource(media, id)) return false;
        try { return NeoForgeRecoveryKnowledgeSavedData.get(server).available(player); }
        catch (RuntimeException | LinkageError failure) { return false; }
    }
}
