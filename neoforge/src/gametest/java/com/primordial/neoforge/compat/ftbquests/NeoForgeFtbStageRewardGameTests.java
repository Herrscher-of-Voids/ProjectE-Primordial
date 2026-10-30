/**
 * 【文件职责】在真实 FTB Quests 2101.1.37 服务端验证奖励注册、NBT、网络和个人领奖。
 * 【变更记录 #1】2026-10-23 16:22:24（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无；新增：共同初始化编号、原生奖励工厂、128 字符边界和 TeamData 领奖回归。
 * - 修改：仅可选生成器在 FTB 存在后反射加载；临时任务不挂入任务文件，不改用户任务。
 * - 用途：证明实际领取者、重复领取和错误线程不跨玩家授予阶段；通知使用目标版允许的字符串参数。
 */
package com.primordial.neoforge.compat.ftbquests;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;
import dev.ftb.mods.ftbquests.quest.Chapter;
import dev.ftb.mods.ftbquests.quest.Quest;
import dev.ftb.mods.ftbquests.quest.ServerQuestFile;
import dev.ftb.mods.ftbquests.quest.TeamData;
import dev.ftb.mods.ftbquests.quest.reward.RewardType;
import dev.ftb.mods.ftbquests.quest.reward.RewardTypes;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;

public final class NeoForgeFtbStageRewardGameTests {
    private NeoForgeFtbStageRewardGameTests() {}

    public static List<TestFunction> tests() {
        return List.of(test("ftb_stage_reward_native_registration", NeoForgeFtbStageRewardGameTests::registration),
                test("ftb_stage_reward_nbt_and_registry_network", NeoForgeFtbStageRewardGameTests::serialization),
                test("ftb_stage_reward_native_personal_claims", NeoForgeFtbStageRewardGameTests::personalClaims),
                test("ftb_stage_reward_boolean_claim_contract", NeoForgeFtbStageRewardGameTests::booleanClaims),
                test("ftb_stage_reward_invalid_and_wrong_thread", NeoForgeFtbStageRewardGameTests::invalidClaims));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> body) {
        return new TestFunction("defaultBatch", name, "primordial:capability_proxy_empty",
                Rotation.NONE, 100, 0, true, body);
    }

    private static void registration(GameTestHelper helper) {
        RewardType fluid = NeoForgeFtbQuestRewards.FLUID_STAGE;
        RewardType gas = NeoForgeFtbQuestRewards.GAS_STAGE;
        check(fluid != null && gas != null && fluid != gas, "Both native FTB stage reward types must exist after common setup");
        check(fluid.internalId > 0 && gas.internalId > 0 && fluid.internalId != gas.internalId,
                "Both custom types must have been registered before ServerQuestFile assigned network IDs");
        int count = RewardTypes.TYPES.size();
        NeoForgeFtbQuestRewards.register();
        check(RewardTypes.TYPES.size() == count && fluid == NeoForgeFtbQuestRewards.FLUID_STAGE
                        && gas == NeoForgeFtbQuestRewards.GAS_STAGE,
                "Repeated registration must preserve every built-in and custom reward object");
        Quest quest = quest(helper);
        var fluidReward = RewardType.requireCreateReward(1_200_101L, quest, "primordial:fluid_stage");
        var gasReward = RewardType.requireCreateReward(1_200_102L, quest, "primordial:gas_stage");
        check(fluidReward instanceof NeoForgeStageReward.Fluid && gasReward instanceof NeoForgeStageReward.Gas
                        && fluidReward.getType() == fluid && gasReward.getType() == gas,
                "Real FTB factories must create distinct correct types, including their parent constructor");
        check(RewardTypes.TYPES.get(ResourceLocation.fromNamespaceAndPath("ftbquests", "item")) == RewardTypes.ITEM,
                "Custom registration must retain the native item reward");
        helper.succeed();
    }

    private static void serialization(GameTestHelper helper) {
        Quest quest = quest(helper);
        for (RewardType type : List.of(NeoForgeFtbQuestRewards.FLUID_STAGE, NeoForgeFtbQuestRewards.GAS_STAGE)) {
            String boundary = "pack:" + "a".repeat(123);
            NeoForgeStageReward original = reward(type, quest, boundary, helper);
            CompoundTag saved = new CompoundTag();
            original.writeData(saved, helper.getLevel().registryAccess());
            check(boundary.length() == 128 && saved.getString("stage").equals(boundary),
                    "NBT must preserve an explicit namespaced stage at the maximum valid length");
            NeoForgeStageReward loaded = reward(type, quest, "pack:replaced", helper);
            loaded.readData(saved, helper.getLevel().registryAccess());
            RegistryFriendlyByteBuf buffer = buffer(helper);
            try {
                loaded.writeNetData(buffer);
                NeoForgeStageReward decoded = reward(type, quest, "pack:old", helper);
                decoded.readNetData(buffer);
                CompoundTag roundTrip = new CompoundTag();
                decoded.writeData(roundTrip, helper.getLevel().registryAccess());
                check(buffer.readableBytes() == 0 && roundTrip.getString("stage").equals(boundary)
                                && decoded.getType() == type,
                        "The exact target RegistryFriendlyByteBuf must round-trip all native and custom fields");
            } finally {
                buffer.release();
            }
            for (String invalid : List.of("plain", "pack:", "Pack:upper", " pack:space", "pack:" + "a".repeat(124))) {
                check(!NeoForgeStageReward.validStage(invalid), "Invalid or oversized stage IDs must be rejected");
                NeoForgeStageReward bad = reward(type, quest, invalid, helper);
                CompoundTag rejected = new CompoundTag();
                bad.writeData(rejected, helper.getLevel().registryAccess());
                check(rejected.getString("stage").isEmpty(), "NBT cannot retain an invalid grantable stage");
            }
            RegistryFriendlyByteBuf hostile = buffer(helper);
            try {
                reward(type, quest, "", helper).writeNetData(hostile);
                hostile.writerIndex(hostile.writerIndex() - 1);
                hostile.writeUtf("pack:" + "a".repeat(124), 129);
                boolean rejected = false;
                try {
                    reward(type, quest, "", helper).readNetData(hostile);
                } catch (io.netty.handler.codec.DecoderException expected) {
                    rejected = true;
                }
                check(rejected, "An overlong incoming stage must fail bounded UTF decoding");
            } finally {
                hostile.release();
            }
        }
        helper.succeed();
    }

    private static void personalClaims(GameTestHelper helper) {
        var data = NeoForgePlayerStagesSavedData.get(helper.getLevel().getServer());
        RecordingPlayer first = new RecordingPlayer(helper, "ftb-first");
        RecordingPlayer second = new RecordingPlayer(helper, "ftb-second");
        Quest quest = quest(helper);
        TeamData team = new TeamData(UUID.randomUUID(), ServerQuestFile.INSTANCE);
        ResourceLocation stage = ResourceLocation.fromNamespaceAndPath("pack", "ftb_personal");
        NeoForgeStageReward reward = reward(NeoForgeFtbQuestRewards.FLUID_STAGE, quest, stage.toString(), helper);
        check(!reward.isTeamReward(), "The fixture must explicitly request native per-player claiming");
        long before = data.revision(first.getUUID());
        team.claimReward(first, reward, true);
        check(data.has(first.getUUID(), stage) && !data.has(second.getUUID(), stage)
                        && team.isRewardClaimed(first.getUUID(), reward)
                        && !team.isRewardClaimed(second.getUUID(), reward),
                "Native TeamData claiming grants only the recipient and retains personal claim tracking");
        check(data.revision(first.getUUID()) == before + 1 && first.notifications.size() == 1,
                "Only the first actual grant may change stage revision and produce a notification");
        Component message = first.notifications.getFirst();
        check(message.getContents() instanceof TranslatableContents text
                        && text.getKey().equals("reward.primordial.fluid_stage.granted")
                        && text.getArgs().length == 1 && text.getArgs()[0].equals(stage.toString()),
                "Native feedback must use a serializable stage string rather than raw ResourceLocation");
        team.claimReward(first, reward, true);
        reward.claim(first, true);
        check(data.revision(first.getUUID()) == before + 1 && first.notifications.size() == 1,
                "Repeated native claims and direct duplicate grants must be idempotent");
        data.revoke(first.getUUID(), stage);
        team.claimReward(first, reward, true);
        check(!data.has(first.getUUID(), stage), "A previously claimed FTB reward cannot silently regrant a revoked stage");
        team.claimReward(second, reward, false);
        check(data.has(second.getUUID(), stage) && second.notifications.isEmpty(),
                "A second player may claim their own reward with native notification suppression");
        helper.succeed();
    }

    private static void invalidClaims(GameTestHelper helper) {
        var data = NeoForgePlayerStagesSavedData.get(helper.getLevel().getServer());
        RecordingPlayer player = new RecordingPlayer(helper, "ftb-invalid");
        Quest quest = quest(helper);
        long before = data.revision(player.getUUID());
        check(!reward(NeoForgeFtbQuestRewards.GAS_STAGE, quest, "invalid", helper).claim(player, true),
                "Invalid grants must report failure to the native FTB claim tracker");
        NeoForgeStageReward gas = reward(NeoForgeFtbQuestRewards.GAS_STAGE, quest, "pack:thread", helper);
        check(!gas.claim(null, true) && !CompletableFuture.supplyAsync(() -> gas.claim(player, true)).join(),
                "Absent recipients and wrong-thread calls must report failure rather than consume a reward");
        check(data.revision(player.getUUID()) == before && data.stages(player.getUUID()).isEmpty()
                        && player.notifications.isEmpty(),
                "Invalid data, absent recipients and non-server threads cannot mutate any stages or notify");
        check(gas.claim(player, true), "A valid main-thread grant must report actual success");
        check(data.has(player.getUUID(), ResourceLocation.parse("pack:thread"))
                        && player.notifications.size() == 1
                        && player.notifications.getFirst().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("reward.primordial.gas_stage.granted"),
                "The same valid gas reward must succeed normally on the main server thread");
        helper.succeed();
    }

    private static void booleanClaims(GameTestHelper helper) {
        var data = NeoForgePlayerStagesSavedData.get(helper.getLevel().getServer());
        RecordingPlayer player = new RecordingPlayer(helper, "ftb-boolean");
        Quest quest = quest(helper);
        TeamData team = new TeamData(UUID.randomUUID(), ServerQuestFile.INSTANCE);
        NeoForgeStageReward failed = reward(NeoForgeFtbQuestRewards.FLUID_STAGE, quest, "invalid", helper);
        team.claimReward(player, failed, true);
        check(!team.isRewardClaimed(player.getUUID(), failed) && data.stages(player.getUUID()).isEmpty(),
                "The native 1.37 TeamData path must leave a refused grant unclaimed without granting stages");
        ResourceLocation stage = ResourceLocation.parse("pack:already_owned");
        data.grant(player.getUUID(), stage);
        NeoForgeStageReward fulfilled = reward(NeoForgeFtbQuestRewards.FLUID_STAGE, quest, stage.toString(), helper);
        long revision = data.revision(player.getUUID());
        team.claimReward(player, fulfilled, true);
        check(team.isRewardClaimed(player.getUUID(), fulfilled) && data.has(player.getUUID(), stage)
                        && data.revision(player.getUUID()) == revision && player.notifications.isEmpty(),
                "A stage already granted elsewhere must fulfill and consume the personal reward without duplicate revision or notification");
        helper.succeed();
    }

    private static Quest quest(GameTestHelper helper) {
        ServerQuestFile file = ServerQuestFile.INSTANCE;
        check(file != null && file.server == helper.getLevel().getServer(), "The real FTB server quest lifecycle must be active");
        return new Quest(1_200_002L, new Chapter(1_200_001L, file, file.getDefaultChapterGroup()));
    }

    private static NeoForgeStageReward reward(RewardType type, Quest quest, String stage, GameTestHelper helper) {
        NeoForgeStageReward reward = (NeoForgeStageReward) type.createReward(1_200_100L, quest);
        CompoundTag tag = new CompoundTag();
        tag.putString("stage", stage);
        tag.putBoolean("team_reward", false);
        reward.readData(tag, helper.getLevel().registryAccess());
        return reward;
    }

    private static RegistryFriendlyByteBuf buffer(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static final class RecordingPlayer extends ServerPlayer {
        private final List<Component> notifications = new ArrayList<>();
        private RecordingPlayer(GameTestHelper helper, String name) {
            super(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name),
                    ClientInformation.createDefault());
        }
        @Override public void sendSystemMessage(Component message) { notifications.add(message); }
    }
}
