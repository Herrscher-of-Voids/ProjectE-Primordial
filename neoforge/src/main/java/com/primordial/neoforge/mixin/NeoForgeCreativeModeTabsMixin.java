/** Keep vanilla's creative search and screen refresh in sync with price-editor visibility. */
package com.primordial.neoforge.mixin;

import com.primordial.neoforge.client.NeoForgeCreativePriceEditorVisibility;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CreativeModeTabs.class, remap = false)
public abstract class NeoForgeCreativeModeTabsMixin {
    @Shadow private static CreativeModeTab.ItemDisplayParameters CACHED_PARAMETERS;
    @Unique private static boolean primordial$priceEditorVisible;

    @Inject(method = "tryRebuildTabContents", at = @At("HEAD"))
    private static void primordial$refreshPriceEditor(CallbackInfoReturnable<Boolean> callback) {
        boolean visible = NeoForgeCreativePriceEditorVisibility.canShowPriceEditor();
        if (visible != primordial$priceEditorVisible) {
            primordial$priceEditorVisible = visible;
            // Vanilla's cache key omits our local-owner/OP state when its operator-items toggle is off.
            CACHED_PARAMETERS = null;
        }
    }
}
