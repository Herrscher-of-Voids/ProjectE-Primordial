/**
 * 变更记录 #3（2026-09-13）：统一通过 EmcService 门面访问余额，并完成 ProjectE/Curios API 审计。
 * - 新增：能量换算配置键 energy_to_emc_rate 与 emc_to_energy_rate。
 * - 修改：业务层不再直接调用 ProjectECompat，保证后续适配器共享同一 EMC 结算规则。
 */
/**
 * 变更记录 #2（2026-09-13）：接入 ProjectE 1.20.1 BigInteger EMC。
 * - 删除：玩家 PM 余额读写和 microPM 结算路径。
 * - 新增：ProjectE capability、IEMCProxy、BigInteger 网络同步及离线增量保存。
 * - 修改：物品、流体、能量结算统一使用 EMC，避免 long 溢出。
 */
/**
 * 【变更记录 #4】2026-09-15 12:04:51（第 4 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：管理员命令 set_value gas <id> <emc_per_mb>，最多解析 4096 位非负整数。
 * - 修改：验证气体注册名后写 ValueManager 气体表，结果提示注明 EMC/mB 单位。
 * - 用途：给反物质等非物品资源配置价格；零表示禁用，普通玩家不能修改公共价格。
 */
/**
 * 【变更记录 #5】2026-09-15 15:11:36（第 5 次备注 · 1.0.0-emc-service）
 * - 删除：item/fluid 使用 DoubleArgumentType，以及保存失败仍提示成功的分支。
 * - 新增：整数/分数文本解析、流体 EMC/1000mB 提示和人工价格同步。
 * - 修改：气体/物品严格非负整数，流体支持精确分数；管理员重载沿用服务端价表。
 * - 用途：命令、GUI 和机器服务共用精确价格，非法/过长输入不写盘。
 */
/**
 * 【变更记录 #6】2026-09-16 01:52:17（第 6 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：命令修改独立物品覆盖和同步本模组客户端物品缓存的路径。
 * - 新增：物品价格原生上限错误提示；reload_values 同时重读扩展介质与 ProjectE 自定义物品价格并重算。
 * - 修改：物品命令与配置器共用 ProjectEItemPrices 写入/重算/同步，流体每桶和气体每 mB 单位不变。
 * - 用途：无论从命令还是界面改价，都改变同一份 ProjectE 物品价表。
 */
/**
 * 【变更记录 #7】2026-10-02 19:08:00（第 7 次备注 · 1.2.0-resource-stages）
 * - 删除：无。
 * - 新增：注册 StageCommands 提供的管理员阶段命令树。
 * - 修改：沿用 /primordial 根节点；余额和价格命令行为不变。
 * - 用途：为任务完成奖励提供可直接执行的服务器阶段命令。
 */
/**
 * 【变更记录 #8】2026-10-10 03:04:14（第 8 次备注 · 1.2.0-price-change-audit）
 * - 删除：无；命令树与价格语法保持。
 * - 新增：物品、流体、气体 set_value 命令接入 PriceChangeAudit，日志同时记录控制台/命令源或玩家名称与 UUID。
 * - 修改：权限、注册名与数值解析后调用原有保存事务；只在保存成功后记录真实前后状态。
 * - 用途：确保通过命令绕过定价器 GUI 的价格管理也能追溯；不接管其它模组命令或管理员直接编辑文件。
 */
/**
 * 【变更记录 #9】2026-10-10 03:07:44（第 9 次备注 · 1.2.0-price-change-audit）
 * - 删除：流体价格参数使用 word() 导致斜杠分数在命令解析阶段被截断的限制。
 * - 新增：无；继续由 EmcAmount 对非负、长度和分母进行严格校验。
 * - 修改：流体末尾价格参数改用 greedyString()，原有整数/小数和未加引号的 1/3 均交给同一精确价格解析器。
 * - 用途：修复命令宣称支持分数但无法到达实际保存事务的缺口，成功保存同步产生 PriceAudit 记录。
 */
/**
 * 【变更记录 #10】2026-10-13 03:04:47（第 10 次备注 · 1.2.0-quest-system）
 * - 删除：无。新增：管理员 quest check 命令验证十六进制任务 ID 并返回 FTB 进度状态。
 * - 修改：查询调用隔离的 QuestProgress，命令不能授予阶段、任务或知识。
 * - 用途：服务器管理员可检查目标玩家任务条件；普通玩家不可查看他人进度。
 * 【变更记录 #11】2026-10-13（第 11 次备注 · FTB 阶段奖励）
 * - 删除：直接查询 FTB 任务 ID 的临时诊断命令。
 * - 用途：任务奖励现在向领取者个人阶段存档发放门禁阶段；保留管理员 grant/revoke/check/list。
 */
/**
 * 【变更记录 #12】2026-10-13 20:14:16（第 12 次备注 · 1.2.0-pack-knowledge-api）
 * - 删除：撤回上一轮新增的 media_access 多组学习/购买/卖出/补充规则，历史备注保留。
 * - 新增：注册 knowledge 管理员子命令，调用 MediaKnowledgeApi 查询、授予、撤销单种知识；权限等级为 2。
 * - 修改：按用户修正保留卖入学习、已学购买及已有可选 FTB 接口，不再扩展默认门禁。
 * - 用途：包作者主动通过知识 API 或管理员命令联动，普通玩法不新增配置负担。
 */
/**
 * 【变更记录 #13】2026-10-13 23:31:57（第 13 次备注 · 1.2.0-blacklist-system）
 * - 删除/新增/修改：新增 blacklist check 管理员只读诊断命令注册；删除无；命中规则说明与真实黑名单使用同一服务，不提供命令修改价格、知识或配置。
 * - 用途：第 10 系统保持作者主动配置才生效；缓存和诊断共用真实规则，不删除已有价格与知识，不新增默认交易门槛。
 */
package com.primordial.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.primordial.service.EmcAmount;
import com.primordial.service.PriceChangeAudit;
import com.primordial.value.ValueTable;
import com.primordial.compat.ProjectEItemPrices;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.primordial.service.EmcService;
import com.primordial.util.EmcFormatter;
import com.primordial.value.ValueManager;

/** /primordial balance [player] / set_value / reload_values */
public final class PrimordialCommands {
    private PrimordialCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("primordial");
        root.then(StageCommands.tree());
        root.then(MediaKnowledgeCommands.tree());
        root.then(BlacklistCommands.tree());

        root.then(Commands.literal("balance")
                .executes(ctx -> balance(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(s -> s.hasPermission(2))
                        .executes(ctx -> balance(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "target")))));

        root.then(Commands.literal("set_value").requires(s -> s.hasPermission(2))
                .then(Commands.literal("item")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.ITEM.keySet(), builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(ctx -> setValue(ctx.getSource(), "item",
                                                ResourceLocationArgument.getId(ctx, "id"),
                                                StringArgumentType.getString(ctx, "value"))))))
                .then(Commands.literal("fluid")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(ctx -> setValue(ctx.getSource(), "fluid",
                                                ResourceLocationArgument.getId(ctx, "id"),
                                                StringArgumentType.getString(ctx, "value")))))));

        root.then(Commands.literal("set_value").requires(s -> s.hasPermission(2))
                .then(Commands.literal("gas")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .then(Commands.argument("emc_per_mb", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> setGasValue(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id"),
                                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "emc_per_mb")))))));

        root.then(Commands.literal("reload_values").requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    if (!ValueManager.reload() || !ProjectEItemPrices.reload(ctx.getSource().getServer())) {
                        ctx.getSource().sendFailure(Component.translatable("message.primordial.price_save_failed"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(
                            () -> Component.translatable("commands.primordial.reload"), true);
                    return 1;
                }));

        dispatcher.register(root);
    }

    private static int balance(CommandSourceStack source, ServerPlayer target) {
        java.math.BigInteger emc = EmcService.balance(target.server, target.getUUID());
        String text = EmcFormatter.format(emc);
        if (source.getEntity() == target) {
            source.sendSuccess(() -> Component.translatable("commands.primordial.balance.self", text), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "commands.primordial.balance.other", target.getName(), text), false);
        }
        return 1;
    }

    /** 气体按每 mB EMC 定价；注册名必须存在，解析有长度上限，避免畸形超长整数占用服务器线程。 */
    private static int setGasValue(CommandSourceStack source, ResourceLocation id, String text) {
        if (!com.primordial.compat.MekanismGasCompat.validGas(id)) {
            source.sendFailure(Component.translatable("commands.primordial.unknown_id", id));
            return 0;
        }
        if (text.length() > 4096 || !text.matches("[0-9]+")) {
            source.sendFailure(Component.translatable("commands.primordial.invalid_gas_value"));
            return 0;
        }
        if (!PriceChangeAudit.change(source, PriceChangeAudit.Kind.GAS, id,
                () -> ValueManager.setGasValue(id, new java.math.BigInteger(text)))) {
            source.sendFailure(Component.translatable("message.primordial.price_save_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.primordial.set_gas_value", id, text), true);
        return 1;
    }

    private static int setValue(CommandSourceStack source, String type, ResourceLocation id, String text) {
        if ("item".equals(type) && !BuiltInRegistries.ITEM.containsKey(id)) {
            source.sendFailure(Component.translatable("commands.primordial.unknown_id", id));
            return 0;
        }
        if ("fluid".equals(type) && !BuiltInRegistries.FLUID.containsKey(id)) {
            source.sendFailure(Component.translatable("commands.primordial.unknown_id", id));
            return 0;
        }
        boolean saved;
        String value;
        try {
            if ("fluid".equals(type)) {
                EmcAmount price = EmcAmount.parse(text);
                saved = PriceChangeAudit.change(source, PriceChangeAudit.Kind.FLUID, id,
                        () -> ValueManager.setFluidValue(id, price));
                value = price.toString();
            } else {
                var price = ValueTable.parseInteger(text);
                if (!ProjectEItemPrices.validItemPrice(price)) {
                    source.sendFailure(Component.translatable("screen.primordial.price_invalid_item"));
                    return 0;
                }
                saved = PriceChangeAudit.change(source, PriceChangeAudit.Kind.ITEM, id,
                        () -> ValueManager.setItemValue(source.getServer(), id, price));
                value = price.toString();
            }
        } catch (RuntimeException invalid) {
            source.sendFailure(Component.translatable("commands.primordial.invalid_value"));
            return 0;
        }
        if (!saved) {
            source.sendFailure(Component.translatable("message.primordial.price_save_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "fluid".equals(type) ? "commands.primordial.set_fluid_value" : "commands.primordial.set_value", type, id, value), true);
        return 1;
    }
}
