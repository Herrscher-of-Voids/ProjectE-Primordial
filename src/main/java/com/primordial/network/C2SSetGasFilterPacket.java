/**
 * 【文件职责】接收 JEI 投放的 Mekanism GasStack 化学品过滤器。
 * 【变更记录 #1】2026-09-14 13:15:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：GasStack registry id 与数量的网络序列化，写入流体页对应样本。
 * - 修改：服务器校验菜单所有者与 section/slot，避免把气体伪装成 Forge Fluid。
 * - 用途：反物质等没有桶形态的 Mekanism 气体可以从 JEI 直接配置。
 */
/**
 * 【变更记录 #2】2026-09-15 12:04:51（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：保存任意气体样本数量且不更新服务端幽灵槽的行为。
 * - 新增：未定价气体的明确提示，说明 EMC/mB 与管理员命令。
 * - 修改：气体样本规范为 1 mB，回填显示槽后同步配置；不因无价格拒绝保存样本。
 * - 用途：区分配置成功和可执行转换，反物质等气体不再静默失败。
 */
/**
 * 【变更记录 #3】2026-09-15 15:22:01（第 3 次备注 · 1.0.0-emc-service）
 * - 删除：通过可选 Gas 参数重载直接查询 ValueManager。
 * - 新增：统一 EmcService 的注册名报价调用。
 * - 修改：提示与机器/黑洞查价完全相同，仍允许保存未定价样本。
 * - 用途：核心价格服务不链接 Mekanism 类，未安装可选模组仍可运行物品、流体和 FE。
 */
package com.primordial.network;

import mekanism.api.chemical.gas.GasStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public record C2SSetGasFilterPacket(int containerId, int section, int slot, GasStack gas) {
    public static void encode(C2SSetGasFilterPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId); buf.writeByte(msg.section); buf.writeByte(msg.slot); msg.gas.writeToPacket(buf);
    }
    public static C2SSetGasFilterPacket decode(FriendlyByteBuf buf) {
        return new C2SSetGasFilterPacket(buf.readVarInt(), buf.readByte(), buf.readByte(), GasStack.readFromPacket(buf));
    }
    public static void handle(C2SSetGasFilterPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get(); context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof com.primordial.menu.ConfiguratorMenu menu)
                    || menu.containerId != msg.containerId || !menu.canEdit(player)
                    || msg.section < 2 || msg.section > 3 || msg.slot < 0 || msg.slot >= 9
                    || msg.gas == null || msg.gas.isEmpty()) return;
            var config = msg.section == 2 ? menu.config.fluidIn[msg.slot] : menu.config.fluidOut[msg.slot];
            config.filter = net.minecraftforge.fluids.FluidStack.EMPTY;
            config.gasFilter = new GasStack(msg.gas, 1); config.enabled = true;
            menu.refillFromConfig();
            menu.broadcastChanges();
            if (com.primordial.service.EmcService.gasPrice(msg.gas.getType().getRegistryName()).isZero()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.primordial.gas_unpriced", msg.gas.getType().getRegistryName()), false);
            }
            com.primordial.data.MachineConfigSavedData.get(player.server).setDirty();
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CFullConfigSyncPacket(menu.machineKey, menu.config.save()));
        }); context.setPacketHandled(true);
    }
}
