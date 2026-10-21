/**
 * 【文件职责】拦截实体模式下的原版实体右键，覆盖生物和可交互非生物实体。
 * 【变更记录 #1】2026-09-18 01:50:43（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：EntityInteract 与 EntityInteractSpecific 路由，使用实际服务端目标并限制重复交互。
 * - 修改：只在手持加速器且选择实体模式时消费事件，其它模式不抢占实体原操作。
 * - 用途：绑定仍经过实体种类、存活、区块、骑乘与服务器开关校验，客户端不能指定任意 UUID。
 */
package com.primordial.service;

import com.primordial.item.EmcAcceleratorItem;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "primordial")
public final class AccelerationEntityInteraction {
    private static final Map<ServerPlayer, Integer> LAST_INTERACTION = new WeakHashMap<>();
    private AccelerationEntityInteraction() { }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void interact(PlayerInteractEvent.EntityInteract event) {
        route(event, event.getTarget());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void specific(PlayerInteractEvent.EntityInteractSpecific event) {
        route(event, event.getTarget());
    }
    private static void route(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
        var tool = event.getItemStack();
        if (!(tool.getItem() instanceof EmcAcceleratorItem) || tool.getTag() == null
                || AccelerationSettings.kind(tool.getTag()) != AccelerationSettings.Kind.ENTITY) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int now = player.server.getTickCount();
        if (LAST_INTERACTION.getOrDefault(player, Integer.MIN_VALUE) == now) return;
        LAST_INTERACTION.put(player, now);
        AccelerationBindingActions.entity(tool, player, target, event.getHand());
    }
}
