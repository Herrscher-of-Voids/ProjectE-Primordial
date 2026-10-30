/** JEI 19 parity: exact counts, saved toggle and authoritative Shift-left-click purchases. */
package com.primordial.neoforge.client;

import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.client.EmcCountText;
import mezz.jei.api.ingredients.ITypedIngredient;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import com.primordial.neoforge.compat.jei.NeoForgeJeiPayloads.Key;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import com.primordial.neoforge.compat.jei.NeoForgeJeiPayloads;
import com.primordial.neoforge.compat.NeoForgePriceEditorChemicalBridge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.*;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID, value = Dist.CLIENT)
public final class NeoForgeJeiEmcCounts {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static long purchaseSequence;
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
        NeoForgeJeiMediaQuotes.begin();
    }
    public static void renderCounts(GuiGraphics graphics, List<?> slots) {
        var mc = Minecraft.getInstance();
        if (failed || mc.player == null) return;
        var knowledge = mc.player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
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
                    exactPrice = NeoForgeJeiMediaQuotes.price(new Key(false,
                            net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid()))).multiply(fluid.getAmount());
                    price = exactPrice.numerator();
                    known = price.signum() > 0;
                } else if (NeoForgePriceEditorChemicalBridge.chemicalId(value) != null) {
                    var id = NeoForgePriceEditorChemicalBridge.chemicalId(value);
                    long amount = Math.max(1L, NeoForgePriceEditorChemicalBridge.chemicalAmount(value));
                    exactPrice = id == null ? com.primordial.service.EmcAmount.ZERO
                            : NeoForgeJeiMediaQuotes.price(new Key(true, id)).multiply(amount);
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
            failed = true; LOGGER.warn("[Primordial] JEI count overlay disabled after compatibility error", error);
        }
    }
    public static void renderButton(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        var mc = Minecraft.getInstance();
        if (left == Integer.MAX_VALUE || mc.screen == null || failed) return;
        if (enabled) NeoForgeJeiMediaQuotes.flush();
        if (button == null) button = Button.builder(Component.literal("E"), ignored -> {
            enabled = !enabled;
            try { Files.writeString(preference(), Boolean.toString(enabled), StandardCharsets.UTF_8); }
            catch (Exception error) { LOGGER.warn("[Primordial] Cannot save JEI count preference", error); }
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
            var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            if (knowledge == null) return;
            ItemInfo info = ItemInfo.fromStack(stack);
            if (!knowledge.hasKnowledge(info) || IEMCProxy.INSTANCE.getValue(info) <= 0) return;
            PacketDistributor.sendToServer(new NeoForgeJeiPayloads.Purchase(
                    player.containerMenu.containerId, ++purchaseSequence, stack.copyWithCount(1)));
            event.setCanceled(true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            LOGGER.warn("[Primordial] JEI purchase input disabled after compatibility error", error);
        }
    }
}
