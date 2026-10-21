package com.primordial.neoforge.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class NeoForgePayloads {
    private NeoForgePayloads() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgePayloads::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(SyncBalancePayload.TYPE, SyncBalancePayload.STREAM_CODEC,
                SyncBalancePayload::handle);
    }
}
