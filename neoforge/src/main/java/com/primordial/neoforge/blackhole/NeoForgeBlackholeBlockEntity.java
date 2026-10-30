/**
 * 【文件职责】NeoForge 1.21.1 黑洞归属、精确待结算队列及其 NBT/移交生命周期。
 *
 * 【变更记录 #1】2026-10-22 11:11:02（第 1 次备注 · 2.0.0-neo-dev 黑洞系统迁移）
 * - 删除：无。
 * - 新增：owner UUID、服务端 ticker 计数、HolderLookup.Provider NBT 保存/加载和移除状态查询。
 * - 修改：旧 Forge `owner` UUID 字段名保持不变；未迁移 buffer、pending EMC、Forge 能力和气体状态。
 * - 用途：为后续能力 provider 与 EMC 结算迁移提供可回归的方块实体生命周期边界。
 *
 * 【变更记录 #2】2026-10-22 11:18:30（第 2 次备注 · 2.0.0-neo-dev 黑洞系统迁移）
 * - 删除：生产方块实体中的 serverTickCount 测试计数、访问器和 NBT 字段。
 * - 新增：无。
 * - 修改：当前实体只持久化真实 owner 状态；回收 ticker 随后续 EMC 服务迁移再启用。
 * - 用途：避免测试追踪字段污染存档，当前独立新档不导入 Forge 世界或旧回收队列。
 *
 * 【变更记录 #3】2026-10-22 21:13:00（第 3 次备注 · 2.0.0-neo-dev 精确 EMC 入账）
 * - 删除：无。
 * - 新增：精确待结算队列、服务器移交、换主人前结清及损坏队列原文保留。
 * - 修改：服务端 ticker 只提交已有队列；world SavedData 接收后清队列，在线写入故障由账本重试。
 * - 用途：建立资源回收前的结算生命周期，尚不接收或销毁资源。
 *
 * 【变更记录 #4】2026-10-22 21:44:20（第 4 次备注 · 2.0.0-neo-dev 黑洞资源回收）
 * - 删除：资源入口保持关闭的阶段限制。
 * - 新增：真实回收端口、缓冲 NBT、有效归属和账本预检以及损坏缓冲恢复。
 * - 修改：ticker 先回收再结算；拆除或换主人先按旧归属处理缓冲与收益。
 * - 用途：防止缓存端口在拆除后继续收取资源，保留未知数据供恢复。
 * 【变更记录 #5】2026-10-22 22:20:00（第 5 次备注 · 2.0.0-neo-dev 统一化学品回收）
 * - 删除：无。
 * - 新增：只传注册 ID/标签回调的化学品接收桥及共用实例预算。
 * - 修改：报价期间禁止换主人及其他资源入口；NBT 重载重置化学品瞬态预算。
 * - 用途：隔离 Mekanism 类型，同时让六面及无面查询共享同一交易状态。
 * 【变更记录 #6】2026-10-23 13:40:12（第 6 次备注 · 2.0.0-neoforge-knowledge）
 * - 删除：仅检查全局知识根而接受被隔离所有者资源的旧路径。
 * - 新增：消费前按 owner 核验知识账本健康。
 * - 修改：损坏负向知识的所有者不抽取资源，避免收入成功但本次学习无法保存。
 * - 用途：其它健康玩家的黑洞不受单账户隔离影响，不清空任何已有缓冲或恢复数据。
 */
package com.primordial.neoforge.blackhole;

import java.util.UUID;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.primordial.neoforge.NeoForgeModRegistries;
import com.primordial.neoforge.emc.NeoForgeEmcCredits;
import com.primordial.neoforge.emc.NeoForgeEmcNbt;
import com.primordial.neoforge.emc.NeoForgeEmcRecoveryData;
import com.primordial.neoforge.emc.NeoForgeEmcSavedData;
import com.primordial.service.EmcAmount;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.energy.IEnergyStorage;

public final class NeoForgeBlackholeBlockEntity extends BlockEntity {
    @Nullable
    private UUID owner;
    private EmcAmount pendingCredit = EmcAmount.ZERO;
    @Nullable
    private Tag invalidQueue;
    private boolean settlementWarned;
    private boolean settling;
    private final NeoForgeBlackholeResources resources = new NeoForgeBlackholeResources(this);
    private final NeoForgeChemicalReception chemicals = new NeoForgeChemicalReception(this);

    public NeoForgeBlackholeBlockEntity(BlockPos pos, BlockState state) {
        super(NeoForgeModRegistries.BLACKHOLE_ENTITY.get(), pos, state);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        if (owner == null ? this.owner != null : !owner.equals(this.owner)) {
            if (!resources.usable() || resources.busy() || chemicals.busy()) throw new IllegalStateException("Blackhole buffer is unavailable");
            if (this.owner != null) resources.release();
            if ((!pendingCredit.isZero() || invalidQueue != null) && !settlePending()) {
                throw new IllegalStateException("Cannot change owner before queued EMC is settled");
            }
            this.owner = owner;
            setChanged();
        }
    }

    public EmcAmount pendingCredit() {
        return pendingCredit;
    }

    public IItemHandler itemHandler() { return resources.items; }
    public IFluidHandler fluidHandler() { return resources.fluids; }
    public IEnergyStorage energyHandler() { return resources.energy; }

    public boolean canRecycleResources() { return resources.externalReady(); }
    public boolean isChemicalValid(ResourceLocation id, Predicate<ResourceLocation> inTag) {
        return chemicals.valid(id, inTag);
    }
    public long recycleChemical(ResourceLocation id, long amount, boolean simulate, Predicate<ResourceLocation> inTag) {
        return chemicals.receive(id, amount, simulate, inTag);
    }
    boolean chemicalBusy() { return chemicals.busy(); }
    void recoveryParticles() { resources.particles(); }

    boolean resourceReady() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved() || owner == null || invalidQueue != null
                || !serverLevel.getServer().isSameThread() || serverLevel.getBlockEntity(worldPosition) != this) return false;
        return NeoForgeEmcSavedData.get(serverLevel.getServer()).canCredit(owner)
                && NeoForgeRecoveryKnowledge.available(serverLevel.getServer(), owner);
    }

    /** 回收适配器确认实际数量后，将精确收益加入本机待结算队列。 */
    public void queueCredit(EmcAmount amount) {
        Objects.requireNonNull(amount);
        requireServer();
        if (owner == null || invalidQueue != null) throw new IllegalStateException("Blackhole has no usable owner/queue");
        if (!amount.isZero()) {
            pendingCredit = pendingCredit.add(amount);
            setChanged();
        }
    }

    public boolean settlePending() {
        if (settling) return false;
        if (invalidQueue != null) return false;
        if (pendingCredit.isZero()) return true;
        settling = true;
        try {
            ServerLevel serverLevel = requireServer();
            if (owner == null) return false;
            EmcAmount accepted = pendingCredit;
            NeoForgeEmcCredits.credit(serverLevel.getServer(), owner, accepted);
            pendingCredit = pendingCredit.subtract(accepted);
            settlementWarned = false;
            setChanged();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (!settlementWarned) LogUtils.getLogger().warn(
                    "[Primordial] Blackhole EMC queue retained at {}", worldPosition, failure);
            settlementWarned = true;
            return false;
        } finally {
            settling = false;
        }
    }

    /** 正常拆除先结算；无法结算的原文另存恢复日志，不把它当作已经入账。 */
    public void settleOnRemoval() {
        resources.release();
        if (settlePending() && resources.usable()) return;
        ServerLevel serverLevel = requireServer();
        CompoundTag snapshot = saveWithFullMetadata(serverLevel.registryAccess());
        snapshot.putString("dimension", serverLevel.dimension().location().toString());
        NeoForgeEmcRecoveryData.preserve(serverLevel.getServer(), snapshot);
        LogUtils.getLogger().error("[Primordial] Unsettled blackhole preserved in EMC recovery data at {}", worldPosition);
        pendingCredit = EmcAmount.ZERO;
        invalidQueue = null;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, NeoForgeBlackholeBlockEntity blackhole) {
        if (level instanceof ServerLevel serverLevel && !blackhole.isRemoved()) {
            blackhole.resources.tick(serverLevel);
            blackhole.settlePending();
        }
    }

    private ServerLevel requireServer() {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            throw new IllegalStateException("Blackhole credit requires an attached server block entity");
        }
        NeoForgeEmcCredits.requireServerThread(serverLevel.getServer());
        return serverLevel;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) tag.putUUID("owner", owner);
        if (invalidQueue != null) tag.put("queued_credit", invalidQueue.copy());
        else if (!pendingCredit.isZero()) tag.put("queued_credit", NeoForgeEmcNbt.write(pendingCredit));
        resources.save(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        pendingCredit = EmcAmount.ZERO;
        invalidQueue = null;
        settlementWarned = false;
        resources.load(tag, registries);
        chemicals.reset();
        if (!resources.usable()) LogUtils.getLogger().warn("[Primordial] Invalid blackhole item buffer preserved at {}", worldPosition);
        if (tag.contains("queued_credit")) {
            try {
                if (!tag.contains("queued_credit", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Expected EMC compound");
                pendingCredit = NeoForgeEmcNbt.read(tag.getCompound("queued_credit"));
            } catch (RuntimeException invalid) {
                invalidQueue = Objects.requireNonNull(tag.get("queued_credit")).copy();
                LogUtils.getLogger().warn("[Primordial] Invalid blackhole EMC queue preserved at {}", worldPosition);
            }
        }
    }
}
