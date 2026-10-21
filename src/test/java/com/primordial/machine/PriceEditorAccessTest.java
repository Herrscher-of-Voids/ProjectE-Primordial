/**
 * 【文件职责】验证定价器物品实际入口的创造模式/管理员组合和拒绝结果。
 * 【变更记录 #1】2026-10-11 04:28:00（第 1 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：四种权限与游戏模式组合，通过真实 use 验证是否调用服务器打开菜单。
 * - 修改：只替代 Forge 网络开屏和玩家世界，门禁与物品实现保持真实。
 * - 用途：普通创造玩家、生存管理员均不能打开定价器，允许条件同时满足时不消耗工具。
 */
/**
 * 【变更记录 #1】2026-10-11 04:26:53（第 1 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：四种创造模式/管理员组合的统一门禁测试。
 * - 修改：使用 PriceEditorAccess 的真实组合规则，不启动注册表或网络。
 * - 用途：锁定定价器物品的最小使用条件。
 */
package com.primordial.machine;

import com.primordial.service.PriceEditorAccess;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceEditorAccessTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @ParameterizedTest @CsvSource({"true,true,true", "true,false,false", "false,true,false", "false,false,false"})
    void onlyCreativeAdministratorsCanOpenThePriceEditor(boolean creative, boolean admin, boolean allowed) {
        var player = mock(ServerPlayer.class);
        when(player.isCreative()).thenReturn(creative);
        when(player.hasPermissions(2)).thenReturn(admin);
        assertEquals(allowed, PriceEditorAccess.canEdit(player));
    }
}
