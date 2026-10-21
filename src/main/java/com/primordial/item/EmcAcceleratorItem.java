/**
 * 文件职责：提供以 EMC 付费的方块实体加速器物品。
 *
 * 变更记录 #1（2026-09-16，EMC 加速功能）
 * - 新增：右键方块绑定目标，潜行右键循环倍率，右键空气清除绑定。
 * - 新增：倍率范围 2 至 1024；256、512 和 1024 倍必须经过性能警告确认。
 * - 修改：实际计费和 ticker 执行委托 EmcAccelerationService，物品只负责交互与 NBT 状态。
 * - 用途：让玩家可以使用 EMC 加速机器，同时不把第三方机器 API 写入物品类。
 */
/**
 * 【变更记录 #2】2026-09-16 13:36:00（第 2 次备注 · 1.0.0-acceleration-safety）
 * - 删除：仅在 useOn 处理导致机器 GUI 先消费右键的路径。
 * - 新增：onItemUseFirst 优先交互、空气右键启停、潜行空气右键解绑及目标/暂停提示。
 * - 修改：切换倍率会停用旧任务；启动与高倍率确认统一委托服务端服务。
 * - 用途：物品只保存绑定和接收交互，不在客户端扣款或运行机器。
 */
/**
 * 【变更记录 #3】2026-09-16 14:41:00（第 3 次备注 · 1.0.0-acceleration-multiple-targets）
 * - 删除：提示只能列出物品根节点中的唯一位置。
 * - 新增：绑定总数、运行数、待确认状态和最多 5 条独立目标摘要。
 * - 修改：倍率和单目标报价指向下一次启动的选择；各目标独立保存运行倍率。
 * - 用途：玩家可从提示确认多台机器已绑定，长列表仅显示摘要避免撑满屏幕。
 */
/**
 * 【文件职责】处理加速器交互与提示。
 * 【变更记录 #4】2026-09-16 19:41:50（第 4 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：每轮600 tick、同工具目标共享21亿EMC额度提示。
 * - 修改：与服务端实际限额对应，既有多目标操作保留。
 * - 用途：玩家可在物品提示中确认新的时长与费用边界。
 */
/**
 * 【变更记录 #5】2026-09-18 01:08:12（第 5 次备注 · 1.0.0-acceleration-second-pricing）
 * - 删除：物品提示中的每 tick 费用读取。
 * - 新增：无。
 * - 修改：提示从统一加速服务读取每秒单目标报价，中英文单位同步改为 EMC/秒。
 * - 用途：显示倍率乘 1000 的秒价，保留多目标摘要、30 秒时限和 21 亿 EMC 上限提示。
 */
/**
 * 【变更记录 #6】2026-09-18 01:53:11（第 6 次备注 · 1.0.0-acceleration-modes）
 * - 删除：所有右键都按方块绑定处理的假设。
 * - 新增：模式提示、世界模式空气启动、实体模式射线补选及范围名单的潜行同类标记。
 * - 修改：普通潜行方块右键仍循环倍率；管理按键见控制设置，实体标记由服务端实际目标决定。
 * - 用途：统一物品交互与管理菜单，实体射线被方块遮挡且限制在原版交互距离内。
 */
package com.primordial.item;

import com.primordial.service.EmcAccelerationService;
import com.primordial.service.AccelerationBindings;
import com.primordial.service.AccelerationBindingActions;
import com.primordial.service.AccelerationSettings;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

public final class EmcAcceleratorItem extends Item {
    public static final String DIMENSION_KEY = "TargetDimension";
    public static final String X_KEY = "TargetX";
    public static final String Y_KEY = "TargetY";
    public static final String Z_KEY = "TargetZ";
    public static final String BLOCK_KEY = "TargetBlock";
    public static final String MULTIPLIER_KEY = "Multiplier";
    public static final String ENABLED_KEY = "Enabled";
    public static final String WARNING_PENDING_KEY = "WarningPending";

    public EmcAcceleratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        var kind = AccelerationSettings.read(stack.getOrCreateTag()).kind();
        if (player.isShiftKeyDown() && !kind.filter()) {
            EmcAccelerationService.cycleMultiplier(context.getItemInHand(), player);
        } else {
            EmcAccelerationService.bind(context.getItemInHand(), player, player.serverLevel(), context.getClickedPos(), context.getHand());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var tool = player.getItemInHand(hand);
            var kind = AccelerationSettings.read(tool.getOrCreateTag()).kind();
            if (serverPlayer.isShiftKeyDown()) EmcAccelerationService.clear(player.getItemInHand(hand), serverPlayer);
            else if (kind.world()) AccelerationBindingActions.world(tool, serverPlayer, hand);
            else if (kind == AccelerationSettings.Kind.ENTITY && bindLookedEntity(tool, serverPlayer, hand)) { }
            else EmcAccelerationService.toggle(player.getItemInHand(hand), serverPlayer, hand);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        int rate = EmcAccelerationService.multiplier(stack);
        var settings = AccelerationSettings.read(stack.hasTag() ? stack.getTag() : new net.minecraft.nbt.CompoundTag());
        tooltip.add(Component.translatable("tooltip.primordial.accelerator_mode", Component.translatable(settings.kind().translation())));
        tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_rate", rate));
        tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_cost",
                EmcAccelerationService.costPerSecondEmc(stack).toString()));
        var entries = AccelerationBindings.view(stack);
        long active = entries.stream().filter(AccelerationBindings::enabled).count();
        tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_targets", entries.size(), active));
        for (int i = 0; i < entries.size() && i < 5; i++) {
            var entry = entries.get(i);
            tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_target",
                    entry.getString(DIMENSION_KEY), entry.getInt(X_KEY), entry.getInt(Y_KEY), entry.getInt(Z_KEY),
                    AccelerationBindings.rate(entry), Component.translatable(AccelerationBindings.enabled(entry)
                            ? "tooltip.primordial.emc_accelerator_enabled" : "tooltip.primordial.emc_accelerator_paused")));
        }
        if (entries.size() > 5) tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_more_targets", entries.size() - 5));
        if (entries.stream().anyMatch(AccelerationBindings::pending)) {
            tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_warning"));
        }
        tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_bind_help"));
        tooltip.add(Component.translatable("tooltip.primordial.emc_accelerator_control_help"));
        tooltip.add(Component.translatable("tooltip.primordial.acceleration_limits"));
        tooltip.add(Component.translatable("tooltip.primordial.accelerator_management"));
    }

    private static boolean bindLookedEntity(ItemStack tool, ServerPlayer player, net.minecraft.world.InteractionHand hand) {
        double reach = Math.min(6, player.getEntityReach());
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(reach));
        var block = player.level().clip(new net.minecraft.world.level.ClipContext(start, end,
                net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        double distance = Math.min(reach * reach, start.distanceToSqr(block.getLocation()));
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, start, end,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1),
                entity -> entity != player && !entity.isRemoved() && !entity.isSpectator(), distance);
        if (hit == null) return false;
        AccelerationBindingActions.entity(tool, player, hit.getEntity(), hand);
        return true;
    }
}
