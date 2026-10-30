package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.acceleration.AccelerationConfig;
import com.primordial.neoforge.acceleration.EmcAcceleratorItem;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.primordial.service.EmcAmount;
import com.primordial.neoforge.emc.NeoForgeEmcTransactions;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "primordial")
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
        return stack.getItem() instanceof EmcAcceleratorItem && AccelerationData.has(stack)
                ? AccelerationBindings.rate(AccelerationData.get(stack)) : MIN_MULTIPLIER;
    }

    public static BigInteger costPerSecondEmc(ItemStack stack) { return costPerSecond(multiplier(stack)); }

    public static BigInteger costPerSecond(int multiplier) {
        int normalized = isMultiplier(multiplier) ? multiplier : MIN_MULTIPLIER;
        return AccelerationConfig.accelerationEmcPerMultiplierSecond().multiply(BigInteger.valueOf(normalized));
    }

    public static boolean isMultiplier(int value) {
        return value >= MIN_MULTIPLIER && value <= MAX_MULTIPLIER && (value & (value - 1)) == 0;
    }

    public static int nextMultiplier(int current) {
        return !isMultiplier(current) || current >= MAX_MULTIPLIER ? MIN_MULTIPLIER : current * 2;
    }


    static EmcAmount costPerExtraTick(int rate) {
        int normalized = isMultiplier(rate) ? rate : MIN_MULTIPLIER;
        return EmcAmount.of(costPerSecond(normalized)).divide(20L * (normalized - 1));
    }


    public static void bind(ItemStack stack, ServerPlayer player, ServerLevel level, BlockPos pos, InteractionHand hand) {
        try (var edit = AccelerationData.edit(stack)) {
        AccelerationBindingActions.block(stack, player, level, pos, hand);

        }
    }


    public static void toggle(ItemStack stack, ServerPlayer player, InteractionHand hand) {
        try (var edit = AccelerationData.edit(stack)) {
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
        if (!AccelerationConfig.accelerationEnabled()) {
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
    }


    public static void cycleMultiplier(ItemStack stack, ServerPlayer player) {
        cycleMultiplier(stack, player, false);
    }

    public static void cycleMultiplier(ItemStack stack, ServerPlayer player, boolean reverse) {
        try (var edit = AccelerationData.edit(stack)) {
        AccelerationBindings.editable(stack);
        int current = multiplier(stack);
        int next = reverse ? (current <= MIN_MULTIPLIER ? MAX_MULTIPLIER : current / 2) : nextMultiplier(current);
        AccelerationConfirmation.cancel(player.getUUID());
        AccelerationData.get(stack).putInt(EmcAcceleratorItem.MULTIPLIER_KEY, next);
        message(player, "message.primordial.acceleration_rate", next);

        }
    }

    public static void clear(ItemStack stack, ServerPlayer player) {
        try (var edit = AccelerationData.edit(stack)) {
        AccelerationConfirmation.cancel(player.getUUID());
        AccelerationBindings.pauseAll(stack);
        AccelerationBindings.view(stack).forEach(entry -> AccelerationArea.invalidate(AccelerationBindings.id(entry)));
        AccelerationBindings.editable(stack).clear();
        TARGET_CURSORS.remove(stack);
        message(player, "message.primordial.acceleration_cleared");

        }
    }

    public static void confirm(ServerPlayer player, UUID token, boolean accepted) {
        AccelerationConfirmation.confirm(player, token, accepted);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTickStart(ServerTickEvent.Pre event) {
        tickStart = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTick(ServerTickEvent.Post event) {

        MinecraftServer server = event.getServer();
        if (server == null) return;
        AccelerationMarkers.synchronize(server);
        AccelerationArea.expire(server);
        AccelerationConfirmation.expire(server);
        if (!AccelerationConfig.accelerationEnabled()) { pauseAll(server); return; }
        long headroom = AccelerationConfig.accelerationPauseAboveNanos() - (System.nanoTime() - tickStart);
        if (headroom <= 0) return;
        var budget = new AccelerationBudget(AccelerationConfig.accelerationMaxTargetsPerTick(),
                AccelerationConfig.accelerationMaxExtraTicks(),
                Math.min(headroom, AccelerationConfig.accelerationTimeBudgetNanos()), System::nanoTime);
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


    private static void expireTool(ItemStack stack, ServerPlayer player) {
        if (AccelerationRun.expired(stack, player.server)) AccelerationBindings.pauseAll(stack);
    }


    static void process(MinecraftServer server, ServerPlayer player, ItemStack stack, AccelerationBudget budget) {

        try (var edit = AccelerationData.edit(stack)) {
        if (!(stack.getItem() instanceof EmcAcceleratorItem) || !AccelerationData.has(stack)) return;
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
        var receipt = NeoForgeEmcTransactions.purchaseWithReceipt(server, player.getUUID(), unitPrice, permitted, affordable -> {
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
        AccelerationRun.charge(tool, charged);
        if (result.attempted() > 0 && target.entityId() >= -1)
            AccelerationMarkers.record(server, tool, tag, target.level(), target.position(), rate, target.entityId());
        if (AccelerationRun.remaining(tool).signum() == 0 || permitted < rate - 1 && result.attempted() == permitted) {
            AccelerationBindings.pauseAll(tool);
            message(player, "message.primordial.acceleration_spend_limit");
        }
        if (result.failure() != null) {
            pause(tag, player, "message.primordial.acceleration_error");
            com.mojang.logging.LogUtils.getLogger().warn("[Primordial] Accelerator stopped at {} {}", target.level().dimension().location(), target.position(), result.failure());
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

        try (var edit = AccelerationData.edit(stack)) {
        if (stack.getItem() instanceof EmcAcceleratorItem) AccelerationBindings.pauseAll(stack);

        }


    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        event.getEntity().getInventory().items.forEach(EmcAccelerationService::pauseTool);
        event.getEntity().getInventory().offhand.forEach(EmcAccelerationService::pauseTool);
        AccelerationConfirmation.cancel(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void onServerStart(ServerAboutToStartEvent event) {
        AccelerationMarkers.clear();
        AccelerationArea.clear();
        AccelerationConfirmation.reset(); AccelerationRun.reset(); TARGET_CURSORS.clear();  cursor = 0; tickStart = 0;
    }

    @SubscribeEvent public static void onServerStopped(ServerStoppedEvent event) {
        AccelerationMarkers.clear();
        AccelerationArea.clear();
        AccelerationConfirmation.reset(); TARGET_CURSORS.clear();
    }
}
