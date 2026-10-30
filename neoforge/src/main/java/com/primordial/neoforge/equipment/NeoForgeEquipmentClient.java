package com.primordial.neoforge.equipment;

import com.mojang.blaze3d.platform.InputConstants;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class NeoForgeEquipmentClient {
    public static final KeyMapping EQUIPMENT_MENU=new KeyMapping("key.primordial.equipment_menu",KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_H,"key.categories.primordial");
    public static final KeyMapping SIGNATURE=new KeyMapping("key.primordial.signature",KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.primordial");
    private NeoForgeEquipmentClient() {}

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.register(EQUIPMENT_MENU); event.register(SIGNATURE);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->{
            ItemProperties.register(NeoForgeModRegistries.BLACKHOLE_BOW.get(),ResourceLocation.withDefaultNamespace("pulling"),
                    (stack,level,entity,seed)->entity!=null&&entity.isUsingItem()&&entity.getUseItem()==stack?1:0);
            ItemProperties.register(NeoForgeModRegistries.BLACKHOLE_BOW.get(),ResourceLocation.withDefaultNamespace("pull"),
                    (stack,level,entity,seed)->entity==null||entity.getUseItem()!=stack?0:
                            NeoForgeBlackholeBowItem.drawProgress(stack,stack.getUseDuration(entity)-entity.getUseItemRemainingTicks()));
        });
    }

    @EventBusSubscriber(modid=PrimordialNeoForgeMod.MOD_ID,value=Dist.CLIENT)
    public static final class Input {
        private Input() {}
        @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
            Minecraft client=Minecraft.getInstance();
            while(SIGNATURE.consumeClick())if(client.player!=null&&client.screen==null)
                PacketDistributor.sendToServer(new NeoForgeBlackholeEquipmentPayloads.Signature());
            while(EQUIPMENT_MENU.consumeClick()) {
                if(client.player==null||client.screen!=null)continue;
                InteractionHand hand=NeoForgeBlackholeGear.isGear(client.player.getMainHandItem())?InteractionHand.MAIN_HAND:InteractionHand.OFF_HAND;
                if(NeoForgeBlackholeGear.isGear(client.player.getItemInHand(hand)))PacketDistributor.sendToServer(new NeoForgeBlackholeEquipmentPayloads.Open(hand));
            }
        }
    }
}
