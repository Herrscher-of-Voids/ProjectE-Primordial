/**
 * 【文件职责】在真实 NeoForge 世界验证定价器权限、容器幽灵槽和有界网络闭环。
 * 【变更记录 #1】2026-10-23 17:02:44（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：真实登录管理员/创造访客、样本不吞不复制、容器读取和旧请求隔离测试。
 * - 修改：复用生产包处理器与注册菜单；失败请求不写公共价格，测试玩家和权限在 finally 清理。
 * - 用途：补足价格服务测试以外的菜单边界；专服自动测试不冒充单人主人 GUI 或实际 JEI 拖动验收。
 * 【变更记录 #2】2026-10-23 17:13:41（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：测试夹具依赖 GameTestServer 默认管理员等级可能小于二的假设。
 * - 新增：真实 ServerOpList 中显式写入二级权限，finally 继续撤销该测试玩家权限。
 * - 修改：只修正玩家夹具，不修改生产定价器的二级门禁。
 * - 用途：准确验证用户确认的二级管理员权限，而非因测试服务器默认配置产生假失败。
 */
package com.primordial.neoforge.priceeditor;

import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.value.NeoForgePriceEditing;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.fml.loading.FMLPaths;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePriceEditorGameTests {
    private static final ResourceLocation DIAMOND = ResourceLocation.parse("minecraft:diamond"), WATER = ResourceLocation.parse("minecraft:water");
    private NeoForgePriceEditorGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void administratorAnyModeAndCreativeVisitorAreCheckedOnTheActualServer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            player.setGameMode(GameType.CREATIVE);
            check(!NeoForgePriceEditorAccess.canEdit(player), "A creative visitor on a dedicated server must not edit prices");
            grantOperator(server, player);
            for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR)) {
                player.setGameMode(mode);
                check(NeoForgePriceEditorAccess.canEdit(player), "A level-two administrator may edit in every game mode");
            }
            var menu = new NeoForgePriceEditorMenu(41, player.getInventory());
            player.containerMenu = menu;
            check(menu.stillValid(player), "Authorized menu must remain valid");
            server.getPlayerList().deop(player.getGameProfile());
            player.setGameMode(GameType.CREATIVE);
            check(!menu.stillValid(player), "Losing operator status must invalidate even a creative visitor menu");
            menu.broadcastChanges();
            check(player.containerMenu == player.inventoryMenu, "A broadcast after losing permission must close the real open menu");
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void sampleNeverConsumesDuplicatesOrAcceptsVanillaDragCollectAndThrow(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            grantOperator(server, player);
            var menu = new NeoForgePriceEditorMenu(42, player.getInventory());
            player.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.DIAMOND, 3));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            check(menu.selectedId().equals(DIAMOND) && menu.selectedItem().getCount() == 1 && menu.getCarried().getCount() == 3,
                    "Left click must copy one sample while keeping all three carried real items");
            long version = menu.selectionVersion();
            for (ClickType click : List.of(ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.CLONE, ClickType.THROW, ClickType.QUICK_CRAFT, ClickType.PICKUP_ALL))
                menu.clicked(0, 0, click, player);
            check(menu.selectionVersion() == version && menu.getCarried().getCount() == 3 && menu.selectedItem().getCount() == 1,
                    "Alternate vanilla clicks must not move, clone, throw or replace the sample");
            check(!menu.canDragTo(menu.getSlot(0)) && !menu.canTakeItemForPickAll(ItemStack.EMPTY, menu.getSlot(0))
                    && !menu.getSlot(0).mayPickup(player) && !menu.getSlot(0).mayPlace(new ItemStack(Items.DIAMOND)),
                    "Drag and double collection must explicitly exclude the ghost slot");
            menu.selectedItem().setCount(64);
            check(menu.selectedItem().getCount() == 1, "Sample accessor must return a copy");
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(0, 0, ClickType.PICKUP, player);
            check(menu.selectedId().equals(DIAMOND), "Empty-handed left click must retain the selected sample");
            menu.setCarried(new ItemStack(Items.DIAMOND, 3));
            menu.clicked(0, 1, ClickType.PICKUP, player);
            check(menu.selectedId() == null && menu.getCarried().getCount() == 3, "Right click clears only the ghost selection");
            menu.setItemSelection(new ItemStack(Items.DIAMOND));
            menu.removed(player);
            check(player.getInventory().countItem(Items.DIAMOND) == 3, "Closing must return only the real carried stack, never the ghost");
            player.containerMenu = player.inventoryMenu;
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void filledBucketSamplingIsReadOnlyAndKindSwitchClearsOldMedia(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            grantOperator(server, player);
            var menu = new NeoForgePriceEditorMenu(43, player.getInventory());
            player.containerMenu = menu;
            menu.setCarried(new ItemStack(Items.WATER_BUCKET));
            menu.clicked(0, 0, ClickType.PICKUP, player);
            check(menu.selectedKind() == NeoForgePriceEditorMenu.KIND_FLUID && WATER.equals(menu.selectedId())
                    && menu.selectedItem().isEmpty() && menu.selectedFluid().getAmount() == 1000 && menu.getCarried().is(Items.WATER_BUCKET),
                    "A real water bucket must produce a 1000mB ghost and remain a filled bucket");
            menu.selectedFluid().setAmount(0);
            check(menu.selectedFluid().getAmount() == 1000, "Fluid sample accessor must return a copy");
            menu.setItemSelection(new ItemStack(Items.DIAMOND));
            check(menu.selectedKind() == NeoForgePriceEditorMenu.KIND_ITEM && menu.selectedFluid().isEmpty(), "Item selection must clear old fluid identity");
            menu.clearSelection();
            check(menu.selectedId() == null && menu.selectedItem().isEmpty() && menu.selectedFluid().isEmpty(), "Clear must remove every old sample representation");
            menu.setCarried(ItemStack.EMPTY);
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void wrongContainerTargetGenerationRevisionAndPermissionNeverWrite(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            grantOperator(server, player);
            var menu = new NeoForgePriceEditorMenu(44, player.getInventory());
            player.containerMenu = menu;
            menu.setItemSelection(new ItemStack(Items.DIAMOND));
            long version = menu.selectionVersion(), revision = NeoForgePriceEditing.revision();
            var before = NeoForgePriceEditing.snapshot((byte) 0, DIAMOND);
            NeoForgePriceEditorPayloads.handleTarget(player, new NeoForgePriceEditorPayloads.Target(45, (byte) 1, WATER.toString(), version + 1));
            check(menu.selectedId().equals(DIAMOND), "A different menu ID cannot change the sample");
            for (var edit : List.of(
                    new NeoForgePriceEditorPayloads.Edit(45, (byte) 0, DIAMOND.toString(), version, revision, "999"),
                    new NeoForgePriceEditorPayloads.Edit(44, (byte) 0, WATER.toString(), version, revision, "999"),
                    new NeoForgePriceEditorPayloads.Edit(44, (byte) 1, DIAMOND.toString(), version, revision, "999"),
                    new NeoForgePriceEditorPayloads.Edit(44, (byte) 0, DIAMOND.toString(), version + 1, revision, "999"),
                    new NeoForgePriceEditorPayloads.Edit(44, (byte) 0, DIAMOND.toString(), version, revision - 1, "999"),
                    new NeoForgePriceEditorPayloads.Edit(44, (byte) 0, DIAMOND.toString(), version, revision, "")))
                NeoForgePriceEditorPayloads.handleEdit(player, edit);
            check(before.equals(NeoForgePriceEditing.snapshot((byte) 0, DIAMOND)), "Wrong identity, old quote and empty price must leave the actual price unchanged");
            check(!menu.acceptTarget((byte) 0, ResourceLocation.parse("minecraft:air"), version + 1)
                    && !menu.acceptTarget((byte) 1, ResourceLocation.parse("minecraft:empty"), version + 1)
                    && !menu.acceptTarget((byte) 99, DIAMOND, version + 1)
                    && !menu.acceptTarget((byte) 0, DIAMOND, version), "Empty, unknown kind and stale target requests must not establish a valid sample");
            server.getPlayerList().deop(player.getGameProfile());
            player.setGameMode(GameType.CREATIVE);
            NeoForgePriceEditorPayloads.handleEdit(player, new NeoForgePriceEditorPayloads.Edit(44, (byte) 0, DIAMOND.toString(), version, NeoForgePriceEditing.revision(), "999"));
            check(player.containerMenu == player.inventoryMenu && before.equals(NeoForgePriceEditing.snapshot((byte) 0, DIAMOND)),
                    "A C2S save after losing permission must close the menu without writing even while creative");
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void lateSampleStatesAreDiscardedAndProtocolPreservesExactLargePrices(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try {
            var menu = new NeoForgePriceEditorMenu(45, player.getInventory());
            menu.setItemSelection(new ItemStack(Items.DIAMOND));
            long oldVersion = menu.selectionVersion();
            menu.clearSelection();
            menu.setItemSelection(new ItemStack(Items.DIAMOND));
            var old = new NeoForgePriceEditorPayloads.State(45, (byte) 0, DIAMOND.toString(), oldVersion, "1", "CUSTOM_VALUE", true, true,
                    10, NeoForgePriceEditorPayloads.Status.SAVED, "primordial.price_editor.result.saved");
            menu.acceptPriceState(old);
            check(menu.priceState() == null, "Switching away and back to the same ID must reject the old selection generation");
            var current = new NeoForgePriceEditorPayloads.State(45, (byte) 0, DIAMOND.toString(), menu.selectionVersion(), "9".repeat(4096),
                    "CUSTOM_VALUE", false, true, 11, NeoForgePriceEditorPayloads.Status.CURRENT, "");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
            try {
                NeoForgePriceEditorPayloads.State.STREAM_CODEC.encode(buffer, current);
                var decoded = NeoForgePriceEditorPayloads.State.STREAM_CODEC.decode(buffer);
                check(current.equals(decoded), "The bounded protocol must round-trip exact decimal strings, permission, revision and selection version");
                menu.acceptPriceState(decoded);
                check(menu.priceState().price().length() == 4096 && !menu.priceState().editable(), "Client state must use only synchronized permission and exact server text");
            } finally { buffer.release(); }
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void actualNetworkSaveModeExternalRefreshAndInvalidTextUseAuthoritativeSnapshots(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        // 先读以确保初始价表存在；同步方法内备份/还原，没有跨 tick 的全局配置窗口。
        NeoForgePriceEditing.snapshot((byte) 1, WATER);
        var path = FMLPaths.CONFIGDIR.get().resolve("primordial_values.json");
        byte[] previous = Files.exists(path) ? Files.readAllBytes(path) : null;
        try {
            grantOperator(server, player);
            var menu = new NeoForgePriceEditorMenu(46, player.getInventory());
            player.containerMenu = menu;
            check(menu.acceptTarget((byte) 1, WATER, 1), "A valid JEI target must establish the real server selection");
            menu.broadcastChanges();
            NeoForgePriceEditorPayloads.handleEdit(player, edit(menu, "17/3"));
            check(menu.priceState().status() == NeoForgePriceEditorPayloads.Status.SAVED && menu.priceState().price().equals("17/3")
                    && menu.priceState().mode().equals("CUSTOM_VALUE"), "Successful C2S must return the saved exact server price and actual mode");
            check(NeoForgePriceEditing.mutate(server, player, (byte) 1, WATER, "buy_only").success(), "Another authorized editor must change the public price mode");
            menu.broadcastChanges();
            check(menu.priceState().status() == NeoForgePriceEditorPayloads.Status.CURRENT && menu.priceState().mode().equals("BUY_ONLY")
                    && menu.priceState().price().equals("17/3"), "An external revision must refresh an already open menu without losing the saved price");
            NeoForgePriceEditorPayloads.handleEdit(player, edit(menu, "-1"));
            check(menu.priceState().status() == NeoForgePriceEditorPayloads.Status.INVALID_PRICE && menu.priceState().price().equals("17/3"),
                    "Invalid input must report failure and preserve the real previous value");
            NeoForgePriceEditorPayloads.handleEdit(player, edit(menu, "disabled"));
            check(menu.priceState().status() == NeoForgePriceEditorPayloads.Status.SAVED && menu.priceState().mode().equals("DISABLED"), "Cancel price must return saved disabled mode");
            NeoForgePriceEditorPayloads.handleEdit(player, edit(menu, "inherit_native"));
            check(menu.priceState().status() == NeoForgePriceEditorPayloads.Status.SAVED && menu.priceState().mode().equals("INHERIT_NATIVE"), "Restore must return the actual native mode");
        } finally {
            try {
                if (previous == null) Files.deleteIfExists(path); else Files.write(path, previous);
                NeoForgeRecoveryPrices.reload();
            } finally { logout(server, player); }
        }
        helper.succeed();
    }

    private static NeoForgePriceEditorPayloads.Edit edit(NeoForgePriceEditorMenu menu, String text) {
        return new NeoForgePriceEditorPayloads.Edit(menu.containerId, menu.selectedKind(), menu.selectedId().toString(),
                menu.selectionVersion(), NeoForgePriceEditing.revision(), text);
    }

    private static void grantOperator(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
    }

    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "price-" + UUID.randomUUID().toString().substring(0, 6));
        var cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            try { logout(server, player); } finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        try {
            server.getPlayerList().deop(player.getGameProfile());
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel) channel.finishAndReleaseAll();
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
