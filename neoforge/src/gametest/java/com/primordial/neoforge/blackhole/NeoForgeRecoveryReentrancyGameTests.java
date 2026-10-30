/**
 * 【文件职责】用真实 NeoForge 实体离开事件验收黑洞消费时的拆除、换主、异常和账本失效重入。
 * 【变更记录 #1】2026-10-22 21:55:24（第 1 次备注 · 2.0.0-neo-dev 实体移除重入保护）
 * - 删除：无。
 * - 新增：同步移除回调变更方块/归属、已移除后抛错仍入账和收益恢复日志的经济一致性测试。
 * - 修改：只在测试期间注册原生事件监听器，finally 注销并恢复被临时替换的世界账本缓存。
 * - 用途：确认已经消费的实体只付给消费前 UUID 一次；无法入账时保留完整原始收益供恢复。
 * 【变更记录 #2】2026-10-22 21:56:43（第 2 次备注 · 2.0.0-neo-dev 原生异步保存验收）
 * - 删除：保存排队后立即读取恢复文件的时序假设。
 * - 新增：NeoForge IOUtilities 保存任务完成等待。
 * - 修改：同进程磁盘断言在实际异步写入完成后执行。
 * - 用途：区分恢复记录缺失与测试过早读盘，不修改生产保存机制。
 */
package com.primordial.neoforge.blackhole;

import java.io.IOException;
import java.math.BigInteger;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcNbt;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.service.EmcAmount;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeRecoveryReentrancyGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeRecoveryReentrancyGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void entityLeaveChangingOwnerStillPaysOriginalOwnerOnce(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID replacementOwner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        Entity entity = cow(helper);
        EmcAmount expected = positiveQuote(entity);
        RemovalListener listener = new RemovalListener(entity, () -> block.setOwner(replacementOwner));
        NeoForge.EVENT_BUS.register(listener);
        try {
            NeoForgeEntityRecovery.absorbAbove(block, helper.getLevel());
            check(listener.calls.get() == 1 && entity.isRemoved() && replacementOwner.equals(block.owner()),
                    "The native removal callback must synchronously change the owner's actual block state");
            check(ledger(helper).pending(owner).equals(expected) && ledger(helper).pending(replacementOwner).isZero(),
                    "A consumed entity must pay only the UUID captured before the ownership-changing callback");
            NeoForgeEntityRecovery.absorbAbove(block, helper.getLevel());
            check(listener.calls.get() == 1 && ledger(helper).pending(owner).equals(expected)
                    && ledger(helper).pending(replacementOwner).isZero(),
                    "Another entity query must not replay credit under either owner");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            if (!entity.isRemoved()) entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void entityLeaveRemovingBlockStillPaysCapturedOwnerOnce(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        Entity entity = cow(helper);
        EmcAmount expected = positiveQuote(entity);
        RemovalListener listener = new RemovalListener(entity, () -> helper.setBlock(POSITION, Blocks.AIR));
        NeoForge.EVENT_BUS.register(listener);
        try {
            NeoForgeEntityRecovery.absorbAbove(block, helper.getLevel());
            check(listener.calls.get() == 1 && entity.isRemoved() && block.isRemoved(),
                    "The native EntityLeaveLevelEvent must execute real blackhole teardown before discard returns");
            check(ledger(helper).pending(owner).equals(expected) && block.pendingCredit().isZero(),
                    "Credit must reach the captured owner's world ledger after the original block entity was removed");
            NeoForgeEntityRecovery.absorbAbove(block, helper.getLevel());
            check(ledger(helper).pending(owner).equals(expected), "The invalid old block must not replay removed-entity credit");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            if (!entity.isRemoved()) entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void entityLeaveFailureAfterRemovalDoesNotLoseOrDuplicatePayment(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        Entity entity = cow(helper);
        EmcAmount expected = positiveQuote(entity);
        RemovalListener listener = new RemovalListener(entity, () -> {
            throw new IllegalStateException("intentional GameTest failure after entity removal");
        });
        NeoForge.EVENT_BUS.register(listener);
        try {
            NeoForgeEntityRecovery.absorbAbove(block, helper.getLevel());
            check(listener.calls.get() == 1 && entity.isRemoved() && ledger(helper).pending(owner).equals(expected),
                    "A removal listener throwing after consumption must not escape recovery or suppress the earned payment");
            NeoForgeEntityRecovery.absorbAbove(block, helper.getLevel());
            check(ledger(helper).pending(owner).equals(expected), "A partially completed native removal callback must not pay twice");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            if (!entity.isRemoved()) entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void ledgerInvalidatedDuringEntityLeavePreservesCapturedPaymentSnapshot(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity block = place(helper, owner);
        Entity entity = cow(helper);
        EmcAmount expected = positiveQuote(entity);
        NeoForgeEmcSavedData original = ledger(helper);
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("schema", 918);
        NeoForgeEmcSavedData blocked = NeoForgeEmcSavedData.load(unknown, helper.getLevel().registryAccess());
        var storage = helper.getLevel().getServer().overworld().getDataStorage();
        RemovalListener listener = new RemovalListener(entity, () -> storage.set("primordial_emc_credits", blocked));
        NeoForge.EVENT_BUS.register(listener);
        try {
            NeoForgeEntityRecovery.absorbAbove(block, helper.getLevel());
            check(listener.calls.get() == 1 && entity.isRemoved() && original.pending(owner).isZero()
                    && blocked.pending(owner).isZero(),
                    "If the preflight-valid ledger becomes blocked inside discard, consumed credit must not be silently booked elsewhere");
        } finally {
            storage.set("primordial_emc_credits", original);
            NeoForge.EVENT_BUS.unregister(listener);
            if (!entity.isRemoved()) entity.discard();
        }
        // 恢复真实账本之后才保存，避免测试损坏快照写入本世界的正式金额文件。
        storage.save();
        IOUtilities.waitUntilIOWorkerComplete();
        check(recoveryContains(helper, block, owner, expected),
                "The real recovery .dat must preserve owner, exact credit, dimension, coordinates and entity type");
        helper.succeed();
    }

    private static boolean recoveryContains(GameTestHelper helper, NeoForgeBlackholeBlockEntity block,
            UUID owner, EmcAmount expected) {
        try {
            CompoundTag disk = NbtIo.readCompressed(helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data/primordial_emc_recovery.dat"), NbtAccounter.unlimitedHeap()).getCompound("data");
            ListTag snapshots = disk.getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < snapshots.size(); i++) {
                CompoundTag snapshot = snapshots.getCompound(i);
                if (snapshot.hasUUID("owner") && owner.equals(snapshot.getUUID("owner"))) {
                    return expected.equals(NeoForgeEmcNbt.read(snapshot.getCompound("queued_credit")))
                            && snapshot.getString("entity_type").equals("minecraft:cow")
                            && snapshot.getString("dimension").equals(helper.getLevel().dimension().location().toString())
                            && snapshot.getInt("x") == block.getBlockPos().getX()
                            && snapshot.getInt("y") == block.getBlockPos().getY()
                            && snapshot.getInt("z") == block.getBlockPos().getZ();
                }
            }
            return false;
        } catch (IOException failure) {
            throw new GameTestAssertException("Cannot read the actual entity credit recovery file: " + failure.getMessage());
        }
    }

    public static final class RemovalListener {
        private final Entity target;
        private final Runnable callback;
        private final AtomicInteger calls = new AtomicInteger();

        private RemovalListener(Entity target, Runnable callback) {
            this.target = target;
            this.callback = callback;
        }

        @SubscribeEvent
        public void onEntityLeave(EntityLeaveLevelEvent event) {
            if (event.getEntity() == target) {
                calls.incrementAndGet();
                callback.run();
            }
        }
    }

    private static NeoForgeBlackholeBlockEntity place(GameTestHelper helper, UUID owner) {
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        check(helper.getBlockEntity(POSITION) instanceof NeoForgeBlackholeBlockEntity, "Expected the registered production blackhole");
        NeoForgeBlackholeBlockEntity block = (NeoForgeBlackholeBlockEntity) helper.getBlockEntity(POSITION);
        block.setOwner(owner);
        return block;
    }

    private static Entity cow(GameTestHelper helper) {
        Entity entity = helper.spawn(EntityType.COW, Vec3.atCenterOf(POSITION.above()));
        entity.setNoGravity(true);
        return entity;
    }

    private static EmcAmount positiveQuote(Entity entity) {
        BigInteger quote = NeoForgeEntityRecovery.price(entity);
        check(quote.signum() > 0, "The actual test cow must have a positive configured entity quote");
        return EmcAmount.of(quote);
    }

    private static NeoForgeEmcSavedData ledger(GameTestHelper helper) {
        return NeoForgeEmcSavedData.get(helper.getLevel().getServer());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
