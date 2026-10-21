/**
 * 【文件职责】集中定义 Primordial 所有客户端界面的颜色和基础视觉常量。
 *
 * 【变更记录 #1】2026-09-15 15:25:00（第 1 次备注 · 1.0.0-stage9-gui-style）
 * - 删除：配置器、玩家背包和价格编辑界面各自重复的颜色字面量。
 * - 新增：背景、面板、边框、槽位、选择状态和文本颜色的统一调色板。
 * - 修改：无业务逻辑修改；调用方只替换颜色来源，不改变坐标、控件尺寸或交互。
 * - 用途：保证新增护符界面和现有配置器使用一致的深色 EMC 风格，集中调整时避免页面间颜色漂移。
 */
package com.primordial.screen;

/** 仅保存不可变 ARGB 颜色，供客户端渲染线程直接读取。 */
public final class PrimordialGuiStyle {
    public static final int SCREEN_BACKGROUND = 0xF0080C12;
    public static final int PANEL_BACKGROUND = 0xCC101820;
    public static final int PANEL_BORDER = 0xFF3A5A7A;
    public static final int SLOT_BACKGROUND = 0xFF202830;
    public static final int SLOT_BORDER = 0xFF5A6A7A;
    public static final int SLOT_FRAME = 0xFF373737;
    public static final int SLOT_EMPTY = 0xFF8B8B8B;
    public static final int SELECTION = 0xFF55FFFF;
    public static final int PRIMARY_TEXT = 0xFF7FDFFF;
    public static final int SECONDARY_TEXT = 0xFFAAAAAA;
    public static final int WARNING_TEXT = 0xFFFFAA55;

    private PrimordialGuiStyle() {}
}
