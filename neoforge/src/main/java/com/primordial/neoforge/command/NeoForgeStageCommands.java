/**
 * 【文件职责】注册个人阶段管理员命令，供服务端函数和任务系统授予、撤销及查询阶段。
 * 【变更记录 #1】2026-10-23 16:19:58（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无。
 * - 新增：权限等级 2 的 /primordial stage grant/revoke/check/list，支持在线玩家选择器和多目标。
 * - 修改：与 knowledge 子树合并注册，只操作个人 UUID 存档；幂等操作返回实际变化人数，损坏目标单独报告。
 * - 用途：阶段不赠送知识、物品或 EMC；消息使用目标版支持的字符串/组件参数，可信离线集成可直接调用阶段账本。
 */
package com.primordial.neoforge.command;

import java.util.Collection;
import java.util.stream.Collectors;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;
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
public final class NeoForgeStageCommands {
    private NeoForgeStageCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("primordial").then(tree()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var root = Commands.literal("stage").requires(source -> source.hasPermission(2));
        for (String operation : new String[]{"grant", "revoke", "check"}) {
            root.then(Commands.literal(operation)
                    .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("stage", ResourceLocationArgument.id())
                                    .executes(context -> execute(context.getSource(), EntityArgument.getPlayers(context, "targets"),
                                            ResourceLocationArgument.getId(context, "stage"), operation)))));
        }
        root.then(Commands.literal("list")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> list(context.getSource(), EntityArgument.getPlayers(context, "targets")))));
        return root;
    }

    private static int execute(CommandSourceStack source, Collection<ServerPlayer> targets,
                               ResourceLocation stage, String operation) {
        if (!NeoForgePlayerStagesSavedData.validStage(stage)) {
            source.sendFailure(Component.translatable("commands.primordial.stage.invalid"));
            return 0;
        }
        NeoForgePlayerStagesSavedData data = NeoForgePlayerStagesSavedData.get(source.getServer());
        int count = 0;
        for (ServerPlayer player : targets) {
            if (!data.available(player.getUUID())) {
                source.sendFailure(Component.translatable("commands.primordial.stage.unavailable", player.getName()));
                continue;
            }
            boolean result = switch (operation) {
                case "grant" -> data.grant(player.getUUID(), stage);
                case "revoke" -> data.revoke(player.getUUID(), stage);
                default -> data.has(player.getUUID(), stage);
            };
            if (result) count++;
        }
        int result = count;
        source.sendSuccess(() -> Component.translatable("commands.primordial.stage." + operation,
                stage.toString(), result, targets.size()), !operation.equals("check"));
        return result;
    }

    private static int list(CommandSourceStack source, Collection<ServerPlayer> targets) {
        NeoForgePlayerStagesSavedData data = NeoForgePlayerStagesSavedData.get(source.getServer());
        int count = 0;
        for (ServerPlayer player : targets) {
            if (!data.available(player.getUUID())) {
                source.sendFailure(Component.translatable("commands.primordial.stage.unavailable", player.getName()));
                continue;
            }
            String text = data.stages(player.getUUID()).stream().map(ResourceLocation::toString).sorted().collect(Collectors.joining(", "));
            Component stages = text.isEmpty() ? Component.translatable("commands.primordial.stage.none") : Component.literal(text);
            source.sendSuccess(() -> Component.translatable("commands.primordial.stage.list", player.getName(), stages), false);
            count++;
        }
        return count;
    }
}
