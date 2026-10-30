/**
 * 【文件职责】用真实命令分发器验证定价命令的权限、数值边界、控制台/函数来源及文件重载事务。
 * 【变更记录 #1】2026-10-23 17:34:49（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。
 * - 新增：生存二级 OP、一级 OP、普通创造玩家、真实控制台与原生函数来源的命令返回值验收。
 * - 修改：通过已注册的 primordial 子树写价/置零/恢复、手动改文件后重载，并拒绝坏根、未知注册项和模式文本。
 * - 用途：登录完成后才备份原生映射；finally 恢复价表、dirty、燃料、文件和查询配置，不污染其他回收测试。
 * 【变更记录 #2】2026-10-23 17:41:52（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：把 Brigadier 在 word 参数拒绝分数时抛出的语法异常误判为生产失败的断言。
 * - 新增：非法输入允许真实语法拒绝或返回零，并继续核验文件和报价序号完全不变。
 * - 修改：物品与化学品分数沿用 Forge 的 word 语法，流体精确分数仍走正式 greedyString 数值校验。
 * - 用途：区分解析阶段和事务阶段的正常拒绝，不放宽实际命令边界。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.service.EmcAmount;
import com.primordial.value.PriceMode;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.FuelMapper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderSet;
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
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePriceCommandGameTests {
    private static final ResourceLocation HOE = ResourceLocation.parse("minecraft:golden_hoe");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");

    private NeoForgePriceCommandGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_price_commands", setupTicks = 0, required = true, timeoutTicks = 400)
    public static void preservesForgeSyntaxAndRealCommandSourcePermissions(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try (Backup ignored = new Backup()) {
            var dispatcher = server.getCommands().getDispatcher();
            player.setGameMode(GameType.SURVIVAL);
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
            var admin = player.createCommandSourceStack().withSuppressedOutput();
            check(admin.hasPermission(2) && !player.isCreative(), "The authorized player source must be a real survival level-two OP");
            check(dispatcher.execute("primordial set_value item minecraft:golden_hoe 17", admin) == 1
                    && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 17,
                    "The registered item command must return one and change the actual native mapping");
            check(dispatcher.execute("primordial set_value item minecraft:golden_hoe 0", admin) == 1
                    && NeoForgePriceEditing.customItemValue(HOE).orElse(-1) == 0
                    && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 0,
                    "Explicit command zero must persist as disabled instead of falling back to native price");
            check(dispatcher.execute("primordial set_value item minecraft:golden_hoe 17", admin) == 1
                    && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 17,
                    "A subsequent valid numeric command must restore a disabled item");
            byte[] stable = Files.readAllBytes(NeoForgeProjectEItemPrices.file());
            long revision = NeoForgePriceEditing.revision();
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 1, false));
            var operatorOne = player.createCommandSourceStack().withSuppressedOutput();
            check(operatorOne.hasPermission(1) && !operatorOne.hasPermission(2), "The real OP list must produce a level-one source");
            denied(dispatcher, operatorOne, "primordial set_value item minecraft:golden_hoe 99");
            denied(dispatcher, operatorOne, "primordial reload_values");
            check(!NeoForgePriceEditing.mutate(operatorOne, (byte) 0, HOE, "99").success(),
                    "The shared transaction must also reject a level-one direct source");
            server.getPlayerList().deop(player.getGameProfile());
            player.setGameMode(GameType.CREATIVE);
            var ordinary = player.createCommandSourceStack().withSuppressedOutput();
            denied(dispatcher, ordinary, "primordial set_value item minecraft:golden_hoe 99");
            denied(dispatcher, ordinary, "primordial reload_values");
            check(Arrays.equals(stable, Files.readAllBytes(NeoForgeProjectEItemPrices.file()))
                    && NeoForgePriceEditing.revision() == revision && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 17,
                    "Rejected lower-permission and creative sources must leave both disk and native price unchanged");
            var console = server.createCommandSourceStack().withSuppressedOutput();
            check(console.getEntity() == null && console.hasPermission(2)
                    && dispatcher.execute("primordial set_value fluid minecraft:water 1/3", console) == 1
                    && NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).equals(EmcAmount.parse("1/3000")),
                    "The real server console must be able to save an exact non-player fluid price");
            var function = server.getFunctions().getGameLoopSender();
            check(function.getEntity() == null && function.hasPermission(2)
                    && dispatcher.execute("primordial set_value fluid minecraft:water 0", function) == 1
                    && NeoForgePriceEditing.snapshot((byte) 1, WATER).mode() == PriceMode.DISABLED,
                    "The native function command source must return success and disable water with numeric zero");
            check(dispatcher.execute("primordial set_value fluid minecraft:water 1/3", function) == 1
                    && NeoForgePriceEditing.snapshot((byte) 1, WATER).price().equals("1/3"),
                    "The function source must restore a zero price without a real player requirement");
            byte[] itemStable = Files.readAllBytes(NeoForgeProjectEItemPrices.file());
            byte[] mediaStable = Files.readAllBytes(NeoForgeMediaPriceEditing.file());
            revision = NeoForgePriceEditing.revision();
            for (String invalid : List.of("item minecraft:golden_hoe -1", "item minecraft:golden_hoe 1/3",
                    "item minecraft:golden_hoe 9223372036854775808", "item minecraft:golden_hoe inherit",
                    "fluid minecraft:water 1/0", "fluid minecraft:water buy_only", "fluid minecraft:water -1",
                    "item primordial:missing_price_item 5", "fluid primordial:missing_price_fluid 5",
                    "item minecraft:air 5", "fluid minecraft:empty 5", "gas primordial:missing_price_chemical 5"))
                check(rejected(dispatcher, console, "primordial set_value " + invalid),
                        "Invalid numeric or unknown resource input must return failure: " + invalid);
            check(Arrays.equals(itemStable, Files.readAllBytes(NeoForgeProjectEItemPrices.file()))
                    && Arrays.equals(mediaStable, Files.readAllBytes(NeoForgeMediaPriceEditing.file()))
                    && NeoForgePriceEditing.revision() == revision, "Rejected command arguments must not write or publish prices");
            if (ModList.get().isLoaded("mekanism")) {
                check(dispatcher.execute("primordial set_value gas mekanism:hydrogen 5", function) == 1
                        && NeoForgePriceEditing.snapshot((byte) 2, HYDROGEN).price().equals("5"),
                        "The legacy gas command must use the actual unified chemical registry and integer per-mB price");
                mediaStable = Files.readAllBytes(NeoForgeMediaPriceEditing.file());
                revision = NeoForgePriceEditing.revision();
                for (String invalid : List.of("1/2", "1.0", "inherit", "-1", "9".repeat(4097)))
                    check(rejected(dispatcher, console, "primordial set_value gas mekanism:hydrogen " + invalid),
                            "Chemical command syntax must retain Forge's bounded digits-only contract");
                check(Arrays.equals(itemStable, Files.readAllBytes(NeoForgeProjectEItemPrices.file()))
                        && Arrays.equals(mediaStable, Files.readAllBytes(NeoForgeMediaPriceEditing.file()))
                        && NeoForgePriceEditing.revision() == revision,
                        "Syntax and numeric chemical rejections must not write either file or publish a new price");
            } else check(dispatcher.execute("primordial set_value gas mekanism:hydrogen 5", function) == 0,
                    "Absent Mekanism must reject a chemical command without linking optional classes");
            var root = dispatcher.getRoot().getChild("primordial");
            check(root.getChild("knowledge") != null && root.getChild("stage") != null && root.getChild("reload_values") != null,
                    "Price event registration must preserve existing administrator subtrees");
        } finally {
            try { server.getPlayerList().deop(player.getGameProfile()); }
            finally { logout(server, player); }
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_price_commands", setupTicks = 0, required = true, timeoutTicks = 400)
    public static void reloadsAdministratorFilesAndRejectsMalformedCandidates(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server);
        try (Backup ignored = new Backup()) {
            var dispatcher = server.getCommands().getDispatcher();
            var source = server.getFunctions().getGameLoopSender();
            check(dispatcher.execute("primordial set_value item minecraft:golden_hoe 17", source) == 1
                    && dispatcher.execute("primordial set_value fluid minecraft:water 1/3", source) == 1,
                    "A real function source must first establish valid prices through the registered command tree");
            Path itemFile = NeoForgeProjectEItemPrices.file();
            Path mediaFile = NeoForgeMediaPriceEditing.file();
            JsonObject item = JsonParser.parseString(Files.readString(itemFile, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean edited = false;
            for (var raw : item.getAsJsonArray("entries")) {
                JsonObject entry = raw.getAsJsonObject();
                var key = NSSItem.CODEC.codec().parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), entry).getOrThrow();
                if (key.equals(NSSItem.createItem(Items.GOLDEN_HOE))) { entry.addProperty("emc", 29); edited = true; }
            }
            check(edited, "The item fixture must update the actual native base-item entry");
            item.addProperty("command_reload_extension", "原文保留");
            JsonObject media = JsonParser.parseString(Files.readString(mediaFile, StandardCharsets.UTF_8)).getAsJsonObject();
            media.getAsJsonObject("fluid_values").addProperty(WATER.toString(), "2/7");
            media.getAsJsonObject("fluid_modes").addProperty(WATER.toString(), "buy_only");
            media.addProperty("command_reload_extension", "原文保留");
            Files.writeString(itemFile, item.toString(), StandardCharsets.UTF_8);
            Files.writeString(mediaFile, media.toString(), StandardCharsets.UTF_8);
            check(dispatcher.execute("primordial reload_values", source) == 1
                    && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 29
                    && NeoForgePriceEditing.customItemValue(HOE).orElse(-1) == 29
                    && NeoForgePriceEditing.snapshot((byte) 1, WATER).price().equals("2/7")
                    && NeoForgePriceEditing.snapshot((byte) 1, WATER).mode() == PriceMode.BUY_ONLY
                    && NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).equals(EmcAmount.parse("1/3500"))
                    && NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.WATER, 1)).isZero(),
                    "Reload must recalculate native item EMC and publish the exact media mode and amount together");
            byte[] goodItem = Files.readAllBytes(itemFile);
            byte[] goodMedia = Files.readAllBytes(mediaFile);
            long revision = NeoForgePriceEditing.revision();
            Files.writeString(mediaFile, "[]", StandardCharsets.UTF_8);
            check(dispatcher.execute("primordial reload_values", source) == 0
                    && Files.readString(mediaFile).equals("[]") && Arrays.equals(goodItem, Files.readAllBytes(itemFile))
                    && NeoForgePriceEditing.revision() == revision && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 29
                    && NeoForgePriceEditing.snapshot((byte) 1, WATER).price().equals("2/7"),
                    "Malformed media must reject the whole reload without overwriting disk or publishing a failed snapshot");
            Files.write(mediaFile, goodMedia);
            Files.writeString(itemFile, "[]", StandardCharsets.UTF_8);
            check(dispatcher.execute("primordial reload_values", source) == 0
                    && Files.readString(itemFile).equals("[]") && Arrays.equals(goodMedia, Files.readAllBytes(mediaFile))
                    && NeoForgePriceEditing.revision() == revision && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 29
                    && NeoForgePriceEditing.snapshot((byte) 1, WATER).price().equals("2/7"),
                    "Malformed native EMC must preserve the current native mapping and media snapshot as well");
            Files.write(itemFile, goodItem);
            check(dispatcher.execute("primordial reload_values", source) == 1
                    && JsonParser.parseString(Files.readString(itemFile)).getAsJsonObject().get("command_reload_extension").getAsString().equals("原文保留")
                    && JsonParser.parseString(Files.readString(mediaFile)).getAsJsonObject().get("command_reload_extension").getAsString().equals("原文保留"),
                    "A repaired candidate must reload successfully while preserving unrelated administrator JSON");
        } finally { logout(server, player); }
        helper.succeed();
    }

    private static boolean rejected(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String command) {
        try { return dispatcher.execute(command, source) == 0; }
        catch (CommandSyntaxException expected) { return true; }
    }

    private static void denied(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String command) {
        boolean denied = false;
        try { dispatcher.execute(command, source); } catch (CommandSyntaxException expected) { denied = true; }
        check(denied, "The real command tree must hide this administrator node from a lower permission source");
    }

    private static final class Backup implements AutoCloseable {
        private final Path itemFile = NeoForgeProjectEItemPrices.file();
        private final Path mediaFile = NeoForgeMediaPriceEditing.file();
        private final byte[] itemBytes;
        private final byte[] mediaBytes;
        private final CustomEMCParser.CustomEMCFile entries = CustomEMCParser.currentEntries;
        private final Object2LongOpenHashMap<ItemInfo> prices = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        private final HolderSet<Item> fuel = FuelMapper.getFuelMap();
        private final Field dirty;
        private final boolean wasDirty;
        private final boolean fluidBuy = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        private final boolean fluidSell = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        private final List<? extends String> fluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());

        Backup() throws IOException, ReflectiveOperationException {
            itemBytes = Files.exists(itemFile) ? Files.readAllBytes(itemFile) : null;
            mediaBytes = Files.exists(mediaFile) ? Files.readAllBytes(mediaFile) : null;
            dirty = CustomEMCParser.class.getDeclaredField("dirty");
            dirty.setAccessible(true);
            wasDirty = dirty.getBoolean(null);
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
        }

        @Override public void close() throws IOException, IllegalAccessException {
            try {
                CustomEMCParser.currentEntries = entries;
                dirty.setBoolean(null, wasDirty);
                EMCMappingHandler.updateEmcValues(prices);
                FuelMapper.setFuelMap(fuel);
                if (itemBytes == null) Files.deleteIfExists(itemFile); else NeoForgePriceFiles.atomicWrite(itemFile, itemBytes);
                if (mediaBytes == null) Files.deleteIfExists(mediaFile); else NeoForgePriceFiles.atomicWrite(mediaFile, mediaBytes);
            } finally {
                NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(fluidBuy);
                NeoForgeServerConfig.FLUID_SALE_ENABLED.set(fluidSell);
                NeoForgeServerConfig.FLUID_BLACKLIST.set(fluidBlacklist);
                NeoForgeRecoveryPrices.reload();
                NeoForgeRecoveryPrices.invalidatePrices();
            }
        }
    }

    private static ServerPlayer login(MinecraftServer server) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "price-command");
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        try { if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player); }
        finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
