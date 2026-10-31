/**
 * 【文件职责】仅由 JEI 客户端发现定价器幽灵样本目标，将拖放转换为既有服务端校验的选择请求。
 * 【变更记录 #1】2026-10-23 16:57:42（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。
 * - 新增：JEI 19 的单一样本槽 Ghost Ingredient handler，支持物品、NeoForge 流体及可选统一化学品。
 * - 修改：物品和流体先复制样本，化学品只保留 ID 与 long 数量；accept 委托屏幕发送 C2S 选择，不直接改服务端库存。
 * - 用途：拖放不移动或消耗任何实物，不实现交易或管理员授权；无 JEI 时公共启动器不引用本类。
 */
package com.primordial.neoforge.compat.jei;

import java.util.List;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import com.primordial.neoforge.priceeditor.NeoForgePriceEditorScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

@JeiPlugin
public final class NeoForgePriceEditorJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, "price_editor");
    }

    @Override public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        // Permission-filtered creative entries can be absent from JEI's initial item scan.
        registration.addExtraItemStacks(List.of(NeoForgeModRegistries.PRICE_EDITOR_ITEM.get().getDefaultInstance()));
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(NeoForgePriceEditorScreen.class, new IGhostIngredientHandler<>() {
            @Override public <I> List<Target<I>> getTargetsTyped(NeoForgePriceEditorScreen screen,
                                                              ITypedIngredient<I> ingredient, boolean doStart) {
                Object value = ingredient.getIngredient();
                Runnable accept;
                if (value instanceof ItemStack item && !item.isEmpty()) {
                    ItemStack sample = item.copyWithCount(1);
                    accept = () -> screen.acceptJeiItem(sample.copy());
                } else if (value instanceof FluidStack fluid && !fluid.isEmpty()) {
                    FluidStack sample = fluid.copy();
                    accept = () -> screen.acceptJeiFluid(sample.copy());
                } else {
                    ResourceLocation id = NeoForgePriceEditorChemicalBridge.chemicalId(value);
                    long amount = NeoForgePriceEditorChemicalBridge.chemicalAmount(value);
                    if (id == null || amount <= 0) return List.of();
                    accept = () -> screen.acceptJeiChemical(id, amount);
                }
                Rect2i area = screen.jeiPriceArea();
                return List.of(new Target<>() {
                    @Override public Rect2i getArea() { return area; }
                    @Override public void accept(I ignored) { accept.run(); }
                });
            }

            @Override public void onComplete() { }
            @Override public boolean shouldHighlightTargets() { return true; }
        });
    }
}
