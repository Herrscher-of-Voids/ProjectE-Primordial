/**
 * 【文件职责】验证真实槽充电的独立副本与生产 EMC 结算。
 * 【变更记录 #1】2026-10-12 23:43:00（第 1 次备注 · 1.2.0-media-real-slots）
 * - 删除：无。新增：限速持续充电、部分接收、执行异常、未知知识和无余额回归。
 * - 修改：复用生产结算及真实 EnergyStorage，仅账户、容器和槽的外部边界用替身。
 * - 用途：充电失败不写回副本，重复交易与实际接收量按分数精确计费。
 */
package com.primordial.machine;

import com.primordial.service.*;
import com.primordial.energy.EnergyConversions;
import com.primordial.config.PrimordialServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MediaSlotTransactionsTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    private ItemStack battery(int initial, int accepted, boolean fail) {
        ItemStack stack = mock(ItemStack.class);
        EnergyStorage energy = new EnergyStorage(1000, 100, 0, initial) {
            @Override public int receiveEnergy(int amount, boolean simulate) {
                int moved = super.receiveEnergy(simulate ? amount : Math.min(amount, accepted), simulate);
                if (!simulate && fail) throw new IllegalStateException("detached battery failed");
                return moved;
            }
        };
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getCount()).thenReturn(1);
        when(stack.getCapability(ForgeCapabilities.ENERGY)).thenReturn(LazyOptional.of(() -> energy));
        when(stack.copy()).thenAnswer(call -> battery(energy.getEnergyStored(), accepted, fail));
        return stack;
    }

    private void run(int calls, int accepted, boolean fail, boolean known, BigInteger balance, int expected) throws Exception {
        try (var account = new EmcSettlementTest.Account(balance);
             var config = mockStatic(PrimordialServerConfig.class);
             var knowledge = mockStatic(ResourceKnowledge.class)) {
            ServerPlayer player = mock(ServerPlayer.class);
            var field = ServerPlayer.class.getDeclaredField("server");
            field.setAccessible(true); field.set(player, account.server);
            when(player.getUUID()).thenReturn(account.owner);
            knowledge.when(() -> ResourceKnowledge.allows(account.server, account.owner, ResourceKnowledge.Kind.ENERGY))
                    .thenReturn(known);
            account.emc.when(() -> EmcService.energyPrice(EnergyConversions.FE)).thenReturn(EmcAmount.parse("1/1000"));
            ItemStack original = battery(0, accepted, fail);
            var current = new AtomicReference<>(original);
            Slot slot = mock(Slot.class);
            when(slot.getItem()).thenAnswer(call -> current.get());
            doAnswer(call -> { current.set(call.getArgument(0)); return null; }).when(slot).set(any());
            EmcAmount before = account.total();
            for (int i = 0; i < calls; i++) MediaSlotTransactions.charge(player, slot);
            assertEquals(expected, current.get().getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).getEnergyStored());
            assertEquals(before.subtract(EmcAmount.parse("1/1000").multiply(expected)), account.total());
            assertEquals(0, original.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).getEnergyStored());
            if (expected == 0) assertSame(original, current.get());
        }
    }

    @Test void rateLimitedChargingContinuesUntilFullWithoutExtraFees() throws Exception {
        run(15, 100, false, true, BigInteger.TEN, 1000);
    }
    @Test void actualAcceptanceDeterminesTheFractionalCost() throws Exception {
        run(3, 7, false, true, BigInteger.TEN, 21);
    }
    @Test void detachedCapabilityFailureRefundsAndPreservesOriginal() throws Exception {
        run(1, 100, true, true, BigInteger.TEN, 0);
    }
    @Test void unknownKnowledgeNeverCharges() throws Exception {
        run(1, 100, false, false, BigInteger.TEN, 0);
    }
    @Test void emptyAccountNeverChangesContainer() throws Exception {
        run(1, 100, false, true, BigInteger.ZERO, 0);
    }
}
