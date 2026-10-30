/**
 * 【文件职责】验收 NeoForge 黑洞玩家放置、owner、NBT、实际资源能力和替换生命周期。
 *
 * 【变更记录 #1】2026-10-22 11:11:02（第 1 次备注 · 2.0.0-neo-dev 黑洞系统迁移）
 * - 删除：无。
 * - 新增：黑洞放置 owner 绑定、服务端 ticker、owner NBT 重载和方块替换四项 GameTest。
 * - 修改：无生产测试旁路；测试使用正式 NeoForge 注册对象和空结构模板。
 * - 用途：把 Forge 黑洞方块实体的最小生命周期变成目标平台可重复验收的契约。
 *
 * 【变更记录 #2】2026-10-22 11:22:28（第 2 次备注 · 2.0.0-neo-dev 黑洞生命周期验收）
 * - 删除：直接设置 owner 冒充玩家放置的测试，以及对生产 ticker 计数器和存档字段的依赖。
 * - 新增：真实 BlockItem 放置与绑定保护、owner 独立标签读取、三类资源入口关闭和掉落实体保留检查。
 * - 修改：NBT 往返分开验证新写入数据与独立 owner 标签，不据此宣称可导入 Forge 世界。
 * - 用途：让首阶段测试覆盖真实生产入口，确保 EMC 接入前不会吞资源或写入测试追踪状态。
 *
 * 【变更记录 #3】2026-10-22 21:46:13（第 3 次备注 · 2.0.0-neo-dev 黑洞实际回收）
 * - 删除：三类资源入口应关闭及掉落应原样停留的阶段性断言。
 * - 新增：六面与无方向能力、七颗钻石真实 ticker 吸收及唯一离线归属的精确入账断言。
 * - 修改：保留放置绑定、独立 owner 标签与 NBT 往返三项测试；移除仍走正式方块生命周期。
 * - 用途：让已接通回收系统的生命周期测试验证真实工作行为，并防止重复拆除重复付费。
 */
package com.primordial.neoforge.blackhole;

import java.util.UUID;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeBlackholeLifecycleGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);
    private static final UUID OWNER = UUID.fromString("2f7fc8d1-653a-4a72-b9d9-a44d8a9d6b01");

    private NeoForgeBlackholeLifecycleGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void bindsPlayerOwnerThroughBlockItemPlacementAndKeepsExistingOwner(GameTestHelper helper) {
        helper.setBlock(POSITION.below(), Blocks.STONE);
        helper.setBlock(POSITION, Blocks.AIR);
        Player placer = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockItem item = NeoForgeModRegistries.BLACKHOLE_ITEM.get();
        ItemStack held = new ItemStack(item);
        placer.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos absolutePos = helper.absolutePos(POSITION);
        BlockPos support = absolutePos.below();
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(absolutePos), Direction.UP, support, false);
        UseOnContext use = new UseOnContext(placer, InteractionHand.MAIN_HAND, hit);
        check(item.place(new BlockPlaceContext(use)).consumesAction(), "Registered blackhole item must place successfully");
        NeoForgeBlackholeBlockEntity blackhole = require(helper.getBlockEntity(POSITION), "Expected blackhole block entity");
        check(placer.getUUID().equals(blackhole.owner()), "BlockItem placement must bind the placing player's UUID");
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        check(!placer.getUUID().equals(other.getUUID()), "Expected distinct test players");
        NeoForgeModRegistries.BLACKHOLE.get().setPlacedBy(helper.getLevel(), absolutePos,
                helper.getBlockState(POSITION), other, new ItemStack(item));
        check(placer.getUUID().equals(blackhole.owner()), "Placement callback must not overwrite an existing owner");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void preservesOwnerThroughNbtRoundTripWithoutTickerState(GameTestHelper helper) {
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        NeoForgeBlackholeBlockEntity original = require(helper.getBlockEntity(POSITION), "Expected original blackhole");
        original.setOwner(OWNER);
        CompoundTag saved = original.saveWithFullMetadata(helper.getLevel().registryAccess());
        check(saved.hasUUID("owner") && OWNER.equals(saved.getUUID("owner")), "Saved NBT must contain the owner UUID");
        check(!saved.contains("NeoForgeServerTickCount"), "Production NBT must not contain a test ticker counter");
        helper.setBlock(POSITION, Blocks.AIR);
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        NeoForgeBlackholeBlockEntity restored = require(helper.getBlockEntity(POSITION), "Expected restored blackhole");
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        check(original.isRemoved(), "Replacing the block must remove the old block entity");
        check(OWNER.equals(restored.owner()), "Owner UUID must survive NeoForge NBT reload");
        CompoundTag resaved = restored.saveWithFullMetadata(helper.getLevel().registryAccess());
        check(resaved.hasUUID("owner") && OWNER.equals(resaved.getUUID("owner")), "Reloaded owner must be saved again");
        check(!resaved.contains("NeoForgeServerTickCount"), "Reloaded NBT must not acquire a test ticker counter");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void readsIndependentOwnerTagsAndClearsMissingOrMalformedUuid(GameTestHelper helper) {
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        NeoForgeBlackholeBlockEntity blackhole = require(helper.getBlockEntity(POSITION), "Expected blackhole block entity");
        CompoundTag ownerTag = new CompoundTag();
        ownerTag.putUUID("owner", OWNER);
        blackhole.loadWithComponents(ownerTag, helper.getLevel().registryAccess());
        check(OWNER.equals(blackhole.owner()), "An independent owner UUID tag must be readable");
        blackhole.loadWithComponents(new CompoundTag(), helper.getLevel().registryAccess());
        check(blackhole.owner() == null, "Missing owner UUID must clear the previous owner");
        blackhole.setOwner(OWNER);
        CompoundTag wrongType = new CompoundTag();
        wrongType.putString("owner", "not-a-uuid");
        blackhole.loadWithComponents(wrongType, helper.getLevel().registryAccess());
        check(blackhole.owner() == null, "A non-UUID owner tag must be read safely as unbound");
        blackhole.setOwner(OWNER);
        CompoundTag wrongLength = new CompoundTag();
        wrongLength.putIntArray("owner", new int[]{1, 2, 3});
        blackhole.loadWithComponents(wrongLength, helper.getLevel().registryAccess());
        check(blackhole.owner() == null, "A malformed UUID array must be read safely as unbound");
        helper.succeed();
    }

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void exposesResourcePortsAndAbsorbsDroppedItemsBeforeReplacement(GameTestHelper helper) {
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        NeoForgeBlackholeBlockEntity blackhole = require(helper.getBlockEntity(POSITION), "Expected blackhole block entity");
        UUID owner = UUID.randomUUID();
        blackhole.setOwner(owner);
        for (Direction side : Direction.values()) assertResourcePorts(helper, side);
        assertResourcePorts(helper, null);
        ItemEntity dropped = helper.spawnItem(Items.DIAMOND, Vec3.atCenterOf(POSITION.above()));
        dropped.setItem(new ItemStack(Items.DIAMOND, 7));
        dropped.setNoGravity(true);
        EmcAmount unitPrice = NeoForgeRecoveryPrices.item(dropped.getItem());
        check(!unitPrice.isZero(), "The installed ProjectE map must price the dropped diamonds");
        EmcAmount expected = unitPrice.multiply(7);
        helper.runAfterDelay(3, () -> {
            check(dropped.isRemoved(), "Production blackhole ticker must absorb all seven dropped diamonds");
            check(NeoForgeEmcSavedData.get(helper.getLevel().getServer()).pending(owner).equals(expected),
                    "Exactly seven native sell prices must reach the offline owner's ledger");
            helper.setBlock(POSITION, Blocks.AIR);
            check(blackhole.isRemoved(), "Replacing blackhole with air must remove its block entity");
            check(helper.getLevel().getBlockEntity(helper.absolutePos(POSITION)) == null,
                    "Removed blackhole must not remain at its position");
            check(NeoForgeEmcSavedData.get(helper.getLevel().getServer()).pending(owner).equals(expected),
                    "Replacing an already settled blackhole must not repeat the absorbed items' credit");
            helper.succeed();
        });
    }

    private static void assertResourcePorts(GameTestHelper helper, Direction side) {
        BlockPos absolutePos = helper.absolutePos(POSITION);
        check(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, absolutePos, side) != null,
                "Blackhole must expose its real item sink (side=" + side + ")");
        check(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, absolutePos, side) != null,
                "Blackhole must expose its real fluid sink (side=" + side + ")");
        check(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, absolutePos, side) != null,
                "Blackhole must expose its real energy sink (side=" + side + ")");
    }

    private static NeoForgeBlackholeBlockEntity require(net.minecraft.world.level.block.entity.BlockEntity value,
            String message) {
        if (!(value instanceof NeoForgeBlackholeBlockEntity blackhole)) throw new GameTestAssertException(message);
        return blackhole;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
