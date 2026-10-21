/**
 * 【变更记录 #3】2026-09-15 00:40:01（第 3 次备注 · 性能紧急修复）
 * - 删除：每次输入立即回收、超出缓冲直接回收以及每堆物品的 INFO 日志路径。
 * - 新增：物品、流体、能量的每 tick 接收预算、pendingEmc 聚合结算、异常堆余量返回、实体吸收上限、粒子节流和待结算 EMC 持久化。
 * - 修改：IItemHandler 和掉落实体无法全部接收时保留 remainder；tick 尾部只执行一次 EmcService.add 与 BalanceSyncer.markDirty。
 * - 用途：阻断 Pipez 等管道对超大堆的无限传输和 ProjectE 高频同步，控制实体、NBT、日志和网络对象的瞬时数量。
 */
/**
 * 【变更记录 #4】2026-09-15 01:21:04（编码修复）
 * - 删除：本文件中因错误字符集转换产生的不可读问号备注。
 * - 新增：依据当前实现补充文件职责、数据边界、失败处理和线程安全说明。
 * - 修改：仅重建注释并保留可追溯历史，不改变业务代码、网络协议、NBT 字段或运行时行为。
 * - 用途：让后续维护者能够准确阅读本文件，并通过 UTF-8 字节检查防止同类损坏再次进入源码。
 */
/**
 * 【变更记录 #5】2026-09-15 12:04:51（第 5 次备注 · 1.0.0-configurator-fixes）
 * - 删除：无。
 * - 新增：气体 LazyOptional、每 tick 接收预算，以及 capability 失效/恢复生命周期。
 * - 修改：MekanismGasCompat 按需创建气体能力，回收仍汇总至 pendingEmc；不按管道调用次数结算。
 * - 用途：黑洞直接连接气体管道；超预算返回剩余量，区块能力重建时不复用失效对象。
 */
/**
 * 【变更记录 #6】2026-09-15 14:58:00（第 6 次备注 · 1.0.0-emc-service）
 * - 删除：黑洞独立 FE 整除余数的运行时结算。
 * - 新增：精确 EMC 聚合金额、分母持久化和方块移除前结清入口。
 * - 修改：每 tick 经 EmcService.credit 一次结算，成功后清队列；旧 FE 余数在首次 tick 转入同一队列。
 * - 用途：管道仍受每 tick 预算保护，小额流体/FE 不归零；区块保存与玩家离线都保留换算金额。
 */
/**
 * 【变更记录 #7】2026-09-15 15:44:04（第 7 次备注 · 1.0.0-emc-service）
 * - 删除：精确队列以有长度上限的十进制字符串保存的实现。
 * - 新增：分子/分母字节数组，兼容读取旧整数及分数字段。
 * - 修改：更新缓冲刷新说明，未定价物品保留或在拆除时掉回世界。
 * - 用途：不同价格分母累计后仍能完整重载，记录与实际交易行为保持一致。
 */
/**
 * 【变更记录 #8】2026-09-16 01:59:37（第 8 次备注 · 1.0.0-projecte-shared-prices）
 * - 删除：回收与购买强行共用同一个数值、绕过 ProjectE 损耗规则的假定。
 * - 新增：黑洞接收物品前按 ProjectE 回收价格判断，损耗使售价为零时拒绝接收，避免无收益销毁。
 * - 修改：交易定价统一委托 ProjectE，扩展流体/气体定价和 BigInteger 玩家余额不受影响。
 * - 用途：让本模组回收物品与转化桌回收遵循同一套价值规则。
 */
/**
 * 【变更记录 #9】2026-10-10 04:18:06（第 9 次备注 · 1.2.0-resource-system-one）
 * - 删除：FE 禁用时清空尚未计价的旧能量余数的路径。
 * - 新增：统一报价非零才转换旧余数，禁用期间保留原余数。
 * - 修改：已有精确 EMC 待结算金额仍按原流程入账，不重新按当前报价计算。
 * - 用途：黑名单影响新交易，但不能销毁存档中尚未结算的旧 FE。
 */
/**
 * 【变更记录 #10】2026-10-14 16:01:56（第 10 次备注 · 1.2.0-entity-emc-fusion）
 * - 删除：旧实体无报价/无入账路径的相关限制（其它历史功能保留）。
 * - 新增：黑洞方块正上方实体无掉落吸收入 owner 待结算 EMC，每刻最多 64 个。
 * - 修改：服务端校验、报价与统一 EMC 结算共同执行；不信任客户端价格或 NBT。
 * - 用途：完成实体生成、黑洞回收与同账户融合系统，失败不重复收费或产出。
 */
package com.primordial.blockentity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.primordial.PrimordialMod;
import com.primordial.config.PrimordialServerConfig;
import com.primordial.registry.ModBlockEntities;
import com.primordial.service.EmcAmount;
import com.primordial.service.EmcService;
import com.primordial.energy.EnergyConversions;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

/**
 * 【变更记录 #2】2026-09-14 12:05:00（第 2 次备注 · 1.0.0-stage5-feedback）
 * - 删除：无。
 * - 新增：黑洞暴露 Forge ENERGY 接口，并持久化 FE→EMC 换算余数，支持能量管道和龙之研究能量塔的标准 FE Utility。
 * - 修改：能力失效时同时关闭物品、流体和能量 LazyOptional；余数在方块保存时写入，避免小额 FE 永久丢失。
 * - 用途：黑洞作为物品、流体和电力的 EMC 汇，所有接收操作都在服务端线程完成并归属于 owner。
 */

/**
 * 黑洞方块实体：记录 owner，暴露虚无接收端，吸收正上方一格内的掉落物。
 *
 * 优化后逻辑：所有进入黑洞的物品先存入内部缓冲 buffer，
 * 每 tick 将缓冲全部转化为EMC（能量）入池，
 * 保证即时回收，不占用延时。
 */
public class BlackholeBlockEntity extends BlockEntity {
    /**
     * 黑洞缓冲容量上限为 64 个待处理堆栈；Forge 物品、流体和能量接口只负责接收预算内的数据。
     * 每 tick 将本 tick 的回收价值合并到 pendingEmc，避免每次管道调用都触发 ProjectE 余额写入。
     * 方块实体在服务端线程统一提交结算，能力失效或区块卸载时不会创建额外任务。
     */
    private static final int BUFFER_CAP = 64;

    @javax.annotation.Nullable
    private UUID owner;

    /** 本 tick 已接受的物品实体数量与物品总数，用于限制掉落物扫描和防止异常堆栈占满内存。 */
    private final List<ItemStack> buffer = new ArrayList<>();

    private final VoidHandlers.VoidItemHandler itemHandler = new VoidHandlers.VoidItemHandler(this);
    private final VoidHandlers.VoidFluidHandler fluidHandler = new VoidHandlers.VoidFluidHandler(this);
    private final VoidHandlers.VoidEnergyHandler energyHandler = new VoidHandlers.VoidEnergyHandler(this);
    private LazyOptional<VoidHandlers.VoidItemHandler> itemCap = LazyOptional.of(() -> itemHandler);
    private LazyOptional<VoidHandlers.VoidFluidHandler> fluidCap = LazyOptional.of(() -> fluidHandler);
    private LazyOptional<VoidHandlers.VoidEnergyHandler> energyCap = LazyOptional.of(() -> energyHandler);
    private LazyOptional<?> gasCap;

    /** FE 输入尚未换算成整 EMC 的余数；该值随方块保存，保证小额能量不会因 tick 结算而丢失。 */
    private long energyRemainder;
    /** 待结算 EMC 在 tick 尾部一次性提交给 EmcService；队列为空时跳过网络同步和持久化。 */
    private EmcAmount pendingEmc = EmcAmount.ZERO;
    /** 当前 tick 的物品、流体和 FE 接收预算，具体上限来自 primordial-server.toml，避免第三方管道无限调用。 */
    private int acceptedItemCount;
    private int acceptedFluidMb;
    private int acceptedGasMb;
    private int acceptedEnergy;
    private long budgetTick = Long.MIN_VALUE;
    /** 粒子生成和回收统计均按 tick 节流，只在达到配置间隔时生成视觉效果并记录聚合数量。 */
    private long lastParticleTick = Long.MIN_VALUE;
    private int reclaimedStacksThisTick;
    private long reclaimedItemsThisTick;

    public long getEnergyRemainder() { return energyRemainder; }

    public void setEnergyRemainder(long value) {
        long next = Math.max(0L, value);
        if (energyRemainder != next) {
            energyRemainder = next;
            setChanged();
        }
    }

    public BlackholeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLACKHOLE.get(), pos, state);
    }

    @javax.annotation.Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        if (this.owner != null && !this.owner.equals(owner)) settleBeforeRemoval();
        this.owner = owner;
        setChanged();
    }

    /**
 * 【编码修复说明】BlackholeBlockEntity 的原备注因错误字符集转换被写成问号，已根据当前实现恢复。
 * - 删除：不可读的问号文本。
 * - 新增：职责、边界条件和失败处理说明。
 * - 修改：只修复注释内容，不改变字段、协议、算法或运行时行为。
 * - 用途：帮助维护者理解本段代码，并确保后续修改可追溯。
 */
    public void puffParticles() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        int interval = PrimordialServerConfig.blackholeParticleIntervalTicks();
        if (lastParticleTick != Long.MIN_VALUE && now - lastParticleTick < interval) return;
        lastParticleTick = now;
        serverLevel.sendParticles(ParticleTypes.SMOKE,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                3, 0.3, 0.3, 0.3, 0.01);
    }

    /**
     * 扫描黑洞附近掉落实体并按配置上限处理，拒绝空堆栈、无价格物品和不属于当前世界的实体。
     * IItemHandler 的返回值保留未接收余量，避免 Pipez 等管道把一个超大堆重复发送到同一 tick。
     * 处理循环只在有限预算内运行，异常堆栈不会触发 x2147479551 次级别的 CPU 循环。
     */
    public ItemStack pushToBuffer(ItemStack input) {
        if (input == null || input.isEmpty()) return ItemStack.EMPTY;
        if (owner == null || level == null || level.isClientSide || level.getServer() == null
                || EmcService.itemRecoveryPrice(input, level.getServer()).isZero()) return input.copy();
        resetBudgetsIfNeeded();
        int budget = remainingItemBudget();
        if (budget <= 0) return input.copy();

        int accepted = Math.min(input.getCount(), budget);
        ItemStack remainder = input.copyWithCount(accepted);
        for (int i = 0; i < buffer.size() && !remainder.isEmpty(); i++) {
            ItemStack existing = buffer.get(i);
            if (!ItemStack.isSameItemSameTags(existing, remainder)) continue;
            int room = Math.max(0, existing.getMaxStackSize() - existing.getCount());
            int add = Math.min(room, remainder.getCount());
            if (add > 0) {
                existing.grow(add);
                remainder.shrink(add);
            }
        }
        while (!remainder.isEmpty() && buffer.size() < BUFFER_CAP) {
            int move = Math.min(remainder.getMaxStackSize(), remainder.getCount());
            buffer.add(remainder.copyWithCount(move));
            remainder.shrink(move);
        }

        int moved = accepted - remainder.getCount();
        if (moved > 0) {
            acceptedItemCount = (int) Math.min(PrimordialServerConfig.blackholeMaxItemCountPerTick(),
                    (long) acceptedItemCount + moved);
            setChanged();
        }
        int left = input.getCount() - moved;
        if (left <= 0) return ItemStack.EMPTY;
        return input.copyWithCount(left);
    }

    /** 将有价缓冲物品转换为精确待入账队列；无价或无法结算的堆栈继续保留。 */
    private void flushBuffer() {
        if (buffer.isEmpty()) return;
        int stacks = 0;
        var iterator = buffer.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack != null && !stack.isEmpty()) {
                if (VoidHandlers.reclaimItem(this, stack)) { stacks++; iterator.remove(); }
            } else iterator.remove();
        }
        reclaimedStacksThisTick += stacks;
        puffParticles();
        setChanged();
    }

    /** 每 tick 结束时合并回收统计、提交 EMC，并按需标记余额同步；所有操作均在服务端主线程执行。 */
    private void flushPendingEmc(ServerLevel serverLevel) {
        if (energyRemainder > 0) {
            EmcAmount price = EmcService.energyPrice(EnergyConversions.FE);
            if (!price.isZero()) {
                queueEmc(price.multiply(energyRemainder));
                energyRemainder = 0;
            }
        }
        if (pendingEmc.isZero() || owner == null) return;
        net.minecraft.server.MinecraftServer server = serverLevel.getServer();
        if (server == null) return;
        EmcAmount amount = pendingEmc;
        EmcService.credit(server, owner, amount);
        pendingEmc = EmcAmount.ZERO;
        PrimordialMod.LOGGER.debug("[Primordial][BH] tick settlement: {} EMC, {} stacks, {} items (owner={})",
                amount, reclaimedStacksThisTick, reclaimedItemsThisTick, owner);
        reclaimedStacksThisTick = 0;
        reclaimedItemsThisTick = 0;
        setChanged();
    }

    private void resetBudgetsIfNeeded() {
        long now = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (now == budgetTick) return;
        budgetTick = now;
        acceptedItemCount = 0;
        acceptedFluidMb = 0;
        acceptedGasMb = 0;
        acceptedEnergy = 0;
    }

    public int remainingItemBudget() {
        resetBudgetsIfNeeded();
        return Math.max(0, PrimordialServerConfig.blackholeMaxItemCountPerTick() - acceptedItemCount);
    }

    public int remainingFluidBudget() {
        resetBudgetsIfNeeded();
        return Math.max(0, PrimordialServerConfig.blackholeMaxFluidMbPerTick() - acceptedFluidMb);
    }

    public int remainingEnergyBudget() {
        resetBudgetsIfNeeded();
        return Math.max(0, PrimordialServerConfig.blackholeMaxEnergyPerTick() - acceptedEnergy);
    }

    public int remainingGasBudget() {
        resetBudgetsIfNeeded();
        return Math.max(0, PrimordialServerConfig.blackholeMaxGasMbPerTick() - acceptedGasMb);
    }

    public void recordGasAccepted(int amount) {
        resetBudgetsIfNeeded();
        acceptedGasMb = (int) Math.min(PrimordialServerConfig.blackholeMaxGasMbPerTick(),
                (long) acceptedGasMb + Math.max(0, amount));
    }

    public void recordFluidAccepted(int amount) {
        resetBudgetsIfNeeded();
        acceptedFluidMb = Math.min(PrimordialServerConfig.blackholeMaxFluidMbPerTick(),
                (int) Math.min(Integer.MAX_VALUE, (long) acceptedFluidMb + Math.max(0, amount)));
    }

    public void recordEnergyAccepted(int amount) {
        resetBudgetsIfNeeded();
        acceptedEnergy = Math.min(PrimordialServerConfig.blackholeMaxEnergyPerTick(),
                (int) Math.min(Integer.MAX_VALUE, (long) acceptedEnergy + Math.max(0, amount)));
    }

    /** 由 VoidHandlers 调用的统一回收入口；只负责入队和标记方块变更，不在能力回调中执行昂贵结算。 */
    public void queueEmc(EmcAmount amount) {
        if (amount == null || amount.isZero()) return;
        pendingEmc = pendingEmc.add(amount);
        setChanged();
    }

    /** 方块被拆除/替换前由 Block 调用；无价缓冲物品掉回世界，避免变更价格后被静默销毁。 */
    public void settleBeforeRemoval() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        flushBuffer();
        flushPendingEmc(serverLevel);
        for (ItemStack stack : buffer) net.minecraft.world.Containers.dropItemStack(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
        buffer.clear();
    }

    public void recordReclaimedItems(long amount) {
        long safe = Math.max(0L, amount);
        reclaimedItemsThisTick = safe > Long.MAX_VALUE - reclaimedItemsThisTick
                ? Long.MAX_VALUE : reclaimedItemsThisTick + safe;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlackholeBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel) || be.getOwner() == null) return;
        be.resetBudgetsIfNeeded();

        AABB scanBox = new AABB(pos).inflate(1.5);
        int converted=0;
        for (net.minecraft.world.entity.Entity entity : serverLevel.getEntities((net.minecraft.world.entity.Entity)null, scanBox)) {
            if(converted>=64)break;
            if(!entity.isRemoved()&&entity.blockPosition().equals(pos.above())) {
                BigInteger value=com.primordial.service.EntityEmc.absorb(entity);
                if(value.signum()>0){be.queueEmc(EmcAmount.of(value));converted++;}
            }
        }

        AABB aboveBox = new AABB(pos.above()).inflate(0.1);
        List<ItemEntity> entities = serverLevel.getEntitiesOfClass(ItemEntity.class, aboveBox);
        int absorbed = 0;
        int entityLimit = PrimordialServerConfig.blackholeMaxItemEntitiesPerTick();
        for (ItemEntity entity : entities) {
            if (absorbed >= entityLimit || be.remainingItemBudget() <= 0) break;
            if (entity.isRemoved()) continue;
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                entity.discard();
                continue;
            }
            ItemStack remainder = be.pushToBuffer(stack);
            if (remainder.isEmpty()) {
                entity.discard();
                absorbed++;
            } else if (remainder.getCount() < stack.getCount()) {
                entity.setItem(remainder);
                entity.setPickUpDelay(10);
                absorbed++;
            }
        }
        if (absorbed > 0) {
            PrimordialMod.LOGGER.debug("[Primordial][BH] absorbed {} item entities at {} (owner={})",
                    absorbed, pos, be.getOwner());
        }

        be.flushBuffer();
        be.flushPendingEmc(serverLevel);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @javax.annotation.Nullable net.minecraft.core.Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        if (cap == ForgeCapabilities.FLUID_HANDLER) return fluidCap.cast();
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        if (com.primordial.compat.MekanismGasCompat.isGasCapability(cap)) {
            if (isRemoved()) return LazyOptional.empty();
            if (gasCap == null) gasCap = com.primordial.compat.MekanismGasCompat.blackholeCapability(this);
            return gasCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
        fluidCap.invalidate();
        energyCap.invalidate();
        if (gasCap != null) gasCap.invalidate();
    }

    @Override public void reviveCaps() {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> itemHandler);
        fluidCap = LazyOptional.of(() -> fluidHandler);
        energyCap = LazyOptional.of(() -> energyHandler);
        gasCap = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("owner", owner);
        if (!buffer.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack stack : buffer) if (stack != null && !stack.isEmpty()) list.add(stack.save(new CompoundTag()));
            tag.put("buffer", list);
        }
        tag.putLong("energy_remainder", energyRemainder);
        if (!pendingEmc.isZero()) {
            tag.putByteArray("pending_emc_n", pendingEmc.numerator().toByteArray());
            tag.putByteArray("pending_emc_d", pendingEmc.denominator().toByteArray());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        buffer.clear();
        if (tag.contains("buffer")) {
            ListTag list = tag.getList("buffer", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && buffer.size() < BUFFER_CAP; i++) {
                ItemStack stack = ItemStack.of(list.getCompound(i));
                if (!stack.isEmpty()) {
                    stack.setCount(Math.min(stack.getCount(), stack.getMaxStackSize()));
                    buffer.add(stack);
                }
            }
        }
        energyRemainder = Math.max(0L, tag.getLong("energy_remainder"));
        try {
            BigInteger n = tag.contains("pending_emc_n", Tag.TAG_BYTE_ARRAY) ? new BigInteger(tag.getByteArray("pending_emc_n"))
                    : tag.contains("pending_emc") ? new BigInteger(tag.getString("pending_emc")) : BigInteger.ZERO;
            BigInteger d = tag.contains("pending_emc_d", Tag.TAG_BYTE_ARRAY) ? new BigInteger(tag.getByteArray("pending_emc_d"))
                    : tag.contains("pending_emc_denominator") ? new BigInteger(tag.getString("pending_emc_denominator")) : BigInteger.ONE;
            pendingEmc = new EmcAmount(n, d);
        } catch (RuntimeException invalid) {
            pendingEmc = EmcAmount.ZERO;
            PrimordialMod.LOGGER.warn("[Primordial] Invalid black hole EMC queue at {}", worldPosition);
        }
    }
}
