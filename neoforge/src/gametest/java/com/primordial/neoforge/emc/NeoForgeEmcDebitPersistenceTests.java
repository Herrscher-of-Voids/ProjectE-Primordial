/**
 * 【文件职责】在三个独立 JVM 中验证离线购买借项、精确退款及登录确认后的不重放行为。
 * 【变更记录 #1】2026-10-23 11:43:49（第 1 次备注 · 2.0.0-neo-dev 消费持久化验收）
 * - 删除：无。
 * - 新增：显式 write/read/verify 阶段生成器、固定隔离测试账户和真实 ProjectE 登录/退出。
 * - 修改：默认 off 不注册空测试；各阶段依赖上一个进程正常停服保存的世界账本和玩家附件。
 * - 用途：区分同进程 NBT 往返与跨进程恢复，不触碰正式玩家或 Forge 世界，不模拟异常断电。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.service.EmcAmount;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEmcDebitPersistenceTests {
    private static final UUID OWNER = UUID.fromString("b16c1121-7bbf-4b6c-bfc7-738a1aef0d22");

    private NeoForgeEmcDebitPersistenceTests() {}

    @GameTestGenerator
    public static List<TestFunction> controlledRestartTests() {
        String phase = System.getProperty("primordial.test.transactionPersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        return List.of(new TestFunction("emc_transaction_persistence", "emc_transaction_persistence." + phase,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                helper -> controlledRestart(helper, phase)));
    }

    private static void controlledRestart(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
        GameProfile profile = new GameProfile(OWNER, "emc-debit-test");
        ServerPlayer player = null;
        try {
            check(server.getPlayerList().getPlayer(OWNER) == null, "The controlled-restart owner must begin offline");
            switch (phase) {
                case "write" -> {
                    check(data.pending(OWNER).isZero() && data.debit(OWNER).signum() == 0 && data.canCredit(OWNER),
                            "Write requires a clean fixed-owner ledger in an isolated restart-test world");
                    player = login(server, profile);
                    knowledge(player).setEmc(BigInteger.TEN);
                    logout(server, player);
                    player = null;
                    NeoForgeEmcCredits.credit(server, OWNER, fraction(1, 2));
                    check(NeoForgeEmcTransactions.purchase(server, OWNER, fraction(2, 3), 9, units -> 4) == 4,
                            "The offline write phase must purchase only four actual units");
                    check(data.debit(OWNER).equals(BigInteger.valueOf(6))
                            && data.pending(OWNER).equals(fraction(23, 6)),
                            "Normal shutdown must save six EMC of debt and twenty-three sixths of credit");
                    check(NeoForgeEmcTransactions.affordable(server, OWNER, fraction(1, 6), 100) == 47,
                            "The write phase must leave forty-seven sixths of total spendable EMC");
                }
                case "read" -> {
                    check(data.debit(OWNER).equals(BigInteger.valueOf(6))
                            && data.pending(OWNER).equals(fraction(23, 6)),
                            "The second JVM must restore both exact credit and integer debt from disk");
                    check(NeoForgeEmcTransactions.affordable(server, OWNER, fraction(1, 6), 100) == 47,
                            "Restored offline ProjectE balance plus the world ledger must remain exact");
                    player = login(server, profile);
                    assertPaidOnce(data, player);
                    NeoForgeEmcCredits.flush(player);
                    NeoForgeEmcCredits.flush(player);
                    assertPaidOnce(data, player);
                }
                case "verify" -> {
                    check(data.debit(OWNER).signum() == 0 && data.pending(OWNER).equals(fraction(5, 6)),
                            "The third JVM must restore only the unspent five-sixths remainder");
                    player = login(server, profile);
                    assertPaidOnce(data, player);
                    check(NeoForgeEmcTransactions.purchase(server, OWNER, fraction(5, 6), 1, units -> units) == 1,
                            "The saved fraction must remain spendable after two independent normal shutdowns");
                    check(knowledge(player).getEmc().equals(BigInteger.valueOf(7))
                            && data.pending(OWNER).isZero() && data.debit(OWNER).signum() == 0,
                            "Spending exactly the saved fraction must leave seven EMC with no residual ledger");
                }
                default -> throw new GameTestAssertException("Unknown transaction persistence phase: " + phase);
            }
        } finally {
            logout(server, player);
        }
        LogUtils.getLogger().info("[Primordial GameTest] EMC transaction controlled restart PASS phase={}", phase);
        helper.succeed();
    }

    private static void assertPaidOnce(NeoForgeEmcSavedData data, ServerPlayer player) {
        check(knowledge(player).getEmc().equals(BigInteger.valueOf(7))
                && data.pending(OWNER).equals(fraction(5, 6)) && data.debit(OWNER).signum() == 0,
                "Login must apply net debt exactly once and retain five sixths EMC");
    }

    /** 正常玩家文件负责 ProjectE 附件恢复，世界 SavedData 负责未提交借项；不手工写这两种存档。 */
    private static ServerPlayer login(MinecraftServer server, GameProfile profile) {
        CommonListenerCookie cookie = new CommonListenerCookie(profile, 0, ClientInformation.createDefault(),
                false, ConnectionType.NEOFORGE);
        ServerPlayer player = server.getPlayerList().getPlayerForLogin(profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        try {
            NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            return player;
        } catch (RuntimeException | Error failure) {
            try { logout(server, player); }
            finally { channel.finishAndReleaseAll(); }
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
        IKnowledgeProvider result = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(result != null, "The test player must expose the actual ProjectE knowledge capability");
        return result;
    }

    private static EmcAmount fraction(long numerator, long denominator) {
        return new EmcAmount(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
