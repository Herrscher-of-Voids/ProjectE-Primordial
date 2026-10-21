/**
 * 【文件职责】从真实 Screen 投放入口验证物品/流体路由，并验证服务器样本和存档一致。
 * 【变更记录 #1】2026-09-15 11:56:00（第 1 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：同坐标不同页的 JEI 投放、网络往返、NBT 重载、左键保留与右键清除测试。
 * - 修改：只替代世界和网络连接，使用真实菜单、包处理器、屏幕投放逻辑和 SavedData。
 * - 用途：防止 Rect2i 身份判断、隐藏页提前返回或服务端幽灵槽不同步再次丢失样本。
 */
/**
 * 【变更记录 #2】2026-09-15 12:08:58（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：依赖 Forge 事件转换器的真实网络通道初始化，以及 Mockito spy 复制菜单造成的内部槽归属差异。
 * - 新增：只模拟传输通道；真实菜单子类提供编辑校验和空目标机器。
 * - 修改：从真实 Screen 抓取投放包，执行真实编码/解码/服务端处理；检查样本 NBT、重开、左键保留和右键清空。
 * - 用途：确保测试覆盖用户实际触发的页面坐标复用和幽灵样本保存问题，而非仅检查序列化辅助函数。
 */
package com.primordial.machine;

import com.primordial.data.MachineConfig;
import com.primordial.data.MachineConfigSavedData;
import com.primordial.menu.ConfiguratorMenu;
import com.primordial.network.*;
import com.primordial.registry.ModMenus;
import com.primordial.screen.ConfiguratorScreen;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GhostIngredientPersistenceTest {
    private static final String KEY = "minecraft:overworld|0|64|0";
    private static net.minecraftforge.network.simple.SimpleChannel channel;

    @BeforeAll static void bootstrap() {
        AdapterTestBootstrap.initialize();
        // JUnit 没有 Forge 事件转换器；仅替代传输通道，包编码、解码和处理器仍运行真实逻辑。
        try (var registry = mockStatic(net.minecraftforge.network.NetworkRegistry.class)) {
            registry.when(() -> net.minecraftforge.network.NetworkRegistry.newSimpleChannel(any(), any(), any(), any()))
                    .thenReturn(mock(net.minecraftforge.network.simple.SimpleChannel.class));
            channel = PrimordialNetwork.CHANNEL;
        }
    }

    @Test void dropsReachOnlyTheActivePageAndPersistAfterReopen() throws Exception {
        var player = mock(ServerPlayer.class);
        var inventory = new Inventory(player);
        var server = mock(MinecraftServer.class, RETURNS_DEEP_STUBS);
        when(player.getServer()).thenReturn(server);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.level()).thenReturn(mock(net.minecraft.server.level.ServerLevel.class));
        var store = new MachineConfigSavedData();
        var config = store.getOrCreate(KEY, player.getUUID());
        var registryValue = RegistryObject.class.getDeclaredField("value");
        registryValue.setAccessible(true);
        Object previous = registryValue.get(ModMenus.CONFIGURATOR);
        registryValue.set(ModMenus.CONFIGURATOR, mock(MenuType.class));
        clearInvocations(channel);
        try (var stores = mockStatic(MachineConfigSavedData.class)) {
            stores.when(() -> MachineConfigSavedData.get(server)).thenReturn(store);
            stores.when(() -> MachineConfigSavedData.get(player.server)).thenReturn(store);
            var menu = new ConfiguratorMenu(7, inventory, KEY) {
                @Override public boolean canEdit(net.minecraft.world.entity.player.Player editor) { return editor == player; }
                @Override public net.minecraft.world.level.block.entity.BlockEntity targetMachine() { return null; }
            };
            player.containerMenu = menu;
            var screen = new ConfiguratorScreen(menu, inventory, Component.empty());
            ItemStack item = new ItemStack(Items.DIAMOND, 32);
            item.getOrCreateTag().putString("sample", "kept");
            var water = new FluidStack(Fluids.WATER, 1000);
            water.getOrCreateTag().putString("fluid_sample", "kept");
            var itemArea = screen.jeiSampleAreas(false).get(0);
            screen.acceptJeiIngredient(item, itemArea);
            menu.activeTab = 1;
            var fluidArea = screen.jeiSampleAreas(true).get(0);
            assertEquals(itemArea.getX(), fluidArea.getX());
            assertNotSame(itemArea, fluidArea);
            screen.acceptJeiIngredient(water, fluidArea);
            var capture = ArgumentCaptor.forClass(Object.class);
            verify(channel, times(2)).sendToServer(capture.capture());
            var context = context(player);
            for (Object packet : capture.getAllValues()) {
                var data = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    if (packet instanceof C2SSetItemFilterPacket itemPacket) {
                        C2SSetItemFilterPacket.encode(itemPacket, data);
                        C2SSetItemFilterPacket.handle(C2SSetItemFilterPacket.decode(data), () -> context);
                    } else if (packet instanceof C2SSetFluidFilterPacket fluidPacket) {
                        C2SSetFluidFilterPacket.encode(fluidPacket, data);
                        C2SSetFluidFilterPacket.handle(C2SSetFluidFilterPacket.decode(data), () -> context);
                    } else fail("Unexpected sample packet");
                } finally { data.release(); }
            }
            assertEquals(item.getTag(), menu.getSampleItem(0).getTag());
            assertEquals(1, menu.getSampleItem(0).getCount());
            assertEquals(water.getTag(), config.fluidIn[0].filter.getTag());
            var loaded = MachineConfig.load(config.save());
            assertEquals(item.getTag(), loaded.itemIn[0].filter.getTag());
            assertEquals(water.getTag(), loaded.fluidIn[0].filter.getTag());
            var reopened = new ConfiguratorMenu(8, inventory, KEY);
            assertEquals(item.getTag(), reopened.getSampleItem(0).getTag());
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(18, 0, ClickType.PICKUP, player);
            assertFalse(config.fluidIn[0].filter.isEmpty());
            menu.clicked(18, 1, ClickType.PICKUP, player);
            assertTrue(config.fluidIn[0].filter.isEmpty());
            assertFalse(config.fluidIn[0].enabled);
            menu.pickingTarget = true;
            assertTrue(screen.jeiSampleAreas(true).isEmpty());
        } finally { registryValue.set(ModMenus.CONFIGURATOR, previous); }
    }

    @Test void gasFilterRetainsIdentityAcrossNbtReload() {
        try (var mods = mockStatic(net.minecraftforge.fml.ModList.class);
             var registry = new GasTestRegistry()) {
            var modList = mock(net.minecraftforge.fml.ModList.class);
            when(modList.isLoaded("mekanism")).thenReturn(true);
            mods.when(net.minecraftforge.fml.ModList::get).thenReturn(modList);
            var config = new MachineConfig();
            config.fluidOut[8].gasFilter = new mekanism.api.chemical.gas.GasStack(registry.gas, 1);
            config.fluidOut[8].enabled = true;
            var loaded = MachineConfig.load(config.save());
            assertTrue(loaded.fluidOut[8].hasGasFilter());
            assertEquals(registry.id,
                    com.primordial.compat.MekanismGasCompat.gasId(loaded.fluidOut[8].gasFilter));
            assertTrue(loaded.fluidOut[8].enabled);
            assertTrue(loaded.fluidOut[8].filter.isEmpty());
        }
    }

    private static NetworkEvent.Context context(ServerPlayer player) {
        var context = mock(NetworkEvent.Context.class);
        when(context.getSender()).thenReturn(player);
        when(context.enqueueWork(any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        return context;
    }
}
