package com.primordial.neoforge.equipment;
import com.primordial.neoforge.equipment.*;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Result;
import com.primordial.neoforge.NeoForgeModRegistries;
import java.math.BigInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class NeoForgeBlackholeEquipmentMenu extends AbstractContainerMenu {
    private final Player owner; private final InteractionHand hand; private final ItemStack bound;
    private final DataSlot feedback=DataSlot.standalone();
    public NeoForgeBlackholeEquipmentMenu(int id,Inventory inventory,InteractionHand hand) {
        super(NeoForgeModRegistries.BLACKHOLE_EQUIPMENT_MENU.get(),id);
        owner=inventory.player;this.hand=hand;bound=owner.getItemInHand(hand);
        addSlot(new Slot(inventory,hand==InteractionHand.MAIN_HAND?inventory.selected:40,12,25){
            @Override public boolean mayPlace(ItemStack s){return false;}
            @Override public boolean mayPickup(Player p){return false;}
        });
        addDataSlot(feedback);
    }
    public boolean action(ServerPlayer player,String id,boolean buy,String amount,String expected) {
        if(!player.server.isSameThread()||player.containerMenu!=this||!stillValid(player))return false;
        Kind kind=Kind.byId(id);BigInteger value=NeoForgeBlackholeModules.parse(amount), prior=NeoForgeBlackholeModules.parse(expected);
        Result result=buy?NeoForgeBlackholeModules.buy(player,bound,kind,value,prior):NeoForgeBlackholeModules.setLevel(bound,kind,value);
        feedback.set(result.ordinal());owner.getInventory().setChanged();broadcastChanges();return result==Result.OK;
    }
    @Override public boolean stillValid(Player player){return player==owner&&player.isAlive()&&player.getItemInHand(hand)==bound&&NeoForgeBlackholeGear.isGear(bound);}
    @Override public void clicked(int index,int button,ClickType click,Player player) { }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public ItemStack gear(){return slots.get(0).getItem();}
    public Result feedback(){return Result.values()[Math.max(0,Math.min(Result.values().length-1,feedback.get()))];}
}
