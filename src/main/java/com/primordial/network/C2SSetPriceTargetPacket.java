/**
 * 【文件职责】接收价格编辑页从 JEI 投放的物品、流体或气体目标，并在服务端确认注册名后更新菜单样本。
 *
 * 【变更记录 #1】2026-09-16（价格编辑页 JEI 目标）
 * - 删除：客户端仅显示幽灵样本、服务端仍为空导致后续价格请求被拒绝的路径。
 * - 新增：按介质类型和注册名传输目标的 C2S 包；物品、Forge 流体和 Mekanism 气体分别校验对应注册表。
 * - 修改：包只保存目标身份，不传输可被伪造的价格和大对象；真正的价格修改仍由 C2SSetMediaPricePacket 执行权限校验。
 * - 用途：让 JEI 无桶流体和气体能够在价格编辑页保存，并避免跨菜单或未知注册名写入公共价表。
 */
/**
 * 【变更记录 #3】2026-10-11 04:26:53（第 3 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：JEI/样本目标包在修改菜单前复核创造模式和管理员权限。
 * - 修改：越权请求返回权限状态，不改变当前样本。
 * - 用途：堵住生存管理员通过伪造目标包改变定价器选择的入口。
 */
package com.primordial.network;

import com.primordial.menu.PriceEditorMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SSetPriceTargetPacket(int containerId, byte kind, String id) {
    public static void encode(C2SSetPriceTargetPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.containerId);
        buffer.writeByte(message.kind);
        buffer.writeUtf(message.id == null ? "" : message.id, 256);
    }

    public static C2SSetPriceTargetPacket decode(FriendlyByteBuf buffer) {
        return new C2SSetPriceTargetPacket(buffer.readVarInt(), buffer.readByte(), buffer.readUtf(256));
    }

    public static void handle(C2SSetPriceTargetPacket message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof PriceEditorMenu menu)
                    || menu.containerId != message.containerId || message.id == null
                    || message.id.length() > 256) return;
            ResourceLocation id = ResourceLocation.tryParse(message.id);
            if (!com.primordial.service.PriceEditorAccess.canEdit(player)) {
                menu.sendPriceState(player, com.primordial.network.S2CPriceEditorStatePacket.Status.PERMISSION_DENIED);
                return;
            }
            if (id == null) return;
            switch (message.kind) {
                case PriceEditorMenu.KIND_ITEM -> {
                    if (!BuiltInRegistries.ITEM.containsKey(id)) return;
                    menu.setItemSelection(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                }
                case PriceEditorMenu.KIND_FLUID -> {
                    if (!BuiltInRegistries.FLUID.containsKey(id)) return;
                    menu.setFluidSelection(new net.minecraftforge.fluids.FluidStack(
                            BuiltInRegistries.FLUID.get(id), 1000));
                }
                case PriceEditorMenu.KIND_GAS -> {
                    if (!com.primordial.compat.MekanismGasCompat.validGas(id)) return;
                    menu.setGasSelection(id);
                }
                default -> { return; }
            }
            menu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}
