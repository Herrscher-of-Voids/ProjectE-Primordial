/**
 * 【文件职责】验收黑洞待结算收益移交世界账本时的 ticker、NBT、归属切换与拆除生命周期。
 *
 * 【变更记录 #1】2026-10-22 21:13:17（第 1 次备注 · 2.0.0-neo-dev 黑洞精确 EMC 入账）
 * - 删除：无。
 * - 新增：同 owner 多黑洞分数合并、NBT 队列恢复、换绑先结算和真实拆除不重付四项 GameTest。
 * - 修改：通过正式方块注册、生产待结算 API 与世界账本读取验收，不启用资源转换入口。
 * - 用途：保证已产生收益移交账本后才清空方块队列，反复结算不会重复接收同一份收益。
 *
 * 【变更记录 #2】2026-10-22 21:15:42（第 2 次备注 · 2.0.0-neo-dev 损坏黑洞队列隔离）
 * - 删除：无。
 * - 新增：错误类型队列原文往返、拒绝追加及换绑、实际拆除不发放收益的测试。
 * - 修改：损坏队列通过独立 NBT 标签注入，不把读取失败误判为可正常结算的零金额。
 * - 用途：防止未识别的待结算原文被覆盖或改变归属；恢复日志内容由独立持久化检查验收。
 */
package com.primordial.neoforge.blackhole;

import java.math.BigInteger;
import java.util.UUID;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.service.EmcAmount;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeBlackholeCreditsGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeBlackholeCreditsGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void combinesFractionsFromTwoBlackholesThroughServerTicker(GameTestHelper helper) {
        UUID owner = offlineOwner(helper);
        NeoForgeEmcSavedData ledger = ledger(helper);
        NeoForgeBlackholeBlockEntity first = place(helper, new BlockPos(0, 1, 1), owner);
        NeoForgeBlackholeBlockEntity second = place(helper, new BlockPos(2, 1, 1), owner);
        first.queueCredit(new EmcAmount(BigInteger.valueOf(3), BigInteger.valueOf(4)));
        second.queueCredit(new EmcAmount(BigInteger.valueOf(5), BigInteger.valueOf(6)));
        check(ledger.pending(owner).isZero(), "Queued block credit must wait for a production settlement entry point");
        helper.runAfterDelay(2, () -> {
            EmcAmount expected = new EmcAmount(BigInteger.valueOf(19), BigInteger.valueOf(12));
            check(ledger.pending(owner).equals(expected), "Server ticker must combine both exact fractions by owner");
            check(first.pendingCredit().isZero() && second.pendingCredit().isZero(),
                    "Both block queues must clear after the world ledger accepts their credit");
            first.settlePending();
            second.settlePending();
            check(ledger.pending(owner).equals(expected), "Repeated settlement must not pay either block twice");
            helper.succeed();
        });
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void restoresPendingQueueFromNbtAndTransfersItOnlyOnce(GameTestHelper helper) {
        UUID owner = offlineOwner(helper);
        NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
        EmcAmount queued = new EmcAmount(BigInteger.valueOf(23), BigInteger.valueOf(7));
        blackhole.queueCredit(queued);
        CompoundTag saved = blackhole.saveWithFullMetadata(helper.getLevel().registryAccess());
        // 原位清空再读回快照，避免先拆除已结算原件、再恢复旧队列而人为重放收益。
        blackhole.loadWithComponents(new CompoundTag(), helper.getLevel().registryAccess());
        check(blackhole.pendingCredit().isZero(), "Loading an empty tag must clear the previous in-memory queue");
        blackhole.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(owner.equals(blackhole.owner()) && blackhole.pendingCredit().equals(queued),
                "NBT reload must restore both owner and the complete pending rational amount");
        check(blackhole.settlePending(), "A restored queue must transfer successfully to the offline ledger");
        check(blackhole.settlePending(), "An already accepted queue must settle as an empty no-op");
        check(blackhole.pendingCredit().isZero() && ledger(helper).pending(owner).equals(queued),
                "Repeated settlement must preserve exactly one copy of the restored credit");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void settlesPreviousOwnerBeforeRebindingPendingBlackhole(GameTestHelper helper) {
        UUID previousOwner = offlineOwner(helper);
        UUID nextOwner = offlineOwner(helper);
        NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, previousOwner);
        EmcAmount previousCredit = new EmcAmount(BigInteger.valueOf(13), BigInteger.valueOf(5));
        EmcAmount nextCredit = new EmcAmount(BigInteger.valueOf(7), BigInteger.valueOf(4));
        blackhole.queueCredit(previousCredit);
        blackhole.setOwner(nextOwner);
        NeoForgeEmcSavedData ledger = ledger(helper);
        check(nextOwner.equals(blackhole.owner()) && blackhole.pendingCredit().isZero(),
                "Owner replacement must transfer the previous queue before changing its owner");
        check(ledger.pending(previousOwner).equals(previousCredit) && ledger.pending(nextOwner).isZero(),
                "Credit earned before rebinding must belong entirely to the previous owner");
        blackhole.queueCredit(nextCredit);
        check(blackhole.settlePending(), "The rebound owner's future credit must settle normally");
        blackhole.setOwner(nextOwner);
        check(ledger.pending(previousOwner).equals(previousCredit) && ledger.pending(nextOwner).equals(nextCredit),
                "Future credit must belong to the new owner without replaying the old queue");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void settlesPendingCreditWhenReplacedWithAirWithoutRepeatingIt(GameTestHelper helper) {
        UUID owner = offlineOwner(helper);
        NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
        EmcAmount queued = new EmcAmount(BigInteger.valueOf(29), BigInteger.valueOf(8));
        blackhole.queueCredit(queued);
        helper.setBlock(POSITION, Blocks.AIR);
        check(blackhole.isRemoved(), "Replacing the blackhole with air must remove its block entity");
        check(helper.getLevel().getBlockEntity(helper.absolutePos(POSITION)) == null,
                "The removed block entity must no longer occupy its world position");
        check(blackhole.pendingCredit().isZero() && ledger(helper).pending(owner).equals(queued),
                "The real removal callback must transfer pending credit before losing the block entity");
        blackhole.settlePending();
        helper.setBlock(POSITION, Blocks.AIR);
        helper.runAfterDelay(2, () -> {
            check(ledger(helper).pending(owner).equals(queued),
                    "Removed-entity calls and later ticks must not repeat the removal credit");
            helper.succeed();
        });
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesMalformedQueueAndRejectsCreditAndRebindingBeforeRemoval(GameTestHelper helper) {
        UUID owner = offlineOwner(helper);
        UUID nextOwner = offlineOwner(helper);
        NeoForgeBlackholeBlockEntity blackhole = place(helper, POSITION, owner);
        StringTag rawQueue = StringTag.valueOf("preserve this malformed pending-credit payload");
        CompoundTag source = new CompoundTag();
        source.putUUID("owner", owner);
        source.put("queued_credit", rawQueue);
        blackhole.loadWithComponents(source, helper.getLevel().registryAccess());
        check(rawQueue.equals(blackhole.saveWithFullMetadata(helper.getLevel().registryAccess()).get("queued_credit")),
                "A wrongly typed queue must survive saving as its original raw NBT");
        expectRejected(() -> blackhole.queueCredit(EmcAmount.ONE),
                "A malformed queue must reject new credit instead of overwriting the original payload");
        expectRejected(() -> blackhole.setOwner(nextOwner),
                "A malformed queue must prevent rebinding credit to another owner");
        CompoundTag retained = blackhole.saveWithFullMetadata(helper.getLevel().registryAccess());
        check(owner.equals(blackhole.owner()) && owner.equals(retained.getUUID("owner"))
                && rawQueue.equals(retained.get("queued_credit")),
                "Rejected operations must retain both the previous owner and the complete malformed queue");
        check(!blackhole.settlePending(), "A malformed queue must remain unsettled rather than becoming zero credit");
        helper.setBlock(POSITION, Blocks.AIR);
        check(blackhole.isRemoved(), "Malformed blackholes must still support the real removal lifecycle");
        check(ledger(helper).pending(owner).isZero() && ledger(helper).pending(nextOwner).isZero(),
                "Removal must preserve malformed data separately without inventing credit for either owner");
        helper.succeed();
    }

    private static void expectRejected(Runnable operation, String message) {
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new GameTestAssertException(message);
    }

    private static NeoForgeBlackholeBlockEntity place(GameTestHelper helper, BlockPos position, UUID owner) {
        helper.setBlock(position, NeoForgeModRegistries.BLACKHOLE.get());
        BlockEntity entity = helper.getBlockEntity(position);
        if (!(entity instanceof NeoForgeBlackholeBlockEntity blackhole)) {
            throw new GameTestAssertException("Expected the registered blackhole block entity");
        }
        blackhole.setOwner(owner);
        return blackhole;
    }

    private static UUID offlineOwner(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        check(helper.getLevel().getServer().getPlayerList().getPlayer(owner) == null,
                "Credit lifecycle tests require an offline owner");
        return owner;
    }

    private static NeoForgeEmcSavedData ledger(GameTestHelper helper) {
        return NeoForgeEmcSavedData.get(helper.getLevel().getServer());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
