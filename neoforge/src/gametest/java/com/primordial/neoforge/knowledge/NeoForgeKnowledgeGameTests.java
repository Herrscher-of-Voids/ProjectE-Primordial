/**
 * 【文件职责】以真实 ProjectE 玩家、离线玩家文件和生产知识 API 验证类别凭证与遗忘闭环。
 * 【变更记录 #1】2026-10-23 13:37:02（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：注册校验、作者/玩家遗忘区分、逐种隔离、11 类凭证、真实离线恢复、坏档及命令权限测试。
 * - 修改：NBT 测试只证明编解码往返；在线/离线测试通过真实登录退出保存，不冒充跨 JVM 重启。
 * - 用途：自动验证默认知识规则和配置关闭时仍存在的显式遗忘边界，不代表用户游戏验收。
 * 【变更记录 #2】2026-10-23 13:41:03（第 2 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：安装 Mekanism 时核实真实氢气注册项及 recordChemical 清遗忘。
 * - 修改：可选正向路径只在真实依赖存在时执行；纯知识查询和幂等学习不推进报价序号。
 * - 用途：两种介质均覆盖生产回收再学习，不以任意字符串账本替代可选模组注册表验证。
 * 【变更记录 #3】2026-10-23 13:43:35（第 3 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。新增：实际账本隔离 owner 后通过正式黑洞能力验证物品、流体和 FE 均不消费。
 * - 修改：测试临时隔离状态和配置全部 finally 恢复；同世界健康 owner 的三个模拟入口继续可用。
 * - 用途：锁定消费前 owner 健康预检，避免接收成功后无法持久学习；不增生产测试接口。
 */
package com.primordial.neoforge.knowledge;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.api.MediaKnowledgeApi;
import com.primordial.neoforge.api.MediaKnowledgeApi.Media;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledgeSavedData;
import com.primordial.neoforge.blackhole.NeoForgeBlackholeBlockEntity;
import com.primordial.neoforge.config.NeoForgeKnowledgeConfig;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeKnowledgeGameTests {
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation LAVA = ResourceLocation.parse("minecraft:lava");
    private static final ResourceLocation CHEMICAL = ResourceLocation.parse("mekanism:hydrogen");

    private NeoForgeKnowledgeGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rejectsInvalidResourcesAndOtherThreadsWithoutWritingKnowledge(GameTestHelper helper) throws InterruptedException {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        CompoundTag before = data.save(new CompoundTag(), server.registryAccess());
        for (ResourceLocation id : new ResourceLocation[]{ResourceLocation.parse("minecraft:empty"),
                ResourceLocation.parse("minecraft:flowing_water"), ResourceLocation.parse("primordial:missing_fluid")}) {
            check(!MediaKnowledgeApi.validResource(Media.FLUID, id)
                    && !MediaKnowledgeApi.learn(server, owner, Media.FLUID, id)
                    && !MediaKnowledgeApi.forget(server, owner, Media.FLUID, id)
                    && !MediaKnowledgeApi.isPurchaseUnlocked(server, owner, Media.FLUID, id),
                    "Empty, flowing and nonexistent fluids must fail before writing knowledge");
        }
        check(!MediaKnowledgeApi.validResource(null, WATER) && !MediaKnowledgeApi.validResource(Media.FLUID, null)
                && !MediaKnowledgeApi.learn(null, owner, Media.FLUID, WATER)
                && !MediaKnowledgeApi.learn(server, null, Media.FLUID, WATER), "Null API inputs must reject");
        AtomicBoolean rejected = new AtomicBoolean();
        Thread worker = new Thread(() -> rejected.set(!MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER)
                && !MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER)
                && !MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER)
                && !NeoForgeResourceKnowledge.allows(server, owner, Kind.ENERGY)), "primordial-knowledge-thread-test");
        worker.start();
        worker.join();
        check(rejected.get() && before.equals(data.save(new CompoundTag(), server.registryAccess())),
                "Wrong-thread and invalid-input calls must leave the entire knowledge ledger unchanged");
        check(!MediaKnowledgeApi.validResource(Media.GAS, ResourceLocation.parse("primordial:missing_chemical")),
                "A made-up chemical must never be valid, including when Mekanism is installed");
        if (!ModList.get().isLoaded("mekanism")) {
            check(!MediaKnowledgeApi.learn(server, owner, Media.GAS, CHEMICAL)
                    && !NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.GAS, CHEMICAL),
                    "Absent optional API must not grant fake chemical knowledge");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void authorGrantForgetAndRealSaleRelearnAreIdempotentAndOwnerScoped(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        check(!MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER), "A new owner starts without explicit knowledge");
        if (NeoForgeKnowledgeConfig.required()) check(!NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.FLUID, WATER),
                "The player's own forget route cannot add exclusions for resources that are not unlocked");
        check(MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER)
                && !MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER),
                "A trusted author may exclude an unknown resource and repeating the revoke is a no-op");
        check(!NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.FLUID, WATER),
                "An explicit exclusion must reject even with knowledge requirements disabled");
        check(MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER)
                && !MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER)
                && MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER)
                && !MediaKnowledgeApi.hasLearned(server, other, Media.FLUID, WATER)
                && !MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, LAVA),
                "Grant removes an exclusion, is idempotent and does not grant another owner or resource");
        long revision = NeoForgeResourceKnowledge.revision(server, owner);
        MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER);
        check(!MediaKnowledgeApi.learn(server, owner, Media.FLUID, WATER)
                && NeoForgeResourceKnowledge.revision(server, owner) == revision,
                "Queries and an idempotent grant must preserve the authorization revision");
        check(NeoForgeResourceKnowledge.forgetMedia(server, owner, Kind.FLUID, WATER)
                && !MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER)
                && !NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.FLUID, WATER),
                "A player can forget an unlocked resource, removing its explicit learned entry");
        NeoForgeRecoveryKnowledge.recordFluid(server, owner, WATER);
        check(MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, WATER)
                && NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.FLUID, WATER),
                "Production successful-sale learning must clear a prior exclusion");
        check(NeoForgeResourceKnowledge.revision(server, owner) > revision,
                "A forget then learn cycle must advance the revision even if final authorization matches");
        if (ModList.get().isLoaded("mekanism")) {
            check(MediaKnowledgeApi.validResource(Media.GAS, CHEMICAL)
                    && MediaKnowledgeApi.learn(server, owner, Media.GAS, CHEMICAL)
                    && MediaKnowledgeApi.forget(server, owner, Media.GAS, CHEMICAL)
                    && !NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.GAS, CHEMICAL),
                    "The installed native chemical registry must validate hydrogen and honor its exclusion");
            NeoForgeRecoveryKnowledge.recordChemical(server, owner, CHEMICAL);
            check(MediaKnowledgeApi.hasLearned(server, owner, Media.GAS, CHEMICAL)
                    && NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.GAS, CHEMICAL)
                    && !MediaKnowledgeApi.hasLearned(server, other, Media.GAS, CHEMICAL),
                    "Production chemical sale learning clears the exclusion only for its actual owner");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void elevenCertificatesUseRealKnowledgeAndForgetOverridesCategory(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, new GameProfile(UUID.randomUUID(), "know-certs"));
        try {
            IKnowledgeProvider knowledge = knowledge(player);
            knowledge.setEmc(BigInteger.valueOf(37));
            check(Kind.values().length == 11, "All eleven Forge resource categories must remain present");
            for (Kind kind : Kind.values()) {
                ItemStack decorated = kind.sample().copyWithCount(7);
                decorated.set(DataComponents.CUSTOM_NAME, Component.literal("presentation only"));
                check(NeoForgeResourceKnowledge.certificateKind(decorated) == kind
                        && ItemStack.matches(kind.sample(), NeoForgeResourceKnowledge.knowledgeSample(decorated)),
                        "A decorated/category certificate must normalize to its own clean count-one sample");
                check(!NeoForgeKnowledgeConfig.required() || !NeoForgeResourceKnowledge.allows(server, player.getUUID(), kind),
                        "Holding a category item alone must not learn it");
                knowledge.addKnowledge(ItemInfo.fromStack(kind.sample()));
                check(NeoForgeResourceKnowledge.allows(server, player.getUUID(), kind),
                        "The actual PE provider must authorize its learned category");
            }
            check(!MediaKnowledgeApi.hasLearned(server, player.getUUID(), Media.FLUID, WATER)
                    && NeoForgeResourceKnowledge.allowsMedia(server, player.getUUID(), Kind.FLUID, WATER),
                    "The fluid certificate unlocks a whole category without inventing explicit per-ID learned entries");
            check(MediaKnowledgeApi.forget(server, player.getUUID(), Media.FLUID, WATER)
                    && !NeoForgeResourceKnowledge.allowsMedia(server, player.getUUID(), Kind.FLUID, WATER)
                    && NeoForgeResourceKnowledge.allowsMedia(server, player.getUUID(), Kind.FLUID, LAVA),
                    "An explicit forgotten ID dominates the category certificate without excluding other fluid IDs");
            knowledge.removeKnowledge(ItemInfo.fromStack(Kind.FLUID.sample()));
            knowledge.addKnowledge(ItemInfo.fromStack(Kind.FLUID.sample()));
            check(!NeoForgeResourceKnowledge.allowsMedia(server, player.getUUID(), Kind.FLUID, WATER),
                    "Removing and relearning a category certificate must not erase explicit forgotten IDs");
            check(MediaKnowledgeApi.learn(server, player.getUUID(), Media.FLUID, WATER)
                    && NeoForgeResourceKnowledge.allowsMedia(server, player.getUUID(), Kind.FLUID, WATER),
                    "An explicit relearn clears the category override");
            knowledge.removeKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample()));
            check(NeoForgeResourceKnowledge.allows(server, player.getUUID(), Kind.ENERGY) == !NeoForgeKnowledgeConfig.required(),
                    "Category authorization must query live native knowledge after removal");
            ItemStack diamond = new ItemStack(Items.DIAMOND);
            check(NeoForgeResourceKnowledge.knowledgeSample(diamond) == diamond && knowledge.getEmc().equals(BigInteger.valueOf(37)),
                    "Knowledge policy does not rewrite ordinary item samples or change EMC");
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void savedOfflineCertificateRemainsReadOnlyAndExplicitForgetSurvivesRelogin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "know-offline");
        UUID owner = profile.getId();
        ServerPlayer previous = login(server, profile);
        ServerPlayer current = null;
        try {
            knowledge(previous).addKnowledge(ItemInfo.fromStack(Kind.FLUID.sample()));
            knowledge(previous).addKnowledge(ItemInfo.fromStack(Kind.ENERGY.sample()));
            knowledge(previous).setEmc(BigInteger.valueOf(19));
            logout(server, previous);
            check(NeoForgeResourceKnowledge.allows(server, owner, Kind.ENERGY)
                    && NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.FLUID, WATER),
                    "Saved offline native PE knowledge must authorize learned certificates");
            check(MediaKnowledgeApi.forget(server, owner, Media.FLUID, WATER)
                    && !NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.FLUID, WATER),
                    "An offline author exclusion must dominate a saved native category certificate");
            check(MediaKnowledgeApi.learn(server, owner, Media.FLUID, LAVA), "Offline author learning must be durable world data");
            check(knowledge(previous).getEmc().equals(BigInteger.valueOf(19))
                    && knowledge(previous).hasKnowledge(ItemInfo.fromStack(Kind.FLUID.sample())),
                    "Offline operations must not mutate the old player attachment");
            current = login(server, profile);
            check(knowledge(current).getEmc().equals(BigInteger.valueOf(19))
                    && !NeoForgeResourceKnowledge.allowsMedia(server, owner, Kind.FLUID, WATER)
                    && MediaKnowledgeApi.hasLearned(server, owner, Media.FLUID, LAVA),
                    "A fresh player object keeps its native EMC and the world's per-resource forgotten/learned state");
        } finally { logout(server, current); logout(server, previous); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void legacyKnowledgeAndForgottenStateRoundtripWithoutCrossKindGrant(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID();
        CompoundTag legacy = legacy();
        ListTag known = new ListTag();
        known.add(StringTag.valueOf(WATER.toString()));
        legacy.getCompound("fluids").put(owner.toString(), known);
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.load(legacy, server.registryAccess());
        check(data.available(owner) && data.knowsFluid(owner, WATER) && !data.knowsChemical(owner, WATER)
                && !data.isForgotten(owner, false, WATER), "Old schema one needs no new optional tables and keeps kind isolation");
        check(data.forget(owner, false, WATER) && !data.forget(owner, false, WATER)
                && data.learn(owner, true, WATER), "Knowledge and exclusions remain separate for the same ID in another kind");
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(
                data.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
        check(restored.available(owner) && restored.isForgotten(owner, false, WATER)
                && !restored.knowsFluid(owner, WATER) && restored.knowsChemical(owner, WATER)
                && !restored.isForgotten(owner, true, WATER), "NBT must preserve the exact per-kind learned and excluded distinction");
        check(restored.learn(owner, false, WATER) && !restored.learn(owner, false, WATER)
                && !restored.isForgotten(owner, false, WATER), "Relearn clears the restored exclusion once");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void malformedExclusionsPreserveRawDataAndPersistOwnerIsolation(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        CompoundTag source = legacy();
        CompoundTag exclusions = new CompoundTag();
        ListTag badList = new ListTag();
        badList.add(StringTag.valueOf("not a resource id"));
        exclusions.put(broken.toString(), badList);
        StringTag badOwner = StringTag.valueOf("opaque invalid owner payload");
        exclusions.put("not-a-uuid", badOwner);
        source.put("forgotten_fluids", exclusions);
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.load(source, server.registryAccess());
        check(data.available() && !data.available(broken) && data.available(healthy)
                && !data.learn(broken, false, WATER) && data.learn(healthy, false, WATER),
                "A broken exclusion locks that owner and leaves a healthy neighbour usable");
        CompoundTag saved = data.save(new CompoundTag(), server.registryAccess());
        check(saved.getCompound("quarantine").get("forgotten_fluids/" + broken + "/0").equals(badList.get(0))
                && saved.getCompound("quarantine").get("forgotten_fluids/not-a-uuid").equals(badOwner),
                "Malformed exclusions retain their original NBT type and payload");
        NeoForgeRecoveryKnowledgeSavedData restored = NeoForgeRecoveryKnowledgeSavedData.load(saved, server.registryAccess());
        check(!restored.available(broken) && restored.available(healthy) && restored.knowsFluid(healthy, WATER),
                "Owner isolation must persist even when the bad original entries now live in quarantine");
        for (String key : new String[]{"forgotten_fluids", "forgotten_chemicals", "blocked_owners"}) {
            CompoundTag badRoot = legacy();
            badRoot.putString(key, "preserve unsupported root type");
            NeoForgeRecoveryKnowledgeSavedData blocked = NeoForgeRecoveryKnowledgeSavedData.load(badRoot, server.registryAccess());
            check(!blocked.available() && badRoot.equals(blocked.save(new CompoundTag(), server.registryAccess())),
                    "An invalid exclusion root must remain an opaque, unavailable complete snapshot");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void adminCommandRequiresPermissionTwoAndReturnsActualChanges(GameTestHelper helper) throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, new GameProfile(UUID.randomUUID(), "know-command"));
        try {
            var dispatcher = server.getCommands().getDispatcher();
            var source = server.createCommandSourceStack().withSuppressedOutput();
            String base = "primordial knowledge ";
            String target = " " + player.getGameProfile().getName() + " fluid minecraft:water";
            boolean denied = false;
            try { dispatcher.execute(base + "learn" + target, source.withPermission(1)); }
            catch (CommandSyntaxException expected) { denied = true; }
            check(denied && !MediaKnowledgeApi.hasLearned(server, player.getUUID(), Media.FLUID, WATER),
                    "Permission one must not be able to grant knowledge");
            check(dispatcher.execute(base + "learn" + target, source.withPermission(2)) == 1
                    && dispatcher.execute(base + "learn" + target, source.withPermission(2)) == 0
                    && dispatcher.execute(base + "check" + target, source.withPermission(2)) == 1
                    && dispatcher.execute(base + "forget" + target, source.withPermission(2)) == 1
                    && dispatcher.execute(base + "check" + target, source.withPermission(2)) == 0,
                    "Administrator learn/forget/check must report actual changes and explicit learned membership");
            check(dispatcher.execute(base + "learn " + player.getGameProfile().getName()
                    + " fluid primordial:missing_fluid", source.withPermission(2)) == 0,
                    "An authorized command still cannot grant nonexistent resources");
        } finally { logout(server, player); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    @SuppressWarnings("unchecked")
    public static void quarantinedOwnerCannotConsumeResourcesWhileHealthyOwnerStillSimulates(GameTestHelper helper)
            throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        UUID broken = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();
        NeoForgeRecoveryKnowledgeSavedData data = NeoForgeRecoveryKnowledgeSavedData.get(server);
        var field = NeoForgeRecoveryKnowledgeSavedData.class.getDeclaredField("blockedOwners");
        field.setAccessible(true);
        Set<UUID> blockedOwners = (Set<UUID>) field.get(data);
        int oldItems = NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.get();
        int oldFluids = NeoForgeServerConfig.BLACKHOLE_MAX_FLUID_MB_PER_TICK.get();
        int oldEnergy = NeoForgeServerConfig.BLACKHOLE_MAX_ENERGY_PER_TICK.get();
        boolean oldSale = NeoForgeServerConfig.FLUID_SALE_ENABLED.get();
        List<? extends String> oldFluidBlacklist = List.copyOf(NeoForgeServerConfig.FLUID_BLACKLIST.get());
        List<? extends String> oldResourceBlacklist = List.copyOf(NeoForgeServerConfig.RESOURCE_BLACKLIST.get());
        try {
            blockedOwners.add(broken);
            NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.set(5);
            NeoForgeServerConfig.BLACKHOLE_MAX_FLUID_MB_PER_TICK.set(5);
            NeoForgeServerConfig.BLACKHOLE_MAX_ENERGY_PER_TICK.set(5);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(true);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(List.of());
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(List.of());
            check(!NeoForgeRecoveryKnowledge.available(server, broken) && NeoForgeRecoveryKnowledge.available(server, healthy),
                    "Only the owner with a broken exclusion ledger must be unavailable to recovery");
            BlockPos position = new BlockPos(1, 1, 1);
            helper.setBlock(position, NeoForgeModRegistries.BLACKHOLE.get());
            NeoForgeBlackholeBlockEntity blackhole = (NeoForgeBlackholeBlockEntity) helper.getBlockEntity(position);
            blackhole.setOwner(broken);
            var items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(position), Direction.UP);
            var fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(position), Direction.UP);
            var energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(position), Direction.UP);
            check(items != null && fluids != null && energy != null && !blackhole.canRecycleResources(),
                    "The actual registered ports must respect their quarantined owner's health gate");
            ItemStack diamonds = new ItemStack(Items.DIAMOND, 5);
            FluidStack water = new FluidStack(Fluids.WATER, 5);
            CompoundTag before = blackhole.saveWithFullMetadata(server.registryAccess());
            check(items.insertItem(0, diamonds, true).getCount() == 5
                    && items.insertItem(0, diamonds, false).getCount() == 5
                    && fluids.fill(water, IFluidHandler.FluidAction.SIMULATE) == 0
                    && fluids.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0
                    && energy.receiveEnergy(5, true) == 0 && energy.receiveEnergy(5, false) == 0,
                    "Neither simulation nor execution may consume any resource for a quarantined owner");
            NeoForgeRecoveryKnowledge.recordItem(server, broken, diamonds);
            NeoForgeBlackholeBlockEntity.serverTick(helper.getLevel(), blackhole.getBlockPos(), blackhole.getBlockState(), blackhole);
            check(before.equals(blackhole.saveWithFullMetadata(server.registryAccess())) && blackhole.pendingCredit().isZero()
                    && NeoForgeEmcSavedData.get(server).pending(broken).isZero()
                    && data.pendingItemCount(broken) == 0 && !data.knowsFluid(broken, WATER)
                    && diamonds.getCount() == 5 && water.getAmount() == 5,
                    "Rejected inputs and recordItem must create no buffer, EMC credit or queued knowledge");
            blackhole.setOwner(healthy);
            check(blackhole.canRecycleResources() && items.insertItem(0, diamonds, true).isEmpty()
                    && fluids.fill(water, IFluidHandler.FluidAction.SIMULATE) == 5 && energy.receiveEnergy(5, true) == 5
                    && NeoForgeEmcSavedData.get(server).pending(healthy).isZero() && data.pendingItemCount(healthy) == 0,
                    "Cached ports must allow a healthy owner to simulate all three resources without global shutdown or side effects");
        } finally {
            blockedOwners.remove(broken);
            NeoForgeServerConfig.BLACKHOLE_MAX_ITEM_COUNT_PER_TICK.set(oldItems);
            NeoForgeServerConfig.BLACKHOLE_MAX_FLUID_MB_PER_TICK.set(oldFluids);
            NeoForgeServerConfig.BLACKHOLE_MAX_ENERGY_PER_TICK.set(oldEnergy);
            NeoForgeServerConfig.FLUID_SALE_ENABLED.set(oldSale);
            NeoForgeServerConfig.FLUID_BLACKLIST.set(oldFluidBlacklist);
            NeoForgeServerConfig.RESOURCE_BLACKLIST.set(oldResourceBlacklist);
        }
        helper.succeed();
    }

    private static CompoundTag legacy() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.put("items", new CompoundTag());
        tag.put("fluids", new CompoundTag());
        return tag;
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
            try { logout(server, player); } finally { channel.finishAndReleaseAll(); }
            throw failure;
        }
    }

    private static void logout(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        try { if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player); }
        finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel)
                channel.finishAndReleaseAll();
        }
    }

    private static IKnowledgeProvider knowledge(ServerPlayer player) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(knowledge != null, "The installed PE mod must supply its real knowledge attachment");
        return knowledge;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
