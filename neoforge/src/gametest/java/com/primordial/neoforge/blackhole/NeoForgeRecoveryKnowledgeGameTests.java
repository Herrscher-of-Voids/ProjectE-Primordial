/**
 * 【文件职责】以真实 ProjectE 玩家能力、注册表组件及世界存档验收回收知识的在线/离线交接。
 * 【变更记录 #1】2026-10-22 21:46:33（第 1 次备注 · 2.0.0-neo-dev 黑洞实际回收）
 * - 删除：无。
 * - 新增：离线组件去重、流体知识、注册表附魔容器往返、真实登录学习及损坏原始数据保护测试。
 * - 修改：受 creditPersistencePhase 控制的独立 write/read/verify 批次读取真实世界 .dat 与玩家附件。
 * - 用途：区分内存 NBT 往返与跨 JVM 正常停服重启；测试连接协商和退出保存使用真实 NeoForge 路径。
 * 【变更记录 #2】2026-10-22 21:48:51（第 2 次备注 · 2.0.0-neo-dev 测试玩家约束核验）
 * - 删除：未使用的 ListTag 导入。
 * - 新增：无。
 * - 修改：重启测试玩家名称缩至 12 字符，符合 Minecraft 玩家名的 16 字符上限。
 * - 用途：保证真实登录路径中的测试资料可正常序列化，避免无关名称约束干扰知识持久化验收。
 */
package com.primordial.neoforge.blackhole;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeRecoveryKnowledgeGameTests {
    private static final UUID PERSISTENCE_OWNER = UUID.fromString("1be89e18-05e8-4fa3-af7a-c95eb689e129");
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("water");
    private static final ResourceLocation LAVA = ResourceLocation.withDefaultNamespace("lava");

    private NeoForgeRecoveryKnowledgeGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void deduplicatesOfflineKnowledgeAndPersistsFluidIds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        check(server.getPlayerList().getPlayer(owner) == null, "The knowledge owner must be genuinely offline");
        check(NeoForgeRecoveryKnowledge.available(server), "The world knowledge ledger must accept recovery");
        ItemStack sample = namedDiamond(64);
        ItemInfo normalized = normalize(sample);
        NeoForgeRecoveryKnowledge.recordItem(server, owner, sample);
        NeoForgeRecoveryKnowledge.recordItem(server, owner, sample.copyWithCount(1));
        NeoForgeRecoveryKnowledge.recordFluid(server, owner, WATER);
        NeoForgeRecoveryKnowledge.recordFluid(server, owner, WATER);
        NeoForgeRecoveryKnowledge.recordFluid(server, owner, LAVA);
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        check(data.pendingItemCount(owner) == 1 && data.pending(owner).contains(normalized),
                "Different sold counts of the same native persistent ItemInfo must create only one pending knowledge entry");
        check(NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER)
                && NeoForgeRecoveryKnowledge.knowsFluid(server, owner, LAVA),
                "Fluid IDs must be learned for the offline owner without a player capability");
        CompoundTag saved = data.save(new CompoundTag(), server.registryAccess());
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(saved, server.registryAccess());
        check(restored.pendingItemCount(owner) == 1 && restored.pending(owner).contains(normalized)
                && restored.knowsFluid(owner, WATER) && restored.knowsFluid(owner, LAVA),
                "Offline pending items and both fluid IDs must survive registry-aware SavedData serialization");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void roundTripsRegistryBackedEnchantmentsInsideContainerComponents(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        ItemInfo original = ItemInfo.fromStack(enchantedContainer(server));
        NeoForgeRecoveryKnowledgeSavedData data = new NeoForgeRecoveryKnowledgeSavedData();
        data.queue(owner, original, server.registryAccess());
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(
                data.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
        check(restored.pendingItemCount(owner) == 1 && restored.pending(owner).contains(original),
                "ItemInfo codec must retain component identity, nested ItemStacks and registry-backed enchantment holders");
        ItemStack recovered = restored.pending(owner).iterator().next().createStack();
        ItemContainerContents contents = recovered.get(DataComponents.CONTAINER);
        check(contents != null, "The knowledge sample must retain its non-empty container component");
        ItemStack tool = contents.nonEmptyStream().filter(stack -> stack.is(Items.DIAMOND_PICKAXE)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("The nested enchanted pickaxe disappeared"));
        check(tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(
                server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING)) == 3,
                "Unbreaking III must resolve through the actual server enchantment registry after decoding");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void actualLoginLearnsNormalizedPendingItemAndReloginDoesNotDuplicateIt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "knowledge-test");
        UUID owner = profile.getId();
        ItemStack sample = namedDiamond(32);
        ItemInfo normalized = normalize(sample);
        NeoForgeRecoveryKnowledge.recordItem(server, owner, sample);
        NeoForgeRecoveryKnowledge.recordFluid(server, owner, WATER);
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        check(data.pendingItemCount(owner) == 1, "The item must be queued before the owner's real login");
        ServerPlayer player = null;
        try {
            player = login(server, profile);
            check(knowledge(player).hasKnowledge(normalized) && data.pendingItemCount(owner) == 0,
                    "The production login handler must merge pending knowledge into the actual ProjectE attachment");
            int learned = knowledge(player).getKnowledge().size();
            NeoForgeRecoveryKnowledge.recordItem(server, owner, sample.copyWithCount(1));
            NeoForgeRecoveryKnowledge.flush(player);
            NeoForgeRecoveryKnowledge.flush(player);
            check(knowledge(player).getKnowledge().size() == learned && data.pendingItemCount(owner) == 0,
                    "Selling an already-known normalized item and repeated flushes must be true knowledge no-ops");
            logout(server, player);
            player = null;
            player = login(server, profile);
            check(knowledge(player).hasKnowledge(normalized) && knowledge(player).getKnowledge().size() == learned
                    && data.pendingItemCount(owner) == 0 && NeoForgeRecoveryKnowledge.knowsFluid(server, owner, WATER),
                    "A new player object must restore saved ProjectE knowledge without replay while fluid knowledge stays in the world");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void blocksUnknownSchemaAndPreservesMalformedOriginalTags(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("schema", 918);
        unknown.putString("future_payload", "must survive without reinterpretation");
        NeoForgeRecoveryKnowledgeSavedData blocked = NeoForgeRecoveryKnowledgeSavedData.load(unknown, server.registryAccess());
        blocked.queue(owner, ItemInfo.fromItem(Items.DIAMOND), server.registryAccess());
        blocked.recordFluid(owner, WATER);
        check(!blocked.available() && blocked.pendingItemCount(owner) == 0 && !blocked.knowsFluid(owner, WATER)
                && unknown.equals(blocked.save(new CompoundTag(), server.registryAccess())),
                "Unknown schema must reject new knowledge and return the exact original opaque snapshot");

        NeoForgeRecoveryKnowledgeSavedData initial = new NeoForgeRecoveryKnowledgeSavedData();
        initial.queue(owner, ItemInfo.fromItem(Items.DIAMOND), server.registryAccess());
        initial.recordFluid(owner, WATER);
        CompoundTag damaged = initial.save(new CompoundTag(), server.registryAccess());
        CompoundTag malformedItem = new CompoundTag();
        malformedItem.putString("opaque_item_payload", "preserve this failed ItemInfo exactly");
        damaged.getCompound("items").getList(owner.toString(), Tag.TAG_COMPOUND).add(malformedItem.copy());
        StringTag malformedOwner = StringTag.valueOf("preserve this invalid owner entry exactly");
        damaged.getCompound("items").put("invalid-owner", malformedOwner);
        StringTag malformedFluid = StringTag.valueOf("not a valid resource id");
        damaged.getCompound("fluids").getList(owner.toString(), Tag.TAG_STRING).add(malformedFluid);
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(damaged, server.registryAccess());
        check(restored.available() && restored.pendingItemCount(owner) == 1 && restored.knowsFluid(owner, WATER),
                "A malformed item, owner or fluid must not destroy healthy pending knowledge");
        restored.queue(owner, ItemInfo.fromItem(Items.EMERALD), server.registryAccess());
        CompoundTag saved = restored.save(new CompoundTag(), server.registryAccess());
        CompoundTag raw = saved.getCompound("quarantine");
        check(malformedItem.equals(raw.get("items/" + owner + "/1"))
                && malformedOwner.equals(raw.get("items/invalid-owner"))
                && malformedFluid.equals(raw.get("fluids/" + owner + "/1")),
                "Each failed entry must retain its original Tag type and full value in quarantine");
        NeoForgeRecoveryKnowledgeSavedData second = NeoForgeRecoveryKnowledgeSavedData.load(saved, server.registryAccess());
        check(second.pendingItemCount(owner) == 2 && second.knowsFluid(owner, WATER)
                && raw.equals(second.save(new CompoundTag(), server.registryAccess()).getCompound("quarantine")),
                "Healthy new knowledge and raw quarantine must both survive another save/load without overwriting each other");
        helper.succeed();
    }

    @GameTestGenerator
    public static List<TestFunction> controlledRestartTests() {
        String phase = System.getProperty("primordial.test.creditPersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        return List.of(new TestFunction("blackhole_knowledge_persistence", "blackhole_knowledge_persistence." + phase,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                helper -> controlledRestart(helper, phase)));
    }

    /** 同一隔离世界由三个独立 JVM 正常停服保存；磁盘检查先于 read 阶段的真实登录合并。 */
    private static void controlledRestart(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        GameProfile profile = new GameProfile(PERSISTENCE_OWNER, "know-restart");
        ItemStack sample = enchantedContainer(server);
        ItemInfo normalized = normalize(sample);
        ServerPlayer player = null;
        try {
            check(server.getPlayerList().getPlayer(PERSISTENCE_OWNER) == null, "Restart-test knowledge owner must start offline");
            switch (phase) {
                case "write" -> {
                    check(data.pendingItemCount(PERSISTENCE_OWNER) == 0 && !data.knowsFluid(PERSISTENCE_OWNER, WATER),
                            "Write phase requires a fresh isolated world for the fixed knowledge-test owner");
                    player = login(server, profile);
                    check(!knowledge(player).hasKnowledge(normalized), "Write phase must start without the test knowledge");
                    logout(server, player);
                    player = null;
                    NeoForgeRecoveryKnowledge.recordItem(server, PERSISTENCE_OWNER, sample);
                    NeoForgeRecoveryKnowledge.recordItem(server, PERSISTENCE_OWNER, sample.copyWithCount(3));
                    NeoForgeRecoveryKnowledge.recordFluid(server, PERSISTENCE_OWNER, WATER);
                    check(data.pendingItemCount(PERSISTENCE_OWNER) == 1 && data.knowsFluid(PERSISTENCE_OWNER, WATER),
                            "Write phase must leave one offline normalized item and fluid knowledge for normal shutdown");
                }
                case "read" -> {
                    assertKnowledgeOnDisk(server, normalized, true);
                    check(data.pendingItemCount(PERSISTENCE_OWNER) == 1 && data.knowsFluid(PERSISTENCE_OWNER, WATER),
                            "The second JVM must load the first JVM's offline pending item and fluid ID");
                    player = login(server, profile);
                    check(data.pendingItemCount(PERSISTENCE_OWNER) == 0 && knowledge(player).hasKnowledge(normalized),
                            "Actual login after restart must learn through ProjectE and acknowledge the pending item");
                    int learned = knowledge(player).getKnowledge().size();
                    NeoForgeRecoveryKnowledge.flush(player);
                    NeoForgeRecoveryKnowledge.flush(player);
                    check(knowledge(player).getKnowledge().size() == learned, "Read-phase repeated flush must not duplicate knowledge");
                }
                case "verify" -> {
                    assertKnowledgeOnDisk(server, normalized, false);
                    check(data.pendingItemCount(PERSISTENCE_OWNER) == 0 && data.knowsFluid(PERSISTENCE_OWNER, WATER),
                            "The third JVM must load an acknowledged empty item queue and stable fluid knowledge");
                    player = login(server, profile);
                    check(knowledge(player).hasKnowledge(normalized),
                            "The real ProjectE player attachment must retain learned container knowledge across another restart");
                    NeoForgeRecoveryKnowledge.flush(player);
                    check(data.pendingItemCount(PERSISTENCE_OWNER) == 0 && data.knowsFluid(PERSISTENCE_OWNER, WATER),
                            "Another login/flush must keep the queue empty without deleting learned fluids");
                }
                default -> throw new GameTestAssertException("Unknown knowledge persistence phase: " + phase);
            }
        } finally {
            logout(server, player);
        }
        LogUtils.getLogger().info("[Primordial GameTest] Recovery knowledge controlled restart PASS phase={}", phase);
        helper.succeed();
    }

    private static void assertKnowledgeOnDisk(MinecraftServer server, ItemInfo expected, boolean pending) {
        try {
            CompoundTag disk = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/primordial_recovery_knowledge.dat"), NbtAccounter.unlimitedHeap()).getCompound("data");
            check(disk.getInt("schema") == 1, "The previous JVM must write the real versioned knowledge .dat");
            NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(disk, server.registryAccess());
            check(restored.available() && restored.knowsFluid(PERSISTENCE_OWNER, WATER),
                    "The previous JVM's actual disk file must preserve learned water knowledge");
            check(restored.pendingItemCount(PERSISTENCE_OWNER) == (pending ? 1 : 0)
                    && (!pending || restored.pending(PERSISTENCE_OWNER).contains(expected)),
                    "The previous JVM's disk queue must contain exactly the expected normalized registry-aware ItemInfo state");
        } catch (IOException failure) {
            throw new GameTestAssertException("Cannot read the previous JVM's recovery knowledge .dat: " + failure.getMessage());
        }
    }

    private static ItemStack namedDiamond(int count) {
        ItemStack sample = new ItemStack(Items.DIAMOND, count);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("recovery knowledge sample"));
        return sample;
    }

    private static ItemStack enchantedContainer(MinecraftServer server) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchantments.set(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
        tool.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        ItemStack sample = new ItemStack(Items.SHULKER_BOX);
        sample.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(tool, new ItemStack(Items.DIAMOND, 3))));
        return sample;
    }

    private static ItemInfo normalize(ItemStack stack) {
        return IEMCProxy.INSTANCE.getPersistentInfo(ItemInfo.fromStack(stack.copyWithCount(1)));
    }

    private static ServerPlayer login(MinecraftServer server, GameProfile profile) {
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(), false, ConnectionType.NEOFORGE);
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
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel) {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static IKnowledgeProvider knowledge(ServerPlayer player) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(knowledge != null, "The actual installed ProjectE mod must provide the player's knowledge capability");
        return knowledge;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
