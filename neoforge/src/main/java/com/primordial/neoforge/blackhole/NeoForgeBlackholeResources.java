/**
 * 【文件职责】黑洞物品/流体/FE 接收预算、缓冲清算、掉落吸收与粒子节流；不直接写 ProjectE 余额。
 * 【变更记录 #1】2026-10-22 21:38:58（第 1 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：无。
 * - 新增：三类只入端口、同刻共用预算、按实际数量计价、物品缓冲和掉落实体余量处理。
 * - 修改：所有执行先验证服务端主线程、归属与账本；模拟不修改资源、队列、知识或预算。
 * - 用途：沿用 Forge 数值与接收语义，修正满缓冲模拟误报；可选化学品另行迁移。
 * 【变更记录 #2】2026-10-22 21:51:06（第 2 次备注 · 2.0.0-neo-dev 第三方回调边界）
 * - 删除：跨原生报价/学习回调保留的可变迭代器。
 * - 新增：报价重入保护、回调后有效性检查、忙时延后返还、返还时关闭端口及粒子异常隔离。
 * - 修改：原生组件处理器、知识事件或实体加入事件拆除黑洞时，停止接收并退回未处理缓冲。
 * - 用途：外部回调不能在已移除实体上确认资源，通知失败不改变已确认接收数量。
 * 【变更记录 #3】2026-10-22 22:20:00（第 3 次备注 · 2.0.0-neo-dev 化学品共用交易边界）
 * - 删除：无。
 * - 新增：供不依赖 Mekanism 的接收器使用的有效性检查。
 * - 修改：化学品报价期间关闭其他资源入口，统一复用粒子节流。
 * - 用途：防止第三方标签回调重入改变待结算状态；保留三类原有预算。
 */
package com.primordial.neoforge.blackhole;

import com.primordial.neoforge.config.NeoForgeServerConfig;
import com.primordial.neoforge.value.NeoForgeRecoveryPrices;
import com.primordial.service.EmcAmount;
import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

final class NeoForgeBlackholeResources {
    private final NeoForgeBlackholeBlockEntity block;
    private final NeoForgeBlackholeStorage storage = new NeoForgeBlackholeStorage();
    final IItemHandler items = new Items();
    final IFluidHandler fluids = new Fluids();
    final IEnergyStorage energy = new Energy();
    private long budgetTick = Long.MIN_VALUE;
    private long particleTick = Long.MIN_VALUE;
    private int itemCount, fluidAmount, energyAmount;
    private boolean flushing;
    private boolean quoting;
    private boolean releasing;
    private boolean releaseRequested;
    private boolean particleWarned;
    private long storageVersion;

    NeoForgeBlackholeResources(NeoForgeBlackholeBlockEntity block) { this.block = block; }
    boolean usable() { return storage.usable(); }
    boolean busy() { return flushing || quoting || releasing; }

    boolean externalReady() { return available(); }
    private boolean available() { return !busy() && !block.chemicalBusy() && storage.usable() && block.resourceReady(); }

    private EmcAmount itemPrice(ItemStack input) {
        if (quoting) return EmcAmount.ZERO;
        long version = storageVersion;
        var owner = block.owner();
        quoting = true;
        EmcAmount result;
        try {
            result = NeoForgeRecoveryPrices.item(input);
        } finally {
            quoting = false;
            if (releaseRequested && !flushing) release();
        }
        return version == storageVersion && java.util.Objects.equals(owner, block.owner()) ? result : EmcAmount.ZERO;
    }

    private void resetBudget() {
        long now = block.getLevel().getGameTime();
        if (budgetTick == now) return;
        budgetTick = now;
        itemCount = fluidAmount = energyAmount = 0;
    }

    private int insert(ItemStack input, boolean simulate) {
        if (input == null || input.isEmpty()) return 0;
        if (!available() || itemPrice(input).isZero() || !available()) return 0;
        resetBudget();
        int accepted = storage.insert(input, Math.max(0, NeoForgeServerConfig.maxItems() - itemCount), simulate);
        if (!simulate && accepted > 0) {
            itemCount += accepted;
            block.setChanged();
        }
        return accepted;
    }

    void tick(ServerLevel level) {
        if (!available()) return;
        resetBudget();
        NeoForgeEntityRecovery.absorbAbove(block, level);
        AABB box = new AABB(block.getBlockPos().above()).inflate(0.1);
        int absorbed = 0;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (absorbed >= NeoForgeServerConfig.maxItemEntities() || itemCount >= NeoForgeServerConfig.maxItems()) break;
            if (entity.isRemoved()) continue;
            ItemStack stack = entity.getItem();
            int accepted = insert(stack, false);
            if (accepted <= 0) continue;
            if (accepted == stack.getCount()) entity.discard();
            else {
                entity.setItem(stack.copyWithCount(stack.getCount() - accepted));
                entity.setPickUpDelay(10);
            }
            absorbed++;
        }
        flush();
    }

    void flush() {
        if (!available() || storage.isEmpty()) return;
        flushing = true;
        try {
            long version = storageVersion;
            int index = 0;
            while (index < storage.stacks().size() && version == storageVersion && block.resourceReady()) {
                ItemStack stack = storage.stacks().get(index);
                EmcAmount price = itemPrice(stack);
                if (version != storageVersion || !block.resourceReady()) break;
                if (price.isZero()) { index++; continue; }
                block.queueCredit(price.multiply(stack.getCount()));
                storage.stacks().remove(index);
                block.setChanged();
                if (NeoForgeServerConfig.autoLearnReclaimedItems()) {
                    NeoForgeRecoveryKnowledge.recordItem(block.getLevel().getServer(), block.owner(), stack);
                }
                particles();
            }
        } finally {
            flushing = false;
            if (releaseRequested) release();
        }
    }

    /** 在原归属下清算，剩余无价物品退回世界；损坏原文由方块实体转存恢复日志。 */
    void release() {
        if (releasing) return;
        if (flushing || quoting) { releaseRequested = true; return; }
        releaseRequested = false;
        flush();
        if (!storage.usable()) return;
        releasing = true;
        try {
            var refund = java.util.List.copyOf(storage.stacks());
            storage.stacks().clear();
            if (!refund.isEmpty()) block.setChanged();
            for (ItemStack stack : refund) Containers.dropItemStack(block.getLevel(),
                    block.getBlockPos().getX() + 0.5, block.getBlockPos().getY() + 0.5,
                    block.getBlockPos().getZ() + 0.5, stack);
        } finally {
            releasing = false;
        }
    }

    void particles() {
        if (!(block.getLevel() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (particleTick != Long.MIN_VALUE && now - particleTick < NeoForgeServerConfig.particleInterval()) return;
        particleTick = now;
        var pos = block.getBlockPos();
        try {
            level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
        } catch (RuntimeException | LinkageError failure) {
            if (!particleWarned) LogUtils.getLogger().warn("[Primordial] Recovery particle notification failed at {}", pos, failure);
            particleWarned = true;
        }
    }

    void save(CompoundTag tag, HolderLookup.Provider registries) { storage.save(tag, registries); }
    void load(CompoundTag tag, HolderLookup.Provider registries) {
        storageVersion++;
        storage.load(tag, registries);
        budgetTick = particleTick = Long.MIN_VALUE;
        itemCount = fluidAmount = energyAmount = 0;
    }

    private final class Items implements IItemHandler {
        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return slot == 0 ? Math.min(64, NeoForgeServerConfig.maxItems()) : 0; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && available() && stack != null && !stack.isEmpty()
                    && !itemPrice(stack).isZero() && available();
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            int accepted = slot == 0 ? insert(stack, simulate) : 0;
            return accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
        }
    }

    private final class Fluids implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return FluidStack.EMPTY; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? NeoForgeServerConfig.maxFluid() : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && available() && stack != null && !stack.isEmpty()
                    && !NeoForgeRecoveryPrices.fluid(stack).isZero();
        }
        @Override public int fill(FluidStack input, FluidAction action) {
            if (input == null || input.isEmpty() || !available()) return 0;
            EmcAmount price = NeoForgeRecoveryPrices.fluid(input);
            if (price.isZero()) return 0;
            resetBudget();
            int accepted = Math.min(input.getAmount(), Math.max(0, NeoForgeServerConfig.maxFluid() - fluidAmount));
            if (accepted <= 0 || action.simulate()) return accepted;
            block.queueCredit(price.multiply(accepted));
            fluidAmount += accepted;
            NeoForgeRecoveryKnowledge.recordFluid(block.getLevel().getServer(), block.owner(),
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(input.getFluid()));
            particles();
            return accepted;
        }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) { return FluidStack.EMPTY; }
        @Override public FluidStack drain(int amount, FluidAction action) { return FluidStack.EMPTY; }
    }

    private final class Energy implements IEnergyStorage {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            if (amount <= 0 || !available()) return 0;
            EmcAmount price = NeoForgeRecoveryPrices.energy();
            if (price.isZero()) return 0;
            resetBudget();
            int accepted = Math.min(amount, Math.max(0, NeoForgeServerConfig.maxEnergy() - energyAmount));
            if (accepted <= 0 || simulate) return accepted;
            block.queueCredit(price.multiply(accepted));
            energyAmount += accepted;
            particles();
            return accepted;
        }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return NeoForgeServerConfig.maxEnergy(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return available() && !NeoForgeRecoveryPrices.energy().isZero(); }
    }
}
