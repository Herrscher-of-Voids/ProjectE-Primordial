package com.primordial.neoforge.mixin;

import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** PNC 8.2's native Botania integration requires the legacy API absent from Botania Revived. */
@Pseudo
@Mixin(targets = "me.desht.pneumaticcraft.common.thirdparty.ThirdPartyManager", remap = false)
public abstract class NeoForgePneumaticBotaniaPresenceMixin {
    @Redirect(method = "discoverMods", at = @At(value = "INVOKE", target = "Lnet/neoforged/fml/ModList;isLoaded(Ljava/lang/String;)Z"), remap = false)
    private boolean primordial$requireCompatibleBotaniaApi(ModList mods, String modId) {
        if (!mods.isLoaded(modId)) return false;
        if (!"botania".equals(modId)) return true;
        try {
            Class.forName("vazkii.botania.api.BotaniaAPI", false, NeoForgePneumaticBotaniaPresenceMixin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError unavailable) { return false; }
    }
}
