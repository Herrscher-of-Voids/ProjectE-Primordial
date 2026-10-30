package com.primordial.neoforge.equipment;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Kind;
import com.primordial.neoforge.equipment.NeoForgeBlackholeModules.Result;
import net.neoforged.neoforge.network.PacketDistributor;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

@yalter.mousetweaks.api.MouseTweaksIgnore
public final class NeoForgeBlackholeEquipmentScreen extends AbstractContainerScreen<NeoForgeBlackholeEquipmentMenu> {
    private final List<Kind> kinds=new ArrayList<>();
    private Kind selected;private int page;private EditBox quantity,active;private Button buy;
    public NeoForgeBlackholeEquipmentScreen(NeoForgeBlackholeEquipmentMenu menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=360;imageHeight=238;
        for(Kind k:Kind.values())if(NeoForgeBlackholeModules.supports(menu.gear(),k))kinds.add(k);
        kinds.sort(Comparator.comparingInt(k->k==Kind.ASSAULT?0:NeoForgeBlackholeModules.innate(menu.gear(),k)?1:2));
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
        active.setValue(selected==null?"0":NeoForgeBlackholeModules.level(menu.gear(),selected).toString());addRenderableWidget(active);
        addRenderableWidget(Button.builder(Component.translatable("screen.primordial.equipment.apply"),button->send(false,active.getValue()))
                .bounds(leftPos+228,topPos+153,120,20).build());
        if(selected!=null){
            var tip=Tooltip.create(Component.translatable("feature.primordial.desc."+selected.id));
            active.setTooltip(tip);
        }
    }
    private void send(boolean purchase,String amount){
        if(selected!=null)PacketDistributor.sendToServer(new NeoForgeBlackholeEquipmentPayloads.Action(menu.containerId,selected.id,purchase,amount,
                NeoForgeBlackholeModules.owned(menu.gear(),selected).toString()));
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
        g.drawString(font,Component.translatable("screen.primordial.equipment.levels",compact(NeoForgeBlackholeModules.owned(menu.gear(),selected)),compact(NeoForgeBlackholeModules.level(menu.gear(),selected))),116,66,0xEEEEEE,false);
        g.drawString(font,Component.translatable("screen.primordial.equipment.quantity"),116,89,0xC8D4D8,false);
        BigInteger amount=NeoForgeBlackholeModules.parse(quantity.getValue());
        Component price=Component.translatable("screen.primordial.equipment.total_price",amount==null?"—":NeoForgeBlackholeModules.priceText(selected,NeoForgeBlackholeModules.owned(menu.gear(),selected),amount));
        g.drawString(font,font.plainSubstrByWidth(price.getString(),232),116,129,0xC8D4D8,false);
        buy.setTooltip(Tooltip.create(price));
        g.drawString(font,Component.translatable("screen.primordial.equipment.zero_off"),116,182,0xC8D4D8,false);
        g.drawString(font,(page+1)+" / "+Math.max(1,(kinds.size()+4)/5),30,216,0xC8D4D8,false);
        if(menu.feedback()!=Result.OK)g.drawString(font,Component.translatable("screen.primordial.equipment.result."+menu.feedback().name().toLowerCase(Locale.ROOT)),116,214,0xFFBB77,false);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g,x,y,partial);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
