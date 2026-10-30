/**
 * 【文件职责】借原生物品退款产生的实体加入事件验收黑洞资源端口的同步回调重入边界。
 * 【变更记录 #1】2026-10-22 21:54:40（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：换绑退回无价缓冲时，从真实 EntityJoinLevelEvent 递归访问全部缓存端口及归属修改的测试。
 * - 修改：测试使用合法目标版 NBT 恢复无价缓冲，不替换 ProjectE 报价器；临时事件监听器在 finally 注销。
 * - 用途：保证退款回调不能接收新资源、截走收益或改变本轮退款归属，并验证缓冲原物只退还一次。
 * 【变更记录 #2】2026-10-22 21:55:59（第 2 次备注 · 2.0.0-neo-dev 退款测试前提校验）
 * - 删除：无。新增：退款开始前确认三类真实端口均可接收夹具的模拟断言。
 * - 修改：先证明输入有效，再检查退款时拒收，避免普通禁用配置掩盖重入缺陷。
 * - 用途：确保测试确实经过退款门禁而非其它接收限制。
 */
package com.primordial.neoforge.blackhole;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.PrimordialNeoForgeMod;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder(PrimordialNeoForgeMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgeResourceReentrancyGameTests {
    private static final BlockPos POSITION = new BlockPos(1, 1, 1);

    private NeoForgeResourceReentrancyGameTests() {}

    @GameTest(template = "capability_proxy_empty", templateNamespace = PrimordialNeoForgeMod.MOD_ID,
            batch = "primordial_reentrancy", setupTicks = 0, required = true)
    public static void rejectsCachedPortsAndOwnerChangesDuringNativeUnpricedRefund(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        UUID nextOwner = UUID.randomUUID();
        helper.setBlock(POSITION, NeoForgeModRegistries.BLACKHOLE.get());
        check(helper.getBlockEntity(POSITION) instanceof NeoForgeBlackholeBlockEntity, "Expected the real registered blackhole");
        NeoForgeBlackholeBlockEntity blackhole = (NeoForgeBlackholeBlockEntity) helper.getBlockEntity(POSITION);
        blackhole.setOwner(owner);
        BlockPos position = helper.absolutePos(POSITION);
        IItemHandler items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, position, Direction.UP);
        IFluidHandler fluids = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, position, Direction.UP);
        IEnergyStorage energy = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, position, Direction.UP);
        check(items != null && fluids != null && energy != null, "All three registered sink ports must be present");

        ItemStack refund = new ItemStack(Items.BARRIER, 7);
        refund.set(DataComponents.CUSTOM_NAME, Component.literal("refund reentrancy " + owner));
        check(NeoForgeRecoveryPrices.item(refund).isZero(), "The refund fixture must be unpriced in native ProjectE");
        CompoundTag saved = blackhole.saveWithFullMetadata(helper.getLevel().registryAccess());
        ListTag buffer = new ListTag();
        buffer.add(refund.save(helper.getLevel().registryAccess()));
        saved.put("buffer", buffer);
        blackhole.loadWithComponents(saved, helper.getLevel().registryAccess());

        ItemStack attemptedItems = new ItemStack(Items.DIAMOND, 3);
        FluidStack attemptedFluid = new FluidStack(Fluids.WATER, 5);
        check(items.insertItem(0, attemptedItems, true).isEmpty()
                        && fluids.fill(attemptedFluid, IFluidHandler.FluidAction.SIMULATE) == 5
                        && energy.receiveEnergy(5, true) == 5,
                "Before refunding, the attached owned blackhole must accept simulations for all three valid inputs");
        AtomicInteger returnedCount = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<String> failure = new AtomicReference<>();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getLevel() != helper.getLevel() || !(event.getEntity() instanceof ItemEntity entity)
                    || !ItemStack.isSameItemSameComponents(entity.getItem(), refund)) return;
            returnedCount.addAndGet(entity.getItem().getCount());
            if (callbacks.incrementAndGet() != 1) return;
            if (!owner.equals(blackhole.owner())) failure.compareAndSet(null, "The old owner must remain bound throughout its refund callback");
            if (items.insertItem(0, attemptedItems, true).getCount() != 3
                    || items.insertItem(0, attemptedItems, false).getCount() != 3
                    || items.isItemValid(0, attemptedItems)) {
                failure.compareAndSet(null, "A refund callback must not reserve, buffer or validate new item input");
            }
            if (fluids.fill(attemptedFluid, IFluidHandler.FluidAction.SIMULATE) != 0
                    || fluids.fill(attemptedFluid, IFluidHandler.FluidAction.EXECUTE) != 0
                    || fluids.isFluidValid(0, attemptedFluid)) {
                failure.compareAndSet(null, "A refund callback must reject both simulated and actual fluid input");
            }
            if (energy.receiveEnergy(5, true) != 0 || energy.receiveEnergy(5, false) != 0 || energy.canReceive()) {
                failure.compareAndSet(null, "A refund callback must reject both simulated and actual FE input");
            }
            boolean ownerChangeRejected = false;
            try {
                blackhole.setOwner(UUID.randomUUID());
            } catch (IllegalStateException expected) {
                ownerChangeRejected = true;
            }
            if (!ownerChangeRejected || !owner.equals(blackhole.owner())) {
                failure.compareAndSet(null, "A nested owner change must fail while refund delivery is in progress");
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, listener);
        try {
            // 方块仍留在世界且原归属有效；因此拒收必须来自退款重入门禁，而非移除后的失效检查。
            blackhole.setOwner(nextOwner);
            check(failure.get() == null, failure.get() == null ? "Unexpected refund callback failure" : failure.get());
            check(callbacks.get() == 1 && returnedCount.get() == 7,
                    "The complete seven-item buffer must be returned through exactly one native spawn callback");
            check(nextOwner.equals(blackhole.owner()) && !blackhole.isRemoved(),
                    "The original owner change must complete once after its refund finishes");
            NeoForgeEmcSavedData ledger = NeoForgeEmcSavedData.get(helper.getLevel().getServer());
            check(blackhole.pendingCredit().isZero() && ledger.pending(owner).isZero() && ledger.pending(nextOwner).isZero(),
                    "Rejected recursive resources and unpriced refunds must create no credit for either owner");
            check(!blackhole.saveWithFullMetadata(helper.getLevel().registryAccess()).contains("buffer"),
                    "Refunded items and rejected recursive inputs must leave no hidden buffer stock");
            check(attemptedItems.getCount() == 3 && attemptedFluid.getAmount() == 5 && refund.getCount() == 7,
                    "The callback must retain all caller-owned inputs and leave its fixture unchanged");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
            helper.setBlock(POSITION, Blocks.AIR);
        }
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
