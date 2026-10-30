package com.primordial.neoforge.resource;

import java.math.BigInteger;
import java.util.List;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.service.EmcAmount;
import net.minecraft.resources.ResourceLocation;

/** Stable Forge machine row order; reserved magic/technology options never become priced endpoints. */
public final class NeoForgeResourceUnits {
    private static final List<ResourceLocation> MACHINE = List.of("botania:mana", "pneumaticcraft:pressure",
            "naturesaura:aura", "ars_nouveau:source", "create:rotation_speed", "create:stress")
            .stream().map(ResourceLocation::parse).toList();
    private static final EmcAmount DEFAULT = new EmcAmount(BigInteger.ONE, BigInteger.valueOf(1000));
    private NeoForgeResourceUnits() {}
    public static List<ResourceLocation> machineUnits() { return MACHINE; }
    public static ResourceLocation machineUnit(int index) { return index < 0 || index >= MACHINE.size() ? null : MACHINE.get(index); }
    public static boolean known(ResourceLocation unit) {
        return unit != null && (MACHINE.contains(unit) || ResourceLocation.parse("irons_spellbooks:mana").equals(unit)
                || ResourceLocation.parse("goety:soul_energy").equals(unit));
    }
    public static EmcAmount price(ResourceLocation unit) {
        if (!known(unit)) return EmcAmount.ZERO;
        for (String rule : NeoForgeServerConfig.resourceBlacklist())
            if (rule.trim().equals(unit.toString()) || rule.trim().equals(unit.getNamespace() + ":*")) return EmcAmount.ZERO;
        EmcAmount result = DEFAULT;
        for (String entry : NeoForgeServerConfig.nativeResourceRatios()) if (validEntry(entry)) {
            int split = entry.indexOf('=');
            if (unit.toString().equals(entry.substring(0, split))) result = EmcAmount.parse(entry.substring(split + 1));
        }
        return result;
    }
    public static boolean validEntry(Object value) {
        if (!(value instanceof String text) || text.length() > 512) return false;
        String[] pair = text.split("=", -1);
        if (pair.length != 2 || ResourceLocation.tryParse(pair[0]) == null) return false;
        try { return EmcAmount.parse(pair[1]).numerator().signum() > 0; }
        catch (RuntimeException invalid) { return false; }
    }
}
