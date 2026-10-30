/**
 * 【文件职责】以原版箱子验收标准能力查询及方块移除后的缓存失效，不代表生产机器功能。
 * 【变更记录 #1】2026-10-22 11:36:36（第 1 次备注 · 2.0.0-neo-dev 能力平台契约）
 * - 删除：无。
 * - 新增：27 槽物品查询、空侧拒绝、流体/能量缺省与缓存失效后下一 tick 重查。
 * - 修改：测试代码与结构模板位于独立 gametest 源集，普通运行与正式 JAR 均不加载。
 * - 用途：保留原版 provider 冒烟基线；自有 provider 的主动失效另由测试夹具覆盖。
 */
package com.primordial.neoforge.machine;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import com.primordial.neoforge.PrimordialNeoForgeMod;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeCapabilityProxyGameTests {
    private NeoForgeCapabilityProxyGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID)
    public static void queriesRegisteredChestCapabilityAndInvalidatesAfterRemoval(GameTestHelper helper) {
        BlockPos relativePos = new BlockPos(1, 1, 1);
        helper.setBlock(relativePos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(relativePos);

        IItemHandler items = NeoForgeCapabilityProxy.INSTANCE.itemHandler(chest, Direction.NORTH);
        if (items == null || items.getSlots() != 27) {
            throw new GameTestAssertException("Expected the registered chest item capability with 27 slots");
        }
        if (NeoForgeCapabilityProxy.INSTANCE.itemHandler(chest, null) != null
                || NeoForgeCapabilityProxy.INSTANCE.fluidHandler(chest, null) != null) {
            throw new GameTestAssertException("Item and fluid capability queries must reject a null side");
        }
        if (NeoForgeCapabilityProxy.INSTANCE.fluidHandler(chest, Direction.NORTH) != null
                || NeoForgeCapabilityProxy.INSTANCE.energyHandler(chest, Direction.NORTH) != null) {
            throw new GameTestAssertException("Chest must not expose fluid or energy capabilities");
        }

        AtomicBoolean invalidated = new AtomicBoolean();
        BlockCapabilityCache<IItemHandler, Direction> cache = BlockCapabilityCache.create(
                Capabilities.ItemHandler.BLOCK,
                helper.getLevel(),
                helper.absolutePos(relativePos),
                Direction.NORTH,
                () -> true,
                () -> invalidated.set(true));
        if (cache.getCapability() == null) {
            throw new GameTestAssertException("Expected the cache to resolve the registered chest capability");
        }

        helper.setBlock(relativePos, Blocks.AIR);
        if (!invalidated.get()) {
            throw new GameTestAssertException("Expected block removal to invalidate the capability cache");
        }
        if (NeoForgeCapabilityProxy.INSTANCE.itemHandler(chest, Direction.NORTH) != null) {
            throw new GameTestAssertException("Removed block entities must not provide capabilities");
        }
        helper.runAfterDelay(1, () -> {
            if (cache.getCapability() != null) {
                throw new GameTestAssertException("Invalidated cache must resolve no capability after block removal");
            }
            helper.succeed();
        });
    }
}
