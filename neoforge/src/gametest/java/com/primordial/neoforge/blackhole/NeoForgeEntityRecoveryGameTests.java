/**
 * 【文件职责】通过真实世界实体与方块拆除入口验收实体回收、无价退还和损坏缓冲隔离。
 * 【变更记录 #1】2026-10-22 21:48:38（第 1 次备注 · 2.0.0-neo-dev 黑洞实际回收）
 * - 删除：无。
 * - 新增：僵尸基础生命计价、排除类别、实体价格覆盖、无价物品退还与原始损坏缓冲恢复测试。
 * - 修改：实体真实加入世界并调用生产 ticker；原始缓冲从独立 NBT 注入，恢复日志由实际磁盘文件验收。
 * - 用途：确保实体确实移除才发放收益，未识别缓冲既不吞输入也不被换主或拆除悄然覆盖。
 * 【变更记录 #2】2026-10-22 21:56:35（第 2 次备注 · 2.0.0-neo-dev 恢复文件验收顺序）
 * - 删除：SavedData 发起异步保存后立即读取磁盘的竞态。
 * - 新增：NeoForge 官方 IOUtilities 写盘完成屏障。
 * - 修改：恢复日志先正常保存并等待 IO worker，再读取真实文件验证最新原文。
 * - 用途：区分尚未完成的后台写入和实际数据丢失，避免将存盘调度时序误报为生产故障。
 */
package com.primordial.neoforge.blackhole;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeEntityRecoveryGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeEntityRecoveryGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void absorbsZombieAtItsNativeBaseHealthPriceWithoutDrops(GameTestHelper helper) {
        List<? extends String> previous = List.copyOf(NeoForgeServerConfig.ENTITY_EMC_OVERRIDES.get());
        try {
            NeoForgeServerConfig.ENTITY_EMC_OVERRIDES.set(List.of());
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity blackhole = place(helper, owner);
            Zombie zombie = zombie(helper);
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            zombie.setHealth(1);
            BigInteger expected = BigInteger.valueOf(20L * 128 * 2);
            check(NeoForgeEntityRecovery.price(zombie).equals(expected),
                    "Zombie pricing must use its registered base health, ignoring current health and runtime health boosts");
            tick(helper, blackhole);
            check(zombie.isRemoved() && ledger(helper).pending(owner).equals(EmcAmount.of(expected)),
                    "A genuinely removed zombie must earn exactly its base-health monster quote");
            AABB nearby = new AABB(blackhole.getBlockPos().above()).inflate(0.5);
            check(helper.getLevel().getEntitiesOfClass(ItemEntity.class, nearby).isEmpty()
                    && helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, nearby).isEmpty(),
                    "Entity reclamation must not create vanilla loot or XP to reclaim a second time");
        } finally {
            NeoForgeServerConfig.ENTITY_EMC_OVERRIDES.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void excludesWorldPlayersItemsExperienceAndProjectilesFromEntityCredit(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity blackhole = place(helper, owner);
        Vec3 center = helper.absoluteVec(Vec3.atCenterOf(POSITION.above()));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(center.x, center.y, center.z);
        player.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(player), "The native mock player must actually enter the entity query world");
        try {
            ItemEntity item = helper.spawnItem(Items.BARRIER, Vec3.atCenterOf(POSITION.above()));
            item.setItem(new ItemStack(Items.BARRIER, 7));
            item.setNoGravity(true);
            ExperienceOrb orb = helper.spawn(EntityType.EXPERIENCE_ORB, Vec3.atCenterOf(POSITION.above()));
            orb.setNoGravity(true);
            Arrow arrow = helper.spawn(EntityType.ARROW, Vec3.atCenterOf(POSITION.above()));
            arrow.setNoGravity(true);
            check(NeoForgeEntityRecovery.price(player).signum() == 0 && NeoForgeEntityRecovery.price(item).signum() == 0
                    && NeoForgeEntityRecovery.price(orb).signum() == 0 && NeoForgeEntityRecovery.price(arrow).signum() == 0,
                    "Players, loose items, XP and projectiles must have no entity recovery quote");
            tick(helper, blackhole);
            check(!player.isRemoved() && !item.isRemoved() && item.getItem().getCount() == 7
                    && !orb.isRemoved() && !arrow.isRemoved(), "The production entity query must leave every excluded category present");
            check(ledger(helper).pending(owner).isZero(), "Excluded entities and an unpriced item must create no EMC");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void honorsDisabledAndHugeCustomEntityPriceOverrides(GameTestHelper helper) {
        List<? extends String> previous = List.copyOf(NeoForgeServerConfig.ENTITY_EMC_OVERRIDES.get());
        try {
            UUID owner = UUID.randomUUID();
            NeoForgeBlackholeBlockEntity blackhole = place(helper, owner);
            Zombie zombie = zombie(helper);
            NeoForgeServerConfig.ENTITY_EMC_OVERRIDES.set(List.of("minecraft:zombie=0"));
            tick(helper, blackhole);
            check(!zombie.isRemoved() && ledger(helper).pending(owner).isZero(), "A zero override must leave the zombie alive and uncredited");
            BigInteger custom = new BigInteger("123456789012345678901234567890123456789");
            NeoForgeServerConfig.ENTITY_EMC_OVERRIDES.set(List.of("minecraft:zombie=" + custom));
            check(NeoForgeEntityRecovery.price(zombie).equals(custom), "A custom entity quote must preserve every integer digit");
            tick(helper, blackhole);
            check(zombie.isRemoved() && ledger(helper).pending(owner).equals(EmcAmount.of(custom)),
                    "The same remaining zombie must be consumed once at the new exact custom quote");
        } finally {
            NeoForgeServerConfig.ENTITY_EMC_OVERRIDES.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void returnsValidUnpricedBufferedItemsWithTheirComponentsOnRemoval(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity blackhole = place(helper, owner);
        ItemStack unpriced = new ItemStack(Items.BARRIER, 7);
        unpriced.set(DataComponents.CUSTOM_NAME, Component.literal("unpriced buffered resource " + owner));
        check(NeoForgeRecoveryPrices.item(unpriced).isZero(), "The chosen buffered stack must currently be unpriced");
        ListTag buffer = new ListTag();
        buffer.add(unpriced.save(helper.getLevel().registryAccess()));
        CompoundTag source = new CompoundTag();
        source.putUUID("owner", owner);
        source.put("buffer", buffer);
        // 独立有效目标版 NBT 表示已在缓冲中的资源后来失去价格，不绕过正式物品组件 codec。
        blackhole.loadWithComponents(source, helper.getLevel().registryAccess());
        tick(helper, blackhole);
        check(buffer.equals(blackhole.saveWithFullMetadata(helper.getLevel().registryAccess()).get("buffer"))
                && ledger(helper).pending(owner).isZero(), "A currently unpriced valid buffer must remain present without invented earnings");
        helper.setBlock(POSITION, Blocks.AIR);
        int returned = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(POSITION)).inflate(1)).stream()
                .map(ItemEntity::getItem).filter(stack -> ItemStack.isSameItemSameComponents(stack, unpriced))
                .mapToInt(ItemStack::getCount).sum();
        check(blackhole.isRemoved() && returned == 7 && ledger(helper).pending(owner).isZero(),
                "Real block removal must refund all seven unpriced items with their original components and no EMC");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void quarantinesCorruptBufferThroughRebindingRejectionAndRealRemoval(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        NeoForgeBlackholeBlockEntity blackhole = place(helper, owner);
        StringTag raw = StringTag.valueOf("preserve malformed resource buffer " + owner);
        CompoundTag source = new CompoundTag();
        source.putUUID("owner", owner);
        source.put("buffer", raw);
        blackhole.loadWithComponents(source, helper.getLevel().registryAccess());
        expectRejected(() -> blackhole.setOwner(nextOwner), "A corrupt buffer must prevent changing its original owner");
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        check(blackhole.itemHandler().insertItem(0, diamond, true).getCount() == 1
                && blackhole.itemHandler().insertItem(0, diamond, false).getCount() == 1
                && blackhole.fluidHandler().fill(new FluidStack(Fluids.WATER, 10), IFluidHandler.FluidAction.EXECUTE) == 0
                && blackhole.energyHandler().receiveEnergy(10, false) == 0,
                "An unreadable buffer must block all resource consumption");
        Zombie zombie = zombie(helper);
        tick(helper, blackhole);
        check(!zombie.isRemoved() && owner.equals(blackhole.owner())
                && raw.equals(blackhole.saveWithFullMetadata(helper.getLevel().registryAccess()).get("buffer")),
                "A blocked ticker must retain the exact buffer and owner without consuming a nearby entity");
        helper.setBlock(POSITION, Blocks.AIR);
        check(blackhole.isRemoved() && !zombie.isRemoved() && ledger(helper).pending(owner).isZero()
                && ledger(helper).pending(nextOwner).isZero(), "Removing a corrupt block must neither swallow entities nor invent or reassign EMC");
        check(recoveryContains(helper, owner, raw), "The actual recovery file must retain the malformed buffer and its original owner");
        helper.succeed();
    }

    private static boolean recoveryContains(GameTestHelper helper, UUID owner, Tag raw) {
        var server = helper.getLevel().getServer();
        server.overworld().getDataStorage().save();
        IOUtilities.waitUntilIOWorkerComplete();
        try {
            CompoundTag disk = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/primordial_emc_recovery.dat"), NbtAccounter.unlimitedHeap());
            ListTag records = disk.getCompound("data").getList("blocks", Tag.TAG_COMPOUND);
            for (int i = 0; i < records.size(); i++) {
                CompoundTag record = records.getCompound(i);
                if (owner.equals(record.getUUID("owner")) && raw.equals(record.get("buffer"))) return true;
            }
            return false;
        } catch (IOException failure) {
            throw new GameTestAssertException("Cannot read the real recovery file: " + failure.getMessage());
        }
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atCenterOf(POSITION.above()));
        zombie.setNoGravity(true);
        return zombie;
    }

    private static NeoForgeBlackholeBlockEntity place(GameTestHelper helper, UUID owner) {
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        check(helper.getBlockEntity(POSITION) instanceof NeoForgeBlackholeBlockEntity, "Expected a registered blackhole");
        NeoForgeBlackholeBlockEntity blackhole = (NeoForgeBlackholeBlockEntity) helper.getBlockEntity(POSITION);
        blackhole.setOwner(owner);
        return blackhole;
    }

    private static NeoForgeEmcSavedData ledger(GameTestHelper helper) {
        return NeoForgeEmcSavedData.get(helper.getLevel().getServer());
    }

    private static void tick(GameTestHelper helper, NeoForgeBlackholeBlockEntity blackhole) {
        NeoForgeBlackholeBlockEntity.serverTick(helper.getLevel(), blackhole.getBlockPos(), blackhole.getBlockState(), blackhole);
    }

    private static void expectRejected(Runnable operation, String message) {
        try {
            operation.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new GameTestAssertException(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
