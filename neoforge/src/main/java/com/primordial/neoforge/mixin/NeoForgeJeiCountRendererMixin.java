/** Optional JEI 19 count hook; excluded from dedicated-server mixin loading. */
package com.primordial.neoforge.mixin;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListRenderer", remap = false)
public abstract class NeoForgeJeiCountRendererMixin {
    @Shadow @Final private List<?> slots;
    @Shadow @Final private boolean searchable;
    @Inject(method = "render", at = @At("TAIL"))
    private void primordial$counts(GuiGraphics graphics, CallbackInfo ci) {
        if (searchable) com.primordial.neoforge.client.NeoForgeJeiEmcCounts.renderCounts(graphics, slots);
    }
}
