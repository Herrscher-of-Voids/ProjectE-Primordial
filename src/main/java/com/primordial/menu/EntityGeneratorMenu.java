/**
 * 【文件职责】服务端绑定方块与报价，客户端只提交目录索引；兑换前重新校验位置、价格和余额。
 * 【变更记录 #1】2026-10-14 15:59:10（第 1 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：无。新增：报价同步、单实体购买和结果反馈。
 * - 修改：不接受类型 NBT 或余额输入，价格变化时拒绝旧报价。
 * - 用途：实体生成器事务边界；每个请求只生成一个实体。
 */
package com.primordial.menu;
import com.primordial.registry.*;
import com.primordial.service.EntityEmc;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
@org.anti_ad.mc.ipn.api.IPNIgnore
public final class EntityGeneratorMenu extends AbstractContainerMenu {
    private final BlockPos position;
    private final Player owner;
    private final List<EntityEmc.Offer> offers;
    private final DataSlot feedback=DataSlot.standalone();
    public EntityGeneratorMenu(int id,Inventory inventory,FriendlyByteBuf buf){this(id,inventory,buf.readBlockPos(),readOffers(buf));}
    public EntityGeneratorMenu(int id,Inventory inventory,BlockPos pos,List<EntityEmc.Offer> offers){
        super(ModMenus.ENTITY_GENERATOR.get(),id);owner=inventory.player;position=pos.immutable();this.offers=List.copyOf(offers);addDataSlot(feedback);
    }
    private static List<EntityEmc.Offer> readOffers(FriendlyByteBuf buf){
        int count=buf.readVarInt();if(count<0||count>4096)throw new IllegalArgumentException("Entity catalog too large");
        List<EntityEmc.Offer> offers=new ArrayList<>();
        for(int i=0;i<count;i++)offers.add(new EntityEmc.Offer(BuiltInRegistries.ENTITY_TYPE.get(buf.readResourceLocation()),new BigInteger(buf.readUtf(4096))));
        return offers;
    }
    public List<EntityEmc.Offer> offers(){return offers;}
    public int feedback(){return feedback.get();}
    @Override public boolean clickMenuButton(Player player,int index){
        if(!(player instanceof ServerPlayer server)||player.containerMenu!=this||!stillValid(player)||index<0||index>=offers.size())return false;
        boolean success=EntityEmc.spawn(server,position,offers.get(index));feedback.set(success?1:2);broadcastChanges();return success;
    }
    @Override public boolean stillValid(Player player){return player==owner&&player.isAlive()&&player.distanceToSqr(position.getX()+0.5,position.getY()+0.5,position.getZ()+0.5)<=64
            &&player.level().getBlockState(position).is(ModBlocks.ENTITY_GENERATOR.get());}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
