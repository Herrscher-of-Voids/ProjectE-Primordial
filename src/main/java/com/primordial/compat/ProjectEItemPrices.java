/**
 * 【文件职责】直接编辑 ProjectE 的 custom_emc.json，调用其原生重算、知识刷新和客户端同步。
 * 【变更记录 #2】2026-10-10 02:33:01（第 2 次备注 · 1.2.0-item-price-inherit）
 * - 删除：无。
 * - 新增：物品覆盖价移除入口，可恢复 ProjectE 配方/Mapper 计算出的默认基础价。
 * - 修改：设置与重置共用同一份写盘、原生重算、失败回滚和客户端同步流程；重置只删目标 NSS 项。
 * - 用途：价格编辑器输入 inherit 时撤销人工覆盖，不影响 custom_emc.json 中其他物品或未 flush 的命令修改。
 * 【变更记录 #1】2026-09-16 01:52:17（第 1 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：无；本桥接替代 ValueManager 的独立物品覆盖和自定义物品价格同步。
 * - 新增：原生 NSS 编码、UTF-8 原子写入、当前资源上下文、单件 long 边界和失败恢复。
 * - 修改：只重算 ProjectE 的 EMC 图，不触发整服数据包重载；同一服务端线程完成写入和发布。
 * - 用途：转化桌、原生 Tooltip、配置器和黑洞共同读取 ProjectE 价表，余额仍由其 BigInteger capability 管理。
 */
package com.primordial.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.primordial.PrimordialMod;
import com.primordial.value.ValueManager;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.event.EMCRemapEvent;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.CustomEMCParser;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.FuelMapper;
import moze_intel.projecte.emc.json.NSSSerializer;
import moze_intel.projecte.gameObjs.container.TransmutationContainer;
import moze_intel.projecte.network.PacketHandler;
import moze_intel.projecte.network.packets.to_client.knowledge.UpdateTransmutationTargetsPkt;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = PrimordialMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProjectEItemPrices {
    public static final BigInteger MAX_ITEM_PRICE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(NormalizedSimpleStack.class, NSSSerializer.INSTANCE).setPrettyPrinting().create();
    private static volatile RemapContext remapContext;

    private ProjectEItemPrices() { }
    private record RemapContext(ReloadableServerResources resources, RegistryAccess registries, ResourceManager manager) { }

    /** 只保留已应用的当前资源上下文；下一次重载替换引用，停服释放，避免持有旧整合包资源。 */
    @SubscribeEvent public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) manager -> remapContext =
                new RemapContext(event.getServerResources(), event.getRegistryAccess(), manager));
    }

    @SubscribeEvent public static void onRemap(EMCRemapEvent event) { ValueManager.invalidatePrices(); }

    public static void clear() { remapContext = null; }

    public static boolean validItemPrice(BigInteger price) {
        return price != null && price.signum() >= 0 && price.compareTo(MAX_ITEM_PRICE) <= 0;
    }

    public static Path file() { return FMLPaths.CONFIGDIR.get().resolve("ProjectE").resolve("custom_emc.json"); }

    /** 原生基础单价，不把样本的耐久、储能等 NBT 附加价值误写进基础价。 */
    public static BigInteger basePrice(ResourceLocation id) {
        return BigInteger.valueOf(EMCMappingHandler.getStoredEmcValue(ItemInfo.fromItem(BuiltInRegistries.ITEM.get(id))));
    }

    /** 给价格编辑及命令共用。写盘失败不改内存，重算失败恢复旧价；不裁剪或另存超 long 物品价格。 */
    public static synchronized boolean setPrice(MinecraftServer server, ResourceLocation id, BigInteger price) {
        if (!validItemPrice(price) || id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        return updatePrice(server, id, price);
    }

    /** 删除目标物品的人工基础价，让 ProjectE 重新采用 Mapper/配方默认值；零价仍由 setPrice 保留。 */
    public static synchronized boolean resetPrice(MinecraftServer server, ResourceLocation id) {
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        return updatePrice(server, id, null);
    }

    private static boolean updatePrice(MinecraftServer server, ResourceLocation id, BigInteger price) {
        RemapContext context = currentContext(server);
        if (context == null) return false;
        try {
            Path path = file();
            byte[] before = Files.exists(path) ? Files.readAllBytes(path) : null;
            JsonObject root = readRoot(before);
            CustomEMCParser.CustomEMCFile oldEntries = CustomEMCParser.currentEntries;
            // 合并 ProjectE 命令尚未 flush 的修改，防止编辑器覆盖管理员刚设置的其它物品。
            if (oldEntries != null) root.add("entries", GSON.toJsonTree(oldEntries).getAsJsonObject().getAsJsonArray("entries"));
            NSSItem target = NSSItem.createItem(id);
            JsonArray updated = new JsonArray();
            for (var element : root.getAsJsonArray("entries")) {
                var entry = element.getAsJsonObject();
                if (!target.equals(GSON.fromJson(entry.get("item"), NormalizedSimpleStack.class))) updated.add(entry);
            }
            if (price != null) {
                JsonObject entry = new JsonObject();
                entry.add("item", GSON.toJsonTree(target, NormalizedSimpleStack.class));
                entry.addProperty("emc", price.longValueExact());
                updated.add(entry);
            }
            root.add("entries", updated);
            var next = parseEntries(root);
            var oldMap = EMCMappingHandler.createPacketData();
            atomicWrite(path, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            CustomEMCParser.currentEntries = next;
            try {
                remap(context);
                if (price != null && !basePrice(id).equals(price))
                    throw new IllegalStateException("ProjectE did not apply the custom price; check enabledMappers.CustomEMCMapper and pregenerate");
            } catch (RuntimeException | LinkageError failure) {
                CustomEMCParser.currentEntries = oldEntries;
                EMCMappingHandler.fromPacket(oldMap);
                FuelMapper.loadMap();
                try {
                    if (before == null) Files.deleteIfExists(path); else atomicWrite(path, before);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                ValueManager.invalidatePrices();
                synchronize(server);
                throw failure;
            }
            synchronize(server);
            return true;
        } catch (IOException | RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.error("[Primordial] ProjectE item price update failed for {}", id, failure);
            return false;
        }
    }

    /** 与 ProjectE 命令兼容：先 flush 其待保存项，再读同一文件并只重算 EMC；不保留本模组覆盖层。 */
    public static synchronized boolean reload(MinecraftServer server) {
        RemapContext context = currentContext(server);
        if (context == null) return false;
        try {
            CustomEMCParser.flush();
            Path path = file();
            var next = parseEntries(readRoot(Files.exists(path) ? Files.readAllBytes(path) : null));
            var previous = CustomEMCParser.currentEntries;
            var oldMap = EMCMappingHandler.createPacketData();
            CustomEMCParser.currentEntries = next;
            try {
                remap(context);
            } catch (RuntimeException | LinkageError failure) {
                CustomEMCParser.currentEntries = previous;
                EMCMappingHandler.fromPacket(oldMap);
                FuelMapper.loadMap();
                ValueManager.invalidatePrices();
                synchronize(server);
                throw failure;
            }
            synchronize(server);
            return true;
        } catch (IOException | RuntimeException | LinkageError failure) {
            PrimordialMod.LOGGER.error("[Primordial] Cannot reload ProjectE item prices", failure);
            return false;
        }
    }

    private static RemapContext currentContext(MinecraftServer server) {
        RemapContext context = remapContext;
        if (server == null || !server.isSameThread() || context == null
                || context.resources().getRecipeManager() != server.getRecipeManager()) {
            PrimordialMod.LOGGER.error("[Primordial] Cannot edit ProjectE prices without the active server recipe context");
            return null;
        }
        return context;
    }

    private static void remap(RemapContext context) {
        EMCMappingHandler.map(context.resources(), context.registries(), context.manager());
        ValueManager.invalidatePrices();
    }

    /** 原生价表/燃料同步发出后再刷新已打开的转化桌，远程客户端不会按旧数据重建目标。 */
    private static void synchronize(MinecraftServer server) {
        try {
            PacketHandler.sendFragmentedEmcPacketToAll();
            for (var player : server.getPlayerList().getPlayers())
                if (player.containerMenu instanceof TransmutationContainer)
                    PacketHandler.sendTo(new UpdateTransmutationTargetsPkt(), player);
        } catch (RuntimeException | LinkageError failure) {
            // 写盘与原生计价已提交；网络错误不能把一次成功保存误报为未保存。
            PrimordialMod.LOGGER.error("[Primordial] ProjectE price saved, but client synchronization failed", failure);
        }
    }

    private static JsonObject readRoot(byte[] bytes) throws IOException {
        JsonObject root;
        if (bytes == null) {
            root = new JsonObject();
            root.addProperty("__comment", "使用 ProjectE 命令或原初配置器编辑；物品基础 EMC 与 ProjectE 共用。");
            root.add("entries", new JsonArray());
        } else {
            root = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), JsonObject.class);
        }
        parseEntries(root); // 严格验证现有文件；损坏时不能用一个新样本覆盖整份价格配置。
        return root;
    }

    private static CustomEMCParser.CustomEMCFile parseEntries(JsonObject root) throws IOException {
        if (root == null || !root.has("entries") || !root.get("entries").isJsonArray())
            throw new IOException("Invalid ProjectE custom EMC file");
        for (var element : root.getAsJsonArray("entries")) {
            var entry = element.getAsJsonObject();
            BigInteger price = com.primordial.value.ValueTable.parseInteger(entry.get("emc").getAsString());
            if (!validItemPrice(price) || !(GSON.fromJson(entry.get("item"), NormalizedSimpleStack.class) instanceof NSSItem))
                throw new IOException("Invalid ProjectE custom EMC entry");
        }
        return GSON.fromJson(root, CustomEMCParser.CustomEMCFile.class);
    }

    private static void atomicWrite(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "custom_emc-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    /** 兼容旧配方分析器的只读查询，返回的就是 ProjectE 人工基础价，零值也保留。 */
    public static Map<String, BigInteger> customPrices() {
        Map<String, BigInteger> result = new HashMap<>();
        var current = CustomEMCParser.currentEntries;
        if (current != null) for (var entry : current.entries)
            if (entry.item instanceof NSSItem item && !item.representsTag() && item.getNBT() == null)
                result.put(item.getResourceLocation().toString(), BigInteger.valueOf(entry.emc));
        return Map.copyOf(result);
    }
}
