/**
 * 【文件职责】有界查询 JEI 当前可见介质的服务端购买资格与单价。
 * 【变更记录 #1】2026-10-09 01:36:12（第 1 次备注 · 1.2.0-jei-media-knowledge）
 * - 删除：无。新增：最多 128 项、每玩家 10 tick 的只读查询。
 * - 修改：知识、阶段、遗忘、购买开关和价格方向均在服务端检查。
 * - 用途：未授权返回零价；不改变余额，不依赖客户端价表或可选气体 API 类型。
 */
package com.primordial.network;

import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.service.ResourceKnowledge;
import com.primordial.value.ValueManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public record C2SJeiMediaQuotesPacket(int request, List<Key> keys) {
    public static final int LIMIT = 128;
    private static final Map<ServerPlayer, Integer> LAST_QUERY = new WeakHashMap<>();
    public record Key(boolean gas, ResourceLocation id) {
        public void write(FriendlyByteBuf buf) { buf.writeBoolean(gas); buf.writeUtf(id.toString(), 256); }
        public static Key read(FriendlyByteBuf buf) {
            return new Key(buf.readBoolean(), new ResourceLocation(buf.readUtf(256)));
        }
    }
    public static void encode(C2SJeiMediaQuotesPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.request); buf.writeVarInt(msg.keys.size());
        msg.keys.forEach(key -> key.write(buf));
    }
    public static int readCount(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > LIMIT) throw new io.netty.handler.codec.DecoderException("Invalid JEI media count");
        return count;
    }
    public static C2SJeiMediaQuotesPacket decode(FriendlyByteBuf buf) {
        int request = buf.readVarInt(), count = readCount(buf);
        List<Key> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) keys.add(Key.read(buf));
        return new C2SJeiMediaQuotesPacket(request, List.copyOf(keys));
    }
    public static EmcAmount quote(ServerPlayer player, Key key) {
        if (PrimordialServerConfig.mediaPurchaseDisabled(key.gas)
                || !ResourceKnowledge.allowsMedia(player.server, player.getUUID(),
                key.gas ? ResourceKnowledge.Kind.GAS : ResourceKnowledge.Kind.FLUID, key.id)) return EmcAmount.ZERO;
        if (key.gas) return MekanismGasCompat.validGas(key.id) && ValueManager.canBuyGas(key.id)
                ? EmcService.gasPrice(key.id) : EmcAmount.ZERO;
        if (!BuiltInRegistries.FLUID.containsKey(key.id)) return EmcAmount.ZERO;
        var fluid = BuiltInRegistries.FLUID.get(key.id);
        return fluid.defaultFluidState().isSource() && ValueManager.canBuyFluid(fluid)
                ? EmcService.fluidPrice(fluid) : EmcAmount.ZERO;
    }
    public static void handle(C2SJeiMediaQuotesPacket msg, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || msg.keys.size() > LIMIT) return;
            Integer previous = LAST_QUERY.get(player);
            if (previous != null && player.tickCount - previous < 10) return;
            LAST_QUERY.put(player, player.tickCount);
            var entries = msg.keys.stream().map(key -> new S2CJeiMediaQuotesPacket.Entry(key, quote(player, key).toString())).toList();
            PrimordialNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CJeiMediaQuotesPacket(msg.request, entries));
        });
        context.setPacketHandled(true);
    }
}
