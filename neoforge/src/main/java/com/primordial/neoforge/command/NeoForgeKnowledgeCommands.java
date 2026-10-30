/**
 * 【文件职责】注册权限等级 2 的介质知识管理员命令，供任务奖励和服务端函数主动调用。
 * 【变更记录 #1】2026-10-23 13:34:30（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：/primordial knowledge learn|forget|check <targets> fluid|gas <id>。
 * - 修改：所有命令复用已校验的服务端 API，返回实际变更/逐种已学人数，普通玩家不能授权。
 * - 用途：保持 Forge 作者入口语义，不增加客户端授予知识的数据包或自动限制策略。
 * 【变更记录 #2】2026-10-23 13:41:53（第 2 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：直接将 ResourceLocation 作为原生翻译组件参数的路径。
 * - 新增：无。修改：非法资源与操作结果消息均先转注册 ID 字符串。
 * - 用途：满足目标版翻译组件参数类型约束，使实际管理员命令能够返回结果而不抛错。
 */
package com.primordial.neoforge.command;

import java.util.Collection;
import java.util.Locale;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.api.MediaKnowledgeApi;
import com.primordial.neoforge.api.MediaKnowledgeApi.Media;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeKnowledgeCommands {
    private NeoForgeKnowledgeCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("primordial").then(tree()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = Commands.literal("knowledge").requires(source -> source.hasPermission(2));
        for (String operation : new String[]{"learn", "forget", "check"}) {
            var targets = Commands.argument("targets", EntityArgument.players());
            for (Media media : Media.values()) {
                targets.then(Commands.literal(media.name().toLowerCase(Locale.ROOT))
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(context -> execute(context.getSource(), EntityArgument.getPlayers(context, "targets"),
                                        media, ResourceLocationArgument.getId(context, "id"), operation))));
            }
            root.then(Commands.literal(operation).then(targets));
        }
        return root;
    }

    private static int execute(CommandSourceStack source, Collection<ServerPlayer> players,
            Media media, ResourceLocation id, String operation) {
        if (!MediaKnowledgeApi.validResource(media, id)) {
            source.sendFailure(Component.translatable("commands.primordial.knowledge.invalid", id.toString()));
            return 0;
        }
        int count = 0;
        for (ServerPlayer player : players) {
            if (switch (operation) {
                case "learn" -> MediaKnowledgeApi.learn(source.getServer(), player.getUUID(), media, id);
                case "forget" -> MediaKnowledgeApi.forget(source.getServer(), player.getUUID(), media, id);
                default -> MediaKnowledgeApi.hasLearned(source.getServer(), player.getUUID(), media, id);
            }) count++;
        }
        int result = count;
        source.sendSuccess(() -> Component.translatable("commands.primordial.knowledge." + operation, id.toString(), result, players.size()),
                !operation.equals("check"));
        return result;
    }
}
