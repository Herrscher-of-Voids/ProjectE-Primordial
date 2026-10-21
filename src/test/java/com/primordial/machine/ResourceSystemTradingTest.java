/**
 * 【文件职责】执行第 1 系统的转化桌容器交易、结算、交付和失败回滚集成回归。
 * 【变更记录 #1】2026-10-10 03:58:22（第 1 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：真实 Forge 桶/限流储罐、气体、FE、堆叠背包和精确找零链路测试。
 * - 修改：仅启动、玩家菜单和知识/价表边界用替身，执行真实事务与库存操作。
 * - 用途：验证实际转移计费与原容器隔离，不将纯 JUnit 结果当作游戏视觉验收。
 */
/**
 * 【变更记录 #2】2026-10-10 04:07:17（第 2 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：机器独立资源模拟/结算、背包只剩可合并槽、气体堆叠提交失败及过期/错误菜单请求回归。
 * - 修改：继续运行真实交易及背包实现，回包边界只模拟网络发送。
 * - 用途：覆盖第 1 系统跨容器和自动化的事务边界，避免背包失败学习、越权菜单成交或模拟数量误收费。
 */
/**
 * 【变更记录 #3】2026-10-10 04:08:38（第 3 次备注 · 1.2.0-resource-system-one）
 * - 删除：对读取 unit() 次数的脆弱测试断言。
 * - 新增：无。
 * - 修改：顺序校验仅关注模拟先于执行，结算与数量断言保留。
 * - 用途：让测试约束真实事务行为，避免把无关内部调用次数当作功能失败。
 */
/**
 * 【变更记录 #4】2026-10-10 04:16:28（第 4 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：同时具有 FE 能力的满气罐不能自动转向充电的回归。
 * - 修改：模拟玩家边界，运行真实满容量气罐和能源容器，断言账户及两种资源均不变。
 * - 用途：整体验收要求失败不悄悄变成另一类别交易，精确反馈也必须对应实际请求。
 */
/**
 * 【变更记录 #5】2026-10-10 04:18:06（第 5 次备注 · 1.2.0-resource-system-one）
 * - 删除：无。
 * - 新增：FE 黑名单在黑洞能力声明、模拟和执行阶段均拒收的回归。
 * - 修改：运行真实 VoidEnergyHandler，明确断言黑洞预算/队列无调用和账户不变。
 * - 用途：避免新增统一 FE 黑名单带来只改价格却仍销毁资源的系统回归。
 */
package com.primordial.machine;

import com.primordial.service.*;
import com.primordial.compat.MekanismGasCompat;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.energy.EnergyConversions;
import com.primordial.value.ValueManager;
import com.primordial.value.PriceMode;
import com.primordial.network.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.minecraftforge.energy.EnergyStorage;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigInteger;

class ResourceSystemTradingTest {
    @BeforeAll static void init() { AdapterTestBootstrap.initialize(); }

    @Test void stackedBucketsPurchaseOneAndDeliverToInventoryWithoutChangingRemainingStack() throws Exception {
        try (var f = new Fixture(10000, Items.BUCKET, 16)) {
            f.bucket();
            var result = f.buy("minecraft:water");
            assertTrue(result.traded());
            assertEquals(1000, result.units());
            assertEquals("1/3", result.emc());
            assertEquals(15, f.menu.getCarried().getCount());
            assertTrue(f.inventory.getItem(0).is(Items.WATER_BUCKET));
            assertEquals(EmcAmount.of(BigInteger.valueOf(10000)).subtract(EmcAmount.parse("1/3")), f.account.total());
            assertEquals(16, f.original.getCount());
        }
    }

    @Test void fullInventoryRefundsAndKeepsOriginalStack() throws Exception {
        try (var f = new Fixture(10000, Items.BUCKET, 16)) {
            f.bucket();
            for (int i = 0; i < 36; i++) f.inventory.setItem(i, new ItemStack(Items.STONE, 64));
            assertEquals(ResourceTradeResult.Status.INVENTORY_FULL, f.buy("minecraft:water").status());
            assertSame(f.original, f.menu.getCarried());
            assertEquals(16, f.original.getCount());
            assertEquals(EmcAmount.of(BigInteger.valueOf(10000)), f.account.total());
            for (int i = 0; i < 36; i++) assertEquals(64, f.inventory.getItem(i).getCount());
        }
    }

    @Test void fullInventoryStillAllowsSingleContainerOnCursor() throws Exception {
        try (var f = new Fixture(10, Items.BUCKET, 1)) {
            f.bucket();
            for (int i = 0; i < 36; i++) f.inventory.setItem(i, new ItemStack(Items.STONE, 64));
            assertTrue(f.buy("minecraft:water").traded());
            assertTrue(f.menu.getCarried().is(Items.WATER_BUCKET));
        }
    }

    @Test void waterAndLavaSaleReturnEmptyBucketAndLearnOnlyActualResource() throws Exception {
        for (Item item : java.util.List.of(Items.WATER_BUCKET, Items.LAVA_BUCKET)) {
            try (var f = new Fixture(0, item, 1)) {
                f.bucket();
                var result = f.sell();
                assertTrue(result.traded());
                assertTrue(f.menu.getCarried().is(Items.BUCKET));
                assertEquals(1000, result.units());
                assertEquals(EmcAmount.parse("1/3"), f.account.total());
                ResourceLocation id = new ResourceLocation(item == Items.WATER_BUCKET ? "minecraft:water" : "minecraft:lava");
                f.knowledge.verify(() -> ResourceKnowledge.learnMedia(f.account.server, f.account.owner, ResourceKnowledge.Kind.FLUID, id));
            }
        }
    }

    @Test void partialFillChargesOnlyExecutedAmountAndPersistsFraction() throws Exception {
        try (var f = new Fixture(1, Items.POTION, 1)) {
            var tank = f.tank(5000, 37, false);
            var result = f.buy("minecraft:water");
            assertTrue(result.traded());
            assertEquals(37, result.units());
            assertEquals(37, tank.getFluidAmount());
            assertEquals(EmcAmount.ONE.subtract(EmcAmount.parse("37/3000")), f.account.total());
            f.account.reload();
            assertEquals(EmcAmount.ONE.subtract(EmcAmount.parse("37/3000")), f.account.total());
        }
    }

    @Test void insufficientBalanceBuysOnlyAffordableMillibuckets() throws Exception {
        try (var f = new Fixture(1, Items.POTION, 1)) {
            var tank = f.tank(10000, 10000, false);
            var result = f.buy("minecraft:water");
            assertEquals(3000, result.units());
            assertEquals(3000, tank.getFluidAmount());
            assertEquals(EmcAmount.ZERO, f.account.total());
        }
    }

    @Test void noBalanceOrUnknownResourceDoesNotMutateOrCharge() throws Exception {
        try (var f = new Fixture(0, Items.BUCKET, 1)) {
            f.bucket();
            assertEquals(ResourceTradeResult.Status.NO_FUNDS, f.buy("minecraft:water").status());
            assertSame(f.original, f.menu.getCarried());
            f.denyKnowledge();
            assertEquals(ResourceTradeResult.Status.UNKNOWN, f.buy("minecraft:water").status());
            assertEquals(EmcAmount.ZERO, f.account.total());
        }
    }

    @Test void blockedZeroWrongResourceAndSellOnlyNeverDeliver() throws Exception {
        try (var f = new Fixture(10, Items.BUCKET, 1)) {
            f.bucket();
            assertEquals(ResourceTradeResult.Status.INVALID_RESOURCE, f.buy("missing:water").status());
            assertEquals(ResourceTradeResult.Status.INVALID_RESOURCE, f.buy("minecraft:flowing_water").status());
            f.values.when(() -> ValueManager.fluidMode(new ResourceLocation("minecraft:water"))).thenReturn(PriceMode.SELL_ONLY);
            assertEquals(ResourceTradeResult.Status.DISABLED, f.buy("minecraft:water").status());
            f.values.when(() -> ValueManager.fluidMode(new ResourceLocation("minecraft:water"))).thenReturn(PriceMode.CUSTOM_VALUE);
            f.values.when(() -> ValueManager.fluidPricePerMB(Fluids.WATER)).thenReturn(EmcAmount.ZERO);
            assertEquals(ResourceTradeResult.Status.UNPRICED, f.buy("minecraft:water").status());
            assertSame(f.original, f.menu.getCarried());
            assertEquals(EmcAmount.parse("10"), f.account.total());
        }
    }

    @Test void incompatibleOrFullContainersNeverCharge() throws Exception {
        try (var f = new Fixture(10, Items.LAVA_BUCKET, 1)) {
            f.bucket();
            assertEquals(ResourceTradeResult.Status.FULL, f.buy("minecraft:water").status());
            assertSame(f.original, f.menu.getCarried());
            assertEquals(EmcAmount.parse("10"), f.account.total());
        }
    }

    @Test void detachedCapabilityFailureRefundsAndDoesNotDeliverMutatedCopy() throws Exception {
        try (var f = new Fixture(10, Items.POTION, 1)) {
            var tank = f.tank(1000, 50, true);
            assertFalse(f.buy("minecraft:water").traded());
            assertEquals(50, tank.getFluidAmount());
            assertSame(f.original, f.menu.getCarried());
            assertEquals(EmcAmount.parse("10"), f.account.total());
        }
    }

    @Test void energyContainerBuySellUsesOwnPriceAndEnergyKnowledge() throws Exception {
        try (var f = new Fixture(10, Items.REDSTONE, 1)) {
            var battery = new EnergyStorage(2000);
            doReturn(LazyOptional.of(() -> battery)).when(f.copy).getCapability(ForgeCapabilities.ENERGY);
            var result = f.buy("forge:fe");
            assertEquals(2000, result.units());
            assertEquals("2", result.emc());
            assertEquals(EmcAmount.parse("8"), f.account.total());
            // Fresh request begins from the delivered cursor capability.
            doReturn(f.copy).when(f.copy).copyWithCount(1);
            var sold = f.sell();
            assertEquals(2000, sold.units());
            assertEquals(0, battery.getEnergyStored());
            assertEquals(EmcAmount.parse("10"), f.account.total());
            f.knowledge.verify(() -> ResourceKnowledge.allows(f.account.server, f.account.owner, ResourceKnowledge.Kind.ENERGY));
            f.knowledge.verify(() -> ResourceKnowledge.learnMedia(any(), any(), any(), any()), never());
        }
    }

    @Test void energyBlacklistRejectsBothDirectionsBeforeTransfer() throws Exception {
        try (var f = new Fixture(10, Items.REDSTONE, 1)) {
            var battery = new EnergyStorage(1000);
            battery.receiveEnergy(500, false);
            doReturn(LazyOptional.of(() -> battery)).when(f.copy).getCapability(ForgeCapabilities.ENERGY);
            f.config.when(() -> PrimordialServerConfig.resourceBlocked(EnergyConversions.FE)).thenReturn(true);
            assertEquals(ResourceTradeResult.Status.DISABLED, f.buy("forge:fe").status());
            assertEquals(ResourceTradeResult.Status.DISABLED, f.sell().status());
            assertEquals(500, battery.getEnergyStored());
            assertEquals(EmcAmount.parse("10"), f.account.total());
        }
    }

    @Test void energyBlacklistMakesBlackHoleRejectSimulationAndExecutionWithoutConsumingBudget() throws Exception {
        try (var f = new Fixture(10, Items.REDSTONE, 1)) {
            f.config.when(() -> PrimordialServerConfig.resourceBlocked(EnergyConversions.FE)).thenReturn(true);
            var blackhole = mock(com.primordial.blockentity.BlackholeBlockEntity.class);
            var handler = new com.primordial.blockentity.VoidHandlers.VoidEnergyHandler(blackhole);
            assertFalse(handler.canReceive());
            assertEquals(0, handler.receiveEnergy(1000, true));
            assertEquals(0, handler.receiveEnergy(1000, false));
            verifyNoInteractions(blackhole);
            assertEquals(EmcAmount.parse("10"), f.account.total());
        }
    }

    @Test void gasContainerTailSaleAndEmptyPurchaseHaveExactActualSettlement() throws Exception {
        try (var f = new Fixture(10000, Items.POTION, 1); var registry = new GasTestRegistry();
             var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS)) {
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            var tank = new mekanism.common.capabilities.chemical.item.ChemicalTankRateLimitChemicalTank.GasTankRateLimitChemicalTank(
                    mekanism.common.tier.ChemicalTankTier.ULTIMATE, null);
            doReturn(LazyOptional.of(() -> tank)).when(f.copy).getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER);
            f.values.when(() -> ValueManager.gasValueEmcPerMB(registry.id)).thenReturn(BigInteger.valueOf(3));
            var bought = TransmutationMediaTrade.trade(f.player, true, true, registry.id);
            assertEquals(3333, bought.units());
            assertEquals(3333, tank.getStored());
            assertEquals(EmcAmount.ONE, f.account.total());
            doReturn(f.copy).when(f.copy).copyWithCount(1);
            assertEquals(3333, f.sell().units());
            assertTrue(tank.isEmpty());
            assertEquals(EmcAmount.parse("10000"), f.account.total());
            f.knowledge.verify(() -> ResourceKnowledge.learnMedia(f.account.server, f.account.owner, ResourceKnowledge.Kind.GAS, registry.id));
        }
    }

    @Test void fullGasContainerNeverFallsBackToChargingItsEnergyCapability() throws Exception {
        try (var f = new Fixture(10000, Items.POTION, 1); var registry = new GasTestRegistry();
             var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS)) {
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            var tank = new mekanism.common.capabilities.chemical.item.ChemicalTankRateLimitChemicalTank.GasTankRateLimitChemicalTank(
                    mekanism.common.tier.ChemicalTankTier.ULTIMATE, null);
            tank.setStack(new mekanism.api.chemical.gas.GasStack(registry.gas, 8192000));
            var battery = new EnergyStorage(2000);
            doReturn(LazyOptional.of(() -> tank)).when(f.copy).getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER);
            doReturn(LazyOptional.of(() -> battery)).when(f.copy).getCapability(ForgeCapabilities.ENERGY);
            f.values.when(() -> ValueManager.gasValueEmcPerMB(registry.id)).thenReturn(BigInteger.valueOf(3));
            assertEquals(ResourceTradeResult.Status.FULL, TransmutationMediaTrade.trade(f.player, true, false, null).status());
            assertEquals(0, battery.getEnergyStored());
            assertEquals(8192000, tank.getStored());
            assertEquals(EmcAmount.parse("10000"), f.account.total());
        }
    }

    @Test void optionalMekanismClassesCanBeAbsentFromTradingFacade() throws Exception {
        var code = MekanismGasCompat.class.getProtectionDomain().getCodeSource().getLocation();
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{code}, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("mekanism.")) throw new ClassNotFoundException(name);
                if (name.startsWith("com.primordial.compat.MekanismGasCompat")
                        || name.equals("com.primordial.compat.CarriedGasTrade")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name, resolve);
            }
        }) {
            var facade = loader.loadClass(MekanismGasCompat.class.getName());
            assertFalse((boolean) facade.getMethod("loaded").invoke(null));
            assertNotNull(facade.getMethod("tradeCarriedGas", ServerPlayer.class, ItemStack.class, boolean.class,
                    ResourceLocation.class, java.util.function.Predicate.class));
        }
    }

    @Test void responseRoundTripsHugeExactValueAndRejectsNegativeQuantity() {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            var message = new S2CMediaTradeResultPacket(19, true,
                    new ResourceTradeResult(ResourceTradeResult.Status.SUCCESS, "minecraft:water", Long.MAX_VALUE,
                            BigInteger.TEN.pow(4096).add(BigInteger.ONE) + "/3000"));
            S2CMediaTradeResultPacket.encode(message, buf);
            assertEquals(message, S2CMediaTradeResultPacket.decode(buf));
            buf.clear();
            S2CMediaTradeResultPacket.encode(new S2CMediaTradeResultPacket(19, true,
                    new ResourceTradeResult(ResourceTradeResult.Status.SUCCESS, "forge:fe", -1, "0")), buf);
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> S2CMediaTradeResultPacket.decode(buf));
        } finally { buf.release(); }
    }

    @Test void independentResourcePortSimulatesBeforeChargingAndUsesRealTransferQuantity() throws Exception {
        try (var f = new Fixture(10, Items.REDSTONE, 1)) {
            var port = mock(com.primordial.resource.EmcResourcePort.class);
            var unit = com.primordial.resource.ResourceExchangeConversions.BOTANIA_MANA;
            when(port.unit()).thenReturn(unit);
            when(port.canReceive()).thenReturn(true);
            when(port.canExtract()).thenReturn(true);
            when(port.receive(5000, true)).thenReturn(2000L);
            when(port.receive(2000, false)).thenReturn(37L);
            when(port.extract(5000, true)).thenReturn(37L);
            when(port.extract(37, false)).thenReturn(37L);
            f.knowledge.when(() -> ResourceKnowledge.allows(f.account.server, f.account.owner, unit)).thenReturn(true);
            f.config.when(PrimordialServerConfig::nativeResourceRatios).thenReturn(java.util.List.of());
            assertEquals(37, EmcService.supplyResource(f.account.server, f.account.owner, port, 5000));
            assertEquals(EmcAmount.parse("10").subtract(EmcAmount.parse("37/1000")), f.account.total());
            var order = inOrder(port);
            order.verify(port).receive(5000, true);
            order.verify(port).receive(2000, false);
            assertEquals(37, EmcService.reclaimResource(f.account.server, f.account.owner, port, 5000));
            assertEquals(EmcAmount.parse("10"), f.account.total());
            f.knowledge.when(() -> ResourceKnowledge.allows(f.account.server, f.account.owner, unit)).thenReturn(false);
            clearInvocations(port);
            assertEquals(0, EmcService.supplyResource(f.account.server, f.account.owner, port, 5000));
            verify(port, never()).receive(anyLong(), anyBoolean());
            f.config.when(() -> PrimordialServerConfig.resourceBlocked(unit)).thenReturn(true);
            assertEquals(0, EmcService.reclaimResource(f.account.server, f.account.owner, port, 5000));
            verify(port, never()).extract(anyLong(), anyBoolean());
        }
    }

    @Test void stackedResultCanMergeIntoOnlyRemainingInventorySpace() throws Exception {
        try (var f = new Fixture(0, Items.WATER_BUCKET, 3)) {
            f.bucket();
            for (int i = 0; i < 36; i++) f.inventory.setItem(i, new ItemStack(Items.STONE, 64));
            f.inventory.setItem(17, new ItemStack(Items.BUCKET, 15));
            assertTrue(f.sell().traded());
            assertEquals(2, f.menu.getCarried().getCount());
            assertEquals(16, f.inventory.getItem(17).getCount());
            assertEquals(EmcAmount.parse("1/3"), f.account.total());
        }
    }

    @Test void gasStackInventoryFailureDoesNotLearnOrCredit() throws Exception {
        try (var f = new Fixture(10, Items.POTION, 2); var registry = new GasTestRegistry();
             var compat = mockStatic(MekanismGasCompat.class, CALLS_REAL_METHODS)) {
            compat.when(MekanismGasCompat::loaded).thenReturn(true);
            var tank = new mekanism.common.capabilities.chemical.item.ChemicalTankRateLimitChemicalTank.GasTankRateLimitChemicalTank(
                    mekanism.common.tier.ChemicalTankTier.ULTIMATE, null);
            tank.setStack(new mekanism.api.chemical.gas.GasStack(registry.gas, 1));
            doReturn(LazyOptional.of(() -> tank)).when(f.copy).getCapability(mekanism.common.capabilities.Capabilities.GAS_HANDLER);
            f.values.when(() -> ValueManager.gasValueEmcPerMB(registry.id)).thenReturn(BigInteger.valueOf(3));
            for (int i = 0; i < 36; i++) f.inventory.setItem(i, new ItemStack(Items.STONE, 64));
            assertEquals(ResourceTradeResult.Status.INVENTORY_FULL, f.sell().status());
            assertSame(f.original, f.menu.getCarried());
            assertEquals(2, f.original.getCount());
            assertEquals(EmcAmount.parse("10"), f.account.total());
            f.knowledge.verify(() -> ResourceKnowledge.learnMedia(any(), any(), any(), any()), never());
        }
    }

    @Test void staleAndNonTableRequestsNeverTradeOrSendSuccess() {
        var player = mock(ServerPlayer.class);
        player.containerMenu = mock(TransmutationContainer.class);
        var context = mock(net.minecraftforge.network.NetworkEvent.Context.class);
        when(context.getSender()).thenReturn(player);
        when(context.enqueueWork(any())).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        });
        try (var trade = mockStatic(TransmutationMediaTrade.class); var replies = mockStatic(S2CMediaTradeResultPacket.class)) {
            C2SBuyCarriedMediaPacket.handle(new C2SBuyCarriedMediaPacket(1, false, "minecraft:water"), () -> context);
            C2SSellCarriedMediaPacket.handle(new C2SSellCarriedMediaPacket(1), () -> context);
            player.containerMenu = mock(net.minecraft.world.inventory.AbstractContainerMenu.class);
            C2SBuyCarriedMediaPacket.handle(new C2SBuyCarriedMediaPacket(0, false, "forge:fe"), () -> context);
            C2SSellCarriedMediaPacket.handle(new C2SSellCarriedMediaPacket(0), () -> context);
            trade.verifyNoInteractions();
            replies.verifyNoInteractions();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final EmcSettlementTest.Account account;
        final ServerPlayer player = mock(ServerPlayer.class);
        final TransmutationContainer menu = mock(TransmutationContainer.class);
        final Inventory inventory = new Inventory(player);
        final ItemStack original, copy;
        final org.mockito.MockedStatic<ResourceKnowledge> knowledge = mockStatic(ResourceKnowledge.class);
        final org.mockito.MockedStatic<ValueManager> values = mockStatic(ValueManager.class);
        final org.mockito.MockedStatic<PrimordialServerConfig> config = mockStatic(PrimordialServerConfig.class);

        Fixture(long balance, Item item, int count) throws Exception {
            account = new EmcSettlementTest.Account(BigInteger.valueOf(balance));
            var field = ServerPlayer.class.getDeclaredField("server"); field.setAccessible(true); field.set(player, account.server);
            player.containerMenu = menu;
            when(player.getUUID()).thenReturn(account.owner);
            when(player.getInventory()).thenReturn(inventory);
            original = spy(new ItemStack(item, count));
            copy = spy(new ItemStack(item, 1));
            doReturn(copy).when(original).copyWithCount(1);
            when(menu.getCarried()).thenReturn(original);
            doAnswer(call -> { when(menu.getCarried()).thenReturn(call.getArgument(0)); return null; }).when(menu).setCarried(any());
            doReturn(LazyOptional.empty()).when(copy).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            doReturn(LazyOptional.empty()).when(copy).getCapability(ForgeCapabilities.ENERGY);
            knowledge.when(() -> ResourceKnowledge.allowsMedia(any(), any(), any(), any())).thenReturn(true);
            knowledge.when(() -> ResourceKnowledge.allows(any(), any(), any(ResourceKnowledge.Kind.class))).thenReturn(true);
            values.when(() -> ValueManager.fluidPricePerMB(any())).thenReturn(EmcAmount.parse("1/3000"));
            config.when(PrimordialServerConfig::fePerEmc).thenReturn(BigInteger.valueOf(1000));
        }
        void denyKnowledge() {
            knowledge.when(() -> ResourceKnowledge.allowsMedia(any(), any(), any(), any())).thenReturn(false);
            knowledge.when(() -> ResourceKnowledge.allows(any(), any(), any(ResourceKnowledge.Kind.class))).thenReturn(false);
        }
        void bucket() {
            var handler = new FluidBucketWrapper(copy);
            doReturn(LazyOptional.of(() -> handler)).when(copy).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
        }
        FluidTank tank(int tankCapacity, int executedLimit, boolean failAfterMutation) {
            class ContainerTank extends FluidTank implements IFluidHandlerItem {
                ContainerTank() { super(tankCapacity); }
                @Override public ItemStack getContainer() { return copy; }
                @Override public int fill(FluidStack resource, FluidAction action) {
                    FluidStack limited = resource.copy();
                    if (action.execute()) limited.setAmount(Math.min(resource.getAmount(), executedLimit));
                    int actual = super.fill(limited, action);
                    if (action.execute() && failAfterMutation) throw new IllegalStateException("mutated detached copy");
                    return actual;
                }
            }
            var handler = new ContainerTank();
            doReturn(LazyOptional.of(() -> handler)).when(copy).getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM);
            return handler;
        }
        ResourceTradeResult buy(String id) { return TransmutationMediaTrade.trade(player, true, false, new ResourceLocation(id)); }
        ResourceTradeResult sell() { return TransmutationMediaTrade.trade(player, false, false, null); }
        @Override public void close() { config.close(); values.close(); knowledge.close(); account.close(); }
    }
}
