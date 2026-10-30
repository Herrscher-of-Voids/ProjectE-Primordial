/**
 * 【文件职责】通过 write/read/verify 三个独立服务端进程，验收正式定价器写盘、原生载入和撤销后的持久化。
 * 【变更记录 #1】2026-10-23 17:16:16（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。
 * - 新增：显式系统属性启用的一项重启夹具，真实二级管理员依次保存物品 17、水桶 1/3 仅购买和可选氢 5。
 * - 修改：原生默认价只在首次真实登录后记录到测试世界的期望元数据，后续进程不手动重载或注入价格。
 * - 用途：独立批次串行检查 ProjectE 与介质原生文件；finally 注销管理员并恢复查询配置，不重写知识或余额。
 * 【变更记录 #2】2026-10-23 17:23:30（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：持久化夹具与普通回收测试共用模板命名空间。
 * - 新增：primordial_pricing_persistence 独立测试模板，与原生 enabledGameTestNamespaces 筛选配套。
 * - 修改：三次重启仅注册所选持久化阶段，避免已持久化的 buy_only 水价污染其他批次的默认价假设。
 * - 用途：不修改旧测试或运行时价格语义；新命名空间及空模板仅属于 gametest 源集。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.api.MediaKnowledgeApi;
import com.primordial.neoforge.api.MediaKnowledgeApi.Media;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.service.EmcAmount;
import com.primordial.value.PriceMode;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePricingPersistenceGameTests {
    public static final String TEMPLATE_NAMESPACE = "primordial_pricing_persistence";
    private static final UUID OWNER = UUID.fromString("10becb6a-1582-471a-b30e-4001009063ae");
    private static final ResourceLocation HOE = ResourceLocation.parse("minecraft:golden_hoe");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");
    private static final EmcAmount THIRD = EmcAmount.parse("1/3");
    private static final EmcAmount FIVE = EmcAmount.parse("5");

    private NeoForgePricingPersistenceGameTests() {}

    @GameTestGenerator
    public static List<TestFunction> controlledRestarts() {
        String phase = System.getProperty("primordial.test.pricePersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        if (!Set.of("write", "read", "verify").contains(phase))
            throw new IllegalArgumentException("Unknown price persistence phase: " + phase);
        // GameTestRegistry 对生成器按结构命名空间筛选，不依赖 HashMap 中的批次先后。
        return List.of(new TestFunction("pricing_persistence", "pricing_persistence." + phase,
                TEMPLATE_NAMESPACE + ":capability_proxy_empty", 400, 0L, true,
                helper -> run(helper, phase)));
    }

    private static void run(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        boolean chemicals = ModList.get().isLoaded("mekanism");
        ServerPlayer player = null;
        try (QuerySettings ignored = new QuerySettings()) {
            check(server.getPlayerList().getPlayer(OWNER) == null, "The isolated price administrator must start offline in each JVM");
            player = login(server);
            player.setGameMode(GameType.SURVIVAL);
            // GameTestServer 的默认 op 等级较低，必须使用实际二级条目。
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
            check(player.hasPermissions(2), "A real survival administrator must have level two authority");
            var knowledge = knowledge(player);
            NativeAccount before = account(server, knowledge);
            switch (phase) {
                case "write" -> {
                    check(NeoForgePriceEditing.customItemValue(HOE).isEmpty(), "The isolated write directory must start without a golden hoe override");
                    long defaultPrice = IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE);
                    check(defaultPrice > 0 && defaultPrice != 17, "First login must initialize a distinct native default hoe price");
                    saveExpectation(server, defaultPrice, chemicals);
                    mutate(server, player, (byte) 0, HOE, "17");
                    mutate(server, player, (byte) 1, WATER, "1/3");
                    mutate(server, player, (byte) 1, WATER, "buy_only");
                    if (chemicals) mutate(server, player, (byte) 2, HYDROGEN, "5");
                    assertCustom(chemicals);
                }
                case "read" -> {
                    long defaultPrice = expectation(server, chemicals);
                    // 这里不调用 reload、load 或原生 map：断言的正是新 JVM 的正常载入结果。
                    assertCustom(chemicals);
                    mutate(server, player, (byte) 0, HOE, "inherit");
                    mutate(server, player, (byte) 1, WATER, "inherit");
                    if (chemicals) mutate(server, player, (byte) 2, HYDROGEN, "inherit");
                    assertInherited(defaultPrice, chemicals);
                }
                case "verify" -> assertInherited(expectation(server, chemicals), chemicals);
                default -> throw new IllegalArgumentException("Unknown price persistence phase: " + phase);
            }
            check(before.equals(account(server, knowledge)), "Price edits and native restarts must not grant, clear or rewrite this account's EMC or knowledge");
        } catch (IOException failure) {
            throw new GameTestAssertException("Cannot read/write isolated pricing expectation: " + failure.getMessage());
        } finally {
            if (player != null) {
                try { server.getPlayerList().deop(player.getGameProfile()); }
                finally { logout(server, player); }
            }
        }
        LogUtils.getLogger().info("[Primordial] Price persistence phase {} passed (chemical registry installed: {})", phase, chemicals);
        helper.succeed();
    }

    private static void assertCustom(boolean chemicals) throws IOException {
        var item = NeoForgePriceEditing.snapshot((byte) 0, HOE);
        check(item.available() && item.mode() == PriceMode.CUSTOM_VALUE && item.price().equals("17")
                && NeoForgePriceEditing.customItemValue(HOE).orElse(-1) == 17
                && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == 17,
                "Persisted native custom EMC must control both editor state and the actual ProjectE mapping");
        var fluid = NeoForgePriceEditing.snapshot((byte) 1, WATER);
        check(fluid.available() && fluid.mode() == PriceMode.BUY_ONLY && fluid.price().equals("1/3")
                && NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).equals(EmcAmount.parse("1/3000"))
                && NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.WATER, 1)).isZero(),
                "Restart must retain an exact bucket third while enforcing purchase-only direction");
        assertStoredMedia(chemicals, PriceMode.BUY_ONLY, PriceMode.CUSTOM_VALUE);
        if (chemicals) {
            var chemical = NeoForgePriceEditing.snapshot((byte) 2, HYDROGEN);
            check(chemical.available() && chemical.mode() == PriceMode.CUSTOM_VALUE && chemical.price().equals("5")
                    && NeoForgeRecoveryPrices.chemicalPurchase(HYDROGEN, tag -> false).equals(FIVE)
                    && NeoForgeRecoveryPrices.chemical(HYDROGEN, tag -> false).equals(FIVE),
                    "The real optional unified chemical must retain integer five per mB in both directions");
        } else check(!NeoForgePriceEditing.snapshot((byte) 2, HYDROGEN).available(),
                "A missing optional registry must remain unavailable across restarts");
    }

    private static void assertInherited(long defaultPrice, boolean chemicals) throws IOException {
        var item = NeoForgePriceEditing.snapshot((byte) 0, HOE);
        check(item.available() && item.mode() == PriceMode.INHERIT_NATIVE
                && NeoForgePriceEditing.customItemValue(HOE).isEmpty()
                && item.price().equals(Long.toString(defaultPrice))
                && IEMCProxy.INSTANCE.getValue(Items.GOLDEN_HOE) == defaultPrice,
                "Removing the native override must survive restart and restore the original calculated default");
        var fluid = NeoForgePriceEditing.snapshot((byte) 1, WATER);
        check(fluid.available() && fluid.mode() == PriceMode.INHERIT_NATIVE && fluid.price().equals("1000")
                && NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).equals(EmcAmount.ONE)
                && NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.WATER, 1)).equals(EmcAmount.ONE),
                "Water inheritance must survive restart with default 1000 per bucket in both directions");
        assertStoredMedia(chemicals, PriceMode.INHERIT_NATIVE, PriceMode.INHERIT_NATIVE);
        if (chemicals) {
            var chemical = NeoForgePriceEditing.snapshot((byte) 2, HYDROGEN);
            check(chemical.available() && chemical.mode() == PriceMode.INHERIT_NATIVE && chemical.price().equals("1")
                    && NeoForgeRecoveryPrices.chemicalPurchase(HYDROGEN, tag -> false).equals(EmcAmount.ONE)
                    && NeoForgeRecoveryPrices.chemical(HYDROGEN, tag -> false).equals(EmcAmount.ONE),
                    "Chemical inheritance must survive restart with the native one per mB default");
        }
    }

    private static void assertStoredMedia(boolean chemicals, PriceMode fluidMode, PriceMode chemicalMode) throws IOException {
        JsonObject root = JsonParser.parseString(Files.readString(NeoForgeMediaPriceEditing.file(), StandardCharsets.UTF_8)).getAsJsonObject();
        var fluid = NeoForgeRecoveryPrices.mediaState(true, WATER);
        check(fluid.readable() && fluid.mode() == fluidMode && fluid.retainedPrice().equals(THIRD)
                && root.getAsJsonObject("fluid_values").get(WATER.toString()).getAsString().equals("1/3")
                && PriceMode.parse(root.getAsJsonObject("fluid_modes").get(WATER.toString()).getAsString()) == fluidMode,
                "Persisted water mode must remain separate from its retained exact administrator price");
        if (chemicals) {
            var chemical = NeoForgeRecoveryPrices.mediaState(false, HYDROGEN);
            check(chemical.readable() && chemical.mode() == chemicalMode && chemical.retainedPrice().equals(FIVE)
                    && root.getAsJsonObject("gas_values").get(HYDROGEN.toString()).getAsString().equals("5")
                    && PriceMode.parse(root.getAsJsonObject("gas_modes").get(HYDROGEN.toString()).getAsString()) == chemicalMode,
                    "Persisted unified chemical inheritance must keep the administrator's integer five available");
        }
    }

    private static void mutate(MinecraftServer server, ServerPlayer player, byte kind, ResourceLocation id, String value) {
        check(NeoForgePriceEditing.mutate(server, player, kind, id, value).success(),
                "The real authorized backend must commit " + id + " = " + value);
    }

    private static Path expectationFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("primordial-pricing-persistence-expectation.json");
    }

    private static void saveExpectation(MinecraftServer server, long nativeDefault, boolean chemicals) throws IOException {
        JsonObject fixture = new JsonObject();
        fixture.addProperty("native_golden_hoe_default", nativeDefault);
        fixture.addProperty("mekanism", chemicals);
        Files.writeString(expectationFile(server), fixture.toString(), StandardCharsets.UTF_8);
    }

    private static long expectation(MinecraftServer server, boolean chemicals) throws IOException {
        JsonObject fixture = JsonParser.parseString(Files.readString(expectationFile(server), StandardCharsets.UTF_8)).getAsJsonObject();
        check(fixture.get("mekanism").getAsBoolean() == chemicals, "All controlled phases must use the same optional mod set");
        return fixture.get("native_golden_hoe_default").getAsLong();
    }

    private record NativeAccount(BigInteger emc, Set<ItemInfo> items, boolean full, boolean water, boolean hydrogen) {}

    private static NativeAccount account(MinecraftServer server, IKnowledgeProvider knowledge) {
        return new NativeAccount(knowledge.getEmc(), Set.copyOf(knowledge.getKnowledge()), knowledge.hasFullKnowledge(),
                MediaKnowledgeApi.hasLearned(server, OWNER, Media.FLUID, WATER),
                MediaKnowledgeApi.hasLearned(server, OWNER, Media.GAS, HYDROGEN));
    }

    private static IKnowledgeProvider knowledge(ServerPlayer player) {
        var knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(knowledge != null, "The real administrator must have a native ProjectE account");
        return knowledge;
    }

    private static ServerPlayer login(MinecraftServer server) {
        GameProfile profile = new GameProfile(OWNER, "pricing-restart");
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            if (server.getPlayerList().getPlayer(OWNER) == player) server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        try { if (server.getPlayerList().getPlayer(OWNER) == player) server.getPlayerList().remove(player); }
        finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }

    private static final class QuerySettings implements AutoCloseable {
        private final boolean fluidBuy = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        private final boolean fluidSell = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        private final boolean chemicalBuy = NeoForgeServerConfig.GAS_PURCHASE_ENABLED.get();
        private final boolean chemicalSell = NeoForgeServerConfig.GAS_SALE_ENABLED.get();
        private final List<? extends String> fluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        private final List<? extends String> chemicalBlacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());

        QuerySettings() {
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
        }

        @Override public void close() {
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(fluidBuy);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(fluidSell);
            NeoForgeServerConfig.GAS_PURCHASE_ENABLED.set(chemicalBuy);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(chemicalSell);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(fluidBlacklist);
            NeoForgeServerConfig.GAS_BLACKLIST.set(chemicalBlacklist);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
