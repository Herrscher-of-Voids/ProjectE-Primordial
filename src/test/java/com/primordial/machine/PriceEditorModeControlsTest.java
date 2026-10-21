/**
 * 【文件职责】通过真实按钮回调验证定价模式请求、等待状态与权限控制。
 * 【变更记录 #1】2026-10-10 03:18:05（第 1 次备注 · 1.2.0-price-mode-controls）
 * - 删除：无。
 * - 新增：物品恢复/禁用请求、流体和气体方向请求、未报价/无权限拒绝、保存中重复点击及布局边界验证。
 * - 修改：仅替代菜单状态、Forge 通道与输入框，不替代 Button 或 PriceEditorScreen 的实际提交路径。
 * - 用途：证明按钮只发送受支持的既有协议、不覆盖未保存输入、等待服务器结果，避免客户端自称保存成功。
 */
/**
 * 【变更记录 #2】2026-10-10 03:21:28（第 2 次备注 · 1.2.0-price-mode-controls）
 * - 删除：无。
 * - 新增：检查物品两按钮的扩展宽度、媒体四按钮切换后的范围与反馈区隔离。
 * - 修改：用真实按钮坐标验证选择类型切换后布局，不依赖渲染或游戏窗口。
 * - 用途：防止价格模式按钮与邻近按钮、输入行或反馈区重叠；实际字号和游戏视觉仍需实例验证。
 */
/**
 * 【变更记录 #3】2026-10-10 03:25:41（第 3 次备注 · 1.2.0-media-custom-restore）
 * - 删除：旧四按钮布局宽度的测试期待。
 * - 新增：自定按钮的模式请求、物品隐藏、无权限拒绝和等待确认控制验证。
 * - 修改：介质按钮边界断言使用五按钮 30 像素宽度并覆盖全部 PriceMode；物品维持原有两按钮布局。
 * - 用途：验证恢复人工价格只发送模式包且不读取未保存输入，防止把当前零价错误当作新价格保存。
 */
package com.primordial.machine;

import com.primordial.menu.PriceEditorMenu;
import com.primordial.network.C2SPlayerActionPacket;
import com.primordial.network.C2SSetMediaPricePacket;
import com.primordial.network.PrimordialNetwork;
import com.primordial.network.S2CPriceEditorStatePacket;
import com.primordial.screen.PriceEditorScreen;
import com.primordial.value.PriceMode;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.simple.SimpleChannel;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceEditorModeControlsTest {
    private static SimpleChannel channel;
    private PriceEditorMenu menu;
    private PriceEditorScreen screen;
    private EditBox input;
    private final AtomicReference<S2CPriceEditorStatePacket> state = new AtomicReference<>();

    @BeforeAll static void bootstrap() {
        AdapterTestBootstrap.initialize();
        try (var registry = mockStatic(net.minecraftforge.network.NetworkRegistry.class)) {
            registry.when(() -> net.minecraftforge.network.NetworkRegistry.newSimpleChannel(any(), any(), any(), any()))
                    .thenReturn(mock(SimpleChannel.class));
            channel = PrimordialNetwork.CHANNEL;
        }
    }

    @BeforeEach void setup() throws Exception {
        reset(channel);
        menu = mock(PriceEditorMenu.class);
        when(menu.selectedFluid()).thenReturn(FluidStack.EMPTY);
        when(menu.priceState()).thenAnswer(call -> state.get());
        screen = new PriceEditorScreen(menu, new Inventory(mock(Player.class)), Component.empty());
        input = mock(EditBox.class);
        var field = PriceEditorScreen.class.getDeclaredField("value");
        field.setAccessible(true);
        field.set(screen, input);
        call("initModeButtons");
        select(PriceEditorMenu.KIND_FLUID, "minecraft:water", true);
    }

    private void select(byte kind, String id, boolean editable) throws Exception {
        when(menu.selectedKind()).thenReturn(kind);
        when(menu.selectedId()).thenReturn(new ResourceLocation(id));
        state.set(new S2CPriceEditorStatePacket(0, kind, id, "1000", "CUSTOM_VALUE", editable,
                S2CPriceEditorStatePacket.Status.CURRENT));
        call("syncSelectionUi");
    }

    private void call(String name) throws Exception {
        var method = PriceEditorScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(screen);
    }

    @SuppressWarnings("unchecked") private Button button(PriceMode mode) throws Exception {
        var field = PriceEditorScreen.class.getDeclaredField("modeButtons");
        field.setAccessible(true);
        return ((Map<PriceMode, Button>) field.get(screen)).get(mode);
    }

    @Test void mediaButtonsWaitForServerAndDoNotResubmitOrOverwriteInput() throws Exception {
        button(PriceMode.SELL_ONLY).onPress();
        verify(channel).sendToServer(new C2SSetMediaPricePacket(0, PriceEditorMenu.KIND_FLUID, "minecraft:water", "sell_only"));
        assertEquals("CUSTOM_VALUE", state.get().mode());
        call("syncSelectionUi");
        for (PriceMode mode : PriceMode.values()) {
            assertFalse(button(mode).active);
            button(mode).onPress();
        }
        verify(channel, times(1)).sendToServer(any());
        verify(input, never()).getValue();

        state.set(new S2CPriceEditorStatePacket(0, PriceEditorMenu.KIND_FLUID, "minecraft:water", "1000", "SELL_ONLY", true,
                S2CPriceEditorStatePacket.Status.SAVED));
        call("syncSelectionUi");
        assertFalse(button(PriceMode.SELL_ONLY).active);
        assertTrue(button(PriceMode.BUY_ONLY).active);
    }

    @Test void itemsExposeOnlyResetAndDisableAndDisableUsesProjectEZeroPrice() throws Exception {
        select(PriceEditorMenu.KIND_ITEM, "minecraft:diamond", true);
        Button reset = button(PriceMode.INHERIT_NATIVE);
        Button disable = button(PriceMode.DISABLED);
        assertEquals(78, reset.getWidth());
        assertEquals(78, disable.getWidth());
        assertTrue(reset.getX() + reset.getWidth() < disable.getX());
        assertTrue(disable.getX() + disable.getWidth() <= 168);
        assertFalse(button(PriceMode.BUY_ONLY).visible);
        assertFalse(button(PriceMode.SELL_ONLY).visible);
        assertFalse(button(PriceMode.CUSTOM_VALUE).visible);
        button(PriceMode.CUSTOM_VALUE).onPress();
        button(PriceMode.BUY_ONLY).onPress();
        verifyNoInteractions(channel);
        button(PriceMode.DISABLED).onPress();
        var capture = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(channel).sendToServer(capture.capture());
        assertInstanceOf(C2SPlayerActionPacket.class, capture.getValue());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            C2SPlayerActionPacket.encode((C2SPlayerActionPacket) capture.getValue(), buffer);
            assertEquals(1, buffer.readByte());
            assertEquals("minecraft:diamond", buffer.readUtf(256));
            assertEquals("0", buffer.readUtf(4096));
        } finally { buffer.release(); }
    }

    @Test void itemResetAndGasBuyUseTheExistingMediaProtocol() throws Exception {
        select(PriceEditorMenu.KIND_ITEM, "minecraft:diamond", true);
        button(PriceMode.INHERIT_NATIVE).onPress();
        verify(channel).sendToServer(new C2SSetMediaPricePacket(0, PriceEditorMenu.KIND_ITEM, "minecraft:diamond", "inherit"));
        clearInvocations(channel);
        select(PriceEditorMenu.KIND_GAS, "mekanism:antimatter", true);
        for (PriceMode mode : PriceMode.values()) {
            Button control = button(mode);
            assertEquals(30, control.getWidth());
            assertTrue(control.getY() >= 90);
            assertTrue(control.getY() + control.getHeight() < 118);
            assertTrue(control.getX() >= 8 && control.getX() + control.getWidth() <= 169);
        }
        button(PriceMode.BUY_ONLY).onPress();
        verify(channel).sendToServer(new C2SSetMediaPricePacket(0, PriceEditorMenu.KIND_GAS, "mekanism:antimatter", "buy_only"));
    }

    @Test void restoringCustomModeUsesSavedPriceAndWaitsForConfirmation() throws Exception {
        state.set(new S2CPriceEditorStatePacket(0, PriceEditorMenu.KIND_FLUID, "minecraft:water", "0", "DISABLED", true,
                S2CPriceEditorStatePacket.Status.CURRENT));
        call("syncSelectionUi");
        assertTrue(button(PriceMode.CUSTOM_VALUE).active);
        button(PriceMode.CUSTOM_VALUE).onPress();
        verify(channel).sendToServer(new C2SSetMediaPricePacket(0, PriceEditorMenu.KIND_FLUID, "minecraft:water", "custom_value"));
        verify(input, never()).getValue();
        call("syncSelectionUi");
        assertFalse(button(PriceMode.CUSTOM_VALUE).active);
        assertEquals("DISABLED", state.get().mode());
    }

    @Test void missingQuoteAndDeniedPermissionNeverSubmitModes() throws Exception {
        state.set(null);
        call("syncSelectionUi");
        assertFalse(button(PriceMode.DISABLED).active);
        button(PriceMode.DISABLED).onPress();
        button(PriceMode.CUSTOM_VALUE).onPress();
        verifyNoInteractions(channel);
        select(PriceEditorMenu.KIND_FLUID, "minecraft:water", false);
        assertFalse(button(PriceMode.DISABLED).active);
        button(PriceMode.DISABLED).onPress();
        button(PriceMode.CUSTOM_VALUE).onPress();
        verifyNoInteractions(channel);
    }
}
