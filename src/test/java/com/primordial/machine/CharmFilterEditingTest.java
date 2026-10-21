/**
 * 【文件职责】验证护符编辑器、NBT持久化与只读库存。
 * 【变更记录 #1】2026-09-16 19:46:19（第 1 次备注 · 1.0.0-review-ready）
 * - 删除：无。
 * - 新增：选定字段精确类型、转义路径、完整NBT、黑名单及菜单重新打开验证。
 * - 修改：遍历所有ClickType确认不移动32件样本，换手持物后拒绝修改。
 * - 用途：证明护符筛选是可保存的真实规则，取样不会移动或丢弃实物。
 */
package com.primordial.machine;

import com.primordial.item.*;
import com.primordial.menu.BlackholeCharmMenu;
import com.primordial.network.*;
import com.primordial.registry.ModMenus;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 覆盖编辑到运行时匹配、重新打开及禁止移动玩家实物的完整菜单路径。 */
class CharmFilterEditingTest {
    @BeforeAll static void init() {
        AdapterTestBootstrap.initialize();
        try (var registry = mockStatic(net.minecraftforge.network.NetworkRegistry.class)) {
            registry.when(() -> net.minecraftforge.network.NetworkRegistry.newSimpleChannel(any(), any(), any(), any()))
                    .thenReturn(mock(net.minecraftforge.network.simple.SimpleChannel.class));
            assertNotNull(PrimordialNetwork.CHANNEL);
        }
    }
    @Test void selectedNbtPathsMatchExactlyAndPersistThroughItemNbtRoundTrip() {
        var charm = new ItemStack(Items.PAPER);
        var sample = new ItemStack(Items.DIAMOND);
        var data = new CompoundTag(); data.putInt("charge", 7); data.putString("name", "sample");
        sample.getOrCreateTag().put("a/b~c", data);
        assertTrue(CharmFilterEditor.add(charm, sample, 1, false, List.of("/a~1b~0c/charge")));
        var restored = ItemStack.of(charm.save(new CompoundTag()));
        var candidate = sample.copy(); candidate.getTag().getCompound("a/b~c").putString("name", "other");
        assertTrue(BlackholeCharmFilter.accepts(restored, candidate));
        candidate.getTag().getCompound("a/b~c").putLong("charge", 7L);
        assertFalse(BlackholeCharmFilter.accepts(restored, candidate));
        assertTrue(CharmFilterEditor.remove(restored, 1, false, 0));
        assertTrue(BlackholeCharmFilter.accepts(restored, candidate));
    }
    @Test void wholeTagAndBlacklistKeepExactSemantics() {
        var charm = new ItemStack(Items.PAPER); var sample = new ItemStack(Items.DIAMOND);
        sample.getOrCreateTag().putInt("charge", 7);
        assertTrue(CharmFilterEditor.add(charm, sample, 1, false, List.of()));
        var candidate = sample.copy(); candidate.getOrCreateTag().putBoolean("extra", true);
        assertFalse(BlackholeCharmFilter.accepts(charm, candidate));
        assertTrue(CharmFilterEditor.add(charm, sample, 0, true, List.of()));
        assertFalse(BlackholeCharmFilter.accepts(charm, sample));
    }
    @Test void menuSamplesWithoutMovingItemsSavesRulesAndRejectsChangedCharm() throws Exception {
        var value = RegistryObject.class.getDeclaredField("value"); value.setAccessible(true);
        var previous = value.get(ModMenus.BLACKHOLE_CHARM); value.set(ModMenus.BLACKHOLE_CHARM, mock(MenuType.class));
        try {
            var player = mock(ServerPlayer.class); var inventory = new Inventory(player);
            when(player.getInventory()).thenReturn(inventory); when(player.isAlive()).thenReturn(true);
            var charm = mock(ItemStack.class); var tag = new CompoundTag();
            when(charm.getItem()).thenReturn(mock(BlackholeCharmItem.class));
            when(charm.getTag()).thenReturn(tag); when(charm.getOrCreateTag()).thenReturn(tag);
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(charm);
            var original = new ItemStack(Items.DIAMOND, 32); inventory.setItem(9, original);
            var menu = new BlackholeCharmMenu(3, inventory, InteractionHand.MAIN_HAND); player.containerMenu = menu;
            for (ClickType click : ClickType.values()) menu.clicked(1, 0, click, player);
            assertSame(original, inventory.getItem(9)); assertEquals(32, original.getCount());
            assertEquals(1, menu.sample().getCount());
            assertTrue(menu.clickMenuButton(player, 4));
            assertTrue(BlackholeCharmFilter.accepts(charm, new ItemStack(Items.DIAMOND)));
            assertFalse(BlackholeCharmFilter.accepts(charm, new ItemStack(Items.IRON_INGOT)));
            var reopened = new BlackholeCharmMenu(4, inventory, InteractionHand.MAIN_HAND);
            assertEquals(1, reopened.rules().size());
            assertTrue(reopened.clickMenuButton(player, 1000)); assertTrue(reopened.rules().isEmpty());
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(ItemStack.EMPTY);
            assertFalse(reopened.stillValid(player)); assertFalse(reopened.clickMenuButton(player, 4));
            assertEquals(32, inventory.getItem(9).getCount());
        } finally { value.set(ModMenus.BLACKHOLE_CHARM, previous); }
    }
}
