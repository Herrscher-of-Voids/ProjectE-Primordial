/**
 * 【文件职责】仅在 Mekanism 已加载时生成真实 ChemicalStack、能力、标签与收益持久化验收。
 * 【变更记录 #1】2026-10-22 22:21:04（第 1 次备注 · 2.0.0-neo-dev Mekanism 化学品回收）
 * - 删除：无。
 * - 新增：原生能力发现、模拟、实际知识/EMC、跨面额度、long 上界、拆除拒收、真实标签及统一化学品测试。
 * - 修改：不自动订阅或声明 GameTest 注解；公共生成器在确认可选模组存在后反射 tests()。
 * - 用途：默认测试通过真实方块和正式接收入口验收；chemicalCapPersistencePhase 可用三个独立 JVM 检查磁盘与防重入账。
 * 【变更记录 #2】2026-10-22 22:24:38（第 2 次备注 · 2.0.0-neo-dev 原生源罐交易验收）
 * - 删除：仅比较两个自行创建能力常量的同构断言。
 * - 新增：反射官方 Capabilities.CHEMICAL.block() 身份比较，以及原生 BasicChemicalTank 源库存的模拟/执行闭环。
 * - 修改：源罐按照接收端实际返回的剩余量扣除库存，重复推送仍受同一黑洞预算约束。
 * - 用途：证明真实源资源减少量、收益与知识一致；common 类型仅按名称反射，编译仍只依赖 Mekanism API。
 */
package com.primordial.neoforge.compat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.logging.LogUtils;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeBlackholeBlockEntity;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledgeSavedData;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.MekanismAPI;
import mekanism.api.MekanismAPITags;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.common.IOUtilities;
import org.jetbrains.annotations.Nullable;

public final class NeoForgeMekanismRecoveryTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);
    private static final ResourceLocation HYDROGEN = ResourceLocation.fromNamespaceAndPath("mekanism", "hydrogen");
    private static final ResourceLocation CARBON = ResourceLocation.fromNamespaceAndPath("mekanism", "carbon");
    private static final UUID PERSISTENCE_OWNER = UUID.fromString("b16aa35a-6118-4855-ac52-13cef7044e46");
    private static final BlockCapability<IChemicalHandler, @Nullable Direction> NATIVE_CHEMICAL =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    private NeoForgeMekanismRecoveryTests() {}

    public static List<TestFunction> tests() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(test("native_capability_all_sides", NeoForgeMekanismRecoveryTests::nativeCapabilityAllSides));
        tests.add(test("native_source_tank_transfer", NeoForgeMekanismRecoveryTests::nativeSourceTankTransfer));
        tests.add(test("simulation_preserves_state", NeoForgeMekanismRecoveryTests::simulationPreservesState));
        tests.add(test("actual_sale_teaches_and_pays_once", NeoForgeMekanismRecoveryTests::actualSaleTeachesAndPaysOnce));
        tests.add(test("all_sides_share_one_budget", NeoForgeMekanismRecoveryTests::allSidesShareOneBudget));
        tests.add(test("long_maximum_remainder", NeoForgeMekanismRecoveryTests::longMaximumRemainder));
        tests.add(test("cached_handler_rejects_after_removal", NeoForgeMekanismRecoveryTests::cachedHandlerRejectsAfterRemoval));
        tests.add(test("native_tag_blacklist", NeoForgeMekanismRecoveryTests::nativeTagBlacklist));
        tests.add(test("setter_and_invalid_slot_do_not_sell", NeoForgeMekanismRecoveryTests::setterAndInvalidSlotDoNotSell));
        tests.add(test("unified_infusion_chemical", NeoForgeMekanismRecoveryTests::unifiedInfusionChemical));
        String phase = System.getProperty("primordial.test.chemicalCapPersistencePhase", "off");
        if (!phase.equals("off")) tests.add(new TestFunction("mekanism_cap_persistence", "mekanism_cap_persistence." + phase,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                helper -> controlledRestart(helper, phase)));
        return List.copyOf(tests);
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> action) {
        return new TestFunction("mekanism_recovery_native", "mekanism_recovery_native." + name,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true, action);
    }

    private static void nativeCapabilityAllSides(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            NeoForgeBlackholeBlockEntity block = place(helper, UUID.randomUUID());
            try {
                Object official = Class.forName("mekanism.common.capabilities.Capabilities").getField("CHEMICAL").get(null);
                check(official.getClass().getMethod("block").invoke(official) == NATIVE_CHEMICAL
                        && NATIVE_CHEMICAL == NeoForgeMekanismRecovery.CHEMICAL,
                        "The registered API-only capability must be identical to the real Mekanism CHEMICAL.block() constant");
            } catch (ReflectiveOperationException failure) {
                throw new GameTestAssertException("Cannot inspect the installed Mekanism native capability: " + failure);
            }
            for (Direction side : Direction.values()) assertEmptyPort(port(helper, block, side));
            assertEmptyPort(port(helper, block, null));
        }
        helper.succeed();
    }

    private static void nativeSourceTankTransfer(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            IChemicalHandler sink = port(helper, block, Direction.UP);
            IChemicalTank source = BasicChemicalTank.create(100, null);
            check(source.insert(sample(HYDROGEN, 41), Action.EXECUTE, AutomationType.INTERNAL).isEmpty()
                    && source.getStored() == 41, "The real native source tank must hold the original 41 mB");
            ChemicalStack offered = source.extract(41, Action.SIMULATE, AutomationType.EXTERNAL);
            check(sink.insertChemical(offered, Action.SIMULATE).getAmount() == 18 && source.getStored() == 41
                    && block.pendingCredit().isZero() && !knows(helper, owner, HYDROGEN),
                    "A fully simulated source-to-sink transfer must change neither native inventory nor proceeds/knowledge");
            ChemicalStack remainder = sink.insertChemical(offered, Action.EXECUTE);
            long transferred = offered.getAmount() - remainder.getAmount();
            ChemicalStack removed = source.extract(transferred, Action.EXECUTE, AutomationType.EXTERNAL);
            check(transferred == 23 && removed.getAmount() == 23 && source.getStored() == 18 && offered.getAmount() == 41
                    && block.pendingCredit().equals(price(removed).multiply(23)) && knows(helper, owner, HYDROGEN),
                    "Source inventory must fall by exactly the actually accepted 23 mB while the source offer stays unchanged");
            ChemicalStack nextOffer = source.extract(Long.MAX_VALUE, Action.SIMULATE, AutomationType.EXTERNAL);
            ChemicalStack nextRemainder = sink.insertChemical(nextOffer, Action.EXECUTE);
            check(nextRemainder.getAmount() == 18
                    && source.extract(nextOffer.getAmount() - nextRemainder.getAmount(), Action.EXECUTE, AutomationType.EXTERNAL).isEmpty()
                    && source.getStored() == 18 && block.settlePending()
                    && ledger(helper).pending(owner).equals(price(removed).multiply(23)),
                    "An exhausted sink must leave the remaining native source inventory intact and pay only its first transfer");
        }
        helper.succeed();
    }

    private static void simulationPreservesState(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            IChemicalHandler port = port(helper, block, Direction.UP);
            ChemicalStack input = sample(HYDROGEN, 31);
            CompoundTag before = block.saveWithFullMetadata(helper.getLevel().registryAccess());
            check(port.insertChemical(0, input, Action.SIMULATE).getAmount() == 8
                    && port.insertChemical(input, Action.SIMULATE).getAmount() == 8,
                    "Repeated simulated insertion must see the full unchanged 23 mB budget");
            check(input.getAmount() == 31 && before.equals(block.saveWithFullMetadata(helper.getLevel().registryAccess()))
                    && ledger(helper).pending(owner).isZero() && !knows(helper, owner, HYDROGEN),
                    "Simulation must preserve source stack, block NBT, exact ledger and chemical knowledge");
            check(port.insertChemical(input, Action.EXECUTE).getAmount() == 8,
                    "Simulation must not spend the actual execution budget");
        }
        helper.succeed();
    }

    private static void actualSaleTeachesAndPaysOnce(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            ChemicalStack input = sample(HYDROGEN, 7);
            EmcAmount expected = price(input).multiply(7);
            check(!expected.isZero() && !knows(helper, owner, HYDROGEN), "The native chemical must start priced and unknown");
            check(port(helper, block, null).insertChemical(input, Action.EXECUTE).isEmpty() && input.getAmount() == 7,
                    "Native execution must consume precisely the returned amount without mutating the source stack");
            check(block.pendingCredit().equals(expected) && ledger(helper).pending(owner).isZero()
                    && knows(helper, owner, HYDROGEN)
                    && !NeoForgeRecoveryKnowledge.knowsFluid(helper.getLevel().getServer(), owner, HYDROGEN),
                    "Actual reception must queue its exact proceeds and teach only the chemical channel");
            check(block.settlePending() && block.pendingCredit().isZero() && ledger(helper).pending(owner).equals(expected),
                    "The truly offline owner must receive exact chemical EMC in the world ledger");
            block.settlePending();
            check(ledger(helper).pending(owner).equals(expected), "Repeated settlement must not pay the same chemical twice");
        }
        helper.succeed();
    }

    private static void allSidesShareOneBudget(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            long accepted = 0;
            ChemicalStack input = sample(HYDROGEN, 5);
            for (Direction side : Direction.values()) accepted += 5 - port(helper, block, side).insertChemical(input, Action.EXECUTE).getAmount();
            accepted += 5 - port(helper, block, null).insertChemical(input, Action.EXECUTE).getAmount();
            check(accepted == 23 && input.getAmount() == 5 && block.pendingCredit().equals(price(input).multiply(23)),
                    "Six sided handlers and the null-side handler must share exactly one 23 mB allowance");
            check(port(helper, block, Direction.NORTH).insertChemical(input, Action.SIMULATE).getAmount() == 5,
                    "A newly queried native port must not reset an exhausted block budget");
        }
        helper.succeed();
    }

    private static void longMaximumRemainder(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            NeoForgeBlackholeBlockEntity block = place(helper, UUID.randomUUID());
            IChemicalHandler port = port(helper, block, Direction.DOWN);
            ChemicalStack input = sample(HYDROGEN, Long.MAX_VALUE);
            ChemicalStack simulated = port.insertChemical(input, Action.SIMULATE);
            ChemicalStack actual = port.insertChemical(input, Action.EXECUTE);
            check(simulated.getAmount() == Long.MAX_VALUE - 23 && actual.getAmount() == Long.MAX_VALUE - 23
                    && actual != input && input.getAmount() == Long.MAX_VALUE
                    && ChemicalStack.isSameChemical(input, actual),
                    "Long.MAX_VALUE input must retain its precise positive remainder and native chemical holder");
            check(block.pendingCredit().equals(price(input).multiply(23)), "Huge input must pay only the bounded accepted amount");
        }
        helper.succeed();
    }

    private static void cachedHandlerRejectsAfterRemoval(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            IChemicalHandler cached = port(helper, block, Direction.EAST);
            helper.setBlock(POSITION, Blocks.AIR);
            ChemicalStack input = sample(HYDROGEN, 9);
            check(!cached.isValid(0, input) && cached.insertChemical(input, Action.SIMULATE).getAmount() == 9
                    && cached.insertChemical(input, Action.EXECUTE).getAmount() == 9 && input.getAmount() == 9,
                    "Previously cached capability objects must reject simulation and execution after block removal");
            check(ledger(helper).pending(owner).isZero() && !knows(helper, owner, HYDROGEN),
                    "A removed native port must neither pay nor learn from rejected input");
        }
        helper.succeed();
    }

    private static void nativeTagBlacklist(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            IChemicalHandler port = port(helper, block, Direction.WEST);
            ChemicalStack input = sample(HYDROGEN, 11);
            check(input.is(MekanismAPITags.Chemicals.GASEOUS), "The target Mekanism data pack must bind hydrogen's real gaseous tag");
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of("#" + MekanismAPITags.Chemicals.GASEOUS.location()));
            check(!port.isValid(0, input) && port.insertChemical(input, Action.SIMULATE).getAmount() == 11
                    && port.insertChemical(input, Action.EXECUTE).getAmount() == 11
                    && block.pendingCredit().isZero() && !knows(helper, owner, HYDROGEN),
                    "A genuine registry tag blacklist must deny the native capability without teaching or charging budget");
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
            check(port.insertChemical(input.copyWithAmount(23), Action.EXECUTE).isEmpty(),
                    "Removing the blacklist must reveal the still-unspent full allowance");
        }
        helper.succeed();
    }

    private static void setterAndInvalidSlotDoNotSell(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            IChemicalHandler port = port(helper, block, Direction.UP);
            ChemicalStack input = sample(HYDROGEN, 13);
            check(!port.isValid(-1, input) && !port.isValid(1, input)
                    && port.insertChemical(-1, input, Action.EXECUTE).getAmount() == 13
                    && port.insertChemical(1, input, Action.EXECUTE).getAmount() == 13,
                    "Only virtual slot zero may accept resources");
            boolean refused = false;
            try { port.setChemicalInTank(0, input); } catch (UnsupportedOperationException expected) { refused = true; }
            check(refused && block.pendingCredit().isZero() && !knows(helper, owner, HYDROGEN),
                    "A raw inventory setter must never be interpreted as a sale or silently discard the caller's stack");
            block.setOwner(null);
            check(port.insertChemical(input, Action.EXECUTE).getAmount() == 13 && !port.isValid(0, input),
                    "An ownerless capability must leave the whole native input with the source");
        }
        helper.succeed();
    }

    private static void unifiedInfusionChemical(GameTestHelper helper) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity block = place(helper, owner);
            ChemicalStack carbon = sample(CARBON, 17);
            check(carbon.is(MekanismAPITags.Chemicals.CARBON) && !carbon.is(MekanismAPITags.Chemicals.GASEOUS),
                    "The native carbon infusion must be a real unified chemical outside the gas rendering tag");
            check(port(helper, block, Direction.SOUTH).insertChemical(carbon, Action.EXECUTE).isEmpty()
                    && block.pendingCredit().equals(price(carbon).multiply(17)) && knows(helper, owner, CARBON),
                    "Unified recovery must support old infusion chemicals, not filter the channel to gaseous rendering");
        }
        helper.succeed();
    }

    /** write/read/verify 分别启动 JVM；read/verify 先检查上个进程的实际文件，再执行空结算验证不重复发放。 */
    private static void controlledRestart(GameTestHelper helper, String phase) {
        try (ConfigSnapshot ignored = new ConfigSnapshot()) {
            MinecraftServer server = helper.getLevel().getServer();
            check(server.getPlayerList().getPlayer(PERSISTENCE_OWNER) == null, "The persistence owner must stay truly offline");
            NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.set(100);
            EmcAmount expected = price(sample(HYDROGEN, 37)).multiply(37);
            if (phase.equals("write")) {
                check(ledger(helper).pending(PERSISTENCE_OWNER).isZero() && !knows(helper, PERSISTENCE_OWNER, HYDROGEN),
                        "Write phase requires a fresh isolated capability-test owner");
                NeoForgeBlackholeBlockEntity block = place(helper, PERSISTENCE_OWNER);
                check(port(helper, block, null).insertChemical(sample(HYDROGEN, 37), Action.EXECUTE).isEmpty()
                        && block.settlePending(), "Write phase must transfer a real native ChemicalStack and settle its proceeds");
            } else if (phase.equals("read") || phase.equals("verify")) {
                assertDisk(server, expected);
                NeoForgeBlackholeBlockEntity block = place(helper, PERSISTENCE_OWNER);
                check(block.pendingCredit().isZero() && block.settlePending() && block.settlePending(),
                        "An empty native receiver must have no saved chemical sale to replay after restart");
            } else throw new GameTestAssertException("Unknown chemical capability persistence phase: " + phase);
            check(ledger(helper).pending(PERSISTENCE_OWNER).equals(expected) && knows(helper, PERSISTENCE_OWNER, HYDROGEN),
                    "Every process must retain exactly the one actual chemical sale and its owner knowledge");
            server.overworld().getDataStorage().save();
            IOUtilities.waitUntilIOWorkerComplete();
            assertDisk(server, expected);
            LogUtils.getLogger().info("[Primordial GameTest] Chemical capability controlled restart PASS phase={}", phase);
        }
        helper.succeed();
    }

    private static void assertDisk(MinecraftServer server, EmcAmount expected) {
        try {
            var directory = server.getWorldPath(LevelResource.ROOT).resolve("data");
            CompoundTag credits = NbtIo.readCompressed(directory.resolve("primordial_emc_credits.dat"), NbtAccounter.unlimitedHeap()).getCompound("data");
            check(NeoForgeEmcSavedData.load(credits, server.registryAccess()).pending(PERSISTENCE_OWNER).equals(expected),
                    "The actual previous-JVM EMC file must contain exactly one native chemical sale");
            CompoundTag knowledge = NbtIo.readCompressed(directory.resolve("primordial_recovery_knowledge.dat"), NbtAccounter.unlimitedHeap()).getCompound("data");
            var ids = knowledge.getCompound("chemicals").getList(PERSISTENCE_OWNER.toString(), Tag.TAG_STRING);
            check(ids.size() == 1 && ids.getString(0).equals(HYDROGEN.toString())
                    && NeoForgeRecoveryKnowledgeSavedData.load(knowledge, server.registryAccess()).knowsChemical(PERSISTENCE_OWNER, HYDROGEN),
                    "The actual knowledge file must retain exactly one hydrogen ID for the fixed offline owner");
        } catch (IOException failure) {
            throw new GameTestAssertException("Cannot read actual chemical capability persistence files: " + failure.getMessage());
        }
    }

    private static ChemicalStack sample(ResourceLocation id, long amount) {
        return new ChemicalStack(MekanismAPI.CHEMICAL_REGISTRY.getHolder(ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id))
                .orElseThrow(() -> new GameTestAssertException("Native Mekanism chemical is not registered: " + id)), amount);
    }

    private static EmcAmount price(ChemicalStack stack) {
        ResourceLocation id = stack.getChemicalHolder().unwrapKey().orElseThrow().location();
        return NeoForgeRecoveryPrices.chemical(id, tag -> stack.is(TagKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, tag)));
    }

    private static NeoForgeBlackholeBlockEntity place(GameTestHelper helper, UUID owner) {
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        check(helper.getBlockEntity(POSITION) instanceof NeoForgeBlackholeBlockEntity, "The actual registered blackhole must be placed");
        NeoForgeBlackholeBlockEntity block = (NeoForgeBlackholeBlockEntity) helper.getBlockEntity(POSITION);
        block.setOwner(owner);
        return block;
    }

    private static IChemicalHandler port(GameTestHelper helper, NeoForgeBlackholeBlockEntity block, @Nullable Direction side) {
        IChemicalHandler port = helper.getLevel().getCapability(NATIVE_CHEMICAL, block.getBlockPos(), side);
        check(port != null, "The actual world must expose Mekanism's native chemical handler for side " + side);
        return port;
    }

    private static void assertEmptyPort(IChemicalHandler port) {
        check(port.getChemicalTanks() == 1 && port.getChemicalInTank(0).isEmpty() && port.getChemicalTankCapacity(0) == 23
                && port.getChemicalTankCapacity(-1) == 0 && port.extractChemical(0, Long.MAX_VALUE, Action.EXECUTE).isEmpty()
                && port.extractChemical(Long.MAX_VALUE, Action.SIMULATE).isEmpty(),
                "The single virtual native tank must expose capacity but no stored or extractable chemicals");
    }

    private static NeoForgeEmcSavedData ledger(GameTestHelper helper) { return NeoForgeEmcSavedData.get(helper.getLevel().getServer()); }
    private static boolean knows(GameTestHelper helper, UUID owner, ResourceLocation id) {
        return NeoForgeRecoveryKnowledge.knowsChemical(helper.getLevel().getServer(), owner, id);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }

    private static final class ConfigSnapshot implements AutoCloseable {
        private final int budget = NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.get();
        private final boolean sale = NeoForgeServerConfig.GAS_SALE_ENABLED.get();
        private final List<? extends String> blacklist = List.copyOf(NeoForgeServerConfig.GAS_BLACKLIST.get());

        private ConfigSnapshot() {
            NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.set(23);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(true);
            NeoForgeServerConfig.GAS_BLACKLIST.set(List.of());
        }

        @Override public void close() {
            NeoForgeServerConfig.BLACKHOLE_MAX_GAS_MB_PER_TICK.set(budget);
            NeoForgeServerConfig.GAS_SALE_ENABLED.set(sale);
            NeoForgeServerConfig.GAS_BLACKLIST.set(blacklist);
        }
    }
}
