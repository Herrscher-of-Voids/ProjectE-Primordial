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
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.item.IItemEmcHolder;
import moze_intel.projecte.capability.EmcHolderItemCapabilityWrapper;
import moze_intel.projecte.capability.ItemCapabilityWrapper;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.items.ItemPE;
import moze_intel.projecte.gameObjs.items.KleinStar;
import moze_intel.projecte.impl.capability.KnowledgeImpl;
import moze_intel.projecte.utils.EMCHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContainerReturnSaleTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }
    @Test void cursorDeliveryReturnsContentsAndLearnsAndPaysOnlyTheEmptyShell() throws Exception {
        try (var sale = new Sale(2048)) {
            var consume = mock(Runnable.class);
            assertTrue(TransmutationContainerReturn.sell(sale.table, filled(), consume));
            verify(consume).run();
            assertEquals(32, count(sale.inventory, Items.DIAMOND));
            assertEquals(BigInteger.valueOf(2048), sale.provider.getEmc());
            assertEquals(1, sale.provider.getKnowledge().size());
            ItemStack learned = sale.provider.getKnowledge().iterator().next().createStack();
            assertTrue(learned.is(Items.SHULKER_BOX));
            assertFalse(learned.getOrCreateTag().contains("BlockEntityTag"));
        }
    }

    @Test void zeroPricedShellDoesNotConsumeOrReturnAnything() throws Exception {
        try (var sale = new Sale(0)) {
            ItemStack source = filled();
            sale.inventory.setItem(0, source);
            var consume = mock(Runnable.class);
            assertFalse(TransmutationContainerReturn.sell(sale.table, source, consume));
            verifyNoInteractions(consume);
            assertSame(source, sale.inventory.getItem(0));
            assertEquals(0, count(sale.inventory, Items.DIAMOND));
            assertEquals(BigInteger.ZERO, sale.provider.getEmc());
        }
    }

    @Test void sharedHandlerIsDrainedOnceAndNestedContentsAndShellIdentityArePreservedSafely() throws Exception {
        try (var sale = new Sale(321)) {
            var storage = new ItemStackHandler(2);
            storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            storage.setStackInSlot(1, filled());
            ItemStack source = capabilityStack(storage);
            source.getOrCreateTag().putUUID("contentsUuid", UUID.randomUUID());
            ItemStack alias = source.copy();
            assertNotNull(TransmutationContainerReturn.prepare(source));
            assertEquals(3, storage.getStackInSlot(0).getCount());
            sale.inventory.setItem(0, source);
            assertTrue(TransmutationContainerReturn.sell(sale.table, source,
                    () -> sale.inventory.setItem(0, ItemStack.EMPTY)));
            assertTrue(alias.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new).getStackInSlot(0).isEmpty());
            assertTrue(storage.getStackInSlot(1).isEmpty());
            assertEquals(3, count(sale.inventory, Items.DIAMOND));
            ItemStack nested = sale.inventory.items.stream().filter(item -> item.is(Items.SHULKER_BOX)).findFirst().orElseThrow();
            assertEquals(32, nested.getOrCreateTagElement("BlockEntityTag").getList("Items", 10).getCompound(0).getByte("Count"));
            ItemStack learned = sale.provider.getKnowledge().iterator().next().createStack();
            assertFalse(learned.getOrCreateTag().contains("contentsUuid"));
            assertEquals(BigInteger.valueOf(321), sale.provider.getEmc());
        }
    }

    @Test void emptyWritableContainersCanBeStackedAndConsumeCannotEraseTheCapturedQuantity() throws Exception {
        try (var sale = new Sale(321)) {
            ItemStack source = capabilityStack(new ItemStackHandler(1));
            source.setCount(2);
            sale.inventory.setItem(0, source);
            assertTrue(TransmutationContainerReturn.sell(sale.table, source, () -> source.setCount(0)));
            assertEquals(BigInteger.valueOf(642), sale.provider.getEmc());
            assertTrue(sale.inventory.getItem(0).isEmpty());
        }
    }

    @Test void extractionFailureRestoresAllLiveSlotsWithoutReturningCopiesOrEmc() throws Exception {
        try (var sale = new Sale(321)) {
            var storage = new ItemStackHandler(2) {
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    ItemStack removed = super.extractItem(slot, amount, simulate);
                    if (slot == 1 && !simulate) throw new IllegalStateException("failed after extraction");
                    return removed;
                }
            };
            storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            storage.setStackInSlot(1, new ItemStack(Items.EMERALD, 2));
            ItemStack source = capabilityStack(storage);
            sale.inventory.setItem(0, source);
            assertFalse(TransmutationContainerReturn.sell(sale.table, source,
                    () -> sale.inventory.setItem(0, ItemStack.EMPTY)));
            assertSame(source, sale.inventory.getItem(0));
            assertEquals(3, storage.getStackInSlot(0).getCount());
            assertEquals(2, storage.getStackInSlot(1).getCount());
            assertEquals(0, count(sale.inventory, Items.DIAMOND));
            assertTrue(sale.provider.getKnowledge().isEmpty());
            assertEquals(BigInteger.ZERO, sale.provider.getEmc());
        }
    }

    @Test void reentryIsRejectedAndConsumptionAndPaymentFailuresRollBackStorageInventoryAndAccount() throws Exception {
        try (var sale = new Sale(321)) {
            var storage = new ItemStackHandler(1) {
                @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (!simulate) assertFalse(TransmutationContainerReturn.sell(sale.table, filled(), () -> fail("reentry consumed")));
                    return super.extractItem(slot, amount, simulate);
                }
            };
            storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            ItemStack source = capabilityStack(storage);
            sale.inventory.setItem(0, source);
            assertFalse(TransmutationContainerReturn.sell(sale.table, source, () -> {
                sale.inventory.setItem(0, ItemStack.EMPTY);
                throw new IllegalStateException("consume failed");
            }));
            assertEquals(3, storage.getStackInSlot(0).getCount());
            assertFalse(sale.inventory.getItem(0).isEmpty());
            ItemInfo prior = ItemInfo.fromStack(new ItemStack(Items.EMERALD));
            sale.provider.addKnowledge(prior);
            sale.provider.setEmc(BigInteger.valueOf(19));
            doAnswer(call -> {
                sale.provider.setEmc(BigInteger.valueOf(340));
                throw new IllegalStateException("payment failed after mutation");
            }).when(sale.table).addEmc(any(BigInteger.class));
            ItemStack restored = sale.inventory.getItem(0);
            assertFalse(TransmutationContainerReturn.sell(sale.table, restored,
                    () -> sale.inventory.setItem(0, ItemStack.EMPTY)));
            assertEquals(3, storage.getStackInSlot(0).getCount());
            assertEquals(1, sale.provider.getKnowledge().size());
            assertTrue(sale.provider.hasKnowledge(prior));
            assertEquals(BigInteger.valueOf(19), sale.provider.getEmc());
            assertEquals(0, count(sale.inventory, Items.DIAMOND));
        }
    }

    @Test void fullInventoryRejectsBeforeDrainingAndEnoughSpaceSplitsLargeStacksLegally() throws Exception {
        try (var sale = new Sale(321)) {
            var storage = new ItemStackHandler(2);
            storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 128));
            storage.setStackInSlot(1, new ItemStack(Items.EMERALD));
            ItemStack source = capabilityStack(storage);
            for (int slot = 0; slot < sale.inventory.items.size(); slot++) sale.inventory.setItem(slot, new ItemStack(Items.STONE, 64));
            sale.inventory.setItem(0, source);
            assertFalse(TransmutationContainerReturn.sell(sale.table, source,
                    () -> fail("capacity failure consumed the source")));
            assertEquals(128, storage.getStackInSlot(0).getCount());
            assertSame(source, sale.inventory.getItem(0));
            assertEquals(BigInteger.ZERO, sale.provider.getEmc());
            sale.inventory.setItem(1, ItemStack.EMPTY);
            sale.inventory.setItem(2, ItemStack.EMPTY);
            assertTrue(TransmutationContainerReturn.sell(sale.table, source,
                    () -> sale.inventory.setItem(0, ItemStack.EMPTY)));
            assertEquals(128, count(sale.inventory, Items.DIAMOND));
            assertEquals(1, count(sale.inventory, Items.EMERALD));
            assertTrue(sale.inventory.items.stream().allMatch(item -> item.getCount() <= item.getMaxStackSize()));
            verify(sale.player, never()).drop(any(ItemStack.class), anyBoolean());
        }
    }

    @Test void failedPaymentRestoresChargedInputItemsAndTheLockSlotTogetherWithTheAccount() throws Exception {
        try (var sale = new Sale(321)) {
            var inputs = (net.minecraftforge.items.IItemHandlerModifiable) sale.provider.getInputAndLocks();
            // Bootstrap freezes the item registry; skip only the KleinStar constructor, retaining its real EMC methods.
            KleinStar starItem = mock(KleinStar.class, CALLS_REAL_METHODS);
            ItemStack star = spy(new ItemStack(Items.NETHER_STAR));
            doReturn(starItem).when(star).getItem();
            doAnswer(call -> {
                ItemStack copy = new ItemStack(Items.NETHER_STAR, star.getCount());
                copy.setTag(star.getTag() == null ? null : star.getTag().copy());
                return copy;
            }).when(star).copy();
            ItemPE.setEmc(star, 17);
            var holder = new EmcHolderItemCapabilityWrapper();
            new ItemCapabilityWrapper(star, holder);
            doReturn(LazyOptional.of(() -> holder)).when(star).getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY);
            sale.prices.when(() -> EMCHelper.getKleinStarMaxEmc(any(ItemStack.class))).thenReturn(50_000L);
            inputs.setStackInSlot(0, star);
            inputs.setStackInSlot(8, new ItemStack(Items.EMERALD));
            ItemStack source = filled();
            sale.inventory.setItem(0, source);
            doCallRealMethod().when(sale.table).addEmc(any(BigInteger.class));
            doAnswer(call -> {
                assertEquals(338, holder.getStoredEmc(star));
                inputs.setStackInSlot(8, ItemStack.EMPTY);
                throw new IllegalStateException("charged inputs before sync failed");
            }).when(sale.table).syncChangedSlots(anyList(), any(IKnowledgeProvider.TargetUpdateType.class));
            assertFalse(TransmutationContainerReturn.sell(sale.table, source,
                    () -> sale.inventory.setItem(0, ItemStack.EMPTY)));
            assertEquals(17, ItemPE.getEmc(inputs.getStackInSlot(0)));
            assertTrue(inputs.getStackInSlot(8).is(Items.EMERALD));
            assertTrue(sale.inventory.getItem(0).is(Items.SHULKER_BOX));
            assertEquals(0, count(sale.inventory, Items.DIAMOND));
            assertEquals(BigInteger.ZERO, sale.provider.getEmc());
            assertTrue(sale.provider.getKnowledge().isEmpty());
            verify(sale.provider).sync(sale.player);
        }
    }

    @Test void unknownExternalEmcHolderRejectsBeforeDrainButTheLockSlotDoesNotBlockSale() throws Exception {
        try (var sale = new Sale(321)) {
            var inputs = (net.minecraftforge.items.IItemHandlerModifiable) sale.provider.getInputAndLocks();
            ItemStack external = spy(new ItemStack(Items.NETHER_STAR));
            IItemEmcHolder holder = mock(IItemEmcHolder.class);
            doReturn(LazyOptional.of(() -> holder)).when(external).getCapability(PECapabilities.EMC_HOLDER_ITEM_CAPABILITY);
            inputs.setStackInSlot(0, external);
            var storage = new ItemStackHandler(1);
            storage.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            ItemStack source = capabilityStack(storage);
            sale.inventory.setItem(0, source);
            var consume = mock(Runnable.class);
            assertFalse(TransmutationContainerReturn.sell(sale.table, source, consume));
            verifyNoInteractions(consume, holder);
            assertEquals(3, storage.getStackInSlot(0).getCount());
            assertSame(source, sale.inventory.getItem(0));
            assertEquals(BigInteger.ZERO, sale.provider.getEmc());
            inputs.setStackInSlot(0, ItemStack.EMPTY);
            inputs.setStackInSlot(8, external);
            assertTrue(TransmutationContainerReturn.sell(sale.table, source,
                    () -> sale.inventory.setItem(0, ItemStack.EMPTY)));
            assertTrue(storage.getStackInSlot(0).isEmpty());
            assertEquals(BigInteger.valueOf(321), sale.provider.getEmc());
        }
    }

    @Test void linkageFailureWhileRestoringAnUpgradeStillRestoresOtherSlotsAndMainInventory() {
        var main = new ItemStackHandler(1);
        boolean[] fail = {false};
        var upgrades = new ItemStackHandler(2) {
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                if (fail[0] && slot == 0) throw new NoClassDefFoundError("optional upgrade class missing");
                super.setStackInSlot(slot, stack);
            }
        };
        main.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        upgrades.setStackInSlot(0, new ItemStack(Items.EMERALD));
        upgrades.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
        ContainerItemStorage snapshot = ContainerItemStorage.snapshot(new ItemStack(Items.STICK), List.of(main, upgrades));
        assertNotNull(snapshot);
        assertTrue(snapshot.drain());
        fail[0] = true;
        IllegalStateException error = assertThrows(IllegalStateException.class, snapshot::restore);
        assertInstanceOf(LinkageError.class, error.getCause());
        assertEquals(2, upgrades.getStackInSlot(1).getCount());
        assertEquals(3, main.getStackInSlot(0).getCount());
    }

    @Test void bothMainStorageAndUpgradeSlotsDrainAndUpgradeSlotsRestoreFirstOnFailure() {
        var main = new ItemStackHandler(1);
        var upgrades = new ItemStackHandler(1);
        main.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        upgrades.setStackInSlot(0, new ItemStack(Items.EMERALD));
        ContainerItemStorage snapshot = ContainerItemStorage.snapshot(new ItemStack(Items.STICK), List.of(main, upgrades));
        assertNotNull(snapshot);
        assertEquals(2, snapshot.contents().size());
        assertTrue(snapshot.drain());
        assertTrue(main.getStackInSlot(0).isEmpty());
        assertTrue(upgrades.getStackInSlot(0).isEmpty());
        snapshot.restore();
        assertEquals(3, main.getStackInSlot(0).getCount());
        assertEquals(1, upgrades.getStackInSlot(0).getCount());

        var failingUpgrades = new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack extracted = super.extractItem(slot, amount, simulate);
                if (!simulate) throw new IllegalStateException("upgrade inventory failed after extraction");
                return extracted;
            }
        };
        failingUpgrades.setStackInSlot(0, new ItemStack(Items.EMERALD));
        var dependentMain = new ItemStackHandler(1) {
            @Override public void setStackInSlot(int slot, ItemStack stack) {
                assertFalse(failingUpgrades.getStackInSlot(0).isEmpty(), "upgrades must be restored first");
                super.setStackInSlot(slot, stack);
            }
        };
        dependentMain.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        ContainerItemStorage rejected = ContainerItemStorage.snapshot(new ItemStack(Items.STICK), List.of(dependentMain, failingUpgrades));
        assertNotNull(rejected);
        assertFalse(rejected.drain());
        assertEquals(3, dependentMain.getStackInSlot(0).getCount());
        assertEquals(1, failingUpgrades.getStackInSlot(0).getCount());
    }

    @Test void readOnlyAndStackedFilledHandlersAreRejectedWithoutMutation() {
        IItemHandler readOnly = mock(IItemHandler.class);
        when(readOnly.getSlots()).thenReturn(1);
        when(readOnly.getStackInSlot(0)).thenReturn(new ItemStack(Items.DIAMOND));
        assertNull(TransmutationContainerReturn.prepare(capabilityStack(readOnly)));
        var storage = new ItemStackHandler(1);
        storage.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        ItemStack stacked = capabilityStack(storage);
        stacked.setCount(2);
        assertNull(TransmutationContainerReturn.prepare(stacked));
        assertEquals(1, storage.getStackInSlot(0).getCount());
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

    private static int count(Inventory inventory, net.minecraft.world.item.Item item) {
        return inventory.items.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    // Copies intentionally share one handler, modelling UUID-backed storage instead of private stack NBT.
    private static ItemStack capabilityStack(IItemHandler handler) {
        ItemStack stack = spy(new ItemStack(Items.STICK));
        doReturn(LazyOptional.of(() -> handler)).when(stack).getCapability(ForgeCapabilities.ITEM_HANDLER);
        doAnswer(call -> {
            ItemStack copy = capabilityStack(handler);
            copy.setCount(stack.getCount());
            copy.setTag(stack.getTag() == null ? null : stack.getTag().copy());
            return copy;
        }).when(stack).copy();
        return stack;
    }

    private static final class Sale implements AutoCloseable {
        final ServerPlayer player = mock(ServerPlayer.class);
        final Inventory inventory = new Inventory(player);
        final TransmutationInventory table = mock(TransmutationInventory.class);
        final IKnowledgeProvider provider = spy(KnowledgeImpl.getDefault());
        final MockedStatic<EMCHelper> prices = mockStatic(EMCHelper.class);

        Sale(long price) throws Exception {
            when(player.getInventory()).thenReturn(inventory);
            for (String name : List.of("player", "provider", "inputLocks")) {
                var field = TransmutationInventory.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(table, switch (name) {
                    case "player" -> player;
                    case "provider" -> provider;
                    default -> provider.getInputAndLocks();
                });
            }
            doNothing().when(provider).sync(player);
            doAnswer(call -> { provider.addKnowledge((ItemStack) call.getArgument(0)); return null; })
                    .when(table).handleKnowledge(any(ItemStack.class));
            doAnswer(call -> {
                BigInteger amount = call.getArgument(0);
                provider.setEmc(provider.getEmc().add(amount));
                return null;
            }).when(table).addEmc(any(BigInteger.class));
            prices.when(() -> EMCHelper.getEmcSellValue(any(ItemStack.class))).thenReturn(price);
        }
        @Override public void close() { prices.close(); }
    }
}
