package com.primordial.neoforge.equipment;

import com.primordial.neoforge.NeoForgeModRegistries;
import java.util.ArrayList;
import java.util.List;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.gameObjs.block_entities.CollectorMK1BlockEntity;
import moze_intel.projecte.gameObjs.container.CollectorMK1Container;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Original adjacent collector chain, isolated from ordinary ProjectE fuels. */
public final class NeoForgeBlackholeCollectorConversion {
    public static final List<String> TIERS = List.of("primordial", "stellar", "intermediate", "supermassive", "ultramassive",
            "schwarzschild", "reissner_nordstrom", "kerr", "kerr_newman");
    private NeoForgeBlackholeCollectorConversion() {}

    public static ItemStack material(String tier) {
        return new ItemStack(NeoForgeModRegistries.BLACKHOLE_MATERIALS.get(tier).get());
    }
    public static int tier(Item item) {
        for (int i = 0; i < TIERS.size(); i++)
            if (NeoForgeModRegistries.BLACKHOLE_MATERIALS.get(TIERS.get(i)).get() == item) return i;
        return -1;
    }
    public static boolean isMaterial(ItemStack stack) { return !stack.isEmpty() && tier(stack.getItem()) >= 0; }
    public static ItemStack next(ItemStack stack) {
        int i = stack.isEmpty() ? -1 : tier(stack.getItem());
        return i >= 0 && i + 1 < TIERS.size() ? material(TIERS.get(i + 1)) : ItemStack.EMPTY;
    }
    public static HolderSet<Item> ordered(HolderSet<Item> input) {
        var fuels = new ArrayList<Holder<Item>>();
        input.stream().filter(holder -> tier(holder.value()) < 0).forEach(fuels::add);
        TIERS.forEach(tier -> fuels.add(material(tier).getItemHolder()));
        return HolderSet.direct(List.copyOf(fuels));
    }
    public static boolean lastOrdinary(Item item) {
        var fuels = FuelMapper.getFuelMap();
        for (int i = 0; i + 1 < fuels.size(); i++)
            if (fuels.get(i).value() == item) return tier(fuels.get(i + 1).value()) >= 0;
        return false;
    }
    public static boolean selectNext(CollectorMK1Container menu, ServerPlayer player) {
        if (!player.server.isSameThread() || player.containerMenu != menu || !menu.stillValid(player)
                || menu.collector.isRemoved() || menu.collector.getLevel() != player.level()
                || player.level().getBlockEntity(menu.collector.getBlockPos()) != menu.collector
                || !isMaterial(menu.collector.getAux().getStackInSlot(CollectorMK1BlockEntity.UPGRADING_SLOT))) return false;
        int lockSlot = menu.collector.getInput().getSlots() + 2;
        menu.getSlot(lockSlot).set(next(menu.collector.getAux().getStackInSlot(CollectorMK1BlockEntity.UPGRADING_SLOT)));
        menu.broadcastChanges();
        return true;
    }
}
