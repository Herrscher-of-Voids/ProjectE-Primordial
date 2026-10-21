/**
 * 【文件职责】处理加速器绑定、单目标管理和范围名单编辑，所有写入由服务端确认。
 * 【变更记录 #1】2026-09-18 01:38:53（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无；接管原单方块绑定分支，避免执行服务持续膨胀。
 * - 新增：实体 UUID、范围中心、全局时钟绑定和按绑定 UUID 编辑/启停/删除的入口。
 * - 修改：变更运行参数或名单会暂停该目标并使旧确认失效；追加目标不延长本轮时间。
 * - 用途：物品与管理菜单共用校验、目标数量和 NBT 大小边界，客户端不能上传任意目标 NBT。
 */
/**
 * 【变更记录 #2】2026-09-18 02:16:58（第 2 次备注 · 1.0.0-acceleration-modes）
 * - 删除：世界模式绑定无返回值，导致界面把游戏规则禁止误报为成功的流程。
 * - 新增：绑定操作返回真实受理结果。
 * - 修改：目标无效、总开关关闭、绑定数或 NBT 超限时返回失败，确认待处理算已受理。
 * - 用途：AccelerationMenu 的操作状态与实际服务端校验结果一致。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.item.EmcAcceleratorItem;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public final class AccelerationBindingActions {
    public static final UUID SELECTION = new UUID(0, 0);
    private AccelerationBindingActions() { }

    static void block(ItemStack tool, ServerPlayer player, ServerLevel level, BlockPos pos, InteractionHand hand) {
        var settings = AccelerationSettings.read(tool.getOrCreateTag());
        if (settings.kind().filter()) {
            boolean saved = AccelerationArea.mark(tool, player, level, pos, player.isShiftKeyDown());
            EmcAccelerationService.message(player, saved ? "message.primordial.acceleration_marked" : "message.primordial.acceleration_mark_failed");
            return;
        }
        if (settings.kind().world()) { world(tool, player, hand); return; }
        if (settings.kind() == AccelerationSettings.Kind.ENTITY) return;
        var candidate = new CompoundTag();
        settings.write(candidate);
        position(candidate, level, pos);
        if (settings.kind() == AccelerationSettings.Kind.AREA) {
            if (!AccelerationArea.valid(level, candidate)) { invalid(player); return; }
        } else {
            AccelerationTarget target = AccelerationTargets.block(level, pos);
            if (target == null) { invalid(player); return; }
            candidate.putString(EmcAcceleratorItem.BLOCK_KEY, target.blockId());
        }
        bind(tool, player, hand, candidate);
    }

    public static void entity(ItemStack tool, ServerPlayer player, Entity entity, InteractionHand hand) {
        if (!(entity.level() instanceof ServerLevel level) || !AccelerationTargets.entityValid(level, entity)) { invalid(player); return; }
        var candidate = new CompoundTag();
        AccelerationSettings.read(tool.getOrCreateTag()).withKind(AccelerationSettings.Kind.ENTITY).write(candidate);
        position(candidate, level, entity.blockPosition());
        candidate.putUUID("EntityId", entity.getUUID());
        candidate.putString("EntityType", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        String name = entity.getDisplayName().getString();
        candidate.putString("EntityName", name.substring(0, Math.min(64, name.length())));
        bind(tool, player, hand, candidate);
    }

    public static boolean world(ItemStack tool, ServerPlayer player, InteractionHand hand) {
        var settings = AccelerationSettings.read(tool.getOrCreateTag());
        if (!settings.kind().world()) return false;
        var candidate = new CompoundTag();
        settings.write(candidate);
        position(candidate, player.server.overworld(), BlockPos.ZERO);
        if (AccelerationTargets.read(player.server, candidate) == null) { invalid(player); return false; }
        return bind(tool, player, hand, candidate);
    }

    private static boolean bind(ItemStack tool, ServerPlayer player, InteractionHand hand, CompoundTag candidate) {
        if (AccelerationRun.expired(tool, player.server)) AccelerationBindings.pauseAll(tool);
        var entries = AccelerationBindings.editable(tool);
        CompoundTag previous = AccelerationBindings.view(tool).stream().filter(entry -> sameTarget(entry, candidate)).findFirst().orElse(null);
        AccelerationConfirmation.cancel(player.getUUID());
        if (previous != null && AccelerationBindings.enabled(previous)) {
            AccelerationBindings.pause(previous);
            EmcAccelerationService.message(player, "message.primordial.acceleration_disabled", AccelerationBindings.rate(previous));
            return true;
        }
        if (!PrimordialServerConfig.accelerationEnabled()) {
            EmcAccelerationService.message(player, "message.primordial.acceleration_server_disabled"); return false;
        }
        if (previous == null) {
            if (entries.size() >= PrimordialServerConfig.accelerationMaxBoundTargets()) {
                EmcAccelerationService.message(player, "message.primordial.acceleration_target_limit", PrimordialServerConfig.accelerationMaxBoundTargets()); return false;
            }
            candidate.putUUID("BindingId", UUID.randomUUID());
            entries.add(candidate);
            if (tool.getOrCreateTag().sizeInBytes() > AccelerationArea.MAX_TOOL_BYTES) {
                entries.remove(entries.size() - 1); invalid(player); return false;
            }
            previous = candidate;
        } else {
            // 重新右键采用当前选择；几何变化时清除旧位置名单，避免标记落到范围外。
            var oldSettings = AccelerationSettings.read(previous);
            var settings = AccelerationSettings.read(candidate);
            if (!oldSettings.bounds(AccelerationBindings.pos(previous)).equals(settings.bounds(AccelerationBindings.pos(candidate)))) {
                previous.remove("AreaMarks"); previous.remove("IdleMarks");
            }
            for (String key : candidate.getAllKeys()) previous.put(key, candidate.get(key).copy());
            AccelerationArea.invalidate(AccelerationBindings.id(previous));
        }
        AccelerationConfirmation.start(tool, player, hand, List.of(previous));
        return true;
    }

    private static boolean sameTarget(CompoundTag left, CompoundTag right) {
        var kind = AccelerationSettings.kind(right);
        if (AccelerationSettings.kind(left) != kind) return false;
        if (!left.getString(EmcAcceleratorItem.DIMENSION_KEY).equals(right.getString(EmcAcceleratorItem.DIMENSION_KEY))) return false;
        if (kind == AccelerationSettings.Kind.ENTITY)
            return left.hasUUID("EntityId") && left.getUUID("EntityId").equals(right.getUUID("EntityId"));
        return kind.world() || AccelerationBindings.pos(left).equals(AccelerationBindings.pos(right));
    }

    public static boolean edit(ItemStack tool, ServerPlayer player, UUID id, AccelerationSettings settings) {
        if (!settings.valid() || settings.kind().filter()) return false;
        AccelerationBindings.editable(tool);
        CompoundTag entry = id.equals(SELECTION) ? tool.getOrCreateTag() : AccelerationBindings.find(tool, id);
        if (entry == null || !id.equals(SELECTION) && settings.kind() != AccelerationSettings.kind(entry)) return false;
        AccelerationConfirmation.cancel(player.getUUID());
        if (!id.equals(SELECTION)) {
            AccelerationBindings.pause(entry);
            var old = AccelerationSettings.read(entry);
            if (!old.bounds(AccelerationBindings.pos(entry)).equals(settings.bounds(AccelerationBindings.pos(entry)))) {
                entry.remove("AreaMarks"); entry.remove("IdleMarks");
            }
            AccelerationArea.invalidate(id);
        }
        settings.write(entry);
        return true;
    }

    public static boolean start(ItemStack tool, ServerPlayer player, InteractionHand hand, UUID id) {
        CompoundTag entry = AccelerationBindings.find(tool, id);
        if (entry == null || !PrimordialServerConfig.accelerationEnabled() || !EmcAccelerationService.validBinding(player.server, entry)) return false;
        if (AccelerationRun.expired(tool, player.server)) AccelerationBindings.pauseAll(tool);
        if (AccelerationBindings.enabled(entry)) return true;
        AccelerationConfirmation.start(tool, player, hand, List.of(entry));
        return true;
    }

    public static boolean pause(ItemStack tool, ServerPlayer player, UUID id, boolean remove) {
        CompoundTag entry = AccelerationBindings.find(tool, id);
        if (entry == null) return false;
        AccelerationConfirmation.cancel(player.getUUID());
        AccelerationBindings.pause(entry);
        AccelerationArea.invalidate(id);
        if (remove) AccelerationBindings.editable(tool).remove(entry);
        return true;
    }

    public static boolean policy(ItemStack tool, ServerPlayer player, UUID id, boolean idle, boolean whitelist, boolean clear) {
        CompoundTag entry = AccelerationBindings.find(tool, id);
        if (entry == null || AccelerationSettings.kind(entry) != AccelerationSettings.Kind.AREA) return false;
        AccelerationConfirmation.cancel(player.getUUID());
        AccelerationBindings.pause(entry);
        String prefix = idle ? "Idle" : "Area";
        entry.putBoolean(prefix + "Whitelist", whitelist);
        if (clear) entry.remove(prefix + "Marks");
        AccelerationArea.invalidate(id);
        return true;
    }

    public static boolean marking(ItemStack tool, UUID id, boolean idle) {
        CompoundTag entry = AccelerationBindings.find(tool, id);
        if (entry == null || AccelerationSettings.kind(entry) != AccelerationSettings.Kind.AREA) return false;
        CompoundTag root = tool.getOrCreateTag();
        root.putUUID("EditingArea", id);
        root.putString("AccelerationKind", (idle ? AccelerationSettings.Kind.IDLE_FILTER : AccelerationSettings.Kind.AREA_FILTER).name());
        return true;
    }

    public static void startAll(ItemStack tool, ServerPlayer player, InteractionHand hand) {
        if (!PrimordialServerConfig.accelerationEnabled()) return;
        if (AccelerationRun.expired(tool, player.server)) AccelerationBindings.pauseAll(tool);
        var entries = AccelerationBindings.view(tool).stream().filter(entry -> !AccelerationBindings.enabled(entry)
                && EmcAccelerationService.validBinding(player.server, entry)).toList();
        if (!entries.isEmpty()) AccelerationConfirmation.start(tool, player, hand, entries);
    }

    public static void pauseAll(ItemStack tool, ServerPlayer player) {
        AccelerationConfirmation.cancel(player.getUUID());
        AccelerationBindings.pauseAll(tool);
    }

    public static void prepare(ItemStack tool) { AccelerationBindings.editable(tool); }
    private static void position(CompoundTag entry, ServerLevel level, BlockPos pos) {
        entry.putString(EmcAcceleratorItem.DIMENSION_KEY, level.dimension().location().toString());
        entry.putInt(EmcAcceleratorItem.X_KEY, pos.getX()); entry.putInt(EmcAcceleratorItem.Y_KEY, pos.getY()); entry.putInt(EmcAcceleratorItem.Z_KEY, pos.getZ());
    }
    private static void invalid(ServerPlayer player) { EmcAccelerationService.message(player, "message.primordial.acceleration_invalid"); }
}
