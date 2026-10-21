/**
 * 【文件职责】在 JEI 右侧物品图标左下方显示已学习物品的 EMC 可兑换数量。
 * 【变更记录 #1】2026-10-08 02:06:10（第 1 次备注 · 1.2.0-jei-counts）
 * - 删除：无；新增：绿色数量、原版按钮、客户端偏好持久化与反射方法缓存。
 * - 修改：不更改物品或余额，仅读取 ProjectE 同步后的知识、余额及价格。
 * - 用途：仅查询当前可见物品；JEI 内部 API 不兼容时停止叠加并记录一次错误。
 *
 * 【变更记录 #2】2026-10-08 02:41:00（第 2 次备注 · 1.2.0-jei-counts-purchase）
 * - 删除：无。
 * - 新增：显示开关开启时记录 JEI 物品区域，并响应 Shift + 左键兑换请求。
 * - 修改：兑换触发键由 Shift + 右键改为 Shift + 左键；显示开关按钮仍优先处理自身点击。
 * - 用途：让 JEI 物品兑换避开 JEI 对右键事件的默认处理，同时继续由服务端验证知识、容量和 EMC 结算。
 *
 * 【变更记录 #3】2026-10-08（第 3 次备注 · 1.2.0-jei-media-counts）
 * - 删除：JEI 叠加层只处理 ItemStack 的限制。
 * - 新增：Forge FluidStack 和 Mekanism 气体的 EMC 数量显示，按当前 ingredient 数量计算整份价格。
 * - 修改：介质只显示数量，不伪造物品输出；实际容器购买仍由服务器交易入口校验。
 * - 用途：让 JEI 中的物品、流体和气体都能看到一致的可兑换数量提示。
 */
/** 【变更记录 #4】2026-10-09 01:36:12（第 4 次备注 · 1.2.0-jei-media-knowledge）
 * - 删除：把非零价格当作已学习，以及客户端直接查询介质价表的路径。
 * - 新增：当前可见介质批量查询服务端授权报价，等待或拒绝时隐藏。
 * - 修改：按服务器确认的每 mB 价格计算整份数量。用途：修复未学习介质仍显示 5.7K 的问题。 */
/** 【变更记录 #5】2026-10-10 00:46:20（第 5 次备注 · 1.2.0-jei-purchase-hit）
 * - 删除：依赖上一帧渲染区域缓存的 Shift 左键命中逻辑。
 * - 新增：点击时查询 JEI 当前图标，并优先接收左键事件。
 * - 修改：仅对显示开关开启且具有知识、正 EMC 价格的物品发送购买包。
 * - 用途：修复点击无响应及翻页后的误命中；服务端仍重新验证资格和实际扣费。 */
package com.primordial.client;

import com.primordial.PrimordialMod;
import mezz.jei.api.ingredients.ITypedIngredient;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import com.primordial.network.C2SJeiMediaQuotesPacket.Key;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import com.primordial.network.C2SJeiEmcPurchasePacket;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.*;

@Mod.EventBusSubscriber(modid = PrimordialMod.MODID, value = Dist.CLIENT)
public final class JeiEmcCounts {
    private static final Map<Class<?>, Map<String, Method>> METHODS = new HashMap<>();
    private static boolean enabled = readPreference(), failed;
    private static int left = Integer.MAX_VALUE;
    private static boolean drewButton;
    private static Button button;
    private static Object ingredientOverlay;
    public static void setIngredientOverlay(Object overlay) { ingredientOverlay = overlay; }
    private static Path preference() { return FMLPaths.CONFIGDIR.get().resolve("primordial-jei-counts.txt"); }
    private static boolean readPreference() {
        try { return !Files.exists(preference()) || Boolean.parseBoolean(Files.readString(preference(), StandardCharsets.UTF_8).trim()); }
        catch (Exception e) { return true; }
    }
    private static Object call(Object object, String name) throws ReflectiveOperationException {
        var methods = METHODS.computeIfAbsent(object.getClass(), ignored -> new HashMap<>());
        Method method = methods.get(name);
        if (method == null) {
            method = object.getClass().getMethod(name); method.setAccessible(true); methods.put(name, method);
        }
        return method.invoke(object);
    }
    @SubscribeEvent public static void begin(ScreenEvent.Render.Pre event) {
        left = Integer.MAX_VALUE; drewButton = false;
        JeiMediaQuotes.begin();
    }
    public static void renderCounts(GuiGraphics graphics, List<?> slots) {
        var mc = Minecraft.getInstance();
        if (failed || mc.player == null) return;
        var knowledge = mc.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).orElse(null);
        if (knowledge == null) return;
        try {
            for (Object slot : slots) {
                if ((boolean) call(slot, "isBlocked")) continue;
                Object area = call(slot, "getRenderArea");
                int x = (int) call(area, "getX"), y = (int) call(area, "getY");
                left = Math.min(left, x);
                if (!enabled) continue;
                Object element = call(slot, "getElement");
                if (element == null) continue;
                Object value = ((ITypedIngredient<?>) call(element, "getTypedIngredient")).getIngredient();
                BigInteger price;
                com.primordial.service.EmcAmount exactPrice = null;
                boolean known;
                if (value instanceof ItemStack stack && !stack.isEmpty()) {
                    ItemInfo info = ItemInfo.fromStack(stack);
                    known = knowledge.hasKnowledge(info);
                    price = BigInteger.valueOf(IEMCProxy.INSTANCE.getValue(info));
                } else if (value instanceof FluidStack fluid && !fluid.isEmpty()) {
                    exactPrice = JeiMediaQuotes.price(new Key(false,
                            net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()))).multiply(fluid.getAmount());
                    price = exactPrice.numerator();
                    known = price.signum() > 0;
                } else if (com.primordial.compat.MekanismGasCompat.isGas(value)) {
                    var id = com.primordial.compat.MekanismGasCompat.gasId(value);
                    long amount = Math.max(1L, com.primordial.compat.MekanismGasCompat.gasAmount(value));
                    exactPrice = id == null ? com.primordial.service.EmcAmount.ZERO
                            : JeiMediaQuotes.price(new Key(true, id)).multiply(amount);
                    price = exactPrice.numerator();
                    known = price.signum() > 0;
                } else continue;
                if (!known || price.signum() <= 0) continue;
                String label = exactPrice == null
                        ? EmcCountText.label(true, knowledge.getEmc(), price)
                        : EmcCountText.label(true, knowledge.getEmc(), exactPrice);
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(x, y + 11, 220);
                    float scale = Math.min(0.65F, 16F / Math.max(1, mc.font.width(label)));
                    graphics.pose().scale(scale, scale, 1);
                    graphics.drawString(mc.font, label, 0, 0, 0x55FF55, true);
                } finally { graphics.pose().popPose(); }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true; PrimordialMod.LOGGER.warn("[Primordial] JEI count overlay disabled after compatibility error", error);
        }
    }
    public static void renderButton(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        var mc = Minecraft.getInstance();
        if (left == Integer.MAX_VALUE || mc.screen == null || failed) return;
        if (enabled) JeiMediaQuotes.flush();
        if (button == null) button = Button.builder(Component.literal("E"), ignored -> {
            enabled = !enabled;
            try { Files.writeString(preference(), Boolean.toString(enabled), StandardCharsets.UTF_8); }
            catch (Exception error) { PrimordialMod.LOGGER.warn("[Primordial] Cannot save JEI count preference", error); }
        }).bounds(0, 0, 20, 20).build();
        button.setX(Math.max(0, left - 23)); button.setY(mc.screen.height - 24);
        button.setMessage(Component.literal("E").withStyle(enabled ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY));
        button.render(graphics, mouseX, mouseY, partial); drewButton = true;
        if (button.isMouseOver(mouseX, mouseY)) graphics.renderTooltip(mc.font,
                Component.translatable(enabled ? "screen.primordial.jei_counts_on" : "screen.primordial.jei_counts_off"), mouseX, mouseY);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void click(ScreenEvent.MouseButtonPressed.Pre event) {
        if (drewButton && button != null && event.getButton() == 0
                && button.mouseClicked(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
            return;
        }
        if (!enabled || failed || ingredientOverlay == null || event.getButton() != 0
                || !net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        try {
            if (!(boolean) call(ingredientOverlay, "isListDisplayed")) return;
            Object hovered = call(ingredientOverlay, "getIngredientUnderMouse");
            if (!(hovered instanceof Optional<?> optional) || optional.isEmpty()
                    || !(optional.get() instanceof ITypedIngredient<?> ingredient)
                    || !(ingredient.getIngredient() instanceof ItemStack stack) || stack.isEmpty()) return;
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).orElse(null);
            if (knowledge == null) return;
            ItemInfo info = ItemInfo.fromStack(stack);
            if (!knowledge.hasKnowledge(info) || IEMCProxy.INSTANCE.getValue(info) <= 0) return;
            com.primordial.network.PrimordialNetwork.CHANNEL.sendToServer(
                    new C2SJeiEmcPurchasePacket(stack.copyWithCount(1)));
            event.setCanceled(true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            PrimordialMod.LOGGER.warn("[Primordial] JEI purchase input disabled after compatibility error", error);
        }
    }
}
