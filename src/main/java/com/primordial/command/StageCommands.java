/**
 * 【文件职责】为任务系统提供管理员阶段 grant/revoke/check/list 命令，不依赖任务模组 API。
 * 【变更记录 #1】2026-10-02 19:08:00（第 1 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：权限等级 2 的 /primordial stage 子命令，支持在线玩家选择器和多目标。
 * - 修改：阶段只操作 UUID 存档，不授予资源知识；重复增删返回实际变更人数。
 * - 用途：FTB Quests 等通过服务端命令奖励解锁个人阶段，check 返回持有阶段的人数。
 */
package com.primordial.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.primordial.data.PlayerStagesSavedData;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class StageCommands {
    private StageCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = Commands.literal("stage").requires(source -> source.hasPermission(2));
        for (String operation : new String[]{"grant", "revoke", "check"}) {
            root.then(Commands.literal(operation)
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("stage", ResourceLocationArgument.id())
                                    .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                            ResourceLocationArgument.getId(ctx, "stage"), operation)))));
        }
        root.then(Commands.literal("list")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets")))));
        return root;
    }

    private static int execute(CommandSourceStack source, Collection<ServerPlayer> targets,
            ResourceLocation stage, String operation) {
        if (!PlayerStagesSavedData.validStage(stage)) {
            source.sendFailure(Component.translatable("commands.primordial.stage.invalid"));
            return 0;
        }
        var data = PlayerStagesSavedData.get(source.getServer());
        int count = 0;
        for (var player : targets) {
            boolean result = switch (operation) {
                case "grant" -> data.grant(player.getUUID(), stage);
                case "revoke" -> data.revoke(player.getUUID(), stage);
                default -> data.has(player.getUUID(), stage);
            };
            if (result) count++;
        }
        final int result = count;
        source.sendSuccess(() -> Component.translatable("commands.primordial.stage." + operation,
                stage.toString(), result, targets.size()), !operation.equals("check"));
        return result;
    }

    private static int list(CommandSourceStack source, Collection<ServerPlayer> targets) {
        var data = PlayerStagesSavedData.get(source.getServer());
        for (var player : targets) {
            String stages = data.stages(player.getUUID()).stream().map(ResourceLocation::toString)
                    .sorted().collect(java.util.stream.Collectors.joining(", "));
            Component values = stages.isEmpty() ? Component.translatable("commands.primordial.stage.none")
                    : Component.literal(stages);
            source.sendSuccess(() -> Component.translatable("commands.primordial.stage.list", player.getName(), values), false);
        }
        return targets.size();
    }
}
