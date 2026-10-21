/**
 * 【文件职责】传递加速器管理动作及有界设置，客户端不能提交任意目标 NBT。
 * 【变更记录 #1】2026-09-18 01:47:24（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：打开、选中、保存、启停、移除、名单策略及范围标记动作，目标使用 UUID 而非列表下标。
 * - 修改：开窗与滚轮仅接受手持加速器；菜单动作要求容器身份及手持实例一致，限制每 tick 的请求数。
 * - 用途：服务端主线程处理几何与倍率校验，无菜单或旧窗口数据不能改写绑定。
 */
package com.primordial.network;

import com.primordial.item.EmcAcceleratorItem;
import com.primordial.menu.AccelerationMenu;
import com.primordial.service.AccelerationBindingActions;
import com.primordial.service.AccelerationSettings;
import com.primordial.service.EmcAccelerationService;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

public record C2SAccelerationActionPacket(int containerId, Action action, UUID target,
                                         AccelerationSettings settings, boolean flag, InteractionHand hand) {
    public enum Action {
        OPEN, CYCLE, SELECT, SAVE, START, PAUSE, REMOVE, START_ALL, PAUSE_ALL, CLEAR_ALL, WORLD,
        AREA_POLICY, IDLE_POLICY, AREA_CLEAR, IDLE_CLEAR, MARK_AREA, MARK_IDLE
    }
    private static final Map<ServerPlayer, int[]> REQUESTS = new WeakHashMap<>();

    public static C2SAccelerationActionPacket open(InteractionHand hand) {
        return new C2SAccelerationActionPacket(-1, Action.OPEN, AccelerationBindingActions.SELECTION,
                AccelerationSettings.read(new net.minecraft.nbt.CompoundTag()), false, hand);
    }

    public static void encode(C2SAccelerationActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId); buffer.writeEnum(packet.action); buffer.writeUUID(packet.target);
        var s = packet.settings;
        buffer.writeEnum(s.kind()); buffer.writeVarInt(s.rate());
        buffer.writeVarInt(s.sizeX()); buffer.writeVarInt(s.sizeY()); buffer.writeVarInt(s.sizeZ());
        buffer.writeVarInt(s.offsetX()); buffer.writeVarInt(s.offsetY()); buffer.writeVarInt(s.offsetZ());
        buffer.writeBoolean(s.idleThrottle()); buffer.writeBoolean(s.disableAi());
        buffer.writeBoolean(packet.flag); buffer.writeEnum(packet.hand);
    }

    public static C2SAccelerationActionPacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        Action action = buffer.readEnum(Action.class);
        UUID target = buffer.readUUID();
        var settings = new AccelerationSettings(buffer.readEnum(AccelerationSettings.Kind.class), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean());
        if (!settings.valid()) throw new io.netty.handler.codec.DecoderException("Invalid acceleration settings");
        return new C2SAccelerationActionPacket(container, action, target, settings, buffer.readBoolean(), buffer.readEnum(InteractionHand.class));
    }

    public static void handle(C2SAccelerationActionPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> apply(context.get().getSender(), packet));
        context.get().setPacketHandled(true);
    }

    static void apply(ServerPlayer player, C2SAccelerationActionPacket packet) {
        if (player == null || !player.isAlive() || player.isSpectator() || !packet.settings.valid()) return;
        int[] limit = REQUESTS.computeIfAbsent(player, ignored -> new int[]{player.server.getTickCount(), 0});
        if (limit[0] != player.server.getTickCount()) { limit[0] = player.server.getTickCount(); limit[1] = 0; }
        if (++limit[1] > 8) return;
        var tool = player.getItemInHand(packet.hand);
        if (!(tool.getItem() instanceof EmcAcceleratorItem)) return;
        if (packet.action == Action.OPEN || packet.action == Action.CYCLE) {
            if (player.containerMenu != player.inventoryMenu) return;
            if (packet.action == Action.OPEN) NetworkHooks.openScreen(player,
                    new SimpleMenuProvider((id, inv, owner) -> new AccelerationMenu(id, inv, packet.hand),
                            Component.translatable("screen.primordial.accelerator.title")), buffer -> buffer.writeEnum(packet.hand));
            else EmcAccelerationService.cycleMultiplier(tool, player, !packet.flag);
        } else if (player.containerMenu instanceof AccelerationMenu menu && menu.containerId == packet.containerId
                && menu.hand() == packet.hand && menu.stillValid(player)) menu.apply(packet);
    }
}
