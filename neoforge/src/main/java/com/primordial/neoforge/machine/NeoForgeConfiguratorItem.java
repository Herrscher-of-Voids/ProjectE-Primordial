package com.primordial.neoforge.machine;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.util.MachineKey;

import java.util.OptionalInt;
import net.minecraft.nbt.CompoundTag;

/** Air right-click opens the same player-inventory configuration flow as 1.20.1. */
public final class NeoForgeConfiguratorItem extends Item {
    public NeoForgeConfiguratorItem(Properties properties) { super(properties); }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer owner && !player.isShiftKeyDown())
            owner.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> new NeoForgePlayerInventoryMenu(id, inventory),
                    Component.translatable("screen.primordial.player_inventory")));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null) return InteractionResult.PASS;
        if (level.getBlockState(pos).is(NeoForgeModRegistries.BLACKHOLE.get())) {
            if (!level.isClientSide) player.displayClientMessage(
                    Component.translatable("message.primordial.blackhole_no_config"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.PASS;

        BlockEntity machine = serverLevel.getBlockEntity(pos);
        if (machine == null || !NeoForgeMachineConfiguratorAccess.hasAnyCapability(machine)) {
            player.displayClientMessage(Component.translatable("message.primordial.no_machine"), true);
            return InteractionResult.PASS;
        }
        String machineKey = MachineKey.of(serverLevel.dimension(), pos);
        if (serverPlayer.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)
                >= NeoForgeMachineConfiguratorAccess.MAX_DISTANCE_SQR
                || NeoForgeMachineConfiguratorAccess.findLoaded(serverLevel.getServer(), machineKey) != machine) {
            player.displayClientMessage(Component.translatable("message.primordial.no_machine"), true);
            return InteractionResult.PASS;
        }

        NeoForgeMachineConfigSavedData data = NeoForgeMachineConfigSavedData.get(serverLevel.getServer());
        CompoundTag config = data.getOrCreate(machineKey, player.getUUID());
        if (config == null || !data.canEdit(machineKey, player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.primordial.not_owner"), true);
            return InteractionResult.FAIL;
        }
        if (!NeoForgeMachineConfiguratorAccess.canOpen(serverPlayer, machineKey, data))
            return InteractionResult.FAIL;

        OptionalInt opened = serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inventory, ignored) -> new NeoForgeMachineConfiguratorMenu(id, inventory, machineKey),
                Component.translatable("item.primordial.configurator")),
                buffer -> buffer.writeUtf(machineKey, 256));
        if (opened.isPresent() && serverPlayer.containerMenu instanceof NeoForgeMachineConfiguratorMenu menu
                && menu.containerId == opened.getAsInt()) {
            NeoForgeMachineConfiguratorPayloads.sendSnapshot(serverPlayer, menu);
        }
        return InteractionResult.sidedSuccess(false);
    }

}
