/**
 * 【变更记录 #1】2026-10-14 01:41:14（第 1 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：大整数批量购买/设置等级网络请求；不接收价格或客户端装备 NBT，重复购买按预期等级拒绝。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.network;
import com.primordial.menu.BlackholeEquipmentMenu;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
public record C2SEquipmentFeaturePacket(int window,String kind,boolean buy,String amount,String expected) {
    public static void encode(C2SEquipmentFeaturePacket p,FriendlyByteBuf b){b.writeVarInt(p.window);b.writeUtf(p.kind,64);b.writeBoolean(p.buy);b.writeUtf(p.amount,2048);b.writeUtf(p.expected,2048);}
    public static C2SEquipmentFeaturePacket decode(FriendlyByteBuf b){return new C2SEquipmentFeaturePacket(b.readVarInt(),b.readUtf(64),b.readBoolean(),b.readUtf(2048),b.readUtf(2048));}
    public static void handle(C2SEquipmentFeaturePacket p,Supplier<NetworkEvent.Context> context){
        context.get().enqueueWork(()->{var player=context.get().getSender();
            if(player!=null&&player.containerMenu instanceof BlackholeEquipmentMenu menu&&menu.containerId==p.window)
                menu.action(player,p.kind,p.buy,p.amount,p.expected);
        });context.get().setPacketHandled(true);
    }
}
