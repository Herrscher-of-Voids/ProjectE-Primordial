/**
 * 【文件职责】用真实管理员、原生 ProjectE 映射及价格文件验收定价事务和精确介质模式。
 * 【变更记录 #1】2026-10-23 17:05:33（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：原生改价/撤销、待保存命令合并、组件项/未知字段保留、凭证零价和拒写边界。
 * - 修改：介质在独立临时目录覆盖五模式、精确桶价、整数化学品、另一通道及坏根保护。
 * - 用途：同步测试 finally 恢复文件、原生价表/dirty/燃料和配置；不把同进程读回冒充跨 JVM 或客户端验收。
 * 【变更记录 #2】2026-10-23 17:09:56（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：把人工保留价等同有效价的模式断言。
 * - 新增：每种模式的界面快照与原价独立断言。
 * - 修改：继承明确读回默认桶价，禁用明确读回零，恢复定制保留精确分数。
 * - 用途：防止定价器显示旧人工值而误导管理员判断当前生效价格。
 * 【变更记录 #3】2026-10-23 17:12:25（第 3 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：在真实原生映射事件注入失败，核对文件、价表、燃料、dirty 和已学物品回滚。
 * - 修改：注入监听器仅包围单次同步保存并在 finally 解除。
 * - 用途：实际执行失败恢复分支，随后成功零价仍必须保留原生知识清理。
 * 【变更记录 #4】2026-10-23 17:13:09（第 4 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：沿用 GameTestServer 默认低权限 OP，以及首次登录前备份空原生映射的夹具。
 * - 新增：真实二级 ServerOpListEntry 和登录初始化完成后的原生快照。
 * - 修改：finally 恢复登录后有效价表，避免已初始化 ProjectE 被测试恢复为空表。
 * - 用途：准确验证生存管理员权限，不污染后续回收/交易测试的原生计价环境。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.registry.NeoForgeCertificates;
import com.primordial.service.EmcAmount;
import com.primordial.value.PriceMode;

import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.FuelMapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePriceEditingGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation DIAMOND = ResourceLocation.parse("minecraft:diamond");
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");

    private NeoForgePriceEditingGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_admin_prices", setupTicks = 0, required = true, timeoutTicks = 400)
    public static void commitsNativeItemChangesAndRestoresDefaultsWithoutLosingAdministratorData(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path file = NeoForgeProjectEItemPrices.file();
        ServerPlayer player = login(server);
        byte[] original = Files.exists(file) ? Files.readAllBytes(file) : null;
        var originalEntries = CustomEMCParser.currentEntries;
        var originalMap = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        var originalFuel = FuelMapper.getFuelMap();
        Field dirty = CustomEMCParser.class.getDeclaredField("dirty");
        dirty.setAccessible(true);
        boolean originalDirty = dirty.getBoolean(null);
        try {
            player.setGameMode(GameType.SURVIVAL);
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
            check(player.hasPermissions(2) && !player.isCreative(), "The real survival OP must be allowed to edit");
            long defaultDiamond = EMCMappingHandler.getStoredEmcValue(ItemInfo.fromItem(Items.DIAMOND));
            JsonObject root = new JsonObject();
            root.addProperty("comment", "管理员保留说明");
            root.add("administrator_extension", JsonParser.parseString("{\"keep\":[1,true,\"中文\"]}"));
            JsonArray entries = new JsonArray();
            ItemStack named = new ItemStack(Items.EMERALD);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("price-component-preserved"));
            entries.add(entry(server, NSSItem.createItem(Items.EMERALD), 4321L));
            entries.add(entry(server, NSSItem.createItem(named), 76543L));
            entries.get(0).getAsJsonObject().add("administrator_note", JsonParser.parseString("{\"keep\":true}"));
            root.add("entries", entries);
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
            CustomEMCParser.currentEntries = CustomEMCParser.CustomEMCFile.CODEC.parse(
                    server.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow();
            dirty.setBoolean(null, false);
            // 原生命令尚未 flush 的另一物品必须一起保留，不能为编辑一个样本而丢失。
            CustomEMCParser.addToFile(NSSItem.createItem(Items.GOLD_INGOT), 2468L);
            long revision = NeoForgeRecoveryPrices.revision();
            var saved = NeoForgePriceEditing.mutate(server, player, (byte) 0, DIAMOND, "12345");
            check(saved.success() && saved.state().price().equals("12345")
                    && saved.state().mode() == PriceMode.CUSTOM_VALUE && NeoForgeRecoveryPrices.revision() > revision,
                    "Production mutation must persist, publish native price and invalidate old trade quotes");
            check(IEMCProxy.INSTANCE.getValue(Items.DIAMOND) == 12345L
                    && IEMCProxy.INSTANCE.getSellValue(ItemInfo.fromItem(Items.DIAMOND)) > 0,
                    "Both native purchase and native sell must use the edited ProjectE mapping");
            JsonObject actual = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            check(actual.get("administrator_extension").equals(root.get("administrator_extension")),
                    "Unknown root values must survive native item editing");
            check(actual.getAsJsonArray("entries").asList().stream().anyMatch(value ->
                    value.getAsJsonObject().has("administrator_note")), "Unknown fields inside another native entry must survive");
            check(CustomEMCParser.currentEntries.entries().getLong(NSSItem.createItem(Items.GOLD_INGOT)) == 2468L
                    && CustomEMCParser.currentEntries.entries().getLong(NSSItem.createItem(named)) == 76543L,
                    "Pending native commands and component-specific administrator prices must both remain");
            check(!dirty.getBoolean(null), "A committed atomic write must not later be overwritten by native dirty flush");
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null && knowledge.addKnowledge(new ItemStack(Items.DIAMOND)),
                    "The real player must know the item before an induced failed zero-price remap");
            byte[] beforeRollback = Files.readAllBytes(file);
            var beforeRollbackMap = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
            var beforeRollbackFuel = FuelMapper.getFuelMap();
            Consumer<EMCRemapEvent> rejectRemap = event -> { throw new IllegalStateException("price transaction rollback probe"); };
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EMCRemapEvent.class, rejectRemap);
            try {
                check(!NeoForgePriceEditing.mutate(server, player, (byte) 0, DIAMOND, "0").success(),
                        "A failed native remap event must be reported as a failed save");
            } finally { NeoForge.EVENT_BUS.unregister(rejectRemap); }
            check(java.util.Arrays.equals(beforeRollback, Files.readAllBytes(file))
                    && beforeRollbackMap.equals(EMCMappingHandler.createPacketData().data())
                    && beforeRollbackFuel.equals(FuelMapper.getFuelMap())
                    && knowledge.hasKnowledge(new ItemStack(Items.DIAMOND)) && !dirty.getBoolean(null),
                    "A failed remap must restore disk, native map, fuel, dirty flag and knowledge pruned during its rejected zero");
            revision = NeoForgeRecoveryPrices.revision();
            check(NeoForgePriceEditing.mutate(server, player, (byte) 0, DIAMOND, "inherit").success()
                    && NeoForgeProjectEItemPrices.customItemValue(DIAMOND).isEmpty()
                    && IEMCProxy.INSTANCE.getValue(Items.DIAMOND) == defaultDiamond
                    && NeoForgeRecoveryPrices.revision() > revision,
                    "Inheritance must remove only the base override and recalculate the actual default recipe price");
            byte[] stable = Files.readAllBytes(file);
            revision = NeoForgeRecoveryPrices.revision();
            for (String invalid : List.of("-1", "1/3", "9223372036854775808", "buy_only"))
                check(!NeoForgePriceEditing.mutate(server, player, (byte) 0, DIAMOND, invalid).success(),
                        "Item fractional, negative, overflowing and direction-only edits must fail");
            check(java.util.Arrays.equals(stable, Files.readAllBytes(file)) && NeoForgeRecoveryPrices.revision() == revision,
                    "Rejected item input must not write or publish a new price generation");
            ResourceLocation certificate = BuiltInRegistries.ITEM.getKey(NeoForgeCertificates.CERTIFICATES.get(Kind.FLUID).get());
            check(knowledge != null, "The real ProjectE player attachment must exist");
            knowledge.addKnowledge(Kind.FLUID.sample());
            check(NeoForgePriceEditing.mutate(server, player, (byte) 0, certificate, "0").success()
                    && NeoForgePriceEditing.customItemValue(certificate).orElse(-1) == 0
                    && IEMCProxy.INSTANCE.getValue(Kind.FLUID.sample()) == 0
                    && !knowledge.hasKnowledge(Kind.FLUID.sample()),
                    "Explicit zero must survive certificate remapping and keep the native stale-knowledge prune");
            check(NeoForgePriceEditing.mutate(server, player, (byte) 0, certificate, "inherit").success(),
                    "Removing a certificate zero must restore its normal required-knowledge fallback");
            Files.writeString(file, "[]", StandardCharsets.UTF_8);
            long beforeBad = IEMCProxy.INSTANCE.getValue(Items.DIAMOND);
            check(!NeoForgePriceEditing.mutate(server, player, (byte) 0, DIAMOND, "99").success()
                    && Files.readString(file).equals("[]") && IEMCProxy.INSTANCE.getValue(Items.DIAMOND) == beforeBad,
                    "A broken native root must stay intact and leave the published price untouched");
            server.getPlayerList().deop(player.getGameProfile());
            check(!NeoForgePriceEditing.mutate(server, player, (byte) 0, DIAMOND, "100").success()
                    && Files.readString(file).equals("[]"), "Losing OP must immediately deny direct backend writes");
        } finally {
            server.getPlayerList().deop(player.getGameProfile());
            logout(server, player);
            CustomEMCParser.currentEntries = originalEntries;
            dirty.setBoolean(null, originalDirty);
            EMCMappingHandler.updateEmcValues(originalMap);
            FuelMapper.setFuelMap(originalFuel);
            if (original == null) Files.deleteIfExists(file); else NeoForgePriceFiles.atomicWrite(file, original);
            NeoForgeRecoveryPrices.reload();
            NeoForgeRecoveryPrices.invalidatePrices();
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_admin_prices", setupTicks = 0, required = true)
    public static void preservesExactMediaPricesModesAndUnrelatedAdministratorJson(GameTestHelper helper) throws IOException {
        Path directory = Files.createTempDirectory("primordial-media-edit-");
        Path file = directory.resolve("values.json");
        boolean oldBuy = NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.get();
        boolean oldSell = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        var oldBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        try {
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            String original = """
                    {"format_version":8,"unknown":{"keep":[true,"中文",4]},"item_overrides":{"old":"42"},
                     "fluid_values":{"water":"4","minecraft:lava":"77","invalid id":{"keep":1}},
                     "fluid_modes":{"water":"sell_only"},"gas_values":{"mekanism:hydrogen":"999"},
                     "gas_modes":{"mekanism:hydrogen":"buy_only"}}
                    """;
            Files.writeString(file, original, StandardCharsets.UTF_8);
            JsonObject before = JsonParser.parseString(original).getAsJsonObject();
            NeoForgeMediaPriceEditing.edit(file, true, WATER, PriceMode.CUSTOM_VALUE, EmcAmount.parse("1/3"));
            check(NeoForgeRecoveryPrices.mediaState(true, WATER).price().equals(EmcAmount.parse("1/3"))
                    && NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.WATER, 3000)).equals(EmcAmount.parse("1/3000")),
                    "A bucket third must remain exact as 1/3000 per mB regardless of quoted stack count");
            for (PriceMode mode : PriceMode.values()) {
                long revision = NeoForgePriceEditing.revision();
                NeoForgeMediaPriceEditing.edit(file, true, WATER, mode, null);
                var state = NeoForgeRecoveryPrices.mediaState(true, WATER);
                check(state.mode() == mode && state.retainedPrice().equals(EmcAmount.parse("1/3")) && NeoForgePriceEditing.revision() > revision,
                        "All five mode transitions must retain the configured exact amount and invalidate old quotes");
                String shown = mode == PriceMode.INHERIT_NATIVE ? "1000" : mode == PriceMode.DISABLED ? "0" : "1/3";
                check(NeoForgePriceEditing.snapshot((byte) 1, WATER).price().equals(shown),
                        "The editor must show the current effective mode price while keeping the custom amount separately");
                EmcAmount expected = mode == PriceMode.INHERIT_NATIVE ? EmcAmount.ONE : EmcAmount.parse("1/3000");
                check(NeoForgeRecoveryPrices.fluid(new FluidStack(Fluids.WATER, 1)).equals(mode.allowsSell() ? expected : EmcAmount.ZERO)
                        && NeoForgeRecoveryPrices.fluidPurchase(new FluidStack(Fluids.WATER, 1)).equals(mode.allowsBuy() ? expected : EmcAmount.ZERO),
                        "Published mode must immediately control real purchase and recovery directions");
            }
            JsonObject after = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : List.of("unknown", "item_overrides", "gas_values", "gas_modes"))
                check(after.get(key).equals(before.get(key)), "Editing a fluid must preserve unrelated JSON field " + key);
            check(after.getAsJsonObject("fluid_values").get("minecraft:lava").equals(before.getAsJsonObject("fluid_values").get("minecraft:lava"))
                    && after.getAsJsonObject("fluid_values").get("invalid id").equals(before.getAsJsonObject("fluid_values").get("invalid id"))
                    && !after.getAsJsonObject("fluid_values").has("water"),
                    "Only the edited logical ID may be normalized; other valid and invalid administrator entries remain raw");
            String maximum = "9".repeat(4096);
            NeoForgeMediaPriceEditing.edit(file, false, HYDROGEN, PriceMode.SELL_ONLY, EmcAmount.parse(maximum));
            check(NeoForgeRecoveryPrices.mediaState(false, HYDROGEN).price().toString().equals(maximum),
                    "Chemical prices must retain all 4096 integer digits without a bucket conversion");
            byte[] stable = Files.readAllBytes(file);
            long revision = NeoForgePriceEditing.revision();
            expectFailure(() -> NeoForgeMediaPriceEditing.edit(file, false, HYDROGEN, PriceMode.CUSTOM_VALUE, EmcAmount.parse("1/2")));
            check(java.util.Arrays.equals(stable, Files.readAllBytes(file)) && NeoForgePriceEditing.revision() == revision,
                    "Fractional chemical edits must not write or publish");
            for (String bad : List.of("[]", "{\"format_version\":9}", "{\"fluid_values\":[]}", "{\"fluid_modes\":42}", "{")) {
                Files.writeString(file, bad, StandardCharsets.UTF_8);
                revision = NeoForgePriceEditing.revision();
                expectFailure(() -> NeoForgeMediaPriceEditing.edit(file, true, WATER, PriceMode.CUSTOM_VALUE, EmcAmount.ONE));
                check(Files.readString(file).equals(bad) && NeoForgePriceEditing.revision() == revision,
                        "Bad root/selected sections must remain byte-for-byte preserved with the old snapshot");
            }
            Files.writeString(file, "{\"gas_values\":[]}", StandardCharsets.UTF_8);
            NeoForgeMediaPriceEditing.edit(file, true, WATER, PriceMode.CUSTOM_VALUE, EmcAmount.ONE);
            check(JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("gas_values").isJsonArray()
                    && !NeoForgeRecoveryPrices.mediaState(false, HYDROGEN).readable(),
                    "An edited healthy fluid must preserve the other corrupt channel and keep it disabled");
            Path blocked = directory.resolve("blocked.json");
            Files.createDirectory(blocked);
            Files.writeString(blocked.resolve("keep"), "unchanged", StandardCharsets.UTF_8);
            revision = NeoForgePriceEditing.revision();
            expectFailure(() -> NeoForgePriceFiles.atomicWrite(blocked, "{}".getBytes(StandardCharsets.UTF_8)));
            check(Files.readString(blocked.resolve("keep")).equals("unchanged") && NeoForgePriceEditing.revision() == revision,
                    "Atomic replacement failure must preserve the target and all published prices");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "Failed writes must not leak temporary price files");
            }
        } finally {
            NeoForgeServerConfig.FLUID_PURCHASE_ENABLED.set(oldBuy);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(oldSell);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(oldBlacklist);
            NeoForgeRecoveryPrices.reload();
            List<Path> cleanup;
            try (var files = Files.walk(directory)) {
                cleanup = files.sorted(java.util.Comparator.reverseOrder()).toList();
            }
            // Close all directory handles before deleting their roots on Windows.
            for (Path path : cleanup) deleteFixturePath(path);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_admin_prices", setupTicks = 0, required = true)
    public static void validatesRealOptionalChemicalRegistryAtEditorBoundary(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path file = NeoForgeMediaPriceEditing.file();
        byte[] original = Files.exists(file) ? Files.readAllBytes(file) : null;
        ServerPlayer player = login(server);
        try {
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
            check(!NeoForgePriceEditing.snapshot((byte) 2, ResourceLocation.parse("primordial:invented_chemical")).available(),
                    "An arbitrary chemical ID must never be editable");
            check(NeoForgePriceEditing.snapshot((byte) 2, HYDROGEN).available() == ModList.get().isLoaded("mekanism"),
                    "The editor must match the actual optional registry installation");
            if (ModList.get().isLoaded("mekanism")) {
                var saved = NeoForgePriceEditing.mutate(server, player, (byte) 2, HYDROGEN, "123456789012345678901");
                check(saved.success() && saved.state().price().equals("123456789012345678901"),
                        "The public backend must save the actual unified chemical without optional API types");
                check(!NeoForgePriceEditing.mutate(server, player, (byte) 2, HYDROGEN, "0.5").success(),
                        "The public backend must reject fractional chemical prices");
            } else check(!NeoForgePriceEditing.mutate(server, player, (byte) 2, HYDROGEN, "10").success(),
                    "Missing optional API must fail closed without class loading errors");
        } finally {
            server.getPlayerList().deop(player.getGameProfile());
            logout(server, player);
            if (original == null) Files.deleteIfExists(file); else NeoForgePriceFiles.atomicWrite(file, original);
            NeoForgeRecoveryPrices.reload();
        }
        helper.succeed();
    }

    private static JsonObject entry(MinecraftServer server, NSSItem key, long price) {
        JsonObject result = NSSItem.CODEC.codec().encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), key).getOrThrow().getAsJsonObject();
        result.addProperty("emc", price);
        return result;
    }

    private static ServerPlayer login(MinecraftServer server) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "price-test-op");
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

    private interface FailingAction { void run() throws IOException; }

    private static void deleteFixturePath(Path path) throws IOException {
        // Windows scanners may briefly retain a newly written fixture after our own handles close.
        // Retry only filesystem deletion errors; persistent failures still fail this test with their cause.
        for (int attempt = 0; ; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (java.nio.file.FileSystemException temporarilyLocked) {
                if (attempt == 4) throw new IOException("Cannot delete test fixture " + path, temporarilyLocked);
                try {
                    Thread.sleep(25L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while deleting test fixture " + path, interrupted);
                }
            }
        }
    }

    private static void expectFailure(FailingAction action) throws IOException {
        boolean failed = false;
        try { action.run(); } catch (IOException | RuntimeException expected) { failed = true; }
        check(failed, "The invalid or unwritable candidate must throw instead of committing");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
