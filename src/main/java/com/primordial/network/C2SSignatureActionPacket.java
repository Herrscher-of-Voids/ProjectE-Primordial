/**
 * 【变更记录 #1】2026-10-14 02:37:36（第 1 次备注 · 1.2.0-singularity-signatures）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：主动能力请求不携带位置、价格或等级，服务端按实际手持/穿戴和已购等级执行；限制重复请求频率。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.network;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
public record C2SSignatureActionPacket() {
    public static void encode(C2SSignatureActionPacket packet,FriendlyByteBuf buffer) { }
    public static C2SSignatureActionPacket decode(FriendlyByteBuf buffer){return new C2SSignatureActionPacket();}
    public static void handle(C2SSignatureActionPacket packet,Supplier<NetworkEvent.Context> context){
        context.get().enqueueWork(()->{var player=context.get().getSender();if(player!=null)com.primordial.equipment.SignatureActions.activate(player);});context.get().setPacketHandled(true);
    }
}
