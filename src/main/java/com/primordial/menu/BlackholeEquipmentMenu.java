/**
 * 【文件职责】黑洞装备两槽实体模块安装界面，按当前手持装备保存到其 NBT。
 * 【变更记录 #1】2026-10-14 00:51:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增服务器绑定手持实例的两槽安装、取回及背包快捷移动。
 * - 修改：模块资格、重复与冲突均在服务端 Slot.mayPlace 复核；当前手持装备不可移动。
 * - 用途：模块作为实物放入装备，切换物品或关闭菜单不复制、不丢失已安装模块。
 */
/**
 * 【变更记录 #2】2026-10-14 01:17:24（第 2 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：实体模块安装流程。
 * - 新增：装备内 EMC 解锁、开关与服务端验证。
 * - 修改：模块槽和背包搬运改成服务端菜单按钮；价格来自服务端枚举，已购/开关/反馈由 DataSlot 同步。
 * - 用途：落实用户修正，功能属于装备且不再作为物品流通。
 */
/**
 * 【变更记录 #3】2026-10-14 01:41:14（第 3 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：只读装备槽同步完整等级 NBT；自定义请求校验窗口、手持实例、主线程、数量和预期已购等级。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
package com.primordial.menu;
import com.primordial.equipment.*;
import com.primordial.equipment.BlackholeModules.Kind;
import com.primordial.equipment.BlackholeModules.Result;
import com.primordial.registry.ModMenus;
import java.math.BigInteger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

@org.anti_ad.mc.ipn.api.IPNIgnore
public final class BlackholeEquipmentMenu extends AbstractContainerMenu {
    private final Player owner; private final InteractionHand hand; private final ItemStack bound;
    private final DataSlot feedback=DataSlot.standalone();
    public BlackholeEquipmentMenu(int id,Inventory inventory,InteractionHand hand) {
        super(ModMenus.BLACKHOLE_EQUIPMENT.get(),id);
        owner=inventory.player;this.hand=hand;bound=owner.getItemInHand(hand);
        addSlot(new Slot(inventory,hand==InteractionHand.MAIN_HAND?inventory.selected:40,12,25){
            @Override public boolean mayPlace(ItemStack s){return false;}
            @Override public boolean mayPickup(Player p){return false;}
        });
        addDataSlot(feedback);
    }
    public boolean action(ServerPlayer player,String id,boolean buy,String amount,String expected) {
        if(!player.server.isSameThread()||player.containerMenu!=this||!stillValid(player))return false;
        Kind kind=Kind.byId(id);BigInteger value=BlackholeModules.parse(amount), prior=BlackholeModules.parse(expected);
        Result result=buy?BlackholeModules.buy(player,bound,kind,value,prior):BlackholeModules.setLevel(bound,kind,value);
        feedback.set(result.ordinal());owner.getInventory().setChanged();broadcastChanges();return result==Result.OK;
    }
    @Override public boolean stillValid(Player player){return player==owner&&player.isAlive()&&player.getItemInHand(hand)==bound&&BlackholeGear.isGear(bound);}
    @Override public void clicked(int index,int button,ClickType click,Player player) { }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public ItemStack gear(){return slots.get(0).getItem();}
    public Result feedback(){return Result.values()[Math.max(0,Math.min(Result.values().length-1,feedback.get()))];}
}
