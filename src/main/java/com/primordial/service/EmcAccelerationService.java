/**
 * 文件职责：管理 EMC 加速器绑定的方块实体，并在服务端 tick 中执行有限次数的额外机器 tick。
 *
 * 变更记录 #1（2026-09-16，EMC 加速功能）
 * - 新增：支持 2、4、8、16、32、64、128、256、512 和 1024 倍速，费用按倍率/2 线性计算。
 * - 新增：目标位置、维度、倍率、启用状态和高倍率确认状态均保存在加速器物品 NBT 中。
 * - 新增：调用方块原生 ticker，而不是修改第三方方块实体字段，兼容大多数 Forge 机器。
 * - 修改：每个服务端 tick 限制目标数量；费用扣除成功后才执行额外 tick，余额不足时不改变机器状态。
 * - 用途：提供统一的 EMC 计费和性能边界，避免高倍率通过无界循环造成服务端卡死。
 *
 * 性能规则：倍率表示“总速度”，因此额外执行次数为倍率减一；256 倍及以上必须由玩家确认警告。
 * 额外 ticker 调用始终在服务端线程执行，目标卸载、没有 ticker 或 ticker 抛出异常时会安全跳过。
 */
/**
 * 【变更记录 #2】2026-09-16 13:32:00（第 2 次备注 · 1.0.0-acceleration-safety）
 * - 删除：目标数量限制下的无耗时保护循环、失败全额退款、聊天消息二次右键确认及重复位置叠加。
 * - 新增：全服预算和轮转扫描、实际调用计费、持物校验的一次性确认令牌、故障停用和停服清理。
 * - 修改：高倍率每次启动必须经服务端验证弹窗响应；确认只对当前会话和玩家有效，余额不足会暂停。
 * - 用途：机器或随机 tick 方块使用 ProjectE EMC 加速；未加载、被替换或无原生回调的目标不会被扣费。
 */
/**
 * 【变更记录 #3】2026-09-16 14:36:38（第 3 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：绑定新位置覆盖唯一目标，以及切换选择倍率会停用其它机器的行为。
 * - 新增：有界多目标列表、每目标独立倍率和启停、批量启停、轮转调度及实际扣款统计。
 * - 修改：单目标费用为倍率乘以 1000 EMC/tick；继续使用 ProjectE 原生账户与全服预算，实际工作结算后同步扣款明细。
 * - 用途：同一把加速器可同时加速多个位置，高倍率批量启动交由 AccelerationConfirmation 验证。
 */
/**
 * 【变更记录 #4】2026-09-16 15:26:22（第 4 次备注 · 1.0.0-review-ready）
 * - 删除：无时长/总费用上限的持续执行。
 * - 新增：每把工具运行轮次检查和部分工作授权，实际扣款累计到共同的 21 亿 EMC 额度。
 * - 修改：到期或额度不足时全部暂停；额外调用数先按剩余额度裁剪，退款后记录净支出。
 * - 用途：600 tick 后必须手动重新激活，多目标不能在同一轮绕过消费上限。
 */
/**
 * 【文件职责】执行多目标加速、服务端扣款、退款和安全停用。
 * 【变更记录 #5】2026-09-16 19:41:50（第 5 次备注 · 1.0.0-review-ready）
 * - 删除：移除无限运行及不受总额度限制的扣款路径。
 * - 新增：运行前检查600 tick 截止，按剩余额度裁剪调用；实际执行记录送入有界倍率快照。
 * - 修改：净扣款累计到工具；限流退款，额度不足或时间到期停用，追加目标不续期旧目标。
 * - 用途：确保每轮最多21亿 EMC，继续遵守全服调用与耗时预算，不强制加载区块。
 */
/**
 * 【变更记录 #6】2026-09-18 01:06:25（第 6 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：每 tick 收取整秒报价、每次限流单独向上取整及未使用的 long 费用入口。
 * - 新增：每秒报价和精确额外调用单价，按 秒价 / 20 / (倍率 - 1) 经 EmcService 预扣、执行、退款。
 * - 修改：余额不足完整一 tick 时允许可支付的部分工作；找零持续累计，扣款回执更新 HUD 和本轮额度。
 * - 用途：默认单目标每 20 tick 消耗倍率乘 1000 EMC；时长、确认、位置去重与全服性能预算保持有效。
 */
/**
 * 【变更记录 #7】2026-09-18 01:41:19（第 7 次备注 · 1.0.0-acceleration-modes）
 * - 删除：执行入口只能解析单个方块的限制；绑定编辑移至 AccelerationBindingActions。
 * - 新增：范围分派及通用 AccelerationWork 结算，实体、时钟和范围成员共享全服预算与去重。
 * - 修改：空闲降频按实际档位单价执行，所有目标继续共同消耗 600 tick 与 21 亿额度。
 * - 用途：只保留一条预扣、调用、退款、统计和错误停用链，重启时清理范围运行缓存。
 */
package com.primordial.service;

import com.primordial.PrimordialMod;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.item.EmcAcceleratorItem;
import com.primordial.util.BalanceSyncer;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "primordial", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmcAccelerationService {
    private static final int MIN_MULTIPLIER = 2;
    private static final int MAX_MULTIPLIER = 1024;
    private static final int WARNING_MULTIPLIER = 256;
    // 仅由服务端主线程访问；弱键不会因物品被丢弃、删除或玩家离线而永久保留堆栈。
    private static final Map<ItemStack, Integer> TARGET_CURSORS = new WeakHashMap<>();
    private static long tickStart;
    private static int cursor;

    private EmcAccelerationService() { }

    public static int minMultiplier() { return MIN_MULTIPLIER; }
    public static int maxMultiplier() { return MAX_MULTIPLIER; }
    public static int warningMultiplier() { return WARNING_MULTIPLIER; }

    public static int multiplier(ItemStack stack) {
        return stack.getItem() instanceof EmcAcceleratorItem && stack.hasTag()
                ? AccelerationBindings.rate(stack.getTag()) : MIN_MULTIPLIER;
    }

    public static BigInteger costPerSecondEmc(ItemStack stack) { return costPerSecond(multiplier(stack)); }

    public static BigInteger costPerSecond(int multiplier) {
        int normalized = isMultiplier(multiplier) ? multiplier : MIN_MULTIPLIER;
        return PrimordialServerConfig.accelerationEmcPerMultiplierSecond().multiply(BigInteger.valueOf(normalized));
    }

    public static boolean isMultiplier(int value) {
        return value >= MIN_MULTIPLIER && value <= MAX_MULTIPLIER && (value & (value - 1)) == 0;
    }

    public static int nextMultiplier(int current) {
        return !isMultiplier(current) || current >= MAX_MULTIPLIER ? MIN_MULTIPLIER : current * 2;
    }

    /** 一秒为 20 个游戏 tick；保留分数，限流和极低自定义单价不会逐次取整多收费。 */
    static EmcAmount costPerExtraTick(int rate) {
        int normalized = isMultiplier(rate) ? rate : MIN_MULTIPLIER;
        return EmcAmount.of(costPerSecond(normalized)).divide(20L * (normalized - 1));
    }

    /** 每次右键只操作当前目标；不同位置追加绑定，不会覆盖或停用其它机器。 */
    public static void bind(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos pos, InteractionHand hand) {
        AccelerationBindingActions.block(stack, player, level, pos, hand);
    }

    /** 空气右键：有运行中的目标则全部暂停，否则按各目标保存的倍率批量恢复。 */
    public static void toggle(ItemStack stack, ServerPlayer player, InteractionHand hand) {
        expireTool(stack, player);
        AccelerationBindings.editable(stack);
        var entries = AccelerationBindings.view(stack);
        if (entries.isEmpty()) { message(player, "message.primordial.acceleration_unbound"); return; }
        AccelerationConfirmation.cancel(player.getUUID());
        if (entries.stream().anyMatch(AccelerationBindings::enabled)) {
            AccelerationBindings.pauseAll(stack);
            message(player, "message.primordial.acceleration_paused_all");
            return;
        }
        if (!PrimordialServerConfig.accelerationEnabled()) {
            message(player, "message.primordial.acceleration_server_disabled");
            return;
        }
        var ready = new ArrayList<CompoundTag>();
        for (CompoundTag entry : entries) {
            if (validBinding(player.server, entry)) ready.add(entry);
            else AccelerationBindings.pause(entry);
        }
        if (ready.isEmpty()) { message(player, "message.primordial.acceleration_invalid"); return; }
        AccelerationConfirmation.start(stack, player, hand, ready);
    }

    /** 只改变下一次启动目标的选择倍率，已经运行的其它目标保留各自倍率。 */
    public static void cycleMultiplier(ItemStack stack, ServerPlayer player) {
        cycleMultiplier(stack, player, false);
    }

    public static void cycleMultiplier(ItemStack stack, ServerPlayer player, boolean reverse) {
        AccelerationBindings.editable(stack);
        int current = multiplier(stack);
        int next = reverse ? (current <= MIN_MULTIPLIER ? MAX_MULTIPLIER : current / 2) : nextMultiplier(current);
        AccelerationConfirmation.cancel(player.getUUID());
        stack.getOrCreateTag().putInt(EmcAcceleratorItem.MULTIPLIER_KEY, next);
        message(player, "message.primordial.acceleration_rate", next);
    }

    public static void clear(ItemStack stack, ServerPlayer player) {
        AccelerationConfirmation.cancel(player.getUUID());
        AccelerationBindings.pauseAll(stack);
        AccelerationBindings.view(stack).forEach(entry -> AccelerationArea.invalidate(AccelerationBindings.id(entry)));
        AccelerationBindings.editable(stack).clear();
        TARGET_CURSORS.remove(stack);
        message(player, "message.primordial.acceleration_cleared");
    }

    public static void confirm(ServerPlayer player, UUID token, boolean accepted) {
        AccelerationConfirmation.confirm(player, token, accepted);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        AccelerationMarkers.synchronize(server);
        AccelerationArea.expire(server);
        AccelerationConfirmation.expire(server);
        if (!PrimordialServerConfig.accelerationEnabled()) { pauseAll(server); return; }
        long headroom = PrimordialServerConfig.accelerationPauseAboveNanos() - (System.nanoTime() - tickStart);
        if (headroom <= 0) return;
        var budget = new AccelerationBudget(PrimordialServerConfig.accelerationMaxTargetsPerTick(),
                PrimordialServerConfig.accelerationMaxExtraTicks(),
                Math.min(headroom, PrimordialServerConfig.accelerationTimeBudgetNanos()), System::nanoTime);
        var players = server.getPlayerList().getPlayers();
        int slots = players.size() * 37;
        for (int inspected = 0; inspected < slots && budget.acceptsTarget(); inspected++) {
            cursor = Math.floorMod(cursor, slots);
            ServerPlayer player = players.get(cursor / 37);
            int slot = cursor++ % 37;
            ItemStack stack = slot == 36 ? player.getOffhandItem() : player.getInventory().items.get(slot);
            if (player.isAlive() && !player.isSpectator()) process(server, player, stack, budget);
        }
    }

    /** 超时只能由玩家重新激活，不能因追加目标给旧目标续期。 */
    private static void expireTool(ItemStack stack, ServerPlayer player) {
        if (AccelerationRun.expired(stack, player.server)) AccelerationBindings.pauseAll(stack);
    }

    /** 每把工具的目标也轮转，避免高倍率用尽预算后永远只有列表第一台获得运行机会。 */
    static void process(MinecraftServer server, ServerPlayer player, ItemStack stack, AccelerationBudget budget) {
        if (!(stack.getItem() instanceof EmcAcceleratorItem) || !stack.hasTag()) return;
        AccelerationBindings.editable(stack);
        var entries = AccelerationBindings.view(stack);
        if (entries.isEmpty()) return;
        if (entries.stream().anyMatch(AccelerationBindings::enabled) && AccelerationRun.expired(stack, server)) {
            AccelerationBindings.pauseAll(stack);
            message(player, "message.primordial.acceleration_expired");
            return;
        }
        int start = Math.floorMod(TARGET_CURSORS.getOrDefault(stack, 0), entries.size());
        for (int i = 0; i < entries.size() && budget.acceptsTarget(); i++) {
            int index = (start + i) % entries.size();
            TARGET_CURSORS.put(stack, (index + 1) % entries.size());
            processEntry(server, player, stack, entries.get(index), budget);
        }
    }

    private static void processEntry(MinecraftServer server, ServerPlayer player, ItemStack tool, CompoundTag tag, AccelerationBudget budget) {
        if (!AccelerationBindings.enabled(tag) || AccelerationBindings.pending(tag)) return;
        int rate = AccelerationBindings.rate(tag);
        if (!AccelerationConfirmation.authorized(tag, player.getUUID())) {
            pause(tag, player, "message.primordial.acceleration_confirmation_required");
            return;
        }
        if (AccelerationSettings.kind(tag) == AccelerationSettings.Kind.AREA) {
            AccelerationArea.process(server, player, tool, tag, budget);
            return;
        }
        AccelerationWork target = AccelerationTargets.read(server, tag);
        if (target == null) {
            pause(tag, player, "message.primordial.acceleration_invalid");
            return;
        }
        execute(server, player, tool, tag, budget, target, rate);
    }

    static void execute(MinecraftServer server, ServerPlayer player, ItemStack tool, CompoundTag tag,
                        AccelerationBudget budget, AccelerationWork target, int rate) {
        if (!budget.claim(target.key())) return;
        EmcAmount unitPrice = costPerExtraTick(rate);
        int permitted = (int) EmcAmount.of(AccelerationRun.remaining(tool)).affordableUnits(unitPrice, rate - 1);
        if (permitted == 0) {
            AccelerationBindings.pauseAll(tool);
            message(player, "message.primordial.acceleration_spend_limit");
            return;
        }
        // 回调只在预扣成功后执行，预算器记录失败调用；退款不会把已尝试的机器工作变成免费。
        var work = new AccelerationBudget.Result[1];
        var receipt = EmcService.purchaseWithReceipt(server, player.getUUID(), unitPrice, permitted, affordable -> {
            work[0] = budget.run((int) affordable, target.valid(), target.tick());
            return work[0].attempted();
        });
        if (work[0] == null) {
            pause(tag, player, "message.primordial.acceleration_no_emc");
            return;
        }
        var result = work[0];
        // 嵌套资源交易影响找零时，负净款不能扩大本轮 21 亿消费额度。
        BigInteger charged = receipt.charged().max(BigInteger.ZERO);
        BalanceSyncer.recordAccelerationCharge(player.getUUID(), charged);
        AccelerationRun.charge(tool, charged);
        if (result.attempted() > 0 && target.entityId() >= -1)
            AccelerationMarkers.record(server, tool, tag, target.level(), target.position(), rate, target.entityId());
        if (AccelerationRun.remaining(tool).signum() == 0 || permitted < rate - 1 && result.attempted() == permitted) {
            AccelerationBindings.pauseAll(tool);
            message(player, "message.primordial.acceleration_spend_limit");
        }
        if (result.failure() != null) {
            pause(tag, player, "message.primordial.acceleration_error");
            PrimordialMod.LOGGER.warn("[Primordial] Accelerator stopped at {} {}", target.level().dimension().location(), target.position(), result.failure());
        } else if (result.slow()) {
            pause(tag, player, "message.primordial.acceleration_slow");
        } else if (result.invalid()) {
            pause(tag, player, "message.primordial.acceleration_invalid");
        }
    }

    static boolean validBinding(MinecraftServer server, CompoundTag entry) {
        return AccelerationSettings.kind(entry) == AccelerationSettings.Kind.AREA
                ? AccelerationArea.valid(AccelerationTargets.level(server, entry), entry) : AccelerationTargets.read(server, entry) != null;
    }

    private static void pause(CompoundTag tag, ServerPlayer player, String key) {
        AccelerationBindings.pause(tag);
        message(player, key, AccelerationBindings.rate(tag));
    }

    static void message(ServerPlayer player, String key, Object... values) {
        player.displayClientMessage(Component.translatable(key, values), true);
    }

    private static void pauseAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getInventory().items.forEach(EmcAccelerationService::pauseTool);
            player.getInventory().offhand.forEach(EmcAccelerationService::pauseTool);
        }
        AccelerationConfirmation.reset();
    }

    private static void pauseTool(ItemStack stack) {
        if (stack.getItem() instanceof EmcAcceleratorItem) AccelerationBindings.pauseAll(stack);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        event.getEntity().getInventory().items.forEach(EmcAccelerationService::pauseTool);
        event.getEntity().getInventory().offhand.forEach(EmcAccelerationService::pauseTool);
        AccelerationConfirmation.cancel(event.getEntity().getUUID());
        BalanceSyncer.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void onServerStart(ServerAboutToStartEvent event) {
        AccelerationMarkers.clear();
        AccelerationArea.clear();
        AccelerationConfirmation.reset(); AccelerationRun.reset(); TARGET_CURSORS.clear(); BalanceSyncer.clearAll(); cursor = 0; tickStart = 0;
    }

    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) {
        AccelerationMarkers.clear();
        AccelerationArea.clear();
        AccelerationConfirmation.reset(); TARGET_CURSORS.clear(); BalanceSyncer.clearAll();
    }
}
