/**
 * 【文件职责】以真实 PlayerList、ProjectE 知识能力和玩家附件验收在线与离线收益交接。
 * 【变更记录 #1】2026-10-22 21:14:33（第 1 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：在线分数合并、大整数附件保存加载、离线收益登录合并且重登不重放、跨线程拒收测试。
 * - 修改：所有收益经生产 credit/flush 入口，账户来自真实 ProjectE 能力，不使用账户替身。
 * - 用途：验证正常玩家保存与登录边界；finally 移除测试玩家并释放连接，不宣称跨文件崩溃原子性。
 *
 * 【变更记录 #2】2026-10-22 21:17:06（第 2 次备注 · 2.0.0-neo-dev 正常停服重启验收）
 * - 删除：无。
 * - 新增：测试专用 write/read/verify 三阶段入口与固定测试 UUID，可跨独立 JVM 验证保存后的收益和余额。
 * - 修改：仅显式传入测试 system property 时运行持久化阶段；常规测试不改固定账户或主动关闭服务器。
 * - 用途：同一隔离测试世界连续正常停服后复用，验证离线账本恢复及已付款跨重启不重放，非异常断电测试。
 *
 * 【变更记录 #3】2026-10-22 21:18:27（第 3 次备注 · 2.0.0-neo-dev 分阶段重启测试注册）
 * - 删除：关闭持久化测试时仍计为通过的空操作 GameTest。
 * - 新增：测试生成器按阶段注册单项测试、阶段通过日志及恢复日志磁盘原始 StringTag 验证。
 * - 修改：测试属性统一为 primordial.test.creditPersistencePhase；off 返回空列表，不影响常规测试数量。
 * - 用途：使三次独立进程的真实保存结果可从测试报告和磁盘数据核对，不把同进程 NBT 往返当重启。
 *
 * 【变更记录 #4】2026-10-22 21:21:01（第 4 次备注 · 2.0.0-neo-dev 测试连接协商修复）
 * - 删除：未协商模组通信的原版 makeMockServerPlayerInLevel 路径。
 * - 新增：NeoForge 官方 configureMockConnection 测试初始化与 NEOFORGE 登录 cookie。
 * - 修改：创建真实玩家前声明测试客户端支持全部已注册负载，保留数据包同步、登录事件和 ProjectE 原生能力。
 * - 用途：避免 ProjectE 登录前同步被未协商的测试连接拒绝；不跳过网络校验或替换生产账户实现。
 */
package com.primordial.neoforge.emc;

import java.math.BigInteger;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.service.EmcAmount;

import io.netty.channel.embedded.EmbeddedChannel;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEmcOnlineGameTests {
    private static final UUID PERSISTENCE_OWNER = UUID.fromString("ca9a7a31-8c11-4718-9872-44ae4d08ef21");
    private static final BigInteger PERSISTENCE_INITIAL = BigInteger.TEN.pow(100).add(BigInteger.valueOf(73));

    private NeoForgeEmcOnlineGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void mergesExactFractionsAndPreservesRealProjectEAttachment(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, new GameProfile(UUID.randomUUID(), "emc-test-player"));
        try {
            check(server.getPlayerList().getPlayer(player.getUUID()) == player,
                    "The test player must be registered in the actual online PlayerList");
            BigInteger initial = BigInteger.ONE.shiftLeft(256).add(BigInteger.valueOf(17));
            knowledge(player).setEmc(initial);
            NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);

            NeoForgeEmcCredits.credit(server, player.getUUID(), fraction(1, 3));
            check(NeoForgeEmcCredits.onlineBalance(player).equals(initial),
                    "One third must remain queued without rounding the ProjectE balance");
            check(data.pending(player.getUUID()).equals(fraction(1, 3)), "The exact third must remain queued");
            NeoForgeEmcCredits.credit(server, player.getUUID(), fraction(2, 3));
            check(NeoForgeEmcCredits.onlineBalance(player).equals(initial.add(BigInteger.ONE))
                    && data.pending(player.getUUID()).isZero(), "Three thirds must credit exactly one EMC");

            NeoForgeEmcCredits.credit(server, player.getUUID(), fraction(11, 7));
            check(knowledge(player).getEmc().equals(initial.add(BigInteger.TWO))
                    && data.pending(player.getUUID()).equals(fraction(4, 7)),
                    "The whole part must enter the real account while four sevenths remain exact");
            NeoForgeEmcCredits.credit(server, player.getUUID(), fraction(3, 7));
            BigInteger expected = initial.add(BigInteger.valueOf(3));
            check(knowledge(player).getEmc().equals(expected) && data.pending(player.getUUID()).isZero(),
                    "The final sevenths must complete exactly one additional EMC");

            CompoundTag saved = player.saveWithoutId(new CompoundTag());
            check(saved.getCompound("neoforge:attachments").contains("projecte:knowledge"),
                    "Normal player NBT must contain ProjectE's real knowledge attachment");
            knowledge(player).setEmc(BigInteger.ZERO);
            player.load(saved);
            check(knowledge(player).getEmc().equals(expected)
                    && NeoForgeEmcCredits.onlineBalance(player).equals(expected),
                    "Normal player attachment save/load must preserve the complete BigInteger balance");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void creditsOfflineOwnerOnActualLoginWithoutReplayingOnSecondLogin(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer original = login(server, new GameProfile(UUID.randomUUID(), "emc-test-player"));
        ServerPlayer current = null;
        try {
            UUID owner = original.getUUID();
            GameProfile profile = original.getGameProfile();
            BigInteger initial = BigInteger.TEN.pow(80).add(BigInteger.valueOf(31));
            knowledge(original).setEmc(initial);
            logout(server, original);
            check(server.getPlayerList().getPlayer(owner) == null, "Logged-out UUID must be truly offline");

            NeoForgeEmcCredits.credit(server, owner, fraction(5, 2));
            NeoForgeEmcCredits.credit(server, owner, fraction(7, 3));
            NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
            check(data.pending(owner).equals(fraction(29, 6)),
                    "Offline credits must remain in the world ledger with their complete fraction");
            check(knowledge(original).getEmc().equals(initial),
                    "An offline credit must not mutate a stale player or immutable offline proxy");

            current = login(server, profile);
            BigInteger firstBalance = initial.add(BigInteger.valueOf(4));
            check(server.getPlayerList().getPlayer(owner) == current
                    && knowledge(current).getEmc().equals(firstBalance),
                    "The actual login event must restore saved ProjectE EMC and commit the four queued whole EMC");
            check(data.pending(owner).equals(fraction(5, 6)), "Login must retain the exact five-sixths remainder");
            NeoForgeEmcCredits.flush(current);
            NeoForgeEmcCredits.flush(current);
            check(knowledge(current).getEmc().equals(firstBalance), "Repeated flushes must not replay paid credit");

            logout(server, current);
            current = null;
            current = login(server, profile);
            check(knowledge(current).getEmc().equals(firstBalance) && data.pending(owner).equals(fraction(5, 6)),
                    "A second real disk-backed login must preserve the paid balance without replaying the queue");
            NeoForgeEmcCredits.credit(server, owner, fraction(1, 6));
            check(knowledge(current).getEmc().equals(initial.add(BigInteger.valueOf(5)))
                    && data.pending(owner).isZero(), "The remaining sixth must complete exactly one EMC after relogin");
        } finally {
            logout(server, current);
            logout(server, original);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void rejectsCrossThreadCreditBeforeMutatingLedgerOrRealAccount(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = login(server, new GameProfile(UUID.randomUUID(), "emc-test-player"));
        try {
            BigInteger initial = BigInteger.ONE.shiftLeft(128);
            knowledge(player).setEmc(initial);
            NeoForgeEmcCredits.credit(server, player.getUUID(), fraction(1, 7));
            EmcAmount before = NeoForgeEmcSavedData.get(server).pending(player.getUUID());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    NeoForgeEmcCredits.credit(server, player.getUUID(), EmcAmount.ONE);
                } catch (Throwable thrown) {
                    failure.set(thrown);
                }
            }, "primordial-gametest-invalid-credit-thread");
            worker.setDaemon(true);
            worker.start();
            try {
                worker.join(5000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new GameTestAssertException("Interrupted while checking thread rejection");
            }
            check(!worker.isAlive(), "Invalid cross-thread credit must reject without waiting for the server thread");
            check(failure.get() instanceof IllegalStateException, "Cross-thread credit must reject explicitly");
            check(NeoForgeEmcSavedData.get(server).pending(player.getUUID()).equals(before)
                    && knowledge(player).getEmc().equals(initial),
                    "Thread rejection must happen before either the ledger or the real ProjectE account changes");
        } finally {
            logout(server, player);
        }
        helper.succeed();
    }

    @GameTestGenerator
    public static List<TestFunction> controlledRestartTests() {
        String phase = System.getProperty("primordial.test.creditPersistencePhase", "off");
        if (phase.equals("off")) return List.of();
        return List.of(new TestFunction("emc_credit_persistence", "emc_credit_persistence." + phase,
                PrimordialNeoForgeMod.MOD_ID + ":capability_proxy_empty", 100, 0L, true,
                helper -> controlledRestart(helper, phase)));
    }

    /** 外部以同一隔离世界依次启动 write、read、verify 三个进程；各进程由正常 GameTest 停服保存。 */
    private static void controlledRestart(GameTestHelper helper, String phase) {
        MinecraftServer server = helper.getLevel().getServer();
        NeoForgeEmcSavedData data = NeoForgeEmcSavedData.get(server);
        GameProfile profile = new GameProfile(PERSISTENCE_OWNER, "emc-restart-test");
        ServerPlayer player = null;
        try {
            check(server.getPlayerList().getPlayer(PERSISTENCE_OWNER) == null,
                    "The fixed restart-test owner must start offline");
            switch (phase) {
                case "write" -> {
                    check(data.pending(PERSISTENCE_OWNER).isZero(),
                            "Write phase requires a clean fixed test-owner ledger; use an isolated restart-test world");
                    player = login(server, profile);
                    knowledge(player).setEmc(PERSISTENCE_INITIAL);
                    logout(server, player);
                    player = null;
                    NeoForgeEmcCredits.credit(server, PERSISTENCE_OWNER, fraction(5, 2));
                    NeoForgeEmcCredits.credit(server, PERSISTENCE_OWNER, fraction(7, 3));
                    check(data.pending(PERSISTENCE_OWNER).equals(fraction(29, 6)),
                            "Write phase must leave the complete offline credit for normal shutdown to save");
                }
                case "read" -> {
                    assertRecoveryLogOnDisk(server);
                    check(data.pending(PERSISTENCE_OWNER).equals(fraction(29, 6)),
                            "The second JVM must recover the exact offline ledger saved by the first JVM");
                    player = login(server, profile);
                    BigInteger expected = PERSISTENCE_INITIAL.add(BigInteger.valueOf(4));
                    check(knowledge(player).getEmc().equals(expected)
                            && data.pending(PERSISTENCE_OWNER).equals(fraction(5, 6)),
                            "Login after restart must restore the huge ProjectE balance and commit only four whole EMC");
                    NeoForgeEmcCredits.flush(player);
                    NeoForgeEmcCredits.flush(player);
                    check(knowledge(player).getEmc().equals(expected),
                            "Read phase must not replay the four whole EMC before its own normal shutdown");
                }
                case "verify" -> {
                    check(data.pending(PERSISTENCE_OWNER).equals(fraction(5, 6)),
                            "The third JVM must recover only the uncommitted five-sixths remainder");
                    player = login(server, profile);
                    check(knowledge(player).getEmc().equals(PERSISTENCE_INITIAL.add(BigInteger.valueOf(4))),
                            "The paid balance must survive another restart without receiving four EMC again");
                    NeoForgeEmcCredits.credit(server, PERSISTENCE_OWNER, fraction(1, 6));
                    NeoForgeEmcCredits.flush(player);
                    check(knowledge(player).getEmc().equals(PERSISTENCE_INITIAL.add(BigInteger.valueOf(5)))
                            && data.pending(PERSISTENCE_OWNER).isZero(),
                            "The persisted fraction must complete exactly one final EMC and clear the queue");
                }
                default -> throw new GameTestAssertException("Unknown test credit persistence phase: " + phase);
            }
        } finally {
            logout(server, player);
        }
        LogUtils.getLogger().info("[Primordial GameTest] EMC controlled restart PASS phase={}", phase);
        helper.succeed();
    }

    /** 读取上个 JVM 正常停服写出的文件，不借助当前进程中可能已追加新记录的 SavedData 缓存。 */
    private static void assertRecoveryLogOnDisk(MinecraftServer server) {
        try {
            CompoundTag disk = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/primordial_emc_recovery.dat"), NbtAccounter.unlimitedHeap());
            ListTag blocks = disk.getCompound("data").getList("blocks", Tag.TAG_COMPOUND);
            StringTag expected = StringTag.valueOf("preserve this malformed pending-credit payload");
            boolean found = false;
            for (int i = 0; i < blocks.size(); i++) {
                if (expected.equals(blocks.getCompound(i).get("queued_credit"))) {
                    found = true;
                    break;
                }
            }
            check(found, "The previous JVM must preserve the malformed block's original StringTag in the recovery file");
        } catch (IOException failure) {
            throw new GameTestAssertException("Cannot read the previous JVM's EMC recovery file: " + failure.getMessage());
        }
    }

    /** 使用与 GameTestHelper 相同的真实登录路径；同 UUID 新对象从玩家文件恢复附件并触发生产登录事件。 */
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
            try {
                logout(server, player);
            } finally {
                channel.finishAndReleaseAll();
            }
            throw failure;
        }
    }

    /** 先正常退出保存附件，再关闭测试内存连接；不调用第二次注销回调，以免重复保存旧玩家覆盖新附件。 */
    private static void logout(MinecraftServer server, ServerPlayer player) {
        if (player == null) return;
        try {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                server.getPlayerList().remove(player);
            }
        } finally {
            if (player.connection != null && player.connection.getConnection().channel() instanceof EmbeddedChannel channel) {
                channel.finishAndReleaseAll();
            }
        }
    }

    private static IKnowledgeProvider knowledge(ServerPlayer player) {
        IKnowledgeProvider knowledge = player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY);
        check(knowledge != null, "The installed ProjectE mod must provide the real player knowledge capability");
        return knowledge;
    }

    private static EmcAmount fraction(long numerator, long denominator) {
        return new EmcAmount(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
