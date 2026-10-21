/**
 * 【文件职责】接收 JEI 或其它客户端 ingredient 面板直接投放的 FluidStack 过滤器。
 * 【变更记录 #1】2026-09-14 12:20:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：按菜单 ID、输入/输出 section 和槽位写入完整 FluidStack（包含 NBT）。
 * - 修改：服务器重新验证菜单所有者和槽位范围，避免客户端借投放包修改其它机器。
 * - 用途：支持没有桶形态的流体由 JEI 直接拖入配置器幽灵槽。
 */
/**
 * 【变更记录 #2】2026-09-15 12:04:51（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：只修改 MachineConfig、服务端显示槽仍保留旧样本的不一致状态。
 * - 新增：保存过滤器后调用菜单权威回填与 broadcastChanges。
 * - 修改：先统一服务端槽内容，再发送完整配置；保留流体 NBT 和所有者校验。
 * - 用途：让无桶流体拖放后关闭重开仍保留样本，并避免原版槽更新覆盖显示。
 */
package com.primordial.network;

import com.primordial.data.FluidSampleConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public record C2SSetFluidFilterPacket(int containerId, int section, int slot, FluidStack fluid) {
    public static void encode(C2SSetFluidFilterPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.section); buf.writeByte(msg.slot);
        msg.fluid.writeToPacket(buf);
    }

    public static C2SSetFluidFilterPacket decode(FriendlyByteBuf buf) {
        return new C2SSetFluidFilterPacket(buf.readVarInt(), buf.readByte(), buf.readByte(), FluidStack.readFromPacket(buf));
    }

    public static void handle(C2SSetFluidFilterPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof com.primordial.menu.ConfiguratorMenu menu)
                    || menu.containerId != msg.containerId || !menu.canEdit(player)
                    || msg.section < 2 || msg.section > 3 || msg.slot < 0 || msg.slot >= 9
                    || msg.fluid == null || msg.fluid.isEmpty()) return;
            FluidSampleConfig config = msg.section == 2 ? menu.config.fluidIn[msg.slot] : menu.config.fluidOut[msg.slot];
            config.filter = msg.fluid.copy(); config.gasFilter = null;
            config.enabled = true;
            menu.refillFromConfig();
            menu.broadcastChanges();
            com.primordial.data.MachineConfigSavedData.get(player.server).setDirty();
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CFullConfigSyncPacket(menu.machineKey, menu.config.save()));
        });
        context.setPacketHandled(true);
    }
}
