/** 【文件职责】在服务端校验 JEI 物品兑换，并按背包真实接收数量购买一组。
 * 【变更记录 #1】2026-10-10 00:46:20（第 1 次备注 · 1.2.0-jei-purchase-hit）
 * - 删除：固定购买 64 个导致非 64 堆叠物品超量购买的路径。
 * - 新增：以输出物品的堆叠上限作为单次请求量，库存变化后标记保存。
 * - 修改：仅在库存实际接收后同步菜单；费用继续由 EmcService 按真实交付量结算。
 * - 用途：Shift 左键每次最多兑换一组，背包不足时仅扣实际入包数量的 EMC。 */
package com.primordial.network;
import com.primordial.service.EmcService;
import com.primordial.service.TransmutationNbtPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record C2SJeiEmcPurchasePacket(ItemStack sample) {
    public static void encode(C2SJeiEmcPurchasePacket m, FriendlyByteBuf b) { b.writeItem(m.sample); }
    public static C2SJeiEmcPurchasePacket decode(FriendlyByteBuf b) { return new C2SJeiEmcPurchasePacket(b.readItem()); }
    public static void handle(C2SJeiEmcPurchasePacket m, Supplier<NetworkEvent.Context> s) {
        var c=s.get(); c.enqueueWork(() -> { ServerPlayer p=c.getSender(); ItemStack sample=m.sample==null?ItemStack.EMPTY:m.sample.copyWithCount(1);
            if(p==null||sample.isEmpty()||!TransmutationNbtPolicy.canGenerate(sample)||!EmcService.hasKnowledge(p.server,p.getUUID(),sample)) return;
            ItemStack out=TransmutationNbtPolicy.safeOutput(sample);
            long inserted=EmcService.purchaseItem(p.server,p.getUUID(),out,out.getMaxStackSize(),n->insert(p,out.copyWithCount((int)n)));
            if(inserted>0) { p.getInventory().setChanged(); p.containerMenu.broadcastChanges(); }
        }); c.setPacketHandled(true);
    }
    private static long insert(ServerPlayer p, ItemStack s) { int before=s.getCount(); for(int i=0;i<36&&!s.isEmpty();i++){ ItemStack slot=p.getInventory().getItem(i); if(slot.isEmpty()){int n=Math.min(s.getCount(),s.getMaxStackSize());p.getInventory().setItem(i,s.copyWithCount(n));s.shrink(n);} else if(ItemStack.isSameItemSameTags(slot,s)&&slot.getCount()<slot.getMaxStackSize()){int n=Math.min(s.getCount(),slot.getMaxStackSize()-slot.getCount());slot.grow(n);s.shrink(n);}} return before-s.getCount(); }
}
