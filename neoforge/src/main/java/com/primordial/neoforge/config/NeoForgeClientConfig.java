package com.primordial.neoforge.config;

import java.util.Locale;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class NeoForgeClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> EMC_DISPLAY_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("display");
        EMC_DISPLAY_MODE = builder
                .comment("EMC display mode: COMPACT, ENGINEERING or SCIENTIFIC")
                .translation("config.primordial.emc_display_mode")
                .define("emc_display_mode", "COMPACT", value -> value instanceof String mode
                        && switch (mode.toUpperCase(Locale.ROOT)) {
                            case "COMPACT", "ENGINEERING", "SCIENTIFIC" -> true;
                            default -> false;
                        });
        builder.pop();
        SPEC = builder.build();
    }

    private NeoForgeClientConfig() {}

    public static void register(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    public static DisplayMode displayMode() {
        try {
            return DisplayMode.valueOf(EMC_DISPLAY_MODE.get().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return DisplayMode.COMPACT;
        }
    }

    public enum DisplayMode {
        COMPACT,
        ENGINEERING,
        SCIENTIFIC
    }
}
