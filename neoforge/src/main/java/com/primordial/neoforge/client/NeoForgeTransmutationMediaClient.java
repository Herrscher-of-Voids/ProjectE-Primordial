/**
 * 【文件职责】给原生转化桌界面提供目录请求、只读缓存与遗忘后的权威刷新。
 * 【变更记录 #1】2026-10-23 20:54:00（第 1 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 删除：无。新增：有界客户端发送 API、菜单复核和递增请求代数。
 * - 修改：不在本地推断知识或遗忘成功；收到服务端回执后重新查询目录。
 * - 用途：切页/搜索和关闭界面后到达的旧消息不会覆盖当前结果。
 */
package com.primordial.neoforge.client;

import com.primordial.neoforge.transmutation.NeoForgeTransmutationMediaCatalog;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationMediaState;
import com.primordial.neoforge.transmutation.NeoForgeTransmutationPayloads;
import com.primordial.neoforge.transmutation.NeoForgeMediaTradePayloads;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoForgeTransmutationMediaClient {
    private static final NeoForgeTransmutationMediaState STATE = new NeoForgeTransmutationMediaState();
    private static long sequence;
    private static NeoForgeMediaTradePayloads.Trade pendingTrade;
    private NeoForgeTransmutationMediaClient() {}

    public static void requestCatalog(int menuId, boolean chemical, String query, int page) {
        if (!currentMenu(menuId) || query == null || query.length() > NeoForgeTransmutationMediaCatalog.MAX_QUERY
                || page < 0 || page > NeoForgeTransmutationMediaCatalog.MAX_PAGE) return;
        var request = new NeoForgeTransmutationPayloads.RequestCatalog(menuId, chemical, query, page, nextSequence());
        STATE.begin(request);
        PacketDistributor.sendToServer(request);
    }

    /** Null while waiting for this exact query, or after the screen closes. */
    public static NeoForgeTransmutationPayloads.Catalog currentCatalog(int menuId, boolean chemical, String query, int page) {
        var result = STATE.catalog();
        return currentMenu(menuId) && result != null && result.menuId() == menuId && result.chemical() == chemical
                && result.query().equals(query) && result.page() == page ? result : null;
    }

    public static void forget(int menuId, boolean chemical, String id) {
        if (currentMenu(menuId) && id != null && id.length() <= NeoForgeTransmutationPayloads.MAX_ID && ResourceLocation.tryParse(id) != null)
            PacketDistributor.sendToServer(new NeoForgeTransmutationPayloads.Forget(menuId, chemical, id));
    }
    public static void trade(int menuId, boolean buy, boolean chemical, String id) {
        if (!currentMenu(menuId) || pendingTrade != null || id == null || id.length() > NeoForgeTransmutationPayloads.MAX_ID
                || !id.isEmpty() && ResourceLocation.tryParse(id) == null) return;
        pendingTrade = new NeoForgeMediaTradePayloads.Trade(menuId, buy, chemical, id, nextSequence());
        PacketDistributor.sendToServer(pendingTrade);
    }
    public static boolean trading() { return pendingTrade != null; }
    public static void clear() { STATE.clear(); pendingTrade = null; }
    public static void acceptTrade(NeoForgeMediaTradePayloads.Receipt receipt) {
        var pending = pendingTrade;
        if (!currentMenu(receipt.menuId()) || pending == null || pending.menuId() != receipt.menuId()
                || pending.sequence() != receipt.sequence() || pending.buy() != receipt.buy() || pending.chemical() != receipt.chemical()) return;
        pendingTrade = null;
        // Forge 1.20.1 keeps purchases quiet; successful reclamation reveals newly learned media.
        if (receipt.result().traded() && !receipt.buy()) NeoForgeTransmutationPages.mediaReclaimed();
    }
    public static void acceptCatalog(NeoForgeTransmutationPayloads.Catalog catalog) {
        if (currentMenu(catalog.menuId())) STATE.accept(catalog);
    }
    public static void acceptForgetResult(NeoForgeTransmutationPayloads.ForgetResult result) {
        var request = STATE.request();
        if (currentMenu(result.menuId()) && request != null && request.menuId() == result.menuId() && request.chemical() == result.chemical())
            requestCatalog(request.menuId(), request.chemical(), request.query(), request.page());
    }

    private static boolean currentMenu(int menuId) {
        var player = Minecraft.getInstance().player;
        return player != null && player.containerMenu instanceof TransmutationContainer && player.containerMenu.containerId == menuId;
    }
    private static long nextSequence() { sequence = sequence == Long.MAX_VALUE ? 0 : sequence + 1; return sequence; }
}
