/**
 * 【文件职责】显示服务端实体报价目录，提供名称/ID 搜索、翻页和兑换按钮。
 * 【变更记录 #1】2026-10-14 15:59:10（第 1 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：无。新增：本地中文名称搜索、单实体兑换、报价与失败提示。
 * - 修改：客户端不计算价格，只显示打开菜单时的服务端报价。
 * - 用途：玩家看见将生成的实体及 EMC 费用，位置占用、余额不足等失败不扣费。
 */
package com.primordial.neoforge.generator;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
public final class NeoForgeEntityGeneratorScreen extends AbstractContainerScreen<NeoForgeEntityGeneratorMenu> {
    private EditBox search;private int page;private final List<Button> rows=new ArrayList<>();
    public NeoForgeEntityGeneratorScreen(NeoForgeEntityGeneratorMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=310;imageHeight=235;}
    @Override protected void init(){
        super.init();rows.clear();search=new EditBox(font,leftPos+10,topPos+26,290,18,Component.translatable("gui.primordial.entity_search"));
        search.setHint(Component.translatable("gui.primordial.entity_search"));search.setMaxLength(100);search.setResponder(text->{page=0;refresh();});addRenderableWidget(search);
        for(int i=0;i<6;i++){final int row=i;rows.add(addRenderableWidget(Button.builder(Component.empty(),b->{
            var indices=filtered();int position=page*6+row;if(position<indices.size()&&minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,indices.get(position));
        }).bounds(leftPos+10,topPos+50+i*25,290,22).build()));}
        addRenderableWidget(Button.builder(Component.literal("<"),b->{page=Math.max(0,page-1);refresh();}).bounds(leftPos+10,topPos+202,30,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{page=Math.min(Math.max(0,(filtered().size()-1)/6),page+1);refresh();}).bounds(leftPos+270,topPos+202,30,20).build());refresh();
    }
    private List<Integer> filtered(){
        String text=search==null?"":search.getValue().toLowerCase(Locale.ROOT);List<Integer> list=new ArrayList<>();
        for(int i=0;i<menu.offers().size();i++){var type=menu.offers().get(i).type();
            if(type.getDescription().getString().toLowerCase(Locale.ROOT).contains(text)||BuiltInRegistries.ENTITY_TYPE.getKey(type).toString().contains(text))list.add(i);
        }return list;
    }
    private void refresh(){
        var indices=filtered();for(int i=0;i<rows.size();i++){
            Button row=rows.get(i);int index=page*6+i;row.visible=index<indices.size();
            if(row.visible){var offer=menu.offers().get(indices.get(index));row.setMessage(Component.translatable("gui.primordial.entity_offer",offer.type().getDescription(),offer.price().toString()));}
        }
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF25202E);graphics.fill(leftPos+2,topPos+2,leftPos+imageWidth-2,topPos+imageHeight-2,0xFF46404E);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){graphics.drawString(font,title,10,10,0xFFFFFF,false);
        Component status=menu.feedback()==1?Component.translatable("gui.primordial.entity_success"):menu.feedback()==2?Component.translatable("gui.primordial.entity_failed"):Component.literal((page+1)+" / "+Math.max(1,(filtered().size()+5)/6));
        graphics.drawCenteredString(font,status,imageWidth/2,207,0xFFFFFF);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){renderBackground(graphics,x,y,partial);super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);}
}
