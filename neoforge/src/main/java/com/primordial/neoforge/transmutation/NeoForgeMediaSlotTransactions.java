/** Detached-copy FE charging and non-destructive container-based forgetting for real media slots. */
package com.primordial.neoforge.transmutation;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.trade.NeoForgeResourceTrades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class NeoForgeMediaSlotTransactions {
    private NeoForgeMediaSlotTransactions() {}

    public static long charge(ServerPlayer player, Slot slot) {
        if (player == null || slot == null || !player.server.isSameThread() || !slot.isActive()) return 0;
        var menu = player.containerMenu;
        if (!(menu instanceof NeoForgeMediaChargingMenu media) || !media.primordial$mediaPage()
                || !menu.slots.contains(slot) || !NeoForgeTransmutationMediaTrade.currentMenu(player, menu.containerId)) return 0;
        ItemStack original = slot.getItem();
        if (original.isEmpty() || original.getCount() != 1) return 0;
        ItemStack snapshot = original.copy();
        try {
            var probe = original.copy().getCapability(Capabilities.EnergyStorage.ITEM);
            if (probe == null || !probe.canReceive()) return 0;
            int requested = probe.receiveEnergy(Integer.MAX_VALUE, true);
            if (requested <= 0) return 0;
            ItemStack output = original.copy();
            var storage = output.getCapability(Capabilities.EnergyStorage.ITEM);
            if (storage == null || !storage.canReceive()) return 0;
            return NeoForgeResourceTrades.purchaseEnergy(player.server, player.getUUID(), requested, units -> {
                if (!NeoForgeTransmutationMediaTrade.currentMenu(player, menu.containerId)) return 0;
                long actual;
                try { actual = storage.receiveEnergy((int) units, false); }
                catch (RuntimeException | LinkageError unavailable) { return 0; }
                if (actual <= 0 || actual > units || output.isEmpty() || output.getCount() != 1
                        || player.containerMenu != menu || !media.primordial$mediaPage()
                        || !NeoForgeTransmutationMediaTrade.currentMenu(player, menu.containerId)
                        || slot.getItem() != original || !ItemStack.matches(snapshot, original)) return 0;
                slot.set(output);
                return actual;
            });
        } catch (RuntimeException | LinkageError unavailable) {
            LogUtils.getLogger().warn("[Primordial] Media slot charging rejected an incompatible FE capability", unavailable);
            return 0;
        }
    }

    public static boolean forget(ServerPlayer player, ItemStack container, boolean chemical) {
        if (player == null || container == null || container.isEmpty() || !player.server.isSameThread()) return false;
        try {
            ResourceLocation id = chemical ? NeoForgePriceEditorChemicalBridge.sampleContainer(container.copy()) : null;
            if (!chemical) {
                var handler = container.copy().getCapability(Capabilities.FluidHandler.ITEM);
                if (handler != null) for (int tank = 0; tank < handler.getTanks(); tank++) {
                    var fluid = handler.getFluidInTank(tank);
                    if (!fluid.isEmpty()) { id = BuiltInRegistries.FLUID.getKey(fluid.getFluid()); break; }
                }
            }
            return id != null && NeoForgeResourceKnowledge.forgetMedia(player.server, player.getUUID(),
                    chemical ? Kind.GAS : Kind.FLUID, id);
        } catch (RuntimeException | LinkageError unavailable) {
            LogUtils.getLogger().warn("[Primordial] Media forget slot rejected an incompatible container", unavailable);
            return false;
        }
    }
}
