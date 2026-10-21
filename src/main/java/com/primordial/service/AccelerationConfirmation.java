/**
 * 【文件职责】为单目标或批量启动保管高倍率确认，客户端只能接受或拒绝服务端报价。
 * 【变更记录 #1】2026-09-16 14:36:38（第 1 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：无；从加速服务拆出原有确认状态，避免多目标改动使单类继续膨胀。
 * - 新增：整批目标快照及合计价格、逐目标会话授权、过期和取消清理。
 * - 修改：任一目标倍率、NBT、持物或报价改变时整批拒绝；取消不影响其它已运行的目标。
 * - 用途：每次启动 256/512/1024 倍目标必须弹窗，暂停后不能复用旧许可。
 */
/**
 * 【变更记录 #2】2026-09-16 15:26:22（第 2 次备注 · 1.0.0-review-ready）
 * - 删除：确认后无限期运行。
 * - 新增：启用前建立工具运行轮次，合计最多 600 tick 与 21 亿 EMC。
 * - 修改：轮内追加目标共享额度；确认取消不开始计时。
 * - 用途：服务端确认成为唯一计时起点，性能弹窗等待不计入运行时间。
 */
/**
 * 【文件职责】处理高倍率确认及低倍率直接激活。
 * 【变更记录 #3】2026-09-16 19:41:50（第 3 次备注 · 1.0.0-review-ready）
 * - 删除：不再允许启用目标而没有本轮时间和消费记录。
 * - 新增：低倍率直接启动与高倍率确认后都调用 AccelerationRun.begin。
 * - 修改：计时起点为实际同意启动的服务端 tick，等待弹窗不消耗运行时长。
 * - 用途：多目标共用同一轮；同一工具全部停用后再启动重新计时。
 */
/**
 * 【变更记录 #4】2026-09-18 01:08:12（第 4 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：按 tick 报价的调用。
 * - 新增：无。
 * - 修改：批量启动消息、警告弹窗及确认快照统一使用各目标每秒报价之和。
 * - 用途：展示的秒价与 EmcAccelerationService 实际收费一致；价格变化仍使旧确认失效。
 */
/**
 * 【变更记录 #5】2026-09-18 01:41:19（第 5 次备注 · 1.0.0-acceleration-modes）
 * - 删除：所有绑定均只代表一个收费目标的假设。
 * - 新增：范围报价按最大格数计算上限，实体与全局时钟各作为一个目标报价。
 * - 修改：确认仍核对完整服务端快照，几何、名单或单价变化不能复用旧许可。
 * - 用途：让范围最高费用在高倍率警告中可见；实际收费只按执行到的成员和调用次数。
 */
package com.primordial.service;

import com.primordial.config.PrimordialServerConfig;
import com.primordial.item.EmcAcceleratorItem;
import com.primordial.network.S2CAccelerationWarningPacket;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

final class AccelerationConfirmation {
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static UUID session = UUID.randomUUID();

    private AccelerationConfirmation() { }

    static void start(ItemStack stack, ServerPlayer player, InteractionHand hand, List<CompoundTag> entries) {
        cancel(player.getUUID());
        entries.forEach(AccelerationBindings::pause);
        int maxRate = entries.stream().mapToInt(AccelerationBindings::rate).max().orElse(2);
        if (maxRate < EmcAccelerationService.warningMultiplier()) {
            enable(stack, entries, player);
            return;
        }
        entries.forEach(entry -> entry.putBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY, true));
        UUID token = UUID.randomUUID();
        BigInteger price = price(entries);
        PENDING.put(player.getUUID(), new Pending(token, stack, hand, List.copyOf(entries),
                entries.stream().map(CompoundTag::copy).toList(), price, player.server.getTickCount()));
        S2CAccelerationWarningPacket.send(player, token, maxRate, AccelerationBindings.pos(entries.get(0)), entries.size(), price.toString());
    }

    static void confirm(ServerPlayer player, UUID token, boolean accepted) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.token().equals(token)) return;
        PENDING.remove(player.getUUID());
        boolean valid = accepted && player.server.getTickCount() - pending.created() <= 600
                && player.getItemInHand(pending.hand()) == pending.stack() && player.isAlive() && !player.isSpectator()
                && pending.stack().getItem() instanceof EmcAcceleratorItem && PrimordialServerConfig.accelerationEnabled()
                && pending.entries().equals(pending.snapshots()) && pending.price().equals(price(pending.entries()));
        List<CompoundTag> current = AccelerationBindings.view(pending.stack());
        if (valid) {
            valid = pending.entries().stream().allMatch(entry -> current.stream().anyMatch(now -> now == entry)
                    && EmcAccelerationService.validBinding(player.server, entry));
        }
        pending.entries().forEach(AccelerationBindings::pause);
        if (valid) enable(pending.stack(), pending.entries(), player);
    }

    static BigInteger price(List<CompoundTag> entries) {
        BigInteger total = BigInteger.ZERO;
        for (CompoundTag entry : entries) {
            int targets = AccelerationSettings.kind(entry) == AccelerationSettings.Kind.AREA ? AccelerationSettings.read(entry).volume() : 1;
            total = total.add(EmcAccelerationService.costPerSecond(AccelerationBindings.rate(entry)).multiply(BigInteger.valueOf(targets)));
        }
        return total;
    }

    private static void enable(ItemStack stack, List<CompoundTag> entries, ServerPlayer player) {
        AccelerationRun.begin(stack, player.server);
        for (CompoundTag entry : entries) {
            entry.putUUID("ConfirmedSession", session);
            entry.putUUID("ConfirmedOwner", player.getUUID());
            entry.putBoolean(EmcAcceleratorItem.ENABLED_KEY, true);
            entry.putBoolean(EmcAcceleratorItem.WARNING_PENDING_KEY, false);
        }
        EmcAccelerationService.message(player, "message.primordial.acceleration_started_targets", entries.size(), price(entries).toString());
    }

    static boolean authorized(CompoundTag entry, UUID player) {
        return AccelerationBindings.rate(entry) < EmcAccelerationService.warningMultiplier()
                || entry.hasUUID("ConfirmedSession") && session.equals(entry.getUUID("ConfirmedSession"))
                && entry.hasUUID("ConfirmedOwner") && player.equals(entry.getUUID("ConfirmedOwner"));
    }

    static void cancel(UUID player) {
        Pending pending = PENDING.remove(player);
        if (pending != null) pending.entries().forEach(AccelerationBindings::pause);
    }

    static void expire(MinecraftServer server) {
        PENDING.entrySet().removeIf(entry -> {
            if (server.getTickCount() - entry.getValue().created() <= 600) return false;
            entry.getValue().entries().forEach(AccelerationBindings::pause);
            return true;
        });
    }

    static void reset() {
        PENDING.values().forEach(pending -> pending.entries().forEach(AccelerationBindings::pause));
        PENDING.clear();
        session = UUID.randomUUID();
    }

    private record Pending(UUID token, ItemStack stack, InteractionHand hand, List<CompoundTag> entries,
                           List<CompoundTag> snapshots, BigInteger price, int created) { }
}
