package com.primordial.neoforge.acceleration;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
public final class AccelerationPayloads {
    private AccelerationPayloads() {}
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(C2SAccelerationActionPacket.TYPE, C2SAccelerationActionPacket.STREAM_CODEC, C2SAccelerationActionPacket::handle);
        registrar.playToServer(C2SConfirmAccelerationPacket.TYPE, C2SConfirmAccelerationPacket.STREAM_CODEC, C2SConfirmAccelerationPacket::handle);
        registrar.playToClient(S2CAccelerationStatePacket.TYPE, S2CAccelerationStatePacket.STREAM_CODEC, S2CAccelerationStatePacket::handle);
        registrar.playToClient(S2CAccelerationWarningPacket.TYPE, S2CAccelerationWarningPacket.STREAM_CODEC, S2CAccelerationWarningPacket::handle);
        registrar.playToClient(S2CAccelerationMarkersPacket.TYPE, S2CAccelerationMarkersPacket.STREAM_CODEC, S2CAccelerationMarkersPacket::handle);
    }
}
