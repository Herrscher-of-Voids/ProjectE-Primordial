/**
 * 【文件职责】按菜单、类别、查询、页码和请求代数接受目录回包；不依赖客户端游戏类。
 * 【变更记录 #1】2026-10-23 20:54:00（第 1 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 删除：无。新增：可自动验证的目录缓存及严格请求匹配。
 * - 修改：开始新请求立即清除旧显示，回到同一查询也不会接受旧代回包。
 * - 用途：网络延迟和关闭/重开菜单不能使界面展示另一页或另一玩家会话的数据。
 */
package com.primordial.neoforge.transmutation;

public final class NeoForgeTransmutationMediaState {
    private NeoForgeTransmutationPayloads.RequestCatalog request;
    private NeoForgeTransmutationPayloads.Catalog catalog;

    public void begin(NeoForgeTransmutationPayloads.RequestCatalog value) {
        request = value;
        catalog = null;
    }
    public void clear() { request = null; catalog = null; }
    public NeoForgeTransmutationPayloads.RequestCatalog request() { return request; }
    public NeoForgeTransmutationPayloads.Catalog catalog() { return catalog; }
    public boolean accept(NeoForgeTransmutationPayloads.Catalog value) {
        if (request == null || value == null || value.menuId() != request.menuId() || value.chemical() != request.chemical()
                || !value.query().equals(request.query()) || value.page() != request.page() || value.sequence() != request.sequence()) return false;
        catalog = value;
        return true;
    }
}
