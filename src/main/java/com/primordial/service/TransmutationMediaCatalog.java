/**
 * 【文件职责】按玩家已学习的流体或气体生成转化桌搜索结果，输出有界分页 ID。
 * 【变更记录 #1】2026-10-02 17:18:00（第 1 次备注 · 1.2.0-media-catalog）
 * - 删除：无。
 * - 新增：服务端逐种知识和类别凭证合并查询、价格过滤、注册名搜索与分页。
 * - 修改：无既有交易逻辑；最终购买仍由 TransmutationMediaTrade 校验。
 * - 用途：让客户端只接收可选的少量资源 ID，避免整类凭证发送庞大注册表。
 */
/**
 * 【变更记录 #2】2026-10-02 18:00:13（第 2 次备注 · 1.2.0-media-tab）
 * - 删除：五项分页上限。
 * - 新增：无。
 * - 修改：每页改为 27 项以填满独立介质页的 9×3 网格。
 * - 用途：充分利用转化桌上半部空间，仍限制单次目录回包大小。
 */
/**
 * 【变更记录 #3】2026-10-02 18:48:00（第 3 次备注 · 1.2.0-media-forgetting）
 * - 删除：整类凭证目录绕过单种知识检查的路径。
 * - 新增：候选资源经过统一 allowsMedia 过滤。
 * - 修改：先排除遗忘项再统计和分页，凭证无法使其重新出现。
 * - 用途：目录与服务端实际购买资格一致。
 */
/**
 * 【变更记录 #4】2026-10-10 03:53:52（第 4 次备注 · 1.2.0-resource-system-one）
 * - 删除：仅卖模式仍作为可购买资源出现在目录中的差异。
 * - 新增：目录过滤统一交易模式。
 * - 修改：分页统计和候选过滤在 canBuy 检查后进行，原类别凭证、遗忘、阶段和黑名单继续生效。
 * - 用途：让玩家选到的资源与服务端购买资格一致，减少可选图标点击后必然失败的情况。
 */
/**
 * 【变更记录 #5】2026-10-10 03:58:22（第 5 次备注 · 1.2.0-resource-system-one）
 * - 删除：FE 容器购买仅能自动补充、没有资源选择入口的缺口。
 * - 新增：目录中的 forge:fe 单位，按能源凭证、阶段和 FE 独立报价过滤。
 * - 修改：流体/FE 页统一排序分页；气体页不混入能源，流体类别凭证不能解锁 FE。
 * - 用途：让现有能源资源也具有可发现的转化桌买卖入口，而不是要求玩家记忆注册名。
 */
/**
 * 【变更记录 #6】2026-10-12 14:35:41（第 6 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：无；保留原有玩家知识、凭证、阶段、价格和查询筛选。
 * - 新增：无。
 * - 修改：每页容量从 27 项调整为 16 项，与 ProjectE 原生右侧 16 个知识槽一一对应；目录查询及总数排序规则不变。
 * - 用途：为介质知识印记提供匹配原生槽位数量的有界分页，交易仍由 TransmutationMediaTrade 在服务端复核。
 */
/**
 * 【变更记录 #7】2026-10-12 14:52:00（第 7 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：无。
 * - 新增：无。
 * - 修改：目录页容量改为 16 槽，页码偏移改为 `page * 12`；相邻页因此保留原版 4 项重叠，知识过滤、排序和价格判断不变。
 * - 用途：精确对应 ProjectE 的 16 个输出槽和 `searchpage * 12` 翻页算法。
 */
/**
 * 【变更记录 #8】2026-10-12 15:03:11（第 8 次备注 · 1.2.0-transmutation-media-controls）
 * - 删除：误将知识槽容量等同翻页步长的 12 项页容量。
 * - 新增：16 项 PAGE_SIZE 与 12 项 PAGE_STRIDE 常量。
 * - 修改：目录起始索引改为 page * 12、结束索引最多取 16 项，与原版输出槽满页及搜索页重叠方式一致。
 * - 用途：让服务端目录翻页数据准确对应 ProjectE 原生按钮和 16 个右侧槽位。
 */
/**
 * 【变更记录 #9】2026-10-12 23:38:49（第 9 次备注 · 1.2.0-media-real-slots）
 * - 删除：旧介质页不完整的交互路径。
 * - 新增：外圈 12 格按 12 步长展示介质，内圈 FE 知识独立，消除跨页重复资源。
 * - 修改：对照 ProjectE 真实槽位及原生控件处理。
 * - 用途：介质页状态、容器安全与显示一致，保留全部历史。
 */
/**
 * 【变更记录 #10】2026-10-13 01:53:18（第 10 次备注 · 1.2.0-media-feedback）
 * - 删除：FE 目录条目及填充内圈空条目，目录只发送当前类别流体或气体知识。
 * - 新增：无新增玩家配置。
 * - 修改：按用户本轮界面与无限速交易要求调整，价格/知识授权仍由服务端决定。
 * - 用途：界面清晰、交易按实际容量和交付量计费；保留所有历史。
 */
package com.primordial.service;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialKnowledgeConfig;
import com.primordial.data.MediaKnowledgeSavedData;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class TransmutationMediaCatalog {
    public static final int PAGE_SIZE = 16;
    public static final int PAGE_STRIDE = 12;
    private TransmutationMediaCatalog() { }

    public record Page(List<String> ids, int total) { }

    public static Page search(ServerPlayer player, boolean gas, String query, int page) {
        if (player == null || player.server == null || !player.server.isSameThread()
                || page < 0 || page > 10000 || query == null || query.length() > 64) return new Page(List.of(), 0);
        String needle = query.toLowerCase(Locale.ROOT).trim();
        ResourceKnowledge.Kind kind = gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID;
        boolean all = !PrimordialKnowledgeConfig.required()
                || ResourceKnowledge.allows(player.server, player.getUUID(), kind);
        Set<ResourceLocation> candidates = all ? (gas ? MekanismGasCompat.gasIds() : BuiltInRegistries.FLUID.keySet())
                : player.server.overworld() == null ? Set.of()
                : MediaKnowledgeSavedData.get(player.server).knownIds(player.getUUID(), gas);
        List<String> matches = candidates.stream().filter(id -> id.toString().contains(needle))
                .filter(id -> ResourceKnowledge.allowsMedia(player.server, player.getUUID(), kind, id))
                .filter(id -> gas ? MekanismGasCompat.validGas(id) && com.primordial.value.ValueManager.canBuyGas(id) && !EmcService.gasPrice(id).isZero()
                        : BuiltInRegistries.FLUID.containsKey(id)
                        && BuiltInRegistries.FLUID.get(id).defaultFluidState().isSource()
                        && com.primordial.value.ValueManager.canBuyFluid(BuiltInRegistries.FLUID.get(id))
                        && !EmcService.fluidPrice(BuiltInRegistries.FLUID.get(id)).isZero())
                .map(ResourceLocation::toString).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        matches.sort(String::compareTo);
        long start = (long) page * PAGE_STRIDE;
        List<String> ids = new java.util.ArrayList<>();
        if (start < matches.size()) ids.addAll(matches.subList((int) start,
                Math.min(matches.size(), (int) start + PAGE_STRIDE)));
        return new Page(ids, matches.size());
    }
}
