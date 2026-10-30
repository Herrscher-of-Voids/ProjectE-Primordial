/**
 * 【文件职责】为控制台、服务端函数和二级管理员提供与 Forge 一致的公共定价及重载命令。
 * 【变更记录 #1】2026-10-23 17:31:59（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。
 * - 新增：/primordial set_value item|fluid|gas <id> <value> 与 reload_values，返回真实保存结果。
 * - 修改：注册名和有界数值先校验，随后统一进入定价事务；gas 保留旧命令名并指向目标版统一化学品。
 * - 用途：物品遵守原生 long 上限，流体保留精确桶价，化学品只接受非负整数 EMC/mB；不新增 Forge 没有的模式命令。
 */
package com.primordial.neoforge.command;

import java.math.BigInteger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.value.NeoForgePriceEditing;
import com.primordial.service.EmcAmount;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgePriceCommands {
    private NeoForgePriceCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var values = Commands.literal("set_value").requires(source -> source.hasPermission(2));
        for (byte kind = 0; kind < 3; kind++) {
            byte selected = kind;
            var id = Commands.argument("id", ResourceLocationArgument.id());
            if (kind == 0) id.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder));
            if (kind == 1) id.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.FLUID.keySet(), builder));
            id.then(Commands.argument("value", kind == 1 ? StringArgumentType.greedyString() : StringArgumentType.word())
                    .executes(context -> set(context.getSource(), selected, ResourceLocationArgument.getId(context, "id"),
                            StringArgumentType.getString(context, "value"))));
            values.then(Commands.literal(type(kind)).then(id));
        }
        event.getDispatcher().register(Commands.literal("primordial").then(values)
                .then(Commands.literal("reload_values").requires(source -> source.hasPermission(2)).executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (!source.hasPermission(2) || !NeoForgePriceEditing.reload(source.getServer())) {
                        source.sendFailure(Component.translatable("primordial.price_editor.result.failed"));
                        return 0;
                    }
                    source.sendSuccess(() -> Component.translatable("commands.primordial.reload"), true);
                    return 1;
                })));
    }

    private static int set(CommandSourceStack source, byte kind, ResourceLocation id, String text) {
        if (!valid(kind, id)) {
            source.sendFailure(Component.translatable("commands.primordial.unknown_id", id.toString()));
            return 0;
        }
        String value;
        try {
            if (kind == 2 && (text.length() > 4096 || !text.matches("[0-9]+")))
                throw new IllegalArgumentException("Expected a bounded nonnegative chemical integer");
            EmcAmount amount = EmcAmount.parse(text);
            if (kind != 1 && (!amount.denominator().equals(BigInteger.ONE)
                    || kind == 0 && amount.numerator().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0))
                throw new IllegalArgumentException("Expected a supported nonnegative integer");
            value = amount.toString();
        } catch (RuntimeException invalid) {
            source.sendFailure(Component.translatable(kind == 2 ? "commands.primordial.invalid_gas_value" : "commands.primordial.invalid_value"));
            return 0;
        }
        var result = NeoForgePriceEditing.mutate(source, kind, id, value);
        if (!result.success()) {
            source.sendFailure(Component.translatable(result.messageKey()));
            return 0;
        }
        source.sendSuccess(() -> kind == 2 ? Component.translatable("commands.primordial.set_gas_value", id.toString(), value)
                : Component.translatable(kind == 1 ? "commands.primordial.set_fluid_value" : "commands.primordial.set_value",
                        type(kind), id.toString(), value), true);
        return 1;
    }

    private static boolean valid(byte kind, ResourceLocation id) {
        return switch (kind) {
            case 0 -> BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR;
            case 1 -> BuiltInRegistries.FLUID.containsKey(id) && BuiltInRegistries.FLUID.get(id) != Fluids.EMPTY;
            case 2 -> NeoForgeMediaRegistry.validChemical(id);
            default -> false;
        };
    }

    private static String type(byte kind) { return kind == 0 ? "item" : kind == 1 ? "fluid" : "gas"; }
}
