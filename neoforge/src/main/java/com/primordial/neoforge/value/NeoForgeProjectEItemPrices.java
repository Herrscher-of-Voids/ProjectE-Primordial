/**
 * 【文件职责】编辑 ProjectE 原生基础物品价并持久化、重映射和同步，失败恢复旧状态。
 * 【变更记录 #1】2026-10-23 17:00:16（第 1 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：无。新增：目标版原生 NSS Codec、活动资源上下文、long 边界和原子保存事务。
 * - 修改：保留未知 JSON 字段及组件项；合并原生命令待保存修改，恢复默认只删目标基础项。
 * - 用途：转化桌、Tooltip、回收与购买共用一张原生价表；损坏根文件不能被编辑器覆盖。
 * 【变更记录 #2】2026-10-23 17:11:28（第 2 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：失败回滚时单个知识同步异常可能中断磁盘恢复的路径。
 * - 新增：各恢复步骤独立收集异常，客户端逐人同步故障互不阻断。
 * - 修改：只有失败映射才恢复误删知识；成功零价继续沿用原生知识清理。
 * - 用途：一个离线或故障连接不会阻止文件、报价与其他玩家恢复一致状态。
 * 【变更记录 #3】2026-10-23 17:31:58（第 3 次备注 · 2.0.0-neoforge-pricing）
 * - 删除：管理员只能通过编辑样本触发原生价表重读的限制。
 * - 新增：重载原生 CustomEMC 文件入口；与单项编辑共用提交、映射、失败回滚及同步事务。
 * - 修改：重载先严格检查原文；dirty 命令项按原生优先合并，写盘后清标记，未知字段保留。
 * - 用途：控制台 reload_values 真实重算原生价格，不用 flush 的窄 Codec 覆盖管理员扩展字段。
 */
package com.primordial.neoforge.value;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.service.EmcAmount;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import moze_intel.projecte.PECore;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.config.MappingConfig;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.mappers.CustomEMCMapper;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.network.packets.to_client.SyncEmcPKT;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = PrimordialNeoForgeMod.MOD_ID)
public final class NeoForgeProjectEItemPrices {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Context context;

    private record Context(ReloadableServerResources resources, RegistryAccess registries, ResourceManager manager) {}
    private record KnowledgeBefore(IKnowledgeProvider knowledge, Set<ItemInfo> items, boolean full) {}

    private NeoForgeProjectEItemPrices() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) manager -> context =
                new Context(event.getServerResources(), event.getRegistryAccess(), manager));
    }

    @SubscribeEvent
    public static void onRemap(EMCRemapEvent event) { NeoForgeRecoveryPrices.invalidatePrices(); }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) { context = null; NeoForgeRecoveryPrices.invalidatePrices(); }

    public static Path file() { return FMLPaths.CONFIGDIR.get().resolve("ProjectE/custom_emc.json"); }

    static boolean valid(ResourceLocation id) {
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && BuiltInRegistries.ITEM.get(id) != Items.AIR;
    }

    static boolean available() { return context != null && CustomEMCParser.currentEntries != null; }

    public static OptionalLong customItemValue(ResourceLocation id) {
        var current = CustomEMCParser.currentEntries;
        if (!valid(id) || current == null) return OptionalLong.empty();
        NSSItem target = NSSItem.createItem(id);
        return current.entries().containsKey(target) ? OptionalLong.of(current.entries().getLong(target)) : OptionalLong.empty();
    }

    static BigInteger basePrice(ResourceLocation id) {
        return valid(id) ? BigInteger.valueOf(EMCMappingHandler.getStoredEmcValue(
                ItemInfo.fromItem(BuiltInRegistries.ITEM.get(id)))) : BigInteger.ZERO;
    }

    /** null 撤销人工基础价；样本组件完全不参与目标键，原有组件专价保持独立。 */
    static synchronized boolean update(MinecraftServer server, ResourceLocation id, BigInteger price) {
        Context active = context;
        if (server == null || !server.isSameThread() || !valid(id) || active == null
                || active.resources.getRecipeManager() != server.getRecipeManager()
                || price != null && (price.signum() < 0 || price.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)) return false;
        // 预生成文件会绕开 CustomEMCMapper；失败必须在原生知识清理和写盘之前报告。
        if (MappingConfig.usePregenerated() || !MappingConfig.isEnabled(new CustomEMCMapper())) return false;
        try {
            Field dirty = dirtyField();
            boolean wasDirty = dirty.getBoolean(null);
            var previous = CustomEMCParser.currentEntries;
            Path path = file();
            byte[] before = Files.exists(path) ? Files.readAllBytes(path) : null;
            JsonObject root = readRoot(server.registryAccess(), before);
            // Native dirty 是明确的待保存命令标记；否则磁盘上的管理员修改仍以原文为准。
            if (wasDirty && previous != null) mergePending(root, previous, server.registryAccess());
            JsonArray entries = new JsonArray();
            NSSItem target = NSSItem.createItem(id);
            JsonObject replacement = null;
            for (var raw : root.getAsJsonArray("entries")) {
                JsonObject entry = raw.getAsJsonObject();
                if (target.equals(decodeKey(server.registryAccess(), entry))) replacement = entry.deepCopy();
                else entries.add(entry);
            }
            if (price != null) {
                if (replacement == null) replacement = encodeKey(server.registryAccess(), target);
                replacement.addProperty("emc", price.longValueExact());
                entries.add(replacement);
            }
            root.add("entries", entries);
            var next = parse(server.registryAccess(), root);
            commit(server, active, path, before, root, previous, next, dirty, wasDirty, true, id, price);
            return true;
        } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("[Primordial] Native item price edit failed for {}", id, failure);
            return false;
        }
    }

    /** 原文只读重载；只有存在未保存原生命令时，才先原子写入合并后的内容。 */
    static synchronized boolean reload(MinecraftServer server) {
        Context active = context;
        if (server == null || !server.isSameThread() || active == null
                || active.resources.getRecipeManager() != server.getRecipeManager()) return false;
        try {
            Field dirty = dirtyField();
            boolean wasDirty = dirty.getBoolean(null);
            var previous = CustomEMCParser.currentEntries;
            Path path = file();
            byte[] before = Files.exists(path) ? Files.readAllBytes(path) : null;
            JsonObject root = readRoot(server.registryAccess(), before);
            if (wasDirty && previous != null) mergePending(root, previous, server.registryAccess());
            var next = parse(server.registryAccess(), root);
            commit(server, active, path, before, root, previous, next, dirty, wasDirty, wasDirty, null, null);
            return true;
        } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("[Primordial] Cannot reload native ProjectE custom EMC", failure);
            return false;
        }
    }

    private static void commit(MinecraftServer server, Context active, Path path, byte[] before, JsonObject root,
            CustomEMCParser.CustomEMCFile previous, CustomEMCParser.CustomEMCFile next, Field dirty,
            boolean wasDirty, boolean write, ResourceLocation expectedId, BigInteger expectedPrice)
            throws IOException, ReflectiveOperationException {
        var oldMap = new Object2LongOpenHashMap<>(EMCMappingHandler.createPacketData().data());
        var oldFuel = FuelMapper.getFuelMap();
        Map<ServerPlayer, KnowledgeBefore> knowledge = captureKnowledge(server);
        if (write) NeoForgePriceFiles.atomicWrite(path, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        try {
            CustomEMCParser.currentEntries = next;
            // 已原子保存，避免 PE 之后 flush 再用窄 Codec 覆盖保留的未知 JSON 字段。
            dirty.setBoolean(null, false);
            EMCMappingHandler.map(active.resources, active.registries, active.manager);
            if (expectedPrice != null && !basePrice(expectedId).equals(expectedPrice))
                throw new IllegalStateException("Native ProjectE mapping did not apply requested base price");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            CustomEMCParser.currentEntries = previous;
            try { dirty.setBoolean(null, wasDirty); } catch (IllegalAccessException rollback) { failure.addSuppressed(rollback); }
            try { EMCMappingHandler.updateEmcValues(oldMap); }
            catch (RuntimeException | LinkageError rollback) { failure.addSuppressed(rollback); }
            try { FuelMapper.setFuelMap(oldFuel); }
            catch (RuntimeException | LinkageError rollback) { failure.addSuppressed(rollback); }
            restoreKnowledge(knowledge, failure);
            if (write) {
                try {
                    if (before == null) Files.deleteIfExists(path); else NeoForgePriceFiles.atomicWrite(path, before);
                } catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            NeoForgeRecoveryPrices.invalidatePrices();
            synchronize(server);
            throw failure;
        }
        synchronize(server);
    }

    /** 精确绑定已固定 ProjectE 1.1.0 的待保存标记；缺少字段时拒绝编辑，不猜测命令状态。 */
    private static Field dirtyField() throws ReflectiveOperationException {
        Field dirty = CustomEMCParser.class.getDeclaredField("dirty");
        dirty.setAccessible(true);
        return dirty;
    }

    static JsonObject readRoot(RegistryAccess registries, byte[] before) throws IOException {
        JsonObject root;
        if (before == null) {
            root = new JsonObject();
            root.addProperty("comment", "物品基础价与 ProjectE 共用；原初定价器只编辑指定基础注册项。");
            root.add("entries", new JsonArray());
        } else {
            var parsed = JsonParser.parseString(new String(before, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IOException("Expected native custom EMC object");
            root = parsed.getAsJsonObject();
        }
        parse(registries, root);
        return root;
    }

    /** 原生根 Codec 跳过非法 key；编辑器先逐项严格验证，不能静默丢失管理员条目。 */
    private static CustomEMCParser.CustomEMCFile parse(RegistryAccess registries, JsonObject root) throws IOException {
        if (!root.has("entries") || !root.get("entries").isJsonArray()) throw new IOException("Expected native EMC entries array");
        Set<NSSItem> keys = new HashSet<>();
        for (var raw : root.getAsJsonArray("entries")) {
            if (!raw.isJsonObject()) throw new IOException("Expected native EMC entry object");
            JsonObject entry = raw.getAsJsonObject();
            if (!keys.add(decodeKey(registries, entry))) throw new IOException("Duplicate native custom EMC entry");
            if (!entry.has("emc") || !entry.get("emc").isJsonPrimitive()
                    || entry.getAsJsonPrimitive("emc").isBoolean()) throw new IOException("Expected native EMC integer");
            EmcAmount amount = EmcAmount.parse(entry.get("emc").getAsString());
            if (!amount.denominator().equals(BigInteger.ONE)
                    || amount.numerator().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0)
                throw new IOException("Native EMC outside nonnegative long range");
        }
        return CustomEMCParser.CustomEMCFile.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), root).getOrThrow();
    }

    private static NSSItem decodeKey(RegistryAccess registries, JsonObject entry) {
        return NSSItem.CODEC.codec().parse(registries.createSerializationContext(JsonOps.INSTANCE), entry).getOrThrow();
    }

    private static JsonObject encodeKey(RegistryAccess registries, NSSItem key) {
        return NSSItem.CODEC.codec().encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), key).getOrThrow().getAsJsonObject();
    }

    private static void mergePending(JsonObject root, CustomEMCParser.CustomEMCFile current, RegistryAccess registries) {
        Map<NSSItem, JsonObject> original = new LinkedHashMap<>();
        for (var raw : root.getAsJsonArray("entries")) {
            JsonObject entry = raw.getAsJsonObject();
            original.put(decodeKey(registries, entry), entry);
        }
        JsonArray updated = new JsonArray();
        current.entries().forEach((key, value) -> {
            JsonObject entry = original.containsKey(key) ? original.get(key).deepCopy() : encodeKey(registries, key);
            entry.addProperty("emc", value);
            updated.add(entry);
        });
        root.add("entries", updated);
    }

    private static Map<ServerPlayer, KnowledgeBefore> captureKnowledge(MinecraftServer server) {
        Map<ServerPlayer, KnowledgeBefore> result = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
            if (knowledge != null) result.put(player, new KnowledgeBefore(knowledge, Set.copyOf(knowledge.getKnowledge()), knowledge.hasFullKnowledge()));
        }
        return result;
    }

    private static void restoreKnowledge(Map<ServerPlayer, KnowledgeBefore> previous, Throwable failure) {
        previous.forEach((player, before) -> {
            try {
                before.items.forEach(before.knowledge::addKnowledge);
                before.knowledge.setFullKnowledge(before.full);
                before.knowledge.sync(player);
            } catch (RuntimeException | LinkageError rollback) { failure.addSuppressed(rollback); }
        });
    }

    private static void synchronize(MinecraftServer server) {
        SyncEmcPKT emc;
        try {
            emc = SyncEmcPKT.serializeEmcData(server.registryAccess());
        } catch (RuntimeException | LinkageError failure) {
            LogUtils.getLogger().error("[Primordial] Item price saved but native packet serialization failed", failure);
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                PacketDistributor.sendToPlayer(player, emc, FuelMapper.getSyncPacket());
                if (player.containerMenu instanceof TransmutationContainer) PECore.packetHandler().updateTransmutationTargets(player);
            } catch (RuntimeException | LinkageError failure) {
                // 磁盘与计价已提交；网络失败单独记录，不能谎报保存失败诱发重复编辑。
                LogUtils.getLogger().error("[Primordial] Item price saved but native synchronization failed for {}", player.getUUID(), failure);
            }
        }
    }
}
