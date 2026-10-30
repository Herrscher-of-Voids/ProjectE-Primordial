/**
 * 【文件职责】在 mod 总线注册本平台余额、定价器和转化桌介质网络负载，客户端与服务端使用同一协议表。
 * 【变更记录 #1】2026-10-23 17:05:23（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无；新增：定价器取样、提交与状态回执三种包。
 * - 修改：复用协议 1 的 registrar，由专用处理器注册并校验服务端菜单、目标及权限。
 * - 用途：串起界面与原生公共价表，客户端提交不直接更改服务端价格。
 */
package com.primordial.neoforge.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NeoForgePayloads {
    private NeoForgePayloads() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgePayloads::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Configurator queries and batch bindings now include the machine's reference facing.
        var registrar = event.registrar("2");
        registrar.playToClient(SyncBalancePayload.TYPE, SyncBalancePayload.STREAM_CODEC,
                SyncBalancePayload::handle);
        com.primordial.neoforge.priceeditor.NeoForgePriceEditorPayloads.register(registrar);
        com.primordial.neoforge.machine.NeoForgePlayerInventoryPayloads.register(registrar);
        com.primordial.neoforge.machine.NeoForgeMachineConfiguratorPayloads.register(registrar);
        com.primordial.neoforge.transmutation.NeoForgeTransmutationPayloads.register(registrar);
        com.primordial.neoforge.transmutation.NeoForgeMediaTradePayloads.register(registrar);
        com.primordial.neoforge.transmutation.NeoForgeTransmutationNbtPayloads.register(registrar);
        com.primordial.neoforge.charm.NeoForgeBlackholeCharmPayloads.register(registrar);
        com.primordial.neoforge.acceleration.AccelerationPayloads.register(registrar);
        com.primordial.neoforge.equipment.NeoForgeBlackholeEquipmentPayloads.register(registrar);
        com.primordial.neoforge.crafting.NeoForgeKnowledgeCraftPayload.register(registrar);
        com.primordial.neoforge.compat.jei.NeoForgeJeiPayloads.register(registrar);
    }
}
