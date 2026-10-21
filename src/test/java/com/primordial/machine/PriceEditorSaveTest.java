/**
 * 【文件职责】验证价格页从 JEI/背包样本、真实提交方法、网络处理到持久化和回显的完整链路。
 * 【变更记录 #1】2026-09-16 01:32:47（第 1 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：无；补充此前仅检查价格包往返、未执行屏幕提交的覆盖缺口。
 * - 新增：物品大整数、流体小数/分数、气体、零价、权限/目标/写盘失败和迟到回包回归。
 * - 修改：只替代连接、玩家世界、输入控件与 Forge 启动注册；菜单、屏幕提交、处理器和价表读写保持真实。
 * - 用途：证明价格保存后统一服务立即采用新值，重载仍保留；全部文件落在 JUnit 临时目录，结束恢复静态路径。
 */
/**
 * 【变更记录 #2】2026-09-16 01:35:10（第 2 次备注 · 1.0.0-price-editor-save-fix）
 * - 删除：无。
 * - 新增：验证另一路改价会刷新已打开菜单，随后连续 100 tick 不会重复同步。
 * - 修改：继续使用真实 ValueManager 发布和真实菜单广播检测，不手动伪造价格修订号。
 * - 用途：覆盖多人/命令改价后的显示一致性，并确认更新机制没有引入空闲发包开销。
 */
/**
 * 【变更记录 #3】2026-09-16 01:59:37（第 3 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：ProjectE 报价替身、独立物品快照同步及超 long 物品价格成功的断言。
 * - 新增：真实 CustomEMCMapper/EMCMappingHandler 重算、IEMCProxy 查询、原生文件重读和原生网络调用验证。
 * - 修改：只替代完整 Forge 环境中的燃料注册和连接；价格映射、NSS 编码、文件写入和查询均使用真实 ProjectE。
 * - 用途：验证改价确实进入转化桌使用的价表；超范围输入明确失败，BigInteger 余额由原有账户测试继续覆盖。
 */
/**
 * 【变更记录 #4】2026-10-02 15:15:00（第 4 次备注 · 1.2.0-fluid-price-unit-tests）
 * - 删除：流体编辑报价沿用旧每 mB 单位的断言。
 * - 新增：写盘失败测试结束时清理人工创建的阻断目录，保留失败路径验证。
 * - 修改：报价断言对齐当前 EMC/1000mB 单位；拒绝用例从允许的 4096 位改为超限的 4097 位。
 * - 用途：区分界面报价与结算单价，并确保 JUnit 临时目录可以正常清理。
 */
/**
 * 【变更记录 #5】2026-10-10 02:33:01（第 5 次备注 · 1.2.0-item-price-inherit）
 * - 删除：无。
 * - 新增：通过真实屏幕提交与 C2S 包验证物品 inherit 移除目标覆盖、恢复原生价且保留其它人工价。
 * - 修改：无；继续使用 ProjectE 实际映射、文件和网络处理器而非替身。
 * - 用途：覆盖新重置功能的持久化、服务端验证和跨物品隔离。
 */
/**
 * 【变更记录 #6】2026-10-10 02:58:08（第 6 次备注 · 1.2.0-value-temp-cleanup）
 * - 删除：无；保留阻断价表替换以验证失败回滚的既有场景。
 * - 新增：失败写盘后断言同目录 `.tmp` 临时文件已被删除。
 * - 修改：无；仍验证实际流体报价、菜单状态和写盘失败回滚。
 * - 用途：回归保护 ValueTable 在 Windows 文件替换拒绝时清理临时文件，避免 JUnit 临时目录泄漏。
 */
/**
 * 【变更记录 #7】2026-10-10 03:05:29（第 7 次备注 · 1.2.0-price-change-audit）
 * - 删除：无；保留真实 ProjectE 重算与菜单同步回归。
 * - 新增：捕获真实 PriceAudit 日志，验证物品价格保存、inherit 的实际前后状态，以及越权、错目标和写盘失败不输出成功审计。
 * - 修改：测试玩家设置固定管理员名称与 UUID，日志 appender 随每个用例关闭并恢复。
 * - 用途：覆盖客户端提交经服务端事务后产生的真实日志，不用输入值假冒最终保存值。
 */
/**
 * 【变更记录 #8】2026-10-10 03:25:41（第 8 次备注 · 1.2.0-media-custom-restore）
 * - 删除：无；保持真实价格表与网络保存事务。
 * - 新增：屏幕模式入口经实际 C2S 处理验证流体分数、气体大整数在禁用/默认/方向/自定之间保留单价、重载一致、零价不免费及模式写盘失败回滚。
 * - 修改：使用真实提交模式方法并同步服务器回包，不将输入框中显示的默认或零价作为恢复价格。
 * - 用途：补齐五种介质模式的持久化、审计和结算报价验证，防止新增按钮只改变显示而没有真实恢复人工价格。
 */
/**
 * 【变更记录 #9】2026-10-10 04:14:04（第 9 次备注 · 1.2.0-resource-system-one）
 * - 删除：测试结束时直接单次删除临时目录的易波动清理路径。
 * - 新增：使用 RetryingTestTempDirFactory 的有界清理重试，持续失败仍导致测试失败。
 * - 修改：临时目录仍每项/每类独立，模组持久化代码和交易断言保持原有检查；工厂负责最终删除。
 * - 用途：Windows 短暂目录占用不再误判业务失败，绝不关闭真实保存或回滚验证。
 */
/**
 * 【变更记录 #10】2026-10-11 04:26:53（第 10 次备注 · 1.2.0-admin-pricing-system）
 * - 删除：无。
 * - 新增：管理员测试玩家显式设为创造模式，以覆盖新的服务端门禁。
 * - 修改：既有真实 ProjectE、流体、气体、审计和失败回滚断言保持不变。
 * - 用途：确保定价器完整回归不因权限规则改变而使用隐式 mock 默认值。
 */
package com.primordial.machine;

import com.primordial.compat.ProjectEItemPrices;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT;
import com.primordial.menu.PriceEditorMenu;
import com.primordial.network.*;
import com.primordial.network.S2CPriceEditorStatePacket.Status;
import com.primordial.registry.ModMenus;
import com.primordial.screen.PriceEditorScreen;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.value.ValueManager;
import com.primordial.value.ValueTable;
import io.netty.buffer.Unpooled;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.RegistryObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PriceEditorSaveTest {
    @TempDir(factory = com.primordial.util.RetryingTestTempDirFactory.class, cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) Path directory;
    @TempDir(factory = com.primordial.util.RetryingTestTempDirFactory.class, cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER) static Path nativeGameDirectory;
    private static SimpleChannel channel;
    private final Map<FMLPaths, Path> oldPaths = new EnumMap<>(FMLPaths.class);
    private Field registryValue;
    private Object oldMenuType;
    private MockedStatic<FuelMapper> fuels;
    private MockedStatic<PacketHandler> nativeNetwork;
    private List<Object> mappers;
    private List<Object> oldMappers;
    private CustomEMCParser.CustomEMCFile oldEntries;
    private MockedStatic<ModList> mods;
    private ServerPlayer player;
    private MinecraftServer server;
    private NetworkEvent.Context context;
    private PriceEditorMenu serverMenu, clientMenu;
    private PriceEditorScreen screen;
    private EditBox input;
    private PriceAuditLogCapture audit;

    @BeforeAll static void bootstrap() throws Exception {
        AdapterTestBootstrap.initialize();
        Map<FMLPaths, Path> originalPaths = new EnumMap<>(FMLPaths.class);
        for (FMLPaths path : FMLPaths.values()) originalPaths.put(path, path.get());
        FMLPaths.loadAbsolutePaths(nativeGameDirectory);
        // 为真实 ProjectE 重算准备独立临时配置，纯 JUnit 不会经历 Forge 配置加载事件。
        ProjectEConfig.common.getConfigSpec().setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        ProjectEConfig.server.getConfigSpec().setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        restorePaths(originalPaths);
        try (var builders = mockStatic(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class)) {
            var builder = mock(net.minecraftforge.network.NetworkRegistry.ChannelBuilder.class, RETURNS_SELF);
            when(builder.simpleChannel()).thenReturn(mock(SimpleChannel.class));
            builders.when(() -> net.minecraftforge.network.NetworkRegistry.ChannelBuilder.named(any())).thenReturn(builder);
            Class.forName(PacketHandler.class.getName());
        }
        try (var registry = mockStatic(net.minecraftforge.network.NetworkRegistry.class)) {
            registry.when(() -> net.minecraftforge.network.NetworkRegistry.newSimpleChannel(any(), any(), any(), any()))
                    .thenReturn(mock(SimpleChannel.class));
            channel = PrimordialNetwork.CHANNEL;
        }
    }

    @SuppressWarnings("unchecked") @BeforeEach void setup() throws Exception {
        for (FMLPaths path : FMLPaths.values()) oldPaths.put(path, path.get());
        FMLPaths.loadAbsolutePaths(directory);
        // ProjectE 在类初始化时固定 CONFIG_DIR；同 JVM 的其它临时目录清理后仍须重建此目录。
        Files.createDirectories(ProjectEConfig.CONFIG_DIR);
        ValueManager.clear();
        assertTrue(ValueManager.reload());
        registryValue = RegistryObject.class.getDeclaredField("value");
        registryValue.setAccessible(true);
        oldMenuType = registryValue.get(ModMenus.PRICE_EDITOR);
        registryValue.set(ModMenus.PRICE_EDITOR, mock(MenuType.class));
        oldEntries = CustomEMCParser.currentEntries;
        CustomEMCParser.currentEntries = new CustomEMCParser.CustomEMCFile(new ArrayList<>());
        Field mapperField = EMCMappingHandler.class.getDeclaredField("mappers");
        mapperField.setAccessible(true);
        mappers = (List<Object>) mapperField.get(null);
        oldMappers = new ArrayList<>(mappers);
        mappers.clear();
        mappers.add(new CustomEMCMapper());
        EMCMappingHandler.fromPacket(new SyncEmcPKT.EmcPKTInfo[]{new SyncEmcPKT.EmcPKTInfo(Items.DIAMOND, null, 8192)});
        fuels = mockStatic(FuelMapper.class);
        nativeNetwork = mockStatic(PacketHandler.class);
        mods = mockStatic(ModList.class);
        var installed = mock(ModList.class);
        when(installed.isLoaded("mekanism")).thenReturn(true);
        mods.when(ModList::get).thenReturn(installed);
        player = mock(ServerPlayer.class);
        when(player.getName()).thenReturn(Component.literal("PriceAdmin"));
        when(player.getUUID()).thenReturn(java.util.UUID.fromString("00000000-0000-0000-0000-000000000123"));
        server = mock(MinecraftServer.class, RETURNS_DEEP_STUBS);
        when(player.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        var reload = mock(net.minecraftforge.event.AddReloadListenerEvent.class);
        var resources = mock(net.minecraft.server.ReloadableServerResources.class);
        var recipes = server.getRecipeManager();
        var registries = server.registryAccess();
        when(resources.getRecipeManager()).thenReturn(recipes);
        when(reload.getServerResources()).thenReturn(resources);
        when(reload.getRegistryAccess()).thenReturn(registries);
        doAnswer(call -> {
            ((net.minecraft.server.packs.resources.ResourceManagerReloadListener) call.getArgument(0))
                    .onResourceManagerReload(mock(net.minecraft.server.packs.resources.ResourceManager.class));
            return null;
        }).when(reload).addListener(any());
        ProjectEItemPrices.onReloadListeners(reload);
        var serverField = ServerPlayer.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(player, server);
        when(server.getPlayerList().getPlayers()).thenReturn(List.of(player));
        when(player.hasPermissions(2)).thenReturn(true);
        when(player.isCreative()).thenReturn(true);
        context = mock(NetworkEvent.Context.class);
        when(context.getSender()).thenReturn(player);
        when(context.enqueueWork(any(Runnable.class))).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        reset(channel);
        // 传输替身仍经过真实 encode/decode；服务端菜单与客户端菜单是独立对象。
        doAnswer(call -> { deliverRequest(call.getArgument(0)); return null; }).when(channel).sendToServer(any());
        doAnswer(call -> { deliverResponse(call.getArgument(1)); return null; })
                .when(channel).send(any(PacketDistributor.PacketTarget.class), any());
        openMenus();
        audit = new PriceAuditLogCapture();
    }

    @AfterEach void cleanup() throws Exception {
        if (audit != null) audit.close();
        reset(channel);
        ValueManager.clear();
        if (fuels != null) fuels.close();
        if (nativeNetwork != null) nativeNetwork.close();
        if (mappers != null) { mappers.clear(); mappers.addAll(oldMappers); }
        EMCMappingHandler.clearEmcMap();
        CustomEMCParser.flush();
        CustomEMCParser.currentEntries = oldEntries;
        ProjectEConfig.server.difficulty.covalenceLoss.set(1.0);
        if (mods != null) mods.close();
        if (registryValue != null) registryValue.set(ModMenus.PRICE_EDITOR, oldMenuType);
        restorePaths(oldPaths);
    }

    private static void restorePaths(Map<FMLPaths, Path> paths) throws Exception {
        Field pathField = FMLPaths.class.getDeclaredField("absolutePath");
        pathField.setAccessible(true);
        for (var entry : paths.entrySet()) pathField.set(entry.getKey(), entry.getValue());
    }

    private void openMenus() throws Exception {
        serverMenu = new PriceEditorMenu(19, new Inventory(player));
        player.containerMenu = serverMenu;
        Inventory inventory = new Inventory(mock(Player.class));
        clientMenu = new PriceEditorMenu(19, inventory);
        screen = new PriceEditorScreen(clientMenu, inventory, Component.empty());
        input = mock(EditBox.class);
        Field value = PriceEditorScreen.class.getDeclaredField("value");
        value.setAccessible(true);
        value.set(screen, input);
    }

    private void select(Object ingredient) { screen.acceptJeiIngredient(ingredient); }

    private void submit(String text) throws Exception {
        when(input.getValue()).thenReturn(text);
        var method = PriceEditorScreen.class.getDeclaredMethod("submit");
        method.setAccessible(true);
        method.invoke(screen);
        var sync = PriceEditorScreen.class.getDeclaredMethod("syncSelectionUi");
        sync.setAccessible(true);
        sync.invoke(screen);
    }

    private void submitMode(com.primordial.value.PriceMode mode) throws Exception {
        var method = PriceEditorScreen.class.getDeclaredMethod("submitMode", com.primordial.value.PriceMode.class);
        method.setAccessible(true);
        method.invoke(screen, mode);
        var sync = PriceEditorScreen.class.getDeclaredMethod("syncSelectionUi");
        sync.setAccessible(true);
        sync.invoke(screen);
    }

    @Test void fluidModeButtonsRestoreFractionPriceAndPersistBothDirections() throws Exception {
        var id = new ResourceLocation("minecraft:water");
        var fraction = EmcAmount.parse("1/3");
        select(new FluidStack(Fluids.WATER, 1000));
        submit("1/3");
        for (var mode : List.of(com.primordial.value.PriceMode.DISABLED, com.primordial.value.PriceMode.INHERIT_NATIVE,
                com.primordial.value.PriceMode.BUY_ONLY, com.primordial.value.PriceMode.SELL_ONLY,
                com.primordial.value.PriceMode.CUSTOM_VALUE)) {
            submitMode(mode);
            assertEquals(Status.SAVED, clientMenu.priceState().status());
            assertEquals(mode.name(), clientMenu.priceState().mode());
            assertEquals(fraction, ValueManager.fluidSnapshot().get(id.toString()));
            assertEquals(mode.allowsBuy(), ValueManager.canBuyFluid(Fluids.WATER));
            assertEquals(mode.allowsSell(), ValueManager.canSellFluid(Fluids.WATER));
            EmcAmount expected = mode == com.primordial.value.PriceMode.DISABLED ? EmcAmount.ZERO
                    : mode == com.primordial.value.PriceMode.INHERIT_NATIVE ? EmcAmount.ONE : fraction.divide(1000);
            assertEquals(expected, EmcService.fluidPrice(Fluids.WATER));
            assertEquals(mode, ValueTable.read(priceFile()).fluidModes.get(id.toString()));
            assertTrue(ValueManager.reload());
            assertEquals(expected, EmcService.fluidPrice(Fluids.WATER));
        }
        assertEquals("1/3", clientMenu.priceState().price());
        var lastAudit = audit.entries.get(audit.entries.size() - 1);
        assertEquals("CUSTOM_VALUE", lastAudit.getAsJsonObject("after").get("mode").getAsString());
        assertEquals("1/3", lastAudit.getAsJsonObject("after").get("price").getAsString());
    }

    @Test void gasModeButtonsRestoreHugePriceWithoutRoundingAfterReload() throws Exception {
        try (var gas = new GasTestRegistry()) {
            var price = new BigInteger("987654321987654321987654321");
            select(new mekanism.api.chemical.gas.GasStack(gas.gas, 1));
            submit(price.toString());
            for (var mode : List.of(com.primordial.value.PriceMode.DISABLED, com.primordial.value.PriceMode.INHERIT_NATIVE,
                    com.primordial.value.PriceMode.SELL_ONLY, com.primordial.value.PriceMode.BUY_ONLY,
                    com.primordial.value.PriceMode.CUSTOM_VALUE)) {
                submitMode(mode);
                assertEquals(Status.SAVED, clientMenu.priceState().status());
                assertEquals(mode.name(), clientMenu.priceState().mode());
                assertEquals(price, ValueManager.gasSnapshot().get(gas.id.toString()));
                assertEquals(mode.allowsBuy(), ValueManager.canBuyGas(gas.id));
                assertEquals(mode.allowsSell(), ValueManager.canSellGas(gas.id));
                EmcAmount expected = mode == com.primordial.value.PriceMode.DISABLED ? EmcAmount.ZERO
                        : mode == com.primordial.value.PriceMode.INHERIT_NATIVE ? EmcAmount.ONE : EmcAmount.of(price);
                assertTrue(ValueManager.reload());
                assertEquals(expected, EmcService.gasPrice(gas.id));
            }
            assertEquals(price.toString(), clientMenu.priceState().price());
        }
    }

    @Test void restoredZeroPriceCannotBecomeFreeMediaPurchases() throws Exception {
        select(new FluidStack(Fluids.WATER, 1));
        submit("0");
        submitMode(com.primordial.value.PriceMode.CUSTOM_VALUE);
        assertEquals(Status.SAVED, clientMenu.priceState().status());
        assertTrue(EmcService.fluidPrice(Fluids.WATER).isZero());
        var transfer = mock(java.util.function.LongUnaryOperator.class);
        try (var knowledge = mockStatic(com.primordial.service.ResourceKnowledge.class)) {
            knowledge.when(() -> com.primordial.service.ResourceKnowledge.allowsMedia(server, player.getUUID(),
                    com.primordial.service.ResourceKnowledge.Kind.FLUID, new ResourceLocation("minecraft:water"))).thenReturn(true);
            assertEquals(0, EmcService.purchaseFluid(server, player.getUUID(), Fluids.WATER, 1000, transfer));
            assertEquals(0, EmcService.reclaimFluid(server, player.getUUID(), Fluids.WATER, 1000, transfer));
            verifyNoInteractions(transfer);
        }
    }

    @Test void failedModeSavePreservesSavedCustomPriceModeAndAudit() throws Exception {
        var id = new ResourceLocation("minecraft:water");
        select(new FluidStack(Fluids.WATER, 1));
        submit("1/3");
        audit.entries.clear();
        Files.delete(priceFile());
        Files.createDirectory(priceFile());
        var blocker = priceFile().resolve("prevent-replacement");
        try {
            Files.writeString(blocker, "test", java.nio.charset.StandardCharsets.UTF_8);
            long beforeRevision = ValueManager.priceRevision();
            submitMode(com.primordial.value.PriceMode.DISABLED);
            assertEquals(Status.SAVE_FAILED, clientMenu.priceState().status());
            assertEquals("CUSTOM_VALUE", clientMenu.priceState().mode());
            assertEquals(EmcAmount.parse("1/3"), ValueManager.fluidSnapshot().get(id.toString()));
            assertEquals(beforeRevision, ValueManager.priceRevision());
            assertTrue(audit.entries.isEmpty());
            assertFalse(Files.exists(priceFile().resolveSibling(priceFile().getFileName() + ".tmp")));
        } finally {
            Files.deleteIfExists(blocker);
            Files.deleteIfExists(priceFile());
        }
    }

    private void deliverRequest(Object packet) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            if (packet instanceof C2SSetPriceTargetPacket target) {
                C2SSetPriceTargetPacket.encode(target, buffer);
                C2SSetPriceTargetPacket.handle(C2SSetPriceTargetPacket.decode(buffer), () -> context);
            } else if (packet instanceof C2SPlayerActionPacket price) {
                C2SPlayerActionPacket.encode(price, buffer);
                C2SPlayerActionPacket.handle(C2SPlayerActionPacket.decode(buffer), () -> context);
            } else if (packet instanceof C2SSetMediaPricePacket price) {
                C2SSetMediaPricePacket.encode(price, buffer);
                C2SSetMediaPricePacket.handle(C2SSetMediaPricePacket.decode(buffer), () -> context);
            } else fail("Unexpected request " + packet);
        } finally { buffer.release(); }
    }

    private void deliverResponse(Object packet) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            if (packet instanceof S2CPriceEditorStatePacket state) {
                S2CPriceEditorStatePacket.encode(state, buffer);
                clientMenu.acceptPriceState(S2CPriceEditorStatePacket.decode(buffer));
            } else fail("Unexpected response " + packet);
        } finally { buffer.release(); }
    }

    @Test void itemSubmissionChangesTheRealProjectEMapAndItsPersistentFile() throws Exception {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        select(diamond);
        assertEquals("8192", clientMenu.priceState().price());
        BigInteger price = BigInteger.valueOf(Long.MAX_VALUE).subtract(BigInteger.valueOf(37));
        submit(price.toString());
        assertEquals(1, audit.entries.size());
        var change = audit.entries.get(0);
        assertEquals("PriceAdmin", change.get("operator").getAsString());
        assertEquals(player.getUUID().toString(), change.get("operator_uuid").getAsString());
        assertEquals("minecraft:diamond", change.get("resource").getAsString());
        assertEquals("EMC/item", change.get("unit").getAsString());
        assertEquals("8192", change.getAsJsonObject("before").get("price").getAsString());
        assertEquals(price.toString(), change.getAsJsonObject("after").get("price").getAsString());
        assertEquals("CUSTOM_VALUE", change.getAsJsonObject("after").get("mode").getAsString());
        assertNotNull(java.time.Instant.parse(change.get("time").getAsString()));
        assertEquals(Status.SAVED, clientMenu.priceState().status());
        assertEquals(price.toString(), clientMenu.priceState().price());
        assertEquals(price, EmcService.itemValue(diamond, server));
        assertEquals(price, EmcService.itemValue(diamond, null));
        assertEquals(price.longValueExact(), IEMCProxy.INSTANCE.getValue(diamond));
        nativeNetwork.verify(PacketHandler::sendFragmentedEmcPacketToAll);
        assertTrue(ProjectEItemPrices.reload(server));
        openMenus();
        select(diamond);
        assertEquals(price.toString(), clientMenu.priceState().price());
        assertEquals(price.longValueExact(), EMCMappingHandler.getStoredEmcValue(ItemInfo.fromItem(Items.DIAMOND)));
        assertFalse(com.google.gson.JsonParser.parseString(Files.readString(priceFile())).getAsJsonObject().has("item_overrides"));
        assertTrue(Files.readString(ProjectEItemPrices.file()).contains(price.toString()));
    }

    @Test void fluidDecimalAndFractionSubmitWithoutAnItemAndReloadInPerBucketUnits() throws Exception {
        for (String text : List.of("1.25", "1/3", "0.001")) {
            openMenus();
            select(new FluidStack(Fluids.WATER, 1000));
            assertTrue(clientMenu.selectedItem().isEmpty());
            submit(text);
            assertEquals(Status.SAVED, clientMenu.priceState().status());
            EmcAmount expected = EmcAmount.parse(text);
            assertEquals(expected.toString(), clientMenu.priceState().price());
            assertEquals(expected.divide(1000), EmcService.fluidPrice(Fluids.WATER));
            assertEquals(expected, ValueTable.read(priceFile()).fluids.get("minecraft:water"));
            assertTrue(ValueManager.reload());
            openMenus();
            select(new FluidStack(Fluids.WATER, 1));
            assertEquals(expected.toString(), clientMenu.priceState().price());
        }
    }

    @Test void gasSubmissionSavesAndSurvivesReloadWithoutItemSample() throws Exception {
        try (var gas = new GasTestRegistry()) {
            select(new mekanism.api.chemical.gas.GasStack(gas.gas, 1));
            assertTrue(clientMenu.selectedItem().isEmpty());
            assertEquals("1", clientMenu.priceState().price());
            submit("987654321987654321987654321");
            assertEquals(Status.SAVED, clientMenu.priceState().status());
            assertEquals(EmcAmount.parse("987654321987654321987654321"), EmcService.gasPrice(gas.id));
            assertTrue(ValueManager.reload());
            openMenus();
            select(new mekanism.api.chemical.gas.GasStack(gas.gas, 1));
            assertEquals("987654321987654321987654321", clientMenu.priceState().price());
        }
    }

    @Test void zeroOverridesRemainVisibleAndDisableAllThreePrices() throws Exception {
        select(new ItemStack(Items.DIAMOND));
        submit("0");
        assertEquals(BigInteger.ZERO, EmcService.itemValue(new ItemStack(Items.DIAMOND), null));
        assertEquals(0, IEMCProxy.INSTANCE.getValue(Items.DIAMOND));
        select(new FluidStack(Fluids.LAVA, 1000));
        submit("0");
        assertTrue(EmcService.fluidPrice(Fluids.LAVA).isZero());
        try (var gas = new GasTestRegistry()) {
            select(new mekanism.api.chemical.gas.GasStack(gas.gas, 1));
            submit("0");
            assertTrue(EmcService.gasPrice(gas.id).isZero());
            assertEquals("0", clientMenu.priceState().price());
            assertTrue(ValueManager.reload());
            assertTrue(EmcService.gasPrice(gas.id).isZero());
        }
    }

    @Test void permissionAndTargetFailuresAreReportedWithoutWriting() throws Exception {
        select(new ItemStack(Items.DIAMOND));
        when(player.hasPermissions(2)).thenReturn(false);
        deliverRequest(new C2SPlayerActionPacket("minecraft:diamond", BigInteger.TEN, 19));
        assertEquals(Status.PERMISSION_DENIED, clientMenu.priceState().status());
        assertEquals("8192", clientMenu.priceState().price());
        assertTrue(ValueManager.overrideSnapshot().isEmpty());
        when(player.hasPermissions(2)).thenReturn(true);
        select(new FluidStack(Fluids.WATER, 1));
        when(player.hasPermissions(2)).thenReturn(false);
        deliverRequest(new C2SSetMediaPricePacket(19, PriceEditorMenu.KIND_FLUID, "minecraft:water", "2"));
        assertEquals(Status.PERMISSION_DENIED, clientMenu.priceState().status());
        assertEquals(EmcAmount.ONE, EmcService.fluidPrice(Fluids.WATER));
        when(player.hasPermissions(2)).thenReturn(true);
        deliverRequest(new C2SSetMediaPricePacket(19, PriceEditorMenu.KIND_FLUID, "minecraft:lava", "7"));
        assertEquals(Status.INVALID_TARGET, clientMenu.priceState().status());
        assertEquals(EmcAmount.ONE, EmcService.fluidPrice(Fluids.LAVA));
        assertTrue(audit.entries.isEmpty());
    }

    @Test void invalidInputIsNotSubmittedAndServerIndependentlyRejectsIt() throws Exception {
        select(new FluidStack(Fluids.WATER, 1));
        clearInvocations(channel);
        submit("1/0");
        verify(channel, never()).sendToServer(any());
        deliverRequest(new C2SSetMediaPricePacket(19, PriceEditorMenu.KIND_FLUID, "minecraft:water", "1/0"));
        assertEquals(Status.INVALID_PRICE, clientMenu.priceState().status());
        assertEquals("1000", clientMenu.priceState().price());
        submit("9".repeat(4097));
        verify(channel, never()).sendToServer(any());
        assertEquals(EmcAmount.ONE, EmcService.fluidPrice(Fluids.WATER));
    }

    @Test void failedAtomicSaveKeepsActualPriceAndReportsFailure() throws Exception {
        select(new FluidStack(Fluids.WATER, 1));
        // 用非空目录阻止替换价表，仅操作临时目录；检验真实 ValueManager 回滚。
        Files.delete(priceFile());
        Files.createDirectory(priceFile());
        Path blocker = priceFile().resolve("prevent-replacement");
        try {
            Files.writeString(blocker, "test", java.nio.charset.StandardCharsets.UTF_8);
            submit("42");
            assertEquals(Status.SAVE_FAILED, clientMenu.priceState().status());
            assertEquals("1000", clientMenu.priceState().price());
            assertEquals(EmcAmount.ONE, EmcService.fluidPrice(Fluids.WATER));
            assertFalse(Files.exists(priceFile().resolveSibling(priceFile().getFileName() + ".tmp")));
            assertTrue(audit.entries.isEmpty());
        } finally {
            Files.deleteIfExists(blocker);
            Files.deleteIfExists(priceFile());
        }
    }

    @Test void selectionRepliesAreBoundedAndOldTargetsCannotOverwriteCurrentPrice() {
        select(new ItemStack(Items.DIAMOND));
        S2CPriceEditorStatePacket old = clientMenu.priceState();
        select(new FluidStack(Fluids.WATER, 1));
        clientMenu.acceptPriceState(old);
        assertEquals(PriceEditorMenu.KIND_FLUID, clientMenu.priceState().kind());
        assertEquals("1000", clientMenu.priceState().price());
        clearInvocations(channel);
        for (int tick = 0; tick < 100; tick++) serverMenu.broadcastChanges();
        verify(channel, never()).send(any(PacketDistributor.PacketTarget.class), any());
        select(new FluidStack(Fluids.WATER, 1));
        assertNotNull(clientMenu.priceState(), "Reselecting the same sample must return a fresh quote");
        verify(channel, times(1)).send(any(PacketDistributor.PacketTarget.class), any());
    }

    @Test void inventorySampleClickNeverConsumesOrClearsItemOnEmptyLeftClick() {
        ItemStack carried = new ItemStack(Items.DIAMOND, 64);
        serverMenu.setCarried(carried);
        serverMenu.clicked(0, 0, ClickType.PICKUP, player);
        assertEquals(64, serverMenu.getCarried().getCount());
        assertEquals(1, serverMenu.selectedItem().getCount());
        serverMenu.setCarried(ItemStack.EMPTY);
        serverMenu.clicked(0, 0, ClickType.PICKUP, player);
        assertFalse(serverMenu.selectedItem().isEmpty());
        serverMenu.clicked(0, 0, ClickType.SWAP, player);
        assertFalse(serverMenu.selectedItem().isEmpty());
        assertFalse(serverMenu.slots.get(0).mayPlace(carried));
        serverMenu.clicked(0, 1, ClickType.PICKUP, player);
        assertNull(serverMenu.selectedId());
    }

    @Test void externalPriceEditsRefreshOpenMenuOnlyAfterPublish() {
        select(new FluidStack(Fluids.WATER, 1));
        assertEquals("1000", clientMenu.priceState().price());
        clearInvocations(channel);
        assertTrue(ValueManager.setFluidValue(new ResourceLocation("minecraft", "water"), EmcAmount.parse("7000")));
        serverMenu.broadcastChanges();
        assertEquals("7000", clientMenu.priceState().price());
        for (int tick = 0; tick < 100; tick++) serverMenu.broadcastChanges();
        verify(channel, times(1)).send(any(PacketDistributor.PacketTarget.class), any());
    }

    @Test void oversizedItemPriceIsRejectedInsteadOfCreatingAnIndependentPrice() throws Exception {
        select(new ItemStack(Items.DIAMOND));
        clearInvocations(channel);
        submit(BigInteger.ONE.shiftLeft(63).toString());
        verify(channel, never()).sendToServer(any());
        deliverRequest(new C2SPlayerActionPacket("minecraft:diamond", BigInteger.ONE.shiftLeft(63), 19));
        assertEquals(Status.INVALID_PRICE, clientMenu.priceState().status());
        assertEquals(8192, IEMCProxy.INSTANCE.getValue(Items.DIAMOND));
        assertFalse(Files.exists(ProjectEItemPrices.file()));
    }

    @Test void recoveryHonorsTheSameCovalenceLossAsProjectE() {
        ProjectEConfig.server.difficulty.covalenceLoss.set(0.5);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        assertEquals(8192, IEMCProxy.INSTANCE.getValue(diamond));
        assertEquals(4096, IEMCProxy.INSTANCE.getSellValue(diamond));
        assertEquals(EmcAmount.parse("8192"), EmcService.itemPrice(diamond, server));
        assertEquals(EmcAmount.parse("4096"), EmcService.itemRecoveryPrice(diamond, server));
    }

    @Test void nativeCommandEditsArePreservedWhenTheConfiguratorSetsAnotherItem() throws Exception {
        CustomEMCParser.addToFile("minecraft:iron_ingot", 900);
        select(new ItemStack(Items.DIAMOND));
        submit("12345");
        assertEquals(Status.SAVED, clientMenu.priceState().status());
        assertEquals(900, IEMCProxy.INSTANCE.getValue(Items.IRON_INGOT));
        assertEquals(12345, IEMCProxy.INSTANCE.getValue(Items.DIAMOND));
        assertTrue(ProjectEItemPrices.reload(server));
        assertEquals(900, IEMCProxy.INSTANCE.getValue(Items.IRON_INGOT));
    }

    @Test void itemInheritRemovesOnlyItsOverrideAndRestoresNativePrice() throws Exception {
        select(new ItemStack(Items.IRON_INGOT));
        submit("900");
        select(new ItemStack(Items.DIAMOND));
        BigInteger nativeDiamondPrice = BigInteger.valueOf(IEMCProxy.INSTANCE.getValue(Items.DIAMOND));
        submit("12345");
        assertEquals("CUSTOM_VALUE", clientMenu.priceState().mode());
        assertEquals(12345, IEMCProxy.INSTANCE.getValue(Items.DIAMOND));

        audit.entries.clear();
        submit("inherit");
        assertEquals(1, audit.entries.size());
        var change = audit.entries.get(0);
        assertEquals("CUSTOM_VALUE", change.getAsJsonObject("before").get("mode").getAsString());
        assertEquals("12345", change.getAsJsonObject("before").get("price").getAsString());
        assertEquals("INHERIT_NATIVE", change.getAsJsonObject("after").get("mode").getAsString());
        assertEquals(nativeDiamondPrice.toString(), change.getAsJsonObject("after").get("price").getAsString());
        assertEquals(Status.SAVED, clientMenu.priceState().status());
        assertEquals("INHERIT_NATIVE", clientMenu.priceState().mode());
        assertEquals(nativeDiamondPrice.longValueExact(), IEMCProxy.INSTANCE.getValue(Items.DIAMOND));
        assertFalse(ProjectEItemPrices.customPrices().containsKey("minecraft:diamond"));
        assertTrue(Files.readString(ProjectEItemPrices.file()).contains("iron_ingot"));
        assertTrue(Files.readString(ProjectEItemPrices.file()).contains("900"));
    }

    @Test void nativeMapFailureRestoresOriginalPriceAndDoesNotReportSuccess() throws Exception {
        select(new ItemStack(Items.DIAMOND));
        mappers.clear(); // 模拟 CustomEMCMapper 被禁用，真实重算无法应用管理员输入。
        submit("12345");
        assertEquals(Status.SAVE_FAILED, clientMenu.priceState().status());
        assertEquals("8192", clientMenu.priceState().price());
        assertEquals(8192, IEMCProxy.INSTANCE.getValue(Items.DIAMOND));
        assertFalse(Files.exists(ProjectEItemPrices.file()));
    }

    @Test void malformedNativePriceFileIsPreservedWithoutChangingProjectE() throws Exception {
        Path file = ProjectEItemPrices.file();
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{broken", java.nio.charset.StandardCharsets.UTF_8);
        select(new ItemStack(Items.DIAMOND));
        submit("12345");
        assertEquals(Status.SAVE_FAILED, clientMenu.priceState().status());
        assertEquals(8192, IEMCProxy.INSTANCE.getValue(Items.DIAMOND));
        assertEquals("{broken", Files.readString(file));
    }

    private Path priceFile() { return FMLPaths.CONFIGDIR.get().resolve("primordial_values.json"); }
}
