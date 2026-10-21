/**
 * 【文件职责】展示黑洞装备两枚实体模块与玩家背包的安装界面。
 * 【变更记录 #1】2026-10-14 00:51:00（第 1 次备注 · 1.2.0-blackhole-equipment-system）
 * - 删除：无；新增紧凑双槽面板、装备图标和原版物品提示。
 * - 修改：槽位和背包布局与服务端菜单一致，视觉只读，不决定安装资格。
 * - 用途：玩家手持装备按 H 打开界面后直接安装或取回模块。
 */
/**
 * 【变更记录 #2】2026-10-14 01:17:24（第 2 次备注 · 1.2.0-equipment-emc-features）
 * - 删除：实体模块安装流程。
 * - 新增：装备内 EMC 解锁、开关与服务端验证。
 * - 修改：以功能列表显示购买价、使用费用和开启状态；没有实物模块图标或安装槽。
 * - 用途：落实用户修正，功能属于装备且不再作为物品流通。
 */
/**
 * 【变更记录 #3】2026-10-14 01:41:14（第 3 次备注 · 1.2.0-blackhole-equipment-expansion）
 * - 删除：旧版固定强度、模块数量与单级限制的相关路径。
 * - 新增：分页功能列表、批量购买数量与精确启用等级输入框；显示已购/启用等级和本批总价，0 为关闭。
 * - 修改：与服务端 EMC 结算、装备等级和界面请求共同验证。
 * - 用途：实现用户确认的完整高阶装备系统；保留历史记录并兼容旧装备状态。
 */
/**
 * 【变更记录 #4】2026-10-14 03:09:40（第 4 次备注 · 1.2.0-exponential-equipment）
 * - 删除：本文件原有线性/对数等级结算或过时提示。
 * - 新增：攻击强化置顶，拳剑基础能力直接可见；显示下一批升级的几何级数总价，超大数以指数公式显示。
 * - 修改：统一为等级L对应2^(L-1)，使用费与升级价格逐级翻倍，0关闭。
 * - 用途：落实用户指数成长规则；保留历史等级，服务端余额校验和逐刻预算。
 */
package com.primordial.screen;
import com.primordial.equipment.BlackholeModules;
import com.primordial.equipment.BlackholeModules.Kind;
import com.primordial.equipment.BlackholeModules.Result;
import com.primordial.menu.BlackholeEquipmentMenu;
import com.primordial.network.*;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@org.anti_ad.mc.ipn.api.IPNIgnore
@yalter.mousetweaks.api.MouseTweaksIgnore
public final class BlackholeEquipmentScreen extends AbstractContainerScreen<BlackholeEquipmentMenu> {
    private final List<Kind> kinds=new ArrayList<>();
    private Kind selected;private int page;private EditBox quantity,active;private Button buy;
    public BlackholeEquipmentScreen(BlackholeEquipmentMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=360;imageHeight=238;
        for(Kind k:Kind.values())if(BlackholeModules.supports(menu.gear(),k))kinds.add(k);
        kinds.sort(Comparator.comparingInt(k->k==Kind.ASSAULT?0:BlackholeModules.innate(menu.gear(),k)?1:2));
        if(!kinds.isEmpty())selected=kinds.get(0);
    }
    @Override protected void init(){super.init();build();}
    private void build(){
        clearWidgets();
        for(int i=page*5;i<Math.min(kinds.size(),page*5+5);i++){
            Kind k=kinds.get(i);
            addRenderableWidget(Button.builder(name(k),button->{selected=k;build();})
                .bounds(leftPos+10,topPos+52+(i%5)*26,92,22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("<"),button->{page=Math.max(0,page-1);build();}).bounds(leftPos+10,topPos+190,42,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),button->{page=Math.min((kinds.size()-1)/5,page+1);build();}).bounds(leftPos+60,topPos+190,42,20).build());
        quantity=new EditBox(font,leftPos+116,topPos+104,104,20,Component.translatable("screen.primordial.equipment.quantity"));
        quantity.setMaxLength(2048);quantity.setFilter(s->s.matches("[0-9]*"));quantity.setValue("1");addRenderableWidget(quantity);
        buy=addRenderableWidget(Button.builder(Component.translatable("screen.primordial.equipment.buy_levels"),button->send(true,quantity.getValue()))
                .bounds(leftPos+228,topPos+104,120,20).build());
        active=new EditBox(font,leftPos+116,topPos+153,104,20,Component.translatable("screen.primordial.equipment.active_level"));
        active.setMaxLength(2048);active.setFilter(s->s.matches("[0-9]*"));
        active.setValue(selected==null?"0":BlackholeModules.level(menu.gear(),selected).toString());addRenderableWidget(active);
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.equipment.apply"),button->send(false,active.getValue()))
                .bounds(leftPos+228,topPos+153,120,20).build());
        if(selected!=null){
            var tip=Tooltip.create(Component.translatable("feature.primordial.desc."+selected.id));
            active.setTooltip(tip);
        }
    }
    private void send(boolean purchase,String amount){
        if(selected!=null)PrimordialNetwork.CHANNEL.sendToServer(new C2SEquipmentFeaturePacket(menu.containerId,selected.id,purchase,amount,
                BlackholeModules.owned(menu.gear(),selected).toString()));
    }
    private Component name(Kind kind){return Component.translatable("feature.primordial."+kind.id);}
    private String compact(BigInteger value){String s=value.toString();return s.length()>16?s.substring(0,6)+"… ("+s.length()+")":s;}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xF0111820);g.renderOutline(leftPos,topPos,imageWidth,imageHeight,0xFF45858C);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,title,10,8,0xA7E8EA,false);
        g.drawString(font,Component.translatable("screen.primordial.equipment.unlimited"),36,28,0xC8D4D8,false);
        if(selected==null)return;
        g.drawString(font,name(selected),116,50,0x77DD99,false);
        g.drawString(font,Component.translatable("screen.primordial.equipment.levels",compact(BlackholeModules.owned(menu.gear(),selected)),compact(BlackholeModules.level(menu.gear(),selected))),116,66,0xEEEEEE,false);
        g.drawString(font,Component.translatable("screen.primordial.equipment.quantity"),116,89,0xC8D4D8,false);
        BigInteger amount=BlackholeModules.parse(quantity.getValue());
        Component price=Component.translatable("screen.primordial.equipment.total_price",amount==null?"—":BlackholeModules.priceText(selected,BlackholeModules.owned(menu.gear(),selected),amount));
        g.drawString(font,font.plainSubstrByWidth(price.getString(),232),116,129,0xC8D4D8,false);
        buy.setTooltip(Tooltip.create(price));
        g.drawString(font,Component.translatable("screen.primordial.equipment.zero_off"),116,182,0xC8D4D8,false);
        g.drawString(font,(page+1)+" / "+Math.max(1,(kinds.size()+4)/5),30,216,0xC8D4D8,false);
        if(menu.feedback()!=Result.OK)g.drawString(font,Component.translatable("screen.primordial.equipment.result."+menu.feedback().name().toLowerCase(Locale.ROOT)),116,214,0xFFBB77,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
