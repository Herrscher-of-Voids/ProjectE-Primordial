/**
 * 【文件职责】处理加速器管理键与潜行滚轮倍率选择。
 * 【变更记录 #1】2026-09-18 01:50:43（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：主手优先、副手回退的 X 管理入口，潜行滚轮双向循环 2 至 1024 档。
 * - 修改：客户端只发意图，不直接改 NBT 或启用高倍率；界面打开时不拦截滚轮。
 * - 用途：便捷选择与服务器权威一致，空手和其它工具的滚轮保持原行为。
 */
package com.primordial.client;

import com.primordial.item.EmcAcceleratorItem;
import com.primordial.network.C2SAccelerationActionPacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.service.AccelerationBindingActions;
import com.primordial.service.AccelerationSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "primordial", value = Dist.CLIENT)
public final class AccelerationClientInput {
    private AccelerationClientInput() { }
    public static InteractionHand hand() {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        for (var hand : InteractionHand.values()) if (player.getItemInHand(hand).getItem() instanceof EmcAcceleratorItem) return hand;
        return null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (ClientSetup.ACCELERATOR_MENU.consumeClick()) {
            InteractionHand hand = hand();
            if (hand != null && Minecraft.getInstance().screen == null)
                PrimordialNetwork.CHANNEL.sendToServer(C2SAccelerationActionPacket.open(hand));
        }
    }
    @SubscribeEvent public static void scroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        InteractionHand hand = hand();
        if (mc.screen != null || mc.player == null || !mc.player.isShiftKeyDown() || hand == null || event.getScrollDelta() == 0) return;
        event.setCanceled(true);
        PrimordialNetwork.CHANNEL.sendToServer(new C2SAccelerationActionPacket(-1, C2SAccelerationActionPacket.Action.CYCLE,
                AccelerationBindingActions.SELECTION, AccelerationSettings.read(mc.player.getItemInHand(hand).getOrCreateTag()),
                event.getScrollDelta() > 0, hand));
    }
}
