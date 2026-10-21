package com.primordial.neoforge.client;

import java.math.BigInteger;

public final class ClientBalanceState {
    private static volatile BigInteger emc = BigInteger.ZERO;
    private static volatile boolean hudVisible = true;

    private ClientBalanceState() {}

    public static BigInteger emc() {
        return emc;
    }

    public static void setEmc(BigInteger value) {
        emc = value == null || value.signum() < 0 ? BigInteger.ZERO : value;
    }

    public static boolean hudVisible() {
        return hudVisible;
    }

    public static void toggleHud() {
        hudVisible = !hudVisible;
    }

    public static void clear() {
        emc = BigInteger.ZERO;
    }
}
