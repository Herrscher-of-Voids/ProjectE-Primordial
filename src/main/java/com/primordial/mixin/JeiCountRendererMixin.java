/**
 * 【变更记录 #1】2026-10-08 02:06:10（第 1 次备注 · 1.2.0-jei-counts）
 * - 删除：无；新增：JEI 可搜索列表绘制尾部钩子；修改：不改变 JEI 物品渲染。
 * - 用途：仅向右侧列表叠加可兑换数量，JEI 缺失时跳过。
 */
package com.primordial.mixin;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.IngredientListRenderer", remap = false)
public abstract class JeiCountRendererMixin {
    @Shadow @Final private List<?> slots;
    @Shadow @Final private boolean searchable;
    @Inject(method = "render", at = @At("TAIL"))
    private void primordial$counts(GuiGraphics graphics, CallbackInfo ci) {
        if (searchable) com.primordial.client.JeiEmcCounts.renderCounts(graphics, slots);
    }
}
