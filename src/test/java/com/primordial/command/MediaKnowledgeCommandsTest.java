/**
 * 【文件职责】验证知识命令的管理员权限、选择器、多玩家结果和无效资源处理。
 * 【变更记录 #1】2026-10-13 20:09:00（第 1 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：无。新增：真实命令解析与知识 API 回归；仅替代玩家选择和世界存储。
 * - 修改：无。用途：普通玩家不能自行授予知识，任务和脚本重复调用不重复计数。
 */
/**
 * 【变更记录 #2】2026-10-13 20:18:14（第 2 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：静态替身误拦截真实私有 remove 帮助函数的行为。新增：无。
 * - 修改：知识存档仅替换 get 入口，其余静态方法调用真实实现。
 * - 用途：实际验证遗忘和重新学习，不让测试替身改变生产存档行为。
 */
package com.primordial.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.primordial.data.MediaKnowledgeSavedData;
import com.primordial.machine.AdapterTestBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaKnowledgeCommandsTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void ordinaryPlayerCannotUseKnowledgeCommands() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        PrimordialCommands.register(dispatcher);
        var source = mock(CommandSourceStack.class);
        for (var operation : List.of("learn", "forget", "check"))
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(
                    "primordial knowledge " + operation + " @a fluid minecraft:water", source));
        verify(source, never()).getServer();
    }

    @Test void administratorCanGrantCheckAndRevokeForSelectedPlayers() throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        PrimordialCommands.register(dispatcher);
        var source = mock(CommandSourceStack.class);
        var server = mock(MinecraftServer.class);
        when(source.hasPermission(2)).thenReturn(true);
        when(source.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(mock(ServerLevel.class));
        var first = mock(ServerPlayer.class);
        var second = mock(ServerPlayer.class);
        when(first.getUUID()).thenReturn(UUID.randomUUID());
        when(second.getUUID()).thenReturn(UUID.randomUUID());
        var data = new MediaKnowledgeSavedData();
        try (var entities = mockStatic(EntityArgument.class, CALLS_REAL_METHODS);
             var saved = mockStatic(MediaKnowledgeSavedData.class, CALLS_REAL_METHODS)) {
            entities.when(() -> EntityArgument.getPlayers(any(), eq("targets"))).thenReturn(List.of(first, second));
            saved.when(() -> MediaKnowledgeSavedData.get(server)).thenReturn(data);
            assertEquals(0, dispatcher.execute("primordial knowledge check @a fluid minecraft:water", source));
            assertEquals(2, dispatcher.execute("primordial knowledge learn @a fluid minecraft:water", source));
            assertEquals(0, dispatcher.execute("primordial knowledge learn @a fluid minecraft:water", source));
            assertEquals(2, dispatcher.execute("primordial knowledge check @a fluid minecraft:water", source));
            assertEquals(2, dispatcher.execute("primordial knowledge forget @a fluid minecraft:water", source));
            assertEquals(0, dispatcher.execute("primordial knowledge forget @a fluid minecraft:water", source));
            assertEquals(0, dispatcher.execute("primordial knowledge check @a fluid minecraft:water", source));
            assertEquals(0, dispatcher.execute("primordial knowledge learn @a fluid minecraft:missing", source));
            assertTrue(data.knownIds(first.getUUID(), false).isEmpty());
            assertTrue(data.knownIds(second.getUUID(), false).isEmpty());
            verify(source).sendFailure(any());
        }
    }
}
