/**
 * 【文件职责】用独立 write/read/verify 服务端进程的正常保存，验收个人阶段的离线持久化和撤销不重放。
 * 【变更记录 #1】2026-10-23 16:22:45（第 1 次备注 · 2.0.0-neoforge-stages）
 * - 删除：无。
 * - 新增：只由显式系统属性启用的单项阶段夹具，固定隔离账户依次保存 A+B、撤销 A 新增 C、最终 B+C。
 * - 修改：使用真实主世界 SavedData 与正常 PlayerList 登录退出；余额和知识取原生 ProjectE 附件。
 * - 用途：默认不生成测试；验证阶段独立于 EMC 和知识，非异常断电原子性或 Forge 存档导入测试。
 */
package com.primordial.neoforge.trade;

import static com.primordial.neoforge.trade.TradeTestPlayers.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.blackhole.NeoForgeRecoveryKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import com.primordial.neoforge.stage.NeoForgePlayerStagesSavedData;

import moze_intel.projecte.api.ItemInfo;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeStagePersistenceTests {
    private static final UUID OWNER = UUID.fromString("19a66db7-fd5b-42fb-b681-4001008063ae");
    private static final UUID OTHER = UUID.fromString("73c4e54b-5108-42ae-9675-4001008063ae");
    private static final ResourceLocation A = ResourceLocation.parse("primordial:stage_restart_a");
    private static final ResourceLocation B = ResourceLocation.parse("primordial:stage_restart_b");
    private static final ResourceLocation C = ResourceLocation.parse("primordial:stage_restart_c");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final BigInteger BALANCE = BigInteger.valueOf(37);

    private NeoForgeStagePersistenceTests() {}

    @GameTestGenerator
    public static List<TestFunction> controlledRestarts() {
        String phase = System.getProperty("primordial.test.stagePersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        return List.of(new TestFunction("stage_persistence", "stage_persistence." + phase,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                helper -> run(helper, phase)));
    }

    private static void run(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        var stages = NeoForgePlayerStagesSavedData.get(server);
        GameProfile profile = new GameProfile(OWNER, "stage-restart");
        ServerPlayer player = null;
        try {
            check(server.getPlayerList().getPlayer(OWNER) == null, "Each controlled JVM starts with the isolated owner offline");
            switch (phase) {
                case "write" -> {
                    player = login(server, profile);
                    knowledge(player).clearKnowledge();
                    knowledge(player).setEmc(BALANCE);
                    // 固定夹具可重跑 write；只清理本测试保留的两个账户，不修改其它玩家。
                    for (ResourceLocation previous : stages.stages(OWNER)) stages.revoke(OWNER, previous);
                    for (ResourceLocation previous : stages.stages(OTHER)) stages.revoke(OTHER, previous);
                    check(stages.grant(OWNER, A) && stages.grant(OWNER, B) && stages.grant(OTHER, C),
                            "Write phase grants exactly two owner stages and one independent observer stage");
                    assertSnapshot(stages, Set.of(A, B));
                    assertNativeUnchanged(server, player);
                    logout(server, player);
                    player = null;
                    assertSnapshot(stages, Set.of(A, B));
                }
                case "read" -> {
                    assertSnapshot(stages, Set.of(A, B));
                    player = login(server, profile);
                    assertNativeUnchanged(server, player);
                    check(stages.revoke(OWNER, A) && stages.grant(OWNER, C),
                            "Read phase must save a real removal and an independent new grant");
                    assertSnapshot(stages, Set.of(B, C));
                    assertNativeUnchanged(server, player);
                }
                case "verify" -> {
                    assertSnapshot(stages, Set.of(B, C));
                    player = login(server, profile);
                    assertNativeUnchanged(server, player);
                    long revision = stages.revision(OWNER);
                    check(!stages.grant(OWNER, B) && !stages.grant(OWNER, C) && !stages.revoke(OWNER, A)
                            && stages.revision(OWNER) == revision,
                            "Saved revocation is not replayed and repeated final operations do not create new mutations");
                    assertSnapshot(stages, Set.of(B, C));
                }
                default -> throw new IllegalArgumentException("Unknown stage persistence phase: " + phase);
            }
        } finally { logout(server, player); }
        LogUtils.getLogger().info("[Primordial] Personal stage persistence phase {} passed", phase);
        helper.succeed();
    }

    private static void assertSnapshot(NeoForgePlayerStagesSavedData stages, Set<ResourceLocation> expected) {
        check(stages.available(OWNER) && stages.stages(OWNER).equals(expected),
                "Exactly the intended owner's personal stage set must survive normal process restart");
        check(stages.available(OTHER) && stages.stages(OTHER).equals(Set.of(C)),
                "Owner stage mutations must preserve the independent observer's saved stage");
    }

    private static void assertNativeUnchanged(MinecraftServer server, ServerPlayer player) {
        check(knowledge(player).getEmc().equals(BALANCE)
                && !knowledge(player).hasKnowledge(ItemInfo.fromItem(Items.DIAMOND))
                && !knowledge(player).hasKnowledge(Kind.ENERGY.sample())
                && !NeoForgeRecoveryKnowledge.knowsFluid(server, OWNER, WATER),
                "Persistent stage grants and revocations must never donate native EMC, items, certificates or medium knowledge");
    }
}
