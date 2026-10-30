/**
 * 【文件职责】验证系统04第一切片确实扩展 ProjectE 原生转化桌菜单，并在关闭时返还合成材料。
 *
 * 【变更记录 #1】2026-10-23 19:39:28（第 1 次备注 · 2.0.0-neoforge-transmutation-table）
 * - 删除：无。
 * - 新增：原生菜单槽位数量、9 格输入、结果槽及关闭返还的最小 GameTest。
 * - 修改：测试只使用 NeoForge 与必需 ProjectE API，不依赖 JEI、Mekanism 或第三方网络。
 * - 用途：在实现配方展示和媒体交易前先锁定容器边界，防止后续 Mixin 改动破坏原生菜单。
 *
 * 【变更记录 #2】2026-10-23（第 2 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 新增：结果槽 Shift 提取的完整交付与部分容量拒绝测试。
 * - 修改：使用原版木棍配方和真实玩家背包槽，不以替身转移函数绕过原生 ResultSlot。
 * - 用途：验证空间不足不吞材料、空间足够恰好交付完整产物。
 *
 * 【变更记录 #3】2026-10-23（第 3 次备注 · 2.0.0-neoforge-transmutation-media）
 * - 新增：真实 ServerPlayer 在 doLimitedCrafting 下的未解锁拒绝、规则切换和配方解锁测试。
 * - 修改：通过已协商的内存连接正常登录，finally 恢复规则、返还材料并释放玩家连接。
 * - 用途：避免模拟玩家测试掩盖原版有限合成规则被绕过。
 */
package com.primordial.neoforge.transmutation;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeTransmutationCraftingGameTests {
    private NeoForgeTransmutationCraftingGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativeMenuAddsCraftingSlotsAndReturnsInputsOnClose(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TransmutationContainer menu = new TransmutationContainer(77, player.getInventory());
        check(menu.slots.size() == NeoForgeTransmutationTable.TOTAL_SLOT_COUNT,
                "The native ProjectE menu must expose 63 native slots, 9 crafting inputs, one result and 9 real media slots");
        check(NeoForgeTransmutationTable.isCraftingSlot(63)
                        && NeoForgeTransmutationTable.isCraftingSlot(71)
                        && NeoForgeTransmutationTable.isResultSlot(72),
                "The system04 slot contract must remain 63-71 input and 72 result");
        menu.getSlot(63).set(new ItemStack(Items.DIRT));
        menu.removed(player);
        check(player.getInventory().countItem(Items.DIRT) == 1,
                "Closing the native menu must return an unconsumed crafting input to the player");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativeMenuAssemblesVanillaCraftingRecipe(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TransmutationContainer menu = new TransmutationContainer(78, player.getInventory());
        ItemStack plank = new ItemStack(Items.OAK_PLANKS);
        menu.getSlot(63).set(plank.copy());
        menu.getSlot(64).set(plank.copy());
        menu.getSlot(66).set(plank.copy());
        menu.getSlot(67).set(plank.copy());
        // Mock players do not always propagate TransientCraftingContainer#setChanged
        // through the native menu, so exercise the same callback explicitly.
        menu.slotsChanged(menu.getSlot(63).container);
        check(menu.getSlot(72).getItem().is(Items.CRAFTING_TABLE),
                "The added 3x3 grid must assemble the vanilla 2x2 crafting table recipe");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void shiftCraftRejectsPartialOutputCapacityWithoutChangingInputs(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TransmutationContainer menu = stickCraftingMenu(player, 79, 63);
        ItemStack moved = menu.quickMoveStack(player, NeoForgeTransmutationTable.RESULT_SLOT);
        check(moved.isEmpty(), "A four-stick result must not move when only one stick fits");
        check(menu.getSlot(62).getItem().getCount() == 63
                        && player.getInventory().countItem(Items.STICK) == 63,
                "Rejected crafting must leave the partial inventory stack unchanged");
        check(menu.getSlot(63).getItem().is(Items.OAK_PLANKS)
                        && menu.getSlot(63).getItem().getCount() == 1
                        && menu.getSlot(66).getItem().is(Items.OAK_PLANKS)
                        && menu.getSlot(66).getItem().getCount() == 1,
                "Rejected crafting must not consume either plank");
        check(menu.getSlot(72).getItem().is(Items.STICK) && menu.getSlot(72).getItem().getCount() == 4,
                "Rejected crafting must preserve the full recipe result");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void shiftCraftDeliversCompleteOutputAndConsumesOneRecipe(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        TransmutationContainer menu = stickCraftingMenu(player, 80, 60);
        ItemStack moved = menu.quickMoveStack(player, NeoForgeTransmutationTable.RESULT_SLOT);
        check(moved.is(Items.STICK) && moved.getCount() == 4,
                "Shift extraction must report the complete four-stick result");
        check(menu.getSlot(62).getItem().is(Items.STICK) && menu.getSlot(62).getItem().getCount() == 64
                        && player.getInventory().countItem(Items.STICK) == 64,
                "The exact remaining inventory capacity must receive all four sticks");
        check(menu.getSlot(63).getItem().isEmpty() && menu.getSlot(66).getItem().isEmpty()
                        && menu.getSlot(72).getItem().isEmpty(),
                "Successful extraction must consume one recipe and clear its exhausted result");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativeMenuHonorsLimitedCraftingAndActualRecipeBook(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        GameRules.BooleanValue limitedCrafting = player.level().getGameRules().getRule(GameRules.RULE_LIMITED_CRAFTING);
        boolean originalRule = limitedCrafting.get();
        TransmutationContainer menu = null;
        try {
            var recipe = server.getRecipeManager().byKey(ResourceLocation.parse("minecraft:stick")).orElseThrow();
            check(!player.getRecipeBook().contains(recipe), "The fresh real player must not already know the stick recipe");
            limitedCrafting.set(true, server);
            menu = new TransmutationContainer(81, player.getInventory());
            menu.getSlot(63).set(new ItemStack(Items.OAK_PLANKS));
            menu.getSlot(66).set(new ItemStack(Items.OAK_PLANKS));
            check(menu.getSlot(72).getItem().isEmpty(),
                    "Limited crafting must hide an unknown recipe from the added native-menu result slot");

            limitedCrafting.set(false, server);
            menu.slotsChanged(menu.getSlot(63).container);
            check(menu.getSlot(72).getItem().is(Items.STICK) && menu.getSlot(72).getItem().getCount() == 4,
                    "Disabling limited crafting must allow a valid recipe without teaching it");
            limitedCrafting.set(true, server);
            menu.slotsChanged(menu.getSlot(63).container);
            check(menu.getSlot(72).getItem().isEmpty(),
                    "Re-enabling limited crafting must clear the previously assembled unknown result");

            player.awardRecipes(List.of(recipe));
            menu.slotsChanged(menu.getSlot(63).container);
            check(player.getRecipeBook().contains(recipe) && menu.getSlot(72).getItem().is(Items.STICK)
                            && menu.getSlot(72).getItem().getCount() == 4,
                    "The actual recipe-book unlock must allow the same recipe under limited crafting");
        } finally {
            limitedCrafting.set(originalRule, server);
            try {
                if (menu != null) menu.removed(player);
            } finally {
                logout(server, player);
            }
        }
        helper.succeed();
    }

    private static TransmutationContainer stickCraftingMenu(Player player, int containerId, int existingSticks) {
        TransmutationContainer menu = new TransmutationContainer(containerId, player.getInventory());
        for (int index = 27; index < NeoForgeTransmutationTable.NATIVE_SLOT_COUNT; index++)
            menu.getSlot(index).set(new ItemStack(Items.COBBLESTONE, 64));
        menu.getSlot(62).set(new ItemStack(Items.STICK, existingSticks));
        menu.getSlot(63).set(new ItemStack(Items.OAK_PLANKS));
        menu.getSlot(66).set(new ItemStack(Items.OAK_PLANKS));
        check(menu.getSlot(72).getItem().is(Items.STICK) && menu.getSlot(72).getItem().getCount() == 4,
                "The native crafting callbacks must assemble the vanilla four-stick result");
        return menu;
    }

    private static ServerPlayer login(MinecraftServer server) {
        var profile = new GameProfile(UUID.randomUUID(), "crafting-player");
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
        try {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
