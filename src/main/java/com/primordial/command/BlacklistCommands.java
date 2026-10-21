/**
 * 【文件职责】给整合包作者只读查看资源黑名单的实际命中规则。
 * 【变更记录 #1】2026-10-13 23:25:00（第 1 次备注 · 1.2.0-blacklist-system）
 * - 删除：无。新增：权限等级 2 的 blacklist check fluid|gas|resource <id>。
 * - 修改：命中时返回 1，未命中或未知资源返回 0；不改配置、价格、知识和 EMC。
 * - 用途：管理员可定位精确 ID、命名空间或标签规则，普通玩家不能借命令改变玩法。
 */
package com.primordial.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.primordial.api.MediaKnowledgeApi;
import com.primordial.config.ResourceBlacklistRules;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class BlacklistCommands {
    private BlacklistCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        var check = Commands.literal("check");
        for (var kind : ResourceBlacklistRules.Kind.values()) {
            check.then(Commands.literal(kind.name().toLowerCase(java.util.Locale.ROOT))
                    .then(Commands.argument("id", ResourceLocationArgument.id())
                            .executes(ctx -> check(ctx.getSource(), kind, ResourceLocationArgument.getId(ctx, "id")))));
        }
        return Commands.literal("blacklist").requires(source -> source.hasPermission(2)).then(check);
    }

    private static int check(CommandSourceStack source, ResourceBlacklistRules.Kind kind, ResourceLocation id) {
        boolean valid = switch (kind) {
            case FLUID -> net.minecraft.core.registries.BuiltInRegistries.FLUID.containsKey(id)
                    && !id.equals(new ResourceLocation("minecraft:empty"));
            case GAS -> MediaKnowledgeApi.validResource(MediaKnowledgeApi.Media.GAS, id);
            // 独立资源单位是开放 ID，由原生适配器/作者定义，不属于 Minecraft 注册表。
            case RESOURCE -> true;
        };
        if (!valid) {
            source.sendFailure(Component.translatable("commands.primordial.knowledge.invalid", id));
            return 0;
        }
        var match = ResourceBlacklistRules.matchingRule(kind, id);
        source.sendSuccess(() -> match.map(rule -> Component.translatable("commands.primordial.blacklist.blocked", id, rule))
                .orElseGet(() -> Component.translatable("commands.primordial.blacklist.allowed", id)), false);
        return match.isPresent() ? 1 : 0;
    }
}
