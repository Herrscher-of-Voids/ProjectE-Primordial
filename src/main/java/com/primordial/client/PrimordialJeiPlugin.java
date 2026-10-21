/**
 * 【变更记录 #3】2026-09-16（价格编辑页 JEI 拖放）
 * - 删除：无；保留配置器三十六个样本槽的现有 Ghost Ingredient 处理。
 * - 新增：PriceEditorScreen 单一样本槽的 JEI 目标，支持 ItemStack、FluidStack 和 Mekanism GasStack。
 * - 修改：价格页投放回调交由屏幕向服务端发送目标包，服务端不接受客户端直接伪造注册表对象或价格。
 * - 用途：管理员可以把没有桶形态的流体/气体从 JEI 拖到价格编辑界面，再输入 EMC/mB 单价。
 */
/**
 * 【文件职责】把 JEI 的 ItemStack/FluidStack ingredient 拖放桥接到配置器幽灵槽。
 * 【变更记录 #1】2026-09-14 13:05:00（第 1 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：JEI 15 Ghost Ingredient handler，直接接受 FluidStack，不依赖流体桶形态。
 * - 修改：插件仅由 JEI 客户端发现，主模组没有 JEI 时不加载此类；服务端过滤器仍由 C2S 包校验。
 * - 用途：解决气体/无桶流体无法放入配置器的问题，并保留 FluidStack NBT。
 */
/**
 * 【变更记录 #2】2026-09-15 12:04:51（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：直接对可选 GasStack 类型执行 instanceof 的外层引用。
 * - 新增：通过 MekanismGasCompat 判断化学气体。
 * - 修改：继续让 JEI 向当前页提供 18 个目标，气体与 Forge 流体进入流体页。
 * - 用途：未安装 Mekanism 时物品和流体拖放仍可使用；真正保存由服务端包校验。
 */
/**
 * 【变更记录 #4】2026-10-03 03:34:00（第 4 次备注 · 1.2.0-transmutation-crafting）
 * - 删除：无。
 * - 新增：ProjectE 转化桌 3x3 合成页的 JEI 原版配方传输注册。
 * - 修改：传输目标固定为菜单 63-71，材料来源固定为玩家背包 27-62。
 * - 用途：JEI 可将现有材料放入合成网格，配方匹配和产物仍由服务端计算。
 */
/**
 * 【变更记录 #5】2026-10-03 03:55:21（第 5 次备注 · 1.2.0-transmutation-jei-transfer）
 * - 删除：把 27-62 号槽无条件交给 JEI 的固定范围传输注册。
 * - 新增：按当前菜单动态提供 63-71 号合成槽及允许取物的玩家背包槽。
 * - 修改：手持转化工具时排除 ProjectE 锁定的快捷栏槽，避免 JEI 校验失败。
 * - 用途：在转化桌查看配方时可用 JEI 加号自动搬入已有材料，不绕过 ProjectE 的工具槽保护。
 */
/**
 * 【变更记录 #6】2026-10-07 00:44:19（第 6 次备注 · 1.2.0-knowledge-crafting）
 * - 删除：仅使用背包材料的 JEI 基础传输信息注册。
 * - 新增：自定义加号处理器，发送配方 ID 与批量标记并切换合成页。
 * - 修改：材料与知识、EMC 的权威校验交由服务端，客户端不伪造材料或价格。
 * - 用途：缺少背包材料时也可从整个转化知识中兑换，保持 JEI 可选加载边界。
 */
/** 【变更记录 #7】2026-10-10 00:46:20（第 7 次备注 · 1.2.0-jei-purchase-hit）
 * - 删除：无。新增：把 JEI 运行时物品列表交给点击处理器，退出时清除引用。
 * - 修改：Shift 左键根据当前 JEI 图标识别目标，不再依赖上一帧渲染坐标。
 * - 用途：确保翻页、搜索与可选 JEI 生命周期中的兑换命中准确。 */
/**
 * 【变更记录 #8】2026-10-14 04:23:00（第 8 次备注 · 1.2.0-condenser-jei-display）
 * - 删除：无；保留旧 JEI 拖放、转化桌配方搬运和购买交互。
 * - 新增：注册八种原初黑洞到变体的 1:1 EMC 凝聚器展示配方，以及 ProjectE 两代凝聚器入口。
 * - 修改：配方仅在可选 JEI 插件中显示，真正转换仍由服务端 ProjectE 凝聚器执行。
 * - 用途：玩家从黑洞材料的 JEI 用途/配方页能找到取得方式。
 */
/**
 * 【变更记录 #9】2026-10-14 05:43:37（第 9 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：自定义凝聚器 JEI 分类、八条虚假的凝聚器记录及两代凝聚器入口。
 * - 新增：向 ProjectE 原生收集器分类注册原初黑洞到八种材料的升级记录。
 * - 修改：三代收集器入口由 ProjectE 自身管理，扣费显示使用原生收集器配方组件。
 * - 用途：JEI 展示与实际收集器升级槽一致，避免引导玩家使用错误机器。
 */
/**
 * 【变更记录 #10】2026-10-14 05:54:38（第 10 次备注 · 1.2.0-collector-blackhole-correction）
 * - 删除：第八条与 ProjectE 原生燃料图重复的末端目标记录。
 * - 新增：其余七种原初黑洞直达配方由本插件补入 ProjectE 收集器分类。
 * - 修改：末端克尔-纽曼配方交给 ProjectE 原生燃料图注册，避免 JEI 同目标重复。
 * - 用途：八种目标各有一条原初黑洞输入配方，不显示无关的变体链。
 */
/**
 * 【变更记录 #11】2026-10-14 14:30:45（第 11 次备注 · 1.2.0-collector-sequential-chain）
 * - 删除：七条原初黑洞直达变体的手工展示配方。
 * - 新增：无额外展示配方。
 * - 修改：相邻八条升级记录统一交给 ProjectE 原生燃料图注册。
 * - 用途：JEI 的输入、产物和差额与收集器实际升级链一致，避免重复或跳阶。
 */
/**
 * 【变更记录 #12】2026-10-14 14:53:38（第 12 次备注 · 1.2.0-native-storage-nodes）
 * - 删除：已移除手工升级配方的无用导入。
 * - 新增：无。
 * - 修改：JEI 升级记录统一来自 ProjectE 燃料图。
 * - 用途：保持当前配方入口清晰。
 */
package com.primordial.client;

import com.primordial.PrimordialMod;
import com.primordial.screen.ConfiguratorScreen;
import com.primordial.screen.PriceEditorScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.gameObjs.registries.PEContainerTypes;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JeiPlugin
public final class PrimordialJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid() { return new ResourceLocation(PrimordialMod.MODID, "jei"); }

    @Override public void onRuntimeAvailable(IJeiRuntime runtime) {
        JeiEmcCounts.setIngredientOverlay(runtime.getIngredientListOverlay());
    }

    @Override public void onRuntimeUnavailable() {
        JeiEmcCounts.setIngredientOverlay(null);
    }

    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new IRecipeTransferHandler<TransmutationContainer, CraftingRecipe>() {
            @Override public Class<? extends TransmutationContainer> getContainerClass() {
                return TransmutationContainer.class;
            }

            @Override public Optional<MenuType<TransmutationContainer>> getMenuType() {
                return Optional.of(PEContainerTypes.TRANSMUTATION_CONTAINER.get());
            }

            @Override public RecipeType<CraftingRecipe> getRecipeType() { return RecipeTypes.CRAFTING; }

            @Override public IRecipeTransferError transferRecipe(TransmutationContainer menu, CraftingRecipe recipe,
                    IRecipeSlotsView slots, Player player, boolean maximum, boolean doTransfer) {
                if (menu.slots.size() < 73 || recipe.isSpecial() || !recipe.canCraftInDimensions(3, 3))
                    return registration.getTransferHelper().createInternalError();
                if (!menu.getCarried().isEmpty()) return registration.getTransferHelper().createUserErrorWithTooltip(
                        net.minecraft.network.chat.Component.translatable("message.primordial.crafting_empty_cursor"));
                if (doTransfer) {
                    com.primordial.network.PrimordialNetwork.CHANNEL.sendToServer(
                            new com.primordial.network.C2SKnowledgeCraftPacket(menu.containerId, recipe.getId(), maximum));
                    TransmutationMediaButton.showCraftingPage(menu);
                }
                return null;
            }
        }, RecipeTypes.CRAFTING);
    }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(ConfiguratorScreen.class, new IGhostIngredientHandler<ConfiguratorScreen>() {
            @Override public <I> List<Target<I>> getTargetsTyped(ConfiguratorScreen screen,
                    ITypedIngredient<I> ingredient, boolean doStart) {
                Object value = ingredient.getIngredient();
                boolean fluid = value instanceof FluidStack || com.primordial.compat.MekanismGasCompat.isGas(value);
                if (!(value instanceof ItemStack) && !fluid) return List.of();
                List<Target<I>> targets = new ArrayList<>();
                for (Rect2i area : screen.jeiSampleAreas(fluid)) {
                    targets.add(new Target<>() {
                        @Override public Rect2i getArea() { return area; }
                        @SuppressWarnings("unchecked")
                        @Override public void accept(I ignored) { screen.acceptJeiIngredient(value, area); }
                    });
                }
                return targets;
            }
            @Override public void onComplete() { }
            @Override public boolean shouldHighlightTargets() { return true; }
        });
        registration.addGhostIngredientHandler(PriceEditorScreen.class, new IGhostIngredientHandler<PriceEditorScreen>() {
            @Override public <I> List<Target<I>> getTargetsTyped(PriceEditorScreen screen,
                    ITypedIngredient<I> ingredient, boolean doStart) {
                Object value = ingredient.getIngredient();
                boolean accepted = value instanceof ItemStack
                        || value instanceof FluidStack
                        || com.primordial.compat.MekanismGasCompat.isGas(value);
                if (!accepted) return List.of();
                Rect2i area = screen.jeiPriceArea();
                return List.of(new Target<>() {
                    @Override public Rect2i getArea() { return area; }
                    @Override public void accept(I ignored) { screen.acceptJeiIngredient(value); }
                });
            }
            @Override public void onComplete() { }
            @Override public boolean shouldHighlightTargets() { return true; }
        });
    }
}
