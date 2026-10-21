/**
 * 【文件职责】处理玩家请求打开当前手持黑洞装备的模块菜单。
 * 【变更记录 #1】2026-10-14 00:55:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增有向空请求包、服务端装备与菜单状态校验。
 * - 修改：不接收客户端物品 NBT、模块或价格；打开数据只写当前手。
 * - 用途：避免占用 ProjectE 红物质工具原有的右键模式操作。
 */
package com.primordial.network;

import com.primordial.equipment.BlackholeGear;
import com.primordial.menu.BlackholeEquipmentMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

public record C2SOpenBlackholeEquipmentPacket(InteractionHand hand) {
    public static void encode(C2SOpenBlackholeEquipmentPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.hand);
    }
    public static C2SOpenBlackholeEquipmentPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenBlackholeEquipmentPacket(buffer.readEnum(InteractionHand.class));
    }
    public static void handle(C2SOpenBlackholeEquipmentPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> open(context.get().getSender(), packet.hand));
        context.get().setPacketHandled(true);
    }
    static void open(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null || !player.isAlive() || player.isSpectator()
                || player.containerMenu != player.inventoryMenu || !BlackholeGear.isGear(player.getItemInHand(hand))) return;
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inventory, owner) -> new BlackholeEquipmentMenu(id, inventory, hand),
                        Component.translatable("screen.primordial.equipment.title")),
                buffer -> buffer.writeEnum(hand));
    }
}
