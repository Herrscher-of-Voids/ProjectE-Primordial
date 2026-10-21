/**
 * 【文件职责】验证管理员阶段命令的真实 Brigadier 路由、权限及多目标效果。
 * 【变更记录 #1】2026-10-02 19:07:21（第 1 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：普通玩家拒绝、管理员增删查和重复执行幂等性测试。
 * - 修改：仅在线玩家选择结果和世界存储使用替身，命令树及阶段变更真实执行。
 * - 用途：确保任务命令奖励不能由普通玩家自行调用来解锁资源。
 */
package com.primordial.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.primordial.data.PlayerStagesSavedData;
import com.primordial.machine.AdapterTestBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StageCommandsTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void ordinaryPlayersCannotRunStageCommands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        PrimordialCommands.register(dispatcher);
        var source = mock(CommandSourceStack.class);
        for (String command : List.of("grant @a pack:test", "revoke @a pack:test", "check @a pack:test", "list @a"))
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("primordial stage " + command, source));
        verify(source, never()).getServer();
    }

    @Test void adminCommandsGrantRevokeCheckAndListForAllSelectedPlayers() throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        PrimordialCommands.register(dispatcher);
        var source = mock(CommandSourceStack.class);
        var server = mock(MinecraftServer.class);
        when(source.hasPermission(2)).thenReturn(true);
        when(source.getServer()).thenReturn(server);
        var first = mock(ServerPlayer.class);
        var second = mock(ServerPlayer.class);
        when(first.getUUID()).thenReturn(UUID.randomUUID());
        when(second.getUUID()).thenReturn(UUID.randomUUID());
        when(first.getName()).thenReturn(net.minecraft.network.chat.Component.literal("First"));
        when(second.getName()).thenReturn(net.minecraft.network.chat.Component.literal("Second"));
        var data = new PlayerStagesSavedData();
        try (var entities = mockStatic(EntityArgument.class, CALLS_REAL_METHODS);
             var saved = mockStatic(PlayerStagesSavedData.class, CALLS_REAL_METHODS)) {
            entities.when(() -> EntityArgument.getPlayers(any(), eq("targets"))).thenReturn(List.of(first, second));
            saved.when(() -> PlayerStagesSavedData.get(server)).thenReturn(data);
            assertEquals(0, dispatcher.execute("primordial stage check @a pack:test", source));
            assertEquals(2, dispatcher.execute("primordial stage grant @a pack:test", source));
            assertEquals(0, dispatcher.execute("primordial stage grant @a pack:test", source));
            assertEquals(2, dispatcher.execute("primordial stage check @a pack:test", source));
            assertEquals(2, dispatcher.execute("primordial stage list @a", source));
            assertTrue(data.has(first.getUUID(), new ResourceLocation("pack:test")));
            assertTrue(data.has(second.getUUID(), new ResourceLocation("pack:test")));
            assertEquals(2, dispatcher.execute("primordial stage revoke @a pack:test", source));
            assertEquals(0, dispatcher.execute("primordial stage revoke @a pack:test", source));
            assertEquals(0, dispatcher.execute("primordial stage check @a pack:test", source));
            assertEquals(0, dispatcher.execute("primordial stage grant @a pack:" + "a".repeat(129), source));
            assertTrue(data.stages(first.getUUID()).isEmpty());
        }
    }
}
