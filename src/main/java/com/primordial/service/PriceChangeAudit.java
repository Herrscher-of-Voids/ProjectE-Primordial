/**
 * 【文件职责】为管理员价格编辑记录服务端实际保存前后的价格状态。
 * - 调用链：定价器价格提交包或 Primordial 改价命令 -> 权限复核与状态快照 -> 原有保存事务 -> 服务器 PriceAudit 日志。
 * 【变更记录 #1】2026-10-10 03:04:14（第 1 次备注 · 1.2.0-price-change-audit）
 * - 删除：无；不替换 ProjectE 重算、ValueManager 原子保存或失败回滚。
 * - 新增：物品、流体、气体保存成功后的结构化日志，包含资源键、计价单位、前后状态与价格、操作者名称/UUID、入口和 UTC 时间。
 * - 修改：所有受支持入口在服务端线程调用本类，权限不足或保存返回失败时不输出成功记录；价格文本按 JSON 编码避免名称中的换行污染日志。
 * - 用途：把设计讨论的管理员改价追踪落实到现有服务器日志，不缓存历史或逐 tick 写盘；介质记录保留的基础价格，禁用/继承模式另列。
 */
/**
 * 【变更记录 #2】2026-10-11 04:26:53（第 2 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：GUI 审计入口只按 permission level 2 放行的条件。
 * - 新增：GUI 审计保存复核创造模式管理员门禁；命令源审计规则保持独立。
 * - 修改：成功保存后才生成前后状态日志的事务顺序不变。
 * - 用途：审计记录只代表实际允许的定价器修改。
 */
package com.primordial.service;

import com.google.gson.JsonObject;
import com.primordial.PrimordialMod;
import com.primordial.compat.ProjectEItemPrices;
import com.primordial.value.PriceMode;
import com.primordial.value.ValueManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BooleanSupplier;

public final class PriceChangeAudit {
    public enum Kind {
        ITEM("EMC/item"), FLUID("EMC/1000mB"), GAS("EMC/mB");
        private final String unit;
        Kind(String unit) { this.unit = unit; }
    }

    private record State(PriceMode mode, String price) {
        JsonObject json() {
            var result = new JsonObject();
            result.addProperty("mode", mode.name());
            result.addProperty("price", price);
            return result;
        }
    }

    private PriceChangeAudit() {}

    /** GUI 入口复核管理员权限；实际保存沿用调用方已经验证的样本身份和原有事务。 */
    public static boolean change(ServerPlayer player, Kind kind, ResourceLocation id, BooleanSupplier save) {
        if (!PriceEditorAccess.canEdit(player)) return false;
        return change(player.getName().getString(), player.getUUID().toString(), "price_editor", kind, id, save);
    }

    /** 命令源可以是管理员玩家、控制台或命令方块，非玩家操作者用空 UUID 并保留命令源名称。 */
    public static boolean change(CommandSourceStack source, Kind kind, ResourceLocation id, BooleanSupplier save) {
        if (source == null || !source.hasPermission(2)) return false;
        String uuid = source.getEntity() == null ? "" : source.getEntity().getUUID().toString();
        return change(source.getTextName(), uuid, "command", kind, id, save);
    }

    private static boolean change(String operator, String uuid, String origin, Kind kind,
                                  ResourceLocation id, BooleanSupplier save) {
        State before = snapshot(kind, id);
        if (!save.getAsBoolean()) return false;
        State after = snapshot(kind, id);
        var entry = new JsonObject();
        entry.addProperty("time", Instant.now().toString());
        entry.addProperty("operator", operator);
        entry.addProperty("operator_uuid", uuid);
        entry.addProperty("origin", origin);
        entry.addProperty("kind", kind.name());
        entry.addProperty("resource", id.toString());
        entry.addProperty("unit", kind.unit);
        entry.add("before", before.json());
        entry.add("after", after.json());
        PrimordialMod.LOGGER.info("[Primordial][PriceAudit] {}", entry);
        return true;
    }

    /** 记录未受交易黑名单折算的基础配置值；模式决定该值是否参与结算，避免把禁用误记成免费。 */
    private static State snapshot(Kind kind, ResourceLocation id) {
        if (kind == Kind.ITEM) {
            BigInteger custom = ProjectEItemPrices.customPrices().get(id.toString());
            return new State(custom == null ? PriceMode.INHERIT_NATIVE
                    : custom.signum() == 0 ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE,
                    (custom == null ? ProjectEItemPrices.basePrice(id) : custom).toString());
        }
        ValueManager.loadIfNeeded();
        if (kind == Kind.FLUID) return new State(ValueManager.fluidMode(id),
                ValueManager.fluidSnapshot().getOrDefault(id.toString(), EmcAmount.parse("1000")).toString());
        return new State(ValueManager.gasMode(id),
                ValueManager.gasSnapshot().getOrDefault(id.toString(), BigInteger.ONE).toString());
    }
}
