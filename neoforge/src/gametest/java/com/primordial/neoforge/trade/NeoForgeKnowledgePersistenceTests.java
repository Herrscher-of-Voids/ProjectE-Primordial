/**
 * 【文件职责】通过三个独立测试进程正常保存，验证离线凭证、逐种知识及显式遗忘不会因重启混淆。
 * 【变更记录 #1】2026-10-23 13:39:01（第 1 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：无。
 * - 新增：显式 write/read/verify 阶段和固定隔离账户；可选真实氢注册项与流体分开断言。
 * - 修改：使用真实 PlayerList 登录退出和 ProjectE 知识附件，不手写玩家余额/知识文件。
 * - 用途：默认不注册测试；只对各自 NeoForge 测试世界写入夹具，不测试异常断电原子性。
 */
package com.primordial.neoforge.trade;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.api.MediaKnowledgeApi;
import com.primordial.neoforge.api.MediaKnowledgeApi.Media;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge;
import com.primordial.neoforge.knowledge.NeoForgeResourceKnowledge.Kind;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static com.primordial.neoforge.trade.TradeTestPlayers.*;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeKnowledgePersistenceTests {
    private static final UUID OWNER = UUID.fromString("aeb3a19a-07b2-4be2-9842-b332ac0e115a");
    private static final ResourceLocation WATER = ResourceLocation.parse("minecraft:water");
    private static final ResourceLocation LAVA = ResourceLocation.parse("minecraft:lava");
    private static final ResourceLocation HYDROGEN = ResourceLocation.parse("mekanism:hydrogen");
    private NeoForgeKnowledgePersistenceTests() {}

    @GameTestGenerator
    public static List<TestFunction> controlledRestarts() {
        String phase = System.getProperty("primordial.test.knowledgePersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        return List.of(new TestFunction("knowledge_persistence", "knowledge_persistence." + phase,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                helper -> run(helper, phase)));
    }

    private static void run(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(OWNER, "knowledge-restart");
        ServerPlayer player = null;
        boolean chemicals = ModList.get().isLoaded("mekanism");
        try {
            check(server.getPlayerList().getPlayer(OWNER) == null, "Controlled owner starts offline in every JVM");
            switch (phase) {
                case "write" -> {
                    player = login(server, profile);
                    knowledge(player).addKnowledge(Kind.FLUID.sample());
                    knowledge(player).addKnowledge(Kind.ENERGY.sample());
                    knowledge(player).addKnowledge(Kind.GAS.sample());
                    check(NeoForgeResourceKnowledge.allows(server, OWNER, Kind.ENERGY), "Native energy certificate is learned");
                    MediaKnowledgeApi.learn(server, OWNER, Media.FLUID, WATER);
                    MediaKnowledgeApi.learn(server, OWNER, Media.FLUID, LAVA);
                    MediaKnowledgeApi.forget(server, OWNER, Media.FLUID, LAVA);
                    if (chemicals) {
                        MediaKnowledgeApi.learn(server, OWNER, Media.GAS, HYDROGEN);
                        MediaKnowledgeApi.forget(server, OWNER, Media.GAS, HYDROGEN);
                    }
                    assertFirstSnapshot(server, chemicals);
                    logout(server, player);
                    player = null;
                    assertFirstSnapshot(server, chemicals);
                }
                case "read" -> {
                    assertFirstSnapshot(server, chemicals);
                    player = login(server, profile);
                    assertFirstSnapshot(server, chemicals);
                    check(MediaKnowledgeApi.learn(server, OWNER, Media.FLUID, LAVA), "Regranting clears the saved lava exclusion");
                    check(MediaKnowledgeApi.forget(server, OWNER, Media.FLUID, WATER), "The read phase saves a new water exclusion");
                    if (chemicals) check(MediaKnowledgeApi.learn(server, OWNER, Media.GAS, HYDROGEN), "Regranting clears chemical exclusion independently");
                    assertSecondSnapshot(server, chemicals);
                }
                case "verify" -> {
                    assertSecondSnapshot(server, chemicals);
                    player = login(server, profile);
                    assertSecondSnapshot(server, chemicals);
                    check(!MediaKnowledgeApi.learn(server, OWNER, Media.FLUID, LAVA), "Repeated learning remains idempotent after two restarts");
                    check(!MediaKnowledgeApi.forget(server, OWNER, Media.FLUID, WATER), "Repeated author revocation remains idempotent");
                    assertSecondSnapshot(server, chemicals);
                }
                default -> throw new IllegalArgumentException("Unknown knowledge persistence phase: " + phase);
            }
        } finally {
            logout(server, player);
        }
        LogUtils.getLogger().info("[Primordial] Knowledge persistence phase {} passed (chemical registry installed: {})", phase, chemicals);
        helper.succeed();
    }

    private static void assertFirstSnapshot(MinecraftServer server, boolean chemicals) {
        check(NeoForgeResourceKnowledge.allows(server, OWNER, Kind.ENERGY), "Saved native ProjectE certificate grants FE online and offline");
        check(MediaKnowledgeApi.hasLearned(server, OWNER, Media.FLUID, WATER)
                && NeoForgeResourceKnowledge.allowsMedia(server, OWNER, Kind.FLUID, WATER), "Water remains individually learned and authorized");
        check(!MediaKnowledgeApi.hasLearned(server, OWNER, Media.FLUID, LAVA)
                && !NeoForgeResourceKnowledge.allowsMedia(server, OWNER, Kind.FLUID, LAVA), "Saved explicit lava forgetting overrides the fluid certificate");
        if (chemicals) check(!MediaKnowledgeApi.hasLearned(server, OWNER, Media.GAS, HYDROGEN)
                && !NeoForgeResourceKnowledge.allowsMedia(server, OWNER, Kind.GAS, HYDROGEN), "Saved chemical forgetting overrides the gas certificate");
        else check(!MediaKnowledgeApi.validResource(Media.GAS, HYDROGEN), "Missing optional mod never turns an ID into a valid chemical");
    }

    private static void assertSecondSnapshot(MinecraftServer server, boolean chemicals) {
        check(NeoForgeResourceKnowledge.allows(server, OWNER, Kind.ENERGY), "Energy certificate remains learned without duplicate grants");
        check(MediaKnowledgeApi.hasLearned(server, OWNER, Media.FLUID, LAVA)
                && NeoForgeResourceKnowledge.allowsMedia(server, OWNER, Kind.FLUID, LAVA), "Regrant removes exactly the lava exclusion across restart");
        check(!MediaKnowledgeApi.hasLearned(server, OWNER, Media.FLUID, WATER)
                && !NeoForgeResourceKnowledge.allowsMedia(server, OWNER, Kind.FLUID, WATER), "Water exclusion persists without revoking the category certificate");
        if (chemicals) check(MediaKnowledgeApi.hasLearned(server, OWNER, Media.GAS, HYDROGEN)
                && NeoForgeResourceKnowledge.allowsMedia(server, OWNER, Kind.GAS, HYDROGEN), "Chemical regrant persists in its independent channel");
    }
}
