package com.primordial.neoforge.client;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

import com.primordial.neoforge.config.NeoForgeClientConfig;

public final class NeoForgeEmcFormatter {
    private static final BigInteger[] UNITS = {
            BigInteger.TEN.pow(24), BigInteger.TEN.pow(21), BigInteger.TEN.pow(18), BigInteger.TEN.pow(15),
            BigInteger.TEN.pow(12), BigInteger.TEN.pow(9), BigInteger.TEN.pow(6), BigInteger.TEN.pow(3)
    };
    private static final String[] SUFFIXES = {"Y", "Z", "E", "P", "T", "G", "M", "K"};

    private NeoForgeEmcFormatter() {}

    public static String format(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            return "0";
        }
        return switch (NeoForgeClientConfig.displayMode()) {
            case COMPACT -> compact(value);
            case ENGINEERING -> new BigDecimal(value)
                    .round(new MathContext(3, RoundingMode.HALF_UP)).toEngineeringString();
            case SCIENTIFIC -> scientific(value);
        };
    }

    private static String compact(BigInteger value) {
        for (int index = 0; index < UNITS.length; index++) {
            BigInteger unit = UNITS[index];
            if (value.compareTo(unit) >= 0) {
                BigDecimal amount = new BigDecimal(value).divide(new BigDecimal(unit), 2, RoundingMode.DOWN)
                        .stripTrailingZeros();
                return amount.toPlainString() + SUFFIXES[index];
            }
        }
        return value.toString();
    }

    private static String scientific(BigInteger value) {
        String digits = value.toString();
        if (digits.length() <= 6) {
            return digits;
        }
        StringBuilder mantissa = new StringBuilder(digits.substring(0, Math.min(4, digits.length())));
        mantissa.insert(1, '.');
        while (mantissa.length() > 1 && mantissa.charAt(mantissa.length() - 1) == '0') {
            mantissa.deleteCharAt(mantissa.length() - 1);
        }
        if (mantissa.charAt(mantissa.length() - 1) == '.') {
            mantissa.deleteCharAt(mantissa.length() - 1);
        }
        return mantissa + "×10^" + (digits.length() - 1);
    }
}
