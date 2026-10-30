/**
 * 【文件职责】在服务器启动及数据重载时刷新黑洞流体回收价格。
 * 【变更记录 #1】2026-10-22 21:44:20（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：服务端启动和资源重载监听。
 * - 修改：配置解析发布不可变快照，失败时拒绝回收流体。
 * - 用途：价格文件可重新加载且不在每次接收时读磁盘。
 */
package com.primordial.neoforge.events;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeRecoveryEvents {
    private NeoForgeRecoveryEvents() {}
    @SubscribeEvent
    public static void onStarting(ServerAboutToStartEvent event) { NeoForgeRecoveryPrices.reload(); }
    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) manager -> NeoForgeRecoveryPrices.reload());
    }
}
