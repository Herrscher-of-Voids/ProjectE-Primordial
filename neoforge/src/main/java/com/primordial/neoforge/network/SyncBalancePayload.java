package com.primordial.neoforge.network;

import java.math.BigInteger;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.client.ClientBalanceState;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncBalancePayload(BigInteger emc) implements CustomPacketPayload {
    private static final int MAX_DIGITS = 2048;

    public static final Type<SyncBalancePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "balance_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBalancePayload> STREAM_CODEC =
            StreamCodec.of(SyncBalancePayload::encode, SyncBalancePayload::decode);

    public SyncBalancePayload {
        if (emc == null || emc.signum() < 0) {
            emc = BigInteger.ZERO;
        }
        if (emc.toString().length() > MAX_DIGITS) {
            throw new IllegalArgumentException("EMC balance exceeds the network limit");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SyncBalancePayload payload) {
        buffer.writeUtf(payload.emc().toString(), MAX_DIGITS);
    }

    private static SyncBalancePayload decode(RegistryFriendlyByteBuf buffer) {
        String value = buffer.readUtf(MAX_DIGITS);
        if (value.isEmpty() || value.length() > MAX_DIGITS
                || value.chars().anyMatch(character -> character < '0' || character > '9')) {
            throw new DecoderException("Invalid EMC balance payload");
        }
        try {
            return new SyncBalancePayload(new BigInteger(value));
        } catch (NumberFormatException malformed) {
            throw new DecoderException("Invalid EMC balance payload", malformed);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncBalancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBalanceState.setEmc(payload.emc()));
    }
}
