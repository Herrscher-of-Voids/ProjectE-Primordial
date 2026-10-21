/**
 * 【文件职责】验证加速器管理协议、持久化编辑和菜单身份边界。
 * 【变更记录 #1】2026-09-18 02:20:19（第 1 次备注 · 1.0.0-acceleration-modes）
 * - 删除：无。
 * - 新增：设置往返、非法参数、摘要大小、保存重开、旧窗口/换手持拒绝和库存不可交互测试。
 * - 修改：调用实际网络解码和菜单编辑入口；仅替换网络发送及世界查询。
 * - 用途：确认客户端不能借旧菜单修改另一把工具，几何及名单持久化且编辑后暂停。
 */
package com.primordial.network;

import com.primordial.item.EmcAcceleratorItem;
import com.primordial.machine.AdapterTestBootstrap;
import com.primordial.menu.AccelerationMenu;
import com.primordial.registry.ModMenus;
import com.primordial.service.*;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccelerationManagementTest {
    private static final UUID SELECTION = AccelerationBindingActions.SELECTION;
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    @BeforeAll static void bootstrap() {
        AdapterTestBootstrap.initialize();
        try (var registry = mockStatic(net.minecraftforge.network.NetworkRegistry.class)) {
            registry.when(() -> net.minecraftforge.network.NetworkRegistry.newSimpleChannel(any(), any(), any(), any()))
                    .thenReturn(mock(net.minecraftforge.network.simple.SimpleChannel.class));
            assertNotNull(PrimordialNetwork.CHANNEL);
        }
    }

    @Test void everyModeAndActionPreservesSettingsOnTheWire() {
        for (var kind : AccelerationSettings.Kind.values()) {
            for (var action : C2SAccelerationActionPacket.Action.values()) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    var settings = new AccelerationSettings(kind, 1024, 15, 1, 3, -15, 0, 15, true, true);
                    var request = new C2SAccelerationActionPacket(23, action, UUID.randomUUID(), settings, true, InteractionHand.OFF_HAND);
                    C2SAccelerationActionPacket.encode(request, buffer);
                    assertEquals(request, C2SAccelerationActionPacket.decode(buffer));
                    assertEquals(0, buffer.readableBytes());
                } finally { buffer.release(); }
            }
        }
    }

    @Test void malformedGeometryOrRateIsRejectedBeforeUse() {
        for (var settings : new AccelerationSettings[]{
                new AccelerationSettings(AccelerationSettings.Kind.AREA, 3, 3, 3, 3, 0, 0, 0, true, false),
                new AccelerationSettings(AccelerationSettings.Kind.AREA, 4, 16, 3, 3, 0, 0, 0, true, false),
                new AccelerationSettings(AccelerationSettings.Kind.AREA, 4, 3, 0, 3, 0, 0, 0, true, false),
                new AccelerationSettings(AccelerationSettings.Kind.AREA, 4, 3, 3, 3, Integer.MIN_VALUE, 0, 0, true, false)}) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                C2SAccelerationActionPacket.encode(new C2SAccelerationActionPacket(1,
                        C2SAccelerationActionPacket.Action.SAVE, SELECTION, settings, false, HAND), buffer);
                assertThrows(io.netty.handler.codec.DecoderException.class, () -> C2SAccelerationActionPacket.decode(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test void summarySupportsMaximumTargetsAndRejectsExcessOrOversizedNbt() {
        var state = new CompoundTag(); var targets = new ListTag();
        for (int i = 0; i < 256; i++) {
            var entry = new CompoundTag(); entry.putUUID("BindingId", UUID.randomUUID()); targets.add(entry);
        }
        state.put("Targets", targets);
        roundTrip(state, false);
        targets.add(new CompoundTag());
        roundTrip(state, true);
        state.remove("Targets"); state.putByteArray("TooLarge", new byte[S2CAccelerationStatePacket.MAX_BYTES]);
        roundTrip(state, true);
    }

    private static void roundTrip(CompoundTag state, boolean rejected) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new S2CAccelerationStatePacket(9, 2, SELECTION, state, "9".repeat(160), true);
            S2CAccelerationStatePacket.encode(packet, buffer);
            if (rejected) assertThrows(RuntimeException.class, () -> S2CAccelerationStatePacket.decode(buffer));
            else { assertEquals(packet, S2CAccelerationStatePacket.decode(buffer)); assertEquals(0, buffer.readableBytes()); }
        } finally { buffer.release(); }
    }

    @Test void settingsSurviveReopeningAndInventoryClicksCannotMoveRealItems() throws Exception {
        try (var f = new Fixture()) {
            var desired = new AccelerationSettings(AccelerationSettings.Kind.ENTITY, 16, 5, 3, 1, -2, 1, 0, false, true);
            f.send(C2SAccelerationActionPacket.Action.SAVE, SELECTION, desired);
            assertTrue(f.menu.accepted()); assertEquals(desired, AccelerationSettings.read(f.tag));
            var original = new ItemStack(Items.DIAMOND, 32); f.inventory.setItem(9, original);
            for (ClickType click : ClickType.values()) f.menu.clicked(9, 0, click, f.player);
            assertTrue(f.menu.slots.isEmpty()); assertSame(ItemStack.EMPTY, f.menu.quickMoveStack(f.player, 9));
            assertSame(original, f.inventory.getItem(9)); assertEquals(32, original.getCount());
            var reopened = new AccelerationMenu(10, f.inventory, HAND); reopened.broadcastChanges();
            assertEquals(desired, AccelerationSettings.read(reopened.selectedState()));
            assertEquals(desired, AccelerationSettings.read(f.tag.copy()));
        }
    }

    @Test void bindingEditPausesAndRemoveUsesStableIdentity() throws Exception {
        try (var f = new Fixture()) {
            var entry = new CompoundTag(); UUID id = UUID.randomUUID(); entry.putUUID("BindingId", id);
            var initial = new AccelerationSettings(AccelerationSettings.Kind.AREA, 4, 3, 3, 3, 0, 0, 0, true, false);
            initial.write(entry); entry.putBoolean("Enabled", true);
            entry.putLongArray("AreaMarks", new long[]{1, 2});
            f.tag.getList(AccelerationBindings.TARGETS_KEY, Tag.TAG_COMPOUND).add(entry);
            f.send(C2SAccelerationActionPacket.Action.SELECT, id, initial);
            var changed = new AccelerationSettings(AccelerationSettings.Kind.AREA, 8, 5, 3, 3, 1, 0, 0, true, false);
            f.send(C2SAccelerationActionPacket.Action.SAVE, id, changed);
            assertTrue(f.menu.accepted()); assertFalse(AccelerationBindings.enabled(entry));
            assertEquals(changed, AccelerationSettings.read(entry)); assertEquals(0, entry.getLongArray("AreaMarks").length);
            f.send(C2SAccelerationActionPacket.Action.REMOVE, id, changed);
            assertTrue(AccelerationBindings.view(f.tool).isEmpty()); assertEquals(SELECTION, f.menu.selected());
        }
    }

    @Test void staleContainerAndChangedHandCannotSave() throws Exception {
        try (var f = new Fixture()) {
            var changed = new AccelerationSettings(AccelerationSettings.Kind.ENTITY, 16, 3, 3, 3, 0, 0, 0, true, false);
            var original = f.tag.copy();
            C2SAccelerationActionPacket.apply(f.player, new C2SAccelerationActionPacket(99,
                    C2SAccelerationActionPacket.Action.SAVE, SELECTION, changed, false, HAND));
            assertEquals(original, f.tag);
            when(f.player.getItemInHand(HAND)).thenReturn(ItemStack.EMPTY);
            assertFalse(f.menu.stillValid(f.player));
            f.send(C2SAccelerationActionPacket.Action.SAVE, SELECTION, changed);
            assertEquals(original, f.tag);
        }
    }

    @Test void disabledDaylightRuleReturnsFailureWithoutCreatingABinding() throws Exception {
        try (var f = new Fixture()) {
            f.rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            f.send(C2SAccelerationActionPacket.Action.WORLD, SELECTION,
                    AccelerationSettings.read(f.tag).withKind(AccelerationSettings.Kind.DAYTIME));
            assertFalse(f.menu.accepted()); assertTrue(AccelerationBindings.view(f.tool).isEmpty());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final java.lang.reflect.Field registryValue;
        final Object oldMenuType;
        final MinecraftServer server = mock(MinecraftServer.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final Inventory inventory = new Inventory(player);
        final ItemStack tool = mock(ItemStack.class);
        final CompoundTag tag = new CompoundTag();
        final GameRules rules = new GameRules();
        final AccelerationMenu menu;

        Fixture() throws Exception {
            registryValue = RegistryObject.class.getDeclaredField("value"); registryValue.setAccessible(true);
            oldMenuType = registryValue.get(ModMenus.ACCELERATION); registryValue.set(ModMenus.ACCELERATION, mock(MenuType.class));
            var serverField = ServerPlayer.class.getDeclaredField("server"); serverField.setAccessible(true); serverField.set(player, server);
            when(player.getUUID()).thenReturn(UUID.randomUUID()); when(player.isAlive()).thenReturn(true);
            when(player.getInventory()).thenReturn(inventory); when(player.getItemInHand(HAND)).thenReturn(tool);
            when(tool.getItem()).thenReturn(mock(EmcAcceleratorItem.class)); when(tool.hasTag()).thenReturn(true);
            when(tool.getTag()).thenReturn(tag); when(tool.getOrCreateTag()).thenReturn(tag);
            var level = mock(ServerLevel.class); when(server.overworld()).thenReturn(level);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(level); when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(level.getGameRules()).thenReturn(rules); when(level.getLevelData()).thenReturn(mock(ServerLevelData.class));
            menu = new AccelerationMenu(9, inventory, HAND); player.containerMenu = menu;
        }
        void send(C2SAccelerationActionPacket.Action action, UUID id, AccelerationSettings settings) {
            C2SAccelerationActionPacket.apply(player, new C2SAccelerationActionPacket(menu.containerId, action, id, settings, false, HAND));
        }
        @Override public void close() throws Exception { registryValue.set(ModMenus.ACCELERATION, oldMenuType); }
    }
}
