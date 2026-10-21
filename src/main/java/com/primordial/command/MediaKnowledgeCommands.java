/**
 * 【文件职责】将介质知识 API 暴露为管理员命令，供任务奖励、数据包函数和服务器脚本调用。
 * 【变更记录 #1】2026-10-13 20:00:00（第 1 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：无。新增：/primordial knowledge learn|forget|check <players> fluid|gas <id>。
 * - 修改：权限等级 2；注册名由服务端校验，非法资源不写存档，命令结果返回实际变更/已学人数。
 * - 用途：包作者无需链接 Java 即可主动授予/撤销知识；普通玩家不能调用，不新增自动限制。
 */
package com.primordial.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.primordial.api.MediaKnowledgeApi;
import com.primordial.api.MediaKnowledgeApi.Media;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class MediaKnowledgeCommands {
    private MediaKnowledgeCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = Commands.literal("knowledge").requires(source -> source.hasPermission(2));
        for (String operation : new String[]{"learn", "forget", "check"}) {
            var targets = Commands.argument("targets", EntityArgument.players());
            for (Media media : Media.values()) {
                targets.then(Commands.literal(media.name().toLowerCase(java.util.Locale.ROOT))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(ctx -> execute(ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                        media, ResourceLocationArgument.getId(ctx, "id"), operation))));
            }
            root.then(Commands.literal(operation).then(targets));
        }
        return root;
    }

    private static int execute(CommandSourceStack source, Collection<ServerPlayer> players,
            Media media, ResourceLocation id, String operation) {
        if (!MediaKnowledgeApi.validResource(media, id)) {
            source.sendFailure(Component.translatable("commands.primordial.knowledge.invalid", id));
            return 0;
        }
        int count = 0;
        for (var player : players) {
            boolean changed = switch (operation) {
                case "learn" -> MediaKnowledgeApi.learn(source.getServer(), player.getUUID(), media, id);
                case "forget" -> MediaKnowledgeApi.forget(source.getServer(), player.getUUID(), media, id);
                default -> MediaKnowledgeApi.hasLearned(source.getServer(), player.getUUID(), media, id);
            };
            if (changed) count++;
        }
        final int result = count;
        source.sendSuccess(() -> Component.translatable("commands.primordial.knowledge." + operation, id, result, players.size()),
                !operation.equals("check"));
        return result;
    }
}
