package com.primordial.neoforge.acceleration;

import com.primordial.neoforge.acceleration.EmcAccelerationService;
import com.primordial.neoforge.acceleration.AccelerationBindings;
import com.primordial.neoforge.acceleration.AccelerationBindingActions;
import com.primordial.neoforge.acceleration.AccelerationSettings;
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
        try (var edit = AccelerationData.edit(stack)) {
        if (context.getPlayer() == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        var kind = AccelerationSettings.read(AccelerationData.get(stack)).kind();
        if (player.isShiftKeyDown() && !kind.filter()) {
            EmcAccelerationService.cycleMultiplier(context.getItemInHand(), player);
        } else {
            EmcAccelerationService.bind(context.getItemInHand(), player, player.serverLevel(), context.getClickedPos(), context.getHand());
        }
        return InteractionResult.CONSUME;

        }
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        try (var edit = AccelerationData.edit(player.getItemInHand(hand))) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            var tool = player.getItemInHand(hand);
            var kind = AccelerationSettings.read(AccelerationData.get(tool)).kind();
            if (serverPlayer.isShiftKeyDown()) EmcAccelerationService.clear(player.getItemInHand(hand), serverPlayer);
            else if (kind.world()) AccelerationBindingActions.world(tool, serverPlayer, hand);
            else if (kind == AccelerationSettings.Kind.ENTITY && bindLookedEntity(tool, serverPlayer, hand)) { }
            else EmcAccelerationService.toggle(player.getItemInHand(hand), serverPlayer, hand);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());

        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int rate = EmcAccelerationService.multiplier(stack);
        var settings = AccelerationSettings.read(AccelerationData.has(stack) ? AccelerationData.get(stack) : new net.minecraft.nbt.CompoundTag());
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
        double reach = Math.min(6, player.entityInteractionRange());
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
