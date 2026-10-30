package com.primordial.neoforge.generator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class NeoForgeEntityGeneratorBlock extends Block {
    public NeoForgeEntityGeneratorBlock(Properties properties) { super(properties); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer server) {
            var offers = NeoForgeEntityGeneratorService.catalog(server.serverLevel());
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new NeoForgeEntityGeneratorMenu(id, inventory, pos, offers),
                    Component.translatable("block.primordial.entity_generator")), buf -> {
                buf.writeBlockPos(pos);
                NeoForgeEntityGeneratorMenu.writeOffers(buf, offers);
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
