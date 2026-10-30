/**
 * 【文件职责】以真实 NeoForge 世界配置、命令分发和个人阶段账本验证系统 08 的基础契约。
 * 【变更记录 #1】2026-10-23 16:23:11（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无。
 * - 新增：UUID 隔离、幂等序号、128 字符边界、原始坏档保留、四类规则叠加/更新与真实命令权限测试。
 * - 修改：NBT 往返不冒充跨 JVM 重启；在线选择器使用真实登录，配置及临时隔离状态均 finally 恢复。
 * - 用途：证明阶段只限制匹配资源的生成，默认空规则及健康主人不受坏玩家条目影响；自动测试不替代玩家验收。
 * 【变更记录 #2】2026-10-23 16:28:18（第 2 次备注 · 2.0.0-neoforge-stages）
 * - 删除：把总长恰好 512 字符的合法规则误列为超长输入的断言。
 * - 新增：明确验证 512 字符规则接受、513 字符规则拒绝的相邻边界。
 * - 修改：只修正测试样本长度；生产解析器沿用 Forge 的整条规则不超过 512 字符限制，不新增资源 ID 截断。
 * - 用途：准确区分完整规则长度与其中资源路径长度，避免错误测试推动不必要的跨版本行为差异。
 */
package com.primordial.neoforge.stage;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeStageDataGameTests {
    private static final ResourceLocation STAGE = ResourceLocation.parse("pack:water");
    private static final ResourceLocation BASE = ResourceLocation.parse("pack:base");
    private static final ResourceLocation EXTRA = ResourceLocation.parse("pack:extra");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");

    private NeoForgeStageDataGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void personalStagesAreIdempotentAndRetainRevocationThroughNbt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        NeoForgePlayerStagesSavedData data = new NeoForgePlayerStagesSavedData();
        check(data.revision(owner) == 0 && data.grant(owner, STAGE) && data.isDirty() && data.revision(owner) == 1,
                "The first real grant must dirty the ledger and advance only that owner's revision");
        data.setDirty(false);
        check(!data.grant(owner, STAGE) && !data.revoke(other, STAGE) && !data.isDirty()
                && data.revision(owner) == 1 && data.revision(other) == 0 && !data.has(other, STAGE),
                "Duplicate grants, missing revocations and another UUID must not change the original owner's stage");
        boolean immutable = false;
        try { data.stages(owner).clear(); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable && data.has(owner, STAGE), "A caller must not mutate stages through the returned snapshot");
        NeoForgePlayerStagesSavedData restored = NeoForgePlayerStagesSavedData.load(
                data.save(new CompoundTag(), server.registryAccess()), server.registryAccess());
        check(restored.has(owner, STAGE) && !restored.has(other, STAGE) && !restored.isDirty()
                && restored.revision(owner) == 0, "NBT reload retains membership while process-local quote revisions restart");
        check(restored.revoke(owner, STAGE) && restored.grant(owner, STAGE) && restored.revision(owner) == 2,
                "Revoke then regrant must advance twice even when the final allowed state matches the original");
        restored.revoke(owner, STAGE);
        CompoundTag revoked = restored.save(new CompoundTag(), server.registryAccess());
        check(!revoked.getCompound("players").contains(owner.toString())
                && NeoForgePlayerStagesSavedData.load(revoked, server.registryAccess()).stages(owner).isEmpty(),
                "Revocation and removal of an empty owner must persist without deleting other players");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void validatesStageAndRuleBoundsWithoutTruncation(GameTestHelper helper) {
        ResourceLocation longest = ResourceLocation.parse("pack:" + "a".repeat(123));
        ResourceLocation tooLong = ResourceLocation.parse("pack:" + "a".repeat(124));
        check(NeoForgePlayerStagesSavedData.validStage(longest) && !NeoForgePlayerStagesSavedData.validStage(tooLong)
                && !NeoForgePlayerStagesSavedData.validStage(null), "Stage IDs must enforce the full 128-character storage boundary");
        String maximumRule = "pack:" + "a".repeat(500) + "=pack:a";
        check(maximumRule.length() == 512 && NeoForgeResourceStages.validRule(maximumRule),
                "The original Forge contract permits a complete rule at exactly 512 characters");
        for (String rule : List.of("minecraft:water=pack:water", "mekanism:*=pack:gas", " water = water "))
            check(NeoForgeResourceStages.validRule(rule), "A Forge-compatible exact or namespace rule was rejected: " + rule);
        for (String rule : List.of("", "=", "minecraft:water", "minecraft:water=", "*=pack:a",
                "minecraft:wa*ter=pack:a", "minecraft:water=Invalid Stage", "minecraft:water=pack:a=pack:b",
                "minecraft:water=" + tooLong, "pack:" + "a".repeat(501) + "=pack:a"))
            check(!NeoForgeResourceStages.validRule(rule), "Invalid or oversized rule must not become a partial rule: " + rule);
        check(!NeoForgeResourceStages.validRule(null) && !NeoForgeResourceStages.validRule(1)
                && !new NeoForgePlayerStagesSavedData().grant(UUID.randomUUID(), tooLong),
                "Null, wrong-type and oversized stages cannot be stored by the trusted API");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void unsupportedRootsPreserveCompleteOpaqueSnapshots(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (int brokenKind = 0; brokenKind < 5; brokenKind++) {
            CompoundTag original = emptyRoot();
            switch (brokenKind) {
                case 0 -> original.putInt("schema", 500);
                case 1 -> original.putString("players", "wrong players type");
                case 2 -> original.putString("quarantine", "wrong quarantine type");
                case 3 -> {
                    ListTag invalid = new ListTag();
                    invalid.add(IntTag.valueOf(7));
                    original.put("blocked_owners", invalid);
                }
                default -> original.remove("schema");
            }
            original.putString("opaque_payload", "retained without reinterpretation");
            CompoundTag expected = original.copy();
            NeoForgePlayerStagesSavedData data = NeoForgePlayerStagesSavedData.load(original, server.registryAccess());
            original.putString("opaque_payload", "a caller changed the input after loading");
            UUID owner = UUID.randomUUID();
            check(!data.available() && !data.available(owner) && data.revision(owner) == -1
                    && !data.grant(owner, STAGE) && !data.revoke(owner, STAGE)
                    && data.stages(owner).isEmpty() && expected.equals(data.save(new CompoundTag(), server.registryAccess())),
                    "Unknown or damaged roots must be isolated as defensive full copies and never replaced by empty data");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void malformedOwnersStayIsolatedAndPreserveOriginalTagsAcrossReload(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID healthy = UUID.randomUUID(), badList = UUID.randomUUID(), badStage = UUID.randomUUID();
        CompoundTag root = emptyRoot();
        ListTag good = strings("pack:water");
        ListTag bad = strings("pack:base", "Invalid Stage");
        StringTag wrong = StringTag.valueOf("original wrong-type owner payload");
        root.getCompound("players").put(healthy.toString(), good.copy());
        root.getCompound("players").put(badList.toString(), wrong);
        root.getCompound("players").put(badStage.toString(), bad.copy());
        root.getCompound("players").put("invalid-owner", good.copy());
        NeoForgePlayerStagesSavedData data = NeoForgePlayerStagesSavedData.load(root, server.registryAccess());
        check(data.available() && data.available(healthy) && data.has(healthy, STAGE)
                && !data.available(badList) && !data.available(badStage)
                && data.revision(badStage) == -1 && !data.has(badStage, BASE) && !data.grant(badList, STAGE),
                "A malformed player must stay fully locked while healthy players retain their personal stages");
        check(data.grant(healthy, EXTRA), "Healthy players must remain writable after another player's corruption");
        CompoundTag saved = data.save(new CompoundTag(), server.registryAccess());
        CompoundTag raw = saved.getCompound("quarantine");
        check(wrong.equals(raw.get("players/" + badList)) && bad.equals(raw.get("players/" + badStage))
                && good.equals(raw.get("players/invalid-owner")), "Quarantine must retain complete original owner Tag values and types");
        NeoForgePlayerStagesSavedData second = NeoForgePlayerStagesSavedData.load(saved, server.registryAccess());
        check(second.has(healthy, STAGE) && second.has(healthy, EXTRA) && !second.available(badList)
                && !second.available(badStage) && raw.equals(second.save(new CompoundTag(), server.registryAccess()).getCompound("quarantine")),
                "A further reload must preserve both healthy new stages and persistent corrupt-owner isolation");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void nativeConfigurationKeepsForgePathsEmptyDefaultsAndImmutableSnapshots(GameTestHelper helper) {
        try (RuleConfig previous = new RuleConfig()) {
            List<ModConfigSpec.ConfigValue<List<? extends String>>> values = List.of(
                    NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS, NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS,
                    NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS, NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS);
            List<String> paths = List.of("item_supply_requirements", "fluid_requirements", "gas_requirements", "resource_requirements");
            for (int index = 0; index < values.size(); index++) {
                check(values.get(index).getDefault().isEmpty()
                        && values.get(index).getPath().equals(List.of("resource_stages", paths.get(index))),
                        "Each target setting must preserve the Forge key and unrestricted empty default");
                values.get(index).set(List.of());
            }
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=pack:water"));
            List<? extends String> snapshot = NeoForgeServerConfig.mediaStageRequirements(false);
            boolean immutable = false;
            try { snapshot.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of());
            check(immutable && snapshot.equals(List.of("minecraft:water=pack:water"))
                    && NeoForgeServerConfig.mediaStageRequirements(false).isEmpty()
                    && NeoForgeServerConfig.mediaStageRequirements(true).isEmpty()
                    && NeoForgeServerConfig.itemStageRequirements().isEmpty()
                    && NeoForgeServerConfig.resourceStageRequirements().isEmpty(),
                    "Configuration accessors must return isolated snapshots without mixing the four channels");
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void allMatchingRulesCombineForEveryChannelAndRefreshImmediately(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        NeoForgePlayerStagesSavedData data = NeoForgePlayerStagesSavedData.get(server);
        try (RuleConfig previous = new RuleConfig()) {
            List<String> rules = List.of("minecraft:*=pack:base", "minecraft:water=pack:water", "minecraft:water=pack:extra");
            NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(rules);
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(rules);
            NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.set(rules);
            NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS.set(rules);
            check(!allChannelsAllow(server, owner), "Matching wildcard and exact requirements start locked for an offline owner");
            data.grant(owner, STAGE);
            data.grant(owner, BASE);
            check(!allChannelsAllow(server, owner), "Every additional stage on the same ID must also be held");
            data.grant(owner, EXTRA);
            check(allChannelsAllow(server, owner) && !allChannelsAllow(server, other)
                    && NeoForgeResourceStages.allowsMedia(server, other, false, ResourceLocation.parse("other:water")),
                    "The three required stages unlock every configured channel only for their actual UUID");
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of());
            check(NeoForgeResourceStages.allowsMedia(server, other, false, WATER)
                    && !NeoForgeResourceStages.allowsMedia(server, other, true, WATER)
                    && !NeoForgeResourceStages.allowsItem(server, other, WATER)
                    && !NeoForgeResourceStages.allowsResource(server, other, WATER),
                    "Updating one rule list must invalidate only that channel's parsed rules immediately");
            data.revoke(owner, BASE);
            check(!NeoForgeResourceStages.allowsItem(server, owner, WATER)
                    && NeoForgeResourceStages.allowsMedia(server, owner, false, WATER),
                    "Revoking a wildcard stage immediately closes remaining matching paths without changing removed rules");
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void damagedOwnerOnlyBlocksMatchingRulesWhileUnrestrictedChannelsRemainAvailable(GameTestHelper helper)
            throws ReflectiveOperationException {
        MinecraftServer server = helper.getLevel().getServer();
        UUID broken = UUID.randomUUID(), healthy = UUID.randomUUID();
        NeoForgePlayerStagesSavedData data = NeoForgePlayerStagesSavedData.get(server);
        var field = NeoForgePlayerStagesSavedData.class.getDeclaredField("blockedOwners");
        field.setAccessible(true);
        Set<UUID> blocked = (Set<UUID>) field.get(data);
        try (RuleConfig previous = new RuleConfig()) {
            blocked.add(broken);
            NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(List.of());
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(List.of("minecraft:water=pack:water"));
            NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.set(List.of());
            NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS.set(List.of());
            data.grant(healthy, STAGE);
            check(!NeoForgeResourceStages.allowsMedia(server, broken, false, WATER)
                    && NeoForgeResourceStages.allowsMedia(server, healthy, false, WATER)
                    && NeoForgeResourceStages.allowsItem(server, broken, WATER)
                    && NeoForgeResourceStages.allowsMedia(server, broken, true, WATER)
                    && NeoForgeResourceStages.allowsResource(server, broken, WATER),
                    "Corruption cannot grant configured stages, globally block healthy owners or restrict default empty channels");
        } finally { blocked.remove(broken); }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void foreignThreadCannotResolveLedgerOrAuthorizeRules(GameTestHelper helper) throws InterruptedException {
        MinecraftServer server = helper.getLevel().getServer();
        AtomicBoolean denied = new AtomicBoolean();
        Thread foreign = new Thread(() -> {
            try { NeoForgePlayerStagesSavedData.get(server); }
            catch (IllegalStateException expected) {
                denied.set(!NeoForgeResourceStages.allowsMedia(server, UUID.randomUUID(), false, WATER));
            }
        });
        foreign.start();
        foreign.join();
        check(denied.get() && !NeoForgeResourceStages.allowsItem(null, UUID.randomUUID(), WATER),
                "A foreign thread or missing server must fail before resolving persistent personal stages");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void realAdministratorCommandsRequireLevelTwoAndReportActualMultiTargetChanges(GameTestHelper helper)
            throws CommandSyntaxException {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer first = login(server, new GameProfile(UUID.randomUUID(), "stage-first"));
        ServerPlayer second = null;
        try {
            second = login(server, new GameProfile(UUID.randomUUID(), "stage-second"));
            var dispatcher = server.getCommands().getDispatcher();
            var source = server.createCommandSourceStack().withSuppressedOutput();
            // 精确双人选择器通过测试专用 tag，不影响同批其它测试登录者。
            first.addTag("primordial_stage_command_test");
            second.addTag("primordial_stage_command_test");
            String selection = " @a[tag=primordial_stage_command_test]";
            for (String command : List.of("grant" + selection + " pack:water", "revoke" + selection + " pack:water",
                    "check" + selection + " pack:water", "list" + selection)) {
                boolean denied = false;
                try { dispatcher.execute("primordial stage " + command, source.withPermission(1)); }
                catch (CommandSyntaxException expected) { denied = true; }
                check(denied, "Permission one must not grant, revoke, inspect or list administrator stages");
            }
            check(dispatcher.execute("primordial stage grant" + selection + " pack:water", source.withPermission(2)) == 2
                    && dispatcher.execute("primordial stage grant" + selection + " pack:water", source.withPermission(2)) == 0
                    && dispatcher.execute("primordial stage check" + selection + " pack:water", source.withPermission(2)) == 2
                    && dispatcher.execute("primordial stage list" + selection, source.withPermission(2)) == 2
                    && dispatcher.execute("primordial stage revoke" + selection + " pack:water", source.withPermission(2)) == 2
                    && dispatcher.execute("primordial stage revoke" + selection + " pack:water", source.withPermission(2)) == 0
                    && dispatcher.execute("primordial stage check" + selection + " pack:water", source.withPermission(2)) == 0,
                    "The real merged command tree must return actual multi-target counts for all four operations");
            check(dispatcher.execute("primordial stage grant " + first.getGameProfile().getName()
                    + " pack:" + "a".repeat(124), source.withPermission(2)) == 0,
                    "An authorized command cannot store a stage beyond the full 128-character limit");
            check(server.getCommands().getDispatcher().getRoot().getChild("primordial").getChild("knowledge") != null,
                    "Independent event registration must retain the existing knowledge command subtree");
        } finally {
            logout(server, second);
            logout(server, first);
        }
        helper.succeed();
    }

    private static boolean allChannelsAllow(MinecraftServer server, UUID owner) {
        return NeoForgeResourceStages.allowsItem(server, owner, WATER)
                && NeoForgeResourceStages.allowsMedia(server, owner, false, WATER)
                && NeoForgeResourceStages.allowsMedia(server, owner, true, WATER)
                && NeoForgeResourceStages.allowsResource(server, owner, WATER);
    }

    private static CompoundTag emptyRoot() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.put("players", new CompoundTag());
        return tag;
    }

    private static ListTag strings(String... values) {
        ListTag list = new ListTag();
        for (String value : values) list.add(StringTag.valueOf(value));
        return list;
    }

    private record RuleConfig(List<? extends String> items, List<? extends String> fluids,
                              List<? extends String> chemicals, List<? extends String> resources) implements AutoCloseable {
        RuleConfig() {
            this(List.copyOf(NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.get()),
                    List.copyOf(NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.get()),
                    List.copyOf(NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.get()),
                    List.copyOf(NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS.get()));
        }

        @Override public void close() {
            NeoForgeServerConfig.ITEM_SUPPLY_STAGE_REQUIREMENTS.set(items);
            NeoForgeServerConfig.FLUID_STAGE_REQUIREMENTS.set(fluids);
            NeoForgeServerConfig.GAS_STAGE_REQUIREMENTS.set(chemicals);
            NeoForgeServerConfig.RESOURCE_STAGE_REQUIREMENTS.set(resources);
        }
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
