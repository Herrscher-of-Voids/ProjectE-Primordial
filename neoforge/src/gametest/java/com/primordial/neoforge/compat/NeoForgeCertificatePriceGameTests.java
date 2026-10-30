/**
 * 【文件职责】以真实 ProjectE 整表发布、组件键、原生玩家能力和 remap/prune 流程验收凭证价格策略。
 * 【变更记录 #1】2026-10-23 13:35:36（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：8192 缺价兜底、正价与组件价保留、关闭删除所有变体，以及 prune 前保存原生已学凭证的三项测试。
 * - 修改：仅测试反射调用 PE 私有 fireEmcRemapEvent；生产用精确 Mixin，finally 恢复原表、燃料缓存与连接。
 * - 用途：检测整表误覆盖、禁用漏掉组件变体、事件递归或在知识已被清理后才补价；不授予未学凭证。
 */
package com.primordial.neoforge.compat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.registry.NeoForgeCertificates;

import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.proxy.IEMCProxy;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeCertificatePriceGameTests {
    private NeoForgeCertificatePriceGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_certificate_prices", setupTicks = 0, required = true)
    public static void preservesPositiveAndComponentPricesWhileFillingMissingCertificates(GameTestHelper helper) {
        Object2LongMap<ItemInfo> original = snapshot();
        AtomicInteger remaps = new AtomicInteger();
        Consumer<EMCRemapEvent> listener = event -> remaps.incrementAndGet();
        NeoForge.EVENT_BUS.addListener(EMCRemapEvent.class, listener);
        try {
            List<Item> certificates = certificates();
            check(certificates.size() == 11, "All eleven Forge certificate IDs must be registered");
            Object2LongMap<ItemInfo> fixture = NeoForgeCertificatePrices.adjusted(original, false);
            ItemInfo ordinary = ItemInfo.fromItem(Items.DIAMOND);
            ItemInfo ordinaryVariant = named(Items.DIAMOND, "ordinary-price-component");
            fixture.put(ordinary, 1234L);
            fixture.put(ordinaryVariant, 98765L);
            fixture.put(ItemInfo.fromItem(certificates.getFirst()), 16384L);
            fixture.put(ItemInfo.fromItem(certificates.get(1)), 0L);
            fixture.put(ItemInfo.fromItem(certificates.get(2)), -9L);
            for (int index = 0; index < certificates.size(); index++)
                fixture.put(named(certificates.get(index), "certificate-price-component"), 30000L + index);
            Object2LongMap<ItemInfo> before = new Object2LongOpenHashMap<>(fixture);
            Object2LongMap<ItemInfo> adjusted = NeoForgeCertificatePrices.adjusted(fixture, true);
            check(fixture.equals(before), "Price adjustment must not mutate PE's published map or its input");
            EMCMappingHandler.updateEmcValues(adjusted);
            for (int index = 0; index < certificates.size(); index++) {
                Item item = certificates.get(index);
                long expected = index == 0 ? 16384L : NeoForgeCertificatePrices.FALLBACK_EMC;
                check(EMCMappingHandler.getStoredEmcValue(ItemInfo.fromItem(item)) == expected
                        && IEMCProxy.INSTANCE.getValue(item) == expected,
                        "Native ProjectE queries must preserve positive base values and fill only nonpositive or missing bases");
                check(EMCMappingHandler.getStoredEmcValue(named(item, "certificate-price-component")) == 30000L + index,
                        "Fallback must retain every existing component-specific certificate price");
            }
            check(EMCMappingHandler.getStoredEmcValue(ordinary) == 1234L
                    && EMCMappingHandler.getStoredEmcValue(ordinaryVariant) == 98765L,
                    "Replacing the complete table must retain normal items and their component prices");
            check(NeoForgeCertificatePrices.adjusted(adjusted, true).equals(adjusted), "Enabled adjustment must be idempotent");
            if (NeoForgeKnowledgeConfig.required()) {
                check(!NeoForgeCertificatePrices.refresh() && !NeoForgeCertificatePrices.refresh(),
                        "Refreshing an already corrected native table must not republish it");
            }
            check(remaps.get() == 0, "Native table replacement and certificate refresh must never recursively post remap events");
        } finally {
            EMCMappingHandler.updateEmcValues(original);
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_certificate_prices", setupTicks = 0, required = true)
    public static void removesEveryCertificateVariantWithoutTouchingOrdinaryPrices(GameTestHelper helper) {
        Object2LongMap<ItemInfo> original = snapshot();
        try {
            List<Item> certificates = certificates();
            Object2LongMap<ItemInfo> fixture = new Object2LongOpenHashMap<>(original);
            for (Item item : certificates) {
                fixture.put(ItemInfo.fromItem(item), 8192L);
                fixture.put(named(item, "disabled-certificate-component"), 123456L);
            }
            ItemInfo ordinary = named(Items.DIAMOND, "preserved-ordinary-component");
            fixture.put(ordinary, 54321L);
            Object2LongMap<ItemInfo> before = new Object2LongOpenHashMap<>(fixture);
            Object2LongMap<ItemInfo> disabled = NeoForgeCertificatePrices.adjusted(fixture, false);
            check(fixture.equals(before), "Disabling knowledge must not edit the original native map in place");
            EMCMappingHandler.updateEmcValues(disabled);
            for (Item item : certificates) {
                check(!EMCMappingHandler.hasEmcValue(ItemInfo.fromItem(item))
                        && !EMCMappingHandler.hasEmcValue(named(item, "disabled-certificate-component"))
                        && !IEMCProxy.INSTANCE.hasValue(item),
                        "Disabled knowledge must remove both base and component certificate entries from native ProjectE");
            }
            check(EMCMappingHandler.getMappedItems().stream()
                    .noneMatch(info -> certificates.contains(info.getItem().value())),
                    "No unexamined certificate component entry may survive disabled knowledge");
            check(EMCMappingHandler.getStoredEmcValue(ordinary) == 54321L,
                    "Disabling certificates must preserve unrelated component mappings");
            check(NeoForgeCertificatePrices.adjusted(disabled, false).equals(disabled), "Disabled adjustment must be idempotent");
            Object2LongMap<ItemInfo> enabledAgain = NeoForgeCertificatePrices.adjusted(disabled, true);
            for (Item item : certificates)
                check(enabledAgain.getLong(ItemInfo.fromItem(item)) == NeoForgeCertificatePrices.FALLBACK_EMC,
                        "A new enabled mapping must restore certificate bases from the startup rule");
        } finally {
            EMCMappingHandler.updateEmcValues(original);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_certificate_prices", setupTicks = 0, required = true)
    public static void repairsNativeMapBeforeProjectEPrunesPreviouslyLearnedCertificates(GameTestHelper helper) throws Exception {
        check(NeoForgeKnowledgeConfig.required(), "The native remap regression test requires the default enabled startup snapshot");
        Object2LongMap<ItemInfo> original = snapshot();
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = null;
        AtomicInteger remaps = new AtomicInteger();
        Consumer<EMCRemapEvent> listener = event -> remaps.incrementAndGet();
        NeoForge.EVENT_BUS.addListener(EMCRemapEvent.class, listener);
        try {
            NeoForgeCertificatePrices.refresh();
            player = login(server);
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            check(knowledge != null && !knowledge.hasFullKnowledge(), "The real ProjectE attachment must begin without full knowledge");
            ItemInfo fluid = certificate("fluid_certificate");
            ItemInfo energy = certificate("energy_certificate");
            ItemInfo neverLearned = certificate("gas_certificate");
            check(knowledge.addKnowledge(fluid) && knowledge.addKnowledge(energy),
                    "ProjectE must natively learn the priced fluid and energy certificates");
            check(!knowledge.hasKnowledge(neverLearned), "The gas certificate must begin unlearned");

            EMCMappingHandler.updateEmcValues(NeoForgeCertificatePrices.adjusted(snapshot(), false));
            check(!IEMCProxy.INSTANCE.hasValue(fluid) && !IEMCProxy.INSTANCE.hasValue(energy),
                    "The native remap fixture must actually remove certificate prices before the HEAD hook");
            Method nativeRemap = EMCMappingHandler.class.getDeclaredMethod("fireEmcRemapEvent");
            nativeRemap.setAccessible(true);
            nativeRemap.invoke(null);

            check(remaps.get() == 1, "The actual native method must finish and post exactly one remap event");
            check(IEMCProxy.INSTANCE.getValue(fluid) == NeoForgeCertificatePrices.FALLBACK_EMC
                    && IEMCProxy.INSTANCE.getValue(energy) == NeoForgeCertificatePrices.FALLBACK_EMC,
                    "The native mapping must contain fallback prices before pruning");
            check(knowledge.hasKnowledge(fluid) && knowledge.hasKnowledge(energy),
                    "The HEAD hook must preserve already learned certificates before ProjectE pruneStaleKnowledge");
            check(!knowledge.hasKnowledge(neverLearned) && !knowledge.hasFullKnowledge(),
                    "Repairing the price table must not grant an unlearned certificate or full knowledge");
        } finally {
            logout(server, player);
            EMCMappingHandler.updateEmcValues(original);
            FuelMapper.loadMap();
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }

    private static Object2LongMap<ItemInfo> snapshot() {
        return new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
    }

    private static List<Item> certificates() {
        return NeoForgeCertificates.CERTIFICATES.values().stream().map(holder -> (Item) holder.get()).toList();
    }

    private static ItemInfo named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return ItemInfo.fromStack(stack);
    }

    private static ItemInfo certificate(String id) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(PrimordialNeoForgeMod.MOD_ID, id));
        check(item != Items.AIR, "The requested certificate must be registered: " + id);
        return ItemInfo.fromItem(item);
    }

    private static ServerPlayer login(MinecraftServer server) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "certificate-test");
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(),
                false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            try {
                logout(server, player);
            } finally {
                channel.finishAndReleaseAll();
            }
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
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
