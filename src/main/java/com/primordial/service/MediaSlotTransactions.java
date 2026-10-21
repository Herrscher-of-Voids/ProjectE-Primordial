/**
 * 【文件职责】为转化桌真实槽中的容器充 FE 或遗忘其介质知识。
 * 【变更记录 #1】2026-10-12 23:39:00（第 1 次备注 · 1.2.0-media-real-slots）
 * - 删除：无。新增：槽容器独立副本、实际 FE 结算和非破坏性遗忘。
 * - 修改：不借用光标物品，知识/阶段/价格检查复用公共服务。
 * - 用途：自动充电失败不修改原容器；遗忘不抽取内容、不扣 EMC。
 */
package com.primordial.service;

import com.primordial.PrimordialMod;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.FluidSampleConfig;
import com.primordial.energy.EnergyConversions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class MediaSlotTransactions {
    private MediaSlotTransactions() { }

    /** 服务端每 tick 对独立副本操作；只有写回同一个槽后，才提交真实接收量。 */
    public static void charge(ServerPlayer player, Slot slot) {
        if (player == null || player.server == null || !player.server.isSameThread()) return;
        ItemStack original = slot.getItem();
        if (original.isEmpty() || original.getCount() != 1
                || PrimordialServerConfig.resourceBlocked(EnergyConversions.FE)
                || !ResourceKnowledge.allows(player.server, player.getUUID(), ResourceKnowledge.Kind.ENERGY)) return;
        EmcAmount price = EmcService.energyPrice(EnergyConversions.FE);
        if (price.isZero()) return;
        try {
            ItemStack copy = original.copy();
            var storage = copy.getCapability(ForgeCapabilities.ENERGY).orElse(null);
            if (storage == null || !storage.canReceive()) return;
            int request = Math.max(0, storage.receiveEnergy(Integer.MAX_VALUE, true));
            if (request == 0) return;
            EmcService.purchase(player.server, player.getUUID(), price, request, units -> TransmutationMediaTrade.detachedMove(() -> {
                long actual = storage.receiveEnergy((int) units, false);
                if (actual <= 0 || actual > units || slot.getItem() != original) return 0;
                slot.set(copy);
                return actual;
            }));
        } catch (RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn("[Primordial] charging slot rejected FE transfer", failure);
        }
    }

    public static boolean forget(ServerPlayer player, ItemStack container, boolean gas) {
        if (container.isEmpty()) return false;
        try {
            net.minecraft.resources.ResourceLocation id = null;
            if (gas) {
                var sample = new FluidSampleConfig();
                if (MekanismGasCompat.sampleContainer(sample, container.copy()))
                    id = MekanismGasCompat.gasId(sample.gasFilter);
            } else {
                var handler = container.copy().getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (handler != null) for (int i = 0; i < handler.getTanks(); i++) {
                    var fluid = handler.getFluidInTank(i);
                    if (!fluid.isEmpty()) { id = BuiltInRegistries.FLUID.getKey(fluid.getFluid()); break; }
                }
            }
            return id != null && ResourceKnowledge.forgetMedia(player.server, player.getUUID(),
                    gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID, id);
        } catch (RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.warn("[Primordial] unlearn slot rejected container", failure);
            return false;
        }
    }
}
