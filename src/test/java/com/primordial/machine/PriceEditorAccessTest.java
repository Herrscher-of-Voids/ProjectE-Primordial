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
/**
 * 【变更记录 #2】2026-10-23 16:57:02（第 2 次备注 · 2.0.0-neoforge-pricing 双版本权限对齐）
 * - 删除：生存 OP 被拒绝的旧断言；保留全部旧历史。
 * - 新增：单人主人、LAN 访客、专服、OP 与游戏模式组合，以及空玩家/无服务器拒绝。
 * - 修改：使用真实统一门禁，仅替代玩家与服务器环境。
 * - 用途：单人创造授权不泄漏到访客，专服创造不能绕过 OP，生存 OP 正常使用。
 */
package com.primordial.machine;

import com.primordial.service.PriceEditorAccess;
import com.primordial.item.PriceEditorItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceEditorAccessTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void ordinaryPlayersCanUseEitherHandInSurvivalOrCreative(boolean creative, boolean offhand) {
        var player = player();
        var editor = tool();
        when(player.isCreative()).thenReturn(creative);
        when(player.hasPermissions(2)).thenReturn(false);
        when(player.getMainHandItem()).thenReturn(offhand ? ItemStack.EMPTY : editor);
        when(player.getOffhandItem()).thenReturn(offhand ? editor : ItemStack.EMPTY);
        assertTrue(PriceEditorAccess.canEdit(player));
    }

    @ParameterizedTest @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void permissionsDoNotReplaceTheHeldTool(boolean creative, boolean admin) {
        var player = player();
        when(player.isCreative()).thenReturn(creative);
        when(player.hasPermissions(2)).thenReturn(admin);
        assertFalse(PriceEditorAccess.canEdit(player));
    }

    @Test void missingServerAndWrongThreadAreDeniedEvenWithTheTool() {
        var player = player();
        var editor = tool();
        when(player.getMainHandItem()).thenReturn(editor);
        when(player.getServer().isSameThread()).thenReturn(false);
        assertFalse(PriceEditorAccess.canEdit(player));
        when(player.getServer()).thenReturn(null);
        assertFalse(PriceEditorAccess.canEdit(player));
    }

    @Test void clientAndMissingPlayersAreDenied() {
        assertFalse(PriceEditorAccess.canEdit(mock(Player.class)));
        assertFalse(PriceEditorAccess.canEdit(null));
    }

    private static ServerPlayer player() {
        var player = mock(ServerPlayer.class);
        var server = mock(MinecraftServer.class);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        return player;
    }

    static ItemStack tool() {
        var stack = mock(ItemStack.class);
        var editor = mock(PriceEditorItem.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(editor);
        return stack;
    }
}
