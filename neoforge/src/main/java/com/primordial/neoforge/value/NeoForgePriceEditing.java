/**
 * 【文件职责】定价器的服务端价格快照、权限复核、保存反馈和成功审计入口。
 * 【变更记录 #1】2026-10-23 17:00:16（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：物品/流体/统一化学品的无可选 API 合同及有界输入校验。
 * - 修改：价格或模式仅在服务器主线程由实际获权玩家保存，成功后记录真实前后状态。
 * - 用途：权限依据单人主人创造或二级管理员；注册 ID 决定基础价，样本组件不会污染全局价表。
 * 【变更记录 #2】2026-10-23 17:09:56（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：编辑器把继承或禁用前的人工原价作为当前价回显。
 * - 新增：审计保留价快照与界面有效价快照的区分。
 * - 修改：模式切换不清人工原价，界面按实际模式显示默认值或零。
 * - 用途：恢复定制仍采用原金额，管理员可清楚确认当前生效价格。
 * 【变更记录 #3】2026-10-23 17:31:58（第 3 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：公共定价服务仅接收玩家 GUI 操作者的限制。
 * - 新增：二级命令源入口及介质/原生价表联合预检重载。
 * - 修改：控制台、函数与玩家共用同一保存事务；命令审计 origin=command，无玩家则 UUID 为空。
 * - 用途：管理员 set_value/reload_values 完整复用持久化、回滚与原生同步，不伪造玩家授权。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.OptionalLong;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.knowledge.NeoForgeMediaRegistry;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorAccess;
import com.primordial.service.EmcAmount;
import com.primordial.value.PriceMode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluids;

public final class NeoForgePriceEditing {
    public record State(boolean available, PriceMode mode, String price, long revision) {}
    public record Result(boolean success, String messageKey, State state) {}
    private record Operator(String name, String uuid, String origin) {}

    private NeoForgePriceEditing() {}

    public static long revision() { return NeoForgeRecoveryPrices.revision(); }

    public static OptionalLong customItemValue(ResourceLocation id) { return NeoForgeProjectEItemPrices.customItemValue(id); }

    public static State snapshot(byte kind, ResourceLocation id) {
        if (!valid(kind, id)) return new State(false, PriceMode.DISABLED, "0", revision());
        if (kind == 0) {
            OptionalLong custom = customItemValue(id);
            return new State(NeoForgeProjectEItemPrices.available(), custom.isEmpty() ? PriceMode.INHERIT_NATIVE
                    : custom.getAsLong() == 0 ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE,
                    custom.isPresent() ? Long.toString(custom.getAsLong()) : NeoForgeProjectEItemPrices.basePrice(id).toString(), revision());
        }
        var state = NeoForgeRecoveryPrices.mediaState(kind == 1, id);
        return new State(state.readable(), state.mode(), state.price().toString(), revision());
    }

    public static State snapshot(MinecraftServer server, byte kind, ResourceLocation id) { return snapshot(kind, id); }
    public static Result apply(MinecraftServer server, ServerPlayer player, byte kind, ResourceLocation id, String text) {
        return mutate(server, player, kind, id, text);
    }

    /** 菜单仍须校验会话、样本和代号；本入口再次复核线程、真实服务器及最终编辑权限。 */
    public static synchronized Result mutate(MinecraftServer server, ServerPlayer player, byte kind, ResourceLocation id, String text) {
        if (server == null || !server.isSameThread() || player == null || player.server != server
                || !NeoForgePriceEditorAccess.canEdit(player)) return denied();
        return mutateAuthorized(server, new Operator(player.getName().getString(), player.getUUID().toString(), "price_editor"), kind, id, text);
    }

    /** 命令门禁不含单人创造例外；服务器控制台和函数保留真实命令源身份。 */
    public static synchronized Result mutate(CommandSourceStack source, byte kind, ResourceLocation id, String text) {
        if (source == null || !source.hasPermission(2) || !source.getServer().isSameThread()) return denied();
        String uuid = source.getEntity() instanceof ServerPlayer player ? player.getUUID().toString() : "";
        return mutateAuthorized(source.getServer(), new Operator(source.getTextName(), uuid, "command"), kind, id, text);
    }

    /** 全部文件先只读验证；原生重映射成功之后才发布新的介质快照。 */
    public static synchronized boolean reload(MinecraftServer server) {
        if (server == null || !server.isSameThread()) return false;
        try {
            var media = NeoForgeRecoveryPrices.prepareReload();
            if (!NeoForgeProjectEItemPrices.reload(server)) return false;
            NeoForgeRecoveryPrices.publishEdited(media);
            return true;
        } catch (IOException | RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("[Primordial] Cannot reload administrator prices; original files preserved", failure);
            return false;
        }
    }

    private static Result denied() {
        return new Result(false, "primordial.price_editor.result.denied", new State(false, PriceMode.DISABLED, "0", revision()));
    }

    private static Result mutateAuthorized(MinecraftServer server, Operator operator, byte kind, ResourceLocation id, String text) {
        State before = snapshot(kind, id);
        if (!before.available()) return result(false, "unavailable", kind, id);
        if (text == null || text.length() > 4354 || text.isBlank()) return result(false, "invalid", kind, id);
        PriceMode mode;
        EmcAmount amount = null;
        String input = text.trim();
        try { mode = PriceMode.parse(input); }
        catch (IllegalArgumentException notMode) {
            try {
                amount = EmcAmount.parse(input);
                if (kind != 1 && !amount.denominator().equals(BigInteger.ONE)
                        || kind == 0 && amount.numerator().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
                    return result(false, "invalid", kind, id);
                mode = amount.isZero() ? PriceMode.DISABLED : PriceMode.CUSTOM_VALUE;
            } catch (RuntimeException invalid) { return result(false, "invalid", kind, id); }
        }
        if (kind == 0 && (mode == PriceMode.SELL_ONLY || mode == PriceMode.BUY_ONLY)) return result(false, "invalid", kind, id);
        try {
            State auditBefore = auditSnapshot(kind, id, before);
            boolean saved;
            if (kind == 0) {
                BigInteger value = mode == PriceMode.INHERIT_NATIVE ? null : mode == PriceMode.DISABLED
                        ? BigInteger.ZERO : amount == null ? new BigInteger(before.price()) : amount.numerator();
                saved = NeoForgeProjectEItemPrices.update(server, id, value);
            } else {
                NeoForgeMediaPriceEditing.edit(NeoForgeMediaPriceEditing.file(), kind == 1, id, mode, amount);
                saved = true;
            }
            if (!saved) return result(false, "failed", kind, id);
            State after = snapshot(kind, id);
            audit(operator, kind, id, auditBefore, auditSnapshot(kind, id, after));
            return new Result(true, "primordial.price_editor.result.saved", after);
        } catch (IOException | RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("[Primordial] Administrator price edit failed for kind {} {}", kind, id, failure);
            return result(false, "failed", kind, id);
        }
    }

    private static boolean valid(byte kind, ResourceLocation id) {
        if (id == null) return false;
        return switch (kind) {
            case 0 -> NeoForgeProjectEItemPrices.valid(id);
            case 1 -> BuiltInRegistries.FLUID.containsKey(id) && BuiltInRegistries.FLUID.get(id) != Fluids.EMPTY;
            case 2 -> NeoForgeMediaRegistry.validChemical(id);
            default -> false;
        };
    }

    private static Result result(boolean success, String message, byte kind, ResourceLocation id) {
        return new Result(success, "primordial.price_editor.result." + message, snapshot(kind, id));
    }

    private static State auditSnapshot(byte kind, ResourceLocation id, State effective) {
        if (kind == 0) return effective;
        var raw = NeoForgeRecoveryPrices.mediaState(kind == 1, id);
        return new State(raw.readable(), raw.mode(), raw.retainedPrice().toString(), effective.revision());
    }

    private static void audit(Operator operator, byte kind, ResourceLocation id, State before, State after) {
        JsonObject audit = new JsonObject();
        audit.addProperty("time", Instant.now().toString());
        audit.addProperty("operator", operator.name());
        audit.addProperty("operator_uuid", operator.uuid());
        audit.addProperty("origin", operator.origin());
        audit.addProperty("kind", kind == 0 ? "ITEM" : kind == 1 ? "FLUID" : "GAS");
        audit.addProperty("resource", id.toString());
        audit.addProperty("unit", kind == 0 ? "EMC/item" : kind == 1 ? "EMC/1000mB" : "EMC/mB");
        audit.add("before", stateJson(before));
        audit.add("after", stateJson(after));
        LogUtils.getLogger().info("[Primordial][PriceAudit] {}", audit);
    }

    private static JsonObject stateJson(State state) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", state.mode().name());
        json.addProperty("price", state.price());
        return json;
    }
}
