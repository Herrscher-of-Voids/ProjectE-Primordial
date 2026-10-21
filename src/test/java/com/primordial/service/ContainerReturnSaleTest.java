/**
 * 【文件职责】验证容器成交时返还内容与空壳计费，及自动化不得复制内容。
 * 【变更记录 #1】2026-10-02 19:36:05（第 1 次备注 · 1.2.0-transmutation-nbt）
 * - 删除：无。
 * - 新增：消费原容器、返还、学习和结算顺序；零价不成交及生成样本保护。
 * - 修改：真实返还服务执行，玩家和网络账户使用替身。
 * - 用途：只按空壳 EMC 入账，内部物品不重复学习或结算。
 */
package com.primordial.service;

import com.primordial.machine.AdapterTestBootstrap;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContainerReturnSaleTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @Test void originalIsConsumedThenContentsReturnedAndOnlyEmptyShellIsLearnedAndPaid() throws Exception {
        var player = mock(ServerPlayer.class);
        var inventory = mock(Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        var table = mock(TransmutationInventory.class);
        var playerField = TransmutationInventory.class.getDeclaredField("player");
        playerField.setAccessible(true); playerField.set(table, player);
        var consume = mock(Runnable.class);
        try (var prices = mockStatic(EMCHelper.class)) {
            prices.when(() -> EMCHelper.getEmcSellValue(any(ItemStack.class))).thenAnswer(call -> {
                ItemStack shell = call.getArgument(0);
                assertFalse(shell.getOrCreateTag().contains("BlockEntityTag"));
                return 2048L;
            });
            assertTrue(TransmutationContainerReturn.sell(table, filled(), consume));
            var order = inOrder(consume, inventory, table);
            order.verify(consume).run();
            order.verify(inventory).placeItemBackInInventory(argThat(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 32));
            order.verify(table).handleKnowledge(argThat((ItemStack stack) -> stack.is(Items.SHULKER_BOX)
                    && !stack.getOrCreateTag().contains("BlockEntityTag")));
            order.verify(table).addEmc(BigInteger.valueOf(2048));
            order.verifyNoMoreInteractions();
        }
    }
    @Test void zeroPricedShellDoesNotConsumeOrReturnAnything() throws Exception {
        var table = mock(TransmutationInventory.class);
        var player = mock(ServerPlayer.class);
        var field = TransmutationInventory.class.getDeclaredField("player");
        field.setAccessible(true); field.set(table, player);
        var consume = mock(Runnable.class);
        try (var prices = mockStatic(EMCHelper.class)) {
            assertFalse(TransmutationContainerReturn.sell(table, filled(), consume));
            verifyNoInteractions(consume, player, table);
        }
    }
    @Test void automationCannotGenerateFilledContainersFromEmptyShellKnowledge() {
        assertFalse(TransmutationNbtPolicy.canGenerate(filled()));
        assertTrue(TransmutationNbtPolicy.canGenerate(new ItemStack(Items.SHULKER_BOX)));
        var named = new ItemStack(Items.CHEST);
        named.setHoverName(net.minecraft.network.chat.Component.literal("Named empty chest"));
        assertTrue(TransmutationNbtPolicy.canGenerate(named));
    }
    private static ItemStack filled() {
        var box = new ItemStack(Items.SHULKER_BOX);
        var contents = new ListTag();
        contents.add(new ItemStack(Items.DIAMOND, 32).save(new CompoundTag()));
        box.getOrCreateTagElement("BlockEntityTag").put("Items", contents);
        return box;
    }
}
