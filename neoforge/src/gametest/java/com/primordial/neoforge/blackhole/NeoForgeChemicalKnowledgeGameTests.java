/**
 * 【文件职责】不加载可选模组类型，验收化学品 ID 知识去重、通道隔离、坏数据和真实世界保存。
 * 【变更记录 #1】2026-10-22 22:16:19（第 1 次备注 · 2.0.0-neo-dev 可选化学品知识）
 * - 删除：无。
 * - 新增：离线学习、旧 schema 1 缺字段、错误根类型封锁、子项原文隔离及三进程重启测试。
 * - 修改：chemicalPersistencePhase 默认 off 不注册重启测试；write/read/verify 使用独立固定 UUID 和实际 .dat。
 * - 用途：确认同名流体不会授予化学品知识；实际保存等待 NeoForge IO 完成，不把内存往返当作重启。
 */
package com.primordial.neoforge.blackhole;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;

import moze_intel.projecte.api.ItemInfo;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeChemicalKnowledgeGameTests {
    private static final UUID PERSISTENCE_OWNER = UUID.fromString("c81506f6-4d9f-46a9-bc7e-0ac185fd8aa3");
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");
    private static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen");
    private static final ResourceLocation WATER = ResourceLocation.withDefaultNamespace("water");

    private NeoForgeChemicalKnowledgeGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void recordsOfflineChemicalsOnceWithoutTeachingOtherChannelsOrOwners(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        check(server.getPlayerList().getPlayer(owner) == null, "The chemical knowledge owner must be truly offline");
        NeoForgeRecoveryKnowledge.recordChemical(server, owner, HYDROGEN);
        NeoForgeRecoveryKnowledge.recordChemical(server, owner, HYDROGEN);
        NeoForgeRecoveryKnowledge.recordFluid(server, owner, WATER);
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        check(NeoForgeRecoveryKnowledge.knowsChemical(server, owner, HYDROGEN)
                && !NeoForgeRecoveryKnowledge.knowsChemical(server, owner, WATER)
                && !NeoForgeRecoveryKnowledge.knowsChemical(server, stranger, HYDROGEN)
                && !NeoForgeRecoveryKnowledge.knowsFluid(server, owner, HYDROGEN)
                && data.pendingItemCount(owner) == 0,
                "Chemical, fluid, item and owner channels must remain independent");
        CompoundTag saved = data.save(new CompoundTag(), server.registryAccess());
        check(saved.getCompound("chemicals").getList(owner.toString(), Tag.TAG_STRING).size() == 1,
                "Repeated actual records of one chemical must create exactly one ID entry");
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(saved, server.registryAccess());
        check(restored.available() && restored.knowsChemical(owner, HYDROGEN) && restored.knowsFluid(owner, WATER)
                && !restored.knowsChemical(owner, WATER) && !restored.knowsFluid(owner, HYDROGEN),
                "SavedData reload must preserve both channels without converting one kind of knowledge into another");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void readsEarlierSchemaOneWithoutChemicalsAndKeepsExistingKnowledge(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        NeoForgeRecoveryKnowledgeSavedData initial = new NeoForgeRecoveryKnowledgeSavedData();
        initial.queue(owner, ItemInfo.fromItem(Items.DIAMOND), server.registryAccess());
        initial.recordFluid(owner, WATER);
        CompoundTag earlier = initial.save(new CompoundTag(), server.registryAccess());
        earlier.remove("chemicals");
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(earlier, server.registryAccess());
        check(restored.available() && restored.pendingItemCount(owner) == 1 && restored.knowsFluid(owner, WATER)
                && !restored.knowsChemical(owner, HYDROGEN), "The earlier schema-1 snapshot must load with an empty chemical channel");
        restored.recordChemical(owner, HYDROGEN);
        CompoundTag updated = restored.save(new CompoundTag(), server.registryAccess());
        NeoForgeRecoveryKnowledgeSavedData second = NeoForgeRecoveryKnowledgeSavedData.load(updated, server.registryAccess());
        check(updated.getInt("schema") == 1 && second.pendingItemCount(owner) == 1
                && second.knowsFluid(owner, WATER) && second.knowsChemical(owner, HYDROGEN),
                "Adding chemical knowledge must retain the existing item queue, fluid knowledge and schema version");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void blocksWrongChemicalRootTypeAndUnknownSchemaWithoutChangingTheirRawData(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        NeoForgeRecoveryKnowledgeSavedData initial = new NeoForgeRecoveryKnowledgeSavedData();
        initial.recordFluid(owner, WATER);
        CompoundTag wrongType = initial.save(new CompoundTag(), server.registryAccess());
        wrongType.put("chemicals", StringTag.valueOf("preserve the entire malformed chemical root"));
        assertBlocked(server, owner, wrongType);
        CompoundTag future = initial.save(new CompoundTag(), server.registryAccess());
        future.putInt("schema", 902);
        future.putString("future_chemical_payload", "opaque future data must remain untouched");
        assertBlocked(server, owner, future);
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void quarantinesBadChemicalOwnersAndIdsWithoutDamagingHealthyOrLegacyEntries(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        UUID malformedListOwner = UUID.randomUUID();
        NeoForgeRecoveryKnowledgeSavedData initial = new NeoForgeRecoveryKnowledgeSavedData();
        initial.queue(owner, ItemInfo.fromItem(Items.DIAMOND), server.registryAccess());
        initial.recordFluid(owner, WATER);
        initial.recordChemical(owner, HYDROGEN);
        CompoundTag damaged = initial.save(new CompoundTag(), server.registryAccess());
        StringTag badId = StringTag.valueOf("not a valid chemical id");
        StringTag badOwner = StringTag.valueOf("keep this invalid owner payload exactly");
        IntTag wrongList = IntTag.valueOf(827);
        damaged.getCompound("chemicals").getList(owner.toString(), Tag.TAG_STRING).add(badId);
        damaged.getCompound("chemicals").put("bad-chemical-owner", badOwner);
        damaged.getCompound("chemicals").put(malformedListOwner.toString(), wrongList);
        CompoundTag legacyRaw = new CompoundTag();
        legacyRaw.putString("items/legacy-original", "preserve the pre-existing item quarantine key");
        legacyRaw.putString("fluids/legacy-original", "preserve the pre-existing fluid quarantine key");
        damaged.put("quarantine", legacyRaw.copy());
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(damaged, server.registryAccess());
        check(restored.available() && restored.knowsChemical(owner, HYDROGEN) && restored.knowsFluid(owner, WATER)
                && restored.pendingItemCount(owner) == 1, "Malformed chemical entries must not block healthy knowledge in any channel");
        restored.recordChemical(owner, OXYGEN);
        CompoundTag saved = restored.save(new CompoundTag(), server.registryAccess());
        CompoundTag raw = saved.getCompound("quarantine");
        check(badId.equals(raw.get("chemicals/" + owner + "/1"))
                && badOwner.equals(raw.get("chemicals/bad-chemical-owner"))
                && wrongList.equals(raw.get("chemicals/" + malformedListOwner))
                && legacyRaw.get("items/legacy-original").equals(raw.get("items/legacy-original"))
                && legacyRaw.get("fluids/legacy-original").equals(raw.get("fluids/legacy-original")),
                "Bad chemical IDs and owners must retain exact raw types under chemicals/ while legacy keys remain unchanged");
        NeoForgeRecoveryKnowledgeSavedData second = NeoForgeRecoveryKnowledgeSavedData.load(saved, server.registryAccess());
        check(second.knowsChemical(owner, HYDROGEN) && second.knowsChemical(owner, OXYGEN)
                && second.pendingItemCount(owner) == 1 && second.knowsFluid(owner, WATER)
                && raw.equals(second.save(new CompoundTag(), server.registryAccess()).get("quarantine")),
                "A second reload must retain healthy chemical additions and every original quarantined payload");
        helper.succeed();
    }

    @GameTestGenerator
    public static List<TestFunction> controlledRestartTests() {
        String phase = System.getProperty("primordial.test.chemicalPersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        return List.of(new TestFunction("blackhole_chemical_persistence", "blackhole_chemical_persistence." + phase,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                helper -> controlledRestart(helper, phase)));
    }

    /** 三个独立 JVM 复用同一世界；读取上个进程的文件先于当前阶段的去重重写。 */
    private static void controlledRestart(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        check(server.getPlayerList().getPlayer(PERSISTENCE_OWNER) == null, "The fixed chemical restart owner must remain offline");
        switch (phase) {
            case "write" -> {
                check(!data.knowsChemical(PERSISTENCE_OWNER, HYDROGEN) && !data.knowsChemical(PERSISTENCE_OWNER, OXYGEN)
                        && !data.knowsFluid(PERSISTENCE_OWNER, WATER), "Write phase requires a fresh isolated chemical-test owner");
                recordFixedKnowledge(server);
            }
            case "read", "verify" -> {
                assertDisk(server);
                assertFixedKnowledge(data.save(new CompoundTag(), server.registryAccess()));
                recordFixedKnowledge(server);
            }
            default -> throw new GameTestAssertException("Unknown chemical persistence phase: " + phase);
        }
        check(data.knowsChemical(PERSISTENCE_OWNER, HYDROGEN) && data.knowsChemical(PERSISTENCE_OWNER, OXYGEN)
                && !data.knowsChemical(PERSISTENCE_OWNER, WATER) && !data.knowsFluid(PERSISTENCE_OWNER, HYDROGEN)
                && data.knowsFluid(PERSISTENCE_OWNER, WATER) && data.pendingItemCount(PERSISTENCE_OWNER) == 0,
                "Restart phases must keep exact owner/channel isolation and no item queue");
        assertFixedKnowledge(data.save(new CompoundTag(), server.registryAccess()));
        server.overworld().getDataStorage().save();
        IOUtilities.waitUntilIOWorkerComplete();
        assertDisk(server);
        LogUtils.getLogger().info("[Primordial GameTest] Chemical knowledge controlled restart PASS phase={}", phase);
        helper.succeed();
    }

    private static void recordFixedKnowledge(MinecraftServer server) {
        for (int duplicate = 0; duplicate < 3; duplicate++) {
            NeoForgeRecoveryKnowledge.recordChemical(server, PERSISTENCE_OWNER, HYDROGEN);
            NeoForgeRecoveryKnowledge.recordChemical(server, PERSISTENCE_OWNER, OXYGEN);
            NeoForgeRecoveryKnowledge.recordFluid(server, PERSISTENCE_OWNER, WATER);
        }
    }

    private static void assertDisk(MinecraftServer server) {
        try {
            CompoundTag disk = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/primordial_recovery_knowledge.dat"), NbtAccounter.unlimitedHeap()).getCompound("data");
            assertFixedKnowledge(disk);
            NeoForgeRecoveryKnowledgeSavedData loaded = NeoForgeRecoveryKnowledgeSavedData.load(disk, server.registryAccess());
            check(loaded.available() && loaded.knowsChemical(PERSISTENCE_OWNER, HYDROGEN)
                    && loaded.knowsChemical(PERSISTENCE_OWNER, OXYGEN) && loaded.knowsFluid(PERSISTENCE_OWNER, WATER),
                    "The actual .dat must decode into both chemical IDs and independent fluid knowledge");
        } catch (IOException failure) {
            throw new GameTestAssertException("Cannot read the chemical knowledge persistence file: " + failure.getMessage());
        }
    }

    private static void assertFixedKnowledge(CompoundTag saved) {
        check(saved.getInt("schema") == 1 && saved.contains("chemicals", Tag.TAG_COMPOUND),
                "The real knowledge snapshot must retain schema 1 and its chemical channel");
        ListTag chemicalIds = saved.getCompound("chemicals").getList(PERSISTENCE_OWNER.toString(), Tag.TAG_STRING);
        Set<String> actual = Set.copyOf(chemicalIds.stream().map(Tag::getAsString).toList());
        check(chemicalIds.size() == 2 && actual.equals(Set.of(HYDROGEN.toString(), OXYGEN.toString())),
                "Every save and restart must retain exactly one occurrence of each fixed chemical ID");
        ListTag fluidIds = saved.getCompound("fluids").getList(PERSISTENCE_OWNER.toString(), Tag.TAG_STRING);
        check(fluidIds.size() == 1 && fluidIds.getString(0).equals(WATER.toString()),
                "Fluid knowledge must remain one water ID without chemical IDs leaking across channels");
    }

    private static void assertBlocked(MinecraftServer server, UUID owner, CompoundTag raw) {
        NeoForgeRecoveryKnowledgeSavedData blocked = NeoForgeRecoveryKnowledgeSavedData.load(raw, server.registryAccess());
        blocked.recordChemical(owner, HYDROGEN);
        blocked.recordFluid(owner, WATER);
        blocked.queue(owner, ItemInfo.fromItem(Items.DIAMOND), server.registryAccess());
        check(!blocked.available() && !blocked.knowsChemical(owner, HYDROGEN) && !blocked.knowsFluid(owner, WATER)
                && blocked.pendingItemCount(owner) == 0 && raw.equals(blocked.save(new CompoundTag(), server.registryAccess())),
                "An unknown root must block every channel and return its complete untouched source snapshot");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
