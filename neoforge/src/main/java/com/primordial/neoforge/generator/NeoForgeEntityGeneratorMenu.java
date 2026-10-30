package com.primordial.neoforge.generator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import com.primordial.neoforge.NeoForgeModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public final class NeoForgeEntityGeneratorMenu extends AbstractContainerMenu {
    private final BlockPos position;
    private final Player owner;
    private final List<NeoForgeEntityGeneratorService.Offer> offers;
    private final DataSlot feedback = DataSlot.standalone();
    public NeoForgeEntityGeneratorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), readOffers(buf));
    }
    public NeoForgeEntityGeneratorMenu(int id, Inventory inventory, BlockPos pos, List<NeoForgeEntityGeneratorService.Offer> offers) {
        super(NeoForgeModRegistries.ENTITY_GENERATOR_MENU.get(), id);
        owner = inventory.player; position = pos.immutable(); this.offers = List.copyOf(offers); addDataSlot(feedback);
    }
    public static void writeOffers(RegistryFriendlyByteBuf buf, List<NeoForgeEntityGeneratorService.Offer> offers) {
        if (offers.size() > NeoForgeEntityGeneratorService.MAX_OFFERS) throw new IllegalArgumentException("Entity catalogue too large");
        buf.writeVarInt(offers.size());
        for (var offer : offers) { buf.writeResourceLocation(BuiltInRegistries.ENTITY_TYPE.getKey(offer.type())); buf.writeUtf(offer.price().toString(), 128); }
    }
    private static List<NeoForgeEntityGeneratorService.Offer> readOffers(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > NeoForgeEntityGeneratorService.MAX_OFFERS) throw new IllegalArgumentException("Entity catalogue too large");
        List<NeoForgeEntityGeneratorService.Offer> offers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var id = buf.readResourceLocation();
            BigInteger price = new BigInteger(buf.readUtf(128));
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id) || price.signum() <= 0) throw new IllegalArgumentException("Invalid entity offer");
            offers.add(new NeoForgeEntityGeneratorService.Offer(BuiltInRegistries.ENTITY_TYPE.get(id), price));
        }
        return offers;
    }
    public List<NeoForgeEntityGeneratorService.Offer> offers() { return offers; }
    public int feedback() { return feedback.get(); }
    @Override public boolean clickMenuButton(Player player, int index) {
        if (!(player instanceof ServerPlayer server) || player != owner || player.containerMenu != this
                || !stillValid(player) || index < 0 || index >= offers.size()) return false;
        boolean result = NeoForgeEntityGeneratorService.spawn(server, position, offers.get(index));
        feedback.set(result ? 1 : 2); broadcastChanges(); return result;
    }
    @Override public boolean stillValid(Player player) {
        if (player != owner || !player.isAlive() || player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) > 64) return false;
        return player instanceof ServerPlayer server ? NeoForgeEntityGeneratorService.live(server, position)
                : player.level().getBlockState(position).is(NeoForgeModRegistries.ENTITY_GENERATOR.get());
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
