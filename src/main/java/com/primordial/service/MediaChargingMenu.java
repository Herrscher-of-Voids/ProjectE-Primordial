/**
 * 【文件职责】提供转化桌介质页的双端槽位状态，不依赖客户端 GUI。
 * 【变更记录 #1】2026-10-12 23:39:00（第 1 次备注 · 1.2.0-media-real-slots）
 * - 删除：无。新增：介质页状态和固定追加槽位索引。
 * - 修改：状态由菜单请求同步；用途：真实充电槽只在介质页接受容器。
 */
package com.primordial.service;

public interface MediaChargingMenu {
    int FIRST_SLOT = 73;
    int FORGET_SLOT = 81;
    void primordial$setMediaPage(boolean active, boolean gas);
    boolean primordial$mediaPage();
    boolean primordial$gasPage();
}
