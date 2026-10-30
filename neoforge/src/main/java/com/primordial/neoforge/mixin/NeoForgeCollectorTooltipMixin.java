package com.primordial.neoforge.mixin;

import moze_intel.projecte.gameObjs.EnumCollectorTier;
import moze_intel.projecte.gameObjs.blocks.Collector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Match the original native tooltip to 1/3/6 billion EMC and 40/120/400 EMC per second. */
@Mixin(value = Collector.class, remap = false)
public abstract class NeoForgeCollectorTooltipMixin {
    @Redirect(method = "appendHoverText", at = @At(value = "INVOKE", target = "Lmoze_intel/projecte/gameObjs/EnumCollectorTier;getGenRate()J"), remap = false)
    private long primordial$generation(EnumCollectorTier tier) { return Math.multiplyExact(tier.getGenRate(), 10L); }
    @Redirect(method = "appendHoverText", at = @At(value = "INVOKE", target = "Lmoze_intel/projecte/gameObjs/EnumCollectorTier;getStorage()J"), remap = false)
    private long primordial$capacity(EnumCollectorTier tier) { return Math.multiplyExact(tier.getStorage(), 100_000L); }
}
