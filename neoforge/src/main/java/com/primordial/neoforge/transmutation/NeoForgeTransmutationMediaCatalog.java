/**
 * 【文件职责】为原生转化桌介质页生成服务端知识、阶段与购买价格共同过滤的有界目录。
 * 【变更记录 #1】2026-10-23 21:06:00（第 1 次备注 · 2.0.0-neoforge-media-catalog）
 * - 删除：无。新增：源流体/可选统一化学品注册表查询、大小写无关搜索和 12 项非重叠分页。
 * - 修改：复用生产知识与购买价，按原生知识槽 16 项显示、每页前进 12 项，不按余额隐藏资源。
 * - 用途：网络只传注册 ID；目录展示不构成交易授权，真正购买仍需重新验证知识、价格和实际交付。
 */
package com.primordial.neoforge.transmutation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeTransmutationMediaCatalog {
    public static final int PAGE_SIZE = 16;
    public static final int PAGE_STRIDE = 12;
    public static final int MAX_QUERY = 64;
    public static final int MAX_PAGE = 10000;
    public static final int MAX_ID = 128;
    private static final Page EMPTY = new Page(List.of(), 0);

    public record Page(List<String> ids, int total) {
        public Page { ids = List.copyOf(ids); }
    }

    private NeoForgeTransmutationMediaCatalog() {}

    public static Page search(ServerPlayer player, boolean chemical, String query, int page) {
        return player == null ? EMPTY : search(player.getServer(), player.getUUID(), chemical, query, page);
    }

    public static Page search(MinecraftServer server, UUID owner, boolean chemical, String query, int page) {
        if (server == null || owner == null || !server.isSameThread() || server.overworld() == null
                || query == null || query.length() > MAX_QUERY || page < 0 || page > MAX_PAGE
                || NeoForgeResourceKnowledge.revision(server, owner) < 0) return EMPTY;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        Kind kind = chemical ? Kind.GAS : Kind.FLUID;
        Set<ResourceLocation> candidates = chemical ? NeoForgeMediaRegistry.chemicalIds()
                : BuiltInRegistries.FLUID.keySet();
        List<String> matches = new ArrayList<>();
        for (ResourceLocation id : candidates) {
            if (id.toString().length() > MAX_ID || !id.toString().contains(needle)
                    || !NeoForgeResourceKnowledge.allowsMedia(server, owner, kind, id)) continue;
            boolean priced;
            try {
                priced = chemical ? !NeoForgeRecoveryPrices.chemicalPurchase(id, NeoForgeMediaRegistry.chemicalTags(id)).isZero()
                        : !NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(BuiltInRegistries.FLUID.get(id), 1)).isZero();
            } catch (RuntimeException | LinkageError unavailable) { priced = false; }
            if (priced && NeoForgeResourceKnowledge.allowsMedia(server, owner, kind, id)) matches.add(id.toString());
        }
        matches.sort(String::compareTo);
        long start = (long) page * PAGE_STRIDE;
        if (start >= matches.size()) return new Page(List.of(), matches.size());
        return new Page(matches.subList((int) start, Math.min(matches.size(), (int) start + PAGE_SIZE)), matches.size());
    }
}
