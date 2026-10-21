/**
 * 【文件职责】验证解绑的持久化结果和气体接收的真实能力合约。
 * 【变更记录 #1】2026-09-15 11:50:00（第 1 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：解绑空槽/有物品槽、过期菜单拒绝、气体模拟/执行/未知价格和 long 大请求回归。
 * - 修改：世界、服务和注册类型由替身提供，规则存档、气体数量和接收算法运行真实实现。
 * - 用途：验证不会重复补物品、误删实物或超额结算；不等同于实际整合包 GUI 测试。
 */
/**
 * 【变更记录 #2】2026-09-15 12:08:58（第 2 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：隐藏 Mekanism 类后的门面与空配置保存/重载检查。
 * - 修改：以独立类加载器同时加载配置包，保持包级访问权限；气体接收测试使用真实 GasStack 和真实预算。
 * - 用途：验证可选依赖缺失也能运行，解绑和大请求回收不会重建物品或超额计费。
 */
/**
 * 【变更记录 #2】2026-09-15 15:10:05（第 2 次备注 · 1.0.0-emc-service）
 * - 删除：黑洞气体队列期待整数参数的旧断言。
 * - 新增：统一服务气体报价后精确 EMC 队列断言。
 * - 修改：预算、模拟不变、long 上限及无价拒收断言保留。
 * - 用途：验证气体接收已接入统一服务，实际资源仍只消耗一次。
 */
/**
 * 【变更记录 #3】2026-10-14 00:02:00（第 3 次备注 · 1.2.0-discovery-learning-system）
 * - 删除：黑洞预算测试遗漏服务器主线程身份的替身设置。
 * - 新增：显式主线程返回值；修改：沿用原预算和大整数断言。
 * - 用途：正常交易回归符合实际 Forge 执行环境，异步拒收由 DiscoveryLearningTest 覆盖。
 */
package com.primordial.machine;

import com.primordial.blockentity.BlackholeBlockEntity;
import com.primordial.blockentity.MekanismGasSink;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.data.PlayerInventoryConfigSavedData;
import com.primordial.menu.PlayerInventoryMenu;
import com.primordial.network.C2SClearPlayerInventoryRulesPacket;
import com.primordial.service.EmcService;
import com.primordial.task.PlayerInventoryTask;
import com.primordial.value.ValueManager;
import mekanism.api.Action;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasBuilder;
import mekanism.api.chemical.gas.GasStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfiguratorFixesTest {
    @BeforeAll static void bootstrap() { AdapterTestBootstrap.initialize(); }

    @Test void unbindDeletesEmptyAndOccupiedRulesPermanentlyWithoutTouchingItems() {
        var player = mock(ServerPlayer.class);
        var menu = mock(PlayerInventoryMenu.class);
        var server = mock(MinecraftServer.class);
        var players = mock(PlayerList.class);
        var level = mock(ServerLevel.class);
        var inventory = new Inventory(player);
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        when(player.getUUID()).thenReturn(owner);
        when(player.getInventory()).thenReturn(inventory);
        when(player.level()).thenReturn(level);
        when(server.getPlayerList()).thenReturn(players);
        when(players.getPlayers()).thenReturn(List.of(player));
        player.containerMenu = menu;
        var data = new PlayerInventoryConfigSavedData();
        data.set(owner, 0, new ItemStack(Items.DIAMOND), 1, true);
        data.set(owner, 35, new ItemStack(Items.IRON_INGOT), 1, false);
        data.set(other, 0, new ItemStack(Items.DIAMOND), 1, true);
        inventory.setItem(35, new ItemStack(Items.IRON_INGOT, 7));
        var context = context(player);
        try (var stores = mockStatic(PlayerInventoryConfigSavedData.class, CALLS_REAL_METHODS);
             var emc = mockStatic(EmcService.class)) {
            stores.when(() -> PlayerInventoryConfigSavedData.get(player.server)).thenReturn(data);
            C2SClearPlayerInventoryRulesPacket.handle(new C2SClearPlayerInventoryRulesPacket(menu.containerId, new int[]{0, 35}), () -> context);
            var reloaded = PlayerInventoryConfigSavedData.load(data.save(new CompoundTag()));
            assertTrue(reloaded.rules(owner).isEmpty());
            assertNotNull(reloaded.get(other, 0));
            stores.when(() -> PlayerInventoryConfigSavedData.get(server)).thenReturn(reloaded);
            PlayerInventoryTask.run(server);
            assertTrue(inventory.getItem(0).isEmpty());
            assertEquals(7, inventory.getItem(35).getCount());
            verify(menu).broadcastChanges();
            emc.verifyNoInteractions();
        }
    }

    @Test void staleUnbindCannotDeleteRules() {
        var player = mock(ServerPlayer.class);
        var menu = mock(PlayerInventoryMenu.class);
        player.containerMenu = menu;
        try (var stores = mockStatic(PlayerInventoryConfigSavedData.class)) {
            C2SClearPlayerInventoryRulesPacket.handle(new C2SClearPlayerInventoryRulesPacket(menu.containerId + 1, new int[]{0}), () -> context(player));
            stores.verifyNoInteractions();
        }
    }

    @Test void gasFacadeAndEmptyConfigWorkWithoutOptionalModClasses() throws Exception {
        var code = com.primordial.compat.MekanismGasCompat.class.getProtectionDomain().getCodeSource().getLocation();
        try (var mods = mockStatic(net.minecraftforge.fml.ModList.class);
             var loader = new java.net.URLClassLoader(new java.net.URL[]{code}, getClass().getClassLoader()) {
                 @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                     if (name.startsWith("mekanism.") || name.startsWith("org.anti_ad.") || name.startsWith("yalter."))
                         throw new ClassNotFoundException(name);
                     if (name.startsWith("com.primordial.compat.MekanismGasCompat") || name.startsWith("com.primordial.data.")) {
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
            mods.when(net.minecraftforge.fml.ModList::get).thenReturn(mock(net.minecraftforge.fml.ModList.class));
            var facade = loader.loadClass("com.primordial.compat.MekanismGasCompat");
            assertEquals(false, facade.getMethod("isGas", Object.class).invoke(null, new Object()));
            assertEquals(false, facade.getMethod("hasGas", ItemStack.class).invoke(null, ItemStack.EMPTY));
            var sampleType = loader.loadClass("com.primordial.data.FluidSampleConfig");
            var sample = sampleType.getConstructor().newInstance();
            assertEquals(false, sampleType.getMethod("hasGasFilter").invoke(sample));
            var saved = (CompoundTag) sampleType.getMethod("save").invoke(sample);
            assertNotNull(sampleType.getMethod("load", CompoundTag.class).invoke(null, saved));
        }
    }

    @Test void gasSimulationAndHugeInputRespectBudgetAndUseBigIntegerSettlement() {
        var be = mock(BlackholeBlockEntity.class, CALLS_REAL_METHODS);
        var level = mock(ServerLevel.class);
        doReturn(level).when(be).getLevel();
        // 预算读取使用方块实体 level，设置真实引用但无需方块注册。
        be.setLevel(level);
        doReturn(UUID.randomUUID()).when(be).getOwner();
        when(level.getGameTime()).thenReturn(1L);
        var server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        when(level.getServer()).thenReturn(server);
        doNothing().when(be).queueEmc(any());
        doNothing().when(be).puffParticles();
        try (var registry = new GasTestRegistry(); var prices = mockStatic(ValueManager.class); var config = mockStatic(PrimordialServerConfig.class)) {
            var gas = registry.gas;
            var input = new GasStack(gas, Long.MAX_VALUE);
            var handler = new MekanismGasSink(be);
            BigInteger price = BigInteger.TEN.pow(40);
            prices.when(() -> ValueManager.gasValueEmcPerMB(gas.getRegistryName())).thenReturn(price);
            config.when(PrimordialServerConfig::blackholeMaxGasMbPerTick).thenReturn(100);
            assertEquals(Long.MAX_VALUE - 100, handler.insertChemical(0, input, Action.SIMULATE).getAmount());
            assertEquals(100, be.remainingGasBudget());
            verify(be, never()).queueEmc(any());
            assertEquals(Long.MAX_VALUE - 100, handler.insertChemical(0, input, Action.EXECUTE).getAmount());
            assertEquals(Long.MAX_VALUE, input.getAmount());
            verify(be).queueEmc(com.primordial.service.EmcAmount.of(price).multiply(100));
            assertEquals(Long.MAX_VALUE, handler.insertChemical(0, input, Action.EXECUTE).getAmount());
            when(level.getGameTime()).thenReturn(2L);
            assertTrue(handler.insertChemical(0, new GasStack(gas, 10), Action.EXECUTE).isEmpty());
            assertEquals(90, be.remainingGasBudget());
            prices.when(() -> ValueManager.gasValueEmcPerMB(gas.getRegistryName())).thenReturn(BigInteger.ZERO);
            assertEquals(10, handler.insertChemical(0, new GasStack(gas, 10), Action.EXECUTE).getAmount());
            assertFalse(handler.isValid(0, input));
            assertTrue(handler.extractChemical(0, 10, Action.EXECUTE).isEmpty());
            assertEquals(90, be.remainingGasBudget());
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
